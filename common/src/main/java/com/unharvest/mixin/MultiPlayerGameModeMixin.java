package com.unharvest.mixin;

import com.unharvest.UnharvestItems;
import com.unharvest.UnharvestManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
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
            at = @At("HEAD"),
            cancellable = true
    )
    private void unharvest$onStartDestroyBlock(BlockPos pos, Direction direction, CallbackInfoReturnable<Boolean> cir) {
        if (this.minecraft.level != null && this.minecraft.player != null) {
            if (UnharvestManager.onLeftClickBlock(this.minecraft.level, this.minecraft.player, pos, direction)) {
                cir.setReturnValue(true);
            }
        }
    }

    @Inject(
            method = "continueDestroyBlock",
            at = @At("HEAD"),
            cancellable = true
    )
    private void unharvest$onContinueDestroyBlock(BlockPos pos, Direction direction, CallbackInfoReturnable<Boolean> cir) {
        if (this.minecraft.level != null && this.minecraft.player != null) {
            if (UnharvestManager.onLeftClickBlock(this.minecraft.level, this.minecraft.player, pos, direction)) {
                cir.setReturnValue(true);
            }
        }
    }

    @Inject(
            method = "destroyBlock",
            at = @At("HEAD"),
            cancellable = true
    )
    private void unharvest$onDestroyBlock(BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        if (this.minecraft.level != null && this.minecraft.player != null) {
            ItemStack held = this.minecraft.player.getMainHandItem();
            if (UnharvestItems.isWitheringScythe(held)) {
                BlockState state = this.minecraft.level.getBlockState(pos);
                if (UnharvestManager.isCrop(state) || state.is(Blocks.WITHER_ROSE) || state.is(Blocks.FARMLAND)) {
                    cir.setReturnValue(false);
                }
            }
        }
    }

    @Inject(
            method = "useItemOn",
            at = @At("HEAD"),
            cancellable = true
    )
    private void unharvest$onUseItemOn(LocalPlayer player, InteractionHand hand, BlockHitResult hitResult, CallbackInfoReturnable<InteractionResult> cir) {
        if (this.minecraft.level != null && player != null) {
            if (UnharvestManager.onRightClick(this.minecraft.level, player, hand)) {
                cir.setReturnValue(InteractionResult.SUCCESS);
            }
        }
    }

    @Inject(
            method = "useItem",
            at = @At("HEAD"),
            cancellable = true
    )
    private void unharvest$onUseItem(Player player, InteractionHand hand, CallbackInfoReturnable<InteractionResult> cir) {
        if (this.minecraft.level != null && player != null) {
            if (UnharvestManager.onRightClick(this.minecraft.level, player, hand)) {
                cir.setReturnValue(InteractionResult.SUCCESS);
            }
        }
    }
}
