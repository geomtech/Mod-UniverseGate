package fr.geomtech.universegate;

import fr.geomtech.universegate.platform.Services;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.SpawnPlacementTypes;
import net.minecraft.world.level.levelgen.Heightmap;

public final class ModEntityTypes {

    public static final EntityType<RiftBeastEntity> RIFT_BEAST = Registry.register(
            BuiltInRegistries.ENTITY_TYPE,
            ResourceLocation.fromNamespaceAndPath(UniverseGate.MOD_ID, "rift_beast"),
            createRiftBeastType()
    );

    public static final EntityType<RiftShadeEntity> RIFT_SHADE = Registry.register(
            BuiltInRegistries.ENTITY_TYPE,
            ResourceLocation.fromNamespaceAndPath(UniverseGate.MOD_ID, "rift_shade"),
            createRiftShadeType()
    );

    private static EntityType<RiftBeastEntity> createRiftBeastType() {
        EntityType.Builder<RiftBeastEntity> builder = EntityType.Builder.of(RiftBeastEntity::new, MobCategory.CREATURE)
                .sized(1.0F, 4.0F)
                .eyeHeight(3.45F)
                .clientTrackingRange(10);

        //? if >=1.21.11 {
        /*return builder.build(ResourceKey.create(Registries.ENTITY_TYPE, ResourceLocation.fromNamespaceAndPath(UniverseGate.MOD_ID, "rift_beast")));
        *///?} else {
        return builder.build(UniverseGate.MOD_ID + ":rift_beast");
        //?}
    }

    private static EntityType<RiftShadeEntity> createRiftShadeType() {
        EntityType.Builder<RiftShadeEntity> builder = EntityType.Builder.of(RiftShadeEntity::new, MobCategory.MONSTER)
                .sized(0.7F, 2.2F)
                .eyeHeight(1.95F)
                .clientTrackingRange(8);
        //? if >=1.21.11 {
        /*// Replaces RiftShadeEntity.shouldDespawnInPeaceful() (removed from Mob)
        builder = builder.notInPeaceful();
        *///?}

        //? if >=1.21.11 {
        /*return builder.build(ResourceKey.create(Registries.ENTITY_TYPE, ResourceLocation.fromNamespaceAndPath(UniverseGate.MOD_ID, "rift_shade")));
        *///?} else {
        return builder.build(UniverseGate.MOD_ID + ":rift_shade");
        //?}
    }

    public static void register() {
        Services.PLATFORM.registerEntityAttributes(RIFT_BEAST, RiftBeastEntity::createAttributes);
        Services.PLATFORM.registerEntityAttributes(RIFT_SHADE, RiftShadeEntity::createAttributes);
        Services.PLATFORM.registerSpawnPlacement(
                RIFT_BEAST,
                SpawnPlacementTypes.ON_GROUND,
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                RiftBeastEntity::canSpawn
        );
        Services.PLATFORM.registerSpawnPlacement(
                RIFT_SHADE,
                SpawnPlacementTypes.ON_GROUND,
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                RiftShadeEntity::canSpawn
        );
    }

    private ModEntityTypes() {}
}
