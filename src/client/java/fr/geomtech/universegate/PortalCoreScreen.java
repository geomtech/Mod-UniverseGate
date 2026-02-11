package fr.geomtech.universegate;

import fr.geomtech.universegate.net.RenamePortalPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

public class PortalCoreScreen extends AbstractContainerScreen<PortalCoreMenu> {

    private EditBox nameBox;
    private String pendingName = "";

    public PortalCoreScreen(PortalCoreMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        this.imageWidth = 176;
        this.imageHeight = 80;
    }

    public void setPortalName(String name) {
        pendingName = name == null ? "" : name;
        if (nameBox != null) nameBox.setValue(pendingName);
    }

    public net.minecraft.core.BlockPos getCorePos() {
        return this.menu.getCorePos();
    }

    @Override
    protected void init() {
        super.init();

        int x = leftPos + 8;
        int y = topPos + 24;

        nameBox = new EditBox(this.font, x, y, 160, 18, Component.literal("Portal Name"));
        nameBox.setMaxLength(32);
        nameBox.setValue(pendingName);
        this.addRenderableWidget(nameBox);
        this.setInitialFocus(nameBox);

        Button rename = Button.builder(Component.literal("Rename"), (btn) -> submitRename())
                .bounds(leftPos + 8, topPos + 50, 160, 16)
                .build();
        this.addRenderableWidget(rename);

        this.inventoryLabelY = 10000;
    }

    private void submitRename() {
        if (this.minecraft == null || this.minecraft.player == null) return;
        ClientPlayNetworking.send(new RenamePortalPayload(this.menu.getCorePos(), nameBox.getValue()));
        this.minecraft.player.closeContainer();
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (nameBox != null && nameBox.isFocused() && this.minecraft != null) {
            if (this.minecraft.options.keyInventory.matches(keyCode, scanCode)) return true;
            if (nameBox.keyPressed(keyCode, scanCode, modifiers)) return true;
        }
        if (keyCode == 257 || keyCode == 335) {
            submitRename();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (nameBox != null && nameBox.isFocused() && nameBox.charTyped(codePoint, modifiers)) return true;
        return super.charTyped(codePoint, modifiers);
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTicks, int mouseX, int mouseY) {
        g.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, 0xFF2B2B2B);
        g.drawString(this.font, "Portal Name", leftPos + 8, topPos + 8, 0xFFFFFF, false);
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTicks) {
        this.renderBackground(g, mouseX, mouseY, partialTicks);
        super.render(g, mouseX, mouseY, partialTicks);
        this.renderTooltip(g, mouseX, mouseY);
    }
}
