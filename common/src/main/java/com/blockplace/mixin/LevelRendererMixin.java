package com.blockplace.mixin;

import com.blockplace.client.animation.BlockPlacementManager;
import com.blockplace.client.animation.tree.TreeShakeManager;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelRenderer.class)
public abstract class LevelRendererMixin {

    @Inject(
            method = "submitTransientBlocks",
            at = @At("TAIL")
    )
    private void blockplace$renderActiveAnimations(PoseStack poseStack, SubmitNodeCollector submitNodeCollector, LevelRenderState levelRenderState, CallbackInfo ci) {
        BlockPlacementManager.renderAll(poseStack, submitNodeCollector, levelRenderState);
        TreeShakeManager.renderAll(poseStack, submitNodeCollector, levelRenderState);
    }
}
