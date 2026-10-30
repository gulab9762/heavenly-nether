package com.blockplace.mixin;

import com.blockplace.client.animation.tree.TreeShakeManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MultiPlayerGameMode.class)
public abstract class MultiPlayerGameModeMixin {

    @Shadow
    @Final
    private Minecraft minecraft;

    @Inject(
            method = "startDestroyBlock",
            at = @At("HEAD")
    )
    private void blockplace$onStartDestroyBlock(BlockPos pos, Direction direction, CallbackInfoReturnable<Boolean> cir) {
        if (this.minecraft.level != null && this.minecraft.player != null) {
            TreeShakeManager.onPunchBlock(this.minecraft.level, this.minecraft.player, pos, direction);
        }
    }

    @Inject(
            method = "continueDestroyBlock",
            at = @At("HEAD")
    )
    private void blockplace$onContinueDestroyBlock(BlockPos pos, Direction direction, CallbackInfoReturnable<Boolean> cir) {
        if (this.minecraft.level != null && this.minecraft.player != null) {
            TreeShakeManager.onContinuousPunch(this.minecraft.level, this.minecraft.player, pos, direction);
        }
    }

    @Inject(
            method = "destroyBlock",
            at = @At("HEAD")
    )
    private void blockplace$onDestroyBlock(BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        if (this.minecraft.level != null) {
            TreeShakeManager.onBlockDestroyed(this.minecraft.level, pos);
        }
    }
}
