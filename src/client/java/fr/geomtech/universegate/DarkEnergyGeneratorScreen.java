package fr.geomtech.universegate;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;

public class DarkEnergyGeneratorScreen extends AbstractContainerScreen<DarkEnergyGeneratorMenu> {

    public DarkEnergyGeneratorScreen(DarkEnergyGeneratorMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        this.imageWidth = 196;
        this.imageHeight = 120;
        this.inventoryLabelY = 10000;
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTicks, int mouseX, int mouseY) {
        g.fillGradient(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, 0xFF120F1B, 0xFF1A2230);
        g.fill(leftPos + 2, topPos + 2, leftPos + imageWidth - 2, topPos + imageHeight - 2, 0xFF263345);

        int barX = leftPos + 10;
        int fuelBarY = topPos + 28;
        int energyBarY = topPos + 52;
        int barWidth = 176;
        int barHeight = 12;

        g.fill(barX, fuelBarY, barX + barWidth, fuelBarY + barHeight, 0xFF101722);
        int fuelFill = Mth.floor((barWidth - 2) * (menu.darkMatterPercent() / 100.0F));
        if (fuelFill > 0) {
            g.fill(barX + 1, fuelBarY + 1, barX + 1 + fuelFill, fuelBarY + barHeight - 1, 0xFFC95DDA);
        }

        g.fill(barX, energyBarY, barX + barWidth, energyBarY + barHeight, 0xFF101722);
        int energyFill = Mth.floor((barWidth - 2) * (menu.darkEnergyPercent() / 100.0F));
        if (energyFill > 0) {
            g.fill(barX + 1, energyBarY + 1, barX + 1 + energyFill, energyBarY + barHeight - 1, 0xFF4FA7FF);
        }
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        g.drawString(this.font, this.title, 10, 8, 0xE4EBFF, false);

        g.drawString(this.font,
                Component.translatable("gui.universegate.dark_energy_fuel", menu.darkMatterAmount(), menu.darkMatterCapacity()),
                10,
                18,
                0xCCD8EF,
                false);

        g.drawString(this.font,
                Component.translatable("gui.universegate.dark_energy_buffer", menu.storedDarkEnergy(), menu.darkEnergyCapacity()),
                10,
                42,
                0xCCD8EF,
                false);

        Component runningState = menu.isRunning()
                ? Component.translatable("gui.universegate.dark_energy_status_running")
                : Component.translatable("gui.universegate.dark_energy_status_idle");
        g.drawString(this.font,
                Component.translatable("gui.universegate.dark_energy_status", runningState),
                10,
                70,
                0xCCD8EF,
                false);

        Component networkState = menu.networkOnline()
                ? Component.translatable("gui.universegate.dark_energy_network_online")
                : Component.translatable("gui.universegate.dark_energy_network_offline");
        g.drawString(this.font,
                Component.translatable("gui.universegate.dark_energy_network", networkState),
                10,
                82,
                0xCCD8EF,
                false);

        g.drawString(this.font,
                Component.translatable("gui.universegate.dark_energy_output", menu.outputPerTick()),
                10,
                94,
                0xCCD8EF,
                false);

        g.drawString(this.font,
                Component.translatable("gui.universegate.dark_energy_reserve", menu.reserveAmount()),
                10,
                106,
                0xCCD8EF,
                false);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTicks) {
        this.renderBackground(g, mouseX, mouseY, partialTicks);
        super.render(g, mouseX, mouseY, partialTicks);
        this.renderTooltip(g, mouseX, mouseY);
    }
}
