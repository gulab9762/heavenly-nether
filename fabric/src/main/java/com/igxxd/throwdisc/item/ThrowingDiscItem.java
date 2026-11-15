package com.igxxd.throwdisc.item;

import com.igxxd.throwdisc.entity.ThrownDiscEntity;
import com.igxxd.throwdisc.init.ModEnchantments;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;

import java.util.function.Consumer;

public class ThrowingDiscItem extends Item {
    private final String tierName;
    private final float damage;
    private final float knockback;
    private final int baseBounces;

    public ThrowingDiscItem(Properties properties, String tierName, float damage, float knockback, int baseBounces) {
        super(properties.stacksTo(1));
        this.tierName = tierName;
        this.damage = damage;
        this.knockback = knockback;
        this.baseBounces = baseBounces;
    }

    public String getTierName() {
        return tierName;
    }

    public float getDamage() {
        return damage;
    }

    public float getKnockback() {
        return knockback;
    }

    public int getBaseBounces() {
        return baseBounces;
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        boolean thrown = throwDisc(player, hand);
        return thrown ? InteractionResult.SUCCESS : InteractionResult.PASS;
    }

    public static boolean throwDisc(Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!(stack.getItem() instanceof ThrowingDiscItem discItem)) {
            return false;
        }

        Level level = player.level();
        if (level.isClientSide()) {
            player.swing(hand, SwingAnimation.DEFAULT, true);
            return true;
        }

        if (player.getCooldowns().isOnCooldown(stack)) {
            return false;
        }

        int bounceBonus = ModEnchantments.getBounceLevel(stack);
        boolean hasSplit = ModEnchantments.hasSplit(stack);
        int totalBounces = discItem.getBaseBounces() + bounceBonus;

        ItemStack thrownStack = stack.copy();
        if (!player.getAbilities().instabuild && thrownStack.isDamageableItem()) {
            EquipmentSlot slot = hand == InteractionHand.MAIN_HAND ? EquipmentSlot.MAINHAND : EquipmentSlot.OFFHAND;
            thrownStack.hurtAndBreak(1, (ServerPlayer) player, slot);
        }

        ThrownDiscEntity discEntity = new ThrownDiscEntity(
            level,
            player,
            thrownStack,
            discItem.getDamage(),
            discItem.getKnockback(),
            totalBounces,
            false,
            hasSplit
        );

        discEntity.shootFromRotation(player, player.getXRot(), player.getYRot(), 0.0f, 1.6f, 0.5f);
        level.addFreshEntity(discEntity);

        level.playSound(
            null,
            player.getX(),
            player.getY(),
            player.getZ(),
            SoundEvents.TRIDENT_THROW,
            SoundSource.PLAYERS,
            0.9f,
            1.4f
        );

        player.getCooldowns().addCooldown(stack, 10);
        player.swing(hand, SwingAnimation.DEFAULT, true);

        if (!player.getAbilities().instabuild) {
            stack.shrink(1);
        }

        return true;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, display, tooltip, flag);
        tooltip.accept(Component.literal("§7Damage: §c+" + damage));
        int bonus = ModEnchantments.getBounceLevel(stack);
        if (bonus > 0) {
            tooltip.accept(Component.literal("§7Bounces: §b" + (baseBounces + bonus) + " (" + baseBounces + " + " + bonus + ")"));
        } else {
            tooltip.accept(Component.literal("§7Base Bounces: §b" + baseBounces));
        }
        tooltip.accept(Component.literal("§7Knockback: §a" + knockback));
        if (ModEnchantments.hasSplit(stack)) {
            tooltip.accept(Component.literal("§dSplit: 50% chance on hit"));
        }
    }
}
