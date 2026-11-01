package com.unharvest;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;

/**
 * Command logic for /unharvest operations across all mod loaders.
 */
public class UnharvestCommands {

    public static int openUpgrades(Player player) {
        if (player != null && !UnharvestManager.isUnharvestWorld(player.level())) {
            player.sendSystemMessage(Component.literal("§5§l[Unharvest] §cThis world is not an Unharvest world! Create a world with the 'Unharvest (Wheat Field)' world type to play."));
            return 0;
        }
        UnharvestManager.openUpgradesScreen();
        return 1;
    }

    public static int giveScythe(Player player) {
        if (player != null) {
            if (!UnharvestManager.isUnharvestWorld(player.level())) {
                player.sendSystemMessage(Component.literal("§5§l[Unharvest] §cThis world is not an Unharvest world! Create a world with the 'Unharvest (Wheat Field)' world type to play."));
                return 0;
            }
            UnharvestItems.ensurePlayerHasScythe(player);
        }
        return 1;
    }

    public static int reset(Player player) {
        if (player != null && !UnharvestManager.isUnharvestWorld(player.level())) {
            player.sendSystemMessage(Component.literal("§5§l[Unharvest] §cThis world is not an Unharvest world! Create a world with the 'Unharvest (Wheat Field)' world type to play."));
            return 0;
        }
        UnharvestState.getInstance().resetAll();
        UnharvestFieldGenerator.clearCache();
        if (player != null) {
            player.sendSystemMessage(Component.literal("§5§l[Unharvest] §aAll progress, plague essence, and upgrades have been reset to zero!"));
        }
        return 1;
    }

    public static int regenerateField(Player player) {
        if (player != null && !UnharvestManager.isUnharvestWorld(player.level())) {
            player.sendSystemMessage(Component.literal("§5§l[Unharvest] §cThis world is not an Unharvest world!"));
            return 0;
        }
        net.minecraft.client.Minecraft client = net.minecraft.client.Minecraft.getInstance();
        net.minecraft.server.MinecraftServer server = client.getSingleplayerServer();
        if (server != null) {
            net.minecraft.server.level.ServerLevel level = server.overworld();
            if (level != null) {
                UnharvestFieldGenerator.forceRegenerateField(server, level);
                if (player != null) {
                    player.sendSystemMessage(Component.literal("§5§l[Unharvest] §aField regenerated and cleared of all structure obstructions!"));
                }
                return 1;
            }
        }
        return 0;
    }

    // =========================================================================
    // [DEBUG COMMAND - Comment out before final production release]
    // =========================================================================
    public static int giveEssence(Player player, long amount) {
        if (player != null) {
            UnharvestState.getInstance().addEssence(amount);
            player.sendSystemMessage(Component.literal(String.format(
                    "§5§l[Unharvest DEBUG] §aAdded §d+%,d§a Blight Essence! (Total: §d%s§a)",
                    amount, UnharvestState.getInstance().getFormattedEssence()
            )));
        }
        return 1;
    }
}
