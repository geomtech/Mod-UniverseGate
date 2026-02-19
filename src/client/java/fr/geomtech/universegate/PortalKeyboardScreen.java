package fr.geomtech.universegate;

import fr.geomtech.universegate.PortalInfo;
import fr.geomtech.universegate.PortalKeyboardMenu;
import fr.geomtech.universegate.net.ConnectPortalPayload;
import fr.geomtech.universegate.net.DisconnectPortalPayload;
import fr.geomtech.universegate.net.UniverseGateNetwork;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.BooleanSupplier;

public class PortalKeyboardScreen extends AbstractContainerScreen<AbstractContainerMenu> {

    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath("universegate", "textures/gui/portal_keyboard.png");
    private static final ResourceLocation ALT_FONT =
            ResourceLocation.fromNamespaceAndPath("minecraft", "alt");

    private final List<PortalInfo> portals = new ArrayList<>();
    private final List<String> portalSearchIndex = new ArrayList<>();
    private final List<PortalInfo> filteredPortals = new ArrayList<>();
    private final List<Button> portalButtons = new ArrayList<>();
    private final List<VisiblePortalRow> visibleRows = new ArrayList<>();
    private EditBox searchBox;
    private String searchFilter = "";
    private boolean portalActive = false;
    private boolean disconnectAllowed = false;
    private Button disconnectButton;
    private boolean activationRequested = false;
    private int scrollOffset = 0;

    private static final int VISIBLE_PORTALS = 5;
    private static final int ROW_X = 10;
    private static final int ROW_Y = 34;
    private static final int ROW_BUTTON_WIDTH = 156;
    private static final int ROW_HEIGHT = 20;
    private static final int ROW_GAP = 6;
    private static final int COST_BADGE_X = 172;
    private static final int COST_BADGE_WIDTH = 54;

    private String lastErrorMessage = null;
    private final BlockPos keyboardPos;
    private final BooleanSupplier darkPoweredSupplier;
    private final boolean naturalKeyboard;

    private record VisiblePortalRow(PortalInfo portal, int y, boolean darkLocked) {}

    public PortalKeyboardScreen(AbstractContainerMenu menu, Inventory inv, Component title) {
        this(menu, inv, title, resolveKeyboardPos(menu), resolveDarkPoweredSupplier(menu), resolveNaturalKeyboard(menu));
    }

    public PortalKeyboardScreen(PortalKeyboardMenu menu, Inventory inv, Component title) {
        this((AbstractContainerMenu) menu, inv, title);
    }

    public PortalKeyboardScreen(PortalNaturalKeyboardMenu menu, Inventory inv, Component title) {
        this((AbstractContainerMenu) menu, inv, title);
    }

    public PortalKeyboardScreen(PortalMobileKeyboardMenu menu, Inventory inv, Component title) {
        this((AbstractContainerMenu) menu, inv, title);
    }

    private PortalKeyboardScreen(AbstractContainerMenu menu,
                                 Inventory inv,
                                 Component title,
                                 BlockPos keyboardPos,
                                 BooleanSupplier darkPoweredSupplier,
                                 boolean naturalKeyboard) {
        super(menu, inv, title);
        this.keyboardPos = keyboardPos;
        this.darkPoweredSupplier = darkPoweredSupplier;
        this.naturalKeyboard = naturalKeyboard;
        this.imageWidth = 256;
        this.imageHeight = 172;
        this.inventoryLabelY = 10000;
    }

    private static BlockPos resolveKeyboardPos(AbstractContainerMenu menu) {
        if (menu instanceof PortalKeyboardMenu keyboardMenu) {
            return keyboardMenu.getKeyboardPos();
        }
        if (menu instanceof PortalNaturalKeyboardMenu naturalKeyboardMenu) {
            return naturalKeyboardMenu.getKeyboardPos();
        }
        if (menu instanceof PortalMobileKeyboardMenu mobileKeyboardMenu) {
            return mobileKeyboardMenu.getKeyboardPos();
        }
        throw new IllegalArgumentException("Unsupported keyboard menu type: " + menu.getClass().getName());
    }

    private static BooleanSupplier resolveDarkPoweredSupplier(AbstractContainerMenu menu) {
        if (menu instanceof PortalKeyboardMenu keyboardMenu) {
            return keyboardMenu::isDarkPowered;
        }
        if (menu instanceof PortalNaturalKeyboardMenu naturalKeyboardMenu) {
            return naturalKeyboardMenu::isDarkPowered;
        }
        if (menu instanceof PortalMobileKeyboardMenu mobileKeyboardMenu) {
            return mobileKeyboardMenu::isDarkPowered;
        }
        throw new IllegalArgumentException("Unsupported keyboard menu type: " + menu.getClass().getName());
    }

    private static boolean resolveNaturalKeyboard(AbstractContainerMenu menu) {
        if (menu instanceof PortalKeyboardMenu) return false;
        if (menu instanceof PortalNaturalKeyboardMenu) return true;
        if (menu instanceof PortalMobileKeyboardMenu) return false;
        throw new IllegalArgumentException("Unsupported keyboard menu type: " + menu.getClass().getName());
    }

    private boolean lastPoweredState = false;

    @Override
    protected void containerTick() {
        super.containerTick();
        boolean powered = this.darkPoweredSupplier.getAsBoolean();
        if (powered != lastPoweredState) {
            lastPoweredState = powered;
            rebuildPortalButtons();
        }
    }

    public BlockPos getKeyboardPos() {
        return keyboardPos;
    }

    /** Appelé par le handler réseau client quand la liste arrive */
    public void setPortals(List<PortalInfo> newList) {
        portals.clear();
        portalSearchIndex.clear();
        portals.addAll(newList);
        for (PortalInfo portal : newList) {
            portalSearchIndex.add(portal.name().toLowerCase(Locale.ROOT));
        }
        scrollOffset = 0;
        lastErrorMessage = null;
        refreshFilteredPortals();
        rebuildPortalButtons();
    }

    public void setPortalStatus(boolean active, boolean disconnectAllowed) {
        boolean shouldClose = activationRequested && !this.portalActive && active;
        activationRequested = false;
        this.portalActive = active;
        this.disconnectAllowed = disconnectAllowed;
        if (active) {
            lastErrorMessage = null;
        }
        updateDisconnectButton();
        if (shouldClose) {
            this.onClose();
        }
    }

    @Override
    protected void init() {
        super.init();

        portalButtons.clear();
        visibleRows.clear();

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
                    ClientPlayNetworking.send(new DisconnectPortalPayload(this.getKeyboardPos()));
                })
                .bounds(leftPos + imageWidth - 88, topPos + 10, 78, 16)
                .build();
        this.addRenderableWidget(disconnectButton);
        updateDisconnectButton();

        ensurePortalButtons();
        refreshFilteredPortals();
        rebuildPortalButtons();
    }

    private void ensurePortalButtons() {
        if (!portalButtons.isEmpty()) {
            return;
        }

        int x = leftPos + ROW_X;
        int y = topPos + ROW_Y;
        for (int i = 0; i < VISIBLE_PORTALS; i++) {
            int rowIndex = i;
            int btnY = y + i * (ROW_HEIGHT + ROW_GAP);
            Button button = Button.builder(Component.empty(), (btn) -> onPortalButtonPressed(rowIndex))
                    .bounds(x, btnY, ROW_BUTTON_WIDTH, ROW_HEIGHT)
                    .build();
            button.visible = false;
            button.active = false;
            portalButtons.add(button);
            this.addRenderableWidget(button);
        }
    }

    private void onPortalButtonPressed(int rowIndex) {
        if (rowIndex < 0 || rowIndex >= visibleRows.size()) {
            return;
        }

        VisiblePortalRow row = visibleRows.get(rowIndex);
        if (row.darkLocked()) {
            return;
        }

        activationRequested = true;
        ClientPlayNetworking.send(new ConnectPortalPayload(this.getKeyboardPos(), row.portal().id()));
    }

    private void rebuildPortalButtons() {
        if (this.minecraft == null) return;
        ensurePortalButtons();
        visibleRows.clear();

        clampScrollOffset(filteredPortals.size());
        int max = Math.min(VISIBLE_PORTALS, Math.max(0, filteredPortals.size() - scrollOffset));
        boolean darkPowered = this.darkPoweredSupplier.getAsBoolean();

        for (int i = 0; i < VISIBLE_PORTALS; i++) {
            Button button = portalButtons.get(i);
            if (i >= max) {
                button.visible = false;
                button.active = false;
                continue;
            }

            PortalInfo portal = filteredPortals.get(i + scrollOffset);
            boolean isDarkDim = UniverseGateNetwork.DARK_DIMENSION_ID.equals(portal.id());
            boolean darkLocked = isDarkDim && !darkPowered;

            int rowY = ROW_Y + i * (ROW_HEIGHT + ROW_GAP);
            visibleRows.add(new VisiblePortalRow(portal, rowY, darkLocked));

            button.visible = true;
            button.active = !darkLocked;
            button.setMessage(buildPortalButtonLabel(portal, isDarkDim, darkLocked));
        }
    }

    private Component buildPortalButtonLabel(PortalInfo portal, boolean isDarkDim, boolean darkLocked) {
        String dimShort = shortDim(portal.dimId());
        String buttonText = darkLocked
                ? "??? > " + portal.name()
                : dimShort + " > " + portal.name();

        Component label = Component.literal(trimToWidth(buttonText, ROW_BUTTON_WIDTH - 12));
        if (darkLocked) {
            return label.copy()
                    .withStyle(ChatFormatting.GRAY)
                    .withStyle(style -> style.withFont(ALT_FONT));
        }
        if (isDarkDim) {
            return label.copy().withStyle(ChatFormatting.DARK_PURPLE);
        }
        return label;
    }

    private String shortDim(ResourceLocation dim) {
        String path = dim.getPath();
        if (path.contains("overworld")) return "OW";
        if (path.contains("the_nether")) return "NET";
        if (path.contains("the_end")) return "END";
        return path.length() > 3 ? path.substring(0, 3).toUpperCase() : path.toUpperCase();
    }

    private String trimToWidth(String value, int maxWidth) {
        if (this.font.width(value) <= maxWidth) return value;
        String ellipsis = "...";
        int ellipsisWidth = this.font.width(ellipsis);
        String trimmed = this.font.plainSubstrByWidth(value, Math.max(0, maxWidth - ellipsisWidth));
        return trimmed + ellipsis;
    }

    private String formatCostBadge(int openEnergyCost, boolean darkLocked) {
        if (darkLocked) return "LOCK";
        if (openEnergyCost <= 0 || naturalKeyboard) return Component.translatable("gui.universegate.portal_cost_free").getString();

        if (openEnergyCost >= 1_000_000_000) {
            return String.format(Locale.ROOT, "%.1fG", openEnergyCost / 1_000_000_000.0D);
        }
        if (openEnergyCost >= 1_000_000) {
            return String.format(Locale.ROOT, "%.1fM", openEnergyCost / 1_000_000.0D);
        }
        if (openEnergyCost >= 10_000) {
            return String.format(Locale.ROOT, "%.1fk", openEnergyCost / 1_000.0D);
        }
        return Integer.toString(openEnergyCost);
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTicks, int mouseX, int mouseY) {
        g.blit(TEXTURE, leftPos, topPos, 0, 0, imageWidth, 113, 256, 200);

        for (int i = 0; i < 4; i++) {
            g.blit(TEXTURE, leftPos, topPos + 113 + (i * 13), 0, 100, imageWidth, 13, 256, 200);
        }

        g.blit(TEXTURE, leftPos, topPos + 165, 0, 193, imageWidth, 7, 256, 200);

        for (VisiblePortalRow row : visibleRows) {
            int badgeX = leftPos + COST_BADGE_X;
            int badgeY = topPos + row.y() + 2;

            int borderColor;
            int fillColor;
            if (row.darkLocked()) {
                borderColor = 0xFF6A3A3A;
                fillColor = 0xFF2E1A1A;
            } else if (row.portal().openEnergyCost() <= 0 || naturalKeyboard) {
                borderColor = 0xFF4C8B67;
                fillColor = 0xFF203A2B;
            } else {
                borderColor = 0xFF5E7FA1;
                fillColor = 0xFF223349;
            }

            g.fill(badgeX - 1, badgeY - 1, badgeX + COST_BADGE_WIDTH + 1, badgeY + ROW_HEIGHT - 2, borderColor);
            g.fill(badgeX, badgeY, badgeX + COST_BADGE_WIDTH, badgeY + ROW_HEIGHT - 3, fillColor);
        }

        if (filteredPortals.size() > VISIBLE_PORTALS) {
            int trackX = leftPos + imageWidth - 8;
            int trackY = topPos + 40;
            int trackH = 112;
            int handleH = Math.max(10, trackH * VISIBLE_PORTALS / filteredPortals.size());
            int maxOffset = Math.max(1, filteredPortals.size() - VISIBLE_PORTALS);
            int handleY = trackY + (trackH - handleH) * scrollOffset / maxOffset;
            g.fill(trackX, trackY, trackX + 3, trackY + trackH, 0xFF1E222B);
            g.fill(trackX, handleY, trackX + 3, handleY + handleH, 0xFF61A7D8);
        }
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        g.drawString(this.font, Component.translatable("gui.universegate.portal_destination_header"), ROW_X, 24, 0xD4DEEB, false);
        g.drawString(this.font, Component.translatable("gui.universegate.portal_cost_header"), COST_BADGE_X + 10, 24, 0xD4DEEB, false);

        for (VisiblePortalRow row : visibleRows) {
            String cost = formatCostBadge(row.portal().openEnergyCost(), row.darkLocked());
            int textWidth = this.font.width(cost);
            int textX = COST_BADGE_X + Math.max(0, (COST_BADGE_WIDTH - textWidth) / 2);
            int textY = row.y() + 6;

            int textColor;
            if (row.darkLocked()) {
                textColor = 0xFFE5ABAB;
            } else if (row.portal().openEnergyCost() <= 0 || naturalKeyboard) {
                textColor = 0xFFC8F7DD;
            } else {
                textColor = 0xFFE1EDFC;
            }
            g.drawString(this.font, cost, textX, textY, textColor, false);
        }

        if (lastErrorMessage != null && !lastErrorMessage.isEmpty()) {
            g.drawString(this.font, lastErrorMessage, 10, 160, 0xFF5555);
        }
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

        if (portalSearchIndex.size() != portals.size()) {
            portalSearchIndex.clear();
            for (PortalInfo portal : portals) {
                portalSearchIndex.add(portal.name().toLowerCase(Locale.ROOT));
            }
        }

        for (int i = 0; i < portals.size(); i++) {
            if (query.isEmpty() || portalSearchIndex.get(i).contains(query)) {
                filteredPortals.add(portals.get(i));
            }
        }
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (searchBox != null && searchBox.isFocused() && this.minecraft != null) {
            if (searchBox.keyPressed(keyCode, scanCode, modifiers)) return true;
            if (searchBox.canConsumeInput()) return true;
            if (this.minecraft.options.keyInventory.matches(keyCode, scanCode)) return true;
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

    public void showError(String message) {
        this.lastErrorMessage = message;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTicks) {
        this.renderBackground(g, mouseX, mouseY, partialTicks);
        super.render(g, mouseX, mouseY, partialTicks);
        this.renderTooltip(g, mouseX, mouseY);
    }
}
