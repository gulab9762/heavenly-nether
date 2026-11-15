package com.igxxd.throwdisc.init;

import com.igxxd.throwdisc.Constants;
import com.igxxd.throwdisc.entity.ThrownDiscEntity;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

public class ModEntities {
    public static final Identifier THROWN_DISC_ID = Identifier.fromNamespaceAndPath(Constants.MOD_ID, "thrown_disc");
    public static final ResourceKey<EntityType<?>> THROWN_DISC_KEY = ResourceKey.create(Registries.ENTITY_TYPE, THROWN_DISC_ID);

    public static final EntityType<ThrownDiscEntity> THROWN_DISC = Registry.register(
        BuiltInRegistries.ENTITY_TYPE,
        THROWN_DISC_ID,
        EntityType.Builder.<ThrownDiscEntity>of(ThrownDiscEntity::new, MobCategory.MISC)
            .sized(0.5f, 0.2f)
            .clientTrackingRange(64)
            .updateInterval(1)
            .build(THROWN_DISC_KEY)
    );

    public static void init() {
    }
}
