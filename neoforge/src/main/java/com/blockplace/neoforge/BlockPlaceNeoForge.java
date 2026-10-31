package com.blockplace.neoforge;

import com.blockplace.BlockPlaceCommon;
import com.blockplace.Constants;
import com.blockplace.client.animation.tree.TreeShakeManager;
import com.blockplace.client.config.BlockPlaceConfig;
import com.blockplace.client.gui.TreeShakeConfigScreen;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;

@Mod(Constants.MOD_ID)
public class BlockPlaceNeoForge {

    public BlockPlaceNeoForge() {
        BlockPlaceCommon.init();
    }

    @EventBusSubscriber(modid = Constants.MOD_ID, value = Dist.CLIENT)
    public static class ClientEvents {

        @SubscribeEvent
        public static void onClientTick(ClientTickEvent.Post event) {
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
            BlockPlaceConfig.load();

            // 1. Dedicated /treeshake command
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

            // 2. /blockplace alias for backwards compatibility
            event.getDispatcher().register(
                    Commands.literal("blockplace")
                            .then(Commands.literal("toggle")
                                    .executes(context -> toggleTreeShake(context.getSource()))
                            )
                            .then(Commands.literal("config")
                                    .executes(context -> openTreeShakeConfig(context.getSource()))
                            )
                            .then(Commands.literal("gui")
                                    .executes(context -> openTreeShakeConfig(context.getSource()))
                            )
                            .then(Commands.literal("tree")
                                    .then(Commands.literal("config")
                                            .executes(context -> openTreeShakeConfig(context.getSource()))
                                    )
                                    .then(Commands.argument("value", StringArgumentType.word())
                                            .suggests((context, builder) -> {
                                                builder.suggest("on");
                                                builder.suggest("off");
                                                builder.suggest("toggle");
                                                builder.suggest("config");
                                                return builder.buildFuture();
                                            })
                                            .executes(context -> {
                                                String val = StringArgumentType.getString(context, "value");
                                                return applyTreeCommand(context.getSource(), val);
                                            })
                                    )
                                    .executes(context -> toggleTreeShake(context.getSource()))
                            )
                            .executes(context -> openTreeShakeConfig(context.getSource()))
            );
        }

        private static int openTreeShakeConfig(CommandSourceStack source) {
            Minecraft client = Minecraft.getInstance();
            client.execute(() -> client.setScreenAndShow(new TreeShakeConfigScreen(client.gui.screen())));
            return 1;
        }

        private static int toggleTreeShake(CommandSourceStack source) {
            return setTreeShake(source, !BlockPlaceConfig.enableTreeShake);
        }

        private static int setTreeShake(CommandSourceStack source, boolean enable) {
            BlockPlaceConfig.enableTreeShake = enable;
            BlockPlaceConfig.save();
            if (BlockPlaceConfig.enableTreeShake) {
                source.sendSystemMessage(Component.literal("§a[Tree Shake] §fCartoon Tree Shake animations §aENABLED §7(use §f/treeshake config§7 for UI)"));
            } else {
                source.sendSystemMessage(Component.literal("§a[Tree Shake] §fCartoon Tree Shake animations §cDISABLED"));
            }
            return 1;
        }

        private static int applyTreeCommand(CommandSourceStack source, String val) {
            if (val.equalsIgnoreCase("on") || val.equalsIgnoreCase("true") || val.equalsIgnoreCase("enable")) {
                return setTreeShake(source, true);
            } else if (val.equalsIgnoreCase("off") || val.equalsIgnoreCase("false") || val.equalsIgnoreCase("disable")) {
                return setTreeShake(source, false);
            } else if (val.equalsIgnoreCase("toggle")) {
                return toggleTreeShake(source);
            } else if (val.equalsIgnoreCase("config") || val.equalsIgnoreCase("gui") || val.equalsIgnoreCase("menu")) {
                return openTreeShakeConfig(source);
            } else {
                source.sendFailure(Component.literal("§c[Tree Shake] §fUnknown option: '§c" + val + "§f'. Use: §eon, off, toggle, config"));
                return 0;
            }
        }

        private static int showWhitelist(CommandSourceStack source) {
            source.sendSystemMessage(Component.literal("§a[Tree Shake] §fShakable Blocks Whitelist (" + BlockPlaceConfig.shakableBlocksWhitelist.size() + " items):"));
            for (String item : BlockPlaceConfig.shakableBlocksWhitelist) {
                source.sendSystemMessage(Component.literal("  §7- §e" + item));
            }
            return 1;
        }

        private static int resetWhitelist(CommandSourceStack source) {
            BlockPlaceConfig.resetWhitelistToDefaults();
            BlockPlaceConfig.save();
            source.sendSystemMessage(Component.literal("§a[Tree Shake] §fShakable blocks whitelist reset to defaults."));
            return 1;
        }
    }
}
