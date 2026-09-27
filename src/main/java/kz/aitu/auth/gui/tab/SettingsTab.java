package kz.aitu.auth.gui.tab;

import kz.aitu.auth.AituAuthClient;
import kz.aitu.auth.config.SessionManager;
import kz.aitu.auth.gui.AituHubScreen;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;

/**
 * Settings and diagnostics tab for AituHubScreen.
 */
public class SettingsTab implements AituTab {

    private AituHubScreen screen;
    private int contentX;
    private int contentY;
    private int contentWidth;
    private int contentHeight;

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
        int boxHeight = 150;
        int boxY = Math.max(contentY + 12, contentY + (contentHeight - boxHeight) / 2);

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
        ).bounds(centerX - 110, boxY + 70, 220, 20)
        .tooltip(Tooltip.create(Component.literal("Re-reads .minecraft/config/aitu_session.json in case it was modified externally.")))
        .build();
        screen.registerTabWidget(this.reloadSessionButton);

        this.openBotButton = Button.builder(
                Component.literal("🔗 Open Telegram Bot"),
                btn -> Util.getPlatform().openUri(AituAuthClient.TELEGRAM_BOT_URL)
        ).bounds(centerX - 110, boxY + 96, 220, 20)
        .build();
        screen.registerTabWidget(this.openBotButton);
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        Font font = Minecraft.getInstance().font;
        int centerX = contentX + contentWidth / 2;
        int boxWidth = Math.min(320, contentWidth - 20);
        int boxHeight = 150;
        int boxY = Math.max(contentY + 12, contentY + (contentHeight - boxHeight) / 2);
        int boxX = centerX - boxWidth / 2;

        // Darkened Container Box with Crisp Outline
        guiGraphics.fill(boxX, boxY, boxX + boxWidth, boxY + boxHeight, 0xD0101420);
        guiGraphics.renderOutline(boxX, boxY, boxWidth, boxHeight, 0xFF2B3E5C);

        // Header Title (Crisp Gold)
        guiGraphics.drawCenteredString(
                font,
                Component.literal("Client Configuration & Diagnostics").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD),
                centerX,
                boxY + 14,
                0xFFD700
        );

        // Info Rows (Crisp White & High Contrast Colors with Dropshadow)
        guiGraphics.drawString(
                font,
                Component.literal("Mod Version:").withStyle(ChatFormatting.WHITE),
                boxX + 20,
                boxY + 34,
                0xFFFFFF,
                true
        );
        guiGraphics.drawString(
                font,
                Component.literal("1.1.0 (NeoForge 1.21.1)").withStyle(ChatFormatting.AQUA),
                boxX + 110,
                boxY + 34,
                0x55FFFF,
                true
        );

        guiGraphics.drawString(
                font,
                Component.literal("Handshake:").withStyle(ChatFormatting.WHITE),
                boxX + 20,
                boxY + 48,
                0xFFFFFF,
                true
        );
        guiGraphics.drawString(
                font,
                Component.literal("Active (aitu_auth channel)").withStyle(ChatFormatting.GREEN),
                boxX + 110,
                boxY + 48,
                0x55FF55,
                true
        );

        // Feedback message
        if (!this.feedback.getString().isEmpty()) {
            guiGraphics.drawCenteredString(
                    font,
                    this.feedback,
                    centerX,
                    boxY + 124,
                    0xFFFFFF
            );
        }
    }
}
