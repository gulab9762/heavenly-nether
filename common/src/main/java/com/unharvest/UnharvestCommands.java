package com.unharvest;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;

/**
 * Command logic for /unharvest operations across all mod loaders.
 */
public class UnharvestCommands {

    public static int openUpgrades() {
        UnharvestManager.openUpgradesScreen();
        return 1;
    }

    public static int giveScythe(Player player) {
        if (player != null) {
            UnharvestItems.ensurePlayerHasScythe(player);
        }
        return 1;
    }

    public static int reset(Player player) {
        UnharvestState.getInstance().resetAll();
        UnharvestFieldGenerator.clearCache();
        if (player != null) {
            player.sendSystemMessage(Component.literal("§5§l[Unharvest] §aAll progress, plague essence, and upgrades have been reset to zero!"));
        }
        return 1;
    }
}
