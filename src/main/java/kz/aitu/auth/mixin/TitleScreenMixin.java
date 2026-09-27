package kz.aitu.auth.mixin;

import kz.aitu.auth.gui.widget.AituProfileWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mixin injected into net.minecraft.client.gui.screens.TitleScreen.
 * Replaces generic text buttons with a compact top-right profile widget
 * that opens AituHubScreen.
 */
@Mixin(TitleScreen.class)
public abstract class TitleScreenMixin extends Screen {

    protected TitleScreenMixin(Component title) {
        super(title);
    }

    @Inject(method = "init", at = @At("RETURN"))
    private void aitu$onTitleScreenInit(CallbackInfo ci) {
        // Injects compact profile widget in top-right corner
        this.addRenderableWidget(new AituProfileWidget(0, 0, this));
    }
}
