package com.treeshake.fabric.client;

import com.treeshake.Constants;
import com.treeshake.client.animation.tree.TreeShakeManager;
import com.treeshake.client.config.TreeShakeConfig;
import com.treeshake.client.gui.TreeShakeConfigScreen;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

public class TreeShakeFabricClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        Constants.LOG.info("Initializing {} on Fabric Client", Constants.MOD_NAME);

        // Load persisted config on client startup
        TreeShakeConfig.load();

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
        });
    }

    private static int openTreeShakeConfig(FabricClientCommandSource source) {
        Minecraft client = Minecraft.getInstance();
        client.execute(() -> client.setScreenAndShow(new TreeShakeConfigScreen(client.gui.screen())));
        return 1;
    }

    private static int toggleTreeShake(FabricClientCommandSource source) {
        return setTreeShake(source, !TreeShakeConfig.enableTreeShake);
    }

    private static int setTreeShake(FabricClientCommandSource source, boolean enable) {
        TreeShakeConfig.enableTreeShake = enable;
        TreeShakeConfig.save();
        if (TreeShakeConfig.enableTreeShake) {
            source.sendFeedback(Component.literal("§a[Tree Shake] §fTree Shake animations §aENABLED §7(use §f/treeshake config§7 for UI)"));
        } else {
            source.sendFeedback(Component.literal("§a[Tree Shake] §fTree Shake animations §cDISABLED"));
        }
        return 1;
    }

    private static int showWhitelist(FabricClientCommandSource source) {
        source.sendFeedback(Component.literal("§a[Tree Shake] §fShakable Blocks Whitelist (" + TreeShakeConfig.shakableBlocksWhitelist.size() + " items):"));
        for (String item : TreeShakeConfig.shakableBlocksWhitelist) {
            source.sendFeedback(Component.literal("  §7- §e" + item));
        }
        return 1;
    }

    private static int resetWhitelist(FabricClientCommandSource source) {
        TreeShakeConfig.resetWhitelistToDefaults();
        TreeShakeConfig.save();
        source.sendFeedback(Component.literal("§a[Tree Shake] §fShakable blocks whitelist reset to defaults."));
        return 1;
    }
}
