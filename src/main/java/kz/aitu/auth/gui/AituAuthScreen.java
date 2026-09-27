package kz.aitu.auth.gui;

import kz.aitu.auth.AituAuthClient;
import kz.aitu.auth.config.SessionData;
import kz.aitu.auth.config.SessionManager;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

/**
 * Authentication modal screen allowing the player to:
 * 1. Copy the Telegram bot direct link to clipboard.
 * 2. Input a one-time linking token or full session JSON.
 * 3. Validate, save to config/aitu_session.json, and refresh UI state.
 */
public class AituAuthScreen extends Screen {

    private static final Logger LOGGER = LoggerFactory.getLogger(AituAuthScreen.class);

    private final Screen parentScreen;
    private EditBox tokenEditBox;
    private EditBox telegramIdEditBox;
    private Button submitButton;
    private Button copyLinkButton;
    private Button openBrowserButton;
    private Button unlinkButton;

    private Component statusMessage = Component.empty();
    private boolean isSuccessStatus = false;

    public AituAuthScreen(Screen parentScreen) {
        super(Component.translatable("aitu_auth.title"));
        this.parentScreen = parentScreen;
    }

    @Override
    protected void init() {
        super.init();

        int centerX = this.width / 2;
        int startY = 30;

        // Populate existing session data if available
        SessionManager sessionManager = SessionManager.getInstance();
        SessionData existingSession = sessionManager.getSession().orElse(null);

        // 1. Copy Telegram Bot link button
        this.copyLinkButton = Button.builder(
                Component.translatable("aitu_auth.copy_bot_link"),
                btn -> {
                    if (this.minecraft != null) {
                        this.minecraft.keyboardHandler.setClipboard(AituAuthClient.TELEGRAM_BOT_URL);
                        this.statusMessage = Component.translatable("aitu_auth.copied_toast").withStyle(ChatFormatting.GREEN);
                        this.isSuccessStatus = true;
                        btn.setMessage(Component.literal("✔ Copied!").withStyle(ChatFormatting.GREEN));
                    }
                }
        ).bounds(centerX - 155, startY + 50, 150, 20).build();
        this.addRenderableWidget(this.copyLinkButton);

        // 2. Open Telegram Bot in Browser button
        this.openBrowserButton = Button.builder(
                Component.translatable("aitu_auth.open_bot"),
                btn -> Util.getPlatform().openUri(AituAuthClient.TELEGRAM_BOT_URL)
        ).bounds(centerX + 5, startY + 50, 150, 20).build();
        this.addRenderableWidget(this.openBrowserButton);

        // 3. Token input box
        this.tokenEditBox = new EditBox(
                this.font,
                centerX - 155,
                startY + 90,
                310,
                20,
                Component.literal("Token Input")
        );
        this.tokenEditBox.setMaxLength(1024);
        this.tokenEditBox.setHint(Component.translatable("aitu_auth.token_hint").withStyle(ChatFormatting.DARK_GRAY));
        if (existingSession != null && existingSession.getSessionToken() != null) {
            this.tokenEditBox.setValue(existingSession.getSessionToken());
        }
        this.addRenderableWidget(this.tokenEditBox);

        // 4. Telegram ID input box (optional override if raw token is used)
        this.telegramIdEditBox = new EditBox(
                this.font,
                centerX - 155,
                startY + 125,
                310,
                20,
                Component.literal("Telegram ID Input")
        );
        this.telegramIdEditBox.setMaxLength(32);
        this.telegramIdEditBox.setHint(Component.literal("Telegram ID (e.g. 123456789, auto-detected if in token/JSON)").withStyle(ChatFormatting.DARK_GRAY));
        if (existingSession != null && existingSession.getTelegramId() > 0) {
            this.telegramIdEditBox.setValue(String.valueOf(existingSession.getTelegramId()));
        }
        this.addRenderableWidget(this.telegramIdEditBox);

        // 5. Submit / Link button
        this.submitButton = Button.builder(
                Component.translatable("aitu_auth.link_submit").withStyle(ChatFormatting.BOLD),
                btn -> handleSubmit()
        ).bounds(centerX - 155, startY + 160, 150, 20).build();
        this.addRenderableWidget(this.submitButton);

        // 6. Unlink button (if session exists)
        if (existingSession != null) {
            this.unlinkButton = Button.builder(
                    Component.translatable("aitu_auth.unlink_button").withStyle(ChatFormatting.RED),
                    btn -> handleUnlink()
            ).bounds(centerX + 5, startY + 160, 150, 20).build();
            this.addRenderableWidget(this.unlinkButton);
        }

        // 7. Back / Cancel button
        this.addRenderableWidget(
                Button.builder(CommonComponents.GUI_BACK, btn -> onClose())
                        .bounds(centerX - 100, this.height - 30, 200, 20)
                        .build()
        );

        if (existingSession != null) {
            this.statusMessage = Component.literal("Current Account: " + existingSession.getCachedNickname() + " (TG ID: " + existingSession.getTelegramId() + ")")
                    .withStyle(ChatFormatting.AQUA);
            this.isSuccessStatus = true;
        }
    }

    private void handleSubmit() {
        String rawToken = this.tokenEditBox.getValue().trim();
        if (rawToken.isEmpty()) {
            this.statusMessage = Component.translatable("aitu_auth.error.empty").withStyle(ChatFormatting.RED);
            this.isSuccessStatus = false;
            return;
        }

        String currentUsername = (this.minecraft != null && this.minecraft.getUser() != null)
                ? this.minecraft.getUser().getName()
                : "Player";

        long telegramId = 0;
        String rawTgId = this.telegramIdEditBox.getValue().trim();
        if (!rawTgId.isEmpty()) {
            try {
                telegramId = Long.parseLong(rawTgId);
            } catch (NumberFormatException ignored) {
            }
        }

        SessionManager sessionManager = SessionManager.getInstance();
        SessionData parsed = sessionManager.parseInputToken(rawToken, currentUsername, telegramId);

        if (parsed == null || !parsed.isValid()) {
            // Check if telegramId was missing but user didn't fill it
            if (parsed != null && parsed.getTelegramId() == 0 && telegramId == 0) {
                this.statusMessage = Component.literal("Please enter your Telegram ID or include it in the token format.")
                        .withStyle(ChatFormatting.RED);
            } else {
                this.statusMessage = Component.translatable("aitu_auth.error.invalid_format").withStyle(ChatFormatting.RED);
            }
            this.isSuccessStatus = false;
            return;
        }

        try {
            sessionManager.saveSession(parsed.getSessionToken(), parsed.getCachedNickname(), parsed.getTelegramId());
            LOGGER.info("[AITU Auth Screen] Account linked successfully for {}", parsed.getCachedNickname());
            this.statusMessage = Component.translatable("aitu_auth.success").withStyle(ChatFormatting.GREEN);
            this.isSuccessStatus = true;

            // Return to parent screen and refresh UI state
            if (this.minecraft != null) {
                this.minecraft.setScreen(this.parentScreen);
            }
        } catch (IOException e) {
            LOGGER.error("[AITU Auth Screen] Failed to save session: {}", e.getMessage(), e);
            this.statusMessage = Component.literal("Error saving session file: " + e.getMessage())
                    .withStyle(ChatFormatting.RED);
            this.isSuccessStatus = false;
        }
    }

    private void handleUnlink() {
        SessionManager.getInstance().clearSession();
        this.tokenEditBox.setValue("");
        this.telegramIdEditBox.setValue("");
        this.statusMessage = Component.literal("Account unlinked successfully.").withStyle(ChatFormatting.YELLOW);
        this.isSuccessStatus = true;
        if (this.unlinkButton != null) {
            this.unlinkButton.active = false;
        }
    }

    @Override
    public void onClose() {
        if (this.minecraft != null) {
            this.minecraft.setScreen(this.parentScreen);
        }
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        // Step 1: Call super.render FIRST
        super.render(guiGraphics, mouseX, mouseY, partialTick);

        int centerX = this.width / 2;

        // Step 2: ONLY AFTER super.render, render all text labels and headers:
        Component titleComp = this.title.copy().withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD);
        int titleWidth = this.font.width(titleComp);
        guiGraphics.drawString(this.font, titleComp, centerX - (titleWidth / 2), 12, 0xFFFFD700, true);

        // Instruction
        Component instComp = Component.translatable("aitu_auth.instruction.1").withStyle(ChatFormatting.GRAY);
        int instWidth = this.font.width(instComp);
        guiGraphics.drawString(this.font, instComp, centerX - (instWidth / 2), 32, 0xFFAAAAAA, true);

        // Input Labels (with drop-shadow and full 0xFF alpha)
        guiGraphics.drawString(
                this.font,
                Component.literal("Linking Token or JSON:").withStyle(ChatFormatting.YELLOW),
                centerX - 155,
                78,
                0xFFFFFFFF,
                true
        );

        guiGraphics.drawString(
                this.font,
                Component.literal("Telegram ID:").withStyle(ChatFormatting.YELLOW),
                centerX - 155,
                114,
                0xFFFFFFFF,
                true
        );

        // Status or Error Message
        if (this.statusMessage != null && !this.statusMessage.getString().isEmpty()) {
            int msgWidth = this.font.width(this.statusMessage);
            int color = this.isSuccessStatus ? 0xFF55FF55 : 0xFFFF5555;
            guiGraphics.drawString(
                    this.font,
                    this.statusMessage,
                    centerX - (msgWidth / 2),
                    190,
                    color,
                    true
            );
        }
    }
}
