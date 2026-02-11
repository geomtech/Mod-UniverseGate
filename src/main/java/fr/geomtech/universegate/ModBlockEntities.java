package fr.geomtech.universegate;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.entity.BlockEntityType;

public final class ModBlockEntities {

    public static final BlockEntityType<PortalCoreBlockEntity> PORTAL_CORE =
            Registry.register(
                    BuiltInRegistries.BLOCK_ENTITY_TYPE,
                    ResourceLocation.fromNamespaceAndPath(UniverseGate.MOD_ID, "portal_core"),
                    BlockEntityType.Builder.of(PortalCoreBlockEntity::new, ModBlocks.PORTAL_CORE).build(null)
            );

    public static final BlockEntityType<PortalKeyboardBlockEntity> PORTAL_KEYBOARD =
            Registry.register(
                    BuiltInRegistries.BLOCK_ENTITY_TYPE,
                    ResourceLocation.fromNamespaceAndPath(UniverseGate.MOD_ID, "portal_keyboard"),
                    BlockEntityType.Builder.of(PortalKeyboardBlockEntity::new, ModBlocks.PORTAL_KEYBOARD).build(null)
            );


    public static void register() {}

    private ModBlockEntities() {}
}
