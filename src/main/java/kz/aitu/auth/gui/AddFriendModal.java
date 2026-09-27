package kz.aitu.auth.gui;

import kz.aitu.auth.api.AituApiClient;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Modal dialog for sending a friend request.
 * Supports multiple identifier types: Telegram handle (@user), Phone number, Email, or raw UUID/code.
 */
public class AddFriendModal extends Screen {

    private final Screen parentScreen;
    private final Runnable onComplete;

    private EditBox queryEditBox;
    private Button sendButton;
    private Button closeButton;

    private Component statusFeedback = Component.empty();
    private boolean isSuccess = false;
    private boolean isSending = false;

    public AddFriendModal(Screen parentScreen, Runnable onComplete) {
        super(Component.literal("Add Friend"));
        this.parentScreen = parentScreen;
        this.onComplete = onComplete;
    }

    @Override
    protected void init() {
        super.init();

        int centerX = this.width / 2;
        int centerY = this.height / 2;
        int modalWidth = 290;
        int modalHeight = 160;
        int modalX = centerX - modalWidth / 2;
        int modalY = centerY - modalHeight / 2;

        int fieldWidth = 250;
        int fieldX = centerX - fieldWidth / 2;

        // Input field supporting multiple identifier types: Telegram handle (@user), Phone number, Email, or UUID
        this.queryEditBox = new EditBox(
                this.font,
                fieldX,
                modalY + 60,
                fieldWidth,
                20,
                Component.literal("Friend Identifier")
        );
        this.queryEditBox.setMaxLength(128);
        this.queryEditBox.setHint(Component.literal("@username, +7700..., email, UUID").withStyle(ChatFormatting.DARK_GRAY));
        this.addRenderableWidget(this.queryEditBox);

        // Send Button
        this.sendButton = Button.builder(
                Component.literal("[ Send Request ]").withStyle(ChatFormatting.BOLD),
                btn -> handleSend()
        ).bounds(fieldX, modalY + 92, 150, 20)
        .build();
        this.addRenderableWidget(this.sendButton);

        // Close / Cancel Button
        this.closeButton = Button.builder(
                Component.literal("✖ Close"),
                btn -> onClose()
        ).bounds(fieldX + 160, modalY + 92, 90, 20)
        .build();
        this.addRenderableWidget(this.closeButton);
    }

    private void handleSend() {
        if (this.isSending) return;

        String query = this.queryEditBox.getValue().trim();
        if (query.isEmpty()) {
            this.statusFeedback = Component.literal("Please enter a username, phone, or email.").withStyle(ChatFormatting.RED);
            this.isSuccess = false;
            return;
        }

        this.isSending = true;
        this.sendButton.active = false;
        this.sendButton.setMessage(Component.literal("Sending...").withStyle(ChatFormatting.YELLOW));
        this.statusFeedback = Component.literal("Sending request...").withStyle(ChatFormatting.GRAY);

        AituApiClient.getInstance().sendFriendRequest(query).thenAccept(result -> {
            if (this.minecraft != null) {
                this.minecraft.execute(() -> {
                    this.isSending = false;
                    this.sendButton.active = true;
                    this.sendButton.setMessage(Component.literal("[ Send Request ]").withStyle(ChatFormatting.BOLD));

                    if (result.success()) {
                        this.statusFeedback = Component.literal("✔ " + result.message()).withStyle(ChatFormatting.GREEN);
                        this.isSuccess = true;
                        this.queryEditBox.setValue("");
                        if (this.onComplete != null) {
                            this.onComplete.run();
                        }
                    } else {
                        this.statusFeedback = Component.literal("✖ " + result.error()).withStyle(ChatFormatting.RED);
                        this.isSuccess = false;
                    }
                });
            }
        }).exceptionally(ex -> {
            if (this.minecraft != null) {
                this.minecraft.execute(() -> {
                    this.isSending = false;
                    this.sendButton.active = true;
                    this.sendButton.setMessage(Component.literal("[ Send Request ]").withStyle(ChatFormatting.BOLD));
                    this.statusFeedback = Component.literal("✖ " + ex.getMessage()).withStyle(ChatFormatting.RED);
                    this.isSuccess = false;
                });
            }
            return null;
        });
    }

    @Override
    public void renderBackground(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(guiGraphics, mouseX, mouseY, partialTick);

        // Dimmed modal backdrop
        guiGraphics.fill(0, 0, this.width, this.height, 0x85000000);

        int centerX = this.width / 2;
        int centerY = this.height / 2;
        int modalWidth = 290;
        int modalHeight = 160;
        int modalX = centerX - modalWidth / 2;
        int modalY = centerY - modalHeight / 2;

        // Opaque dialog container box
        guiGraphics.fill(modalX, modalY, modalX + modalWidth, modalY + modalHeight, 0xFF141926);
        guiGraphics.renderOutline(modalX, modalY, modalWidth, modalHeight, 0xFF3A4D68);
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        // Step 1: Call super.render FIRST
        super.render(guiGraphics, mouseX, mouseY, partialTick);

        Font font = this.font;
        int centerX = this.width / 2;
        int centerY = this.height / 2;
        int modalHeight = 160;
        int modalY = centerY - modalHeight / 2;

        // Step 2: ONLY AFTER super.render, render text labels and headers with dropShadow
        Component titleComp = Component.literal("Add Friend").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD);
        int titleWidth = font.width(titleComp);
        guiGraphics.drawString(font, titleComp, centerX - (titleWidth / 2), modalY + 16, 0xFFFFD700, true);

        Component descComp = Component.literal("Enter Telegram (@user), phone, email, or UUID:").withStyle(ChatFormatting.GRAY);
        int descWidth = font.width(descComp);
        guiGraphics.drawString(font, descComp, centerX - (descWidth / 2), modalY + 36, 0xFFAAAAAA, true);

        // Feedback Status
        if (!this.statusFeedback.getString().isEmpty()) {
            int fbWidth = font.width(this.statusFeedback);
            int color = this.isSuccess ? 0xFF55FF55 : 0xFFFF5555;
            guiGraphics.drawString(font, this.statusFeedback, centerX - (fbWidth / 2), modalY + 128, color, true);
        }
    }

    @Override
    public void onClose() {
        if (this.minecraft != null) {
            this.minecraft.setScreen(this.parentScreen);
        }
    }
}
