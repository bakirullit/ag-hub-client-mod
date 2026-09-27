package kz.aitu.auth.gui.tab;

import kz.aitu.auth.api.AituApiClient;
import kz.aitu.auth.gui.AituHubScreen;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * Friends Tab (Beta) displaying online friends fetched from AITU backend.
 * Uses an ObjectSelectionList scrollable list and an asynchronous Refresh button.
 * Format: [Status Dot] Nickname - Playing on AITU SMP / Idle
 */
public class FriendsTab implements AituTab {

    private AituHubScreen screen;
    private int contentX;
    private int contentY;
    private int contentWidth;
    private int contentHeight;

    private Button refreshButton;
    private FriendsListWidget friendsListWidget;

    private String statusNote = "Ready";
    private boolean isRefreshing = false;

    @Override
    public Component getTitle() {
        return Component.literal("Friends (Beta)");
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
        int boxWidth = 320;
        int boxHeight = 250;
        int boxY = Math.max(contentY + 4, contentY + (contentHeight - boxHeight) / 2);
        int boxX = centerX - boxWidth / 2;

        // 1. Asynchronous Refresh Button (Top-right of container)
        int btnWidth = 72;
        int btnHeight = 18;
        this.refreshButton = Button.builder(
                Component.literal("🔄 Refresh"),
                btn -> fetchFriendsAsync()
        ).bounds(boxX + boxWidth - btnWidth - 12, boxY + 10, btnWidth, btnHeight)
        .tooltip(Tooltip.create(Component.literal("Fetches online friends from AITU backend.")))
        .build();
        screen.registerTabWidget(this.refreshButton);

        // 2. Scrollable list view (ObjectSelectionList)
        int listWidth = boxWidth - 24;
        int listHeight = 186;
        int listX = boxX + 12;
        int listY = boxY + 34;

        Minecraft mc = Minecraft.getInstance();
        this.friendsListWidget = new FriendsListWidget(mc, listWidth, listHeight, listY, 26);
        this.friendsListWidget.setX(listX);
        screen.registerTabWidget(this.friendsListWidget);

        // Populate initial entries
        fetchFriendsAsync();
    }

    private void fetchFriendsAsync() {
        if (this.isRefreshing) return;
        this.isRefreshing = true;

        if (this.refreshButton != null) {
            this.refreshButton.active = false;
            this.refreshButton.setMessage(Component.literal("..."));
        }
        this.statusNote = "Fetching from backend...";

        AituApiClient.getInstance().fetchFriends().thenAccept(friends -> {
            Minecraft.getInstance().execute(() -> {
                this.isRefreshing = false;
                if (this.refreshButton != null) {
                    this.refreshButton.active = true;
                    this.refreshButton.setMessage(Component.literal("🔄 Refresh"));
                }

                if (this.friendsListWidget != null) {
                    this.friendsListWidget.populate(friends);
                }
                this.statusNote = friends.size() + " friend(s) online";
            });
        }).exceptionally(ex -> {
            Minecraft.getInstance().execute(() -> {
                this.isRefreshing = false;
                if (this.refreshButton != null) {
                    this.refreshButton.active = true;
                    this.refreshButton.setMessage(Component.literal("🔄 Refresh"));
                }
                this.statusNote = "Offline (backend unavailable)";
                if (this.friendsListWidget != null) {
                    this.friendsListWidget.populate(AituApiClient.getDefaultDemoFriends());
                }
            });
            return null;
        });
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
        Component titleComp = Component.literal("Online Friends").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD);
        guiGraphics.drawString(font, titleComp, boxX + 14, boxY + 12, 0xFFFFD700, true);

        // Subtitle status (Crisp Gray with drop-shadow and full 0xFF alpha)
        Component subComp = Component.literal(this.statusNote).withStyle(ChatFormatting.GRAY);
        guiGraphics.drawString(font, subComp, boxX + 14, boxY + 23, 0xFFAAAAAA, true);

        // Bottom hint (Non-intrusive status line)
        Component hintComp = Component.literal("AITU Gaming Network").withStyle(ChatFormatting.DARK_GRAY);
        int hintWidth = font.width(hintComp);
        guiGraphics.drawString(font, hintComp, centerX - (hintWidth / 2), boxY + 234, 0xFF778899, true);
    }

    /**
     * Scrollable list component for online friends.
     */
    public static class FriendsListWidget extends ObjectSelectionList<FriendsListWidget.FriendEntryRow> {

        public FriendsListWidget(Minecraft minecraft, int width, int height, int y, int itemHeight) {
            super(minecraft, width, height, y, itemHeight);
        }

        public void populate(List<AituApiClient.FriendEntry> friends) {
            this.clearEntries();
            if (friends != null) {
                for (AituApiClient.FriendEntry f : friends) {
                    this.addEntry(new FriendEntryRow(f));
                }
            }
        }

        @Override
        public int getRowWidth() {
            return this.width - 12;
        }

        @Override
        protected void renderListBackground(GuiGraphics guiGraphics) {
            // Clean opaque list background
            guiGraphics.fill(this.getX(), this.getY(), this.getX() + this.width, this.getY() + this.height, 0xFF0F131D);
            guiGraphics.renderOutline(this.getX(), this.getY(), this.width, this.height, 0xFF243248);
        }

        @Override
        protected void renderListSeparators(GuiGraphics guiGraphics) {
            // No blurry default textures
        }

        public static class FriendEntryRow extends ObjectSelectionList.Entry<FriendEntryRow> {

            private final AituApiClient.FriendEntry friend;

            public FriendEntryRow(AituApiClient.FriendEntry friend) {
                this.friend = friend;
            }

            @Override
            public Component getNarration() {
                return Component.literal(friend.nickname() + " is " + friend.status());
            }

            @Override
            public void render(GuiGraphics guiGraphics, int index, int top, int left, int width, int height,
                               int mouseX, int mouseY, boolean hovering, float partialTick) {
                Font font = Minecraft.getInstance().font;

                // Entry background on hover or selection
                int bgColor = hovering ? 0xFF1C273C : 0xFF141926;
                guiGraphics.fill(left, top + 1, left + width, top + height - 1, bgColor);
                guiGraphics.renderOutline(left, top + 1, width, height - 2, 0xFF26364D);

                // Format: [Status Dot] Nickname - Playing on AITU SMP / Idle
                // Status dot: Green if online, Gray if idle
                boolean isOnline = friend.isOnline();
                Component statusDot = Component.literal("● ").withStyle(isOnline ? ChatFormatting.GREEN : ChatFormatting.GRAY);
                Component nicknamePart = Component.literal(friend.nickname()).withStyle(ChatFormatting.WHITE, ChatFormatting.BOLD);
                Component separator = Component.literal(" - ").withStyle(ChatFormatting.DARK_GRAY);
                Component statusPart = Component.literal(friend.status()).withStyle(isOnline ? ChatFormatting.AQUA : ChatFormatting.GRAY);

                Component fullText = statusDot.copy().append(nicknamePart).append(separator).append(statusPart);

                // Render with drop-shadow enabled and full 0xFF alpha
                guiGraphics.drawString(font, fullText, left + 8, top + 7, 0xFFFFFFFF, true);
            }
        }
    }
}
