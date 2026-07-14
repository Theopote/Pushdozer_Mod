package com.pushdozer.ui.panels.workmode;

import com.pushdozer.config.PushdozerConfig;
import com.pushdozer.ui.screens.PushdozerConfigScreen;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.ArrayList;
import java.util.List;

/**
 * Work mode configuration panel base class
 * Provides configuration interface for different work modes
 */
public abstract class WorkModeConfigPanel {
    // Color constants
    protected static final int COLOR_PANEL_BG = 0xC0101010;     // Panel background
    protected static final int COLOR_PANEL_BORDER = 0xFFFFFFFF; // Panel border
    protected static final int COLOR_TITLE_BG = 0xE0303030;     // Title bar background
    protected static final int COLOR_WHITE = 0xFFFFFF;          // White text

    // Size constants
    protected static final int PANEL_WIDTH = 210; // Uniformly widened by 40
    protected static final int PANEL_HEIGHT = 140;
    protected static final int TITLE_HEIGHT = 20;
    protected static final int WIDGET_HEIGHT = 20;
    protected static final int WIDGET_MARGIN = 5;
    protected static final int CONFIRM_BUTTON_HEIGHT = 20;
    protected static final int CONFIRM_BUTTON_MARGIN = 5;

    // Core fields
    protected final PushdozerConfigScreen parent;
    protected final PushdozerConfig config;
    protected final List<Element> widgets = new ArrayList<>();
    protected ButtonWidget confirmButton;          // Confirm button

    // State fields
    protected boolean visible = false;
    protected int panelLeft, panelTop;

    // Pre-calculated title position (performance optimization)
    protected int titleX, titleY;
    protected Text titleText;

    /**
     * Constructor
     */
    public WorkModeConfigPanel(PushdozerConfigScreen parent, PushdozerConfig config) {
        this.parent = parent;
        this.config = config;

        // First calculate panel position (screen center)
        this.panelLeft = (parent.getScreenWidth() - PANEL_WIDTH) / 2;
        // Temporarily calculate panel top position, will recalculate later
        this.panelTop = (parent.getScreenHeight() - PANEL_HEIGHT) / 2;

        initializeTitlePosition();
        initializeWidgets();
        initializeConfirmButton();

        // Recalculate panel position using actual height
        this.panelTop = (parent.getScreenHeight() - getPanelHeight()) / 2;

        // Recalculate all widget positions to ensure correct placement
        recalculateAllWidgetPositions();
    }

    /**
     * Initialize widgets, implemented by subclasses for specific logic
     */
    protected abstract void initializeWidgets();

    /**
     * Get panel title, implemented by subclasses
     */
    protected abstract Text getTitleText();

    /**
     * Get panel height, subclasses can override to provide dynamic height
     */
    protected int getPanelHeight() {
        // Calculate dynamic height: confirm button position + confirm button height + confirm button bottom margin
        int confirmButtonY = calculateConfirmButtonY();
        return confirmButtonY + CONFIRM_BUTTON_HEIGHT + CONFIRM_BUTTON_MARGIN - panelTop;
    }

    /**
     * Calculate confirm button Y position
     */
    protected int calculateConfirmButtonY() {
        // Calculate content widget count (excluding confirm button)
        int contentWidgetCount = widgets.size();
        if (confirmButton != null && widgets.contains(confirmButton)) {
            contentWidgetCount = widgets.size() - 1; // Subtract confirm button
        }

        if (contentWidgetCount > 0) {
            // Calculate last content widget position
            int lastWidgetY = panelTop + TITLE_HEIGHT + WIDGET_MARGIN + (contentWidgetCount - 1) * (WIDGET_HEIGHT + WIDGET_MARGIN) + WIDGET_HEIGHT;
            // Confirm button position: 5 pixels below last widget bottom
            return lastWidgetY + CONFIRM_BUTTON_MARGIN;
        } else {
            // When no content widgets, confirm button is directly below title bar
            return panelTop + TITLE_HEIGHT + WIDGET_MARGIN + CONFIRM_BUTTON_MARGIN;
        }
    }

    /**
     * Initialize confirm button
     */
    protected void initializeConfirmButton() {
        // Calculate confirm button position
        int confirmButtonY = calculateConfirmButtonY();

        confirmButton = ButtonWidget.builder(
            Text.translatable("pushdozer.button.done"),
            button -> {
                saveConfig();
                closeSubPanel();
            })
            .dimensions(
                panelLeft + WIDGET_MARGIN,
                confirmButtonY,
                PANEL_WIDTH - (WIDGET_MARGIN * 2),
                CONFIRM_BUTTON_HEIGHT)
            .build();
        widgets.add(confirmButton);
    }

    /**
     * Save configuration, implemented by subclasses
     */
    public abstract void saveConfig();

    /**
     * Write current configuration to disk and display save success message (IO failures are handled in {@link PushdozerConfig#save()}).
     */
    protected void persistPanelConfig() {
        config.save();
        parent.showErrorMessage(Text.translatable("pushdozer.config.saved").getString());
    }

    /**
     * Close sub-panel
     */
    protected void closeSubPanel() {
        hide();                   // Hide current panel
        parent.showMainPanel();   // Show main interface
    }

    /**
     * Pre-calculate title position to optimize rendering performance
     */
    protected void initializeTitlePosition() {
        titleText = getTitleText();
        TextRenderer textRenderer = parent.resolveTextRenderer();
        if (textRenderer != null) {
            titleX = panelLeft + (PANEL_WIDTH - textRenderer.getWidth(titleText)) / 2;
            titleY = panelTop + (TITLE_HEIGHT - textRenderer.fontHeight) / 2;
        }
    }

    /**
     * Show panel
     */
    public void show() {
        visible = true;

        // First calculate panel position
        this.panelLeft = (parent.getScreenWidth() - PANEL_WIDTH) / 2;

        // Clear widget list to ensure accurate height calculation
        widgets.clear();
        confirmButton = null;

        // First initialize widgets, but not including confirm button
        initializeWidgets();

        // Then initialize confirm button
        initializeConfirmButton();

        // Now can correctly calculate panel height and position
        this.panelTop = (parent.getScreenHeight() - getPanelHeight()) / 2;

        // Recalculate all widget positions, since panelTop is now determined
        recalculateAllWidgetPositions();
    }

    /**
     * Recalculate all widget positions
     */
    protected void recalculateAllWidgetPositions() {
        // Recalculate title position
        initializeTitlePosition();

        // Recalculate confirm button position
        if (confirmButton != null) {
            int confirmButtonY = calculateConfirmButtonY();
            int confirmButtonX = panelLeft + WIDGET_MARGIN;
            int confirmButtonWidth = PANEL_WIDTH - (WIDGET_MARGIN * 2);
            confirmButton.setPosition(confirmButtonX, confirmButtonY);
            confirmButton.setWidth(confirmButtonWidth);
        }

        // Recalculate all content widget positions
        recalculateContentWidgetPositions();
    }

    /**
     * Recalculate content widget positions
     */
    protected void recalculateContentWidgetPositions() {
        int currentY = panelTop + TITLE_HEIGHT + WIDGET_MARGIN;

        for (Element widget : widgets) {
            if (widget != confirmButton) {
                if (widget instanceof net.minecraft.client.gui.widget.ClickableWidget clickableWidget) {
                    clickableWidget.setPosition(clickableWidget.getX(), currentY);
                    currentY += WIDGET_HEIGHT + WIDGET_MARGIN;
                }
            }
        }
    }

    /**
     * Hide panel
     */
    public void hide() {
        visible = false;
    }

    /**
     * Check if panel is visible
     */
    public boolean isVisible() {
        return visible;
    }

    /**
     * Render all panel components
     */
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        if (!visible) return;

        renderBackground(context);
        renderWidgets(context, mouseX, mouseY, delta);
        renderTitle(context);
    }

    /**
     * Render background
     */
    protected void renderBackground(DrawContext context) {
        // Draw panel background (semi-transparent)
        context.fill(panelLeft, panelTop, panelLeft + PANEL_WIDTH, panelTop + getPanelHeight(), COLOR_PANEL_BG);

        // Draw title bar background
        context.fill(panelLeft, panelTop, panelLeft + PANEL_WIDTH, panelTop + TITLE_HEIGHT, COLOR_TITLE_BG);

        // Draw panel border
        drawBorder(context, panelLeft, panelTop, getPanelHeight());
    }

    protected static void drawBorder(DrawContext context, int x, int y, int height) {
        // top
        context.fill(x, y, x + WorkModeConfigPanel.PANEL_WIDTH, y + 1, WorkModeConfigPanel.COLOR_PANEL_BORDER);
        // bottom
        context.fill(x, y + height - 1, x + WorkModeConfigPanel.PANEL_WIDTH, y + height, WorkModeConfigPanel.COLOR_PANEL_BORDER);
        // left
        context.fill(x, y, x + 1, y + height, WorkModeConfigPanel.COLOR_PANEL_BORDER);
        // right
        context.fill(x + WorkModeConfigPanel.PANEL_WIDTH - 1, y, x + WorkModeConfigPanel.PANEL_WIDTH, y + height, WorkModeConfigPanel.COLOR_PANEL_BORDER);
    }

    /**
     * Render title text
     */
    protected void renderTitle(DrawContext context) {
        if (titleText == null) {
            titleText = getTitleText();
        }
        TextRenderer textRenderer = parent.resolveTextRenderer();
        if (textRenderer == null) {
            return;
        }

        Text displayTitle = titleText.copy().formatted(Formatting.BOLD, Formatting.YELLOW);
        int titleWidth = textRenderer.getWidth(displayTitle);
        int x = panelLeft + (PANEL_WIDTH - titleWidth) / 2;
        int y = panelTop + (TITLE_HEIGHT - textRenderer.fontHeight) / 2;
        context.drawText(textRenderer, displayTitle, x, y, 0xFFFFFFFF, true);
    }

    /**
     * Render all widgets
     */
    protected void renderWidgets(DrawContext context, int mouseX, int mouseY, float delta) {
        for (Element widget : widgets) {
            if (widget instanceof net.minecraft.client.gui.Drawable) {
                ((net.minecraft.client.gui.Drawable) widget).render(context, mouseX, mouseY, delta);
            }
        }
    }

    /**
     * Handle mouse click events
     */
    public boolean mouseClicked(Click click) {
        if (!visible) return false;

        double mouseX = click.x();
        double mouseY = click.y();

        // Check if click is within panel
        if (mouseX >= panelLeft && mouseX <= panelLeft + PANEL_WIDTH &&
            mouseY >= panelTop && mouseY <= panelTop + getPanelHeight()) {

            // Pass event to widgets
            for (Element widget : widgets) {
                if (widget instanceof net.minecraft.client.gui.widget.ClickableWidget clickableWidget) {
                    if (clickableWidget.mouseClicked(click, false)) {
                        return true;
                    }
                }
            }
            return true;
        }

        // Click outside panel, close panel
        hide();
        parent.showMainPanel();
        return true;
    }

    /**
     * Handle mouse drag events
     */
    public boolean mouseDragged(Click click, double deltaX, double deltaY) {
        if (!visible) return false;

        double mouseX = click.x();
        double mouseY = click.y();
        for (Element widget : widgets) {
            if (widget instanceof net.minecraft.client.gui.widget.ClickableWidget clickableWidget) {
                if (clickableWidget.isMouseOver(mouseX, mouseY)) {
                    if (clickableWidget.mouseDragged(click, deltaX, deltaY)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /**
     * Handle mouse release events
     */
    public boolean mouseReleased(Click click) {
        if (!visible) return false;

        double mouseX = click.x();
        double mouseY = click.y();
        for (Element widget : widgets) {
            if (widget instanceof net.minecraft.client.gui.widget.ClickableWidget clickableWidget) {
                if (clickableWidget.isMouseOver(mouseX, mouseY)) {
                    if (clickableWidget.mouseReleased(click)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /**
     * Get widget list for external access
     */
    public List<Element> getWidgets() {
        return widgets;
    }

    @FunctionalInterface
    protected interface SliderApplyValue {
        void apply(SliderWidget slider);
    }
} 