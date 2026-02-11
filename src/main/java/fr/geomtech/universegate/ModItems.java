package fr.geomtech.universegate;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.registries.Registries;
import net.minecraft.core.Registry;

public final class ModItems {

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

    public static final Item CATALYST = register(
            "catalyst",
            new Item(new Item.Properties().stacksTo(16))
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
