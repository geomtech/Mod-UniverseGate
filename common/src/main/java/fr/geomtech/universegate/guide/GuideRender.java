package fr.geomtech.universegate.guide;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Version glue for the guide screen. Since 1.21.6 the GUI pose is a 2D matrix stack and tooltips are
 * queued for the next frame, so the calls differ between 1.21.1 and 1.21.11.
 */
final class GuideRender {
    private GuideRender() {
    }

    static void push(GuiGraphics g) {
        //? if >=1.21.11 {
        /*g.pose().pushMatrix();
        *///?} else {
        g.pose().pushPose();
        //?}
    }

    static void pop(GuiGraphics g) {
        //? if >=1.21.11 {
        /*g.pose().popMatrix();
        *///?} else {
        g.pose().popPose();
        //?}
    }

    /** Moves the pose. {@code z} only matters in 1.21.1, where GUI items are depth tested. */
    static void translate(GuiGraphics g, float x, float y, float z) {
        //? if >=1.21.11 {
        /*g.pose().translate(x, y);
        *///?} else {
        g.pose().translate(x, y, z);
        //?}
    }

    static void scale(GuiGraphics g, float s) {
        //? if >=1.21.11 {
        /*g.pose().scale(s, s);
        *///?} else {
        g.pose().scale(s, s, s);
        //?}
    }

    static void itemTooltip(GuiGraphics g, Font font, ItemStack stack, int x, int y) {
        //? if >=1.21.11 {
        /*g.setTooltipForNextFrame(font, stack, x, y);
        *///?} else {
        g.renderTooltip(font, stack, x, y);
        //?}
    }

    static void textTooltip(GuiGraphics g, Font font, net.minecraft.network.chat.Component text, int x, int y) {
        //? if >=1.21.11 {
        /*g.setTooltipForNextFrame(font, text, x, y);
        *///?} else {
        g.renderTooltip(font, text, x, y);
        //?}
    }

    /** The item with this id, or {@link Items#AIR} when it does not exist. */
    static Item item(ResourceLocation id) {
        //? if >=1.21.11 {
        /*return BuiltInRegistries.ITEM.getValue(id);
        *///?} else {
        return BuiltInRegistries.ITEM.get(id);
        //?}
    }

    /** Parses "namespace:path" (or a bare path, which means minecraft) into an item stack. */
    static ItemStack stack(String id, int count) {
        if (id == null || id.isEmpty() || id.startsWith("#")) return ItemStack.EMPTY;
        ResourceLocation location = ResourceLocation.tryParse(id);
        if (location == null) return ItemStack.EMPTY;
        Item item = item(location);
        if (item == Items.AIR) return ItemStack.EMPTY;
        return new ItemStack(item, Math.max(1, count));
    }
}
