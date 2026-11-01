package com.unharvest.fabric.client;

import com.unharvest.Constants;
import com.unharvest.UnharvestCommands;
import com.unharvest.UnharvestManager;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.Minecraft;

public class UnharvestFabricClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        Constants.LOG.info("Initializing {} on Fabric Client", Constants.MOD_NAME);

        // Register Keybindings
        KeyMappingHelper.registerKeyMapping(UnharvestManager.UPGRADE_KEY);
        KeyMappingHelper.registerKeyMapping(UnharvestManager.SWARM_KEY);

        // Tick Unharvest gameplay loop every client game tick
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.level != null && !client.isPaused()) {
                UnharvestManager.tick(client.level, client.player);
            }
        });

        // Reset world cache on disconnect
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            UnharvestManager.clearWorldCache();
        });

        // Register client commands
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            dispatcher.register(
                    ClientCommands.literal("unharvest")
                            .then(ClientCommands.literal("upgrades")
                                    .executes(context -> UnharvestCommands.openUpgrades(Minecraft.getInstance().player))
                            )
                            .then(ClientCommands.literal("scythe")
                                    .executes(context -> UnharvestCommands.giveScythe(Minecraft.getInstance().player))
                            )
                            .then(ClientCommands.literal("reset")
                                    .executes(context -> UnharvestCommands.reset(Minecraft.getInstance().player))
                            )
                            .then(ClientCommands.literal("rescan")
                                    .executes(context -> UnharvestCommands.rescanField(Minecraft.getInstance().player))
                            )
                            .then(ClientCommands.literal("regenerate")
                                    .executes(context -> UnharvestCommands.regenerateField(Minecraft.getInstance().player))
                            )
                            // [DEBUG COMMAND - Comment out before final production release]
                            .then(ClientCommands.literal("essence")
                                    .then(ClientCommands.argument("amount", com.mojang.brigadier.arguments.LongArgumentType.longArg(1))
                                            .executes(context -> UnharvestCommands.giveEssence(
                                                    Minecraft.getInstance().player,
                                                    com.mojang.brigadier.arguments.LongArgumentType.getLong(context, "amount")
                                            ))
                                    )
                                    .executes(context -> UnharvestCommands.giveEssence(Minecraft.getInstance().player, 1000L))
                            )
                            .executes(context -> UnharvestCommands.openUpgrades(Minecraft.getInstance().player))
            );
        });
    }
}

