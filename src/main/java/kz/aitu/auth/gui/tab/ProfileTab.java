package kz.aitu.auth.gui.tab;

import kz.aitu.auth.AituAuthClient;
import kz.aitu.auth.api.AituApiClient;
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
 * - When Unlinked: Telegram handle field (@username), 6-digit PIN field, "Verify & Link" button, and "Get Code via Bot" button.
 * - When Linked: User card displaying Player Skin / Head, @TelegramHandle, Linked Status Badge (Green), and "Log Out / Unlink" button.
 * Uses an opaque boxed dialog container (width: 320, height: 250) to prevent blur shader degradation,
 * with >=12px label vertical spacing and full 0xFF alpha drop-shadow strings.
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
    private boolean isVerifying = false;

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

        // 1. "Get Code via Bot" button
        this.getCodeButton = Button.builder(
                Component.literal("💬 Get Code via Bot"),
                btn -> {
                    Minecraft mc = Minecraft.getInstance();
                    mc.keyboardHandler.setClipboard(AituAuthClient.TELEGRAM_BOT_URL);
                    Util.getPlatform().openUri(AituAuthClient.TELEGRAM_BOT_URL);
                    this.statusMessage = Component.literal("✔ Opened Bot & copied link to clipboard!").withStyle(ChatFormatting.GREEN);
                    this.isSuccessStatus = true;
                }
        ).bounds(fieldX, boxY + 44, fieldWidth, 20)
        .tooltip(Tooltip.create(Component.literal("Opens @aitu_gaming_bot in your browser and copies link.")))
        .build();
        screen.registerTabWidget(this.getCodeButton);

        // 2. Field 1: Telegram Handle (@username)
        // EditBox sits at Y = boxY + 98; Label sits at Y = boxY + 82 (16px vertical distance >= 12px)
        this.tagEditBox = new EditBox(
                font,
                fieldX,
                boxY + 98,
                fieldWidth,
                20,
                Component.literal("Telegram Handle")
        );
        this.tagEditBox.setMaxLength(64);
        this.tagEditBox.setHint(Component.literal("@username").withStyle(ChatFormatting.DARK_GRAY));
        screen.registerTabWidget(this.tagEditBox);

        // 3. Field 2: 6-digit PIN
        // EditBox sits at Y = boxY + 154; Label sits at Y = boxY + 138 (16px vertical distance >= 12px)
        this.codeEditBox = new EditBox(
                font,
                fieldX,
                boxY + 154,
                fieldWidth,
                20,
                Component.literal("6-digit PIN")
        );
        this.codeEditBox.setMaxLength(6);
        this.codeEditBox.setFilter(text -> text.matches("\\d*"));
        this.codeEditBox.setHint(Component.literal("Enter 6-digit PIN").withStyle(ChatFormatting.DARK_GRAY));
        screen.registerTabWidget(this.codeEditBox);

        // 4. "Verify & Link" button
        this.verifyButton = Button.builder(
                Component.literal("✔ Verify & Link").withStyle(ChatFormatting.BOLD),
                btn -> handleVerify()
        ).bounds(fieldX, boxY + 186, fieldWidth, 22)
        .build();
        screen.registerTabWidget(this.verifyButton);
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

    private void handleVerify() {
        if (this.isVerifying) return;

        String tag = this.tagEditBox.getValue().trim();
        String pin = this.codeEditBox.getValue().trim();

        if (tag.isEmpty()) {
            this.statusMessage = Component.literal("Please enter your Telegram handle (e.g. @username)").withStyle(ChatFormatting.RED);
            this.isSuccessStatus = false;
            return;
        }

        if (pin.length() != 6) {
            this.statusMessage = Component.literal("PIN must be exactly 6 digits!").withStyle(ChatFormatting.RED);
            this.isSuccessStatus = false;
            return;
        }

        final String formattedTag = tag.startsWith("@") ? tag : "@" + tag;
        final String currentNickname = Minecraft.getInstance().getUser().getName();

        this.isVerifying = true;
        this.verifyButton.active = false;
        this.verifyButton.setMessage(Component.literal("Verifying...").withStyle(ChatFormatting.YELLOW));
        this.statusMessage = Component.literal("Connecting to backend...").withStyle(ChatFormatting.GRAY);

        // Asynchronous call via AituApiClient - never freeze the render thread!
        AituApiClient.getInstance().verifyPin(formattedTag, pin, currentNickname).thenAccept(result -> {
            Minecraft.getInstance().execute(() -> {
                this.isVerifying = false;
                this.verifyButton.active = true;
                this.verifyButton.setMessage(Component.literal("✔ Verify & Link").withStyle(ChatFormatting.BOLD));

                if (result.success() && result.sessionToken() != null) {
                    try {
                        SessionManager.getInstance().saveSession(
                                result.sessionToken(),
                                currentNickname,
                                result.telegramId(),
                                result.telegramTag() != null ? result.telegramTag() : formattedTag
                        );
                        this.statusMessage = Component.literal("✔ Account successfully linked!").withStyle(ChatFormatting.GREEN);
                        this.isSuccessStatus = true;
                        LOGGER.info("[AITU Hub] Account linked for {} with tag {}", currentNickname, formattedTag);

                        // Refresh tab layout to switch to the Logged In view
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
                this.isVerifying = false;
                this.verifyButton.active = true;
                this.verifyButton.setMessage(Component.literal("✔ Verify & Link").withStyle(ChatFormatting.BOLD));
                this.statusMessage = Component.literal("✖ Network error: " + ex.getMessage()).withStyle(ChatFormatting.RED);
                this.isSuccessStatus = false;
            });
            return null;
        });
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
        int boxWidth = 320;
        int boxHeight = 250;
        int boxY = Math.max(contentY + 4, contentY + (contentHeight - boxHeight) / 2);
        int boxX = centerX - boxWidth / 2;
        int fieldWidth = 280;
        int fieldX = centerX - fieldWidth / 2;

        // 1. Opaque boxed dialog container (width: 320, height: 250) with crisp outline
        guiGraphics.fill(boxX, boxY, boxX + boxWidth, boxY + boxHeight, 0xFF121622);
        guiGraphics.renderOutline(boxX, boxY, boxWidth, boxHeight, 0xFF2D3D58);

        // 2. Section Title (Crisp High-Contrast Gold with Drop Shadow and full 0xFF alpha)
        Component titleComp = Component.literal("AITU Account Sign In").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD);
        int titleWidth = font.width(titleComp);
        guiGraphics.drawString(font, titleComp, centerX - (titleWidth / 2), boxY + 14, 0xFFFFD700, true);

        // 3. Sub-header Description (Crisp Light Text with Drop Shadow and full 0xFF alpha)
        Component subComp = Component.literal("Link your Telegram account to play").withStyle(ChatFormatting.GRAY);
        int subWidth = font.width(subComp);
        guiGraphics.drawString(font, subComp, centerX - (subWidth / 2), boxY + 28, 0xFFAAAAAA, true);

        // 4. Field 1 Label: "Telegram Handle (@username)"
        // Box sits at boxY + 98; Label sits at boxY + 82 (16px vertical distance >= 12px)
        guiGraphics.drawString(
                font,
                Component.literal("Telegram Handle (@username):").withStyle(ChatFormatting.WHITE, ChatFormatting.BOLD),
                fieldX,
                boxY + 82,
                0xFFFFFFFF,
                true
        );

        // 5. Field 2 Label: "6-Digit Verification PIN"
        // Box sits at boxY + 154; Label sits at boxY + 138 (16px vertical distance >= 12px)
        guiGraphics.drawString(
                font,
                Component.literal("6-Digit Verification PIN:").withStyle(ChatFormatting.WHITE, ChatFormatting.BOLD),
                fieldX,
                boxY + 138,
                0xFFFFFFFF,
                true
        );

        // 6. Dynamic Feedback Status (Non-intrusive inline label with full 0xFF alpha and drop-shadow)
        if (!this.statusMessage.getString().isEmpty()) {
            int statusWidth = font.width(this.statusMessage);
            int textColor = this.isSuccessStatus ? 0xFF55FF55 : 0xFFFF5555;
            guiGraphics.drawString(
                    font,
                    this.statusMessage,
                    centerX - (statusWidth / 2),
                    boxY + 218,
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

        // Opaque boxed dialog container (width: 320, height: 250) with crisp outline
        guiGraphics.fill(boxX, boxY, boxX + boxWidth, boxY + boxHeight, 0xFF121622);
        guiGraphics.renderOutline(boxX, boxY, boxWidth, boxHeight, 0xFF2D3D58);

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
