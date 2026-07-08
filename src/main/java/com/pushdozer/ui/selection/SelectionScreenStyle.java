package com.pushdozer.ui.selection;

/**
 * 选择界面共享布局与配色常量。
 */
public final class SelectionScreenStyle {
    public static final int BUTTON_HEIGHT = 20;
    public static final int BUTTON_WIDTH = 80;
    public static final int BUTTON_SPACING = 10;
    public static final int BLOCK_SIZE = 20;
    public static final int BLOCK_SPACING = 4;
    public static final int SCROLL_BAR_WIDTH = 4;
    public static final int TOP_MARGIN = 35;
    public static final int BOTTOM_MARGIN = 10;
    public static final int SEARCH_BOTTOM_SPACING = 5;
    public static final int PAGE_NUMBER_SPACING = 10;
    public static final int CATEGORIES_PER_PAGE = 4;
    public static final int CATEGORY_BUTTON_HEIGHT = 20;
    public static final int CATEGORY_SPACING = 8;
    public static final int CATEGORY_TO_BLOCK_SPACING = 10;

    public static final int PANEL_BACKGROUND_COLOR = 0x40000000;
    public static final int BLOCK_BORDER_COLOR = 0xFF8B8B8B;
    public static final int BLOCK_BACKGROUND_COLOR = 0xFF373737;
    public static final int BLOCK_HOVER_COLOR = 0xFF555555;
    public static final int SCROLL_BAR_BACKGROUND_COLOR = 0x33FFFFFF;
    public static final int SCROLL_BAR_COLOR = 0xFFAAAAAA;
    public static final int SCROLL_BAR_HIGHLIGHT_COLOR = 0xFFFFFFFF;
    public static final int SCROLL_BAR_SHADOW_COLOR = 0xFF555555;
    public static final int SELECTED_BLOCK_BORDER_COLOR = 0xFFFFFFFF;
    public static final int SCREEN_BACKGROUND_COLOR = 0x80000000;
    public static final long TOOLTIP_DELAY_MS = 500;

    private SelectionScreenStyle() {
    }

    public static int searchWidth(int buttonCount) {
        return BUTTON_WIDTH * buttonCount + BUTTON_SPACING * (buttonCount - 1);
    }

    public static int categoryColumnWidth(int columnsPerRow) {
        return BLOCK_SIZE * columnsPerRow + BLOCK_SPACING * (columnsPerRow - 1) + SCROLL_BAR_WIDTH;
    }
}
