package fr.geomtech.universegate.mixin.client;

import fr.geomtech.universegate.guide.GuideScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PauseScreen.class)
public abstract class PauseScreenPortalViewMixin extends Screen {
    @Shadow private Button disconnectButton;

    protected PauseScreenPortalViewMixin(Component title) {
        super(title);
    }

    /** Adds the "UniverseGate" button above Disconnect: it opens the guide, which also holds the mod's settings. */
    @Inject(method = "createPauseMenu", at = @At("TAIL"))
    private void universegate$addGuideButton(CallbackInfo ci) {
        int y = disconnectButton.getY();
        disconnectButton.setY(y + 24);
        Button guide = Button.builder(Component.translatable("guide.universegate.open"),
                button -> Minecraft.getInstance().setScreen(new GuideScreen(this)))
                .bounds(disconnectButton.getX(), y, disconnectButton.getWidth(), 20).build();
        guide.setTooltip(Tooltip.create(Component.translatable("guide.universegate.open.tooltip")));
        addRenderableWidget(guide);
    }
}
