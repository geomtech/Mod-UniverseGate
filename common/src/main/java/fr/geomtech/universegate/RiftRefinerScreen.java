package fr.geomtech.universegate;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

public class RiftRefinerScreen extends AbstractContainerScreen<RiftRefinerMenu> {
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(UniverseGate.MOD_ID, "textures/gui/rift_refiner.png");

    public RiftRefinerScreen(RiftRefinerMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
    }

    @Override
    protected void init() {
        super.init();
        this.titleLabelX = (this.imageWidth - this.font.width(this.title)) / 2;
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(guiGraphics, mouseX, mouseY, partialTick);
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        this.renderTooltip(guiGraphics, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.setShaderTexture(0, TEXTURE);
        int x = (this.width - this.imageWidth) / 2;
        int y = (this.height - this.imageHeight) / 2;
        guiGraphics.blit(TEXTURE, x, y, 0, 0, this.imageWidth, this.imageHeight);

        if (menu.isCrafting()) {
            guiGraphics.blit(TEXTURE, x + 79, y + 34, 176, 14, menu.getScaledProgress(), 16);
        }
        
        // Fluid Tank Render
        // Assuming tank is at x+120, y+20, size 16x50 (adjust based on texture)
        // I'll draw a colored box for now as I don't have a specific tank texture slot yet
        int fluidHeight = 0;
        int maxFluid = menu.getMaxFluid();
        if (maxFluid > 0) {
            fluidHeight = (int) ((float) menu.getFluidAmount() / maxFluid * 50); // 50 pixels high tank
        }
        
        if (fluidHeight > 0) {
            // Dark Matter Color (Purple/Black)
            // ARGB: 0xFF320050
            guiGraphics.fill(x + 120, y + 20 + (50 - fluidHeight), x + 136, y + 70, 0xFF320050);
        }
    }

    @Override
    protected void renderTooltip(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        super.renderTooltip(guiGraphics, mouseX, mouseY);
        
        int x = (this.width - this.imageWidth) / 2;
        int y = (this.height - this.imageHeight) / 2;
        
        // Check if hovering over fluid tank area (x+120, y+20 to x+136, y+70)
        if (mouseX >= x + 120 && mouseX <= x + 136 && mouseY >= y + 20 && mouseY <= y + 70) {
            guiGraphics.renderTooltip(this.font, Component.literal(menu.getFluidAmount() + " / " + menu.getMaxFluid() + " mB"), mouseX, mouseY);
        }
    }
}
