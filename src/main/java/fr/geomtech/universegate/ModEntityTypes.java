package fr.geomtech.universegate;

import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.event.entity.RegisterSpawnPlacementsEvent;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.entity.SpawnPlacementTypes;
import net.minecraft.world.level.levelgen.Heightmap;

public final class ModEntityTypes {

    public static final EntityType<RiftBeastEntity> RIFT_BEAST = NeoForgeRegistryBootstrap.register(
            BuiltInRegistries.ENTITY_TYPE,
            ResourceLocation.fromNamespaceAndPath(UniverseGate.MOD_ID, "rift_beast"),
            createRiftBeastType()
    );

    public static final EntityType<RiftShadeEntity> RIFT_SHADE = NeoForgeRegistryBootstrap.register(
            BuiltInRegistries.ENTITY_TYPE,
            ResourceLocation.fromNamespaceAndPath(UniverseGate.MOD_ID, "rift_shade"),
            createRiftShadeType()
    );

    public static final EntityType<RiftBeastEntity> ACID_RIFT_BEAST = NeoForgeRegistryBootstrap.register(
            BuiltInRegistries.ENTITY_TYPE,
            ResourceLocation.fromNamespaceAndPath(UniverseGate.MOD_ID, "acid_rift_beast"),
            createRiftBeastType()
    );

    public static final EntityType<RiftShadeEntity> ACID_RIFT_SHADE = NeoForgeRegistryBootstrap.register(
            BuiltInRegistries.ENTITY_TYPE,
            ResourceLocation.fromNamespaceAndPath(UniverseGate.MOD_ID, "acid_rift_shade"),
            createRiftShadeType()
    );

    public static final EntityType<AcidExplosiveEntity> NITRO_ACID_EXPLOSIVE = NeoForgeRegistryBootstrap.register(
            BuiltInRegistries.ENTITY_TYPE,
            ResourceLocation.fromNamespaceAndPath(UniverseGate.MOD_ID, "nitro_acid_explosive"),
            createAcidExplosiveType()
    );

    public static final EntityType<RustedGolemEntity> RUSTED_GOLEM = NeoForgeRegistryBootstrap.register(
            BuiltInRegistries.ENTITY_TYPE,
            ResourceLocation.fromNamespaceAndPath(UniverseGate.MOD_ID, "rusted_golem"),
            EntityType.Builder.<RustedGolemEntity>of(RustedGolemEntity::new, MobCategory.MISC)
                    .sized(1.4F, 2.7F).clientTrackingRange(10).build(null)
    );

    public static final EntityType<AcidCreeperEntity> ACID_CREEPER = NeoForgeRegistryBootstrap.register(
            BuiltInRegistries.ENTITY_TYPE,
            ResourceLocation.fromNamespaceAndPath(UniverseGate.MOD_ID, "acid_creeper"),
            EntityType.Builder.<AcidCreeperEntity>of(AcidCreeperEntity::new, MobCategory.MONSTER)
                    .sized(0.6F, 1.7F).clientTrackingRange(8).build(null)
    );

    private static EntityType<AcidExplosiveEntity> createAcidExplosiveType() {
        EntityType.Builder<AcidExplosiveEntity> builder = EntityType.Builder
                .<AcidExplosiveEntity>of((type, level) -> new AcidExplosiveEntity(type, level), MobCategory.MISC)
                .sized(0.25F, 0.25F)
                .clientTrackingRange(4)
                .updateInterval(10);
        return builder.build(null);
    }

    private static EntityType<RiftBeastEntity> createRiftBeastType() {
        EntityType.Builder<RiftBeastEntity> builder = EntityType.Builder.of(RiftBeastEntity::new, MobCategory.CREATURE)
                .sized(1.0F, 4.0F)
                .eyeHeight(3.45F)
                .clientTrackingRange(10);

        return builder.build(null);
    }

    private static EntityType<RiftShadeEntity> createRiftShadeType() {
        EntityType.Builder<RiftShadeEntity> builder = EntityType.Builder.of(RiftShadeEntity::new, MobCategory.MONSTER)
                .sized(0.7F, 2.2F)
                .eyeHeight(1.95F)
                .clientTrackingRange(8);

        return builder.build(null);
    }

    public static void register() {
    }

    public static void registerSpawnPlacements(RegisterSpawnPlacementsEvent event) {
        event.register(RIFT_BEAST, SpawnPlacementTypes.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                RiftBeastEntity::canSpawn, RegisterSpawnPlacementsEvent.Operation.REPLACE);
        event.register(RIFT_SHADE, SpawnPlacementTypes.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                RiftShadeEntity::canSpawn, RegisterSpawnPlacementsEvent.Operation.REPLACE);
    }

    public static void registerAttributes(EntityAttributeCreationEvent event) {
        event.put(RUSTED_GOLEM, RustedGolemEntity.createRustedAttributes().build());
        event.put(ACID_CREEPER, AcidCreeperEntity.createAttributes().build());
        event.put(RIFT_BEAST, RiftBeastEntity.createAttributes().build());
        event.put(RIFT_SHADE, RiftShadeEntity.createAttributes().build());
        event.put(ACID_RIFT_BEAST, RiftBeastEntity.createAttributes().build());
        event.put(ACID_RIFT_SHADE, RiftShadeEntity.createAttributes().build());
    }

    private ModEntityTypes() {}
}
