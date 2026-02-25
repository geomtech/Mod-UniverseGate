package fr.geomtech.universegate;

import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;

public final class DnaExtractionHudOverlay {

    private static final int BAR_WIDTH = 144;
    private static final int BAR_HEIGHT = 8;

    private DnaExtractionHudOverlay() {
    }

    public static void register() {
        HudRenderCallback.EVENT.register((guiGraphics, deltaTracker) -> render(guiGraphics));
    }

    private static void render(GuiGraphics guiGraphics) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.options.hideGui) return;

        LocalPlayer player = minecraft.player;
        if (player == null || !player.isUsingItem()) return;

        ItemStack usingStack = player.getUseItem();
        if (!usingStack.is(ModItems.DNA_EXTRACTOR)) return;
        if (!DnaExtractorItem.hasTarget(player)) return;

        int maxTicks = DnaExtractorItem.extractionDurationTicks();
        if (maxTicks <= 0) return;

        int elapsedTicks = player.getTicksUsingItem();
        float progress = Mth.clamp(elapsedTicks / (float) maxTicks, 0.0F, 1.0F);
        int percent = Mth.floor(progress * 100.0F);

        int screenWidth = guiGraphics.guiWidth();
        int screenHeight = guiGraphics.guiHeight();

        int x = (screenWidth - BAR_WIDTH) / 2;
        int y = screenHeight - 62;

        guiGraphics.fill(x - 1, y - 1, x + BAR_WIDTH + 1, y + BAR_HEIGHT + 1, 0xAA000000);
        guiGraphics.fill(x, y, x + BAR_WIDTH, y + BAR_HEIGHT, 0xFF0D131C);

        int fillWidth = Mth.clamp((int) Math.floor((BAR_WIDTH - 2) * progress), 0, BAR_WIDTH - 2);
        if (fillWidth > 0) {
            guiGraphics.fill(x + 1, y + 1, x + 1 + fillWidth, y + BAR_HEIGHT - 1, 0xFF4FA7FF);
        }

        Component text = Component.translatable("hud.universegate.dna_extraction_progress", percent);
        int textWidth = minecraft.font.width(text);
        guiGraphics.drawString(minecraft.font, text, (screenWidth - textWidth) / 2, y - 11, 0xDDE7FF, false);
    }
}
