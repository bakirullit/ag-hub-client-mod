package kz.aitu.auth.gui.tab;

import kz.aitu.auth.api.AituApiClient;
import kz.aitu.auth.gui.AddFriendModal;
import kz.aitu.auth.gui.AituHubScreen;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Friends Tab Architecture connected to real AITU backend API:
 * - Search Box: real-time filtering by nickname or telegram tag.
 * - "+" Button: opens AddFriendModal dialog.
 * - "Requests" Button with counter badge: toggles pending requests sub-panel.
 * - Requests Sub-panel: shows incoming requests with inline "[ ✔ Accept ]" and "[ ✖ Decline ]" actions.
 * - Friends List: shows status indicators (Online: green, Playing on AITU SMP: blue, Offline: grey).
 */
public class FriendsTab implements AituTab {

    private static final int VIEW_FRIENDS = 0;
    private static final int VIEW_REQUESTS = 1;

    private AituHubScreen screen;
    private int contentX;
    private int contentY;
    private int contentWidth;
    private int contentHeight;

    private int currentView = VIEW_FRIENDS;

    // Cache
    private final List<AituApiClient.FriendItem> allFriends = new ArrayList<>();
    private final List<AituApiClient.FriendItem> filteredFriends = new ArrayList<>();
    private final List<AituApiClient.FriendRequestItem> pendingRequests = new ArrayList<>();
    private String searchFilter = "";

    // Widgets (Friends View)
    private EditBox searchBox;
    private Button addButton;
    private Button requestsButton;
    private Button refreshButton;
    private FriendsListWidget friendsListWidget;

    // Widgets (Requests View)
    private Button backToFriendsButton;
    private RequestsListWidget requestsListWidget;

    private boolean isInitialLoaded = false;
    private String statusMessage = "";

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

        Font font = Minecraft.getInstance().font;

        if (this.currentView == VIEW_FRIENDS) {
            // --- Top Control Bar ---
            // 1. Search Box (filters friends in real time)
            this.searchBox = new EditBox(font, boxX + 10, boxY + 8, 140, 20, Component.literal("Search"));
            this.searchBox.setMaxLength(64);
            this.searchBox.setHint(Component.literal("🔍 Search...").withStyle(ChatFormatting.DARK_GRAY));
            this.searchBox.setValue(this.searchFilter);
            this.searchBox.setResponder(this::onSearchChanged);
            screen.registerTabWidget(this.searchBox);

            // 2. "+" Button (opens AddFriendModal)
            this.addButton = Button.builder(
                    Component.literal("+").withStyle(ChatFormatting.BOLD),
                    btn -> Minecraft.getInstance().setScreen(new AddFriendModal(this.screen, this::refreshData))
            ).bounds(boxX + 154, boxY + 8, 22, 20)
            .tooltip(Tooltip.create(Component.literal("Add Friend (Telegram, Phone, Email, UUID)")))
            .build();
            screen.registerTabWidget(this.addButton);

            // 3. "Requests" Button with counter badge
            String reqText = this.pendingRequests.isEmpty() ? "Requests" : "Requests (" + this.pendingRequests.size() + ")";
            this.requestsButton = Button.builder(
                    Component.literal(reqText),
                    btn -> {
                        this.currentView = VIEW_REQUESTS;
                        this.screen.refreshActiveTab();
                    }
            ).bounds(boxX + 180, boxY + 8, 86, 20)
            .tooltip(Tooltip.create(Component.literal("View incoming friend requests")))
            .build();
            screen.registerTabWidget(this.requestsButton);

            // 4. Refresh Button
            this.refreshButton = Button.builder(
                    Component.literal("🔄"),
                    btn -> refreshData()
            ).bounds(boxX + 270, boxY + 8, 40, 20)
            .tooltip(Tooltip.create(Component.literal("Refresh Friends & Requests")))
            .build();
            screen.registerTabWidget(this.refreshButton);

            // 5. Scrollable Friends List Container
            this.friendsListWidget = new FriendsListWidget(Minecraft.getInstance(), boxWidth - 20, 196, boxY + 34, 26);
            this.friendsListWidget.setX(boxX + 10);
            this.friendsListWidget.populate(this.filteredFriends);
            screen.registerTabWidget(this.friendsListWidget);

        } else {
            // --- Requests View / Sub-panel ---
            this.backToFriendsButton = Button.builder(
                    Component.literal("← Friends List"),
                    btn -> {
                        this.currentView = VIEW_FRIENDS;
                        this.screen.refreshActiveTab();
                    }
            ).bounds(boxX + 10, boxY + 8, 120, 20)
            .build();
            screen.registerTabWidget(this.backToFriendsButton);

            this.refreshButton = Button.builder(
                    Component.literal("🔄 Refresh"),
                    btn -> refreshData()
            ).bounds(boxX + 230, boxY + 8, 80, 20)
            .build();
            screen.registerTabWidget(this.refreshButton);

            this.requestsListWidget = new RequestsListWidget(
                    Minecraft.getInstance(),
                    boxWidth - 20,
                    196,
                    boxY + 34,
                    30,
                    this::handleAcceptRequest,
                    this::handleDeclineRequest
            );
            this.requestsListWidget.setX(boxX + 10);
            this.requestsListWidget.populate(this.pendingRequests);
            screen.registerTabWidget(this.requestsListWidget);
        }

        if (!this.isInitialLoaded) {
            this.isInitialLoaded = true;
            refreshData();
        }
    }

    private void onSearchChanged(String query) {
        this.searchFilter = query == null ? "" : query.trim().toLowerCase();
        applyFilter();
        if (this.friendsListWidget != null) {
            this.friendsListWidget.populate(this.filteredFriends);
        }
    }

    private void applyFilter() {
        this.filteredFriends.clear();
        if (this.searchFilter.isEmpty()) {
            this.filteredFriends.addAll(this.allFriends);
        } else {
            for (AituApiClient.FriendItem f : this.allFriends) {
                if (f.nickname().toLowerCase().contains(this.searchFilter)
                        || (f.telegramTag() != null && f.telegramTag().toLowerCase().contains(this.searchFilter))) {
                    this.filteredFriends.add(f);
                }
            }
        }
    }

    public void refreshData() {
        if (this.refreshButton != null) {
            this.refreshButton.active = false;
        }

        AituApiClient api = AituApiClient.getInstance();

        // 1. Fetch Friends List
        api.fetchFriendsList().thenAccept(friends -> {
            Minecraft.getInstance().execute(() -> {
                this.allFriends.clear();
                if (friends != null) {
                    this.allFriends.addAll(friends);
                }
                applyFilter();
                if (this.friendsListWidget != null) {
                    this.friendsListWidget.populate(this.filteredFriends);
                }
                if (this.refreshButton != null) {
                    this.refreshButton.active = true;
                }
            });
        }).exceptionally(ex -> {
            Minecraft.getInstance().execute(() -> {
                if (this.refreshButton != null) {
                    this.refreshButton.active = true;
                }
            });
            return null;
        });

        // 2. Fetch Friend Requests
        api.fetchFriendRequests().thenAccept(requests -> {
            Minecraft.getInstance().execute(() -> {
                this.pendingRequests.clear();
                if (requests != null) {
                    this.pendingRequests.addAll(requests);
                }
                if (this.requestsButton != null) {
                    String reqText = this.pendingRequests.isEmpty() ? "Requests" : "Requests (" + this.pendingRequests.size() + ")";
                    this.requestsButton.setMessage(Component.literal(reqText));
                }
                if (this.requestsListWidget != null) {
                    this.requestsListWidget.populate(this.pendingRequests);
                }
            });
        });
    }

    private void handleAcceptRequest(String requestId) {
        AituApiClient.getInstance().acceptFriendRequest(requestId).thenAccept(res -> {
            Minecraft.getInstance().execute(() -> {
                this.statusMessage = res.success() ? "✔ Accepted request!" : "✖ " + res.error();
                refreshData();
            });
        });
    }

    private void handleDeclineRequest(String requestId) {
        AituApiClient.getInstance().declineFriendRequest(requestId).thenAccept(res -> {
            Minecraft.getInstance().execute(() -> {
                this.statusMessage = res.success() ? "Request declined." : "✖ " + res.error();
                refreshData();
            });
        });
    }

    @Override
    public void renderBackground(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        int centerX = contentX + contentWidth / 2;
        int boxWidth = 320;
        int boxHeight = 250;
        int boxY = Math.max(contentY + 4, contentY + (contentHeight - boxHeight) / 2);
        int boxX = centerX - boxWidth / 2;

        // Opaque boxed dialog container (width: 320, height: 250)
        guiGraphics.fill(boxX, boxY, boxX + boxWidth, boxY + boxHeight, 0xFF121622);
        guiGraphics.renderOutline(boxX, boxY, boxWidth, boxHeight, 0xFF2D3D58);
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        Font font = Minecraft.getInstance().font;
        int centerX = contentX + contentWidth / 2;
        int boxWidth = 320;
        int boxHeight = 250;
        int boxY = Math.max(contentY + 4, contentY + (contentHeight - boxHeight) / 2);
        int boxX = centerX - boxWidth / 2;

        if (this.currentView == VIEW_REQUESTS) {
            Component reqTitle = Component.literal("Incoming Requests (" + this.pendingRequests.size() + ")").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD);
            guiGraphics.drawString(font, reqTitle, boxX + 135, boxY + 14, 0xFFFFD700, true);
        }

        // Bottom non-intrusive status message
        if (!this.statusMessage.isEmpty()) {
            int msgWidth = font.width(this.statusMessage);
            guiGraphics.drawString(font, this.statusMessage, centerX - (msgWidth / 2), boxY + 234, 0xFF55FFFF, true);
        }
    }

    // --- Scrollable Friends List ---

    public static class FriendsListWidget extends ObjectSelectionList<FriendsListWidget.FriendEntryRow> {

        public FriendsListWidget(Minecraft minecraft, int width, int height, int y, int itemHeight) {
            super(minecraft, width, height, y, itemHeight);
        }

        public void populate(List<AituApiClient.FriendItem> friends) {
            this.clearEntries();
            if (friends != null) {
                for (AituApiClient.FriendItem f : friends) {
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
            guiGraphics.fill(this.getX(), this.getY(), this.getX() + this.width, this.getY() + this.height, 0xFF0F131D);
            guiGraphics.renderOutline(this.getX(), this.getY(), this.width, this.height, 0xFF243248);
        }

        @Override
        protected void renderListSeparators(GuiGraphics guiGraphics) {
        }

        @Override
        public void renderWidget(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
            super.renderWidget(guiGraphics, mouseX, mouseY, partialTick);

            // Empty state notice
            if (this.getItemCount() == 0) {
                Font font = Minecraft.getInstance().font;
                Component emptyNotice = Component.literal("No friends found.").withStyle(ChatFormatting.GRAY);
                Component hintNotice = Component.literal("Click [+] to add a friend!").withStyle(ChatFormatting.DARK_GRAY);

                int textX1 = this.getX() + (this.width - font.width(emptyNotice)) / 2;
                int textX2 = this.getX() + (this.width - font.width(hintNotice)) / 2;

                guiGraphics.drawString(font, emptyNotice, textX1, this.getY() + 35, 0xFFAAAAAA, true);
                guiGraphics.drawString(font, hintNotice, textX2, this.getY() + 50, 0xFF777777, true);
            }
        }

        public static class FriendEntryRow extends ObjectSelectionList.Entry<FriendEntryRow> {

            private final AituApiClient.FriendItem friend;

            public FriendEntryRow(AituApiClient.FriendItem friend) {
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

                int bgColor = hovering ? 0xFF1C273C : 0xFF141926;
                guiGraphics.fill(left, top + 1, left + width, top + height - 1, bgColor);
                guiGraphics.renderOutline(left, top + 1, width, height - 2, 0xFF26364D);

                // Status indicators:
                // - Playing on AITU SMP -> Blue (0xFF3399FF)
                // - Online -> Green (0xFF00FF66)
                // - Offline -> Grey (0xFFAAAAAA)
                int dotColor;
                int statusColor;
                String displayStatus = friend.status() != null ? friend.status() : (friend.isOnline() ? "Online" : "Offline");

                if (friend.isPlayingSmp()) {
                    dotColor = 0xFF3399FF; // Blue
                    statusColor = 0xFF55FFFF;
                } else if (friend.isOnline() || displayStatus.equalsIgnoreCase("online")) {
                    dotColor = 0xFF00FF66; // Green
                    statusColor = 0xFF55FF55;
                } else {
                    dotColor = 0xFFAAAAAA; // Grey
                    statusColor = 0xFFAAAAAA;
                }

                // Render Status Dot
                guiGraphics.drawString(font, "● ", left + 6, top + 8, dotColor, true);

                // Render Nickname & Status
                Component nickComp = Component.literal(friend.nickname()).withStyle(ChatFormatting.WHITE, ChatFormatting.BOLD);
                Component statusComp = Component.literal(" - " + displayStatus);

                guiGraphics.drawString(font, nickComp, left + 18, top + 8, 0xFFFFFFFF, true);
                int nickWidth = font.width(nickComp);
                guiGraphics.drawString(font, statusComp, left + 18 + nickWidth, top + 8, statusColor, true);

                // Render Telegram Tag on right
                if (friend.telegramTag() != null && !friend.telegramTag().isBlank()) {
                    Component tagComp = Component.literal(friend.telegramTag()).withStyle(ChatFormatting.DARK_GRAY);
                    int tagWidth = font.width(tagComp);
                    guiGraphics.drawString(font, tagComp, left + width - tagWidth - 8, top + 8, 0xFF8892A4, true);
                }
            }
        }
    }

    // --- Scrollable Requests List ---

    public static class RequestsListWidget extends ObjectSelectionList<RequestsListWidget.RequestEntryRow> {

        private final Consumer<String> acceptHandler;
        private final Consumer<String> declineHandler;

        public RequestsListWidget(Minecraft minecraft, int width, int height, int y, int itemHeight,
                                  Consumer<String> acceptHandler, Consumer<String> declineHandler) {
            super(minecraft, width, height, y, itemHeight);
            this.acceptHandler = acceptHandler;
            this.declineHandler = declineHandler;
        }

        public void populate(List<AituApiClient.FriendRequestItem> requests) {
            this.clearEntries();
            if (requests != null) {
                for (AituApiClient.FriendRequestItem r : requests) {
                    this.addEntry(new RequestEntryRow(r, acceptHandler, declineHandler));
                }
            }
        }

        @Override
        public int getRowWidth() {
            return this.width - 12;
        }

        @Override
        protected void renderListBackground(GuiGraphics guiGraphics) {
            guiGraphics.fill(this.getX(), this.getY(), this.getX() + this.width, this.getY() + this.height, 0xFF0F131D);
            guiGraphics.renderOutline(this.getX(), this.getY(), this.width, this.height, 0xFF243248);
        }

        @Override
        protected void renderListSeparators(GuiGraphics guiGraphics) {
        }

        @Override
        public void renderWidget(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
            super.renderWidget(guiGraphics, mouseX, mouseY, partialTick);

            if (this.getItemCount() == 0) {
                Font font = Minecraft.getInstance().font;
                Component emptyNotice = Component.literal("No pending friend requests.").withStyle(ChatFormatting.GRAY);
                int textX = this.getX() + (this.width - font.width(emptyNotice)) / 2;
                guiGraphics.drawString(font, emptyNotice, textX, this.getY() + 40, 0xFFAAAAAA, true);
            }
        }

        public static class RequestEntryRow extends ObjectSelectionList.Entry<RequestEntryRow> {

            private final AituApiClient.FriendRequestItem request;
            private final Consumer<String> acceptHandler;
            private final Consumer<String> declineHandler;

            public RequestEntryRow(AituApiClient.FriendRequestItem request,
                                   Consumer<String> acceptHandler, Consumer<String> declineHandler) {
                this.request = request;
                this.acceptHandler = acceptHandler;
                this.declineHandler = declineHandler;
            }

            @Override
            public Component getNarration() {
                return Component.literal("Request from " + request.fromNickname());
            }

            @Override
            public boolean mouseClicked(double mouseX, double mouseY, int button) {
                if (button == 0) {
                    int acceptX = lastLeft + lastWidth - 110;
                    int declineX = lastLeft + lastWidth - 55;
                    int btnY = lastTop + 4;
                    int btnHeight = 20;

                    if (mouseX >= acceptX && mouseX <= acceptX + 50 && mouseY >= btnY && mouseY <= btnY + btnHeight) {
                        acceptHandler.accept(request.id());
                        return true;
                    }

                    if (mouseX >= declineX && mouseX <= declineX + 50 && mouseY >= btnY && mouseY <= btnY + btnHeight) {
                        declineHandler.accept(request.id());
                        return true;
                    }
                }
                return false;
            }

            private int lastLeft;
            private int lastTop;
            private int lastWidth;

            @Override
            public void render(GuiGraphics guiGraphics, int index, int top, int left, int width, int height,
                               int mouseX, int mouseY, boolean hovering, float partialTick) {
                this.lastLeft = left;
                this.lastTop = top;
                this.lastWidth = width;

                Font font = Minecraft.getInstance().font;

                int bgColor = hovering ? 0xFF1C273C : 0xFF141926;
                guiGraphics.fill(left, top + 1, left + width, top + height - 1, bgColor);
                guiGraphics.renderOutline(left, top + 1, width, height - 2, 0xFF26364D);

                // Sender Nickname & Tag
                Component fromComp = Component.literal(request.fromNickname()).withStyle(ChatFormatting.WHITE, ChatFormatting.BOLD);
                guiGraphics.drawString(font, fromComp, left + 8, top + 6, 0xFFFFFFFF, true);

                if (request.fromTag() != null && !request.fromTag().isBlank()) {
                    Component tagComp = Component.literal(request.fromTag()).withStyle(ChatFormatting.AQUA);
                    guiGraphics.drawString(font, tagComp, left + 8, top + 17, 0xFF55FFFF, true);
                }

                // Inline Actions: [ ✔ Accept ] and [ ✖ Decline ]
                int acceptX = left + width - 110;
                int declineX = left + width - 55;
                int btnY = top + 4;
                int btnW = 50;
                int btnH = 20;

                boolean acceptHovered = mouseX >= acceptX && mouseX <= acceptX + btnW && mouseY >= btnY && mouseY <= btnY + btnH;
                boolean declineHovered = mouseX >= declineX && mouseX <= declineX + btnW && mouseY >= btnY && mouseY <= btnY + btnH;

                // Accept Button
                int acceptBg = acceptHovered ? 0xFF1A4525 : 0xFF0E2A16;
                guiGraphics.fill(acceptX, btnY, acceptX + btnW, btnY + btnH, acceptBg);
                guiGraphics.renderOutline(acceptX, btnY, btnW, btnH, 0xFF00AA44);
                Component acceptText = Component.literal("✔ Accept").withStyle(ChatFormatting.GREEN);
                int accTextX = acceptX + (btnW - font.width(acceptText)) / 2;
                guiGraphics.drawString(font, acceptText, accTextX, btnY + 6, 0xFF00FF66, true);

                // Decline Button
                int declineBg = declineHovered ? 0xFF4A181C : 0xFF2A0E12;
                guiGraphics.fill(declineX, btnY, declineX + btnW, btnY + btnH, declineBg);
                guiGraphics.renderOutline(declineX, btnY, btnW, btnH, 0xFFAA2233);
                Component declineText = Component.literal("✖ Decline").withStyle(ChatFormatting.RED);
                int decTextX = declineX + (btnW - font.width(declineText)) / 2;
                guiGraphics.drawString(font, declineText, decTextX, btnY + 6, 0xFFFF5555, true);
            }
        }
    }
}
