package com.unharvest.neoforge;

import com.unharvest.Constants;
import com.unharvest.UnharvestCommands;
import com.unharvest.UnharvestCommon;
import com.unharvest.UnharvestManager;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.Commands;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;

@Mod(Constants.MOD_ID)
public class UnharvestNeoForge {

    public UnharvestNeoForge(IEventBus modBus) {
        UnharvestCommon.init();
        modBus.addListener(RegisterKeyMappingsEvent.class, event -> {
            event.register(UnharvestManager.UPGRADE_KEY);
            event.register(UnharvestManager.SWARM_KEY);
        });
    }

    @EventBusSubscriber(modid = Constants.MOD_ID, value = Dist.CLIENT)
    public static class ClientEvents {

        @SubscribeEvent
        public static void onClientTick(ClientTickEvent.Post event) {
            Minecraft client = Minecraft.getInstance();
            if (client.level != null && !client.isPaused()) {
                UnharvestManager.tick(client.level, client.player);
            }
        }

        @SubscribeEvent
        public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
            UnharvestManager.clearWorldCache();
        }

        @SubscribeEvent
        public static void onRegisterClientCommands(RegisterClientCommandsEvent event) {
            event.getDispatcher().register(
                    Commands.literal("unharvest")
                            .then(Commands.literal("upgrades")
                                    .executes(context -> UnharvestCommands.openUpgrades(Minecraft.getInstance().player))
                            )
                            .then(Commands.literal("scythe")
                                    .executes(context -> UnharvestCommands.giveScythe(Minecraft.getInstance().player))
                            )
                            .then(Commands.literal("reset")
                                    .then(Commands.literal("timer")
                                            .executes(context -> UnharvestCommands.resetTimer(Minecraft.getInstance().player))
                                    )
                                    .executes(context -> UnharvestCommands.reset(Minecraft.getInstance().player))
                            )
                            .then(Commands.literal("timer")
                                    .then(Commands.literal("reset")
                                            .executes(context -> UnharvestCommands.resetTimer(Minecraft.getInstance().player))
                                    )
                                    .executes(context -> UnharvestCommands.resetTimer(Minecraft.getInstance().player))
                            )
                            .then(Commands.literal("rescan")
                                    .executes(context -> UnharvestCommands.rescanField(Minecraft.getInstance().player))
                            )
                            .then(Commands.literal("regenerate")
                                    .executes(context -> UnharvestCommands.regenerateField(Minecraft.getInstance().player))
                            )
                            .then(Commands.literal("victory")
                                    .executes(context -> UnharvestCommands.showVictory(Minecraft.getInstance().player))
                            )
                            .then(Commands.literal("stats")
                                    .executes(context -> UnharvestCommands.showVictory(Minecraft.getInstance().player))
                            )
                            // [DEBUG COMMAND - Comment out before final production release]
                            .then(Commands.literal("essence")
                                    .then(Commands.argument("amount", com.mojang.brigadier.arguments.LongArgumentType.longArg(1))
                                            .executes(context -> UnharvestCommands.giveEssence(
                                                    Minecraft.getInstance().player,
                                                    com.mojang.brigadier.arguments.LongArgumentType.getLong(context, "amount")
                                            ))
                                    )
                                    .executes(context -> UnharvestCommands.giveEssence(Minecraft.getInstance().player, 1000L))
                            )
                            .executes(context -> UnharvestCommands.openUpgrades(Minecraft.getInstance().player))
            );
        }
    }
}

