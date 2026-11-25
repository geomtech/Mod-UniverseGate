package fr.geomtech.universegate;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.Registry;

public final class ModItems {

    public static final Item VOID_BLOCK_ITEM = register(
            "void_block",
            new BlockItem(ModBlocks.VOID_BLOCK, blockItemProperties("void_block"))
    );

    public static final Item LIGHT_BLOCK_ITEM = register(
            "light_block",
            new BlockItem(ModBlocks.LIGHT_BLOCK, blockItemProperties("light_block"))
    );

    public static final Item KELO_LOG_ITEM = register(
            "kelo_log",
            new BlockItem(ModBlocks.KELO_LOG, blockItemProperties("kelo_log"))
    );

    public static final Item KELO_PLANKS_ITEM = register(
            "kelo_planks",
            new BlockItem(ModBlocks.KELO_PLANKS, blockItemProperties("kelo_planks"))
    );

    public static final Item WHITE_PURPUR_BLOCK_ITEM = register(
            "white_purpur_block",
            new BlockItem(ModBlocks.WHITE_PURPUR_BLOCK, blockItemProperties("white_purpur_block"))
    );

    public static final Item WHITE_PURPUR_PILLAR_ITEM = register(
            "white_purpur_pillar",
            new BlockItem(ModBlocks.WHITE_PURPUR_PILLAR, blockItemProperties("white_purpur_pillar"))
    );

    public static final Item LIGHT_BEAM_EMITTER_ITEM = register(
            "light_beam_emitter",
            new BlockItem(ModBlocks.LIGHT_BEAM_EMITTER, blockItemProperties("light_beam_emitter"))
    );

    public static final Item ENERGY_CONDENSER_ITEM = register(
            "energy_condenser",
            new BlockItem(ModBlocks.ENERGY_CONDENSER, blockItemProperties("energy_condenser"))
    );

    public static final Item METEOROLOGICAL_CONDENSER_ITEM = register(
            "meteorological_condenser",
            new BlockItem(ModBlocks.METEOROLOGICAL_CONDENSER, blockItemProperties("meteorological_condenser"))
    );

    public static final Item PARABOLA_BLOCK_ITEM = register(
            "parabola_block",
            new BlockItem(ModBlocks.PARABOLA_BLOCK, blockItemProperties("parabola_block"))
    );

    public static final Item METEOROLOGICAL_CATALYST_ITEM = register(
            "meteorological_catalyst",
            new BlockItem(ModBlocks.METEOROLOGICAL_CATALYST, blockItemProperties("meteorological_catalyst"))
    );

    public static final Item METEOROLOGICAL_CONTROLLER_ITEM = register(
            "meteorological_controller",
            new BlockItem(ModBlocks.METEOROLOGICAL_CONTROLLER, blockItemProperties("meteorological_controller"))
    );

    public static final Item ENERGY_CONDUIT_ITEM = register(
            "energy_conduit",
            new BlockItem(ModBlocks.ENERGY_CONDUIT, blockItemProperties("energy_conduit"))
    );

    public static final Item SOLAR_PANEL_ITEM = register(
            "solar_panel",
            new BlockItem(ModBlocks.SOLAR_PANEL, blockItemProperties("solar_panel"))
    );

    public static final Item COMBUSTION_GENERATOR_ITEM = register(
            "combustion_generator",
            new BlockItem(ModBlocks.COMBUSTION_GENERATOR, blockItemProperties("combustion_generator"))
    );

    public static final Item ENERGY_MONITOR_ITEM = register(
            "energy_monitor",
            new BlockItem(ModBlocks.ENERGY_MONITOR, blockItemProperties("energy_monitor"))
    );

    public static final Item ZPC_INTERFACE_CONTROLLER_ITEM = register(
            "zpc_interface_controller",
            new BlockItem(ModBlocks.ZPC_INTERFACE_CONTROLLER, blockItemProperties("zpc_interface_controller"))
    );

    public static final Item RIFT_SHADE_SPAWN_EGG = register(
            "rift_shade_spawn_egg",
            //? if >=1.21.11 {
            /*new SpawnEggItem(properties("rift_shade_spawn_egg").spawnEgg(ModEntityTypes.RIFT_SHADE))
            *///?} else {
            new SpawnEggItem(ModEntityTypes.RIFT_SHADE, 0x08080A, 0xF3F3F3, properties("rift_shade_spawn_egg"))
            //?}
    );

    public static final Item RIFT_BEAST_SPAWN_EGG = register(
            "rift_beast_spawn_egg",
            //? if >=1.21.11 {
            /*new SpawnEggItem(properties("rift_beast_spawn_egg").spawnEgg(ModEntityTypes.RIFT_BEAST))
            *///?} else {
            new SpawnEggItem(ModEntityTypes.RIFT_BEAST, 0x08080A, 0xFFFFFF, properties("rift_beast_spawn_egg"))
            //?}
    );

    public static final Item PORTAL_CORE_ITEM = register(
            "portal_core",
            new BlockItem(ModBlocks.PORTAL_CORE, blockItemProperties("portal_core"))
    );

    public static final Item PORTAL_FRAME_ITEM = register(
            "portal_frame",
            new BlockItem(ModBlocks.PORTAL_FRAME, blockItemProperties("portal_frame"))
    );

    public static final Item PORTAL_KEYBOARD_ITEM = register(
            "portal_keyboard",
            new BlockItem(ModBlocks.PORTAL_KEYBOARD, blockItemProperties("portal_keyboard"))
    );

    public static final Item PORTAL_ACTIVATOR_ITEM = register(
            "portal_activator",
            new BlockItem(ModBlocks.PORTAL_ACTIVATOR, blockItemProperties("portal_activator"))
    );

    public static final Item PORTAL_NATURAL_KEYBOARD_ITEM = register(
            "portal_natural_keyboard",
            new BlockItem(ModBlocks.PORTAL_NATURAL_KEYBOARD, blockItemProperties("portal_natural_keyboard"))
    );

    public static final Item PORTAL_MOBILE_KEYBOARD = register(
            "portal_mobile_keyboard",
            new PortalMobileKeyboardItem(properties("portal_mobile_keyboard"))
    );

    public static final Item LIGHTNING_ANTENNA_ITEM = register(
            "lightning_antenna",
            new BlockItem(ModBlocks.LIGHTNING_ANTENNA, blockItemProperties("lightning_antenna"))
    );

    public static final Item ELECTRIC_WIRE_ITEM = register(
            "electric_wire",
            new BlockItem(ModBlocks.ELECTRIC_WIRE, blockItemProperties("electric_wire"))
    );

    public static final Item CROCODILE_CLIP_ITEM = register(
            "crocodile_clip",
            new BlockItem(ModBlocks.CROCODILE_CLIP, blockItemProperties("crocodile_clip"))
    );

    public static final Item CHARGED_LIGHTNING_ROD_ITEM = register(
            "charged_lightning_rod",
            new BlockItem(ModBlocks.CHARGED_LIGHTNING_ROD, blockItemProperties("charged_lightning_rod"))
    );

    public static final Item RIFT_MEAT = register(
            "rift_meat",
            new Item(properties("rift_meat").food(new FoodProperties.Builder().nutrition(3).saturationModifier(0.3F).build()))
    );

    public static final Item COOKED_RIFT_MEAT = register(
            "cooked_rift_meat",
            new Item(properties("cooked_rift_meat").food(new FoodProperties.Builder().nutrition(8).saturationModifier(0.8F).build()))
    );

    public static final Item ENERGY_CRYSTAL = register(
            "energy_crystal",
            new Item(properties("energy_crystal").stacksTo(64))
    );

    public static final Item RIFT_CRYSTAL = register(
            "rift_crystal",
            new Item(properties("rift_crystal").stacksTo(64))
    );

    public static final Item DNA_EXTRACTOR = register(
            "dna_extractor",
            new DnaExtractorItem(properties("dna_extractor"))
    );

    public static final Item DNA = register(
            "dna",
            new DnaSampleItem(properties("dna"))
    );

    public static final Item RIFT_CORE = register(
            "rift_core",
            new Item(properties("rift_core").stacksTo(16).rarity(net.minecraft.world.item.Rarity.EPIC))
    );

    public static final Item RIFT_CRYSTAL_CLUSTER_ITEM = register(
            "rift_crystal_cluster",
            new BlockItem(ModBlocks.RIFT_CRYSTAL_CLUSTER, blockItemProperties("rift_crystal_cluster"))
    );

    public static final Item ZPC = register(
            "zpc",
            new ZpcItem(properties("zpc"))
    );

    public static final Item RIFT_REFINER_ITEM = register(
            "rift_refiner",
            new BlockItem(ModBlocks.RIFT_REFINER, blockItemProperties("rift_refiner"))
    );

    public static final Item DARK_MATTER_BUCKET = register(
            "dark_matter_bucket",
            new BucketItem(ModFluids.STILL_DARK_MATTER, properties("dark_matter_bucket").stacksTo(1).craftRemainder(net.minecraft.world.item.Items.BUCKET))
    );

    public static final Item FLUID_PIPE_ITEM = register(
            "fluid_pipe",
            new BlockItem(ModBlocks.FLUID_PIPE, blockItemProperties("fluid_pipe"))
    );

    public static final Item DARK_ENERGY_CONDUIT_ITEM = register(
            "dark_energy_conduit",
            new BlockItem(ModBlocks.DARK_ENERGY_CONDUIT, blockItemProperties("dark_energy_conduit"))
    );

    public static final Item DARK_ENERGY_GENERATOR_ITEM = register(
            "dark_energy_generator",
            new BlockItem(ModBlocks.DARK_ENERGY_GENERATOR, blockItemProperties("dark_energy_generator"))
    );

    public static final Item MOB_CLONER_ITEM = register(
            "mob_cloner",
            new BlockItem(ModBlocks.MOB_CLONER, blockItemProperties("mob_cloner"))
    );

    public static final Item MOB_CLONER_CONTROLLER_ITEM = register(
            "mob_cloner_controller",
            new BlockItem(ModBlocks.MOB_CLONER_CONTROLLER, blockItemProperties("mob_cloner_controller"))
    );

    /** Item properties for the item registered as {@code id} (since 1.21.2 an item needs its id up front). */
    private static Item.Properties properties(String id) {
        Item.Properties properties = new Item.Properties();
        //? if >=1.21.11 {
        /*properties.setId(ResourceKey.create(Registries.ITEM, ResourceLocation.fromNamespaceAndPath(UniverseGate.MOD_ID, id)));
        *///?}
        return properties;
    }

    /** Like {@link #properties}, and the item keeps the block's name, as a BlockItem did before 1.21.2. */
    private static Item.Properties blockItemProperties(String id) {
        //? if >=1.21.11 {
        /*return properties(id).useBlockDescriptionPrefix();
        *///?} else {
        return properties(id);
        //?}
    }

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
