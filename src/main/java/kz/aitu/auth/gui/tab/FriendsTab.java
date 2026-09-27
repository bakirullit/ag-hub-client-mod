package kz.aitu.auth.gui.tab;

import kz.aitu.auth.AituAuthClient;
import kz.aitu.auth.gui.AituHubScreen;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

/**
 * Friends Tab (Stub/Foundation for upcoming release).
 * Displays a placeholder view announcing the upcoming social & party system.
 */
public class FriendsTab implements AituTab {

    private AituHubScreen screen;
    private int contentX;
    private int contentY;
    private int contentWidth;
    private int contentHeight;

    private Button telegramCommunityButton;

    @Override
    public Component getTitle() {
        return Component.literal("Friends");
    }

    @Override
    public String getIcon() {
        return "👥";
    }

    @Override
    public void init(AituHubScreen parent, int contentX, int contentY, int contentWidth, int contentHeight) {
        this.screen = parent;
        this.contentX = contentX;
        this.contentY = contentY;
        this.contentWidth = contentWidth;
        this.contentHeight = contentHeight;

        int centerX = contentX + contentWidth / 2;
        int boxHeight = 140;
        int boxY = Math.max(contentY + 12, contentY + (contentHeight - boxHeight) / 2);

        this.telegramCommunityButton = Button.builder(
                Component.literal("💬 Join Gaming Hub on Telegram"),
                btn -> Util.getPlatform().openUri(AituAuthClient.TELEGRAM_BOT_URL)
        ).bounds(centerX - 110, boxY + 98, 220, 20)
        .build();
        screen.registerTabWidget(this.telegramCommunityButton);
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        Font font = Minecraft.getInstance().font;
        int centerX = contentX + contentWidth / 2;
        int boxWidth = Math.min(320, contentWidth - 20);
        int boxHeight = 140;
        int boxY = Math.max(contentY + 12, contentY + (contentHeight - boxHeight) / 2);
        int boxX = centerX - boxWidth / 2;

        // Darkened Container Box with Crisp Outline
        guiGraphics.fill(boxX, boxY, boxX + boxWidth, boxY + boxHeight, 0xD0101420);
        guiGraphics.renderOutline(boxX, boxY, boxWidth, boxHeight, 0xFF2B3E5C);

        // Icon
        guiGraphics.drawCenteredString(
                font,
                Component.literal("👥").withStyle(ChatFormatting.BOLD),
                centerX,
                boxY + 18,
                0x00E676
        );

        // Headline (Crisp High-Contrast Gold)
        guiGraphics.drawCenteredString(
                font,
                Component.literal("Friends system coming soon!").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD),
                centerX,
                boxY + 36,
                0xFFD700
        );

        // Explanatory Text (Crisp White)
        guiGraphics.drawCenteredString(
                font,
                Component.literal("Connect with fellow AITU university students,").withStyle(ChatFormatting.WHITE),
                centerX,
                boxY + 56,
                0xFFFFFF
        );
        guiGraphics.drawCenteredString(
                font,
                Component.literal("view friends online, and jump into servers together.").withStyle(ChatFormatting.WHITE),
                centerX,
                boxY + 70,
                0xFFFFFF
        );
    }
}
