package fr.geomtech.universegate;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.Registry;

public final class ModBlocks {

    public static final Block VOID_BLOCK = register(
            "void_block",
            new Block(BlockBehaviour.Properties.ofFullCopy(Blocks.OBSIDIAN)
                    .strength(5.0f, 6.0f)
                    .requiresCorrectToolForDrops())
    );

    public static final Block LIGHT_BLOCK = register(
            "light_block",
            new Block(BlockBehaviour.Properties.ofFullCopy(Blocks.QUARTZ_BLOCK)
                    .strength(1.5f, 6.0f)
                    .requiresCorrectToolForDrops()
                    .lightLevel(state -> 15))
    );

    public static final Block KELO_LOG = register(
            "kelo_log",
            new RotatedPillarBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.SPRUCE_LOG)
                    .strength(2.0f, 3.0f))
    );

    public static final Block KELO_PLANKS = register(
            "kelo_planks",
            new Block(BlockBehaviour.Properties.ofFullCopy(Blocks.SPRUCE_PLANKS)
                    .strength(2.0f, 3.0f))
    );

    public static final Block WHITE_PURPUR_BLOCK = register(
            "white_purpur_block",
            new Block(BlockBehaviour.Properties.ofFullCopy(Blocks.PURPUR_BLOCK)
                    .strength(1.5f, 6.0f)
                    .requiresCorrectToolForDrops())
    );

    public static final Block WHITE_PURPUR_PILLAR = register(
            "white_purpur_pillar",
            new RotatedPillarBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.PURPUR_PILLAR)
                    .strength(1.5f, 6.0f)
                    .requiresCorrectToolForDrops())
    );

    public static final Block LIGHT_BEAM_EMITTER = register(
            "light_beam_emitter",
            new LightBeamEmitterBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.SEA_LANTERN)
                    .strength(1.5f, 6.0f)
                    .lightLevel(state -> 15))
    );

    public static final Block ENERGY_CONDENSER = register(
            "energy_condenser",
            new EnergyCondenserBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK)
                    .strength(3.0f, 6.0f)
                    .requiresCorrectToolForDrops()
                    .noOcclusion())
    );

    public static final Block METEOROLOGICAL_CONDENSER = register(
            "meteorological_condenser",
            new Block(BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK)
                    .strength(3.0f, 6.0f)
                    .requiresCorrectToolForDrops()
                    .noOcclusion())
    );

    public static final Block PARABOLA_BLOCK = register(
            "parabola_block",
            new ParabolaBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK)
                    .strength(3.0f, 6.0f)
                    .requiresCorrectToolForDrops()
                    .noOcclusion())
    );

    public static final Block METEOROLOGICAL_CATALYST = register(
            "meteorological_catalyst",
            new CrystalCondenserBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK)
                    .strength(3.0f, 6.0f)
                    .requiresCorrectToolForDrops()
                    .noOcclusion())
    );

    public static final Block METEOROLOGICAL_CONTROLLER = register(
            "meteorological_controller",
            new MeteorologicalControllerBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK)
                    .strength(3.0f, 6.0f)
                    .requiresCorrectToolForDrops()
                    .noOcclusion())
    );

    public static final Block ENERGY_CONDUIT = register(
            "energy_conduit",
            new EnergyConduitBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK)
                    .strength(2.5f, 6.0f)
                    .requiresCorrectToolForDrops()
                    .noOcclusion())
    );

    public static final Block SOLAR_PANEL = register(
            "solar_panel",
            new SolarPanelBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK)
                    .strength(3.0f, 6.0f)
                    .requiresCorrectToolForDrops()
                    .noOcclusion())
    );

    public static final Block ENERGY_MONITOR = register(
            "energy_monitor",
            new EnergyMonitorBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK)
                    .strength(2.5f, 6.0f)
                    .requiresCorrectToolForDrops()
                    .noOcclusion())
    );

    public static final Block PORTAL_CORE = register(
            "portal_core",
            new PortalCoreBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.OBSIDIAN)
                    .strength(50.0f, 1200.0f)
                    .requiresCorrectToolForDrops())
    );

    public static final Block PORTAL_FRAME = register(
            "portal_frame",
            new PortalFrameBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.OBSIDIAN)
                    .strength(60.0f, 1200.0f)
                    .requiresCorrectToolForDrops()
                    .lightLevel(PortalFrameBlock::lightLevel))
    );

    public static final Block PORTAL_KEYBOARD = register(
            "portal_keyboard",
            new PortalKeyboardBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.OBSIDIAN)
                    .strength(60.0f, 1200.0f)
                    .requiresCorrectToolForDrops()
                    .lightLevel(PortalKeyboardBlock::lightLevel))
    );

    public static final Block PORTAL_FIELD = register(
            "portal_field",
            new PortalFieldBlock(
                    BlockBehaviour.Properties.of()
                            .noCollission()
                            .strength(1.0F, 0.0F)
                            .lightLevel(state -> 10)
                            .noLootTable()
                            .noOcclusion()
                            .isSuffocating((state, level, pos) -> false)
                            .isViewBlocking((state, level, pos) -> false)
            )
    );

    public static final Block CHARGED_LIGHTNING_ROD = register(
            "charged_lightning_rod",
            new ChargedLightningRodBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.LIGHTNING_ROD))
    );

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
