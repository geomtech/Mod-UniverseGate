package fr.geomtech.universegate;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;

public class EnergyMonitorScreen extends AbstractContainerScreen<EnergyMonitorMenu> {

    public EnergyMonitorScreen(EnergyMonitorMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        this.imageWidth = 320;
        this.imageHeight = 112;
        this.inventoryLabelY = 10000;
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTicks, int mouseX, int mouseY) {
        g.fillGradient(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, 0xFF10161F, 0xFF1A2331);
        g.fill(leftPos + 2, topPos + 2, leftPos + imageWidth - 2, topPos + imageHeight - 2, 0xFF243243);

        int barX = leftPos + 10;
        int barY = topPos + 24;
        int barWidth = imageWidth - 20;
        int barHeight = 14;

        g.fill(barX, barY, barX + barWidth, barY + barHeight, 0xFF0E141D);
        int fill = Mth.floor(barWidth * (menu.chargePercent() / 100.0F));
        if (fill > 0) {
            int color = menu.chargePercent() >= 75 ? 0xFF45D48A : (menu.chargePercent() >= 30 ? 0xFF4FA7FF : 0xFFE6A34B);
            g.fill(barX + 1, barY + 1, barX + fill - 1, barY + barHeight - 1, color);
        }
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        g.drawString(this.font, this.title, 10, 8, 0xDDE7FF, false);

        g.drawString(this.font,
                Component.translatable(
                        "gui.universegate.energy_stored",
                        ZpcItem.formatEnergy(menu.storedEnergy()),
                        ZpcItem.formatEnergy(menu.capacity())
                ),
                10,
                44,
                0xC6D3EE,
                false);

        g.drawString(this.font,
                Component.translatable("gui.universegate.energy_charge_percent", menu.chargePercent()),
                10,
                56,
                0xC6D3EE,
                false);

        g.drawString(this.font,
                Component.translatable("gui.universegate.energy_condensers", menu.condenserCount()),
                10,
                70,
                0xC6D3EE,
                false);

        g.drawString(this.font,
                Component.translatable("gui.universegate.energy_panels", menu.panelCount(), menu.activePanelCount()),
                10,
                82,
                0xC6D3EE,
                false);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTicks) {
        this.renderBackground(g, mouseX, mouseY, partialTicks);
        super.render(g, mouseX, mouseY, partialTicks);
        this.renderTooltip(g, mouseX, mouseY);
    }
}
