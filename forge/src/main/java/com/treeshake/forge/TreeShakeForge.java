package com.treeshake.forge;

import com.treeshake.Constants;
import com.treeshake.TreeShakeCommon;
import com.treeshake.client.animation.tree.TreeShakeManager;
import com.treeshake.client.config.TreeShakeConfig;
import com.treeshake.client.gui.TreeShakeConfigScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.listener.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod(Constants.MOD_ID)
public class TreeShakeForge {

    public TreeShakeForge() {
        TreeShakeCommon.init();
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
                            .then(Commands.literal("toggle")
                                    .executes(context -> toggleTreeShake(context.getSource()))
                            )
                            .then(Commands.literal("on")
                                    .executes(context -> setTreeShake(context.getSource(), true))
                            )
                            .then(Commands.literal("off")
                                    .executes(context -> setTreeShake(context.getSource(), false))
                            )
                            .then(Commands.literal("config")
                                    .executes(context -> openTreeShakeConfig(context.getSource()))
                            )
                            .then(Commands.literal("gui")
                                    .executes(context -> openTreeShakeConfig(context.getSource()))
                            )
                            .then(Commands.literal("menu")
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
