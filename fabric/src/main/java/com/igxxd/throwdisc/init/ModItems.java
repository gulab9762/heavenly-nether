package com.igxxd.throwdisc.init;

import com.igxxd.throwdisc.Constants;
import com.igxxd.throwdisc.item.ThrowingDiscItem;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;

import java.util.function.Function;

public class ModItems {
    public static final ThrowingDiscItem COPPER_THROWING_DISC = register(
        "copper_throwing_disc",
        props -> new ThrowingDiscItem(props, "copper", 5.0f, 0.4f, 1),
        new Item.Properties()
            .durability(180)
            .enchantable(10)
    );

    public static final ThrowingDiscItem IRON_THROWING_DISC = register(
        "iron_throwing_disc",
        props -> new ThrowingDiscItem(props, "iron", 7.0f, 0.6f, 2),
        new Item.Properties()
            .durability(350)
            .enchantable(14)
    );

    public static final ThrowingDiscItem GOLD_THROWING_DISC = register(
        "gold_throwing_disc",
        props -> new ThrowingDiscItem(props, "gold", 6.0f, 0.5f, 3),
        new Item.Properties()
            .durability(120)
            .enchantable(22)
    );

    public static final ThrowingDiscItem DIAMOND_THROWING_DISC = register(
        "diamond_throwing_disc",
        props -> new ThrowingDiscItem(props, "diamond", 9.5f, 0.8f, 4),
        new Item.Properties()
            .durability(1200)
            .enchantable(18)
            .rarity(Rarity.UNCOMMON)
    );

    public static final ThrowingDiscItem NETHERITE_THROWING_DISC = register(
        "netherite_throwing_disc",
        props -> new ThrowingDiscItem(props, "netherite", 12.0f, 1.2f, 6),
        new Item.Properties()
            .durability(2000)
            .fireResistant()
            .enchantable(20)
            .rarity(Rarity.RARE)
    );

    private static <T extends Item> T register(String name, Function<Item.Properties, T> factory, Item.Properties properties) {
        Identifier id = Identifier.fromNamespaceAndPath(Constants.MOD_ID, name);
        ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, id);
        properties.setId(key);
        T item = factory.apply(properties);
        return Registry.register(BuiltInRegistries.ITEM, key, item);
    }

    public static void init() {
    }
}
