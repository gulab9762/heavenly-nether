package com.treeshake.forge;

import com.treeshake.Constants;
import com.treeshake.TreeShakeCommon;
import com.treeshake.client.animation.tree.TreeShakeManager;
import com.treeshake.client.config.TreeShakeConfig;
import com.treeshake.client.gui.TreeShakeConfigScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.HolderLookup;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerAboutToStartEvent;
import net.minecraftforge.eventbus.api.listener.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.stream.Stream;

@Mod(Constants.MOD_ID)
public class TreeShakeForge {

    static {
        ensureLootModifierManagerInitialized(null);
    }

    public TreeShakeForge() {
        TreeShakeCommon.init();
        ensureLootModifierManagerInitialized(null);

        try {
            AddReloadListenerEvent.BUS.addListener(event -> ensureLootModifierManagerInitialized(event.getRegistries()));
            ServerAboutToStartEvent.BUS.addListener(event -> ensureLootModifierManagerInitialized(event.getServer().registryAccess()));
        } catch (Throwable t) {
            Constants.LOG.debug("[Tree Shake] Failed to attach lifecycle listeners for LootModifierManager: {}", t.getMessage());
        }

        Constants.LOG.info("[Tree Shake Forge] Mod initialized on Minecraft Forge 26.3!");
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
                Constants.LOG.info("[Tree Shake] Safeguarded Forge LootModifierManager against uninitialized crash.");
            }
        } catch (Throwable t) {
            Constants.LOG.debug("[Tree Shake] Could not safeguard Forge LootModifierManager: {}", t.getMessage());
        }
    }

    @Mod.EventBusSubscriber(modid = Constants.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
    public static class ClientEvents {

        @SubscribeEvent
        public static void onClientTick(TickEvent.ClientTickEvent.Post event) {
            Minecraft client = Minecraft.getInstance();
            if (client.level != null && !client.isPaused()) {
                TreeShakeManager.tick(client.level);
            }
        }

        @SubscribeEvent
        public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
            TreeShakeManager.clear();
        }

        @SubscribeEvent
        public static void onRegisterClientCommands(RegisterClientCommandsEvent event) {
            TreeShakeConfig.load();

            event.getDispatcher().register(
                    Commands.literal("treeshake")
                            .then(Commands.literal("on")
                                    .executes(context -> setTreeShake(context.getSource(), true))
                            )
                            .then(Commands.literal("off")
                                    .executes(context -> setTreeShake(context.getSource(), false))
                            )
                            .then(Commands.literal("config")
                                    .executes(context -> openTreeShakeConfig(context.getSource()))
                            )
                            .then(Commands.literal("whitelist")
                                    .then(Commands.literal("reset")
                                            .executes(context -> resetWhitelist(context.getSource()))
                                    )
                                    .executes(context -> showWhitelist(context.getSource()))
                            )
                            .executes(context -> toggleTreeShake(context.getSource()))
            );
        }

        private static int openTreeShakeConfig(CommandSourceStack source) {
            Minecraft client = Minecraft.getInstance();
            client.execute(() -> client.setScreenAndShow(new TreeShakeConfigScreen(client.gui.screen())));
            return 1;
        }

        private static int toggleTreeShake(CommandSourceStack source) {
            return setTreeShake(source, !TreeShakeConfig.enableTreeShake);
        }

        private static int setTreeShake(CommandSourceStack source, boolean enable) {
            TreeShakeConfig.enableTreeShake = enable;
            TreeShakeConfig.save();
            if (TreeShakeConfig.enableTreeShake) {
                source.sendSystemMessage(Component.literal("§a[Tree Shake] §fCartoon Tree Shake animations §aENABLED §7(use §f/treeshake config§7 for UI)"));
            } else {
                source.sendSystemMessage(Component.literal("§a[Tree Shake] §fCartoon Tree Shake animations §cDISABLED"));
            }
            return 1;
        }

        private static int showWhitelist(CommandSourceStack source) {
            source.sendSystemMessage(Component.literal("§a[Tree Shake] §fShakable Blocks Whitelist (" + TreeShakeConfig.shakableBlocksWhitelist.size() + " items):"));
            for (String item : TreeShakeConfig.shakableBlocksWhitelist) {
                source.sendSystemMessage(Component.literal("  §7- §e" + item));
            }
            return 1;
        }

        private static int resetWhitelist(CommandSourceStack source) {
            TreeShakeConfig.resetWhitelistToDefaults();
            TreeShakeConfig.save();
            source.sendSystemMessage(Component.literal("§a[Tree Shake] §fShakable blocks whitelist reset to defaults."));
            return 1;
        }
    }
}
