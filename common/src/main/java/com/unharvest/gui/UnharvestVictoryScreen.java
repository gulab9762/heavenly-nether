package com.unharvest.gui;

import com.unharvest.UnharvestState;
import com.unharvest.UnharvestTimer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.ConfirmLinkScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;

import java.net.URI;
import java.util.Locale;

/**
 * Victory / Completion Screen triggered upon reaching 100% field clearance.
 * Congratulates the player, displays stats recap, provides buttons to support
 * the developer on YouTube and Wishlist the original Unharvest on Steam,
 * and provides commands to reset or regenerate.
 */
public class UnharvestVictoryScreen extends Screen {

    private final Screen parent;
    private static final String YOUTUBE_URL = "https://www.youtube.com/@SkorpionDev";
    private static final String STEAM_URL = "https://store.steampowered.com/app/5138990/Unharvest/";

    public UnharvestVictoryScreen(Screen parent) {
        super(Component.literal("Unharvest - 100% Cleared!"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int centerX = this.width / 2;
        int boxWidth = 360;
        int boxHeight = 225;
        int topY = this.height / 2 - boxHeight / 2;

        int btnWidth = 150;
        int btnY = topY + 115;

        // Button 1: Support on YouTube
        addRenderableWidget(Button.builder(
                Component.literal("§c▶ Support on YouTube"),
                ConfirmLinkScreen.confirmLink(this, URI.create(YOUTUBE_URL), false)
        ).bounds(centerX - btnWidth - 6, btnY, btnWidth, 20).build());

        // Button 2: Wishlist original game on Steam
        addRenderableWidget(Button.builder(
                Component.literal("§b★ Wishlist on Steam"),
                ConfirmLinkScreen.confirmLink(this, URI.create(STEAM_URL), false)
        ).bounds(centerX + 6, btnY, btnWidth, 20).build());

        // Button 3: Close / Continue Roaming
        int doneWidth = 130;
        int doneY = topY + 186;
        addRenderableWidget(Button.builder(
                Component.literal("Continue Playing"),
                btn -> this.onClose()
        ).bounds(centerX - doneWidth / 2, doneY, doneWidth, 20).build());

        // Play celebratory victory fanfare if completed, otherwise soft button sound
        UnharvestState state = UnharvestState.getInstance();
        if (this.minecraft != null) {
            if (state.isFieldCompleted()) {
                this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 1.0f));
            } else {
                this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0f));
            }
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor extractor, int mouseX, int mouseY, float partialTicks) {
        // Dark background overlay tint
        extractor.fill(0, 0, this.width, this.height, 0xAA08040C);

        int centerX = this.width / 2;
        int boxWidth = 360;
        int boxHeight = 225;
        int x = centerX - boxWidth / 2;
        int y = this.height / 2 - boxHeight / 2;

        // Card background fill
        extractor.fill(x - 4, y - 4, x + boxWidth + 4, y + boxHeight + 4, 0xFA120A1A);

        // Ornate glowing gold and purple borders
        extractor.fill(x - 5, y - 5, x + boxWidth + 5, y - 4, 0xFFFFCC22); // Top gold border
        extractor.fill(x - 5, y + boxHeight + 4, x + boxWidth + 5, y + boxHeight + 5, 0xFFFFCC22); // Bottom gold border
        extractor.fill(x - 5, y - 5, x - 4, y + boxHeight + 5, 0xCC8A2AB0); // Left purple border
        extractor.fill(x + boxWidth + 4, y - 5, x + boxWidth + 5, y + boxHeight + 5, 0xCC8A2AB0); // Right purple border

        Font font = this.font;
        UnharvestState state = UnharvestState.getInstance();
        boolean completed = state.isFieldCompleted();

        if (completed) {
            // 1. Triumphant Banner Titles
            extractor.centeredText(font, Component.literal("§6§l🏆 100% FIELD WITHERED! 🏆"), centerX, y + 12, 0xFFFFFFFF);
            extractor.centeredText(font, Component.literal("§d§lCONGRATULATIONS!§r"), centerX, y + 26, 0xFFFFFFFF);

            // 2. Victory Message
            extractor.centeredText(font, Component.literal("§aYou have brought total decay upon the golden field!"), centerX, y + 43, 0xFFFFFFFF);
            extractor.centeredText(font, Component.literal("§7Every last stalk has withered into eternal ruin."), centerX, y + 55, 0xFFFFFFFF);

            // 3. Stats Summary
            String timeStr = UnharvestTimer.formatTimer(state.getElapsedMillis());
            String statsText = String.format(Locale.US, "§d✨ Final Essence: §f%s   §7|   §6⏱ Time: §f%s",
                    state.getFormattedEssence(), timeStr);
            extractor.centeredText(font, Component.literal(statsText), centerX, y + 73, 0xFFFFFFFF);
        } else {
            // 1. Progress Status Header (Replaces Congrats when not yet completed)
            extractor.centeredText(font, Component.literal("§5§l🌾 UNHARVEST PLAGUE FIELD 🌾"), centerX, y + 12, 0xFFFFFFFF);
            extractor.centeredText(font, Component.literal("§e§lFIELD CLEARANCE IN PROGRESS§r"), centerX, y + 26, 0xFFFFFFFF);

            // 2. In-Progress Message
            extractor.centeredText(font, Component.literal("§aThe withering blight is spreading across the arena."), centerX, y + 43, 0xFFFFFFFF);
            extractor.centeredText(font, Component.literal("§7Wither every crop with your scythe to achieve 100% victory!"), centerX, y + 55, 0xFFFFFFFF);

            // 3. Stats Summary
            String timeStr = UnharvestTimer.formatTimer(state.getElapsedMillis());
            String statsText = String.format(Locale.US, "§a🌾 Cleared: §f%.1f%% §7(%,d/%,d)   §7|   §6⏱ §f%s",
                    state.getClearedPercentage(), state.getTotalWithered(), state.getFieldTotalCrops(), timeStr);
            extractor.centeredText(font, Component.literal(statsText), centerX, y + 73, 0xFFFFFFFF);
        }

        // Decorative subtle divider
        extractor.fill(x + 25, y + 89, x + boxWidth - 25, y + 90, 0x30FFFFFF);

        // 4. Community & Support Prompt
        extractor.centeredText(font, Component.literal("§eSupport the creator & check out the original game:"), centerX, y + 97, 0xFFFFFFFF);

        // 5. Small commands notice to replay (Clean 2-line layout that fits well within borders)
        extractor.centeredText(font, Component.literal("§7Reset Upgrades & Essence: §e/unharvest reset"), centerX, y + 148, 0xFFCCCCCC);
        extractor.centeredText(font, Component.literal("§7Regrow Wheat Arena: §e/unharvest regenerate"), centerX, y + 162, 0xFFCCCCCC);

        // Render buttons
        super.extractRenderState(extractor, mouseX, mouseY, partialTicks);
    }
}
