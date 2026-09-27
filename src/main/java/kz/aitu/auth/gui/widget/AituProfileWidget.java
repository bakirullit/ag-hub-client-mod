package kz.aitu.auth.gui.widget;

import kz.aitu.auth.config.SessionData;
import kz.aitu.auth.config.SessionManager;
import kz.aitu.auth.gui.AituHubScreen;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.network.chat.Component;

/**
 * Modern compact profile widget rendered in the top-right corner of TitleScreen.
 * - If unlinked: shows guest profile icon with subtle warning indicator and "Sign In" tooltip.
 * - If linked: shows player face avatar, cached @tag, and a green active indicator.
 * Clicking opens AituHubScreen.
 */
public class AituProfileWidget extends AbstractButton {

    private final Screen parentScreen;
    private final boolean isLinked;
    private final SessionData session;
    private final String displayTag;

    public AituProfileWidget(int x, int y, Screen parentScreen) {
        super(x, y, calculateWidth(parentScreen), 22, Component.empty());
        this.parentScreen = parentScreen;

        SessionManager manager = SessionManager.getInstance();
        this.isLinked = manager.hasValidSession();
        this.session = manager.getSession().orElse(null);

        if (this.isLinked && this.session != null) {
            this.displayTag = this.session.getTelegramTag();
            this.setTooltip(Tooltip.create(Component.literal(
                    "§6AITU Gaming Hub\n" +
                    "§a● " + this.displayTag + " §7(" + this.session.getCachedNickname() + ")\n" +
                    "§7Click to open profile, friends & settings."
            )));
        } else {
            this.displayTag = null;
            this.setTooltip(Tooltip.create(Component.literal(
                    "§6AITU Gaming Hub\n" +
                    "§e● Guest Profile §c(Unlinked)\n" +
                    "§7Click to Sign In with Telegram."
            )));
        }

        // Adjust position so right-margin stays constant
        this.setX(parentScreen.width - this.width - 8);
        this.setY(8);
    }

    private static int calculateWidth(Screen screen) {
        SessionManager manager = SessionManager.getInstance();
        if (manager.hasValidSession()) {
            SessionData data = manager.getSession().orElse(null);
            String tag = data != null ? data.getTelegramTag() : "@player";
            Font font = Minecraft.getInstance().font;
            int textWidth = font.width(tag);
            return Math.max(90, 24 + textWidth + 10);
        }
        return 78; // Avatar (16) + padding + "Sign In" text
    }

    @Override
    public void onPress() {
        Minecraft.getInstance().setScreen(new AituHubScreen(this.parentScreen));
    }

    @Override
    protected void renderWidget(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        boolean hovered = this.isHoveredOrFocused();

        // 1. Sleek Glassmorphism Background & Border
        int bgColor = hovered ? 0xD8202534 : 0xB0121520;
        int borderColor;
        if (hovered) {
            borderColor = this.isLinked ? 0xFF00AAFF : 0xFFFFBB33;
        } else {
            borderColor = this.isLinked ? 0xFF2A4060 : 0xFF554422;
        }

        guiGraphics.fill(getX(), getY(), getX() + getWidth(), getY() + getHeight(), bgColor);
        guiGraphics.renderOutline(getX(), getY(), getWidth(), getHeight(), borderColor);

        Font font = Minecraft.getInstance().font;
        int avatarX = getX() + 3;
        int avatarY = getY() + 3;
        int avatarSize = 16;

        if (this.isLinked && this.session != null) {
            // 2A. Linked State: Render player head/avatar
            PlayerSkin skin = Minecraft.getInstance().getSkinManager().getInsecureSkin(
                    Minecraft.getInstance().getGameProfile()
            );
            PlayerFaceRenderer.draw(guiGraphics, skin.texture(), avatarX, avatarY, avatarSize);

            // Active green indicator dot
            int dotX = avatarX + 11;
            int dotY = avatarY + 11;
            guiGraphics.fill(dotX, dotY, dotX + 5, dotY + 5, 0xFF00E676);
            guiGraphics.renderOutline(dotX, dotY, 5, 5, 0xFF003816);

            // Cached @tag text
            int textX = avatarX + avatarSize + 5;
            int textY = getY() + 7;
            int textColor = hovered ? 0xFFFFFF : 0xBBDDFF;
            guiGraphics.drawString(font, this.displayTag, textX, textY, textColor);
        } else {
            // 2B. Unlinked State: Render guest avatar box
            guiGraphics.fill(avatarX, avatarY, avatarX + avatarSize, avatarY + avatarSize, 0xFF262A34);
            guiGraphics.renderOutline(avatarX, avatarY, avatarSize, avatarSize, 0xFF404656);
            guiGraphics.drawCenteredString(font, "👤", avatarX + avatarSize / 2, avatarY + 4, 0x8892A4);

            // Warning indicator dot (amber/orange)
            int dotX = avatarX + 11;
            int dotY = avatarY + 11;
            guiGraphics.fill(dotX, dotY, dotX + 5, dotY + 5, 0xFFFFAB00);
            guiGraphics.renderOutline(dotX, dotY, 5, 5, 0xFF552E00);

            // "Sign In" text
            int textX = avatarX + avatarSize + 5;
            int textY = getY() + 7;
            Component signInText = Component.literal("Sign In").withStyle(
                    hovered ? ChatFormatting.YELLOW : ChatFormatting.GOLD,
                    ChatFormatting.BOLD
            );
            guiGraphics.drawString(font, signInText, textX, textY, 0xFFFFFF);
        }
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput narrationElementOutput) {
        this.defaultButtonNarrationText(narrationElementOutput);
    }
}
