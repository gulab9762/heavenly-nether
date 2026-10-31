package com.blockplace.client.animation.tree;

import com.blockplace.client.config.BlockPlaceConfig;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Global manager for tracking, ticking, hiding, and rendering client-side cartoon tree shake animations.
 * Includes a failsafe watchdog to guarantee chunk-meshed blocks never freeze or vanish permanently.
 */
public class TreeShakeManager {

    private record HiddenBlockEntry(BlockPos rootPos, long expirationTick) {}

    private static final Map<BlockPos, TreeShakeAnimation> ACTIVE_ANIMATIONS = new ConcurrentHashMap<>();
    private static final Map<BlockPos, HiddenBlockEntry> HIDDEN_BLOCKS = new ConcurrentHashMap<>();
    private static final Set<BlockPos> UNHIDDEN_SETTLING_TREES = ConcurrentHashMap.newKeySet();
    private static long currentClientTick = 0;

    /**
     * Checks if a block is temporarily hidden from chunk meshing due to an active tree shake.
     */
    public static boolean isBlockHidden(BlockPos pos) {
        return HIDDEN_BLOCKS.containsKey(pos);
    }

    /**
     * Replaced state for hidden block (air).
     */
    public static BlockState getHiddenBlockReplacedState(BlockPos pos) {
        return HIDDEN_BLOCKS.containsKey(pos) ? Blocks.AIR.defaultBlockState() : null;
    }

    /**
     * Called when the player right-clicks (interacts with) a block.
     * Only triggers if right-clicking with an empty hand and the block matches the shakable whitelist.
     */
    public static void onRightClickBlock(ClientLevel level, Player player, InteractionHand hand, BlockPos pos, Direction direction) {
        if (!BlockPlaceConfig.enableTreeShake) {
            return;
        }

        // Do not shake if right-clicked with a non-empty hand (e.g. holding blocks, tools, items)
        if (player == null || !player.getItemInHand(hand).isEmpty() || !player.getMainHandItem().isEmpty()) {
            return;
        }

        BlockState state = level.getBlockState(pos);
        if (!TreeDetector.isStemOrLog(state)) {
            return; // Whitelisted blocks ONLY
        }

        onPunchBlock(level, player, pos, direction);
    }

    /**
     * Called when the player punches (left-clicks) a block.
     */
    public static void onPunchBlock(ClientLevel level, Player player, BlockPos pos, Direction direction) {
        if (!BlockPlaceConfig.enableTreeShake) {
            return;
        }

        Vec3 punchVec = calculatePunchVector(player, pos, direction);

        // Check if the punched block is already part of an active tree
        for (TreeShakeAnimation anim : ACTIVE_ANIMATIONS.values()) {
            if (anim.getStructure().contains(pos)) {
                anim.punch(punchVec);
                // Reset settling flag so the tree shakes again
                UNHIDDEN_SETTLING_TREES.remove(anim.getStructure().getRootPos());

                // Refresh expiration of hidden blocks for this tree
                long newExpiration = currentClientTick + anim.getMaxDurationTicks() + 12;
                for (BlockPos treePos : anim.getStructure().getBlockPositions()) {
                    HIDDEN_BLOCKS.put(treePos, new HiddenBlockEntry(anim.getStructure().getRootPos(), newExpiration));
                }

                spawnPunchEffects(level, pos, anim.getStructure(), true);
                return;
            }
        }

        // Detect if this block is part of a natural tree
        TreeStructure structure = TreeDetector.findTree(level, pos);
        if (structure == null) {
            return;
        }

        // If the new tree overlaps with an existing animation, cleanly remove the old one first
        ACTIVE_ANIMATIONS.entrySet().removeIf(entry -> {
            TreeShakeAnimation existingAnim = entry.getValue();
            boolean overlaps = false;
            for (BlockPos p : structure.getBlockPositions()) {
                if (existingAnim.getStructure().contains(p)) {
                    overlaps = true;
                    break;
                }
            }
            if (overlaps) {
                for (BlockPos oldPos : existingAnim.getStructure().getBlockPositions()) {
                    HIDDEN_BLOCKS.remove(oldPos);
                }
                UNHIDDEN_SETTLING_TREES.remove(entry.getKey());
                markTreeSectionsDirty(level, existingAnim.getStructure());
                return true;
            }
            return false;
        });

        // Hide all tree blocks from baked chunk rendering with failsafe expiration
        long expiration = currentClientTick + BlockPlaceConfig.treeShakeDurationTicks + 14;
        for (BlockPos treePos : structure.getBlockPositions()) {
            HIDDEN_BLOCKS.put(treePos, new HiddenBlockEntry(structure.getRootPos(), expiration));
        }

        // Mark chunk sections dirty so they compile without the tree
        markTreeSectionsDirty(level, structure);

        TreeShakeAnimation animation = new TreeShakeAnimation(level, structure, punchVec);
        ACTIVE_ANIMATIONS.put(structure.getRootPos(), animation);

        spawnPunchEffects(level, pos, structure, true);
    }

    /**
     * Called continuously while holding attack on a block.
     */
    public static void onContinuousPunch(ClientLevel level, Player player, BlockPos pos, Direction direction) {
        if (!BlockPlaceConfig.enableTreeShake) {
            return;
        }

        for (TreeShakeAnimation anim : ACTIVE_ANIMATIONS.values()) {
            if (anim.getStructure().contains(pos) && anim.canReceiveMiningPunch()) {
                Vec3 punchVec = calculatePunchVector(player, pos, direction);
                anim.punch(punchVec);
                UNHIDDEN_SETTLING_TREES.remove(anim.getStructure().getRootPos());

                long newExpiration = currentClientTick + anim.getMaxDurationTicks() + 12;
                for (BlockPos treePos : anim.getStructure().getBlockPositions()) {
                    HIDDEN_BLOCKS.put(treePos, new HiddenBlockEntry(anim.getStructure().getRootPos(), newExpiration));
                }

                spawnPunchEffects(level, pos, anim.getStructure(), false);
                return;
            }
        }
    }

    /**
     * Called when a block is broken in the level.
     * If a trunk log is broken, cancels animation immediately and unhides all blocks
     * to prevent desynchronized chunk rendering.
     */
    public static void onBlockDestroyed(ClientLevel level, BlockPos pos) {
        HIDDEN_BLOCKS.remove(pos);

        ACTIVE_ANIMATIONS.entrySet().removeIf(entry -> {
            BlockPos rootPos = entry.getKey();
            TreeShakeAnimation anim = entry.getValue();

            if (anim.getStructure().contains(pos)) {
                boolean wasLog = !anim.getStructure().isLeaf(pos);
                anim.getStructure().removeBlock(pos);

                // If a structural trunk log was broken, cancel the entire tree animation
                // and restore all remaining blocks to chunk rendering immediately
                if (wasLog || anim.getStructure().isEmpty() || anim.getStructure().getAllBlocks().size() <= 1) {
                    for (BlockPos treePos : anim.getStructure().getBlockPositions()) {
                        HIDDEN_BLOCKS.remove(treePos);
                    }
                    UNHIDDEN_SETTLING_TREES.remove(rootPos);
                    markTreeSectionsDirty(level, anim.getStructure());
                    spawnLeavesBurst(level, anim.getStructure(), 12);
                    return true;
                }

                // If only a foliage leaf was broken, spawn burst and keep remaining intact
                spawnLeavesBurst(level, anim.getStructure(), 6);
            }
            return false;
        });
    }

    /**
     * Ticks active tree animations every client game tick and runs the watchdog
     * to eliminate any orphaned or expired hidden blocks.
     */
    public static void tick(ClientLevel level) {
        currentClientTick++;

        // Watchdog failsafe: automatically unhide any blocks whose lifespan expired
        // or whose parent animation was removed
        if (!HIDDEN_BLOCKS.isEmpty()) {
            Set<BlockPos> orphanedSectionsToDirty = new HashSet<>();
            HIDDEN_BLOCKS.entrySet().removeIf(entry -> {
                BlockPos blockPos = entry.getKey();
                HiddenBlockEntry info = entry.getValue();

                if (currentClientTick > info.expirationTick() || !ACTIVE_ANIMATIONS.containsKey(info.rootPos())) {
                    orphanedSectionsToDirty.add(blockPos);
                    return true;
                }
                return false;
            });

            for (BlockPos dirtyPos : orphanedSectionsToDirty) {
                level.setSectionDirtyWithNeighbors(
                        dirtyPos.getX() >> 4,
                        dirtyPos.getY() >> 4,
                        dirtyPos.getZ() >> 4
                );
            }
        }

        if (ACTIVE_ANIMATIONS.isEmpty()) {
            return;
        }

        ACTIVE_ANIMATIONS.entrySet().removeIf(entry -> {
            BlockPos rootPos = entry.getKey();
            TreeShakeAnimation anim = entry.getValue();

            anim.tick();

            // When entering the settling phase, unhide blocks immediately and mark dirty
            // so the static chunk mesh compiles seamlessly underneath while the animation
            // dynamically renders resting blocks
            if (anim.isSettling() && !UNHIDDEN_SETTLING_TREES.contains(rootPos)) {
                UNHIDDEN_SETTLING_TREES.add(rootPos);
                TreeStructure struct = anim.getStructure();

                for (BlockPos pos : struct.getBlockPositions()) {
                    HIDDEN_BLOCKS.remove(pos);
                }

                markTreeSectionsDirty(level, struct);
            }

            if (anim.isFinished()) {
                TreeStructure struct = anim.getStructure();
                for (BlockPos pos : struct.getBlockPositions()) {
                    HIDDEN_BLOCKS.remove(pos);
                }
                UNHIDDEN_SETTLING_TREES.remove(rootPos);
                markTreeSectionsDirty(level, struct);
                return true;
            }

            return false;
        });
    }

    /**
     * Marks chunk sections dirty with a 1-section neighborhood margin
     * so that boundary quads and neighbor AO are cleanly recompiled.
     */
    public static void markTreeSectionsDirty(ClientLevel level, TreeStructure struct) {
        if (level == null || struct == null) return;
        int minX = struct.getMinSectionX() - 1;
        int minY = Math.max(level.getMinSectionY(), struct.getMinSectionY() - 1);
        int minZ = struct.getMinSectionZ() - 1;
        int maxX = struct.getMaxSectionX() + 1;
        int maxY = Math.min(level.getMaxSectionY(), struct.getMaxSectionY() + 1);
        int maxZ = struct.getMaxSectionZ() + 1;
        level.setSectionRangeDirty(minX, minY, minZ, maxX, maxY, maxZ);
    }

    /**
     * Renders all active shaking trees.
     */
    public static void renderAll(PoseStack poseStack, SubmitNodeCollector collector, LevelRenderState levelRenderState) {
        if (ACTIVE_ANIMATIONS.isEmpty()) {
            return;
        }

        Minecraft client = Minecraft.getInstance();
        ClientLevel level = client.level;
        if (level == null) return;

        float partialTicks = client.getDeltaTracker().getGameTimeDeltaPartialTick(true);

        for (TreeShakeAnimation anim : ACTIVE_ANIMATIONS.values()) {
            anim.render(poseStack, collector, levelRenderState, partialTicks, level);
        }
    }

    /**
     * Clears all animations and hidden blocks on world unload or disconnect.
     */
    public static void clear() {
        Minecraft client = Minecraft.getInstance();
        ClientLevel level = client.level;
        if (level != null) {
            for (TreeShakeAnimation anim : ACTIVE_ANIMATIONS.values()) {
                markTreeSectionsDirty(level, anim.getStructure());
            }
        }

        ACTIVE_ANIMATIONS.clear();
        HIDDEN_BLOCKS.clear();
        UNHIDDEN_SETTLING_TREES.clear();
        currentClientTick = 0;
    }

    private static Vec3 calculatePunchVector(Player player, BlockPos pos, Direction direction) {
        if (direction != null && direction.getAxis().isHorizontal()) {
            return new Vec3(-direction.getStepX(), 0, -direction.getStepZ());
        }

        Vec3 look = player.getLookAngle();
        double x = look.x;
        double z = look.z;
        double len = Math.sqrt(x * x + z * z);
        if (len < 1e-4) {
            return new Vec3(0, 0, 1);
        }
        return new Vec3(x / len, 0, z / len);
    }

    private static void spawnPunchEffects(ClientLevel level, BlockPos hitPos, TreeStructure structure, boolean isFullPunch) {
        BlockState hitState = level.getBlockState(hitPos);
        RandomSource random = level.getRandom();

        // 1. Wood punch impact particles
        if (BlockPlaceConfig.enableTreeParticles && hitState.isSolid()) {
            Vec3 hitCenter = Vec3.atCenterOf(hitPos);
            int count = isFullPunch ? 6 : 3;
            for (int i = 0; i < count; i++) {
                double vx = (random.nextDouble() - 0.5) * 0.22;
                double vy = random.nextDouble() * 0.15 + 0.04;
                double vz = (random.nextDouble() - 0.5) * 0.22;
                level.addParticle(
                        new BlockParticleOption(ParticleTypes.BLOCK, hitState),
                        hitCenter.x + vx * 1.5,
                        hitCenter.y + vy,
                        hitCenter.z + vz * 1.5,
                        vx, vy, vz
                );
            }
        }

        // 2. Canopy leaf flutter particles
        if (BlockPlaceConfig.enableTreeParticles) {
            spawnLeavesBurst(level, structure, isFullPunch ? 5 : 2);
        }

        // 3. Audio feedback: wood impact + gentle leaves rustle
        if (BlockPlaceConfig.enableTreeSound) {
            float pitchOffset = random.nextFloat() * 0.2f;
            level.playLocalSound(
                    hitPos.getX() + 0.5,
                    hitPos.getY() + 0.5,
                    hitPos.getZ() + 0.5,
                    SoundEvents.WOOD_HIT,
                    SoundSource.PLAYERS,
                    isFullPunch ? 0.75f : 0.45f,
                    1.2f + pitchOffset,
                    false
            );
            level.playLocalSound(
                    hitPos.getX() + 0.5,
                    hitPos.getY() + 0.5,
                    hitPos.getZ() + 0.5,
                    SoundEvents.CHERRY_LEAVES_FALL,
                    SoundSource.PLAYERS,
                    isFullPunch ? 0.65f : 0.35f,
                    1.3f + pitchOffset,
                    false
            );
        }
    }

    private static void spawnLeavesBurst(ClientLevel level, TreeStructure structure, int count) {
        List<TreeBlockEntry> leaves = structure.getLeaves();
        if (leaves.isEmpty()) return;

        RandomSource random = level.getRandom();

        for (int i = 0; i < count; i++) {
            TreeBlockEntry leaf = leaves.get(random.nextInt(leaves.size()));
            BlockPos leafPos = leaf.pos();

            double px = leafPos.getX() + random.nextDouble();
            double py = leafPos.getY() - 0.05;
            double pz = leafPos.getZ() + random.nextDouble();

            double vx = (random.nextDouble() - 0.5) * 0.06;
            double vy = -0.03 - random.nextDouble() * 0.03;
            double vz = (random.nextDouble() - 0.5) * 0.06;

            level.addParticle(
                    new BlockParticleOption(ParticleTypes.BLOCK, leaf.state()),
                    px, py, pz,
                    vx, vy, vz
            );
        }
    }
}
