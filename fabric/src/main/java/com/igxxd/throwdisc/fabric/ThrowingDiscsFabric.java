package com.igxxd.throwdisc.fabric;

import com.igxxd.throwdisc.Constants;
import com.igxxd.throwdisc.init.ModEntities;
import com.igxxd.throwdisc.init.ModItems;
import com.igxxd.throwdisc.item.ThrowingDiscItem;
import com.igxxd.throwdisc.network.ThrowDiscPayload;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.CreativeModeTabs;

public class ThrowingDiscsFabric implements ModInitializer {
    @Override
    public void onInitialize() {
        Constants.LOG.info("Initializing {}", Constants.MOD_NAME);

        ModItems.init();
        ModEntities.init();

        // Add items to Creative Combat tab
        CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.COMBAT).register(entries -> {
            entries.accept(ModItems.COPPER_THROWING_DISC);
            entries.accept(ModItems.IRON_THROWING_DISC);
            entries.accept(ModItems.GOLD_THROWING_DISC);
            entries.accept(ModItems.DIAMOND_THROWING_DISC);
            entries.accept(ModItems.NETHERITE_THROWING_DISC);
        });

        // Register custom network payload for left-click throwing
        PayloadTypeRegistry.serverboundPlay().register(ThrowDiscPayload.TYPE, ThrowDiscPayload.STREAM_CODEC);

        ServerPlayNetworking.registerGlobalReceiver(ThrowDiscPayload.TYPE, (payload, context) -> {
            context.server().execute(() -> {
                ThrowingDiscItem.throwDisc(context.player(), InteractionHand.MAIN_HAND);
            });
        });

        // If player left clicks an entity directly with disc in hand, throw it!
        AttackEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
            if (!world.isClientSide() && player.getItemInHand(hand).getItem() instanceof ThrowingDiscItem) {
                boolean thrown = ThrowingDiscItem.throwDisc(player, hand);
                if (thrown) {
                    return InteractionResult.SUCCESS;
                }
            }
            return InteractionResult.PASS;
        });
    }
}
