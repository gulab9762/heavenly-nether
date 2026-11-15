package com.igxxd.throwdisc.init;

import com.igxxd.throwdisc.Constants;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;

public class ModEnchantments {
    public static final Identifier BOUNCE_ID = Identifier.fromNamespaceAndPath(Constants.MOD_ID, "bounce");
    public static final Identifier SPLIT_ID = Identifier.fromNamespaceAndPath(Constants.MOD_ID, "split");

    public static final ResourceKey<Enchantment> BOUNCE = ResourceKey.create(Registries.ENCHANTMENT, BOUNCE_ID);
    public static final ResourceKey<Enchantment> SPLIT = ResourceKey.create(Registries.ENCHANTMENT, SPLIT_ID);

    public static int getBounceLevel(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return 0;
        ItemEnchantments enchantments = stack.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY);
        for (var entry : enchantments.entrySet()) {
            Holder<Enchantment> holder = entry.getKey();
            if (holder.unwrapKey().isPresent()) {
                Identifier key = holder.unwrapKey().get().identifier();
                if (key.equals(BOUNCE_ID)) {
                    return entry.getIntValue();
                }
            }
        }
        return 0;
    }

    public static boolean hasSplit(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        ItemEnchantments enchantments = stack.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY);
        for (var entry : enchantments.entrySet()) {
            Holder<Enchantment> holder = entry.getKey();
            if (holder.unwrapKey().isPresent()) {
                Identifier key = holder.unwrapKey().get().identifier();
                if (key.equals(SPLIT_ID)) {
                    return entry.getIntValue() > 0;
                }
            }
        }
        return false;
    }
}
