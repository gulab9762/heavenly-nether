package com.blockplace.mixin;

import com.blockplace.client.animation.tree.TreeShakeManager;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BlockEntityRenderDispatcher.class)
public abstract class BlockEntityRenderDispatcherMixin {

    @Inject(
            method = "tryExtractRenderState",
            at = @At("HEAD"),
            cancellable = true
    )
    private <E extends BlockEntity, S extends BlockEntityRenderState> void blockplace$hideBlockEntity(
            E blockEntity,
            float partialTick,
            ModelFeatureRenderer.CrumblingOverlay crumblingOverlay,
            boolean bl,
            CallbackInfoReturnable<S> cir
    ) {
        if (TreeShakeManager.isBlockHidden(blockEntity.getBlockPos())) {
            cir.setReturnValue(null);
        }
    }
}
