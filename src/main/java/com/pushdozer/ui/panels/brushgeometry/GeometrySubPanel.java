package com.pushdozer.ui.panels.brushgeometry;

import com.pushdozer.config.PushdozerConfig;
import com.pushdozer.ui.screens.PushdozerConfigScreen;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.MathHelper;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * GeometrySubPanel is the base class for all geometry shape sub-panels, managing common configuration and components.
 * Specific geometric shapes (such as Box, Sphere, etc.) will inherit from this class and implement specific logic.
 */
public abstract class GeometrySubPanel {
    protected PushdozerConfigScreen parent;        // Parent screen, used for interaction and layout
    protected PushdozerConfig config;              // Configuration object, stores various settings
    protected List<Element> widgets = new ArrayList<>(); // Stores all components in the panel
    protected ButtonWidget confirmButton;          // Confirm button
    protected TextRenderer textRenderer;           // Text renderer field

    // Layout constants
    protected static final int PANEL_WIDTH = 210;           // Panel width (uniformly widened by 40)
    protected static final int TITLE_HEIGHT = 20;           // Title height constant
    // Slider and button widths calculated dynamically based on panel width and margins
    protected static final int SLIDER_HEIGHT = 20;          // Slider height
    protected static final int WIDGET_MARGIN_VERTICAL = 5;  // Vertical spacing between widgets
    protected static final int WIDGET_MARGIN_HORIZONTAL = 5; // Widget horizontal margin
    protected static final int CONFIRM_BUTTON_HEIGHT = 20;  // Confirm button height
    protected static final int CONFIRM_BUTTON_MARGIN = 5;   // Confirm button margin
    protected int panelLeft, panelTop;              // Panel top-left corner coordinates
    private boolean visible = false;                // Whether the panel is visible

    /**
     * Constructor, initializes parent screen and configuration object.
     *
     * @param parent  Parent screen object
     * @param config  Configuration object
     */
    public GeometrySubPanel(PushdozerConfigScreen parent, PushdozerConfig config) {
        this.parent = parent;
        this.config = config;
        this.textRenderer = MinecraftClient.getInstance().textRenderer;
    }

    /**
     * Initialize the panel, including calculating position and creating components.
     * This method is called when the screen is first opened and each time the window size changes.
     */
    public void init() {
        this.widgets.clear();

        this.panelLeft = (parent.getScreenWidth() - PANEL_WIDTH) / 2;
        this.panelTop = (parent.getScreenHeight() - estimatePanelHeight()) / 2;

        this.initPanel();
        this.initConfirmButton();

        this.panelTop = (parent.getScreenHeight() - getPanelHeight()) / 2;
        updateWidgetPositions();
    }

    private int estimatePanelHeight() {
        return TITLE_HEIGHT + 3 * (SLIDER_HEIGHT + WIDGET_MARGIN_VERTICAL) + CONFIRM_BUTTON_HEIGHT + CONFIRM_BUTTON_MARGIN;
    }

    /**
     * Get panel height, dynamically calculated based on widget count
     */
    protected int getPanelHeight() {
        // Calculate dynamic height: title height + content total height + confirm button height + confirm button bottom margin
        int contentHeight = calculateContentHeight();
        return TITLE_HEIGHT + contentHeight + CONFIRM_BUTTON_HEIGHT + CONFIRM_BUTTON_MARGIN;
    }

    /**
     * Calculate content area height
     */
    protected int calculateContentHeight() {
        int widgetCount = widgets.size() - 1; // Subtract confirm button
        if (widgetCount <= 0) {
            return WIDGET_MARGIN_VERTICAL * 2; // Keep at least top and bottom margins
        }

        // Calculate total widget height:
        // - Widget height itself: widgetCount * SLIDER_HEIGHT
        // - Spacing between widgets: (widgetCount - 1) * WIDGET_MARGIN_VERTICAL
        // - Spacing between first widget and title: WIDGET_MARGIN_VERTICAL
        // - Spacing between last widget and confirm button: WIDGET_MARGIN_VERTICAL
        return widgetCount * SLIDER_HEIGHT + (widgetCount - 1) * WIDGET_MARGIN_VERTICAL + WIDGET_MARGIN_VERTICAL + WIDGET_MARGIN_VERTICAL;
    }

    /**
     * Update widget positions
     */
    protected void updateWidgetPositions() {
        int contentX = panelLeft + WIDGET_MARGIN_HORIZONTAL;
        int contentWidth = PANEL_WIDTH - (2 * WIDGET_MARGIN_HORIZONTAL);
        int currentY = panelTop + TITLE_HEIGHT + WIDGET_MARGIN_VERTICAL;

        for (Element widget : widgets) {
            if (widget == confirmButton) {
                continue;
            }
            if (widget instanceof net.minecraft.client.gui.widget.ClickableWidget clickableWidget) {
                clickableWidget.setPosition(contentX, currentY);
                clickableWidget.setWidth(contentWidth);
                currentY += SLIDER_HEIGHT + WIDGET_MARGIN_VERTICAL;
            }
        }

        if (confirmButton != null) {
            int confirmButtonY = panelTop + getPanelHeight() - CONFIRM_BUTTON_HEIGHT - CONFIRM_BUTTON_MARGIN;
            confirmButton.setPosition(contentX, confirmButtonY);
            confirmButton.setWidth(contentWidth);
        }
    }

    /**
     * Initialize panel method, implemented by subclasses for specific component addition logic.
     */
    public abstract void initPanel();

    /**
     * Render panel method, implemented by subclasses for specific rendering logic.
     *
     * @param context  Render context
     * @param mouseX   Mouse X coordinate
     * @param mouseY   Mouse Y coordinate
     * @param delta    Render delta time
     */
    public abstract void render(DrawContext context, int mouseX, int mouseY, float delta);

    /**
     * Save configuration method, implemented by subclasses for specific save logic.
     */
    public abstract void saveConfig();

    protected void persistPanelConfig() {
        config.save();
        parent.showErrorMessage(Text.translatable("pushdozer.config.saved").getString());
    }

    /**
     * CustomSliderWidget is a custom slider component that extends SliderWidget.
     * It is used to display and adjust specific numeric parameters (such as length, width, height, radius, etc.) in the interface.
     */
    protected static class CustomSliderWidget extends SliderWidget {
        private final String label;                    // Slider label, e.g., "Length"
        private final int min;                         // Slider minimum value
        private final int max;                         // Slider maximum value
        private final Consumer<Integer> onValueChange; // Callback when value changes

        /**
         * Constructor, initializes slider properties.
         *
         * @param x              Slider X coordinate
         * @param y              Slider Y coordinate
         * @param width          Slider width
         * @param height         Slider height
         * @param label          Slider label
         * @param min            Slider minimum value
         * @param max            Slider maximum value
         * @param initialValue   Slider initial value
         * @param onValueChange  Callback when value changes
         */
        public CustomSliderWidget(int x, int y, int width, int height, String label, int min, int max,
                                int initialValue, Consumer<Integer> onValueChange) {
            super(x, y, width, height, Text.empty(), (double) (initialValue - min) / (max - min));
            this.label = label;
            this.min = min;
            this.max = max;
            this.onValueChange = onValueChange;
            updateMessage(); // First call to set initial text
        }

        /**
         * Update message displayed on slider, showing current value.
         */
        @Override
        protected void updateMessage() {
            this.setMessage(Text.of(this.label + ": " + this.getIntValue()));
        }

        /**
         * Called when slider value changes, used to update display and execute related logic.
         */
        @Override
        protected void applyValue() {
            if (this.onValueChange != null) {
                this.onValueChange.accept(getIntValue()); // Execute callback and pass current value
            }
        }

        /**
         * Get current integer value of slider, calculated based on slider progress.
         *
         * @return Current integer value
         */
        public int getIntValue() {
            return (int) Math.round(MathHelper.lerp(this.value, this.min, this.max));
        }
    }

    /**
     * Add slider
     * @param index Slider index
     * @param label Slider label
     * @param value Initial value
     * @return Created slider
     */
    protected CustomSliderWidget addSlider(int index, Text label, int value) {
        // Calculate slider position: below title + index * (slider height + spacing) + spacing
        int y = panelTop + TITLE_HEIGHT + WIDGET_MARGIN_VERTICAL + (index * (SLIDER_HEIGHT + WIDGET_MARGIN_VERTICAL));
        CustomSliderWidget slider = new CustomSliderWidget(
            panelLeft + WIDGET_MARGIN_HORIZONTAL,
            y,
            PANEL_WIDTH - (2 * WIDGET_MARGIN_HORIZONTAL),
            SLIDER_HEIGHT,
            label.getString(),
            PushdozerConfig.MIN_BRUSH_RADIUS,
            PushdozerConfig.MAX_BRUSH_RADIUS,
            value,
            (newValue) -> this.updatePreview() // Pass a lambda as callback
        );
        widgets.add(slider);
        return slider;
    }

    /**
     * Get current value of slider.
     *
     * @param slider Slider to get value from
     * @return Slider's current integer value
     */
    protected int getSliderValue(CustomSliderWidget slider) {
        return slider.getIntValue();
    }

    /**
     * Show the panel.
     */
    public void show() {
        visible = true;
        init();
    }

    /**
     * Hide the panel.
     */
    public void hide() {
        visible = false;
    }

    /**
     * Check if the panel is visible.
     *
     * @return true if the panel is visible; false otherwise
     */
    public boolean isVisible() {
        return visible;
    }

    /**
     * Initialize confirm button and add it to the panel.
     */
    protected void initConfirmButton() {
        // Calculate confirm button position
        int confirmButtonY = panelTop + getPanelHeight() - CONFIRM_BUTTON_HEIGHT - CONFIRM_BUTTON_MARGIN;

        confirmButton = ButtonWidget.builder(
            Text.translatable("pushdozer.button.done"),
            button -> {
                saveConfig();
                closeSubPanel();
            })
            .dimensions(
                panelLeft + WIDGET_MARGIN_HORIZONTAL,
                confirmButtonY,
                PANEL_WIDTH - (2 * WIDGET_MARGIN_HORIZONTAL),
                CONFIRM_BUTTON_HEIGHT)
            .build();
        widgets.add(confirmButton);
    }

    /**
     * Close sub-panel, hide current panel and notify parent screen to hide sub-panel.
     */
    protected void closeSubPanel() {
        hide();                   // Hide current panel
        parent.hideSubPanel();    // Notify parent screen to hide sub-panel
        parent.showMainPanel();   // Show main interface
    }

    /**
     * Get all components in the panel.
     *
     * @return Component list
     */
    public List<Element> getWidgets() {
        return widgets;
    }

    /**
     * Handle mouse click events.
     */
    public boolean mouseClicked(Click click) {
        for (Element widget : widgets) {
            if (widget.mouseClicked(click, false)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Handle mouse drag events.
     */
    public boolean mouseDragged(Click click, double deltaX, double deltaY) {
        for (Element widget : widgets) {
            if (widget.mouseDragged(click, deltaX, deltaY)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Render panel background and border (optimized rendering order)
     * @param context Draw context
     */
    protected void renderPanelBackground(DrawContext context) {
        // 1. Draw panel background (semi-transparent)
        context.fill(panelLeft, panelTop, panelLeft + PANEL_WIDTH, panelTop + getPanelHeight(), 0xC0101010);

        // 2. Draw title bar background
        context.fill(panelLeft, panelTop, panelLeft + PANEL_WIDTH, panelTop + TITLE_HEIGHT, 0xE0303030);

        // 3. Draw panel border (after title background to ensure border is not obscured)
        drawBorder(context, panelLeft, panelTop, getPanelHeight());
    }

    protected static void drawBorder(DrawContext context, int x, int y, int height) {
        // top
        context.fill(x, y, x + GeometrySubPanel.PANEL_WIDTH, y + 1, -1);
        // bottom
        context.fill(x, y + height - 1, x + GeometrySubPanel.PANEL_WIDTH, y + height, -1);
        // left
        context.fill(x, y, x + 1, y + height, -1);
        // right
        context.fill(x + GeometrySubPanel.PANEL_WIDTH - 1, y, x + GeometrySubPanel.PANEL_WIDTH, y + height, -1);
    }

    /**
     * Render title text
     * @param context Draw context
     * @param title Title text
     */
    protected void renderTitle(DrawContext context, Text title) {
        TextRenderer renderer = parent != null ? parent.resolveTextRenderer() : textRenderer;
        if (renderer == null) {
            return;
        }

        Text displayTitle = title.copy().formatted(Formatting.BOLD, Formatting.YELLOW);
        int titleWidth = renderer.getWidth(displayTitle);
        int x = panelLeft + (PANEL_WIDTH - titleWidth) / 2;
        int y = panelTop + (TITLE_HEIGHT - renderer.fontHeight) / 2;
        context.drawText(renderer, displayTitle, x, y, 0xFFFFFFFF, true);
    }

    /**
     * Abstract method for updating preview, implemented by subclasses for specific preview logic
     */
    protected abstract void updatePreview();
}