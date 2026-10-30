package com.blockplace.client.animation.tree;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;

import java.util.*;

/**
 * Fast breadth-first search detector that identifies connected tree logs and canopy leaves.
 */
public class TreeDetector {

    public static final int MAX_LOGS = 160;
    public static final int MAX_LEAVES = 384;
    public static final int MAX_HORIZONTAL_RADIUS = 9;
    public static final int MAX_VERTICAL_UP = 36;
    public static final int MAX_VERTICAL_DOWN = 4;
    public static final int MAX_LEAF_DISTANCE = 3;

    /**
     * Attempts to find a connected tree structure starting from the punched block.
     *
     * @param level    client world
     * @param startPos clicked block position
     * @return TreeStructure if a valid tree was identified, null otherwise
     */
    public static TreeStructure findTree(ClientLevel level, BlockPos startPos) {
        BlockState startState = level.getBlockState(startPos);
        boolean isLog = startState.is(BlockTags.LOGS);
        boolean isLeaf = startState.is(BlockTags.LEAVES) || startState.is(BlockTags.WART_BLOCKS);

        if (!isLog && !isLeaf) {
            return null;
        }

        Set<BlockPos> foundLogs = new HashSet<>();
        Set<BlockPos> foundLeaves = new HashSet<>();

        if (isLog) {
            // Standard trunk punch: find connected logs first, then attached leaves
            collectLogs(level, startPos, foundLogs);
            if (foundLogs.isEmpty()) {
                return null;
            }
            collectLeavesFromLogs(level, foundLogs, foundLeaves);
        } else {
            // Leaf punch: find connected leaves and nearby trunk
            collectLeaves(level, startPos, foundLeaves);
            // Check if any leaf touches a log to find the full tree
            for (BlockPos leafPos : foundLeaves) {
                for (int dx = -1; dx <= 1; dx++) {
                    for (int dy = -1; dy <= 1; dy++) {
                        for (int dz = -1; dz <= 1; dz++) {
                            BlockPos neighbor = leafPos.offset(dx, dy, dz);
                            if (level.getBlockState(neighbor).is(BlockTags.LOGS)) {
                                collectLogs(level, neighbor, foundLogs);
                                break;
                            }
                        }
                        if (!foundLogs.isEmpty()) break;
                    }
                    if (!foundLogs.isEmpty()) break;
                }
                if (!foundLogs.isEmpty()) break;
            }

            if (!foundLogs.isEmpty()) {
                collectLeavesFromLogs(level, foundLogs, foundLeaves);
            }
        }

        // Validate structure: must have leaves, or be a small standalone trunk pillar
        if (foundLeaves.isEmpty()) {
            if (foundLogs.size() > 8) {
                // Large structure without leaves is likely a wooden house or wall
                return null;
            }
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

        // Determine root pivot: center of base logs, or fallback to min position
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

            entries.add(new TreeBlockEntry(pos, state, false, heightFraction, radialDist, seed));
        }

        for (BlockPos pos : foundLeaves) {
            BlockState state = level.getBlockState(pos);
            if (state.getRenderShape() != RenderShape.MODEL) continue;

            float heightFraction = (float) (pos.getY() - minY) / (float) heightSpan;
            double rx = pos.getX() - rootPos.getX();
            double rz = pos.getZ() - rootPos.getZ();
            float radialDist = (float) Math.sqrt(rx * rx + rz * rz);
            int seed = (pos.getX() * 31 + pos.getY() * 17 + pos.getZ() * 23) & 0xFFFF;

            entries.add(new TreeBlockEntry(pos, state, true, heightFraction, radialDist, seed));
        }

        if (entries.isEmpty()) {
            return null;
        }

        BlockPos minPos = new BlockPos(minX, minY, minZ);
        BlockPos maxPos = new BlockPos(maxX, maxY, maxZ);

        return new TreeStructure(rootPos, minPos, maxPos, entries);
    }

    private static void collectLogs(ClientLevel level, BlockPos origin, Set<BlockPos> foundLogs) {
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

                        if (!foundLogs.contains(neighbor) && level.getBlockState(neighbor).is(BlockTags.LOGS)) {
                            foundLogs.add(neighbor);
                            queue.add(neighbor);
                        }
                    }
                }
            }
        }
    }

    private static void collectLeavesFromLogs(ClientLevel level, Set<BlockPos> logs, Set<BlockPos> foundLeaves) {
        record LeafNode(BlockPos pos, int distance) {}
        Queue<LeafNode> queue = new ArrayDeque<>();

        // Seed with leaves directly adjacent to logs
        for (BlockPos logPos : logs) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        BlockPos neighbor = logPos.offset(dx, dy, dz);
                        if (!logs.contains(neighbor) && !foundLeaves.contains(neighbor)) {
                            BlockState state = level.getBlockState(neighbor);
                            if (state.is(BlockTags.LEAVES) || state.is(BlockTags.WART_BLOCKS)) {
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
                        if (Math.abs(dx) + Math.abs(dy) + Math.abs(dz) > 2) continue; // up to 18-neighborhood for compact BFS

                        BlockPos neighbor = current.pos.offset(dx, dy, dz);
                        if (!logs.contains(neighbor) && !foundLeaves.contains(neighbor)) {
                            BlockState state = level.getBlockState(neighbor);
                            if (state.is(BlockTags.LEAVES) || state.is(BlockTags.WART_BLOCKS)) {
                                foundLeaves.add(neighbor);
                                queue.add(new LeafNode(neighbor, current.distance + 1));
                            }
                        }
                    }
                }
            }
        }
    }

    private static void collectLeaves(ClientLevel level, BlockPos origin, Set<BlockPos> foundLeaves) {
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
                        if (Math.abs(neighbor.getX() - origin.getX()) > 6 ||
                            Math.abs(neighbor.getZ() - origin.getZ()) > 6 ||
                            Math.abs(neighbor.getY() - origin.getY()) > 6) {
                            continue;
                        }

                        if (!foundLeaves.contains(neighbor)) {
                            BlockState state = level.getBlockState(neighbor);
                            if (state.is(BlockTags.LEAVES) || state.is(BlockTags.WART_BLOCKS)) {
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
