package com.unharvest.gui;

import com.unharvest.UnharvestState;
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
    private static final String YOUTUBE_URL = "https://www.youtube.com/results?search_query=Unharvest+game";
    private static final String STEAM_URL = "https://store.steampowered.com/app/5138990/Unharvest/";

    public UnharvestVictoryScreen(Screen parent) {
        super(Component.literal("Unharvest - 100% Cleared!"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int centerX = this.width / 2;
        int boxWidth = 330;
        int boxHeight = 220;
        int topY = this.height / 2 - boxHeight / 2;

        int btnWidth = 150;
        int btnY = topY + 125;

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
        int doneWidth = 120;
        addRenderableWidget(Button.builder(
                Component.literal("Continue Playing"),
                btn -> this.onClose()
        ).bounds(centerX - doneWidth / 2, topY + 185, doneWidth, 20).build());

        // Play celebratory victory fanfare
        if (this.minecraft != null) {
            this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 1.0f));
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor extractor, int mouseX, int mouseY, float partialTicks) {
        // Dark background overlay tint
        extractor.fill(0, 0, this.width, this.height, 0xAA08040C);

        int centerX = this.width / 2;
        int boxWidth = 330;
        int boxHeight = 220;
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

        // 1. Triumphant Banner Titles
        extractor.centeredText(font, Component.literal("§6§l🏆 100% FIELD WITHERED! 🏆"), centerX, y + 12, 0xFFFFFFFF);
        extractor.centeredText(font, Component.literal("§d§lCONGRATULATIONS!§r"), centerX, y + 26, 0xFFFFFFFF);

        // 2. Victory Message
        extractor.centeredText(font, Component.literal("§aYou have brought total decay upon the golden field!"), centerX, y + 44, 0xFFFFFFFF);
        extractor.centeredText(font, Component.literal("§7Every last stalk has withered into eternal ruin."), centerX, y + 56, 0xFFFFFFFF);

        // 3. Stats Summary
        String statsText = String.format(Locale.US, "§d✨ Final Essence: §f%s   §7|   §a🌾 Withered: §f%,d / %,d",
                state.getFormattedEssence(), state.getTotalWithered(), state.getFieldTotalCrops());
        extractor.centeredText(font, Component.literal(statsText), centerX, y + 76, 0xFFFFFFFF);

        // Decorative subtle divider
        extractor.fill(x + 20, y + 94, x + boxWidth - 20, y + 95, 0x40FFFFFF);

        // 4. Community & Support Prompt
        extractor.centeredText(font, Component.literal("§eSupport the creator & check out the original game:"), centerX, y + 104, 0xFFFFFFFF);

        // 5. Small commands notice to replay
        extractor.centeredText(font, Component.literal("§7Replay Commands: §e/unharvest reset §7(Reset)  |  §e/unharvest regenerate §7(Rebuild)"), centerX, y + 158, 0xFFCCCCCC);

        // Render buttons
        super.extractRenderState(extractor, mouseX, mouseY, partialTicks);
    }
}
