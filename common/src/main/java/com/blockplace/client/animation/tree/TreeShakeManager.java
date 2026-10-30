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
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Global manager for tracking, ticking, hiding, and rendering client-side cartoon tree shake animations.
 */
public class TreeShakeManager {

    private static final Map<BlockPos, TreeShakeAnimation> ACTIVE_ANIMATIONS = new ConcurrentHashMap<>();
    private static final Map<BlockPos, BlockState> HIDDEN_BLOCKS = new ConcurrentHashMap<>();
    private static final Set<BlockPos> UNHIDDEN_SETTLING_TREES = ConcurrentHashMap.newKeySet();

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
        return HIDDEN_BLOCKS.get(pos);
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
                spawnPunchEffects(level, pos, anim.getStructure(), true);
                return;
            }
        }

        // Detect if this block is part of a natural tree
        TreeStructure structure = TreeDetector.findTree(level, pos);
        if (structure == null) {
            return;
        }

        // Hide all tree blocks from baked chunk rendering
        for (BlockPos treePos : structure.getBlockPositions()) {
            HIDDEN_BLOCKS.put(treePos, Blocks.AIR.defaultBlockState());
        }

        // Mark chunk sections dirty so they compile without the tree
        level.setSectionRangeDirty(
                structure.getMinSectionX(),
                structure.getMinSectionY(),
                structure.getMinSectionZ(),
                structure.getMaxSectionX(),
                structure.getMaxSectionY(),
                structure.getMaxSectionZ()
        );

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
                spawnPunchEffects(level, pos, anim.getStructure(), false);
                return;
            }
        }
    }

    /**
     * Called when a block is broken in the level.
     */
    public static void onBlockDestroyed(ClientLevel level, BlockPos pos) {
        HIDDEN_BLOCKS.remove(pos);

        for (Map.Entry<BlockPos, TreeShakeAnimation> entry : ACTIVE_ANIMATIONS.entrySet()) {
            TreeShakeAnimation anim = entry.getValue();
            if (anim.getStructure().contains(pos)) {
                anim.getStructure().removeBlock(pos);
                // Extra leaf burst when a block breaks
                spawnLeavesBurst(level, anim.getStructure(), 10);
                if (anim.getStructure().isEmpty()) {
                    ACTIVE_ANIMATIONS.remove(entry.getKey());
                    UNHIDDEN_SETTLING_TREES.remove(entry.getKey());
                }
                break;
            }
        }
    }

    /**
     * Ticks active tree animations every client game tick.
     */
    public static void tick(ClientLevel level) {
        if (ACTIVE_ANIMATIONS.isEmpty()) {
            return;
        }

        ACTIVE_ANIMATIONS.entrySet().removeIf(entry -> {
            BlockPos rootPos = entry.getKey();
            TreeShakeAnimation anim = entry.getValue();

            anim.tick();

            // When entering the settling phase, unhide blocks immediately and mark dirty
            // so the static chunk mesh compiles seamlessly underneath
            if (anim.isSettling() && !UNHIDDEN_SETTLING_TREES.contains(rootPos)) {
                UNHIDDEN_SETTLING_TREES.add(rootPos);
                TreeStructure struct = anim.getStructure();

                for (BlockPos pos : struct.getBlockPositions()) {
                    HIDDEN_BLOCKS.remove(pos);
                }

                level.setSectionRangeDirty(
                        struct.getMinSectionX(),
                        struct.getMinSectionY(),
                        struct.getMinSectionZ(),
                        struct.getMaxSectionX(),
                        struct.getMaxSectionY(),
                        struct.getMaxSectionZ()
                );
            }

            if (anim.isFinished()) {
                UNHIDDEN_SETTLING_TREES.remove(rootPos);
                return true;
            }

            return false;
        });
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
     * Clears all animations on world unload or disconnect.
     */
    public static void clear() {
        ACTIVE_ANIMATIONS.clear();
        HIDDEN_BLOCKS.clear();
        UNHIDDEN_SETTLING_TREES.clear();
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
