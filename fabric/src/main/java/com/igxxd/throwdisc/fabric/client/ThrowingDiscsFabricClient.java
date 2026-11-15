package com.igxxd.throwdisc.fabric.client;

import com.igxxd.throwdisc.init.ModEntities;
import com.igxxd.throwdisc.item.ThrowingDiscItem;
import com.igxxd.throwdisc.network.ThrowDiscPayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.minecraft.client.renderer.entity.ThrownItemRenderer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.SwingAnimation;

public class ThrowingDiscsFabricClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        // Register 3D entity item renderer
        EntityRendererRegistry.register(ModEntities.THROWN_DISC, ThrownItemRenderer::new);

        // Left-click in air handling
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player != null && client.gui != null && client.gui.screen() == null) {
                while (client.options.keyAttack.consumeClick()) {
                    ItemStack stack = client.player.getMainHandItem();
                    if (stack.getItem() instanceof ThrowingDiscItem) {
                        if (!client.player.getCooldowns().isOnCooldown(stack)) {
                            ClientPlayNetworking.send(new ThrowDiscPayload());
                            client.player.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
                            client.player.getCooldowns().addCooldown(stack, 10);
                        }
                    }
                }
            }
        });

        // Left-click on block handling
        AttackBlockCallback.EVENT.register((player, world, hand, pos, direction) -> {
            ItemStack stack = player.getItemInHand(hand);
            if (stack.getItem() instanceof ThrowingDiscItem) {
                if (!player.getCooldowns().isOnCooldown(stack)) {
                    if (world.isClientSide()) {
                        ClientPlayNetworking.send(new ThrowDiscPayload());
                        player.swing(hand, SwingAnimation.DEFAULT, true);
                        player.getCooldowns().addCooldown(stack, 10);
                    }
                    return InteractionResult.SUCCESS;
                }
            }
            return InteractionResult.PASS;
        });
    }
}
