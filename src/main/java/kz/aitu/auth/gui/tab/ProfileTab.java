package kz.aitu.auth.gui.tab;

import kz.aitu.auth.api.AituApiClient;
import kz.aitu.auth.config.SessionData;
import kz.aitu.auth.config.SessionManager;
import kz.aitu.auth.gui.AituHubScreen;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.network.chat.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

/**
 * Profile & Authentication Tab for AituHubScreen.
 * Implements a Two-Step Authentication Flow:
 * - Step 1: Telegram Handle input (@username) and "[ Request Code ]" button.
 * - Step 2: Dynamically replaces Step 1 with 6-digit PIN input, "[ Verify & Log In ]", and "← Back / Change Tag".
 * - When Linked: Shows User Card with Player Skin / Head, @TelegramHandle, Linked Status Badge (Green), and "Log Out / Unlink".
 */
public class ProfileTab implements AituTab {

    private static final Logger LOGGER = LoggerFactory.getLogger(ProfileTab.class);

    private static final int STEP_REQUEST_CODE = 1;
    private static final int STEP_VERIFY_CODE = 2;

    private AituHubScreen screen;
    private int contentX;
    private int contentY;
    private int contentWidth;
    private int contentHeight;

    // State
    private int currentStep = STEP_REQUEST_CODE;
    private String requestedTag = "";
    private boolean isRequestingCode = false;
    private boolean isVerifyingCode = false;

    // Step 1 Widgets
    private EditBox tagEditBox;
    private Button requestCodeButton;

    // Step 2 Widgets
    private EditBox pinEditBox;
    private Button verifyButton;
    private Button backButton;

    // Logged-in Widgets
    private Button logoutButton;

    // Dynamic Feedback
    private Component statusMessage = Component.empty();
    private boolean isSuccessStatus = false;

    @Override
    public Component getTitle() {
        return Component.literal("Profile");
    }

    @Override
    public String getIcon() {
        return "👤";
    }

    @Override
    public void init(AituHubScreen parent, int contentX, int contentY, int contentWidth, int contentHeight) {
        this.screen = parent;
        this.contentX = contentX;
        this.contentY = contentY;
        this.contentWidth = contentWidth;
        this.contentHeight = contentHeight;

        SessionManager manager = SessionManager.getInstance();
        boolean isLoggedIn = manager.hasValidSession();

        int centerX = contentX + contentWidth / 2;

        if (!isLoggedIn) {
            initUnlinkedView(centerX);
        } else {
            initLinkedView(centerX);
        }
    }

    private void initUnlinkedView(int centerX) {
        int boxWidth = 320;
        int boxHeight = 250;
        int boxY = Math.max(contentY + 4, contentY + (contentHeight - boxHeight) / 2);
        int fieldWidth = 280;
        int fieldX = centerX - fieldWidth / 2;

        Font font = Minecraft.getInstance().font;

        if (this.currentStep == STEP_REQUEST_CODE) {
            // --- Step 1: Request Code ---
            // Only one input field visible: Telegram Handle (@username)
            // Label sits at boxY + 68; EditBox sits at boxY + 84 (16px vertical distance >= 12px)
            this.tagEditBox = new EditBox(
                    font,
                    fieldX,
                    boxY + 84,
                    fieldWidth,
                    20,
                    Component.literal("Telegram Handle")
            );
            this.tagEditBox.setMaxLength(64);
            this.tagEditBox.setHint(Component.literal("@username").withStyle(ChatFormatting.DARK_GRAY));
            if (!this.requestedTag.isEmpty()) {
                this.tagEditBox.setValue(this.requestedTag);
            }
            screen.registerTabWidget(this.tagEditBox);

            // Directly below it: single action button "[ Request Code ]"
            this.requestCodeButton = Button.builder(
                    Component.literal("[ Request Code ]").withStyle(ChatFormatting.BOLD),
                    btn -> handleRequestCode()
            ).bounds(fieldX, boxY + 114, fieldWidth, 22)
            .build();
            screen.registerTabWidget(this.requestCodeButton);

        } else {
            // --- Step 2: Verify Code ---
            // Dynamic replacement: 6-digit PIN input, "[ Verify & Log In ]", "← Back / Change Tag"
            // Label sits at boxY + 70; EditBox sits at boxY + 88 (18px vertical distance >= 12px)
            this.pinEditBox = new EditBox(
                    font,
                    fieldX,
                    boxY + 88,
                    fieldWidth,
                    20,
                    Component.literal("6-Digit Code")
            );
            this.pinEditBox.setMaxLength(6);
            this.pinEditBox.setFilter(text -> text.matches("\\d*"));
            this.pinEditBox.setHint(Component.literal("Enter 6-digit code").withStyle(ChatFormatting.DARK_GRAY));
            screen.registerTabWidget(this.pinEditBox);

            // Button: "[ Verify & Log In ]"
            this.verifyButton = Button.builder(
                    Component.literal("[ Verify & Log In ]").withStyle(ChatFormatting.BOLD),
                    btn -> handleVerifyCode()
            ).bounds(fieldX, boxY + 118, fieldWidth, 22)
            .build();
            screen.registerTabWidget(this.verifyButton);

            // Small secondary button or link: "← Back / Change Tag"
            this.backButton = Button.builder(
                    Component.literal("← Back / Change Tag").withStyle(ChatFormatting.GRAY),
                    btn -> {
                        this.currentStep = STEP_REQUEST_CODE;
                        this.statusMessage = Component.empty();
                        this.screen.refreshActiveTab();
                    }
            ).bounds(centerX - 75, boxY + 148, 150, 18)
            .build();
            screen.registerTabWidget(this.backButton);
        }
    }

    private void initLinkedView(int centerX) {
        int boxWidth = 320;
        int boxHeight = 250;
        int boxY = Math.max(contentY + 4, contentY + (contentHeight - boxHeight) / 2);

        // "Log Out / Unlink" button
        this.logoutButton = Button.builder(
                Component.literal("✖ Log Out / Unlink").withStyle(ChatFormatting.RED, ChatFormatting.BOLD),
                btn -> handleLogout()
        ).bounds(centerX - 80, boxY + 206, 160, 20)
        .tooltip(Tooltip.create(Component.literal("Removes local session data and unlinks this client.")))
        .build();
        screen.registerTabWidget(this.logoutButton);
    }

    private void handleRequestCode() {
        if (this.isRequestingCode) return;

        String rawTag = this.tagEditBox.getValue().trim();
        if (rawTag.isEmpty()) {
            this.statusMessage = Component.literal("Please enter your Telegram handle (e.g. @username)").withStyle(ChatFormatting.RED);
            this.isSuccessStatus = false;
            return;
        }

        final String formattedTag = rawTag.startsWith("@") ? rawTag : "@" + rawTag;
        final String currentNickname = Minecraft.getInstance().getUser().getName();

        this.isRequestingCode = true;
        this.requestCodeButton.active = false;
        this.requestCodeButton.setMessage(Component.literal("Sending code to your Telegram...").withStyle(ChatFormatting.YELLOW));
        this.statusMessage = Component.literal("Sending code to your Telegram...").withStyle(ChatFormatting.GRAY);

        AituApiClient.getInstance().requestCode(formattedTag, currentNickname).thenAccept(result -> {
            Minecraft.getInstance().execute(() -> {
                this.isRequestingCode = false;
                if (result.success()) {
                    this.requestedTag = formattedTag;
                    this.currentStep = STEP_VERIFY_CODE;
                    this.statusMessage = Component.literal("✔ Code sent! Check your Telegram (" + formattedTag + ")").withStyle(ChatFormatting.GREEN);
                    this.isSuccessStatus = true;
                    LOGGER.info("[AITU Auth] Code requested successfully for {}", formattedTag);
                    this.screen.refreshActiveTab();
                } else {
                    this.requestCodeButton.active = true;
                    this.requestCodeButton.setMessage(Component.literal("[ Request Code ]").withStyle(ChatFormatting.BOLD));
                    this.statusMessage = Component.literal("✖ " + result.error()).withStyle(ChatFormatting.RED);
                    this.isSuccessStatus = false;
                    LOGGER.warn("[AITU Auth] Request code failed: {}", result.error());
                }
            });
        }).exceptionally(ex -> {
            Minecraft.getInstance().execute(() -> {
                this.isRequestingCode = false;
                this.requestCodeButton.active = true;
                this.requestCodeButton.setMessage(Component.literal("[ Request Code ]").withStyle(ChatFormatting.BOLD));
                this.statusMessage = Component.literal("✖ Network error: " + ex.getMessage()).withStyle(ChatFormatting.RED);
                this.isSuccessStatus = false;
            });
            return null;
        });
    }

    private void handleVerifyCode() {
        if (this.isVerifyingCode) return;

        String pin = this.pinEditBox.getValue().trim();
        if (pin.length() != 6) {
            this.statusMessage = Component.literal("Code must be exactly 6 digits!").withStyle(ChatFormatting.RED);
            this.isSuccessStatus = false;
            return;
        }

        final String currentNickname = Minecraft.getInstance().getUser().getName();

        this.isVerifyingCode = true;
        this.verifyButton.active = false;
        this.verifyButton.setMessage(Component.literal("Verifying...").withStyle(ChatFormatting.YELLOW));
        this.statusMessage = Component.literal("Connecting to auth server...").withStyle(ChatFormatting.GRAY);

        AituApiClient.getInstance().verifyPin(this.requestedTag, pin, currentNickname).thenAccept(result -> {
            Minecraft.getInstance().execute(() -> {
                this.isVerifyingCode = false;
                this.verifyButton.active = true;
                this.verifyButton.setMessage(Component.literal("[ Verify & Log In ]").withStyle(ChatFormatting.BOLD));

                if (result.success() && result.sessionToken() != null) {
                    try {
                        SessionManager.getInstance().saveSession(
                                result.sessionToken(),
                                currentNickname,
                                result.telegramId(),
                                result.telegramTag() != null ? result.telegramTag() : this.requestedTag
                        );
                        this.statusMessage = Component.literal("✔ Account successfully linked!").withStyle(ChatFormatting.GREEN);
                        this.isSuccessStatus = true;
                        LOGGER.info("[AITU Hub] Account linked for {} with tag {}", currentNickname, this.requestedTag);
                        this.currentStep = STEP_REQUEST_CODE;
                        this.requestedTag = "";
                        screen.refreshActiveTab();
                    } catch (IOException e) {
                        LOGGER.error("[AITU Hub] Failed to save session: {}", e.getMessage(), e);
                        this.statusMessage = Component.literal("Failed to save session: " + e.getMessage()).withStyle(ChatFormatting.RED);
                        this.isSuccessStatus = false;
                    }
                } else {
                    String error = result.error() != null ? result.error() : "Verification failed";
                    this.statusMessage = Component.literal("✖ " + error).withStyle(ChatFormatting.RED);
                    this.isSuccessStatus = false;
                    LOGGER.warn("[AITU Hub] Verification failed: {}", error);
                }
            });
        }).exceptionally(ex -> {
            Minecraft.getInstance().execute(() -> {
                this.isVerifyingCode = false;
                this.verifyButton.active = true;
                this.verifyButton.setMessage(Component.literal("[ Verify & Log In ]").withStyle(ChatFormatting.BOLD));
                this.statusMessage = Component.literal("✖ Network error: " + ex.getMessage()).withStyle(ChatFormatting.RED);
                this.isSuccessStatus = false;
            });
            return null;
        });
    }

    private void handleLogout() {
        SessionManager.getInstance().clearSession();
        this.currentStep = STEP_REQUEST_CODE;
        this.requestedTag = "";
        this.statusMessage = Component.literal("Successfully logged out.").withStyle(ChatFormatting.YELLOW);
        this.isSuccessStatus = true;
        LOGGER.info("[AITU Hub] Session cleared via Log Out button.");
        screen.refreshActiveTab();
    }

    @Override
    public void renderBackground(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        int centerX = contentX + contentWidth / 2;
        int boxWidth = 320;
        int boxHeight = 250;
        int boxY = Math.max(contentY + 4, contentY + (contentHeight - boxHeight) / 2);
        int boxX = centerX - boxWidth / 2;

        // Opaque boxed dialog container (width: 320, height: 250) with crisp outline
        guiGraphics.fill(boxX, boxY, boxX + boxWidth, boxY + boxHeight, 0xFF121622);
        guiGraphics.renderOutline(boxX, boxY, boxWidth, boxHeight, 0xFF2D3D58);
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        Font font = Minecraft.getInstance().font;
        int centerX = contentX + contentWidth / 2;

        SessionManager manager = SessionManager.getInstance();
        boolean isLoggedIn = manager.hasValidSession();

        if (!isLoggedIn) {
            renderUnlinked(guiGraphics, font, centerX);
        } else {
            renderLinked(guiGraphics, font, centerX, manager.getSession().orElse(null));
        }
    }

    private void renderUnlinked(GuiGraphics guiGraphics, Font font, int centerX) {
        int boxWidth = 320;
        int boxHeight = 250;
        int boxY = Math.max(contentY + 4, contentY + (contentHeight - boxHeight) / 2);
        int fieldWidth = 280;
        int fieldX = centerX - fieldWidth / 2;

        // Section Title (Crisp High-Contrast Gold with Drop Shadow and full 0xFF alpha)
        Component titleComp = Component.literal("AITU Account Sign In").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD);
        int titleWidth = font.width(titleComp);
        guiGraphics.drawString(font, titleComp, centerX - (titleWidth / 2), boxY + 16, 0xFFFFD700, true);

        if (this.currentStep == STEP_REQUEST_CODE) {
            // Step 1 Subtitle
            Component subComp = Component.literal("Enter your Telegram handle to receive a code").withStyle(ChatFormatting.GRAY);
            int subWidth = font.width(subComp);
            guiGraphics.drawString(font, subComp, centerX - (subWidth / 2), boxY + 32, 0xFFAAAAAA, true);

            // Step 1 Label: "Telegram Handle (@username):" (16px vertical distance above edit box at boxY + 84)
            guiGraphics.drawString(
                    font,
                    Component.literal("Telegram Handle (@username):").withStyle(ChatFormatting.WHITE, ChatFormatting.BOLD),
                    fieldX,
                    boxY + 68,
                    0xFFFFFFFF,
                    true
            );
        } else {
            // Step 2 Subtitle
            Component subComp = Component.literal("Step 2 of 2: Verification").withStyle(ChatFormatting.AQUA);
            int subWidth = font.width(subComp);
            guiGraphics.drawString(font, subComp, centerX - (subWidth / 2), boxY + 32, 0xFF55FFFF, true);

            // Step 2 Label: "Enter 6-Digit Code sent by bot to @" + username
            String promptText = "Enter 6-Digit Code sent by bot to " + this.requestedTag + ":";
            guiGraphics.drawString(
                    font,
                    Component.literal(promptText).withStyle(ChatFormatting.WHITE, ChatFormatting.BOLD),
                    fieldX,
                    boxY + 70,
                    0xFFFFFFFF,
                    true
            );
        }

        // Dynamic Feedback Status (Non-intrusive inline label with full 0xFF alpha and drop-shadow)
        if (!this.statusMessage.getString().isEmpty()) {
            int statusWidth = font.width(this.statusMessage);
            int textColor = this.isSuccessStatus ? 0xFF55FF55 : 0xFFFF5555;
            guiGraphics.drawString(
                    font,
                    this.statusMessage,
                    centerX - (statusWidth / 2),
                    boxY + 200,
                    textColor,
                    true
            );
        }
    }

    private void renderLinked(GuiGraphics guiGraphics, Font font, int centerX, SessionData session) {
        if (session == null) return;

        int boxWidth = 320;
        int boxHeight = 250;
        int boxY = Math.max(contentY + 4, contentY + (contentHeight - boxHeight) / 2);
        int boxX = centerX - boxWidth / 2;

        // Player Head Avatar (36x36)
        int avatarX = boxX + 16;
        int avatarY = boxY + 16;
        int avatarSize = 36;
        PlayerSkin skin = Minecraft.getInstance().getSkinManager().getInsecureSkin(
                Minecraft.getInstance().getGameProfile()
        );
        PlayerFaceRenderer.draw(guiGraphics, skin.texture(), avatarX, avatarY, avatarSize);
        guiGraphics.renderOutline(avatarX - 1, avatarY - 1, avatarSize + 2, avatarSize + 2, 0xFF00E676);

        // Player Info next to Avatar (Crisp White with Drop Shadow and full 0xFF alpha)
        int textX = avatarX + avatarSize + 12;
        guiGraphics.drawString(
                font,
                Component.literal(session.getCachedNickname()).withStyle(ChatFormatting.WHITE, ChatFormatting.BOLD),
                textX,
                boxY + 16,
                0xFFFFFFFF,
                true
        );

        guiGraphics.drawString(
                font,
                Component.literal(session.getTelegramTag()).withStyle(ChatFormatting.AQUA),
                textX,
                boxY + 28,
                0xFF55FFFF,
                true
        );

        guiGraphics.drawString(
                font,
                Component.literal("ID: " + session.getTelegramId()).withStyle(ChatFormatting.GRAY),
                textX,
                boxY + 40,
                0xFFAAAAAA,
                true
        );

        // Status Badge (Top-Right of Card) - Opaque badge with Green text and border
        int badgeWidth = 66;
        int badgeHeight = 18;
        int badgeX = boxX + boxWidth - badgeWidth - 14;
        int badgeY = boxY + 16;
        guiGraphics.fill(badgeX, badgeY, badgeX + badgeWidth, badgeY + badgeHeight, 0xFF0E2A18);
        guiGraphics.renderOutline(badgeX, badgeY, badgeWidth, badgeHeight, 0xFF00AA44);
        Component badgeText = Component.literal("● Linked").withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD);
        int badgeTextWidth = font.width(badgeText);
        guiGraphics.drawString(
                font,
                badgeText,
                badgeX + (badgeWidth - badgeTextWidth) / 2,
                badgeY + 5,
                0xFF00FF66,
                true
        );

        // Divider
        guiGraphics.fill(boxX + 14, boxY + 62, boxX + boxWidth - 14, boxY + 63, 0xFF243248);

        // Security / Status Details (Crisp High-Contrast Text with full 0xFF alpha and drop-shadow)
        guiGraphics.drawString(
                font,
                Component.literal("Authentication:").withStyle(ChatFormatting.WHITE),
                boxX + 16,
                boxY + 74,
                0xFFFFFFFF,
                true
        );
        guiGraphics.drawString(
                font,
                Component.literal("Active Session Token").withStyle(ChatFormatting.GREEN),
                boxX + 116,
                boxY + 74,
                0xFF55FF55,
                true
        );

        guiGraphics.drawString(
                font,
                Component.literal("Pinned Server:").withStyle(ChatFormatting.WHITE),
                boxX + 16,
                boxY + 94,
                0xFFFFFFFF,
                true
        );
        guiGraphics.drawString(
                font,
                Component.literal("AITU Official SMP (Index 0)").withStyle(ChatFormatting.AQUA),
                boxX + 116,
                boxY + 94,
                0xFF55FFFF,
                true
        );

        guiGraphics.drawString(
                font,
                Component.literal("Config File:").withStyle(ChatFormatting.WHITE),
                boxX + 16,
                boxY + 114,
                0xFFFFFFFF,
                true
        );
        guiGraphics.drawString(
                font,
                Component.literal("config/aitu_session.json").withStyle(ChatFormatting.YELLOW),
                boxX + 116,
                boxY + 114,
                0xFFFFFF55,
                true
        );

        guiGraphics.drawString(
                font,
                Component.literal("Handshake:").withStyle(ChatFormatting.WHITE),
                boxX + 16,
                boxY + 134,
                0xFFFFFFFF,
                true
        );
        guiGraphics.drawString(
                font,
                Component.literal("Auto-sync on server join").withStyle(ChatFormatting.GREEN),
                boxX + 116,
                boxY + 134,
                0xFF55FF55,
                true
        );

        // Feedback / Action notice
        if (!this.statusMessage.getString().isEmpty()) {
            int statusWidth = font.width(this.statusMessage);
            guiGraphics.drawString(
                    font,
                    this.statusMessage,
                    centerX - (statusWidth / 2),
                    boxY + 180,
                    0xFFFFFF55,
                    true
            );
        }
    }
}
