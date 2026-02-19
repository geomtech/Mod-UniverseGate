package fr.geomtech.universegate;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;

public class CombustionGeneratorScreen extends AbstractContainerScreen<CombustionGeneratorMenu> {

    public CombustionGeneratorScreen(CombustionGeneratorMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        this.imageWidth = 176;
        this.imageHeight = 166;
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTicks, int mouseX, int mouseY) {
        g.fillGradient(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, 0xFF171B22, 0xFF10141A);
        g.fill(leftPos + 2, topPos + 2, leftPos + imageWidth - 2, topPos + imageHeight - 2, 0xFF242C37);

        g.fill(leftPos + 7, topPos + 83, leftPos + 169, topPos + 141, 0xFF1A212B);
        g.fill(leftPos + 7, topPos + 141, leftPos + 169, topPos + 165, 0xFF1D2632);

        g.fill(leftPos + 7, topPos + 19, leftPos + 25, topPos + 37, 0xFF0D1118);

        int barX = leftPos + 30;
        int barY = topPos + 52;
        int barWidth = 122;
        int barHeight = 12;
        g.fill(barX, barY, barX + barWidth, barY + barHeight, 0xFF0C1016);

        int fill = Mth.floor((barWidth - 2) * (menu.chargePercent() / 100.0F));
        if (fill > 0) {
            int color = menu.chargePercent() >= 80 ? 0xFF45D48A : (menu.chargePercent() >= 35 ? 0xFF4FA7FF : 0xFFE69E4A);
            g.fill(barX + 1, barY + 1, barX + 1 + fill, barY + barHeight - 1, color);
        }

        if (menu.fuelCount() > 0) {
            g.fill(leftPos + 7, topPos + 42, leftPos + 27, topPos + 46, 0xFFE69E4A);
        }
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        g.drawString(this.font, this.title, 8, 6, 0xDFE9FF, false);
        g.drawString(this.font,
                Component.translatable("gui.universegate.combustion_energy", menu.bufferedEnergy(), menu.capacity()),
                30,
                24,
                0xC7D5EE,
                false);
        g.drawString(this.font,
                Component.translatable("gui.universegate.combustion_output", menu.outputPerSecond()),
                30,
                36,
                0xC7D5EE,
                false);
        g.drawString(this.font,
                Component.translatable("gui.universegate.combustion_fuel", menu.fuelCount()),
                30,
                66,
                0xC7D5EE,
                false);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTicks) {
        this.renderBackground(g, mouseX, mouseY, partialTicks);
        super.render(g, mouseX, mouseY, partialTicks);
        this.renderTooltip(g, mouseX, mouseY);
    }
}
