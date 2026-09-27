package kz.aitu.auth.gui;

import kz.aitu.auth.gui.tab.AituTab;
import kz.aitu.auth.gui.tab.FriendsTab;
import kz.aitu.auth.gui.tab.ProfileTab;
import kz.aitu.auth.gui.tab.SettingsTab;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Full-featured standalone Ecosystem Hub for AITU Gaming:
 * - Header Bar with Vanilla Tab Buttons ("👤 Profile", "👥 Friends (Beta)", "⚙ Settings")
 * - Opaque solid backdrop (0xE0101014) to prevent GUI blur shader degradation
 * - Dynamic tab container supporting sub-panel widgets and scrollable lists
 */
public class AituHubScreen extends Screen {

    private final Screen parentScreen;
    private final List<AituTab> tabs = new ArrayList<>();
    private final List<AbstractWidget> activeTabWidgets = new ArrayList<>();
    private final List<Button> tabButtons = new ArrayList<>();

    private int currentTabIndex = 0;

    public AituHubScreen(Screen parentScreen) {
        super(Component.literal("AITU Gaming Hub"));
        this.parentScreen = parentScreen;

        // Register tabs with exact labels
        this.tabs.add(new ProfileTab());
        this.tabs.add(new FriendsTab());
        this.tabs.add(new SettingsTab());
    }

    @Override
    protected void init() {
        super.init();
        this.clearWidgets();
        this.activeTabWidgets.clear();
        this.tabButtons.clear();

        int centerX = this.width / 2;

        // 1. Top Tab Navigation Bar (placed at Y=28, clearly below the main title at Y=10)
        int tabButtonWidth = Math.min(115, (this.width - 40) / this.tabs.size());
        int tabSpacing = 6;
        int totalBarWidth = (this.tabs.size() * tabButtonWidth) + ((this.tabs.size() - 1) * tabSpacing);
        int barStartX = centerX - (totalBarWidth / 2);
        int tabY = 26;

        for (int i = 0; i < this.tabs.size(); i++) {
            final int tabIndex = i;
            AituTab tab = this.tabs.get(i);
            int x = barStartX + (i * (tabButtonWidth + tabSpacing));

            Component label = Component.literal(tab.getIcon() + " ").append(tab.getTitle());
            Button tabBtn = Button.builder(label, btn -> setActiveTab(tabIndex))
                    .bounds(x, tabY, tabButtonWidth, 20)
                    .build();

            this.tabButtons.add(tabBtn);
            this.addRenderableWidget(tabBtn);
        }

        // 2. Bottom Close / Back Button
        int bottomY = this.height - 26;
        this.addRenderableWidget(
                Button.builder(CommonComponents.GUI_BACK, btn -> onClose())
                        .bounds(centerX - 80, bottomY, 160, 20)
                        .build()
        );

        // 3. Initialize the currently active tab
        loadTabContent(this.currentTabIndex);
    }

    /**
     * Switches the active tab index and re-initializes its sub-panel widgets.
     */
    public void setActiveTab(int index) {
        if (index < 0 || index >= this.tabs.size()) return;
        if (this.currentTabIndex != index) {
            this.tabs.get(this.currentTabIndex).onRemoved();
        }
        this.currentTabIndex = index;
        loadTabContent(index);
    }

    /**
     * Reloads the content of the currently active tab without changing tabs.
     */
    public void refreshActiveTab() {
        loadTabContent(this.currentTabIndex);
    }

    private void loadTabContent(int index) {
        // Clear previous sub-panel widgets
        for (AbstractWidget widget : this.activeTabWidgets) {
            this.removeWidget(widget);
        }
        this.activeTabWidgets.clear();

        // Update active tab button visuals
        for (int i = 0; i < this.tabButtons.size(); i++) {
            Button btn = this.tabButtons.get(i);
            AituTab tab = this.tabs.get(i);
            boolean isActive = (i == index);
            Component formattedLabel = Component.literal(tab.getIcon() + " ")
                    .append(tab.getTitle().copy().withStyle(isActive ? ChatFormatting.AQUA : ChatFormatting.GRAY, ChatFormatting.BOLD));
            btn.setMessage(formattedLabel);
        }

        // Content panel bounds (below the header divider at Y=52)
        int contentX = 20;
        int contentY = 56;
        int contentWidth = this.width - 40;
        int contentHeight = this.height - contentY - 32;

        AituTab activeTab = this.tabs.get(index);
        activeTab.init(this, contentX, contentY, contentWidth, contentHeight);
    }

    /**
     * Registers a widget owned by the active tab so it is rendered and receives input.
     */
    public void registerTabWidget(AbstractWidget widget) {
        this.activeTabWidgets.add(widget);
        this.addRenderableWidget(widget);
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        // 1. Render opaque solid backdrop covering the entire screen to prevent GUI blur shader degradation
        guiGraphics.fill(0, 0, this.width, this.height, 0xE0101014);

        Font font = this.font;
        int centerX = this.width / 2;

        // Top Header Backdrop Banner (clean opaque dark modal frame)
        guiGraphics.fill(0, 0, this.width, 52, 0xFF0D111A);
        guiGraphics.fill(0, 52, this.width, 53, 0xFF2A3648);

        // Bottom Footer Banner
        guiGraphics.fill(0, this.height - 32, this.width, this.height, 0xFF0D111A);
        guiGraphics.fill(0, this.height - 32, this.width, this.height - 31, 0xFF2A3648);

        // Top App Header Title (crisp high-contrast gold with drop-shadow and full 0xFF alpha)
        Component titleComponent = Component.literal("AITU GAMING HUB").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD);
        int titleWidth = font.width(titleComponent);
        guiGraphics.drawString(font, titleComponent, centerX - (titleWidth / 2), 9, 0xFFFFD700, true);

        // Render Active Tab Content
        if (this.currentTabIndex >= 0 && this.currentTabIndex < this.tabs.size()) {
            this.tabs.get(this.currentTabIndex).render(guiGraphics, mouseX, mouseY, partialTick);
        }

        // Render widgets (tab buttons, sub-panel inputs, back button)
        super.render(guiGraphics, mouseX, mouseY, partialTick);

        // Render Active Tab Indicator Underline
        if (this.currentTabIndex >= 0 && this.currentTabIndex < this.tabButtons.size()) {
            Button activeBtn = this.tabButtons.get(this.currentTabIndex);
            int underlineY = activeBtn.getY() + activeBtn.getHeight() + 2;
            guiGraphics.fill(activeBtn.getX() + 4, underlineY, activeBtn.getX() + activeBtn.getWidth() - 4, underlineY + 2, 0xFF00E676);
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (this.currentTabIndex >= 0 && this.currentTabIndex < this.tabs.size()) {
            this.tabs.get(this.currentTabIndex).tick();
        }
    }

    @Override
    public void onClose() {
        if (this.minecraft != null) {
            this.minecraft.setScreen(this.parentScreen);
        }
    }
}
