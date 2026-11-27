package fr.geomtech.universegate.mixin.client;

import fr.geomtech.universegate.seamless.client.PortalViewOptions;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PauseScreen.class)
public abstract class PauseScreenPortalViewMixin extends Screen {
    @Shadow private Button disconnectButton;

    protected PauseScreenPortalViewMixin(Component title) {
        super(title);
    }

    @Inject(method = "createPauseMenu", at = @At("TAIL"))
    private void universegate$addPortalViewOption(CallbackInfo ci) {
        int y = disconnectButton.getY();
        disconnectButton.setY(y + 24);
        Button toggle = Button.builder(universegate$label(), button -> {
            PortalViewOptions.toggle();
            button.setMessage(universegate$label());
        }).bounds(disconnectButton.getX(), y, disconnectButton.getWidth(), 20).build();
        toggle.setTooltip(Tooltip.create(Component.translatable("options.universegate.live_portal_view.tooltip")));
        addRenderableWidget(toggle);
    }

    @Unique
    private static Component universegate$label() {
        return Component.translatable("options.universegate.live_portal_view",
                Component.translatable(PortalViewOptions.enabled() ? "options.on" : "options.off"));
    }
}
