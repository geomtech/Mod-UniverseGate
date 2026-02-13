package fr.geomtech.universegate;

import fr.geomtech.universegate.PortalInfo;
import fr.geomtech.universegate.PortalKeyboardMenu;
import fr.geomtech.universegate.net.ConnectPortalPayload;
import fr.geomtech.universegate.net.DisconnectPortalPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class PortalKeyboardScreen extends AbstractContainerScreen<PortalKeyboardMenu> {

    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath("universegate", "textures/gui/portal_keyboard.png");

    private final List<PortalInfo> portals = new ArrayList<>();
    private final List<PortalInfo> filteredPortals = new ArrayList<>();
    private final List<Button> portalButtons = new ArrayList<>();
    private EditBox searchBox;
    private String searchFilter = "";
    private boolean portalActive = false;
    private boolean disconnectAllowed = false;
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
        refreshFilteredPortals();
        rebuildPortalButtons();
    }

    public void setPortalStatus(boolean active, boolean disconnectAllowed) {
        this.portalActive = active;
        this.disconnectAllowed = disconnectAllowed;
        updateDisconnectButton();
    }

    @Override
    protected void init() {
        super.init();

        searchBox = new EditBox(this.font,
                leftPos + 10,
                topPos + 10,
                150,
                16,
                Component.translatable("gui.universegate.search_portal"));
        searchBox.setMaxLength(32);
        searchBox.setValue(searchFilter);
        searchBox.setHint(Component.translatable("gui.universegate.search_portal"));
        searchBox.setResponder((value) -> {
            searchFilter = value == null ? "" : value;
            scrollOffset = 0;
            refreshFilteredPortals();
            rebuildPortalButtons();
        });
        this.addRenderableWidget(searchBox);

        disconnectButton = Button.builder(Component.translatable("gui.universegate.disconnect"), (btn) -> {
                    ClientPlayNetworking.send(new DisconnectPortalPayload(this.menu.getKeyboardPos()));
                })
                .bounds(leftPos + imageWidth - 88, topPos + 10, 78, 16)
                .build();
        this.addRenderableWidget(disconnectButton);
        updateDisconnectButton();
        refreshFilteredPortals();
        rebuildPortalButtons();
    }

    private void rebuildPortalButtons() {
        // Si l’écran n’est pas encore initialisé
        if (this.minecraft == null) return;

        // Supprime les anciens boutons
        for (Button b : portalButtons) this.removeWidget(b);
        portalButtons.clear();

        clampScrollOffset(filteredPortals.size());
        int max = Math.min(VISIBLE_PORTALS, filteredPortals.size() - scrollOffset);
        int buttonWidth = 200;
        int buttonHeight = 20;
        int x = leftPos + (imageWidth - buttonWidth) / 2;
        int y = topPos + 34;

        for (int i = 0; i < max; i++) {
            PortalInfo p = filteredPortals.get(i + scrollOffset);

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

        if (filteredPortals.size() > VISIBLE_PORTALS) {
            int trackX = leftPos + imageWidth - 28;
            int trackY = topPos + 40;
            int trackH = 60;
            int handleH = Math.max(10, trackH * VISIBLE_PORTALS / filteredPortals.size());
            int maxOffset = Math.max(1, filteredPortals.size() - VISIBLE_PORTALS);
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
        if (filteredPortals.size() <= VISIBLE_PORTALS) return super.mouseScrolled(mouseX, mouseY, deltaX, deltaY);
        int direction = deltaY > 0 ? -1 : 1;
        scrollOffset = Mth.clamp(scrollOffset + direction, 0, filteredPortals.size() - VISIBLE_PORTALS);
        rebuildPortalButtons();
        return true;
    }

    private void clampScrollOffset(int portalCount) {
        int maxOffset = Math.max(0, portalCount - VISIBLE_PORTALS);
        scrollOffset = Mth.clamp(scrollOffset, 0, maxOffset);
    }

    private void refreshFilteredPortals() {
        filteredPortals.clear();
        String query = searchFilter == null ? "" : searchFilter.trim().toLowerCase(Locale.ROOT);

        for (PortalInfo portal : portals) {
            if (query.isEmpty() || portal.name().toLowerCase(Locale.ROOT).contains(query)) {
                filteredPortals.add(portal);
            }
        }
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (searchBox != null && searchBox.isFocused() && this.minecraft != null) {
            if (this.minecraft.options.keyInventory.matches(keyCode, scanCode)) return true;
            if (searchBox.keyPressed(keyCode, scanCode, modifiers)) return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (searchBox != null && searchBox.isFocused() && searchBox.charTyped(codePoint, modifiers)) return true;
        return super.charTyped(codePoint, modifiers);
    }

    private void updateDisconnectButton() {
        if (disconnectButton == null) return;
        boolean canDisconnect = portalActive && disconnectAllowed;
        disconnectButton.visible = canDisconnect;
        disconnectButton.active = canDisconnect;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTicks) {
        this.renderBackground(g, mouseX, mouseY, partialTicks);
        super.render(g, mouseX, mouseY, partialTicks);
        this.renderTooltip(g, mouseX, mouseY);
    }
}
