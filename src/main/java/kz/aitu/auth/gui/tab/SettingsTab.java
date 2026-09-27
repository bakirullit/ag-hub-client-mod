package kz.aitu.auth.gui.tab;

import kz.aitu.auth.AituAuthClient;
import kz.aitu.auth.config.AituClientConfig;
import kz.aitu.auth.config.SessionManager;
import kz.aitu.auth.gui.AituHubScreen;
import kz.aitu.auth.server.AituServerManager;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;

/**
 * Settings Tab featuring quick toggles:
 * - "Auto-join on launch"
 * - "Show server notification"
 * And diagnostics for session configuration and server pinning.
 */
public class SettingsTab implements AituTab {

    private AituHubScreen screen;
    private int contentX;
    private int contentY;
    private int contentWidth;
    private int contentHeight;

    private Button autoJoinToggle;
    private Button notificationsToggle;
    private Button reloadSessionButton;
    private Button openBotButton;
    private Component feedback = Component.empty();

    @Override
    public Component getTitle() {
        return Component.literal("Settings");
    }

    @Override
    public String getIcon() {
        return "⚙";
    }

    @Override
    public void init(AituHubScreen parent, int contentX, int contentY, int contentWidth, int contentHeight) {
        this.screen = parent;
        this.contentX = contentX;
        this.contentY = contentY;
        this.contentWidth = contentWidth;
        this.contentHeight = contentHeight;

        int centerX = contentX + contentWidth / 2;
        int boxWidth = 320;
        int boxHeight = 250;
        int boxY = Math.max(contentY + 4, contentY + (contentHeight - boxHeight) / 2);
        int btnWidth = 260;
        int btnX = centerX - btnWidth / 2;

        AituClientConfig config = AituClientConfig.getInstance();

        // 1. Quick Toggle: Auto-join on launch
        this.autoJoinToggle = Button.builder(
                getAutoJoinMessage(config.isAutoJoinLaunch()),
                btn -> {
                    boolean next = !config.isAutoJoinLaunch();
                    config.setAutoJoinLaunch(next);
                    btn.setMessage(getAutoJoinMessage(next));
                    this.feedback = Component.literal("Auto-join setting saved.").withStyle(ChatFormatting.GREEN);
                }
        ).bounds(btnX, boxY + 68, btnWidth, 20)
        .tooltip(Tooltip.create(Component.literal("Automatically attempts connection to the AITU official server on game launch.")))
        .build();
        screen.registerTabWidget(this.autoJoinToggle);

        // 2. Quick Toggle: Show server notification
        this.notificationsToggle = Button.builder(
                getNotificationsMessage(config.isShowServerNotifications()),
                btn -> {
                    boolean next = !config.isShowServerNotifications();
                    config.setShowServerNotifications(next);
                    btn.setMessage(getNotificationsMessage(next));
                    this.feedback = Component.literal("Notification setting saved.").withStyle(ChatFormatting.GREEN);
                }
        ).bounds(btnX, boxY + 92, btnWidth, 20)
        .tooltip(Tooltip.create(Component.literal("Shows status notifications when connecting to AITU gaming servers.")))
        .build();
        screen.registerTabWidget(this.notificationsToggle);

        // 3. Action Button: Reload Session from Disk
        this.reloadSessionButton = Button.builder(
                Component.literal("🔄 Reload Session from Disk"),
                btn -> {
                    boolean loaded = SessionManager.getInstance().loadSession();
                    if (loaded) {
                        this.feedback = Component.literal("✔ Reloaded valid session from disk!").withStyle(ChatFormatting.GREEN);
                    } else {
                        this.feedback = Component.literal("⚠ No valid session file found on disk.").withStyle(ChatFormatting.YELLOW);
                    }
                    screen.refreshActiveTab();
                }
        ).bounds(btnX, boxY + 120, btnWidth, 20)
        .tooltip(Tooltip.create(Component.literal("Re-reads .minecraft/config/aitu_session.json in case it was modified externally.")))
        .build();
        screen.registerTabWidget(this.reloadSessionButton);

        // 4. Action Button: Open Telegram Bot
        this.openBotButton = Button.builder(
                Component.literal("🔗 Open Telegram Bot"),
                btn -> Util.getPlatform().openUri(AituAuthClient.TELEGRAM_BOT_URL)
        ).bounds(btnX, boxY + 144, btnWidth, 20)
        .build();
        screen.registerTabWidget(this.openBotButton);
    }

    private Component getAutoJoinMessage(boolean enabled) {
        return Component.literal("Auto-join on launch: ").append(
                enabled ? Component.literal("ON").withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD)
                        : Component.literal("OFF").withStyle(ChatFormatting.RED, ChatFormatting.BOLD)
        );
    }

    private Component getNotificationsMessage(boolean enabled) {
        return Component.literal("Server notifications: ").append(
                enabled ? Component.literal("ON").withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD)
                        : Component.literal("OFF").withStyle(ChatFormatting.RED, ChatFormatting.BOLD)
        );
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        Font font = Minecraft.getInstance().font;
        int centerX = contentX + contentWidth / 2;
        int boxWidth = 320;
        int boxHeight = 250;
        int boxY = Math.max(contentY + 4, contentY + (contentHeight - boxHeight) / 2);
        int boxX = centerX - boxWidth / 2;

        // Opaque boxed dialog container (width: 320, height: 250)
        guiGraphics.fill(boxX, boxY, boxX + boxWidth, boxY + boxHeight, 0xFF121622);
        guiGraphics.renderOutline(boxX, boxY, boxWidth, boxHeight, 0xFF2D3D58);

        // Header Title (Crisp Gold with drop-shadow and full 0xFF alpha)
        Component titleComp = Component.literal("Client Settings & Options").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD);
        int titleWidth = font.width(titleComp);
        guiGraphics.drawString(font, titleComp, centerX - (titleWidth / 2), boxY + 14, 0xFFFFD700, true);

        // Diagnostics Rows (Crisp text with drop-shadow and full 0xFF alpha)
        guiGraphics.drawString(
                font,
                Component.literal("Mod Version:").withStyle(ChatFormatting.WHITE),
                boxX + 20,
                boxY + 34,
                0xFFFFFFFF,
                true
        );
        guiGraphics.drawString(
                font,
                Component.literal("1.2.0 (NeoForge 1.21.1)").withStyle(ChatFormatting.AQUA),
                boxX + 110,
                boxY + 34,
                0xFF55FFFF,
                true
        );

        guiGraphics.drawString(
                font,
                Component.literal("Pinned Server:").withStyle(ChatFormatting.WHITE),
                boxX + 20,
                boxY + 48,
                0xFFFFFFFF,
                true
        );
        guiGraphics.drawString(
                font,
                Component.literal(AituServerManager.getServerIp()).withStyle(ChatFormatting.GREEN),
                boxX + 110,
                boxY + 48,
                0xFF55FF55,
                true
        );

        // Feedback message
        if (!this.feedback.getString().isEmpty()) {
            int fbWidth = font.width(this.feedback);
            guiGraphics.drawString(
                    font,
                    this.feedback,
                    centerX - (fbWidth / 2),
                    boxY + 172,
                    0xFFFFFFFF,
                    true
            );
        }

        // Additional information divider & footer
        guiGraphics.fill(boxX + 14, boxY + 192, boxX + boxWidth - 14, boxY + 193, 0xFF243248);

        guiGraphics.drawString(
                font,
                Component.literal("Storage:").withStyle(ChatFormatting.WHITE),
                boxX + 20,
                boxY + 200,
                0xFFFFFFFF,
                true
        );
        guiGraphics.drawString(
                font,
                Component.literal(".minecraft/config/aitu_session.json").withStyle(ChatFormatting.YELLOW),
                boxX + 80,
                boxY + 200,
                0xFFFFFF55,
                true
        );

        guiGraphics.drawString(
                font,
                Component.literal("Handshake:").withStyle(ChatFormatting.WHITE),
                boxX + 20,
                boxY + 214,
                0xFFFFFFFF,
                true
        );
        guiGraphics.drawString(
                font,
                Component.literal("aitu_auth:token_payload").withStyle(ChatFormatting.GREEN),
                boxX + 80,
                boxY + 214,
                0xFF55FF55,
                true
        );
    }
}
