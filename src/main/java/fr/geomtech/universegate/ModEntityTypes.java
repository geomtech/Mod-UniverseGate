package fr.geomtech.universegate;

import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricEntityType;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.SpawnPlacements;
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

        return ((FabricEntityType.Builder<RiftBeastEntity>) builder).build();
    }

    private static EntityType<RiftShadeEntity> createRiftShadeType() {
        EntityType.Builder<RiftShadeEntity> builder = EntityType.Builder.of(RiftShadeEntity::new, MobCategory.MONSTER)
                .sized(0.7F, 2.2F)
                .eyeHeight(1.95F)
                .clientTrackingRange(8);

        return ((FabricEntityType.Builder<RiftShadeEntity>) builder).build();
    }

    public static void register() {
        FabricDefaultAttributeRegistry.register(RIFT_BEAST, RiftBeastEntity.createAttributes());
        FabricDefaultAttributeRegistry.register(RIFT_SHADE, RiftShadeEntity.createAttributes());
        SpawnPlacements.register(
                RIFT_BEAST,
                SpawnPlacementTypes.ON_GROUND,
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                RiftBeastEntity::canSpawn
        );
        SpawnPlacements.register(
                RIFT_SHADE,
                SpawnPlacementTypes.ON_GROUND,
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                RiftShadeEntity::canSpawn
        );
    }

    private ModEntityTypes() {}
}
