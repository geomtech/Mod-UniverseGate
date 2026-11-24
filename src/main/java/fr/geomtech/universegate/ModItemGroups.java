package fr.geomtech.universegate;

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

    public static final CreativeModeTab UNIVERSEGATE_TAB = CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.universegate.main"))
            .icon(() -> new ItemStack(ModItems.PORTAL_CORE_ITEM))
            .displayItems((parameters, output) -> {
                output.accept(ModItems.PORTAL_CORE_ITEM);
                output.accept(ModItems.PORTAL_FRAME_ITEM);
                output.accept(ModItems.PORTAL_KEYBOARD_ITEM);
                output.accept(ModItems.PORTAL_NATURAL_KEYBOARD_ITEM);
                output.accept(ModItems.PORTAL_MOBILE_KEYBOARD);
                output.accept(ModItems.CHARGED_LIGHTNING_ROD_ITEM);
                output.accept(ModItems.LIGHT_BEAM_EMITTER_ITEM);
                output.accept(ModItems.ENERGY_CONDENSER_ITEM);
                output.accept(ModItems.METEOROLOGICAL_CONDENSER_ITEM);
                output.accept(ModItems.PARABOLA_BLOCK_ITEM);
                output.accept(ModItems.METEOROLOGICAL_CATALYST_ITEM);
                output.accept(ModItems.METEOROLOGICAL_CONTROLLER_ITEM);
                output.accept(ModItems.ENERGY_CONDUIT_ITEM);
                output.accept(ModItems.SOLAR_PANEL_ITEM);
                output.accept(ModItems.COMBUSTION_GENERATOR_ITEM);
                output.accept(ModItems.ENERGY_MONITOR_ITEM);
                output.accept(ModItems.ACID_NEON_LAMP_ITEM);
                output.accept(ModItems.ZPC_INTERFACE_CONTROLLER_ITEM);

                output.accept(ModItems.VOID_BLOCK_ITEM);
                output.accept(ModItems.LIGHT_BLOCK_ITEM);
                output.accept(ModItems.MOLDY_DIRT_ITEM);
                output.accept(ModItems.WHITE_PURPUR_BLOCK_ITEM);
                output.accept(ModItems.WHITE_PURPUR_PILLAR_ITEM);
                output.accept(ModItems.KELO_LOG_ITEM);
                output.accept(ModItems.KELO_PLANKS_ITEM);

                output.accept(ModItems.RIFT_MEAT);
                output.accept(ModItems.COOKED_RIFT_MEAT);
                output.accept(ModItems.ENERGY_CRYSTAL);
                output.accept(ModItems.RIFT_CRYSTAL);
                output.accept(ModItems.DNA_EXTRACTOR);
                output.accept(ModItems.DNA);
                output.accept(ModItems.ZPC);
                output.accept(ModItems.RIFT_SHADE_SPAWN_EGG);
                output.accept(ModItems.RIFT_BEAST_SPAWN_EGG);
                output.accept(ModItems.DARK_MATTER_BUCKET);
                output.accept(ModItems.ACID_RAIN_BUCKET);
                output.accept(ModItems.ACID_BATTERY);
                output.accept(ModItems.LASER_RIFLE);
                output.accept(ModItems.HORSE_LASER_HARNESS);
                output.accept(ModItems.CREEPER_COMMANDER);
                output.accept(ModItems.OXIDIZED_IRON);
                output.accept(ModItems.ACID_BEACON_ITEM);
                output.accept(ModItems.ACID_ARROW);
                output.accept(ModItems.PHOSPHATE_FERTILIZER);
                output.accept(ModItems.NITRO_ACID_EXPLOSIVE);
                output.accept(ModItems.RIFT_REFINER_ITEM);
                output.accept(ModItems.FLUID_PIPE_ITEM);
                output.accept(ModItems.DARK_ENERGY_CONDUIT_ITEM);
                output.accept(ModItems.DARK_ENERGY_GENERATOR_ITEM);
                output.accept(ModItems.MOB_CLONER_ITEM);
                output.accept(ModItems.MOB_CLONER_CONTROLLER_ITEM);
            })
            .build();

    public static void register() {
        NeoForgeRegistryBootstrap.register(BuiltInRegistries.CREATIVE_MODE_TAB.key(), UNIVERSEGATE_TAB_KEY.location(), UNIVERSEGATE_TAB);
    }

    private ModItemGroups() {}
}
