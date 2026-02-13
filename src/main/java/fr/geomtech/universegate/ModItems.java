package fr.geomtech.universegate;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CompassItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.Registry;

public final class ModItems {

    public static final Item VOID_BLOCK_ITEM = register(
            "void_block",
            new BlockItem(ModBlocks.VOID_BLOCK, new Item.Properties())
    );

    public static final Item LIGHT_BLOCK_ITEM = register(
            "light_block",
            new BlockItem(ModBlocks.LIGHT_BLOCK, new Item.Properties())
    );

    public static final Item KELO_LOG_ITEM = register(
            "kelo_log",
            new BlockItem(ModBlocks.KELO_LOG, new Item.Properties())
    );

    public static final Item KELO_PLANKS_ITEM = register(
            "kelo_planks",
            new BlockItem(ModBlocks.KELO_PLANKS, new Item.Properties())
    );

    public static final Item WHITE_PURPUR_BLOCK_ITEM = register(
            "white_purpur_block",
            new BlockItem(ModBlocks.WHITE_PURPUR_BLOCK, new Item.Properties())
    );

    public static final Item WHITE_PURPUR_PILLAR_ITEM = register(
            "white_purpur_pillar",
            new BlockItem(ModBlocks.WHITE_PURPUR_PILLAR, new Item.Properties())
    );

    public static final Item LIGHT_BEAM_EMITTER_ITEM = register(
            "light_beam_emitter",
            new BlockItem(ModBlocks.LIGHT_BEAM_EMITTER, new Item.Properties())
    );

    public static final Item RIFT_SHADE_SPAWN_EGG = register(
            "rift_shade_spawn_egg",
            new SpawnEggItem(ModEntityTypes.RIFT_SHADE, 0x08080A, 0xF3F3F3, new Item.Properties())
    );

    public static final Item RIFT_COMPASS = register(
            "rift_compass",
            new CompassItem(new Item.Properties().stacksTo(1))
    );

    public static final Item PORTAL_CORE_ITEM = register(
            "portal_core",
            new BlockItem(ModBlocks.PORTAL_CORE, new Item.Properties())
    );

    public static final Item PORTAL_FRAME_ITEM = register(
            "portal_frame",
            new BlockItem(ModBlocks.PORTAL_FRAME, new Item.Properties())
    );

    public static final Item PORTAL_KEYBOARD_ITEM = register(
            "portal_keyboard",
            new BlockItem(ModBlocks.PORTAL_KEYBOARD, new Item.Properties())
    );

    public static final Item CHARGED_LIGHTNING_ROD_ITEM = register(
            "charged_lightning_rod",
            new BlockItem(ModBlocks.CHARGED_LIGHTNING_ROD, new Item.Properties())
    );

    public static final Item RIFT_ASH = register(
            "rift_ash",
            new Item(new Item.Properties().stacksTo(64))
    );

    private static Item register(String id, Item item) {
        return Registry.register(
                BuiltInRegistries.ITEM,
                ResourceLocation.fromNamespaceAndPath(UniverseGate.MOD_ID, id),
                item
        );
    }

    public static void register() {}

    private ModItems() {}
}
