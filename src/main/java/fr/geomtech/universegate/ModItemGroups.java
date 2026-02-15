package fr.geomtech.universegate;

import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;

public final class ModItemGroups {

    public static final ResourceKey<CreativeModeTab> UNIVERSEGATE_TAB_KEY = ResourceKey.create(
            BuiltInRegistries.CREATIVE_MODE_TAB.key(),
            ResourceLocation.fromNamespaceAndPath(UniverseGate.MOD_ID, "universegate")
    );

    public static final CreativeModeTab UNIVERSEGATE_TAB = FabricItemGroup.builder()
            .title(Component.translatable("itemGroup.universegate.main"))
            .icon(() -> new ItemStack(ModItems.PORTAL_CORE_ITEM))
            .displayItems((parameters, output) -> {
                output.accept(ModItems.PORTAL_CORE_ITEM);
                output.accept(ModItems.PORTAL_FRAME_ITEM);
                output.accept(ModItems.PORTAL_KEYBOARD_ITEM);
                output.accept(ModItems.CHARGED_LIGHTNING_ROD_ITEM);
                output.accept(ModItems.LIGHT_BEAM_EMITTER_ITEM);
                output.accept(ModItems.ENERGY_CONDENSER_ITEM);
                output.accept(ModItems.METEOROLOGICAL_CONDENSER_ITEM);
                output.accept(ModItems.PARABOLA_BLOCK_ITEM);
                output.accept(ModItems.METEOROLOGICAL_CATALYST_ITEM);
                output.accept(ModItems.METEOROLOGICAL_CONTROLLER_ITEM);
                output.accept(ModItems.ENERGY_CONDUIT_ITEM);
                output.accept(ModItems.SOLAR_PANEL_ITEM);
                output.accept(ModItems.ENERGY_MONITOR_ITEM);

                output.accept(ModItems.VOID_BLOCK_ITEM);
                output.accept(ModItems.LIGHT_BLOCK_ITEM);
                output.accept(ModItems.WHITE_PURPUR_BLOCK_ITEM);
                output.accept(ModItems.WHITE_PURPUR_PILLAR_ITEM);
                output.accept(ModItems.KELO_LOG_ITEM);
                output.accept(ModItems.KELO_PLANKS_ITEM);

                output.accept(ModItems.RIFT_MEAT);
                output.accept(ModItems.COOKED_RIFT_MEAT);
                output.accept(ModItems.ENERGY_CRYSTAL);
                output.accept(ModItems.RIFT_SHADE_SPAWN_EGG);
                output.accept(ModItems.RIFT_BEAST_SPAWN_EGG);
            })
            .build();

    public static void register() {
        Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, UNIVERSEGATE_TAB_KEY, UNIVERSEGATE_TAB);
    }

    private ModItemGroups() {}
}
