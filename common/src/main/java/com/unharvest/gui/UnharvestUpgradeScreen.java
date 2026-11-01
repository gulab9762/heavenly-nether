package com.unharvest.gui;

import com.unharvest.UnharvestState;
import com.unharvest.UnharvestState.NodeCategory;
import com.unharvest.UnharvestState.UpgradeNode;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;

import java.util.ArrayList;
import java.util.List;

/**
 * Interactive Graph-Tree Upgrade Menu for Unharvest matching the original game:
 * - Center: Scythe / Sickle vertical diamond tree
 * - Left: Spores / Contagion branching crimson network
 * - Right: Locust Swarm branching dark-moss network
 * - Interactive node buttons, parent-child lines, and live tooltips
 */
public class UnharvestUpgradeScreen extends Screen {

    private final Screen parent;
    private static final int NODE_SIZE = 20;

    public UnharvestUpgradeScreen(Screen parent) {
        super(Component.literal("Unharvest Upgrades"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int centerX = this.width / 2;
        int bottomY = this.height - 28;

        // Reset Button (Bottom Left)
        addRenderableWidget(Button.builder(
                Component.literal("§cReset All"),
                btn -> {
                    UnharvestState.getInstance().resetAll();
                    playUpgradeSound();
                }
        ).bounds(centerX - 140, bottomY, 90, 20).build());

        // Done / Close Button (Bottom Right)
        addRenderableWidget(Button.builder(
                CommonComponents.GUI_DONE,
                btn -> this.onClose()
        ).bounds(centerX + 50, bottomY, 90, 20).build());
    }

    private void playUpgradeSound() {
        if (this.minecraft != null) {
            this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.EXPERIENCE_ORB_PICKUP, 1.25f));
        }
    }

    private void playErrorSound() {
        if (this.minecraft != null) {
            this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_BASS, 0.8f));
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean isDown) {
        // In Minecraft 26.3 MouseButtonInfo: button 1 is left click (button 0 in legacy GLFW)
        if (event.button() <= 1 || event.input() <= 1) {
            double mouseX = event.x();
            double mouseY = event.y();
            UnharvestState state = UnharvestState.getInstance();
            int originX = this.width / 2;
            int originY = this.height / 2 + 38;
            int sx = 25;
            int sy = 24;

            for (UpgradeNode node : UnharvestState.NODES.values()) {
                if (!state.isNodeRevealed(node.id)) continue;

                int nx = originX + (int) (node.gridX * sx) - NODE_SIZE / 2;
                int ny = originY + (int) (node.gridY * sy) - NODE_SIZE / 2;

                // Generous click hitbox with 2px margin
                if (mouseX >= nx - 2 && mouseX <= nx + NODE_SIZE + 2 && mouseY >= ny - 2 && mouseY <= ny + NODE_SIZE + 2) {
                    if (state.canUnlockNode(node.id)) {
                        if (state.unlockNode(node.id)) {
                            playUpgradeSound();
                            return true;
                        }
                    } else if (!state.isNodeUnlocked(node.id)) {
                        playErrorSound();
                        return true;
                    }
                }
            }
        }
        return super.mouseClicked(event, isDown);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor extractor, int mouseX, int mouseY, float partialTicks) {
        this.extractMenuBackground(extractor);

        UnharvestState state = UnharvestState.getInstance();
        Font font = this.font;

        // Dark gothic overlay vignette
        extractor.fill(0, 0, this.width, this.height, 0x880A0C10);

        // Header Title & Stats
        extractor.centeredText(font, Component.literal("§5§lUNHARVEST §7| §dPlague Upgrade Tree"), this.width / 2, 10, 0xFFFFFFFF);

        String statsText = String.format("§d✨ Essence: §f%s   §7|   §a🌿 Cleared: §f%.1f%% §7(%d/%d)",
                state.getFormattedEssence(), state.getClearedPercentage(), state.getTotalWithered(), state.getFieldTotalCrops());
        extractor.centeredText(font, Component.literal(statsText), this.width / 2, 22, 0xFFFFFFFF);

        // Graph Origin
        int originX = this.width / 2;
        int originY = this.height / 2 + 38;
        int sx = 25;
        int sy = 24;

        // 1. Draw Connecting Lines (Only for revealed nodes!)
        for (UpgradeNode node : UnharvestState.NODES.values()) {
            if (!state.isNodeRevealed(node.id)) continue;

            int cx = originX + (int) (node.gridX * sx);
            int cy = originY + (int) (node.gridY * sy);
            boolean childUnlocked = state.isNodeUnlocked(node.id);

            for (String parentId : node.parents) {
                UpgradeNode parent = UnharvestState.NODES.get(parentId);
                if (parent == null) continue;
                if (!state.isNodeRevealed(parent.id)) continue;

                int px = originX + (int) (parent.gridX * sx);
                int py = originY + (int) (parent.gridY * sy);
                boolean parentUnlocked = state.isNodeUnlocked(parent.id);

                int lineColor;
                if (parentUnlocked && childUnlocked) {
                    lineColor = switch (node.category) {
                        case SPORES -> 0xFFD83040; // Crimson
                        case SWARM -> 0xFF42BA50;  // Green
                        case SCYTHE -> 0xFFE0C068; // Gold
                        default -> 0xFFBBBBBB;
                    };
                } else if (parentUnlocked && state.canUnlockNode(node.id)) {
                    lineColor = 0xFF888866; // Available branch
                } else {
                    lineColor = 0xFF2A2A2E; // Locked branch
                }

                drawLine(extractor, px, py, cx, cy, lineColor);
            }
        }

        // 2. Draw Nodes (Only revealed nodes are shown!)
        UpgradeNode hoveredNode = null;
        int hoveredNx = 0;
        int hoveredNy = 0;

        for (UpgradeNode node : UnharvestState.NODES.values()) {
            if (!state.isNodeRevealed(node.id)) continue;

            int nx = originX + (int) (node.gridX * sx) - NODE_SIZE / 2;
            int ny = originY + (int) (node.gridY * sy) - NODE_SIZE / 2;

            boolean isHovered = (mouseX >= nx && mouseX < nx + NODE_SIZE && mouseY >= ny && mouseY < ny + NODE_SIZE);
            if (isHovered) {
                hoveredNode = node;
                hoveredNx = nx;
                hoveredNy = ny;
            }

            renderNode(extractor, font, state, node, nx, ny, isHovered);
        }

        // 3. Super call to render buttons (Reset, Done)
        super.extractRenderState(extractor, mouseX, mouseY, partialTicks);

        // 4. Draw Tooltip for Hovered Node on Top
        if (hoveredNode != null) {
            renderNodeTooltip(extractor, font, state, hoveredNode, mouseX, mouseY);
        }
    }

    private void renderNode(GuiGraphicsExtractor extractor, Font font, UnharvestState state,
                            UpgradeNode node, int x, int y, boolean isHovered) {
        boolean unlocked = state.isNodeUnlocked(node.id);
        boolean canUnlock = state.canUnlockNode(node.id);

        // Background color
        int bgColor;
        if (node.category == NodeCategory.ROOT) {
            bgColor = 0xFF25262B;
        } else if (unlocked) {
            bgColor = switch (node.category) {
                case SPORES -> 0xFF3D1218; // Dark burgundy
                case SWARM -> 0xFF122C16;  // Dark moss
                case SCYTHE -> 0xFF2B2516; // Dark gold
                default -> 0xFF252528;
            };
        } else if (canUnlock) {
            bgColor = 0xFF1C1E18;
        } else {
            bgColor = 0xFF121214;
        }

        // Border color
        int borderColor;
        if (isHovered) {
            borderColor = 0xFFFFFFFF; // Glowing white on hover
        } else if (unlocked) {
            borderColor = switch (node.category) {
                case SPORES -> 0xFFD83040;
                case SWARM -> 0xFF42BA50;
                case SCYTHE -> 0xFFE0C068;
                default -> 0xFFDDDDDD;
            };
        } else if (canUnlock) {
            long pulse = (System.currentTimeMillis() / 250) % 2;
            borderColor = (pulse == 0) ? 0xFFFFE066 : 0xFFCCA830; // Pulsing gold
        } else {
            borderColor = 0xFF36363A; // Dim grey
        }

        // Fill background
        extractor.fill(x, y, x + NODE_SIZE, y + NODE_SIZE, bgColor);

        // 1px Border
        extractor.fill(x, y, x + NODE_SIZE, y + 1, borderColor);
        extractor.fill(x, y + NODE_SIZE - 1, x + NODE_SIZE, y + NODE_SIZE, borderColor);
        extractor.fill(x, y, x + 1, y + NODE_SIZE, borderColor);
        extractor.fill(x + NODE_SIZE - 1, y, x + NODE_SIZE, y + NODE_SIZE, borderColor);

        // Center Icon
        int textY = y + 6;
        int textColor = unlocked ? 0xFFFFFFFF : (canUnlock ? 0xFFE0E0E0 : 0xFF666666);
        extractor.centeredText(font, Component.literal(node.icon), x + NODE_SIZE / 2, textY, textColor);
    }

    private void renderNodeTooltip(GuiGraphicsExtractor extractor, Font font, UnharvestState state,
                                   UpgradeNode node, int mouseX, int mouseY) {
        boolean unlocked = state.isNodeUnlocked(node.id);
        boolean canUnlock = state.canUnlockNode(node.id);

        List<Component> lines = new ArrayList<>();

        // 1. Title
        String catColor = switch (node.category) {
            case SPORES -> "§c";
            case SWARM -> "§a";
            case SCYTHE -> "§e";
            default -> "§f";
        };
        lines.add(Component.literal(catColor + "§l" + node.title));

        // 2. Status Badge
        if (unlocked) {
            lines.add(Component.literal("§a✔ UNLOCKED"));
        } else if (canUnlock) {
            lines.add(Component.literal(String.format("§e★ CLICK TO UNLOCK §d- %d Essence", node.cost)));
        } else if (state.getEssence() < node.cost) {
            lines.add(Component.literal(String.format("§c✖ Requires %d Essence §7(Have: %s)", node.cost, state.getFormattedEssence())));
        } else {
            lines.add(Component.literal("§8🔒 LOCKED (Requires previous node)"));
        }

        // 3. Description
        lines.add(Component.literal("§7" + node.description));

        // Compute tooltip dimensions
        int maxW = 0;
        for (Component c : lines) {
            int w = font.width(c);
            if (w > maxW) maxW = w;
        }

        int tipW = maxW + 12;
        int tipH = lines.size() * 12 + 8;

        int tipX = mouseX + 12;
        int tipY = mouseY - 12;

        if (tipX + tipW > this.width - 6) {
            tipX = mouseX - tipW - 8;
        }
        if (tipY + tipH > this.height - 6) {
            tipY = this.height - tipH - 6;
        }

        // Tooltip box background with border
        extractor.fill(tipX - 1, tipY - 1, tipX + tipW + 1, tipY + tipH + 1, 0xFF4A3B66);
        extractor.fill(tipX, tipY, tipX + tipW, tipY + tipH, 0xF0101018);

        // Tooltip text
        for (int i = 0; i < lines.size(); i++) {
            extractor.text(font, lines.get(i), tipX + 6, tipY + 6 + i * 12, 0xFFFFFFFF);
        }
    }

    private void drawLine(GuiGraphicsExtractor extractor, int x0, int y0, int x1, int y1, int color) {
        int dx = Math.abs(x1 - x0);
        int dy = Math.abs(y1 - y0);
        int sx = x0 < x1 ? 1 : -1;
        int sy = y0 < y1 ? 1 : -1;
        int err = dx - dy;

        while (true) {
            extractor.fill(x0, y0, x0 + 1, y0 + 1, color);
            if (x0 == x1 && y0 == y1) break;
            int e2 = 2 * err;
            if (e2 > -dy) {
                err -= dy;
                x0 += sx;
            }
            if (e2 < dx) {
                err += dx;
                y0 += sy;
            }
        }
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
