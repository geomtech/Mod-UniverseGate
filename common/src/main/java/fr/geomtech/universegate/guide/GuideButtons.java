package fr.geomtech.universegate.guide;

import fr.geomtech.universegate.ModItems;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/** The buttons that open the guide, from the pause menu and the title screen, with the Portal Core as icon. */
public final class GuideButtons {
    private GuideButtons() {
    }

    /** A button that opens the guide; {@code iconOnly} gives a square button with just the icon. */
    public static Button create(Screen parent, int x, int y, int width, boolean iconOnly) {
        Component label = iconOnly ? Component.empty() : Component.translatable("guide.universegate.open");
        Button button = Button.builder(label, b -> Minecraft.getInstance().setScreen(new GuideScreen(parent)))
                .bounds(x, y, width, 20).build();
        button.setTooltip(Tooltip.create(Component.translatable("guide.universegate.open.tooltip")));
        return button;
    }

    /** Draws the icon over the button: centred for a square button, at its left otherwise. */
    public static Renderable icon(Button button) {
        ItemStack stack = new ItemStack(ModItems.PORTAL_CORE_ITEM);
        return (g, mouseX, mouseY, partialTick) -> {
            if (!button.visible) return;
            int x = button.getWidth() <= 24 ? button.getX() + (button.getWidth() - 16) / 2 : button.getX() + 3;
            g.renderItem(stack, x, button.getY() + 2);
        };
    }
}
