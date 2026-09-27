package kz.aitu.auth.gui.tab;

import kz.aitu.auth.gui.AituHubScreen;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * Interface representing a modular tab panel inside AituHubScreen.
 */
public interface AituTab {

    Component getTitle();

    String getIcon();

    void init(AituHubScreen parent, int contentX, int contentY, int contentWidth, int contentHeight);

    void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick);

    default void tick() {
    }

    default void onRemoved() {
    }
}
