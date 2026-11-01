package com.unharvest.gui;

import com.unharvest.UnharvestManager;
import com.unharvest.UnharvestState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

/**
 * In-game HUD overlay for Unharvest:
 * - Top-left: Blight Essence balance, field clearance %, and upgrade ready alerts
 * - Bottom-right: Placeable Locust Swarm ability card with real-time cooldown sweep
 */
public class UnharvestHud {

    public static void render(GuiGraphicsExtractor extractor, Minecraft client) {
        if (client == null || client.level == null || client.player == null) return;
        if (client.gui != null && client.gui.hud != null && client.gui.hud.isHidden()) return;
        if (!UnharvestManager.isUnharvestWorld(client.level)) return;

        Font font = client.font;
        UnharvestState state = UnharvestState.getInstance();

        // 1. Top-Left Progression Card
        renderTopLeftHud(extractor, font, state);

        // 2. Bottom-Right Ability Card: Locust Swarm
        renderSwarmAbilityCard(extractor, client, font, state);
    }

    private static void renderTopLeftHud(GuiGraphicsExtractor extractor, Font font, UnharvestState state) {
        int x = 10;
        int y = 10;
        int width = 180;

        boolean upgradeAvailable = isAnyUpgradeAvailable(state);
        int height = upgradeAvailable ? 54 : 42;

        // Background box: dark semi-transparent fill with purple border
        extractor.fill(x - 4, y - 4, x + width + 4, y + height + 4, 0x90000000);
        extractor.fill(x - 5, y - 5, x + width + 5, y - 4, 0xCC6A2A9A); // top
        extractor.fill(x - 5, y + height + 4, x + width + 5, y + height + 5, 0xCC6A2A9A); // bottom
        extractor.fill(x - 5, y - 5, x - 4, y + height + 5, 0xCC6A2A9A); // left
        extractor.fill(x + width + 4, y - 5, x + width + 5, y + height + 5, 0xCC6A2A9A); // right

        // Title
        extractor.text(font, Component.literal("§5§lUNHARVEST §7| §dPlague Field"), x, y, 0xFFFFFFFF);

        // Essence Count
        extractor.text(font, Component.literal("§d✨ Essence: §f" + state.getFormattedEssence()), x, y + 13, 0xFFFFFFFF);

        // Field Clearance %
        extractor.text(font, Component.literal(String.format("§a🌿 Cleared: §f%.1f%% §7(%d/%d)",
                state.getClearedPercentage(), state.getTotalWithered(), state.getFieldTotalCrops())), x, y + 25, 0xFFFFFFFF);

        // Upgrade Notification
        if (upgradeAvailable) {
            long pulse = (System.currentTimeMillis() / 400) % 2;
            String icon = (pulse == 0) ? "§e▲" : "§6▲";
            extractor.text(font, Component.literal(icon + " §eUpgrade Ready! §6[Press U]"), x, y + 38, 0xFFFFFFFF);
        } else {
            extractor.text(font, Component.literal("§7[U] Upgrades Menu"), x, y + 36, 0xFFAAAAAA);
        }
    }

    private static void renderSwarmAbilityCard(GuiGraphicsExtractor extractor, Minecraft client, Font font, UnharvestState state) {
        int screenWidth = client.getWindow().getGuiScaledWidth();
        int screenHeight = client.getWindow().getGuiScaledHeight();

        int size = 44;
        int x = screenWidth - size - 16;
        int y = screenHeight - size - 16;

        boolean unlocked = state.isSwarmUnlocked();
        int remainingTicks = UnharvestManager.getSwarmCooldownRemaining();
        boolean onCooldown = unlocked && (remainingTicks > 0);

        // Background box
        int bgColor = unlocked ? 0xD0101810 : 0xAA181818;
        extractor.fill(x, y, x + size, y + size, bgColor);

        // Border color
        int borderColor;
        if (!unlocked) {
            borderColor = 0xFF444444;
        } else if (onCooldown) {
            borderColor = 0xFF2A502A;
        } else {
            long pulse = (System.currentTimeMillis() / 300) % 2;
            borderColor = (pulse == 0) ? 0xFF3BD848 : 0xFF28A834;
        }

        // Draw 1px border
        extractor.fill(x, y, x + size, y + 1, borderColor);
        extractor.fill(x, y + size - 1, x + size, y + size, borderColor);
        extractor.fill(x, y, x + 1, y + size, borderColor);
        extractor.fill(x + size - 1, y, x + size, y + size, borderColor);

        // Icon
        int iconY = y + 8;
        if (!unlocked) {
            extractor.centeredText(font, Component.literal("§8🔒"), x + size / 2, iconY, 0xFFFFFFFF);
            extractor.centeredText(font, Component.literal("§8[R] Swarm"), x + size / 2, y + 27, 0xFFAAAAAA);
        } else {
            // Locust icon
            extractor.centeredText(font, Component.literal("§a🦗"), x + size / 2, iconY, 0xFFFFFFFF);

            // Keybind badge [R] at top-right
            extractor.fill(x + size - 12, y + 2, x + size - 2, y + 10, 0xAA000000);
            extractor.text(font, Component.literal("§eR"), x + size - 9, y + 2, 0xFFFFFFFF);

            if (onCooldown) {
                // Cooldown radial wipe overlay
                float frac = UnharvestManager.getSwarmCooldownFraction();
                int overlayHeight = (int) (frac * (size - 2));
                extractor.fill(x + 1, y + size - 1 - overlayHeight, x + size - 1, y + size - 1, 0x90000000);

                // Remaining seconds countdown
                float seconds = remainingTicks / 20.0f;
                String timeStr = String.format("%.1fs", seconds);
                extractor.centeredText(font, Component.literal("§e" + timeStr), x + size / 2, y + 27, 0xFFFFFFFF);
            } else {
                extractor.centeredText(font, Component.literal("§aREADY"), x + size / 2, y + 27, 0xFFFFFFFF);
            }
        }
    }

    public static boolean isAnyUpgradeAvailable(UnharvestState state) {
        for (String id : UnharvestState.NODES.keySet()) {
            if (state.canUnlockNode(id)) return true;
        }
        return false;
    }
}
