package com.treeshake.neoforge;

import com.treeshake.Constants;
import com.treeshake.TreeShakeCommon;
import com.treeshake.client.animation.tree.TreeShakeManager;
import com.treeshake.client.config.TreeShakeConfig;
import com.treeshake.client.gui.TreeShakeConfigScreen;
import com.unharvest.UnharvestCommands;
import com.unharvest.UnharvestManager;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
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
public class TreeShakeNeoForge {

    public TreeShakeNeoForge(IEventBus modBus) {
        TreeShakeCommon.init();
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
                TreeShakeManager.tick(client.level);
                UnharvestManager.tick(client.level, client.player);
            }
        }

        @SubscribeEvent
        public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
            TreeShakeManager.clear();
            UnharvestManager.clearWorldCache();
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
                            .then(Commands.literal("regenerate")
                                    .executes(context -> UnharvestCommands.regenerateField(Minecraft.getInstance().player))
                            )
                            .executes(context -> UnharvestCommands.openUpgrades(Minecraft.getInstance().player))
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
                source.sendSystemMessage(Component.literal("§a[Tree Shake] §fTree Shake animations §aENABLED §7(use §f/treeshake config§7 for UI)"));
            } else {
                source.sendSystemMessage(Component.literal("§a[Tree Shake] §fTree Shake animations §cDISABLED"));
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
