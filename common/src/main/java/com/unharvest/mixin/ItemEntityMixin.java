package com.unharvest.mixin;

import com.unharvest.UnharvestItems;
import com.unharvest.UnharvestManager;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Enforces a strict inventory lock in Unharvest worlds:
 * All dropped entities (crops, lanterns, fences, torches, etc.) are blocked and discarded.
 * Only the Withering Scythe is allowed.
 */
@Mixin(ItemEntity.class)
public abstract class ItemEntityMixin extends Entity {

    @Shadow
    public abstract ItemStack getItem();

    protected ItemEntityMixin(EntityType<?> entityType, Level level) {
        super(entityType, level);
    }

    @Inject(
            method = "playerTouch",
            at = @At("HEAD"),
            cancellable = true
    )
    private void unharvest$onPlayerTouch(Player player, CallbackInfo ci) {
        if (player != null && UnharvestManager.isUnharvestWorld(player.level())) {
            ItemStack item = this.getItem();
            if (!UnharvestItems.isWitheringScythe(item)) {
                this.discard();
                ci.cancel();
            }
        }
    }

    @Inject(
            method = "tick",
            at = @At("HEAD"),
            cancellable = true
    )
    private void unharvest$onTick(CallbackInfo ci) {
        if (UnharvestManager.isUnharvestWorld(this.level())) {
            ItemStack item = this.getItem();
            if (!UnharvestItems.isWitheringScythe(item)) {
                this.discard();
                ci.cancel();
            }
        }
    }
}

