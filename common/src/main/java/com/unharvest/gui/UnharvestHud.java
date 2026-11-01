package com.unharvest.gui;

import com.unharvest.UnharvestFieldGenerator;
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
        int width = 210;

        boolean upgradeAvailable = isAnyUpgradeAvailable(state);
        boolean isGenerating = UnharvestFieldGenerator.isGenerating();
        int height = (upgradeAvailable || isGenerating) ? 54 : 42;

        // Background box: dark semi-transparent fill with purple border
        extractor.fill(x - 4, y - 4, x + width + 4, y + height + 4, 0x90000000);
        extractor.fill(x - 5, y - 5, x + width + 5, y - 4, 0xCC6A2A9A); // top
        extractor.fill(x - 5, y + height + 4, x + width + 5, y + height + 5, 0xCC6A2A9A); // bottom
        extractor.fill(x - 5, y - 5, x - 4, y + height + 5, 0xCC6A2A9A); // left
        extractor.fill(x + width + 4, y - 5, x + width + 5, y + height + 5, 0xCC6A2A9A); // right

        // Title
        extractor.text(font, Component.literal("§5§lUNHARVEST §7| §dPlague Field"), x, y, 0xFFFFFFFF);

        // Essence Count (exact full number to 1s place)
        extractor.text(font, Component.literal("§d✨ Essence: §f" + state.getFormattedEssence()), x, y + 13, 0xFFFFFFFF);

        // Field Clearance % (exact counts with comma formatting)
        extractor.text(font, Component.literal(String.format(java.util.Locale.US, "§a🌿 Withered: §f%.1f%% §7(%,d/%,d)",
                state.getClearedPercentage(), state.getTotalWithered(), state.getFieldTotalCrops())), x, y + 25, 0xFFFFFFFF);

        // Upgrade Notification or Field Generation Status
        if (isGenerating) {
            long pulse = (System.currentTimeMillis() / 350) % 2;
            String icon = (pulse == 0) ? "§e⏳" : "§6⏳";
            extractor.text(font, Component.literal(icon + " §ePopulating Field: §f" + UnharvestFieldGenerator.getGenerationPercent() + "%"), x, y + 38, 0xFFFFFFFF);
        } else if (upgradeAvailable) {
            long pulse = (System.currentTimeMillis() / 400) % 2;
            String icon = (pulse == 0) ? "§e▲" : "§6▲";
            extractor.text(font, Component.literal(icon + " §eUpgrade Ready! §6[Press U]"), x, y + 38, 0xFFFFFFFF);
        } else {
            extractor.text(font, Component.literal("§7[U] Upgrades Menu"), x, y + 36, 0xFFAAAAAA);
        }
    }

    private static void renderSwarmAbilityCard(GuiGraphicsExtractor extractor, Minecraft client, Font font, UnharvestState state) {
        // Completely hide the bottom-right Locust Swarm card if not unlocked yet
        if (!state.isSwarmUnlocked()) {
            return;
        }

        int screenWidth = client.getWindow().getGuiScaledWidth();
        int screenHeight = client.getWindow().getGuiScaledHeight();

        int size = 44;
        int x = screenWidth - size - 16;
        int y = screenHeight - size - 16;

        int remainingTicks = UnharvestManager.getSwarmCooldownRemaining();
        boolean onCooldown = remainingTicks > 0;

        // Background box
        extractor.fill(x, y, x + size, y + size, 0xD0101810);

        // Border color
        int borderColor;
        if (onCooldown) {
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

        // Locust icon
        int iconY = y + 8;
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
            String timeStr = String.format(java.util.Locale.US, "%.1fs", seconds);
            extractor.centeredText(font, Component.literal("§e" + timeStr), x + size / 2, y + 27, 0xFFFFFFFF);
        } else {
            extractor.centeredText(font, Component.literal("§aREADY"), x + size / 2, y + 27, 0xFFFFFFFF);
        }
    }

    public static boolean isAnyUpgradeAvailable(UnharvestState state) {
        return state != null && state.hasAvailableUpgrade();
    }
}
