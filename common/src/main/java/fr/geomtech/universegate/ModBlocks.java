package fr.geomtech.universegate;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.Registry;

public final class ModBlocks {

    public static final Block VOID_BLOCK = register(
            "void_block",
            new Block(properties("void_block", BlockBehaviour.Properties.ofFullCopy(Blocks.OBSIDIAN)
                    .strength(5.0f, 6.0f)
                    .requiresCorrectToolForDrops()))
    );

    public static final Block LIGHT_BLOCK = register(
            "light_block",
            new Block(properties("light_block", BlockBehaviour.Properties.ofFullCopy(Blocks.QUARTZ_BLOCK)
                    .strength(1.5f, 6.0f)
                    .requiresCorrectToolForDrops()
                    .lightLevel(state -> 15)))
    );

    public static final Block KELO_LOG = register(
            "kelo_log",
            new RotatedPillarBlock(properties("kelo_log", BlockBehaviour.Properties.ofFullCopy(Blocks.SPRUCE_LOG)
                    .strength(2.0f, 3.0f)))
    );

    public static final Block KELO_PLANKS = register(
            "kelo_planks",
            new Block(properties("kelo_planks", BlockBehaviour.Properties.ofFullCopy(Blocks.SPRUCE_PLANKS)
                    .strength(2.0f, 3.0f)))
    );

    public static final Block WHITE_PURPUR_BLOCK = register(
            "white_purpur_block",
            new Block(properties("white_purpur_block", BlockBehaviour.Properties.ofFullCopy(Blocks.PURPUR_BLOCK)
                    .strength(1.5f, 6.0f)
                    .requiresCorrectToolForDrops()))
    );

    public static final Block WHITE_PURPUR_PILLAR = register(
            "white_purpur_pillar",
            new RotatedPillarBlock(properties("white_purpur_pillar", BlockBehaviour.Properties.ofFullCopy(Blocks.PURPUR_PILLAR)
                    .strength(1.5f, 6.0f)
                    .requiresCorrectToolForDrops()))
    );

    public static final Block LIGHT_BEAM_EMITTER = register(
            "light_beam_emitter",
            new LightBeamEmitterBlock(properties("light_beam_emitter", BlockBehaviour.Properties.ofFullCopy(Blocks.SEA_LANTERN)
                    .strength(1.5f, 6.0f)
                    .lightLevel(state -> 15)))
    );

    public static final Block ENERGY_CONDENSER = register(
            "energy_condenser",
            new EnergyCondenserBlock(properties("energy_condenser", BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK)
                    .strength(3.0f, 6.0f)
                    .requiresCorrectToolForDrops()
                    .noOcclusion()))
    );

    public static final Block METEOROLOGICAL_CONDENSER = register(
            "meteorological_condenser",
            new Block(properties("meteorological_condenser", BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK)
                    .strength(3.0f, 6.0f)
                    .requiresCorrectToolForDrops()
                    .noOcclusion()))
    );

    public static final Block PARABOLA_BLOCK = register(
            "parabola_block",
            new ParabolaBlock(properties("parabola_block", BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK)
                    .strength(3.0f, 6.0f)
                    .requiresCorrectToolForDrops()
                    .noOcclusion()))
    );

    public static final Block METEOROLOGICAL_CATALYST = register(
            "meteorological_catalyst",
            new CrystalCondenserBlock(properties("meteorological_catalyst", BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK)
                    .strength(3.0f, 6.0f)
                    .requiresCorrectToolForDrops()
                    .noOcclusion()))
    );

    public static final Block METEOROLOGICAL_CONTROLLER = register(
            "meteorological_controller",
            new MeteorologicalControllerBlock(properties("meteorological_controller", BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK)
                    .strength(3.0f, 6.0f)
                    .requiresCorrectToolForDrops()
                    .noOcclusion()))
    );

    public static final Block ENERGY_CONDUIT = register(
            "energy_conduit",
            new EnergyConduitBlock(properties("energy_conduit", BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK)
                    .strength(2.5f, 6.0f)
                    .requiresCorrectToolForDrops()
                    .noOcclusion()))
    );

    public static final Block SOLAR_PANEL = register(
            "solar_panel",
            new SolarPanelBlock(properties("solar_panel", BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK)
                    .strength(3.0f, 6.0f)
                    .requiresCorrectToolForDrops()
                    .noOcclusion()))
    );

    public static final Block COMBUSTION_GENERATOR = register(
            "combustion_generator",
            new CombustionGeneratorBlock(properties("combustion_generator", BlockBehaviour.Properties.ofFullCopy(Blocks.BLAST_FURNACE)
                    .strength(3.5f, 6.0f)
                    .requiresCorrectToolForDrops()
                    .lightLevel(CombustionGeneratorBlock::lightLevel)))
    );

    public static final Block ENERGY_MONITOR = register(
            "energy_monitor",
            new EnergyMonitorBlock(properties("energy_monitor", BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK)
                    .strength(2.5f, 6.0f)
                    .requiresCorrectToolForDrops()
                    .noOcclusion()))
    );

    public static final Block ZPC_INTERFACE_CONTROLLER = register(
            "zpc_interface_controller",
            new ZpcInterfaceControllerBlock(properties("zpc_interface_controller", BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK)
                    .strength(3.5f, 6.0f)
                    .requiresCorrectToolForDrops()
                    .lightLevel(ZpcInterfaceControllerBlock::lightLevel)
                    .noOcclusion()))
    );

    public static final Block PORTAL_CORE = register(
            "portal_core",
            new PortalCoreBlock(properties("portal_core", BlockBehaviour.Properties.ofFullCopy(Blocks.OBSIDIAN)
                    .strength(50.0f, 1200.0f)
                    .requiresCorrectToolForDrops()))
    );

    public static final Block PORTAL_FRAME = register(
            "portal_frame",
            new PortalFrameBlock(properties("portal_frame", BlockBehaviour.Properties.ofFullCopy(Blocks.OBSIDIAN)
                    .strength(60.0f, 1200.0f)
                    .requiresCorrectToolForDrops()
                    .lightLevel(PortalFrameBlock::lightLevel)))
    );

    public static final Block PORTAL_KEYBOARD = register(
            "portal_keyboard",
            new PortalKeyboardBlock(properties("portal_keyboard", BlockBehaviour.Properties.ofFullCopy(Blocks.OBSIDIAN)
                    .strength(60.0f, 1200.0f)
                    .requiresCorrectToolForDrops()
                    .lightLevel(PortalKeyboardBlock::lightLevel)))
    );

    public static final Block PORTAL_ACTIVATOR = register(
            "portal_activator",
            new PortalActivatorBlock(properties("portal_activator", BlockBehaviour.Properties.ofFullCopy(Blocks.OBSIDIAN)
                    .strength(60.0f, 1200.0f)
                    .requiresCorrectToolForDrops()
                    .lightLevel(PortalKeyboardBlock::lightLevel)))
    );

    public static final Block PORTAL_NATURAL_KEYBOARD = register(
            "portal_natural_keyboard",
            new PortalNaturalKeyboardBlock(properties("portal_natural_keyboard", BlockBehaviour.Properties.ofFullCopy(Blocks.OBSIDIAN)
                    .strength(60.0f, 1200.0f)
                    .requiresCorrectToolForDrops()
                    .lightLevel(PortalNaturalKeyboardBlock::lightLevel)
                    .noLootTable()))
    );

    public static final Block PORTAL_FIELD = register(
            "portal_field",
            new PortalFieldBlock(
                    properties("portal_field", BlockBehaviour.Properties.of()
                            .noCollission()
                            .strength(1.0F, 0.0F)
                            .lightLevel(state -> 10)
                            .noLootTable()
                            .noOcclusion()
                            .isSuffocating((state, level, pos) -> false)
                            .isViewBlocking((state, level, pos) -> false))
            )
    );

    public static final Block CHARGED_LIGHTNING_ROD = register(
            "charged_lightning_rod",
            new ChargedLightningRodBlock(properties("charged_lightning_rod", BlockBehaviour.Properties.ofFullCopy(Blocks.LIGHTNING_ROD)))
    );

    public static final Block LIGHTNING_ANTENNA = register(
            "lightning_antenna",
            new LightningAntennaBlock(properties("lightning_antenna", BlockBehaviour.Properties.ofFullCopy(Blocks.LIGHTNING_ROD)
                    .strength(3.0f, 6.0f)
                    .noOcclusion()
                    .lightLevel(LightningAntennaBlock::lightLevel)))
    );

    public static final Block ELECTRIC_WIRE = register(
            "electric_wire",
            new ElectricWireBlock(properties("electric_wire", BlockBehaviour.Properties.of()
                    .noCollission()
                    .instabreak()
                    .noOcclusion()
                    .sound(ModSounds.ELECTRIC_WIRE_SOUND_TYPE)
                    .pushReaction(net.minecraft.world.level.material.PushReaction.DESTROY)))
    );

    public static final Block CROCODILE_CLIP = register(
            "crocodile_clip",
            new CrocodileClipBlock(properties("crocodile_clip", BlockBehaviour.Properties.of()
                    .noCollission()
                    .strength(0.3f)
                    .noOcclusion()
                    .sound(net.minecraft.world.level.block.SoundType.METAL)
                    .pushReaction(net.minecraft.world.level.material.PushReaction.DESTROY)))
    );

    public static final Block DARK_MATTER_BLOCK = register(
            "dark_matter_block",
            //? if >=1.21.11 {
            /*// The constructor is protected in 1.21.11 and no loader opens it.
            new LiquidBlock(ModFluids.STILL_DARK_MATTER, properties("dark_matter_block", BlockBehaviour.Properties.ofFullCopy(Blocks.WATER).noLootTable())) {}
            *///?} else {
            new LiquidBlock(ModFluids.STILL_DARK_MATTER, properties("dark_matter_block", BlockBehaviour.Properties.ofFullCopy(Blocks.WATER).noLootTable()))
            //?}
    );

    public static final Block RIFT_REFINER = register(
            "rift_refiner",
            new RiftRefinerBlock(properties("rift_refiner", BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK).strength(3.5f).requiresCorrectToolForDrops()))
    );

    public static final Block FLUID_PIPE = register(
            "fluid_pipe",
            new FluidPipeBlock(properties("fluid_pipe", BlockBehaviour.Properties.ofFullCopy(Blocks.GLASS).noOcclusion()))
    );

    public static final Block DARK_ENERGY_CONDUIT = register(
            "dark_energy_conduit",
            new DarkEnergyConduitBlock(properties("dark_energy_conduit", BlockBehaviour.Properties.ofFullCopy(Blocks.OBSIDIAN).strength(2.0f).noOcclusion()))
    );

    public static final Block DARK_ENERGY_GENERATOR = register(
            "dark_energy_generator",
            new DarkEnergyGeneratorBlock(properties("dark_energy_generator", BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK)))
    );

    public static final Block MOB_CLONER = register(
            "mob_cloner",
            new MobClonerBlock(properties("mob_cloner", BlockBehaviour.Properties.ofFullCopy(Blocks.OBSIDIAN)
                    .strength(4.0F, 8.0F)
                    .requiresCorrectToolForDrops()))
    );

    public static final Block MOB_CLONER_CONTROLLER = register(
            "mob_cloner_controller",
            new MobClonerControllerBlock(properties("mob_cloner_controller", BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK)
                    .strength(3.0F, 6.0F)
                    .requiresCorrectToolForDrops()))
    );

    public static final Block RIFT_CRYSTAL_CLUSTER = register(
            "rift_crystal_cluster",
            new net.minecraft.world.level.block.AmethystClusterBlock(7.0F, 3.0F,
                    properties("rift_crystal_cluster", BlockBehaviour.Properties.ofFullCopy(Blocks.AMETHYST_CLUSTER)
                            .strength(1.5F)
                            .requiresCorrectToolForDrops()
                            .lightLevel(state -> 6)))
    );

    /** Block properties for the block registered as {@code id} (since 1.21.2 a block needs its id up front). */
    private static BlockBehaviour.Properties properties(String id, BlockBehaviour.Properties properties) {
        //? if >=1.21.11 {
        /*return properties.setId(ResourceKey.create(Registries.BLOCK, ResourceLocation.fromNamespaceAndPath(UniverseGate.MOD_ID, id)));
        *///?} else {
        return properties;
        //?}
    }

    private static Block register(String id, Block block) {
        return Registry.register(
                BuiltInRegistries.BLOCK,
                ResourceLocation.fromNamespaceAndPath(UniverseGate.MOD_ID, id),
                block
        );
    }

    public static void register() {}

    private ModBlocks() {}
}
