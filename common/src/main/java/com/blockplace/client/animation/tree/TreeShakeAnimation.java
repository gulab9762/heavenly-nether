package com.blockplace.client.animation.tree;

import com.blockplace.client.config.BlockPlaceConfig;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.MovingBlockRenderState;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;

/**
 * Manages the client-side cartoonish wobble, inverse pendulum sway, squash and stretch,
 * and foliage flutter for a shaking tree.
 */
public class TreeShakeAnimation {

    private final TreeStructure structure;
    private Vec3 punchDir;
    private Vec3 crossDir;

    private int ageTicks = 0;
    private final int maxDurationTicks;
    private final int settleDurationTicks = 3;

    private float impulseStrength = 1.0f;
    private int ticksSinceLastPunch = 0;

    private boolean settling = false;
    private boolean finished = false;

    // Single reusable state for rendering moving blocks with zero GC allocations per frame
    private final MovingBlockRenderState movingBlockState = new MovingBlockRenderState();

    public TreeShakeAnimation(ClientLevel level, TreeStructure structure, Vec3 punchDir) {
        this.structure = structure;
        this.maxDurationTicks = Math.max(8, BlockPlaceConfig.treeShakeDurationTicks);

        setPunchDirection(punchDir);

        this.movingBlockState.randomSeedPos = structure.getRootPos();
        this.movingBlockState.blockPos = structure.getRootPos();
        this.movingBlockState.cardinalLighting = level.cardinalLighting();
        this.movingBlockState.lightEngine = level.getLightEngine();
    }

    private void setPunchDirection(Vec3 dir) {
        double x = dir.x;
        double z = dir.z;
        double len = Math.sqrt(x * x + z * z);
        if (len < 1e-4) {
            x = 0;
            z = 1;
        } else {
            x /= len;
            z /= len;
        }
        this.punchDir = new Vec3(x, 0, z);
        // Perpendicular vector in the horizontal plane
        this.crossDir = new Vec3(-z, 0, x);
    }

    public TreeStructure getStructure() {
        return structure;
    }

    public boolean isSettling() {
        return settling;
    }

    public boolean isFinished() {
        return finished;
    }

    /**
     * Adds fresh momentum and resets decay when punched again.
     */
    public void punch(Vec3 newPunchDir) {
        if (newPunchDir.lengthSqr() > 1e-4) {
            // Blend previous direction with new punch direction
            Vec3 blended = this.punchDir.scale(0.35).add(newPunchDir.scale(0.65));
            setPunchDirection(blended);
        }

        this.impulseStrength = Math.min(1.7f, this.impulseStrength + 0.35f);
        this.ageTicks = Math.min(2, this.ageTicks);
        this.settling = false;
        this.ticksSinceLastPunch = 0;
    }

    /**
     * Checks if enough ticks have passed to accept another punch pulse while mining.
     */
    public boolean canReceiveMiningPunch() {
        return this.ticksSinceLastPunch >= 5;
    }

    public void tick() {
        if (finished) return;

        ticksSinceLastPunch++;
        ageTicks++;

        if (ageTicks >= maxDurationTicks) {
            settling = true;
        }

        if (ageTicks >= maxDurationTicks + settleDurationTicks) {
            finished = true;
        }
    }

    public void render(PoseStack poseStack, SubmitNodeCollector collector, LevelRenderState levelRenderState, float partialTicks, ClientLevel level) {
        if (structure.isEmpty()) return;

        float p;
        if (settling) {
            p = 1.0f;
        } else {
            p = Math.min(1.0f, (ageTicks + partialTicks) / (float) maxDurationTicks);
        }

        float wobbleMain = 0.0f;
        float wobbleCross = 0.0f;
        float tiltAngle = 0.0f;
        float squashY = 1.0f;
        float stretchXZ = 1.0f;

        if (p < 1.0f) {
            float decay = (1.0f - p) * (1.0f - p) * impulseStrength * BlockPlaceConfig.treeShakeIntensity;

            // Damped spring oscillation: 2 full back-and-forth cartoon bounces
            wobbleMain = (float) Math.sin(p * 4.0 * Math.PI) * decay;

            // Asymmetric secondary cross-wobble for delightful 3D organic figure-8 jiggle
            wobbleCross = (float) (Math.sin(p * 3.0 * Math.PI) * Math.cos(p * 2.0 * Math.PI)) * decay * 0.38f;

            // Rotational tilt in degrees
            tiltAngle = wobbleMain * 4.2f * BlockPlaceConfig.treeShakeIntensity;

            // Cartoon squash and stretch: compresses vertically on impact, stretches on rebound
            squashY = 1.0f - 0.09f * Math.abs(wobbleMain) * BlockPlaceConfig.treeShakeIntensity;
            stretchXZ = 1.0f + 0.045f * Math.abs(wobbleMain) * BlockPlaceConfig.treeShakeIntensity;
        }

        Vec3 cameraPos = levelRenderState.cameraRenderState.pos;
        BlockPos rootPos = structure.getRootPos();
        Vec3 rootCenter = Vec3.atCenterOf(rootPos);

        double rootCamX = rootCenter.x - cameraPos.x();
        double rootCamY = rootCenter.y - cameraPos.y();
        double rootCamZ = rootCenter.z - cameraPos.z();

        for (TreeBlockEntry entry : structure.getAllBlocks()) {
            BlockPos pos = entry.pos();
            Vec3 blockCenter = Vec3.atCenterOf(pos);

            double relX = blockCenter.x - rootCenter.x;
            double relY = blockCenter.y - rootCenter.y;
            double relZ = blockCenter.z - rootCenter.z;

            float h = entry.heightFraction();
            float bendFactor = (float) Math.pow(h, 1.25);

            double swayX = (punchDir.x * wobbleMain + crossDir.x * wobbleCross) * 0.40 * bendFactor;
            double swayZ = (punchDir.z * wobbleMain + crossDir.z * wobbleCross) * 0.40 * bendFactor;

            double leafFlutterX = 0;
            double leafFlutterY = 0;
            double leafFlutterZ = 0;
            float leafScale = 1.0f;

            if (entry.isLeaf() && p < 1.0f) {
                int seed = entry.randomSeed();
                float leafPhase = (seed % 100) * 0.1f;
                float flutterEnv = (1.0f - p) * 0.055f;
                leafFlutterX = Math.sin(p * 45.0 + leafPhase) * flutterEnv;
                leafFlutterY = Math.cos(p * 36.0 + leafPhase) * flutterEnv * 0.65;
                leafFlutterZ = Math.sin(p * 52.0 + leafPhase * 1.4) * flutterEnv;
                leafScale = 1.0f + (float) Math.sin(p * 32.0 + leafPhase) * (1.0f - p) * 0.07f;
            }

            double renderX = rootCamX + relX * stretchXZ + swayX + leafFlutterX;
            double renderY = rootCamY + relY * squashY + leafFlutterY;
            double renderZ = rootCamZ + relZ * stretchXZ + swayZ + leafFlutterZ;

            poseStack.pushPose();

            poseStack.translate(renderX, renderY, renderZ);

            if (tiltAngle != 0.0f) {
                float blockTilt = tiltAngle * bendFactor;
                Quaternionf tiltQuat = new Quaternionf().rotationAxis(
                        (float) Math.toRadians(blockTilt),
                        (float) crossDir.x,
                        0.0f,
                        (float) crossDir.z
                );
                poseStack.rotate(tiltQuat);
            }

            if (leafScale != 1.0f) {
                poseStack.scale(leafScale, leafScale, leafScale);
            }

            poseStack.translate(-0.5, -0.5, -0.5);

            movingBlockState.randomSeedPos = pos;
            movingBlockState.blockPos = pos;
            movingBlockState.blockState = entry.state();
            movingBlockState.biome = level.getBiome(pos);

            collector.submitMovingBlock(poseStack, movingBlockState, 0);

            poseStack.popPose();
        }
    }
}
