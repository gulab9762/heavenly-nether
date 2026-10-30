package com.blockplace.fabric.client;

import com.blockplace.Constants;
import com.blockplace.client.animation.BlockPlacementManager;
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

        // Tick active block placement and tree animations every client game tick
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.level != null && !client.isPaused()) {
                BlockPlacementManager.tick(client.level);
                TreeShakeManager.tick(client.level);
            }
        });

        // Reset animations on disconnect
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            BlockPlacementManager.clear();
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
                            .executes(context -> toggleTreeShake(context.getSource()))
            );

            // 2. /blockplace command
            dispatcher.register(
                    ClientCommands.literal("blockplace")
                            .then(ClientCommands.literal("toggle")
                                    .executes(context -> toggleAnimation(context.getSource()))
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
                            .then(ClientCommands.literal("style")
                                    .then(ClientCommands.argument("value", StringArgumentType.word())
                                            .suggests((context, builder) -> {
                                                for (BlockPlaceConfig.AnimationStyle s : BlockPlaceConfig.AnimationStyle.values()) {
                                                    builder.suggest(s.name().toLowerCase());
                                                }
                                                builder.suggest("vanilla");
                                                return builder.buildFuture();
                                            })
                                            .executes(context -> {
                                                String val = StringArgumentType.getString(context, "value");
                                                return applyStyleCommand(context.getSource(), val);
                                            })
                                    )
                                    .executes(context -> showCurrentStyle(context.getSource()))
                            )
                            .executes(context -> showCurrentStyle(context.getSource()))
            );
        });
    }

    private static int openTreeShakeConfig(FabricClientCommandSource source) {
        Minecraft client = Minecraft.getInstance();
        client.execute(() -> client.setScreenAndShow(new TreeShakeConfigScreen(client.gui.screen())));
        return 1;
    }

    private static int toggleAnimation(FabricClientCommandSource source) {
        if (BlockPlaceConfig.style == BlockPlaceConfig.AnimationStyle.NONE) {
            BlockPlaceConfig.style = BlockPlaceConfig.AnimationStyle.ROTATING;
            BlockPlaceConfig.save();
            source.sendFeedback(Component.literal("§a[Block Place] §fPlacement animations §aENABLED §f(style: §erotating§f)"));
        } else {
            BlockPlaceConfig.style = BlockPlaceConfig.AnimationStyle.NONE;
            BlockPlaceConfig.save();
            source.sendFeedback(Component.literal("§a[Block Place] §fPlacement animations §cDISABLED §7(pure vanilla placement)"));
        }
        return 1;
    }

    private static int toggleTreeShake(FabricClientCommandSource source) {
        return setTreeShake(source, !BlockPlaceConfig.enableTreeShake);
    }

    private static int setTreeShake(FabricClientCommandSource source, boolean enable) {
        BlockPlaceConfig.enableTreeShake = enable;
        BlockPlaceConfig.save();
        if (BlockPlaceConfig.enableTreeShake) {
            source.sendFeedback(Component.literal("§a[Tree Shake] §fCartoon Tree Shake animations §aENABLED §7(use §f/treeshake config§7 for UI)"));
        } else {
            source.sendFeedback(Component.literal("§a[Tree Shake] §fCartoon Tree Shake animations §cDISABLED"));
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

    private static int applyStyleCommand(FabricClientCommandSource source, String val) {
        if (BlockPlaceConfig.setStyle(val)) {
            String name = (BlockPlaceConfig.style == BlockPlaceConfig.AnimationStyle.NONE) ? "none (vanilla placement)" : BlockPlaceConfig.style.name().toLowerCase();
            source.sendFeedback(Component.literal("§a[Block Place] §fAnimation style set to: §e" + name));
            return 1;
        } else {
            source.sendError(Component.literal("§c[Block Place] §fUnknown style: '§c" + val + "§f'. Available: §esmooth, rotating, pop, drop, spiral, expand, spring, none"));
            return 0;
        }
    }

    private static int showCurrentStyle(FabricClientCommandSource source) {
        String name = (BlockPlaceConfig.style == BlockPlaceConfig.AnimationStyle.NONE) ? "none (vanilla)" : BlockPlaceConfig.style.name().toLowerCase();
        String treeStatus = BlockPlaceConfig.enableTreeShake ? "§aenabled" : "§cdisabled";
        source.sendFeedback(Component.literal("§a[Block Place] §fPlacement style: §e" + name + " §7| Tree shake: " + treeStatus));
        source.sendFeedback(Component.literal("§7Use §f/treeshake toggle§7, §f/treeshake config§7, or §f/blockplace style <name>"));
        return 1;
    }
}
