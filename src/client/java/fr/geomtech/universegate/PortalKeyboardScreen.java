package fr.geomtech.universegate;

import fr.geomtech.universegate.PortalInfo;
import fr.geomtech.universegate.PortalKeyboardMenu;
import fr.geomtech.universegate.net.ConnectPortalPayload;
import fr.geomtech.universegate.net.DisconnectPortalPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.List;

public class PortalKeyboardScreen extends AbstractContainerScreen<PortalKeyboardMenu> {

    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath("universegate", "textures/gui/portal_keyboard.png");

    private final List<PortalInfo> portals = new ArrayList<>();
    private final List<Button> portalButtons = new ArrayList<>();
    private boolean portalActive = false;
    private Button disconnectButton;
    private int scrollOffset = 0;
    private static final int VISIBLE_PORTALS = 3;

    public PortalKeyboardScreen(PortalKeyboardMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        this.imageWidth = 256;
        this.imageHeight = 200;
        this.inventoryLabelY = this.imageHeight - 94;
        this.titleLabelX = 10;
    }

    public net.minecraft.core.BlockPos getKeyboardPos() {
        return this.menu.getKeyboardPos();
    }

    /** Appelé par le handler réseau client quand la liste arrive */
    public void setPortals(List<PortalInfo> newList) {
        portals.clear();
        portals.addAll(newList);
        scrollOffset = 0;
        rebuildPortalButtons();
    }

    public void setPortalActive(boolean active) {
        this.portalActive = active;
        updateDisconnectButton();
    }

    @Override
    protected void init() {
        super.init();
        disconnectButton = Button.builder(Component.literal("Déconnexion"), (btn) -> {
                    ClientPlayNetworking.send(new DisconnectPortalPayload(this.menu.getKeyboardPos()));
                })
                .bounds(leftPos + imageWidth - 88, topPos + 10, 78, 16)
                .build();
        this.addRenderableWidget(disconnectButton);
        updateDisconnectButton();
        rebuildPortalButtons();
    }

    private void rebuildPortalButtons() {
        // Si l’écran n’est pas encore initialisé
        if (this.minecraft == null) return;

        // Supprime les anciens boutons
        for (Button b : portalButtons) this.removeWidget(b);
        portalButtons.clear();

        clampScrollOffset();
        int max = Math.min(VISIBLE_PORTALS, portals.size() - scrollOffset);
        int buttonWidth = 200;
        int buttonHeight = 20;
        int x = leftPos + (imageWidth - buttonWidth) / 2;
        int y = topPos + 28;

        for (int i = 0; i < max; i++) {
            PortalInfo p = portals.get(i + scrollOffset);

            String dimShort = shortDim(p.dimId());
            Component label = Component.literal(dimShort + " - " + p.name());

            int btnY = y + i * (buttonHeight + 6);

            Button b = Button.builder(label, (btn) -> {
                        // Envoie la demande de connexion au serveur
                        ClientPlayNetworking.send(new ConnectPortalPayload(this.menu.getKeyboardPos(), p.id()));
                    })
                    .bounds(x, btnY, buttonWidth, buttonHeight)
                    .build();

            portalButtons.add(b);
            this.addRenderableWidget(b);
        }
    }

    private String shortDim(ResourceLocation dim) {
        // ex: minecraft:overworld -> overworld
        String path = dim.getPath();
        if (path.contains("overworld")) return "OW";
        if (path.contains("the_nether")) return "NET";
        if (path.contains("the_end")) return "END";
        return path.length() > 3 ? path.substring(0, 3).toUpperCase() : path.toUpperCase();
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTicks, int mouseX, int mouseY) {
        g.blit(TEXTURE, leftPos, topPos, 0, 0, imageWidth, imageHeight, 256, 200);

        if (portals.size() > VISIBLE_PORTALS) {
            int trackX = leftPos + imageWidth - 28;
            int trackY = topPos + 34;
            int trackH = 60;
            int handleH = Math.max(10, trackH * VISIBLE_PORTALS / portals.size());
            int maxOffset = Math.max(1, portals.size() - VISIBLE_PORTALS);
            int handleY = trackY + (trackH - handleH) * scrollOffset / maxOffset;
            g.fill(trackX, trackY, trackX + 3, trackY + trackH, 0xFF1E222B);
            g.fill(trackX, handleY, trackX + 3, handleY + handleH, 0xFF7E6BC6);
        }
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        g.pose().pushPose();
        g.pose().translate(0.0F, 0.0F, 0.0F);
        g.pose().scale(1.2F, 1.2F, 1.0F);
        g.drawString(this.font, this.title, (int) (titleLabelX / 1.2F), (int) (titleLabelY / 1.2F), 0xE0E0E0, true);
        g.pose().popPose();

        g.drawString(this.font, this.playerInventoryTitle, inventoryLabelX, inventoryLabelY, 0xA0A0A0, false);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double deltaX, double deltaY) {
        if (portals.size() <= VISIBLE_PORTALS) return super.mouseScrolled(mouseX, mouseY, deltaX, deltaY);
        int direction = deltaY > 0 ? -1 : 1;
        scrollOffset = Mth.clamp(scrollOffset + direction, 0, portals.size() - VISIBLE_PORTALS);
        rebuildPortalButtons();
        return true;
    }

    private void clampScrollOffset() {
        int maxOffset = Math.max(0, portals.size() - VISIBLE_PORTALS);
        scrollOffset = Mth.clamp(scrollOffset, 0, maxOffset);
    }

    private void updateDisconnectButton() {
        if (disconnectButton == null) return;
        disconnectButton.visible = portalActive;
        disconnectButton.active = portalActive;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTicks) {
        this.renderBackground(g, mouseX, mouseY, partialTicks);
        super.render(g, mouseX, mouseY, partialTicks);
        this.renderTooltip(g, mouseX, mouseY);
    }
}
