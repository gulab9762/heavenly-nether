package com.unharvest;

import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Unit;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;

import java.util.List;

/**
 * Handles creation, validation, and player delivery of the Withering Scythe.
 */
public class UnharvestItems {

    public static final String SCYTHE_NAME_KEY = "Withering Scythe";

    /**
     * Creates a Withering Scythe with distinct dark styling, custom lore, and unbreakable state.
     */
    public static ItemStack createWitheringScythe() {
        ItemStack stack = new ItemStack(Items.NETHERITE_HOE);

        // Custom name
        stack.set(DataComponents.CUSTOM_NAME, Component.literal("§5§lWithering Scythe"));

        // Custom lore
        ItemLore lore = new ItemLore(List.of(
                Component.literal("§7The touch of decay."),
                Component.literal("§8Left-click wheat to wither it into a Wither Rose."),
                Component.literal("§6Press [U] or Right-Click to open Upgrades.")
        ));
        stack.set(DataComponents.LORE, lore);

        // Unbreakable
        stack.set(DataComponents.UNBREAKABLE, Unit.INSTANCE);

        return stack;
    }

    /**
     * Checks if the given item stack is a Withering Scythe.
     */
    public static boolean isWitheringScythe(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }

        Component customName = stack.get(DataComponents.CUSTOM_NAME);
        if (customName != null && customName.getString().contains(SCYTHE_NAME_KEY)) {
            return true;
        }

        return stack.getHoverName().getString().contains(SCYTHE_NAME_KEY);
    }

    /**
     * Ensures the player possesses a Withering Scythe in their inventory.
     */
    public static void ensurePlayerHasScythe(Player player) {
        if (player == null) return;

        // Check if already in inventory
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (isWitheringScythe(stack)) {
                return;
            }
        }

        // Give scythe to player
        ItemStack scythe = createWitheringScythe();
        if (!player.addItem(scythe)) {
            player.getInventory().setItem(0, scythe);
        }

        player.sendSystemMessage(
                Component.literal("§5§l[Unharvest] §fEquipped the §5§lWithering Scythe§f! Drain the wheat field. (§ePress [U] for Upgrades§f)")
        );
    }
}
