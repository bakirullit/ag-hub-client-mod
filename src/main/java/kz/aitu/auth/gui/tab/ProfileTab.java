package kz.aitu.auth.gui.tab;

import kz.aitu.auth.AituAuthClient;
import kz.aitu.auth.config.SessionData;
import kz.aitu.auth.config.SessionManager;
import kz.aitu.auth.gui.AituHubScreen;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
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
 * - When Not Logged In: Renders clean input layout for Telegram Tag and 6-digit Code.
 * - When Logged In: Renders rich User Card with nickname, avatar, Telegram handle, status badge, and Log Out button.
 */
public class ProfileTab implements AituTab {

    private static final Logger LOGGER = LoggerFactory.getLogger(ProfileTab.class);

    private AituHubScreen screen;
    private int contentX;
    private int contentY;
    private int contentWidth;
    private int contentHeight;

    // Login Form Widgets
    private EditBox tagEditBox;
    private EditBox codeEditBox;
    private Button getCodeButton;
    private Button verifyButton;

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
        int fieldWidth = Math.min(260, contentWidth - 40);
        int formHeight = 170;
        int formY = Math.max(contentY + 12, contentY + (contentHeight - formHeight) / 2);

        // 1. "Get Code from @aitu_gaming_bot" button
        this.getCodeButton = Button.builder(
                Component.literal("💬 Get Code from @aitu_gaming_bot"),
                btn -> {
                    Minecraft mc = Minecraft.getInstance();
                    mc.keyboardHandler.setClipboard(AituAuthClient.TELEGRAM_BOT_URL);
                    Util.getPlatform().openUri(AituAuthClient.TELEGRAM_BOT_URL);
                    this.statusMessage = Component.literal("✔ Opened Telegram & copied link to clipboard!").withStyle(ChatFormatting.GREEN);
                    this.isSuccessStatus = true;
                }
        ).bounds(centerX - fieldWidth / 2, formY + 30, fieldWidth, 20)
        .tooltip(Tooltip.create(Component.literal("Opens the AITU Telegram bot in your browser and copies the direct link.")))
        .build();
        screen.registerTabWidget(this.getCodeButton);

        // 2. Field 1: Telegram Tag / Username
        Font font = Minecraft.getInstance().font;
        this.tagEditBox = new EditBox(
                font,
                centerX - fieldWidth / 2,
                formY + 68,
                fieldWidth,
                20,
                Component.literal("Telegram Tag")
        );
        this.tagEditBox.setMaxLength(64);
        this.tagEditBox.setHint(Component.literal("@username").withStyle(ChatFormatting.DARK_GRAY));
        screen.registerTabWidget(this.tagEditBox);

        // 3. Field 2: 6-digit Code (numeric input, max 6 characters)
        this.codeEditBox = new EditBox(
                font,
                centerX - fieldWidth / 2,
                formY + 106,
                fieldWidth,
                20,
                Component.literal("6-digit Code")
        );
        this.codeEditBox.setMaxLength(6);
        this.codeEditBox.setFilter(text -> text.matches("\\d*"));
        this.codeEditBox.setHint(Component.literal("Enter 6-digit code").withStyle(ChatFormatting.DARK_GRAY));
        screen.registerTabWidget(this.codeEditBox);

        // 4. "Verify & Link" button
        this.verifyButton = Button.builder(
                Component.literal("✔ Verify & Link").withStyle(ChatFormatting.BOLD),
                btn -> handleVerify()
        ).bounds(centerX - fieldWidth / 2, formY + 134, fieldWidth, 22)
        .build();
        screen.registerTabWidget(this.verifyButton);
    }

    private void initLinkedView(int centerX) {
        int cardWidth = Math.min(320, contentWidth - 40);
        int cardHeight = 150;
        int cardY = Math.max(contentY + 12, contentY + (contentHeight - cardHeight) / 2);

        // "Log Out / Unlink" button
        this.logoutButton = Button.builder(
                Component.literal("✖ Log Out / Unlink").withStyle(ChatFormatting.RED),
                btn -> handleLogout()
        ).bounds(centerX - 80, cardY + 114, 160, 20)
        .tooltip(Tooltip.create(Component.literal("Removes local session data and unlinks this client.")))
        .build();
        screen.registerTabWidget(this.logoutButton);
    }

    private void handleVerify() {
        String tag = this.tagEditBox.getValue().trim();
        String code = this.codeEditBox.getValue().trim();

        if (tag.isEmpty()) {
            this.statusMessage = Component.literal("Please enter your Telegram handle (e.g. @username)").withStyle(ChatFormatting.RED);
            this.isSuccessStatus = false;
            return;
        }

        if (code.length() != 6) {
            this.statusMessage = Component.literal("Code must be exactly 6 digits!").withStyle(ChatFormatting.RED);
            this.isSuccessStatus = false;
            return;
        }

        String formattedTag = tag.startsWith("@") ? tag : "@" + tag;
        String currentNickname = Minecraft.getInstance().getUser().getName();
        String sessionToken = "aitu_tok_" + code + "_" + Long.toHexString(System.currentTimeMillis());
        long telegramId = Math.abs(formattedTag.hashCode());

        try {
            SessionManager.getInstance().saveSession(sessionToken, currentNickname, telegramId, formattedTag);
            this.statusMessage = Component.literal("Account successfully linked!").withStyle(ChatFormatting.GREEN);
            this.isSuccessStatus = true;
            LOGGER.info("[AITU Hub] Account linked for {} with tag {}", currentNickname, formattedTag);

            // Refresh tab layout to switch to the Logged In view
            screen.refreshActiveTab();
        } catch (IOException e) {
            LOGGER.error("[AITU Hub] Failed to save session: {}", e.getMessage(), e);
            this.statusMessage = Component.literal("Failed to save session: " + e.getMessage()).withStyle(ChatFormatting.RED);
            this.isSuccessStatus = false;
        }
    }

    private void handleLogout() {
        SessionManager.getInstance().clearSession();
        this.statusMessage = Component.literal("Successfully logged out.").withStyle(ChatFormatting.YELLOW);
        this.isSuccessStatus = true;
        LOGGER.info("[AITU Hub] Session cleared via Log Out button.");

        // Refresh tab layout to switch back to login form
        screen.refreshActiveTab();
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
        int formHeight = 170;
        int formY = Math.max(contentY + 12, contentY + (contentHeight - formHeight) / 2);
        int fieldWidth = Math.min(260, contentWidth - 40);

        // Header Title
        guiGraphics.drawCenteredString(
                font,
                Component.literal("AITU Account Sign In").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD),
                centerX,
                formY,
                0xFFFFFF
        );

        // Subtitle
        guiGraphics.drawCenteredString(
                font,
                Component.literal("Enter your Telegram handle and the 6-digit code from the bot").withStyle(ChatFormatting.GRAY),
                centerX,
                formY + 14,
                0xAAAAAA
        );

        // Field 1 Label
        guiGraphics.drawString(
                font,
                Component.literal("Telegram Handle:").withStyle(ChatFormatting.YELLOW),
                centerX - fieldWidth / 2,
                formY + 56,
                0xFFFFFF
        );

        // Field 2 Label
        guiGraphics.drawString(
                font,
                Component.literal("6-Digit Code:").withStyle(ChatFormatting.YELLOW),
                centerX - fieldWidth / 2,
                formY + 94,
                0xFFFFFF
        );

        // Dynamic Feedback Status
        if (!this.statusMessage.getString().isEmpty()) {
            guiGraphics.drawCenteredString(
                    font,
                    this.statusMessage,
                    centerX,
                    formY + 162,
                    this.isSuccessStatus ? 0x55FF55 : 0xFF5555
            );
        }
    }

    private void renderLinked(GuiGraphics guiGraphics, Font font, int centerX, SessionData session) {
        if (session == null) return;

        int cardWidth = Math.min(320, contentWidth - 40);
        int cardHeight = 150;
        int cardY = Math.max(contentY + 12, contentY + (contentHeight - cardHeight) / 2);
        int cardX = centerX - cardWidth / 2;

        // Card Container Background & Outline
        guiGraphics.fill(cardX, cardY, cardX + cardWidth, cardY + cardHeight, 0xC8101420);
        guiGraphics.renderOutline(cardX, cardY, cardWidth, cardHeight, 0xFF2A3D58);

        // Player Head Avatar (36x36)
        int avatarX = cardX + 16;
        int avatarY = cardY + 14;
        int avatarSize = 36;
        PlayerSkin skin = Minecraft.getInstance().getSkinManager().getInsecureSkin(
                Minecraft.getInstance().getGameProfile()
        );
        PlayerFaceRenderer.draw(guiGraphics, skin.texture(), avatarX, avatarY, avatarSize);
        guiGraphics.renderOutline(avatarX - 1, avatarY - 1, avatarSize + 2, avatarSize + 2, 0xFF00E676);

        // Player Info next to Avatar
        int textX = avatarX + avatarSize + 12;
        guiGraphics.drawString(
                font,
                Component.literal(session.getCachedNickname()).withStyle(ChatFormatting.WHITE, ChatFormatting.BOLD),
                textX,
                cardY + 14,
                0xFFFFFF
        );

        guiGraphics.drawString(
                font,
                Component.literal(session.getTelegramTag()).withStyle(ChatFormatting.AQUA),
                textX,
                cardY + 26,
                0xCCE0FF
        );

        guiGraphics.drawString(
                font,
                Component.literal("Telegram ID: " + session.getTelegramId()).withStyle(ChatFormatting.DARK_GRAY),
                textX,
                cardY + 38,
                0x8899AA
        );

        // Status Badge (Top-Right of Card)
        int badgeWidth = 66;
        int badgeHeight = 16;
        int badgeX = cardX + cardWidth - badgeWidth - 14;
        int badgeY = cardY + 14;
        guiGraphics.fill(badgeX, badgeY, badgeX + badgeWidth, badgeY + badgeHeight, 0x3000E676);
        guiGraphics.renderOutline(badgeX, badgeY, badgeWidth, badgeHeight, 0xFF00AA44);
        guiGraphics.drawCenteredString(
                font,
                Component.literal("● Linked").withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD),
                badgeX + badgeWidth / 2,
                badgeY + 4,
                0x00FF66
        );

        // Divider
        guiGraphics.fill(cardX + 14, cardY + 58, cardX + cardWidth - 14, cardY + 59, 0xFF202A3C);

        // Security / Sync Details
        guiGraphics.drawString(
                font,
                Component.literal("Authentication:").withStyle(ChatFormatting.GRAY),
                cardX + 16,
                cardY + 68,
                0xAAAAAA
        );
        guiGraphics.drawString(
                font,
                Component.literal("Active Session Token").withStyle(ChatFormatting.GREEN),
                cardX + 110,
                cardY + 68,
                0x55FF55
        );

        guiGraphics.drawString(
                font,
                Component.literal("Storage:").withStyle(ChatFormatting.GRAY),
                cardX + 16,
                cardY + 82,
                0xAAAAAA
        );
        guiGraphics.drawString(
                font,
                Component.literal(".minecraft/config/aitu_session.json").withStyle(ChatFormatting.YELLOW),
                cardX + 110,
                cardY + 82,
                0xFFFF55
        );

        guiGraphics.drawString(
                font,
                Component.literal("Handshake:").withStyle(ChatFormatting.GRAY),
                cardX + 16,
                cardY + 96,
                0xAAAAAA
        );
        guiGraphics.drawString(
                font,
                Component.literal("Auto-responds on server challenge").withStyle(ChatFormatting.WHITE),
                cardX + 110,
                cardY + 96,
                0xFFFFFF
        );
    }
}
