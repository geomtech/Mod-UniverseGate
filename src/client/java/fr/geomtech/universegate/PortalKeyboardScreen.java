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

import java.util.ArrayList;
import java.util.List;

public class PortalKeyboardScreen extends AbstractContainerScreen<PortalKeyboardMenu> {

    private final List<PortalInfo> portals = new ArrayList<>();
    private final List<Button> portalButtons = new ArrayList<>();
    private boolean portalActive = false;
    private Button disconnectButton;

    public PortalKeyboardScreen(PortalKeyboardMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        this.imageWidth = 220;
        this.imageHeight = 222;
    }

    public net.minecraft.core.BlockPos getKeyboardPos() {
        return this.menu.getKeyboardPos();
    }

    /** Appelé par le handler réseau client quand la liste arrive */
    public void setPortals(List<PortalInfo> newList) {
        portals.clear();
        portals.addAll(newList);
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
                .bounds(leftPos + 132, topPos + 4, 80, 16)
                .build();
        this.addRenderableWidget(disconnectButton);
        updateDisconnectButton();
        rebuildPortalButtons();
        this.inventoryLabelY = 10000;
    }

    private void rebuildPortalButtons() {
        // Si l’écran n’est pas encore initialisé
        if (this.minecraft == null) return;

        // Supprime les anciens boutons
        for (Button b : portalButtons) this.removeWidget(b);
        portalButtons.clear();

        int max = Math.min(6, portals.size());
        int x = leftPos + 8;
        int y = topPos + 24;

        for (int i = 0; i < max; i++) {
            PortalInfo p = portals.get(i);

            String dimShort = shortDim(p.dimId());
            Component label = Component.literal(dimShort + " - " + p.name());

            int btnY = y + i * 18;

            Button b = Button.builder(label, (btn) -> {
                        // Envoie la demande de connexion au serveur
                        ClientPlayNetworking.send(new ConnectPortalPayload(this.menu.getKeyboardPos(), p.id()));
                    })
                    .bounds(x, btnY, 160, 16)
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
        g.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, 0xFF2B2B2B);
        g.drawString(this.font, "Fuel", leftPos + 180, topPos + 122, 0xFFFFFF, false);
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
