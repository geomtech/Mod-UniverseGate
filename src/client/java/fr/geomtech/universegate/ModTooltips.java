package fr.geomtech.universegate;

import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

public final class ModTooltips {

    public static void register() {
        ItemTooltipCallback.EVENT.register((stack, context, type, lines) -> {
            ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
            if (!UniverseGate.MOD_ID.equals(id.getNamespace())) return;

            String key = "tooltip." + UniverseGate.MOD_ID + "." + id.getPath();
            Component tooltip = Component.translatable(key);
            if (!tooltip.getString().equals(key)) {
                lines.add(Component.translatable(key).withStyle(ChatFormatting.GRAY));
            }
        });
    }

    private ModTooltips() {}
}
