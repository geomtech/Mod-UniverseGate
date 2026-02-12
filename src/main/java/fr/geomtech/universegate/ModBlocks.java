package fr.geomtech.universegate;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.Registry;

public final class ModBlocks {

    public static final Block PORTAL_CORE = register(
            "portal_core",
            new PortalCoreBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.OBSIDIAN)
                    .strength(50.0f, 1200.0f))
    );

    public static final Block PORTAL_FRAME = register(
            "portal_frame",
            new PortalFrameBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.OBSIDIAN)
                    .strength(60.0f, 1200.0f))
    );

    public static final Block PORTAL_KEYBOARD = register(
            "portal_keyboard",
            new PortalKeyboardBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.OBSIDIAN)
                    .strength(60.0f, 1200.0f))
    );

    public static final Block PORTAL_FIELD = register(
            "portal_field",
            new PortalFieldBlock(
                    BlockBehaviour.Properties.of()
                            .noCollission()
                            .strength(-1.0F, 0.0F)
                            .lightLevel(state -> 10)
                            .noLootTable()
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
