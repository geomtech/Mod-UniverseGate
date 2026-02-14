package fr.geomtech.universegate;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;

public class MeteorologicalControllerScreen extends AbstractContainerScreen<MeteorologicalControllerMenu> {

    private Button clearButton;
    private Button rainButton;
    private Button thunderButton;

    public MeteorologicalControllerScreen(MeteorologicalControllerMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        this.imageWidth = 188;
        this.imageHeight = 108;
        this.inventoryLabelY = 10000;
    }

    @Override
    protected void init() {
        super.init();

        int y = topPos + 80;
        clearButton = addRenderableWidget(
                Button.builder(Component.translatable("gui.universegate.weather_clear"), button -> sendWeatherSelection(WeatherSelection.CLEAR))
                        .bounds(leftPos + 10, y, 54, 18)
                        .build()
        );
        rainButton = addRenderableWidget(
                Button.builder(Component.translatable("gui.universegate.weather_rain"), button -> sendWeatherSelection(WeatherSelection.RAIN))
                        .bounds(leftPos + 67, y, 54, 18)
                        .build()
        );
        thunderButton = addRenderableWidget(
                Button.builder(Component.translatable("gui.universegate.weather_thunder"), button -> sendWeatherSelection(WeatherSelection.THUNDER))
                        .bounds(leftPos + 124, y, 54, 18)
                        .build()
        );

        updateButtons();
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        updateButtons();
    }

    private void updateButtons() {
        boolean available = menu.hasFlag(MeteorologicalControllerMenu.FLAG_WEATHER_UNLOCKED)
                && !menu.hasFlag(MeteorologicalControllerMenu.FLAG_SEQUENCE_ACTIVE);
        if (clearButton != null) clearButton.active = available;
        if (rainButton != null) rainButton.active = available;
        if (thunderButton != null) thunderButton.active = available;
    }

    private void sendWeatherSelection(WeatherSelection selection) {
        if (this.minecraft == null || this.minecraft.gameMode == null) return;
        this.minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId, selection.buttonId());
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTicks, int mouseX, int mouseY) {
        g.fillGradient(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, 0xFF10151E, 0xFF1A202D);
        g.fill(leftPos + 2, topPos + 2, leftPos + imageWidth - 2, topPos + imageHeight - 2, 0xFF252C3C);

        int barX = leftPos + 10;
        int barY = topPos + 30;
        int barWidth = 168;
        int barHeight = 12;
        g.fill(barX, barY, barX + barWidth, barY + barHeight, 0xFF0E131D);
        int fill = Mth.floor(barWidth * menu.chargeProgress());
        int color = menu.hasFlag(MeteorologicalControllerMenu.FLAG_FULLY_CHARGED) ? 0xFF45D48A : 0xFF4FA7FF;
        if (fill > 0) {
            g.fill(barX + 1, barY + 1, barX + fill - 1, barY + barHeight - 1, color);
        }

        if (menu.hasFlag(MeteorologicalControllerMenu.FLAG_SEQUENCE_ACTIVE)) {
            int seqBarX = leftPos + 10;
            int seqBarY = topPos + 62;
            int seqBarWidth = 168;
            int seqFill = Mth.floor(seqBarWidth * menu.sequenceProgress());
            g.fill(seqBarX, seqBarY, seqBarX + seqBarWidth, seqBarY + 7, 0xFF0E131D);
            if (seqFill > 0) {
                g.fill(seqBarX + 1, seqBarY + 1, seqBarX + seqFill - 1, seqBarY + 6, 0xFFFFD85A);
            }
        }
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        g.drawString(this.font, this.title, 10, 8, 0xDDE7FF, false);

        int chargePercent = Mth.floor(menu.chargeProgress() * 100.0F);
        g.drawString(this.font,
                Component.translatable("gui.universegate.weather_charge", chargePercent),
                10,
                18,
                0xC6D3EE,
                false);

        Component structureText = menu.hasFlag(MeteorologicalControllerMenu.FLAG_STRUCTURE_READY)
                ? Component.translatable("gui.universegate.weather_structure_ready")
                : Component.translatable("gui.universegate.weather_structure_missing");
        g.drawString(this.font, structureText, 10, 46, 0xC6D3EE, false);

        Component energyText = menu.hasFlag(MeteorologicalControllerMenu.FLAG_ENERGY_LINKED)
                ? Component.translatable("gui.universegate.weather_energy_online")
                : Component.translatable("gui.universegate.weather_energy_offline");
        g.drawString(this.font, energyText, 10, 56, 0xC6D3EE, false);

        Component infoText;
        if (menu.hasFlag(MeteorologicalControllerMenu.FLAG_SEQUENCE_ACTIVE)) {
            infoText = Component.translatable("gui.universegate.weather_sequence");
        } else if (!menu.hasFlag(MeteorologicalControllerMenu.FLAG_STRUCTURE_READY)) {
            infoText = Component.translatable("gui.universegate.weather_structure_missing");
        } else if (!menu.hasFlag(MeteorologicalControllerMenu.FLAG_CATALYST_HAS_CRYSTAL)) {
            infoText = Component.translatable("gui.universegate.weather_missing_crystal");
        } else if (!menu.hasFlag(MeteorologicalControllerMenu.FLAG_FULLY_CHARGED)) {
            infoText = Component.translatable("gui.universegate.weather_waiting_energy");
        } else {
            infoText = Component.translatable("gui.universegate.weather_ready");
        }
        g.drawString(this.font, infoText, 10, 66, 0xF7D98D, false);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTicks) {
        this.renderBackground(g, mouseX, mouseY, partialTicks);
        super.render(g, mouseX, mouseY, partialTicks);
        this.renderTooltip(g, mouseX, mouseY);
    }
}
