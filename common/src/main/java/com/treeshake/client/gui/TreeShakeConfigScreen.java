package com.treeshake.client.gui;

import com.treeshake.client.config.TreeShakeConfig;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

import java.util.Locale;

/**
 * In-game configuration UI for adjusting tree punch shake parameters.
 */
public class TreeShakeConfigScreen extends Screen {

    private final Screen parent;

    public TreeShakeConfigScreen(Screen parent) {
        super(Component.literal("Tree Shake Animation Settings"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int centerX = this.width / 2;
        int startY = 48;
        int buttonWidth = 240;
        int buttonHeight = 20;
        int spacing = 24;

        // 1. Toggle Tree Shake Button
        addRenderableWidget(Button.builder(
                getShakeToggleText(),
                button -> {
                    TreeShakeConfig.enableTreeShake = !TreeShakeConfig.enableTreeShake;
                    TreeShakeConfig.save();
                    button.setMessage(getShakeToggleText());
                }
        ).bounds(centerX - buttonWidth / 2, startY, buttonWidth, buttonHeight).build());

        // 2. Shake Intensity Slider (0.05x to 2.0x, default 0.25x)
        double initialIntensityNorm = (TreeShakeConfig.treeShakeIntensity - 0.05f) / 1.95f;
        addRenderableWidget(new AbstractSliderButton(
                centerX - buttonWidth / 2,
                startY + spacing,
                buttonWidth,
                buttonHeight,
                getIntensityText(TreeShakeConfig.treeShakeIntensity),
                Math.max(0.0, Math.min(1.0, initialIntensityNorm))
        ) {
            @Override
            protected void updateMessage() {
                float intensity = 0.05f + (float) this.value * 1.95f;
                setMessage(getIntensityText(intensity));
            }

            @Override
            protected void applyValue() {
                TreeShakeConfig.treeShakeIntensity = 0.05f + (float) this.value * 1.95f;
                TreeShakeConfig.save();
            }
        });

        // 3. Shake Duration Slider (6 to 30 ticks)
        double initialDurationNorm = (TreeShakeConfig.treeShakeDurationTicks - 6) / 24.0;
        addRenderableWidget(new AbstractSliderButton(
                centerX - buttonWidth / 2,
                startY + spacing * 2,
                buttonWidth,
                buttonHeight,
                getDurationText(TreeShakeConfig.treeShakeDurationTicks),
                Math.max(0.0, Math.min(1.0, initialDurationNorm))
        ) {
            @Override
            protected void updateMessage() {
                int ticks = 6 + (int) Math.round(this.value * 24.0);
                setMessage(getDurationText(ticks));
            }

            @Override
            protected void applyValue() {
                TreeShakeConfig.treeShakeDurationTicks = 6 + (int) Math.round(this.value * 24.0);
                TreeShakeConfig.save();
            }
        });

        // 4. Toggle Particles Button
        addRenderableWidget(Button.builder(
                getParticlesText(),
                button -> {
                    TreeShakeConfig.enableTreeParticles = !TreeShakeConfig.enableTreeParticles;
                    TreeShakeConfig.save();
                    button.setMessage(getParticlesText());
                }
        ).bounds(centerX - buttonWidth / 2, startY + spacing * 3, buttonWidth, buttonHeight).build());

        // 5. Toggle Sound Button
        addRenderableWidget(Button.builder(
                getSoundText(),
                button -> {
                    TreeShakeConfig.enableTreeSound = !TreeShakeConfig.enableTreeSound;
                    TreeShakeConfig.save();
                    button.setMessage(getSoundText());
                }
        ).bounds(centerX - buttonWidth / 2, startY + spacing * 4, buttonWidth, buttonHeight).build());

        // 6. Shakable Whitelist Button
        addRenderableWidget(Button.builder(
                getWhitelistButtonText(),
                button -> {
                    TreeShakeConfig.resetWhitelistToDefaults();
                    TreeShakeConfig.save();
                    button.setMessage(getWhitelistButtonText());
                }
        ).bounds(centerX - buttonWidth / 2, startY + spacing * 5, buttonWidth, buttonHeight).build());

        // 7. Reset to Defaults Button
        addRenderableWidget(Button.builder(
                Component.literal("Reset to Defaults (0.25x)"),
                button -> {
                    TreeShakeConfig.enableTreeShake = true;
                    TreeShakeConfig.treeShakeIntensity = 0.25f;
                    TreeShakeConfig.treeShakeDurationTicks = 14;
                    TreeShakeConfig.enableTreeParticles = true;
                    TreeShakeConfig.enableTreeSound = true;
                    TreeShakeConfig.resetWhitelistToDefaults();
                    TreeShakeConfig.save();
                    this.rebuildWidgets();
                }
        ).bounds(centerX - buttonWidth / 2, startY + spacing * 6, buttonWidth, buttonHeight).build());

        // 8. Done Button
        addRenderableWidget(Button.builder(
                CommonComponents.GUI_DONE,
                button -> onClose()
        ).bounds(centerX - buttonWidth / 2, startY + spacing * 7 + 6, buttonWidth, buttonHeight).build());
    }

    private Component getWhitelistButtonText() {
        return Component.literal("Shakable Whitelist: " + TreeShakeConfig.shakableBlocksWhitelist.size() + " types (Click: Reset)");
    }

    private Component getShakeToggleText() {
        return Component.literal("Tree Shake: " + (TreeShakeConfig.enableTreeShake ? "ENABLED" : "DISABLED"));
    }

    private Component getIntensityText(float intensity) {
        return Component.literal(String.format(Locale.ROOT, "Wobble Intensity: %.2fx", intensity));
    }

    private Component getDurationText(int ticks) {
        return Component.literal(String.format(Locale.ROOT, "Duration: %d ticks (%.2fs)", ticks, ticks / 20.0f));
    }

    private Component getParticlesText() {
        return Component.literal("Tree Particles: " + (TreeShakeConfig.enableTreeParticles ? "ON" : "OFF"));
    }

    private Component getSoundText() {
        return Component.literal("Tree Sound: " + (TreeShakeConfig.enableTreeSound ? "ON" : "OFF"));
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor extractor, int mouseX, int mouseY, float partialTicks) {
        this.extractMenuBackground(extractor);
        extractor.centeredText(this.font, this.title, this.width / 2, 16, 0xFFFFFF);
        extractor.centeredText(this.font, Component.literal("Configure tree punch wobble & physics"), this.width / 2, 30, 0xAAAAAA);
        super.extractRenderState(extractor, mouseX, mouseY, partialTicks);
    }

    @Override
    public void onClose() {
        TreeShakeConfig.save();
        if (this.parent != null && this.minecraft != null) {
            this.minecraft.setScreenAndShow(this.parent);
        } else {
            super.onClose();
        }
    }
}
