package com.pushdozer.ui.panels.brushgeometry;

import com.pushdozer.config.PushdozerConfig;
import com.pushdozer.ui.screens.PushdozerConfigScreen;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Geometry selection panel for displaying and selecting different geometry types.
 * <p>
 * This panel provides a visual interface allowing users to select from available geometry types.
 * The panel displays in the center of the screen and supports mouse interaction.
 */
public class GeometrySelectionPanel {
    // Color constants
    private static final int COLOR_PANEL_BG = 0xC0101010;     // Panel background
    private static final int COLOR_PANEL_BORDER = 0xFFFFFFFF; // Panel border
    private static final int COLOR_TITLE_BG = 0xE0303030;     // Title bar background
    private static final int COLOR_WHITE = 0xFFFFFF;          // White text
    // Removed unused selected color constant, actual highlight implemented by inline color in rendering logic

    // Size constants
    private static final int PANEL_WIDTH = 210; // Uniformly widened by 40
    private static final int PANEL_HEIGHT = 125;
    private static final int BUTTON_HEIGHT = 20;
    private static final int BUTTON_MARGIN = 5;
    private static final int TITLE_HEIGHT = 20;

    // Core fields
    private final PushdozerConfigScreen parent;
    private final PushdozerConfig config;
    private final Consumer<PushdozerConfig.GeometryType> onSelectionChanged;
    private final List<Element> widgets = new ArrayList<>();

    // State fields
    private boolean visible = false;
    private int panelLeft, panelTop;

    // Pre-calculated title position (performance optimization)
    private int titleX, titleY;
    private Text titleText;

    /**
     * Constructor, initializes geometry selection panel.
     * @param parent Parent configuration screen
     * @param config Configuration object
     * @param onSelectionChanged Callback function when selection changes
     */
    public GeometrySelectionPanel(PushdozerConfigScreen parent, PushdozerConfig config,
                                  Consumer<PushdozerConfig.GeometryType> onSelectionChanged) {
        this.parent = parent;
        this.config = config;
        this.onSelectionChanged = onSelectionChanged;

        // Calculate panel position (screen center)
        this.panelLeft = (parent.getScreenWidth() - PANEL_WIDTH) / 2;
        this.panelTop = (parent.getScreenHeight() - PANEL_HEIGHT) / 2;

        initializeWidgets();
        initializeTitlePosition();
    }

    /**
     * Initialize all button controls.
     * Adjusted to place two buttons per row.
     */
    private void initializeWidgets() {
        widgets.clear();

        int startY = panelTop + TITLE_HEIGHT + BUTTON_MARGIN; // Leave space for title and margin
        PushdozerConfig.GeometryType[] types = PushdozerConfig.GeometryType.values();

        // Calculate button width, considering left/right margins and spacing between buttons
        // (PANEL_WIDTH - 2 * BUTTON_MARGIN - BUTTON_MARGIN) / 2
        // = (total panel width - left margin - right margin - center button spacing) / 2
        int buttonWidth = (PANEL_WIDTH - (BUTTON_MARGIN * 3)) / 2;

        for (int i = 0; i < types.length; i++) {
            PushdozerConfig.GeometryType type = types[i];

            // Calculate current button's row and column
            int row = i / 2; // Integer division to get row number
            int col = i % 2; // Modulo to get column number (0 or 1)

            // Calculate button X coordinate
            int buttonX = panelLeft + BUTTON_MARGIN + (col * (buttonWidth + BUTTON_MARGIN));

            // Calculate button Y coordinate
            int buttonY = startY + (row * (BUTTON_HEIGHT + BUTTON_MARGIN));

            ButtonWidget button = ButtonWidget.builder(
                    getButtonText(type),
                    btn -> selectGeometry(type)
            ).dimensions(
                    buttonX,
                    buttonY,
                    buttonWidth,
                    BUTTON_HEIGHT
            ).build();

            widgets.add(button);
        }
    }

    /**
     * Get button text (with selected marker)
     */
    private Text getButtonText(PushdozerConfig.GeometryType type) {
        String prefix = (config.getGeometryType() == type) ? "☑ " : "";
        return Text.literal(prefix).append(type.getDisplayText());
    }

    /**
     * Update button states
     */
    private void updateButtonStates() {
        for (int i = 0; i < widgets.size(); i++) {
            Element widget = widgets.get(i);
            if (widget instanceof ButtonWidget button) {
                PushdozerConfig.GeometryType type = PushdozerConfig.GeometryType.values()[i];
                button.setMessage(getButtonText(type));
            }
        }
    }

    /**
     * Pre-calculate title position to optimize rendering performance.
     */
    private void initializeTitlePosition() {
        titleText = Text.translatable("pushdozer.panel.brush_shape_selection.title");

        if (parent.getClient() != null) {
            int titleWidth = parent.getClient().textRenderer.getWidth(titleText);
            titleX = panelLeft + (PANEL_WIDTH - titleWidth) / 2;
            titleY = panelTop + (TITLE_HEIGHT - parent.getClient().textRenderer.fontHeight) / 2;
        }
    }

    /**
     * Select specified geometry type.
     * @param type Geometry type to select
     */
    private void selectGeometry(PushdozerConfig.GeometryType type) {
        config.setGeometryType(type);
        updateButtonStates();
        if (onSelectionChanged != null) {
            onSelectionChanged.accept(type);
        }
        hide();
    }

    /**
     * Show panel.
     */
    public void show() {
        visible = true;
        // Recalculate position in case window size changed
        this.panelLeft = (parent.getScreenWidth() - PANEL_WIDTH) / 2;
        this.panelTop = (parent.getScreenHeight() - PANEL_HEIGHT) / 2;
        initializeWidgets(); // Reinitialize buttons to ensure correct positions
        initializeTitlePosition();
        updateButtonStates(); // Update button states
    }

    /**
     * Hide panel.
     */
    public void hide() {
        visible = false;
    }

    /**
     * Check if panel is visible.
     * @return Returns true if panel is visible
     */
    public boolean isVisible() {
        return visible;
    }

    /**
     * Render all panel components.
     * @param context Draw context
     * @param mouseX Mouse X coordinate
     * @param mouseY Mouse Y coordinate
     * @param delta Frame interval time
     */
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        if (!visible) return;

        renderBackground(context);
        renderButtons(context, mouseX, mouseY, delta);
        renderTitle(context);
    }

    /**
     * Render background overlay and panel background.
     * @param context Draw context
     */
    private void renderBackground(DrawContext context) {
        // 1. Draw semi-transparent background overlay (commented out in original code, keeping as is)
        // context.fill(0, 0, parent.getScreenWidth(), parent.getScreenHeight(), COLOR_OVERLAY);

        // 2. Draw panel background (semi-transparent)
        context.fill(panelLeft, panelTop, panelLeft + PANEL_WIDTH, panelTop + PANEL_HEIGHT, COLOR_PANEL_BG);

        // 3. Draw title bar background
        context.fill(panelLeft, panelTop, panelLeft + PANEL_WIDTH, panelTop + TITLE_HEIGHT, COLOR_TITLE_BG);

        // 4. Draw panel border (after title background to ensure border is not obscured)
        drawBorder(context, panelLeft, panelTop);
    }

    private static void drawBorder(DrawContext context, int x, int y) {
        // top
        context.fill(x, y, x + GeometrySelectionPanel.PANEL_WIDTH, y + 1, GeometrySelectionPanel.COLOR_PANEL_BORDER);
        // bottom
        context.fill(x, y + GeometrySelectionPanel.PANEL_HEIGHT - 1, x + GeometrySelectionPanel.PANEL_WIDTH, y + GeometrySelectionPanel.PANEL_HEIGHT, GeometrySelectionPanel.COLOR_PANEL_BORDER);
        // left
        context.fill(x, y, x + 1, y + GeometrySelectionPanel.PANEL_HEIGHT, GeometrySelectionPanel.COLOR_PANEL_BORDER);
        // right
        context.fill(x + GeometrySelectionPanel.PANEL_WIDTH - 1, y, x + GeometrySelectionPanel.PANEL_WIDTH, y + GeometrySelectionPanel.PANEL_HEIGHT, GeometrySelectionPanel.COLOR_PANEL_BORDER);
    }

    /**
     * Render title text.
     * @param context Draw context
     */
    private void renderTitle(DrawContext context) {
        if (titleText == null) {
            titleText = Text.translatable("pushdozer.panel.brush_shape_selection.title");
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
     * Render all buttons.
     * @param context Draw context
     * @param mouseX Mouse X coordinate
     * @param mouseY Mouse Y coordinate
     * @param delta Frame interval time
     */
    private void renderButtons(DrawContext context, int mouseX, int mouseY, float delta) {
        // First reset focus state of all buttons
        for (Element widget : widgets) {
            if (widget instanceof ButtonWidget button) {
                button.setFocused(false);
            }
        }

        // Then set focus state of currently selected button
        PushdozerConfig.GeometryType currentType = config.getGeometryType();

        PushdozerConfig.GeometryType[] types = PushdozerConfig.GeometryType.values();
        for (int i = 0; i < widgets.size() && i < types.length; i++) {
            Element widget = widgets.get(i);
            if (widget instanceof ButtonWidget button) {
                button.setFocused(types[i] == currentType);
            }
        }

        // Finally render all buttons (let buttons handle text rendering themselves)
        for (Element widget : widgets) {
            if (widget instanceof net.minecraft.client.gui.Drawable) {
                ((net.minecraft.client.gui.Drawable) widget).render(context, mouseX, mouseY, delta);
            }
        }
    }

    /**
     * Handle mouse click events.
     */
    public boolean mouseClicked(Click click) {
        if (!visible) return false;

        double mouseX = click.x();
        double mouseY = click.y();

        // Check if clicked outside panel
        if (mouseX < panelLeft || mouseX > panelLeft + PANEL_WIDTH ||
                mouseY < panelTop || mouseY > panelTop + PANEL_HEIGHT) {
            hide();
            return true;
        }

        // Use event delegation to handle button clicks
        handleMouseEvent(clickable -> clickable.mouseClicked(click, false));
        return true;
    }

    /**
     * Handle mouse drag events.
     */
    public boolean mouseDragged(Click click, double deltaX, double deltaY) {
        if (!visible) return false;
        return handleMouseEvent(clickable -> clickable.mouseDragged(click, deltaX, deltaY));
    }

    /**
     * Handle mouse release events.
     */
    public boolean mouseReleased(Click click) {
        if (!visible) return false;
        return handleMouseEvent(clickable -> clickable.mouseReleased(click));
    }

    /**
     * Event delegation mechanism, unified handling of mouse events.
     * @param eventHandler Event handler function
     * @return Returns true if event was handled
     */
    private boolean handleMouseEvent(Function<ClickableWidget, Boolean> eventHandler) {
        for (Element widget : widgets) {
            if (widget instanceof ClickableWidget clickable) {
                if (eventHandler.apply(clickable)) {
                    return true;
                }
            }
        }
        return false;
    }
}
