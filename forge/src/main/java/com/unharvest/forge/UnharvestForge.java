package com.unharvest.forge;

import com.unharvest.Constants;
import com.unharvest.UnharvestCommands;
import com.unharvest.UnharvestCommon;
import com.unharvest.UnharvestManager;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.Commands;
import net.minecraft.core.HolderLookup;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerAboutToStartEvent;
import net.minecraftforge.eventbus.api.listener.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.stream.Stream;

@Mod(Constants.MOD_ID)
public class UnharvestForge {

    static {
        ensureLootModifierManagerInitialized(null);
    }

    public UnharvestForge() {
        UnharvestCommon.init();
        ensureLootModifierManagerInitialized(null);

        try {
            AddReloadListenerEvent.BUS.addListener(event -> ensureLootModifierManagerInitialized(event.getRegistries()));
            ServerAboutToStartEvent.BUS.addListener(event -> ensureLootModifierManagerInitialized(event.getServer().registryAccess()));
        } catch (Throwable t) {
            Constants.LOG.debug("[Unharvest] Failed to attach lifecycle listeners for LootModifierManager: {}", t.getMessage());
        }

        Constants.LOG.info("[Unharvest Forge] Mod initialized on Minecraft Forge 26.3!");
    }

    public static void ensureLootModifierManagerInitialized(HolderLookup.Provider lookupProvider) {
        try {
            Class<?> handlerClass = Class.forName("net.minecraftforge.common.ForgeInternalHandler");
            Field instanceField = handlerClass.getDeclaredField("INSTANCE");
            instanceField.setAccessible(true);
            if (instanceField.get(null) == null) {
                Class<?> lmmClass = Class.forName("net.minecraftforge.common.loot.LootModifierManager");
                Constructor<?> ctor = lmmClass.getDeclaredConstructor(HolderLookup.Provider.class);
                ctor.setAccessible(true);
                HolderLookup.Provider provider = lookupProvider != null ? lookupProvider : HolderLookup.Provider.create(Stream.empty());
                Object fallbackManager = ctor.newInstance(provider);
                instanceField.set(null, fallbackManager);
                Constants.LOG.info("[Unharvest Forge] Successfully pre-initialized Forge LootModifierManager!");
            }
        } catch (Throwable t) {
            Constants.LOG.debug("[Unharvest] Could not safeguard Forge LootModifierManager: {}", t.getMessage());
        }
    }

    @Mod.EventBusSubscriber(modid = Constants.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static class ClientModEvents {
        @SubscribeEvent
        public static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
            event.register(UnharvestManager.UPGRADE_KEY);
            event.register(UnharvestManager.SWARM_KEY);
        }
    }

    @Mod.EventBusSubscriber(modid = Constants.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
    public static class ClientEvents {

        @SubscribeEvent
        public static void onClientTick(TickEvent.ClientTickEvent.Post event) {
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
                                    .executes(context -> UnharvestCommands.reset(Minecraft.getInstance().player))
                            )
                            .then(Commands.literal("rescan")
                                    .executes(context -> UnharvestCommands.rescanField(Minecraft.getInstance().player))
                            )
                            .then(Commands.literal("regenerate")
                                    .executes(context -> UnharvestCommands.regenerateField(Minecraft.getInstance().player))
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

