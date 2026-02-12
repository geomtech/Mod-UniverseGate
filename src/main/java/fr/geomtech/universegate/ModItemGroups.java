package fr.geomtech.universegate;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;

public final class ModItemGroups {

    public static final CreativeModeTab UNIVERSEGATE_TAB = Registry.register(
            BuiltInRegistries.CREATIVE_MODE_TAB,
            ResourceLocation.fromNamespaceAndPath(UniverseGate.MOD_ID, "universegate"),
            CreativeModeTab.builder(CreativeModeTab.Row.TOP, 0)
                    .title(Component.translatable("itemGroup.universegate.main"))
                    .icon(() -> new ItemStack(ModItems.PORTAL_CORE_ITEM))
                    .displayItems((parameters, output) -> {
                        output.accept(ModItems.PORTAL_CORE_ITEM);
                        output.accept(ModItems.PORTAL_FRAME_ITEM);
                        output.accept(ModItems.PORTAL_KEYBOARD_ITEM);
                        output.accept(ModItems.CHARGED_LIGHTNING_ROD_ITEM);
                        output.accept(ModItems.LIGHT_BEAM_EMITTER_ITEM);

                        output.accept(ModItems.VOID_BLOCK_ITEM);
                        output.accept(ModItems.LIGHT_BLOCK_ITEM);
                        output.accept(ModItems.WHITE_PURPUR_BLOCK_ITEM);
                        output.accept(ModItems.WHITE_PURPUR_PILLAR_ITEM);
                        output.accept(ModItems.KELO_LOG_ITEM);

                        output.accept(ModItems.RIFT_ASH);
                        output.accept(ModItems.RIFT_COMPASS);
                        output.accept(ModItems.RIFT_SHADE_SPAWN_EGG);
                    })
                    .build()
    );

    public static void register() {}

    private ModItemGroups() {}
}
