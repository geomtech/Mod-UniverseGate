package fr.geomtech.universegate;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;

public class ZpcInterfaceControllerScreen extends AbstractContainerScreen<ZpcInterfaceControllerMenu> {

    public ZpcInterfaceControllerScreen(ZpcInterfaceControllerMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        this.imageWidth = 196;
        this.imageHeight = 118;
        this.inventoryLabelY = 10000;
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTicks, int mouseX, int mouseY) {
        g.fillGradient(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, 0xFF231B13, 0xFF18120D);
        g.fill(leftPos + 2, topPos + 2, leftPos + imageWidth - 2, topPos + imageHeight - 2, 0xFF31261C);

        int barX = leftPos + 10;
        int barY = topPos + 25;
        int barWidth = 176;
        int barHeight = 14;

        g.fill(barX, barY, barX + barWidth, barY + barHeight, 0xFF0F0C09);

        int fill = Mth.floor((barWidth - 2) * (menu.chargePercent() / 100.0F));
        if (fill > 0) {
            int color = menu.chargePercent() >= 65 ? 0xFFF0C24D : (menu.chargePercent() >= 25 ? 0xFFE09339 : 0xFFBF4B2F);
            g.fill(barX + 1, barY + 1, barX + 1 + fill, barY + barHeight - 1, color);
        }
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        g.drawString(this.font, this.title, 10, 8, 0xF2DFCC, false);

        g.drawString(this.font,
                Component.translatable("gui.universegate.zpc_modules", menu.installedCount(), ZpcInterfaceControllerBlockEntity.MAX_ZPCS),
                10,
                44,
                0xE8D3BC,
                false);

        g.drawString(this.font,
                Component.translatable("gui.universegate.zpc_charge_percent", menu.chargePercent()),
                10,
                56,
                0xD7C3AF,
                false);

        g.drawString(this.font,
                Component.translatable("gui.universegate.zpc_stored", ZpcItem.formatEnergy(menu.storedEnergy()), ZpcItem.formatEnergy(menu.totalCapacity())),
                10,
                68,
                0xD7C3AF,
                false);

        g.drawString(this.font,
                Component.translatable("gui.universegate.zpc_output", menu.outputPerSecond()),
                10,
                80,
                0xD7C3AF,
                false);

        Component stateText;
        if (!menu.hasZpc()) {
            stateText = Component.translatable("gui.universegate.zpc_state_missing");
        } else if (menu.animating()) {
            stateText = Component.translatable("gui.universegate.zpc_state_inserting");
        } else if (!menu.engaged()) {
            stateText = Component.translatable("gui.universegate.zpc_state_seated");
        } else if (menu.storedEnergy() <= 0L) {
            stateText = Component.translatable("gui.universegate.zpc_state_depleted");
        } else if (!menu.networkLinked()) {
            stateText = Component.translatable("gui.universegate.zpc_state_offline");
        } else {
            stateText = Component.translatable("gui.universegate.zpc_state_online");
        }

        g.drawString(this.font, stateText, 10, 94, 0xF1C68E, false);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTicks) {
        this.renderBackground(g, mouseX, mouseY, partialTicks);
        super.render(g, mouseX, mouseY, partialTicks);
        this.renderTooltip(g, mouseX, mouseY);
    }
}
