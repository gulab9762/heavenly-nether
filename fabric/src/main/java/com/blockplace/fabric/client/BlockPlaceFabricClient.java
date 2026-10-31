package com.blockplace.fabric.client;

import com.blockplace.Constants;
import com.blockplace.client.animation.tree.TreeShakeManager;
import com.blockplace.client.config.BlockPlaceConfig;
import com.blockplace.client.gui.TreeShakeConfigScreen;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

public class BlockPlaceFabricClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        Constants.LOG.info("Initializing {} on Fabric Client", Constants.MOD_NAME);

        // Load persisted config on client startup
        BlockPlaceConfig.load();

        // Tick active tree animations every client game tick
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.level != null && !client.isPaused()) {
                TreeShakeManager.tick(client.level);
            }
        });

        // Reset animations on disconnect
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            TreeShakeManager.clear();
        });

        // Register client commands
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            // 1. Dedicated /treeshake command
            dispatcher.register(
                    ClientCommands.literal("treeshake")
                            .then(ClientCommands.literal("toggle")
                                    .executes(context -> toggleTreeShake(context.getSource()))
                            )
                            .then(ClientCommands.literal("on")
                                    .executes(context -> setTreeShake(context.getSource(), true))
                            )
                            .then(ClientCommands.literal("off")
                                    .executes(context -> setTreeShake(context.getSource(), false))
                            )
                            .then(ClientCommands.literal("config")
                                    .executes(context -> openTreeShakeConfig(context.getSource()))
                            )
                            .then(ClientCommands.literal("gui")
                                    .executes(context -> openTreeShakeConfig(context.getSource()))
                            )
                            .then(ClientCommands.literal("menu")
                                    .executes(context -> openTreeShakeConfig(context.getSource()))
                            )
                            .then(ClientCommands.literal("whitelist")
                                    .then(ClientCommands.literal("reset")
                                            .executes(context -> resetWhitelist(context.getSource()))
                                    )
                                    .executes(context -> showWhitelist(context.getSource()))
                            )
                            .executes(context -> toggleTreeShake(context.getSource()))
            );

            // 2. /blockplace alias for backwards compatibility
            dispatcher.register(
                    ClientCommands.literal("blockplace")
                            .then(ClientCommands.literal("toggle")
                                    .executes(context -> toggleTreeShake(context.getSource()))
                            )
                            .then(ClientCommands.literal("config")
                                    .executes(context -> openTreeShakeConfig(context.getSource()))
                            )
                            .then(ClientCommands.literal("gui")
                                    .executes(context -> openTreeShakeConfig(context.getSource()))
                            )
                            .then(ClientCommands.literal("tree")
                                    .then(ClientCommands.literal("config")
                                            .executes(context -> openTreeShakeConfig(context.getSource()))
                                    )
                                    .then(ClientCommands.argument("value", StringArgumentType.word())
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
        });
    }

    private static int openTreeShakeConfig(FabricClientCommandSource source) {
        Minecraft client = Minecraft.getInstance();
        client.execute(() -> client.setScreenAndShow(new TreeShakeConfigScreen(client.gui.screen())));
        return 1;
    }

    private static int toggleTreeShake(FabricClientCommandSource source) {
        return setTreeShake(source, !BlockPlaceConfig.enableTreeShake);
    }

    private static int setTreeShake(FabricClientCommandSource source, boolean enable) {
        BlockPlaceConfig.enableTreeShake = enable;
        BlockPlaceConfig.save();
        if (BlockPlaceConfig.enableTreeShake) {
            source.sendFeedback(Component.literal("§a[Tree Shake] §fTree Shake animations §aENABLED §7(use §f/treeshake config§7 for UI)"));
        } else {
            source.sendFeedback(Component.literal("§a[Tree Shake] §fTree Shake animations §cDISABLED"));
        }
        return 1;
    }

    private static int applyTreeCommand(FabricClientCommandSource source, String val) {
        if (val.equalsIgnoreCase("on") || val.equalsIgnoreCase("true") || val.equalsIgnoreCase("enable")) {
            return setTreeShake(source, true);
        } else if (val.equalsIgnoreCase("off") || val.equalsIgnoreCase("false") || val.equalsIgnoreCase("disable")) {
            return setTreeShake(source, false);
        } else if (val.equalsIgnoreCase("toggle")) {
            return toggleTreeShake(source);
        } else if (val.equalsIgnoreCase("config") || val.equalsIgnoreCase("gui") || val.equalsIgnoreCase("menu")) {
            return openTreeShakeConfig(source);
        } else {
            source.sendError(Component.literal("§c[Tree Shake] §fUnknown option: '§c" + val + "§f'. Use: §eon, off, toggle, config"));
            return 0;
        }
    }

    private static int showWhitelist(FabricClientCommandSource source) {
        source.sendFeedback(Component.literal("§a[Tree Shake] §fShakable Blocks Whitelist (" + BlockPlaceConfig.shakableBlocksWhitelist.size() + " items):"));
        for (String item : BlockPlaceConfig.shakableBlocksWhitelist) {
            source.sendFeedback(Component.literal("  §7- §e" + item));
        }
        return 1;
    }

    private static int resetWhitelist(FabricClientCommandSource source) {
        BlockPlaceConfig.resetWhitelistToDefaults();
        BlockPlaceConfig.save();
        source.sendFeedback(Component.literal("§a[Tree Shake] §fShakable blocks whitelist reset to defaults."));
        return 1;
    }
}
