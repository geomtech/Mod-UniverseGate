package fr.geomtech.universegate.mixin.client;

import fr.geomtech.universegate.guide.GuideButtons;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(TitleScreen.class)
public abstract class TitleScreenGuideMixin extends Screen {
    protected TitleScreenGuideMixin(Component title) {
        super(title);
    }

    /** A square UniverseGate button to the right of Singleplayer: the guide can be read before joining a world. */
    @Inject(method = "init", at = @At("TAIL"))
    private void universegate$addGuideButton(CallbackInfo ci) {
        Button guide = GuideButtons.create(this, width / 2 + 104, height / 4 + 48, 20, true);
        addRenderableWidget(guide);
        addRenderableOnly(GuideButtons.icon(guide));
    }
}
