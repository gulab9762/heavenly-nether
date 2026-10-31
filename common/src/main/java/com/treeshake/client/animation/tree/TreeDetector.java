package com.treeshake.client.animation.tree;

import com.treeshake.client.config.TreeShakeConfig;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.block.MovingBlockRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Fast breadth-first search detector that identifies connected tree stems, trunks,
 * branches, and canopy foliage based on the configurable shakable whitelist.
 */
public class TreeDetector {

    public static final int MAX_LOGS = 300;
    public static final int MAX_LEAVES = 1000;
    public static final int MAX_HORIZONTAL_RADIUS = 16;
    public static final int MAX_VERTICAL_UP = 42;
    public static final int MAX_VERTICAL_DOWN = 32;
    public static final int MAX_LEAF_DISTANCE = 7;

    private static final Set<Identifier> WHITELISTED_BLOCKS = ConcurrentHashMap.newKeySet();
    private static final Set<TagKey<Block>> WHITELISTED_TAGS = ConcurrentHashMap.newKeySet();
    private static volatile boolean whitelistInitialized = false;

    /**
     * Rebuilds the fast tag and block ID lookup cache from the config whitelist.
     */
    public static void rebuildWhitelistCache() {
        WHITELISTED_BLOCKS.clear();
        WHITELISTED_TAGS.clear();

        for (String entry : TreeShakeConfig.shakableBlocksWhitelist) {
            String trimmed = entry.trim();
            if (trimmed.isEmpty()) continue;
            if (trimmed.startsWith("#")) {
                String tagStr = trimmed.substring(1);
                Identifier loc = Identifier.tryParse(tagStr);
                if (loc != null) {
                    WHITELISTED_TAGS.add(TagKey.create(Registries.BLOCK, loc));
                }
            } else {
                Identifier loc = Identifier.tryParse(trimmed);
                if (loc != null) {
                    WHITELISTED_BLOCKS.add(loc);
                }
            }
        }
        whitelistInitialized = true;
    }

    /**
     * Checks if a block state represents a tree trunk, stem, or root pillar
     * according to the user-configured whitelist.
     */
    public static boolean isStemOrLog(BlockState state) {
        if (state == null) return false;
        if (!whitelistInitialized) {
            rebuildWhitelistCache();
        }

        for (TagKey<Block> tag : WHITELISTED_TAGS) {
            if (state.is(tag)) {
                return true;
            }
        }

        Identifier blockId = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        return WHITELISTED_BLOCKS.contains(blockId);
    }

    /**
     * Checks if a block state represents tree leaves, wart blocks, or huge mushroom caps.
     */
    public static boolean isFoliageOrLeaves(BlockState state) {
        if (state == null) return false;
        return state.is(BlockTags.LEAVES)
                || state.is(BlockTags.WART_BLOCKS)
                || state.is(Blocks.RED_MUSHROOM_BLOCK)
                || state.is(Blocks.BROWN_MUSHROOM_BLOCK)
                || state.is(Blocks.SHROOMLIGHT)
                || state.is(Blocks.MANGROVE_LEAVES);
    }

    /**
     * Attempts to find a connected tree structure starting from the punched block.
     *
     * @param level    client world
     * @param startPos clicked block position
     * @return TreeStructure if a valid tree was identified, null otherwise
     */
    public static TreeStructure findTree(ClientLevel level, BlockPos startPos) {
        BlockState startState = level.getBlockState(startPos);
        if (!isStemOrLog(startState)) {
            return null; // Wooden blocks ONLY
        }

        Set<BlockPos> foundLogs = new HashSet<>();
        Set<BlockPos> foundLeaves = new HashSet<>();

        // Collect connected stem logs and crown canopy
        collectStemLogs(level, startPos, foundLogs);
        if (foundLogs.isEmpty()) {
            return null;
        }
        collectCanopyLeaves(level, foundLogs, foundLeaves);

        // Do not shake if the tree size is only 1 (e.g. single standalone placed log)
        if (foundLogs.size() + foundLeaves.size() <= 1) {
            return null;
        }

        // Validate: if it's a huge wooden building with zero leaves, don't shake it
        if (foundLeaves.isEmpty() && foundLogs.size() > 8) {
            return null;
        }

        if (foundLogs.isEmpty() && foundLeaves.isEmpty()) {
            return null;
        }

        // Compute tree bounding box and anchor root position
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;

        int lowestLogY = Integer.MAX_VALUE;
        List<BlockPos> baseLogs = new ArrayList<>();

        for (BlockPos pos : foundLogs) {
            minX = Math.min(minX, pos.getX());
            minY = Math.min(minY, pos.getY());
            minZ = Math.min(minZ, pos.getZ());
            maxX = Math.max(maxX, pos.getX());
            maxY = Math.max(maxY, pos.getY());
            maxZ = Math.max(maxZ, pos.getZ());

            if (pos.getY() < lowestLogY) {
                lowestLogY = pos.getY();
                baseLogs.clear();
                baseLogs.add(pos);
            } else if (pos.getY() == lowestLogY) {
                baseLogs.add(pos);
            }
        }

        for (BlockPos pos : foundLeaves) {
            minX = Math.min(minX, pos.getX());
            minY = Math.min(minY, pos.getY());
            minZ = Math.min(minZ, pos.getZ());
            maxX = Math.max(maxX, pos.getX());
            maxY = Math.max(maxY, pos.getY());
            maxZ = Math.max(maxZ, pos.getZ());
        }

        // Determine root pivot: base of trunk where stem touches ground
        BlockPos rootPos;
        if (!baseLogs.isEmpty()) {
            rootPos = baseLogs.get(0);
        } else {
            rootPos = new BlockPos(startPos.getX(), minY, startPos.getZ());
        }

        int heightSpan = Math.max(1, maxY - minY);
        List<TreeBlockEntry> entries = new ArrayList<>(foundLogs.size() + foundLeaves.size());

        for (BlockPos pos : foundLogs) {
            BlockState state = level.getBlockState(pos);
            if (state.getRenderShape() != RenderShape.MODEL) continue;

            float heightFraction = (float) (pos.getY() - minY) / (float) heightSpan;
            double rx = pos.getX() - rootPos.getX();
            double rz = pos.getZ() - rootPos.getZ();
            float radialDist = (float) Math.sqrt(rx * rx + rz * rz);
            int seed = (pos.getX() * 31 + pos.getY() * 17 + pos.getZ() * 23) & 0xFFFF;

            MovingBlockRenderState renderState = new MovingBlockRenderState();
            renderState.randomSeedPos = pos;
            renderState.blockPos = pos;
            renderState.blockState = state;
            renderState.biome = level.getBiome(pos);
            renderState.cardinalLighting = level.cardinalLighting();
            renderState.lightEngine = level.getLightEngine();

            entries.add(new TreeBlockEntry(pos, state, false, heightFraction, radialDist, seed, renderState));
        }

        for (BlockPos pos : foundLeaves) {
            BlockState state = level.getBlockState(pos);
            if (state.getRenderShape() != RenderShape.MODEL) continue;

            float heightFraction = (float) (pos.getY() - minY) / (float) heightSpan;
            double rx = pos.getX() - rootPos.getX();
            double rz = pos.getZ() - rootPos.getZ();
            float radialDist = (float) Math.sqrt(rx * rx + rz * rz);
            int seed = (pos.getX() * 31 + pos.getY() * 17 + pos.getZ() * 23) & 0xFFFF;

            MovingBlockRenderState renderState = new MovingBlockRenderState();
            renderState.randomSeedPos = pos;
            renderState.blockPos = pos;
            renderState.blockState = state;
            renderState.biome = level.getBiome(pos);
            renderState.cardinalLighting = level.cardinalLighting();
            renderState.lightEngine = level.getLightEngine();

            entries.add(new TreeBlockEntry(pos, state, true, heightFraction, radialDist, seed, renderState));
        }

        if (entries.size() <= 1) {
            return null;
        }

        BlockPos minPos = new BlockPos(minX, minY, minZ);
        BlockPos maxPos = new BlockPos(maxX, maxY, maxZ);

        return new TreeStructure(startPos, rootPos, minPos, maxPos, entries);
    }

    /**
     * Traverses the entire stem / trunk column and branches using 26-neighborhood BFS.
     */
    private static void collectStemLogs(ClientLevel level, BlockPos origin, Set<BlockPos> foundLogs) {
        Queue<BlockPos> queue = new ArrayDeque<>();
        queue.add(origin);
        foundLogs.add(origin);

        while (!queue.isEmpty() && foundLogs.size() < MAX_LOGS) {
            BlockPos current = queue.poll();

            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        if (dx == 0 && dy == 0 && dz == 0) continue;

                        BlockPos neighbor = current.offset(dx, dy, dz);

                        if (Math.abs(neighbor.getX() - origin.getX()) > MAX_HORIZONTAL_RADIUS ||
                            Math.abs(neighbor.getZ() - origin.getZ()) > MAX_HORIZONTAL_RADIUS ||
                            neighbor.getY() < origin.getY() - MAX_VERTICAL_DOWN ||
                            neighbor.getY() > origin.getY() + MAX_VERTICAL_UP) {
                            continue;
                        }

                        if (!foundLogs.contains(neighbor) && isStemOrLog(level.getBlockState(neighbor))) {
                            foundLogs.add(neighbor);
                            queue.add(neighbor);
                        }
                    }
                }
            }
        }
    }

    /**
     * Finds the stem / trunk seed block when starting from a leaf punch.
     */
    private static BlockPos findStemSeedFromFoliage(ClientLevel level, BlockPos startPos) {
        // 1. First search immediate connected foliage for any touching stem
        Queue<BlockPos> queue = new ArrayDeque<>();
        Set<BlockPos> visited = new HashSet<>();
        queue.add(startPos);
        visited.add(startPos);

        while (!queue.isEmpty() && visited.size() < 120) {
            BlockPos current = queue.poll();

            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        BlockPos neighbor = current.offset(dx, dy, dz);
                        BlockState state = level.getBlockState(neighbor);

                        if (isStemOrLog(state)) {
                            return neighbor.immutable();
                        }

                        if (!visited.contains(neighbor) && isFoliageOrLeaves(state)) {
                            if (Math.abs(neighbor.getX() - startPos.getX()) <= 7 &&
                                Math.abs(neighbor.getZ() - startPos.getZ()) <= 7 &&
                                neighbor.getY() >= startPos.getY() - 6 &&
                                neighbor.getY() <= startPos.getY() + 16) {
                                visited.add(neighbor);
                                queue.add(neighbor);
                            }
                        }
                    }
                }
            }
        }

        // 2. If foliage was disconnected or outer shell, search radially outward up to 5 blocks
        for (int r = 1; r <= 5; r++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dy = -4; dy <= 8; dy++) {
                    for (int dz = -r; dz <= r; dz++) {
                        if (Math.abs(dx) != r && Math.abs(dz) != r) continue;
                        BlockPos check = startPos.offset(dx, dy, dz);
                        if (isStemOrLog(level.getBlockState(check))) {
                            return check.immutable();
                        }
                    }
                }
            }
        }

        return null;
    }

    /**
     * Gathers all canopy leaves attached to any log in the stem.
     */
    private static void collectCanopyLeaves(ClientLevel level, Set<BlockPos> logs, Set<BlockPos> foundLeaves) {
        record LeafNode(BlockPos pos, int distance) {}
        Queue<LeafNode> queue = new ArrayDeque<>();

        // Seed with leaves directly adjacent to any stem log
        for (BlockPos logPos : logs) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        BlockPos neighbor = logPos.offset(dx, dy, dz);
                        if (!logs.contains(neighbor) && !foundLeaves.contains(neighbor)) {
                            BlockState state = level.getBlockState(neighbor);
                            if (isFoliageOrLeaves(state)) {
                                foundLeaves.add(neighbor);
                                queue.add(new LeafNode(neighbor, 1));
                            }
                        }
                    }
                }
            }
        }

        // Expand through adjacent leaves up to MAX_LEAF_DISTANCE
        while (!queue.isEmpty() && foundLeaves.size() < MAX_LEAVES) {
            LeafNode current = queue.poll();

            if (current.distance >= MAX_LEAF_DISTANCE) {
                continue;
            }

            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        if (Math.abs(dx) + Math.abs(dy) + Math.abs(dz) > 2) continue;

                        BlockPos neighbor = current.pos.offset(dx, dy, dz);
                        if (!logs.contains(neighbor) && !foundLeaves.contains(neighbor)) {
                            BlockState state = level.getBlockState(neighbor);
                            if (isFoliageOrLeaves(state)) {
                                foundLeaves.add(neighbor);
                                queue.add(new LeafNode(neighbor, current.distance + 1));
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * Collects a compact foliage cluster (e.g. for standalone bushes or starting punch).
     */
    private static void collectFoliageCluster(ClientLevel level, BlockPos origin, Set<BlockPos> foundLeaves) {
        Queue<BlockPos> queue = new ArrayDeque<>();
        queue.add(origin);
        foundLeaves.add(origin);

        while (!queue.isEmpty() && foundLeaves.size() < MAX_LEAVES) {
            BlockPos current = queue.poll();

            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        if (dx == 0 && dy == 0 && dz == 0) continue;

                        BlockPos neighbor = current.offset(dx, dy, dz);
                        if (Math.abs(neighbor.getX() - origin.getX()) > 8 ||
                            Math.abs(neighbor.getZ() - origin.getZ()) > 8 ||
                            Math.abs(neighbor.getY() - origin.getY()) > 12) {
                            continue;
                        }

                        if (!foundLeaves.contains(neighbor)) {
                            BlockState state = level.getBlockState(neighbor);
                            if (isFoliageOrLeaves(state)) {
                                foundLeaves.add(neighbor);
                                queue.add(neighbor);
                            }
                        }
                    }
                }
            }
        }
    }
}
