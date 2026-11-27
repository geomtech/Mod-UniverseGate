package fr.geomtech.universegate.guide;

import fr.geomtech.universegate.seamless.client.PortalViewOptions;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
//? if >=1.21.11 {
/*import net.minecraft.client.input.MouseButtonEvent;
*///?}

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The UniverseGate guide: chapters on the left, a scrolling page on the right with text, animated
 * build scenes, recipes and the mod's client settings. Drawn entirely by hand in the mod's style
 * (black panels, luminous white lines, 1 px pixel borders) instead of vanilla widgets, so it looks
 * the same in 1.21.1 and 1.21.11.
 */
public class GuideScreen extends Screen {
    private static final int WHITE = 0xFFFFFFFF;
    private static final int LIGHT = 0xFFD8D8D8;
    private static final int GREY = 0xFF8C8C8C;
    private static final int DIM = 0xFF3A3A3A;
    private static final int PANEL = 0xFF050505;
    private static final int BLACK = 0xFF000000;

    private static final int MARGIN = 10;
    private static final int HEADER = 22;
    private static final int SIDEBAR = 124;
    private static final int CHAPTER_H = 20;
    private static final int PAD = 10;
    private static final int SCENE_H = 150;
    private static final int SLOT = 18;
    private static final int RECIPE_W = SLOT * 3 + 30 + SLOT + 8;
    private static final int RECIPE_H = SLOT * 3 + 8;

    private static int lastChapter;

    private final Screen parent;
    private GuideBook book;
    private int chapter;
    private double scroll;
    private int contentHeight;
    private final Map<Integer, SceneState> scenes = new HashMap<>();
    private final List<Hotspot> hotspots = new ArrayList<>();
    private ItemStack hoveredStack = ItemStack.EMPTY;
    private Component hoveredText;

    private static final class SceneState {
        float time;
        boolean paused;
        int rotation;
        long last = System.currentTimeMillis();
    }

    private record Hotspot(int x, int y, int w, int h, Runnable action) {
        boolean contains(double mx, double my) {
            return mx >= x && mx < x + w && my >= y && my < y + h;
        }
    }

    public GuideScreen(Screen parent) {
        super(Component.translatable("guide.universegate.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        if (book == null) {
            book = GuideBook.load();
            chapter = Mth.clamp(lastChapter, 0, book.chapters.size() - 1);
        }
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(parent);
    }

    // ---------------------------------------------------------------- layout

    private int left() { return MARGIN; }
    private int top() { return MARGIN; }
    private int right() { return width - MARGIN; }
    private int bottom() { return height - MARGIN; }
    private int contentLeft() { return left() + SIDEBAR + 4; }
    private int contentTop() { return top() + HEADER + 4; }
    private int contentRight() { return right(); }
    private int contentBottom() { return bottom(); }
    private int textWidth() { return contentRight() - contentLeft() - PAD * 2 - 6; }

    // ---------------------------------------------------------------- rendering

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        boolean inWorld = Minecraft.getInstance().level != null;
        g.fill(0, 0, width, height, inWorld ? 0xC8000000 : BLACK);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        hotspots.clear();
        hoveredStack = ItemStack.EMPTY;
        hoveredText = null;
        if (book == null) return;

        // Header
        panel(g, left(), top(), right() - left(), HEADER);
        g.drawString(font, title, left() + 8, top() + 7, WHITE, false);
        Component close = Component.literal("×");
        int closeX = right() - 18;
        boolean closeHover = inside(mouseX, mouseY, closeX, top() + 3, 15, 15);
        if (closeHover) g.fill(closeX, top() + 3, closeX + 15, top() + 18, WHITE);
        g.drawString(font, close, closeX + 5, top() + 7, closeHover ? BLACK : WHITE, false);
        hotspots.add(new Hotspot(closeX, top() + 3, 15, 15, this::onClose));

        renderSidebar(g, mouseX, mouseY);
        renderContent(g, mouseX, mouseY);

        if (!hoveredStack.isEmpty()) GuideRender.itemTooltip(g, font, hoveredStack, mouseX, mouseY);
        else if (hoveredText != null) GuideRender.textTooltip(g, font, hoveredText, mouseX, mouseY);
    }

    private void renderSidebar(GuiGraphics g, int mouseX, int mouseY) {
        int x = left(), y = contentTop(), w = SIDEBAR, h = bottom() - contentTop();
        panel(g, x, y, w, h);
        int rowY = y + 5;
        for (int i = 0; i < book.chapters.size(); i++) {
            GuideBook.Chapter c = book.chapters.get(i);
            int rx = x + 4, rw = w - 8;
            if (rowY + CHAPTER_H > y + h - 4) break;
            boolean selected = i == chapter;
            boolean hover = inside(mouseX, mouseY, rx, rowY, rw, CHAPTER_H);
            if (selected) g.fill(rx, rowY, rx + rw, rowY + CHAPTER_H, WHITE);
            else if (hover) border(g, rx, rowY, rw, CHAPTER_H, GREY);
            if (!c.icon().isEmpty()) g.renderItem(c.icon(), rx + 2, rowY + 2);
            String name = font.plainSubstrByWidth(c.title(), rw - 24);
            g.drawString(font, name, rx + 21, rowY + 6, selected ? BLACK : (hover ? WHITE : LIGHT), false);
            final int index = i;
            hotspots.add(new Hotspot(rx, rowY, rw, CHAPTER_H, () -> selectChapter(index)));
            rowY += CHAPTER_H + 2;
        }
    }

    private void renderContent(GuiGraphics g, int mouseX, int mouseY) {
        int x = contentLeft(), y = contentTop(), w = contentRight() - contentLeft(), h = contentBottom() - contentTop();
        panel(g, x, y, w, h);
        int viewTop = y + 2, viewBottom = y + h - 2;
        scroll = Mth.clamp(scroll, 0, Math.max(0, contentHeight - (viewBottom - viewTop)));

        g.enableScissor(x + 1, viewTop, x + w - 1, viewBottom);
        int left = x + PAD;
        int cursor = viewTop + PAD - (int) Math.round(scroll);
        int start = cursor;
        GuideBook.Chapter c = book.chapters.get(chapter);
        for (int i = 0; i < c.elements().size(); i++) {
            cursor = renderElement(g, i, c.elements().get(i), left, cursor, textWidth(), mouseX, mouseY, viewTop, viewBottom);
        }
        contentHeight = cursor - start + PAD;
        g.disableScissor();

        // Scrollbar
        int view = viewBottom - viewTop;
        if (contentHeight > view) {
            int barX = x + w - 5;
            g.fill(barX, viewTop + 2, barX + 2, viewBottom - 2, DIM);
            int track = view - 4;
            int thumb = Math.max(12, track * view / contentHeight);
            int thumbY = viewTop + 2 + (int) ((track - thumb) * (scroll / (contentHeight - view)));
            g.fill(barX, thumbY, barX + 2, thumbY + thumb, WHITE);
        }
    }

    /** Draws one element at {@code y} and returns the y below it. */
    private int renderElement(GuiGraphics g, int index, GuideBook.Element element, int x, int y, int w,
                              int mouseX, int mouseY, int viewTop, int viewBottom) {
        boolean mouseInView = mouseY >= viewTop && mouseY < viewBottom;
        if (element instanceof GuideBook.Title title) {
            y += 4;
            g.drawString(font, title.text(), x, y, WHITE, false);
            g.fill(x, y + 11, x + Math.min(w, font.width(title.text()) + 12), y + 12, WHITE);
            return y + 18;
        }
        if (element instanceof GuideBook.Text text) {
            int color = text.placeholder() ? GREY : LIGHT;
            int textX = x;
            int textW = w;
            List<FormattedCharSequence> lines;
            if (text.placeholder()) {
                textX += 6;
                textW -= 6;
                Component tag = Component.translatable("guide.universegate.placeholder");
                lines = new ArrayList<>();
                lines.add(tag.getVisualOrderText());
                lines.addAll(font.split(FormattedText.of(text.text()), textW));
            } else {
                lines = font.split(FormattedText.of(text.text()), textW);
            }
            int lineY = y;
            for (FormattedCharSequence line : lines) {
                g.drawString(font, line, textX, lineY, color, false);
                lineY += 10;
            }
            if (text.placeholder()) {
                // dashed white margin marks text that still has to be written
                for (int dy = y; dy < lineY - 2; dy += 4) g.fill(x, dy, x + 2, dy + 2, GREY);
            }
            return lineY + 6;
        }
        if (element instanceof GuideBook.Scene scene) return renderScene(g, index, scene, x, y, w, mouseX, mouseY, mouseInView);
        if (element instanceof GuideBook.Recipes recipes) {
            int perRow = Math.max(1, (w + 8) / (RECIPE_W + 8));
            int col = 0;
            int rowY = y;
            for (GuideRecipes.Recipe recipe : recipes.recipes()) {
                renderRecipe(g, recipe, x + col * (RECIPE_W + 8), rowY, mouseX, mouseY, mouseInView);
                if (++col == perRow) {
                    col = 0;
                    rowY += RECIPE_H + 6;
                }
            }
            return (col == 0 ? rowY : rowY + RECIPE_H + 6) + 2;
        }
        if (element instanceof GuideBook.Items items) {
            int perRow = Math.max(1, w / (SLOT + 2));
            for (int i = 0; i < items.items().size(); i++) {
                int sx = x + (i % perRow) * (SLOT + 2), sy = y + (i / perRow) * (SLOT + 2);
                slot(g, items.items().get(i), sx, sy, mouseX, mouseY, mouseInView);
            }
            int rows = (items.items().size() + perRow - 1) / perRow;
            return y + rows * (SLOT + 2) + 6;
        }
        if (element instanceof GuideBook.Setting setting) {
            if ("live_portal_view".equals(setting.key())) {
                Component label = Component.translatable("options.universegate.live_portal_view",
                        Component.translatable(PortalViewOptions.enabled() ? "options.on" : "options.off"));
                int bw = Math.min(w, font.width(label) + 20);
                button(g, label, x, y, bw, 18, mouseX, mouseY, mouseInView, PortalViewOptions::toggle,
                        Component.translatable("options.universegate.live_portal_view.tooltip"));
                return y + 24;
            }
            return y;
        }
        return y;
    }

    private int renderScene(GuiGraphics g, int index, GuideBook.Scene element, int x, int y, int w,
                            int mouseX, int mouseY, boolean mouseInView) {
        GuideScene scene = element.scene();
        SceneState state = scenes.computeIfAbsent(index, i -> new SceneState());
        long now = System.currentTimeMillis();
        if (!state.paused) state.time += (now - state.last) / 50.0F;
        state.last = now;
        int length = scene.length();
        if (state.time >= length) state.time %= length;

        int boxW = w;
        border(g, x, y, boxW, SCENE_H, WHITE);
        // pixel corner notches
        g.fill(x, y, x + 1, y + 1, PANEL);
        g.fill(x + boxW - 1, y, x + boxW, y + 1, PANEL);
        g.fill(x, y + SCENE_H - 1, x + 1, y + SCENE_H, PANEL);
        g.fill(x + boxW - 1, y + SCENE_H - 1, x + boxW, y + SCENE_H, PANEL);
        scene.render(g, x + 1, y + 1, boxW - 2, SCENE_H - 2, state.time, state.rotation);

        int step = scene.stepAt(state.time);
        Component counter = Component.translatable("guide.universegate.scene.step", step + 1, scene.stepCount());
        g.drawString(font, counter, x + boxW - font.width(counter) - 5, y + 5, GREY, false);

        // controls
        int cy = y + SCENE_H + 3;
        int bx = x;
        bx = sceneButton(g, "◀", bx, cy, mouseX, mouseY, mouseInView, "guide.universegate.scene.previous", () -> {
            int current = scene.stepAt(state.time);
            state.time = scene.startOf(Math.max(0, current - 1));
        });
        bx = sceneButton(g, state.paused ? "▶" : "||", bx, cy, mouseX, mouseY, mouseInView,
                state.paused ? "guide.universegate.scene.play" : "guide.universegate.scene.pause",
                () -> state.paused = !state.paused);
        bx = sceneButton(g, "▶▶", bx, cy, mouseX, mouseY, mouseInView, "guide.universegate.scene.next", () -> {
            int current = scene.stepAt(state.time);
            state.time = current + 1 < scene.stepCount() ? scene.startOf(current + 1) : 0;
        });
        bx = sceneButton(g, "↻", bx, cy, mouseX, mouseY, mouseInView, "guide.universegate.scene.rotate",
                () -> state.rotation = (state.rotation + 1) & 3);

        // progress bar of the current step
        int barX = bx + 4, barW = x + boxW - barX;
        int stepStart = scene.startOf(step);
        int stepLength = scene.steps.get(step).duration();
        float progress = Mth.clamp((state.time - stepStart) / stepLength, 0.0F, 1.0F);
        g.fill(barX, cy + 6, barX + barW, cy + 7, DIM);
        g.fill(barX, cy + 6, barX + (int) (barW * progress), cy + 7, WHITE);

        int textY = cy + 18;
        if (step < element.captions().size()) {
            for (FormattedCharSequence line : font.split(FormattedText.of(element.captions().get(step)), w)) {
                g.drawString(font, line, x, textY, WHITE, false);
                textY += 10;
            }
        }
        return textY + 8;
    }

    private int sceneButton(GuiGraphics g, String label, int x, int y, int mouseX, int mouseY, boolean mouseInView,
                            String tooltip, Runnable action) {
        int w = Math.max(14, font.width(label) + 8);
        button(g, Component.literal(label), x, y, w, 13, mouseX, mouseY, mouseInView, action, Component.translatable(tooltip));
        return x + w + 2;
    }

    private void button(GuiGraphics g, Component label, int x, int y, int w, int h, int mouseX, int mouseY,
                        boolean mouseInView, Runnable action, Component tooltip) {
        boolean hover = mouseInView && inside(mouseX, mouseY, x, y, w, h);
        if (hover) g.fill(x, y, x + w, y + h, WHITE);
        else border(g, x, y, w, h, LIGHT);
        g.drawString(font, label, x + (w - font.width(label)) / 2, y + (h - 8) / 2, hover ? BLACK : WHITE, false);
        if (mouseInView) hotspots.add(new Hotspot(x, y, w, h, action));
        if (hover && tooltip != null) hoveredText = tooltip;
    }

    private void renderRecipe(GuiGraphics g, GuideRecipes.Recipe recipe, int x, int y, int mouseX, int mouseY, boolean mouseInView) {
        border(g, x, y, RECIPE_W, RECIPE_H, DIM);
        int gx = x + 4, gy = y + 4;
        if (recipe.kind() == GuideRecipes.Kind.CRAFTING) {
            for (int i = 0; i < 9; i++) slot(g, recipe.grid()[i], gx + (i % 3) * SLOT, gy + (i / 3) * SLOT, mouseX, mouseY, mouseInView);
        } else {
            slot(g, recipe.grid()[0], gx + SLOT, gy, mouseX, mouseY, mouseInView);
            slot(g, new ItemStack(Items.FURNACE), gx + SLOT, gy + SLOT * 2, mouseX, mouseY, mouseInView);
            // flame between input and furnace
            g.fill(gx + SLOT + 8, gy + SLOT + 4, gx + SLOT + 10, gy + SLOT + 14, GREY);
        }
        // arrow, in pixels
        int ax = gx + SLOT * 3 + 6, ay = gy + SLOT + 8;
        g.fill(ax, ay, ax + 14, ay + 2, WHITE);
        for (int i = 0; i < 4; i++) g.fill(ax + 10 + i, ay - 3 + i, ax + 11 + i, ay + 5 - i, WHITE);
        slot(g, recipe.result(), gx + SLOT * 3 + 26, gy + SLOT, mouseX, mouseY, mouseInView);
    }

    private void slot(GuiGraphics g, ItemStack stack, int x, int y, int mouseX, int mouseY, boolean mouseInView) {
        g.fill(x, y, x + SLOT, y + SLOT, DIM);
        g.fill(x + 1, y + 1, x + SLOT - 1, y + SLOT - 1, BLACK);
        if (stack.isEmpty()) return;
        g.renderItem(stack, x + 1, y + 1);
        g.renderItemDecorations(font, stack, x + 1, y + 1);
        if (mouseInView && inside(mouseX, mouseY, x, y, SLOT, SLOT)) {
            hoveredStack = stack;
        }
    }

    private static void panel(GuiGraphics g, int x, int y, int w, int h) {
        g.fill(x, y, x + w, y + h, PANEL);
        border(g, x, y, w, h, WHITE);
        // pixel corners
        g.fill(x, y, x + 1, y + 1, BLACK);
        g.fill(x + w - 1, y, x + w, y + 1, BLACK);
        g.fill(x, y + h - 1, x + 1, y + h, BLACK);
        g.fill(x + w - 1, y + h - 1, x + w, y + h, BLACK);
    }

    private static void border(GuiGraphics g, int x, int y, int w, int h, int color) {
        g.fill(x, y, x + w, y + 1, color);
        g.fill(x, y + h - 1, x + w, y + h, color);
        g.fill(x, y + 1, x + 1, y + h - 1, color);
        g.fill(x + w - 1, y + 1, x + w, y + h - 1, color);
    }

    private static boolean inside(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    // ---------------------------------------------------------------- input

    private void selectChapter(int index) {
        if (index == chapter) return;
        chapter = index;
        lastChapter = index;
        scroll = 0;
        scenes.clear();
    }

    private boolean click(double mouseX, double mouseY, int button) {
        if (button != 0) return false;
        for (Hotspot hotspot : List.copyOf(hotspots)) {
            if (hotspot.contains(mouseX, mouseY)) {
                Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
                hotspot.action().run();
                return true;
            }
        }
        return false;
    }

    //? if >=1.21.11 {
    /*@Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        return click(event.x(), event.y(), event.button()) || super.mouseClicked(event, doubleClick);
    }
    *///?} else {
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        return click(mouseX, mouseY, button) || super.mouseClicked(mouseX, mouseY, button);
    }
    //?}

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (mouseX >= contentLeft()) {
            scroll -= scrollY * 20;
            return true;
        }
        if (book != null && scrollY != 0) {
            selectChapter(Mth.clamp(chapter - (int) Math.signum(scrollY), 0, book.chapters.size() - 1));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }
}
