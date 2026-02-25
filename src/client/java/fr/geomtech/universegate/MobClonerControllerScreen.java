package fr.geomtech.universegate;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

public class MobClonerControllerScreen extends AbstractContainerScreen<MobClonerControllerMenu> {

    private Button cloneButton;

    public MobClonerControllerScreen(MobClonerControllerMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        this.imageWidth = 320;
        this.imageHeight = 188;
        this.inventoryLabelY = 10000;
    }

    @Override
    protected void init() {
        super.init();

        cloneButton = addRenderableWidget(
                Button.builder(Component.translatable("gui.universegate.mob_cloner_clone"), button -> requestClone())
                        .bounds(leftPos + 232, topPos + 52, 80, 20)
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
        if (cloneButton != null) {
            cloneButton.active = menu.ready() && !menu.isCharging();
        }
    }

    private void requestClone() {
        if (this.minecraft == null || this.minecraft.gameMode == null) return;
        this.minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId, MobClonerControllerMenu.BUTTON_CLONE);
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTicks, int mouseX, int mouseY) {
        g.fillGradient(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, 0xFF151A23, 0xFF10151D);
        g.fill(leftPos + 2, topPos + 2, leftPos + imageWidth - 2, topPos + imageHeight - 2, 0xFF232D3A);

        g.fill(leftPos + 7, topPos + 16, leftPos + 222, topPos + 98, 0xFF1B2430);
        g.fill(leftPos + 227, topPos + 16, leftPos + 313, topPos + 98, 0xFF1A212B);

        g.fill(leftPos + 78, topPos + 105, leftPos + 242, topPos + 163, 0xFF1A212B);
        g.fill(leftPos + 78, topPos + 163, leftPos + 242, topPos + 187, 0xFF1D2632);

        g.fill(leftPos + 265, topPos + 29, leftPos + 283, topPos + 47, 0xFF0D1118);

        int barX = leftPos + 232;
        int barY = topPos + 78;
        int barWidth = 80;
        int barHeight = 7;
        g.fill(barX, barY, barX + barWidth, barY + barHeight, 0xFF0E141D);

        int cost = menu.cost();
        long available = menu.availableEnergy();
        int fill = 0;
        if (cost > 0 && available > 0) {
            fill = Mth.clamp((int) Math.floor((barWidth - 2) * (available / (double) cost)), 0, barWidth - 2);
        }

        if (fill > 0) {
            int color = menu.hasFlag(MobClonerControllerMenu.FLAG_ENOUGH_ENERGY) ? 0xFF45D48A : 0xFFE69E4A;
            g.fill(barX + 1, barY + 1, barX + 1 + fill, barY + barHeight - 1, color);
        }

        int chargeBarY = topPos + 90;
        g.fill(barX, chargeBarY, barX + barWidth, chargeBarY + barHeight, 0xFF0E141D);
        if (menu.isCharging()) {
            int chargeFill = Mth.clamp((int) Math.floor((barWidth - 2) * menu.chargeProgress()), 0, barWidth - 2);
            if (chargeFill > 0) {
                g.fill(barX + 1, chargeBarY + 1, barX + 1 + chargeFill, chargeBarY + barHeight - 1, 0xFF4FA7FF);
            }
        }
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        g.drawString(this.font, this.title, 8, 6, 0xDFE9FF, false);

        g.drawString(this.font,
                Component.translatable("gui.universegate.mob_cloner_dna_slot"),
                244,
                20,
                0xC7D5EE,
                false);

        Component dnaText;
        if (!menu.hasFlag(MobClonerControllerMenu.FLAG_HAS_DNA)) {
            dnaText = Component.translatable("gui.universegate.mob_cloner_dna_missing");
        } else if (!menu.hasFlag(MobClonerControllerMenu.FLAG_DNA_VALID)) {
            dnaText = Component.translatable("gui.universegate.mob_cloner_dna_invalid");
        } else {
            dnaText = Component.translatable("gui.universegate.mob_cloner_dna_ready");
        }
        g.drawString(this.font, dnaText, 8, 20, 0xC7D5EE, false);

        ItemStack dnaStack = menu.getSlot(0).getItem();
        int usesLeft = dnaStack.isEmpty() ? 0 : DnaSampleItem.getRemainingUses(dnaStack);
        g.drawString(this.font,
                Component.translatable("gui.universegate.mob_cloner_dna_uses", usesLeft),
                8,
                32,
                0xC7D5EE,
                false);

        Component clonerText = menu.hasFlag(MobClonerControllerMenu.FLAG_HAS_CLONER)
                ? Component.translatable("gui.universegate.mob_cloner_cloner_found", Math.max(0, menu.clonerDistanceBlocks()))
                : Component.translatable("gui.universegate.mob_cloner_cloner_missing");
        g.drawString(this.font, clonerText, 8, 44, 0xC7D5EE, false);

        Component standardLinkText = menu.hasFlag(MobClonerControllerMenu.FLAG_STANDARD_LINK)
                ? Component.translatable("gui.universegate.mob_cloner_standard_online")
                : Component.translatable("gui.universegate.mob_cloner_standard_offline");
        g.drawString(this.font, standardLinkText, 8, 56, 0xC7D5EE, false);

        Component darkLinkText = menu.hasFlag(MobClonerControllerMenu.FLAG_DARK_LINK)
                ? Component.translatable("gui.universegate.mob_cloner_dark_online")
                : Component.translatable("gui.universegate.mob_cloner_dark_offline");
        g.drawString(this.font, darkLinkText, 8, 68, 0xC7D5EE, false);

        g.drawString(this.font,
                Component.translatable(
                        "gui.universegate.mob_cloner_energy_cost",
                        ZpcItem.formatEnergy(menu.availableEnergy()),
                        ZpcItem.formatEnergy(menu.cost())
                ),
                8,
                80,
                0xC7D5EE,
                false);

        Component stateText = menu.isCharging()
                ? Component.translatable(
                        "gui.universegate.mob_cloner_charge",
                        Mth.floor(menu.chargeProgress() * 100.0F),
                        menu.remainingChargeSeconds())
                : (menu.ready()
                ? Component.translatable("gui.universegate.mob_cloner_ready")
                : Component.translatable("gui.universegate.mob_cloner_not_ready"));
        g.drawString(this.font, stateText, 8, 92, 0xF0C98A, false);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTicks) {
        this.renderBackground(g, mouseX, mouseY, partialTicks);
        super.render(g, mouseX, mouseY, partialTicks);
        this.renderTooltip(g, mouseX, mouseY);
    }
}
