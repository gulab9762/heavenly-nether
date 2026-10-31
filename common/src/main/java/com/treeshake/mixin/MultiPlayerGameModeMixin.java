package com.treeshake.mixin;

import com.treeshake.client.animation.tree.TreeShakeManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.phys.BlockHitResult;
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
    private void treeshake$onStartDestroyBlock(BlockPos pos, Direction direction, CallbackInfoReturnable<Boolean> cir) {
        if (this.minecraft.level != null && this.minecraft.player != null) {
            TreeShakeManager.onPunchBlock(this.minecraft.level, this.minecraft.player, pos, direction);
        }
    }

    @Inject(
            method = "continueDestroyBlock",
            at = @At("HEAD")
    )
    private void treeshake$onContinueDestroyBlock(BlockPos pos, Direction direction, CallbackInfoReturnable<Boolean> cir) {
        if (this.minecraft.level != null && this.minecraft.player != null) {
            TreeShakeManager.onContinuousPunch(this.minecraft.level, this.minecraft.player, pos, direction);
        }
    }

    @Inject(
            method = "destroyBlock",
            at = @At("HEAD")
    )
    private void treeshake$onDestroyBlock(BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        if (this.minecraft.level != null) {
            TreeShakeManager.onBlockDestroyed(this.minecraft.level, pos);
        }
    }

    @Inject(
            method = "useItemOn",
            at = @At("HEAD")
    )
    private void treeshake$onUseItemOn(LocalPlayer player, InteractionHand hand, BlockHitResult hitResult, CallbackInfoReturnable<InteractionResult> cir) {
        if (this.minecraft.level != null && player != null && hitResult != null) {
            TreeShakeManager.onRightClickBlock(this.minecraft.level, player, hand, hitResult.getBlockPos(), hitResult.getDirection());
        }
    }
}
