package com.unharvest.gui;

import com.unharvest.UnharvestState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;

/**
 * Interactive in-game menu for purchasing and inspecting Unharvest plague upgrades,
 * viewing current Blight Essence, and checking field clearance percentage.
 */
public class UnharvestUpgradeScreen extends Screen {

    private final Screen parent;
    private Button speedBtn;
    private Button radiusBtn;
    private Button yieldBtn;
    private Button chainBtn;
    private Button swarmBtn;

    public UnharvestUpgradeScreen(Screen parent) {
        super(Component.literal("Unharvest Upgrades"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int centerX = this.width / 2;
        int startY = 54;
        int buttonWidth = 280;
        int buttonHeight = 20;
        int spacing = 24;

        UnharvestState state = UnharvestState.getInstance();

        // 1. Scythe Speed
        this.speedBtn = addRenderableWidget(Button.builder(
                getSpeedText(state),
                btn -> {
                    if (state.upgradeSpeed()) {
                        playUpgradeSound();
                        updateAllButtons();
                    }
                }
        ).bounds(centerX - buttonWidth / 2, startY, buttonWidth, buttonHeight).build());

        // 2. Wither Radius
        this.radiusBtn = addRenderableWidget(Button.builder(
                getRadiusText(state),
                btn -> {
                    if (state.upgradeRadius()) {
                        playUpgradeSound();
                        updateAllButtons();
                    }
                }
        ).bounds(centerX - buttonWidth / 2, startY + spacing, buttonWidth, buttonHeight).build());

        // 3. Essence Multiplier
        this.yieldBtn = addRenderableWidget(Button.builder(
                getYieldText(state),
                btn -> {
                    if (state.upgradeYield()) {
                        playUpgradeSound();
                        updateAllButtons();
                    }
                }
        ).bounds(centerX - buttonWidth / 2, startY + spacing * 2, buttonWidth, buttonHeight).build());

        // 4. Contagion Chain
        this.chainBtn = addRenderableWidget(Button.builder(
                getChainText(state),
                btn -> {
                    if (state.upgradeChain()) {
                        playUpgradeSound();
                        updateAllButtons();
                    }
                }
        ).bounds(centerX - buttonWidth / 2, startY + spacing * 3, buttonWidth, buttonHeight).build());

        // 5. Locust Swarm (Idle Automation)
        this.swarmBtn = addRenderableWidget(Button.builder(
                getSwarmText(state),
                btn -> {
                    if (state.upgradeSwarm()) {
                        playUpgradeSound();
                        updateAllButtons();
                    }
                }
        ).bounds(centerX - buttonWidth / 2, startY + spacing * 4, buttonWidth, buttonHeight).build());

        // Bottom Navigation Buttons
        int bottomY = startY + spacing * 5 + 6;
        int halfWidth = 136;

        // Reset Button
        addRenderableWidget(Button.builder(
                Component.literal("§cReset All Progress"),
                btn -> {
                    state.resetAll();
                    updateAllButtons();
                }
        ).bounds(centerX - buttonWidth / 2, bottomY, halfWidth, buttonHeight).build());

        // Close / Done Button
        addRenderableWidget(Button.builder(
                CommonComponents.GUI_DONE,
                btn -> this.onClose()
        ).bounds(centerX + 8, bottomY, halfWidth, buttonHeight).build());

        updateAllButtons();
    }

    private void updateAllButtons() {
        UnharvestState state = UnharvestState.getInstance();
        long essence = state.getEssence();

        if (speedBtn != null) {
            speedBtn.setMessage(getSpeedText(state));
            speedBtn.active = state.getSpeedLevel() < state.getMaxSpeedLevel() && essence >= state.getSpeedCost();
        }
        if (radiusBtn != null) {
            radiusBtn.setMessage(getRadiusText(state));
            radiusBtn.active = state.getRadiusLevel() < state.getMaxRadiusLevel() && essence >= state.getRadiusCost();
        }
        if (yieldBtn != null) {
            yieldBtn.setMessage(getYieldText(state));
            yieldBtn.active = state.getYieldLevel() < state.getMaxYieldLevel() && essence >= state.getYieldCost();
        }
        if (chainBtn != null) {
            chainBtn.setMessage(getChainText(state));
            chainBtn.active = state.getChainLevel() < state.getMaxChainLevel() && essence >= state.getChainCost();
        }
        if (swarmBtn != null) {
            swarmBtn.setMessage(getSwarmText(state));
            swarmBtn.active = state.getSwarmLevel() < state.getMaxSwarmLevel() && essence >= state.getSwarmCost();
        }
    }

    private Component getSpeedText(UnharvestState state) {
        if (state.getSpeedLevel() >= state.getMaxSpeedLevel()) {
            return Component.literal("§e⚡ Scythe Speed: §aMAX §7(Cooldown: " + state.getCooldownTicks() + "t)");
        }
        return Component.literal("§e⚡ Scythe Speed: §fLvl " + state.getSpeedLevel() + "/" + state.getMaxSpeedLevel() + " §7(" + state.getCooldownTicks() + "t) §d- " + state.getSpeedCost() + " Essence");
    }

    private Component getRadiusText(UnharvestState state) {
        int r = state.getRadius();
        String area = (r == 0) ? "1x1" : (2 * r + 1) + "x" + (2 * r + 1);
        if (state.getRadiusLevel() >= state.getMaxRadiusLevel()) {
            return Component.literal("§c💥 Wither Radius: §aMAX §7(" + area + " AoE)");
        }
        return Component.literal("§c💥 Wither Radius: §fLvl " + state.getRadiusLevel() + "/" + state.getMaxRadiusLevel() + " §7(" + area + ") §d- " + state.getRadiusCost() + " Essence");
    }

    private Component getYieldText(UnharvestState state) {
        if (state.getYieldLevel() >= state.getMaxYieldLevel()) {
            return Component.literal("§d🔮 Essence Multiplier: §aMAX §7(" + state.getYieldMultiplier() + "x)");
        }
        return Component.literal("§d🔮 Essence Multiplier: §fLvl " + state.getYieldLevel() + "/" + state.getMaxYieldLevel() + " §7(" + state.getYieldMultiplier() + "x) §d- " + state.getYieldCost() + " Essence");
    }

    private Component getChainText(UnharvestState state) {
        int pct = (int) (state.getContagionChance() * 100);
        if (state.getChainLevel() >= state.getMaxChainLevel()) {
            return Component.literal("§2☣️ Contagion Chain: §aMAX §7(" + pct + "%)");
        }
        return Component.literal("§2☣️ Contagion Chain: §fLvl " + state.getChainLevel() + "/" + state.getMaxChainLevel() + " §7(" + pct + "%) §d- " + state.getChainCost() + " Essence");
    }

    private Component getSwarmText(UnharvestState state) {
        if (state.getSwarmLevel() >= state.getMaxSwarmLevel()) {
            return Component.literal("§6🦗 Locust Swarm: §aMAX §7(" + state.getSwarmWitherCount() + " crops/0.5s)");
        }
        if (state.getSwarmLevel() == 0) {
            return Component.literal("§6🦗 Locust Swarm: §8Locked §d- " + state.getSwarmCost() + " Essence");
        }
        return Component.literal("§6🦗 Locust Swarm: §fLvl " + state.getSwarmLevel() + "/" + state.getMaxSwarmLevel() + " §7(" + state.getSwarmWitherCount() + " crops) §d- " + state.getSwarmCost() + " Essence");
    }

    private void playUpgradeSound() {
        if (this.minecraft != null) {
            this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.EXPERIENCE_ORB_PICKUP, 1.25f));
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor extractor, int mouseX, int mouseY, float partialTicks) {
        this.extractMenuBackground(extractor);

        UnharvestState state = UnharvestState.getInstance();

        // Title banner
        extractor.centeredText(this.font, Component.literal("§5§lUNHARVEST - PLAGUE UPGRADES"), this.width / 2, 12, 0xFFFFFF);

        // Status lines
        extractor.centeredText(
                this.font,
                Component.literal("§d✨ Blight Essence: §f" + state.getFormattedEssence()),
                this.width / 2, 26, 0xFFAAFF
        );
        extractor.centeredText(
                this.font,
                Component.literal(String.format("§a🌿 Field Cleared: §f%d / %d (§a%.1f%%§f)",
                        state.getTotalWithered(), UnharvestState.FIELD_TOTAL_CROPS, state.getClearedPercentage())),
                this.width / 2, 38, 0xAAFFAA
        );

        super.extractRenderState(extractor, mouseX, mouseY, partialTicks);
    }

    @Override
    public void onClose() {
        if (this.parent != null && this.minecraft != null) {
            this.minecraft.setScreenAndShow(this.parent);
        } else {
            super.onClose();
        }
    }
}
