package fr.geomtech.universegate;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.food.FoodProperties;
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

    public static final Item ENERGY_CONDENSER_ITEM = register(
            "energy_condenser",
            new BlockItem(ModBlocks.ENERGY_CONDENSER, new Item.Properties())
    );

    public static final Item METEOROLOGICAL_CONDENSER_ITEM = register(
            "meteorological_condenser",
            new BlockItem(ModBlocks.METEOROLOGICAL_CONDENSER, new Item.Properties())
    );

    public static final Item PARABOLA_BLOCK_ITEM = register(
            "parabola_block",
            new BlockItem(ModBlocks.PARABOLA_BLOCK, new Item.Properties())
    );

    public static final Item METEOROLOGICAL_CATALYST_ITEM = register(
            "meteorological_catalyst",
            new BlockItem(ModBlocks.METEOROLOGICAL_CATALYST, new Item.Properties())
    );

    public static final Item METEOROLOGICAL_CONTROLLER_ITEM = register(
            "meteorological_controller",
            new BlockItem(ModBlocks.METEOROLOGICAL_CONTROLLER, new Item.Properties())
    );

    public static final Item ENERGY_CONDUIT_ITEM = register(
            "energy_conduit",
            new BlockItem(ModBlocks.ENERGY_CONDUIT, new Item.Properties())
    );

    public static final Item SOLAR_PANEL_ITEM = register(
            "solar_panel",
            new BlockItem(ModBlocks.SOLAR_PANEL, new Item.Properties())
    );

    public static final Item ENERGY_MONITOR_ITEM = register(
            "energy_monitor",
            new BlockItem(ModBlocks.ENERGY_MONITOR, new Item.Properties())
    );

    public static final Item RIFT_SHADE_SPAWN_EGG = register(
            "rift_shade_spawn_egg",
            new SpawnEggItem(ModEntityTypes.RIFT_SHADE, 0x08080A, 0xF3F3F3, new Item.Properties())
    );

    public static final Item RIFT_BEAST_SPAWN_EGG = register(
            "rift_beast_spawn_egg",
            new SpawnEggItem(ModEntityTypes.RIFT_BEAST, 0x08080A, 0xFFFFFF, new Item.Properties())
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

    public static final Item RIFT_MEAT = register(
            "rift_meat",
            new Item(new Item.Properties().food(new FoodProperties.Builder().nutrition(3).saturationModifier(0.3F).build()))
    );

    public static final Item COOKED_RIFT_MEAT = register(
            "cooked_rift_meat",
            new Item(new Item.Properties().food(new FoodProperties.Builder().nutrition(8).saturationModifier(0.8F).build()))
    );

    public static final Item ENERGY_CRYSTAL = register(
            "energy_crystal",
            new Item(new Item.Properties().stacksTo(64))
    );

    public static final Item RIFT_CRYSTAL = register(
            "rift_crystal",
            new Item(new Item.Properties().stacksTo(64))
    );

    public static final Item RIFT_CORE_FRAGMENT = register(
            "rift_core_fragment",
            new Item(new Item.Properties())
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
