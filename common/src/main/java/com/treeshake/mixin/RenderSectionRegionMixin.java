package com.treeshake.mixin;

import com.treeshake.client.animation.tree.TreeShakeManager;
import net.minecraft.client.renderer.chunk.RenderSectionRegion;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(RenderSectionRegion.class)
public abstract class RenderSectionRegionMixin {

    @Inject(
            method = "getBlockState",
            at = @At("HEAD"),
            cancellable = true
    )
    private void treeshake$getHiddenBlockState(BlockPos pos, CallbackInfoReturnable<BlockState> cir) {
        BlockState hiddenTreeState = TreeShakeManager.getHiddenBlockReplacedState(pos);
        if (hiddenTreeState != null) {
            cir.setReturnValue(hiddenTreeState);
        }
    }
}
