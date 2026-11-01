package com.unharvest.gui;

import com.unharvest.UnharvestManager;
import com.unharvest.UnharvestState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

/**
 * Top-left in-game HUD overlay displaying live Unharvest progression:
 * Blight Essence balance, field clearance %, and real-time alerts
 * when new upgrades become affordable.
 */
public class UnharvestHud {

    public static void render(GuiGraphicsExtractor extractor, Minecraft client) {
        if (client == null || client.level == null || client.player == null) return;
        if (client.gui != null && client.gui.hud != null && client.gui.hud.isHidden()) return;
        if (!UnharvestManager.isUnharvestWorld(client.level)) return;

        Font font = client.font;
        UnharvestState state = UnharvestState.getInstance();

        int x = 8;
        int y = 8;
        int width = 168;

        boolean upgradeAvailable = isAnyUpgradeAvailable(state);
        int height = upgradeAvailable ? 56 : 46;

        // Background box: dark semi-transparent fill with purple border
        extractor.fill(x - 4, y - 4, x + width + 4, y + height + 4, 0x90000000);
        extractor.fill(x - 5, y - 5, x + width + 5, y - 4, 0xCC6A2A9A); // top
        extractor.fill(x - 5, y + height + 4, x + width + 5, y + height + 5, 0xCC6A2A9A); // bottom
        extractor.fill(x - 5, y - 5, x - 4, y + height + 5, 0xCC6A2A9A); // left
        extractor.fill(x + width + 4, y - 5, x + width + 5, y + height + 5, 0xCC6A2A9A); // right

        // 1. Mod Title
        extractor.text(font, Component.literal("§5§lUNHARVEST §7| §fPlague Field"), x, y, 0xFFFFFF);

        // 2. Essence Count
        extractor.text(font, Component.literal("§d✨ Essence: §f" + state.getFormattedEssence()), x, y + 13, 0xFFFFFF);

        // 3. Field Clearance %
        extractor.text(font, Component.literal(String.format("§a🌿 Cleared: §f%.1f%% §7(%d/%d)",
                state.getClearedPercentage(), state.getTotalWithered(), state.getFieldTotalCrops())), x, y + 25, 0xFFFFFF);

        // 4. Upgrade Notification
        if (upgradeAvailable) {
            long pulse = (System.currentTimeMillis() / 350) % 2;
            String icon = (pulse == 0) ? "§e▲" : "§6▲";
            extractor.text(font, Component.literal(icon + " §eUpgrade Ready! §6[Press U]"), x, y + 40, 0xFFFFFF);
        } else {
            extractor.text(font, Component.literal("§8Press [U] for Upgrades"), x, y + 37, 0x888888);
        }
    }

    public static boolean isAnyUpgradeAvailable(UnharvestState state) {
        long essence = state.getEssence();
        if (state.getSpeedLevel() < state.getMaxSpeedLevel() && essence >= state.getSpeedCost()) return true;
        if (state.getRadiusLevel() < state.getMaxRadiusLevel() && essence >= state.getRadiusCost()) return true;
        if (state.getYieldLevel() < state.getMaxYieldLevel() && essence >= state.getYieldCost()) return true;
        if (state.getChainLevel() < state.getMaxChainLevel() && essence >= state.getChainCost()) return true;
        if (state.getSwarmLevel() < state.getMaxSwarmLevel() && essence >= state.getSwarmCost()) return true;
        return false;
    }
}
