package kz.aitu.auth.mixin;

import kz.aitu.auth.config.SessionManager;
import kz.aitu.auth.gui.AituAuthScreen;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mixin injected into net.minecraft.client.gui.screens.TitleScreen.
 * Injects a warning button/badge if aitu_session.json is missing or invalid.
 */
@Mixin(TitleScreen.class)
public abstract class TitleScreenMixin extends Screen {

    protected TitleScreenMixin(Component title) {
        super(title);
    }

    @Inject(method = "init", at = @At("RETURN"))
    private void aitu$onTitleScreenInit(CallbackInfo ci) {
        boolean hasValidSession = SessionManager.getInstance().hasValidSession();

        int buttonWidth = 145;
        int buttonHeight = 20;
        int x = this.width - buttonWidth - 10;
        int y = 10;

        if (!hasValidSession) {
            // Render prominent custom warning badge/button: "Link AITU Account"
            Component buttonText = Component.literal("⚠ ")
                    .withStyle(ChatFormatting.GOLD)
                    .append(Component.translatable("aitu_auth.link_button").withStyle(ChatFormatting.RED, ChatFormatting.BOLD));

            Button warningButton = Button.builder(buttonText, btn -> {
                Minecraft.getInstance().setScreen(new AituAuthScreen(this));
            })
            .bounds(x, y, buttonWidth, buttonHeight)
            .tooltip(Tooltip.create(
                    Component.literal("§cYour AITU account is not linked!\n§fClick here to authenticate with the Telegram bot.")
            ))
            .build();

            this.addRenderableWidget(warningButton);
        } else {
            // Subtle linked badge/button for management & status
            Component linkedText = Component.literal("✔ ")
                    .withStyle(ChatFormatting.GREEN)
                    .append(Component.translatable("aitu_auth.status_linked").withStyle(ChatFormatting.GREEN));

            Button linkedButton = Button.builder(linkedText, btn -> {
                Minecraft.getInstance().setScreen(new AituAuthScreen(this));
            })
            .bounds(x, y, buttonWidth, buttonHeight)
            .tooltip(Tooltip.create(
                    Component.literal("§aAccount Linked:\n§f" +
                            SessionManager.getInstance().getSession().map(s -> s.getCachedNickname() + " (ID: " + s.getTelegramId() + ")").orElse("Active") +
                            "\n§7Click to manage or unlink.")
            ))
            .build();

            this.addRenderableWidget(linkedButton);
        }
    }
}
