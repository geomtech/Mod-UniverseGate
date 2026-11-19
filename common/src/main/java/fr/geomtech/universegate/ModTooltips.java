package fr.geomtech.universegate;

import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.List;

public final class ModTooltips {

    /** Adds the generic "tooltip.universegate.&lt;item&gt;" line to UniverseGate items. Called by each loader's tooltip event. */
    public static void appendTooltip(ItemStack stack, List<Component> lines) {
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (!UniverseGate.MOD_ID.equals(id.getNamespace())) return;

        String key = "tooltip." + UniverseGate.MOD_ID + "." + id.getPath();
        Component tooltip = Component.translatable(key);
        if (!tooltip.getString().equals(key)) {
            lines.add(Component.translatable(key).withStyle(ChatFormatting.GRAY));
        }
    }

    private ModTooltips() {}
}
