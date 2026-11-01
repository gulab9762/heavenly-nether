package com.treeshake.fabric.client;

import com.treeshake.Constants;
import com.treeshake.client.animation.tree.TreeShakeManager;
import com.treeshake.client.config.TreeShakeConfig;
import com.treeshake.client.gui.TreeShakeConfigScreen;
import com.unharvest.UnharvestCommands;
import com.unharvest.UnharvestManager;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

public class TreeShakeFabricClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        Constants.LOG.info("Initializing {} on Fabric Client", Constants.MOD_NAME);

        // Load persisted config on client startup
        TreeShakeConfig.load();

        // Register Keybindings
        KeyMappingHelper.registerKeyMapping(UnharvestManager.UPGRADE_KEY);
        KeyMappingHelper.registerKeyMapping(UnharvestManager.SWARM_KEY);

        // Tick animations and Unharvest gameplay loop every client game tick
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.level != null && !client.isPaused()) {
                TreeShakeManager.tick(client.level);
                UnharvestManager.tick(client.level, client.player);
            }
        });

        // Reset animations and world cache on disconnect
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            TreeShakeManager.clear();
            UnharvestManager.clearWorldCache();
        });

        // Register client commands
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            dispatcher.register(
                    ClientCommands.literal("treeshake")
                            .then(ClientCommands.literal("on")
                                    .executes(context -> setTreeShake(context.getSource(), true))
                            )
                            .then(ClientCommands.literal("off")
                                    .executes(context -> setTreeShake(context.getSource(), false))
                            )
                            .then(ClientCommands.literal("config")
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
                            .then(ClientCommands.literal("regenerate")
                                    .executes(context -> UnharvestCommands.regenerateField(Minecraft.getInstance().player))
                            )
                            .executes(context -> UnharvestCommands.openUpgrades(Minecraft.getInstance().player))
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
