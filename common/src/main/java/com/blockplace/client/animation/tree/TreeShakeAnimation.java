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
 * stem ripples, and foliage flutter for a shaking tree.
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

    public TreeShakeAnimation(ClientLevel level, TreeStructure structure, Vec3 punchDir) {
        this.structure = structure;
        this.maxDurationTicks = Math.max(8, BlockPlaceConfig.treeShakeDurationTicks);

        setPunchDirection(punchDir);
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
            Vec3 blended = this.punchDir.scale(0.35).add(newPunchDir.scale(0.65));
            setPunchDirection(blended);
        }

        this.impulseStrength = Math.min(1.8f, this.impulseStrength + 0.40f);
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
        float tiltAngleRad = 0.0f;
        float squashY = 1.0f;
        float stretchXZ = 1.0f;

        if (p < 1.0f) {
            float decay = (1.0f - p) * (1.0f - p) * impulseStrength * BlockPlaceConfig.treeShakeIntensity;

            // Damped spring oscillation: 2 full back-and-forth cartoon bounces
            wobbleMain = (float) Math.sin(p * 4.0 * Math.PI) * decay;

            // Asymmetric secondary cross-wobble for delightful 3D organic figure-8 jiggle
            wobbleCross = (float) (Math.sin(p * 3.0 * Math.PI) * Math.cos(p * 2.0 * Math.PI)) * decay * 0.38f;

            // Unified tilt angle around root anchor
            tiltAngleRad = (float) Math.toRadians(wobbleMain * 5.2f * BlockPlaceConfig.treeShakeIntensity);

            // Cartoon squash and stretch: compresses vertically on impact, stretches on rebound
            squashY = 1.0f - 0.11f * Math.abs(wobbleMain) * BlockPlaceConfig.treeShakeIntensity;
            stretchXZ = 1.0f + 0.055f * Math.abs(wobbleMain) * BlockPlaceConfig.treeShakeIntensity;
        }

        Vec3 cameraPos = levelRenderState.cameraRenderState.pos;
        BlockPos rootPos = structure.getRootPos();
        BlockPos hitPos = structure.getHitPos();
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
            boolean isGroundedLog = !entry.isLeaf() && (!structure.contains(pos.below()) || h <= 0.001f);

            // Ground anchor factor: ground-touching trunk logs stay 100% anchored with zero gap underneath
            float anchor = isGroundedLog ? 0.0f : (float) Math.pow(Math.min(1.0f, h), 1.25);
            float swayFactor = isGroundedLog ? 0.0f : (float) Math.pow(Math.min(1.0f, h), 1.10);

            // Direct punch impact shockwave at the punched height, anchored at ground
            float punchDistY = Math.abs(pos.getY() - hitPos.getY());
            float impactRecoil = 0.0f;
            if (p < 1.0f && anchor > 0.0f) {
                impactRecoil = (float) Math.exp(-0.35f * punchDistY) * (1.0f - p) * 0.12f * (float) Math.sin(p * 8.0 * Math.PI) * anchor;
            }

            // High-frequency ripple traveling up the stem / trunk, anchored at ground
            float ripple = 0.0f;
            if (p < 1.0f && anchor > 0.0f) {
                ripple = (float) Math.sin(p * 7.0 * Math.PI - relY * 0.65) * (1.0f - p) * 0.045f * anchor;
            }

            // Continuous trunk curvature around tree base anchor
            double tiltX = relY * Math.sin(tiltAngleRad) * punchDir.x;
            double tiltZ = relY * Math.sin(tiltAngleRad) * punchDir.z;
            double tiltY = relY * (Math.cos(tiltAngleRad) - 1.0);

            double mainSway = (wobbleMain * 0.42 * swayFactor) + impactRecoil + ripple;
            double crossSway = (wobbleCross * 0.35 * swayFactor);

            double swayX = punchDir.x * mainSway + crossDir.x * crossSway + tiltX;
            double swayZ = punchDir.z * mainSway + crossDir.z * crossSway + tiltZ;
            double swayY = tiltY * anchor;

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
            double renderY = rootCamY + relY * squashY + swayY + leafFlutterY;
            double renderZ = rootCamZ + relZ * stretchXZ + swayZ + leafFlutterZ;

            poseStack.pushPose();

            poseStack.translate(renderX, renderY, renderZ);

            if (tiltAngleRad != 0.0f && anchor > 0.0f) {
                float blockTiltDeg = (float) Math.toDegrees(tiltAngleRad) * anchor;
                Quaternionf tiltQuat = new Quaternionf().rotationAxis(
                        (float) Math.toRadians(blockTiltDeg),
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

            collector.submitMovingBlock(poseStack, entry.renderState(), 0);

            poseStack.popPose();
        }
    }
}
