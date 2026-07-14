package com.pushdozer.ui.panels;

import com.pushdozer.PushdozerMod;
import com.pushdozer.config.PushdozerConfig;
import com.pushdozer.ui.screens.PushdozerConfigScreen;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.Drawable;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.ArrayList;
import java.util.List;

/**
 * Height configuration panel for configuring height limit mode.
 * <p>
 * This panel provides a visual interface allowing users to select height limit mode.
 * The panel displays in the center of the screen and supports mouse interaction.
 */
public class HeightConfigPanel {
    // Color constants
    private static final int COLOR_PANEL_BG = 0xC0101010;     // Panel background
    private static final int COLOR_PANEL_BORDER = 0xFFFFFFFF; // Panel border
    private static final int COLOR_TITLE_BG = 0xE0303030;     // Title bar background
    private static final int COLOR_WHITE = 0xFFFFFF;          // White text
    // Removed unused selected color constant, actual highlight implemented by inline color in rendering logic

    // Size constants
    private static final int PANEL_WIDTH = 250; // Uniformly widened by 40
    private static final int PANEL_HEIGHT = 125;
    private static final int BUTTON_HEIGHT = 20;
    private static final int BUTTON_MARGIN = 5;
    private static final int TITLE_HEIGHT = 20;

    // Core fields
    private final PushdozerConfigScreen parent;
    private final PushdozerConfig config;
    private final List<Element> widgets = new ArrayList<>();

    // State fields
    private boolean visible = false;
    private int panelLeft, panelTop;

    // Pre-calculated title position (performance optimization)
    private int titleX, titleY;
    private Text titleText;

    // Controls
    private ButtonWidget followPlayerButton;
    private ButtonWidget lockOnceButton;
    private ButtonWidget noLimitButton;
    private ButtonWidget customHeightButton;
    private SliderWidget heightSlider;

    /**
     * Constructor, initializes height configuration panel.
     *
     * @param parent Parent configuration screen
     * @param config Configuration object
     */
    public HeightConfigPanel(PushdozerConfigScreen parent, PushdozerConfig config) {
        this.parent = parent;
        this.config = config;

        // Calculate panel position (screen center)
        this.panelLeft = (parent.getScreenWidth() - PANEL_WIDTH) / 2;
        this.panelTop = (parent.getScreenHeight() - PANEL_HEIGHT) / 2;

        initializeWidgets();
        initializeTitlePosition();
    }

    /**
     * Initialize all button controls.
     */
    private void initializeWidgets() {
        widgets.clear();

        int startY = panelTop + TITLE_HEIGHT + BUTTON_MARGIN; // Leave space for title and margin
        int buttonWidth = (PANEL_WIDTH - (BUTTON_MARGIN * 3)) / 2; // Two columns
        int buttonFullWidth = PANEL_WIDTH - (BUTTON_MARGIN * 2);

        // First row: Follow player height, Lock to player height
        followPlayerButton = ButtonWidget.builder(
                getButtonText(PushdozerConfig.HeightMode.FOLLOW_PLAYER),
                button -> selectHeightMode(PushdozerConfig.HeightMode.FOLLOW_PLAYER))
                .dimensions(panelLeft + BUTTON_MARGIN, startY, buttonWidth, BUTTON_HEIGHT)
                .tooltip(Tooltip.of(Text.translatable("pushdozer.tooltip.height_follow_player")))
                .build();
        lockOnceButton = ButtonWidget.builder(
                getButtonText(PushdozerConfig.HeightMode.LOCKED_ONCE),
                button -> selectHeightMode(PushdozerConfig.HeightMode.LOCKED_ONCE))
                .dimensions(panelLeft + BUTTON_MARGIN * 2 + buttonWidth, startY, buttonWidth, BUTTON_HEIGHT)
                .tooltip(Tooltip.of(Text.translatable("pushdozer.tooltip.height_locked_once")))
                .build();

        // Second row: No height limit, Custom height
        noLimitButton = ButtonWidget.builder(
                getButtonText(PushdozerConfig.HeightMode.NO_LIMIT),
                button -> selectHeightMode(PushdozerConfig.HeightMode.NO_LIMIT))
                .dimensions(panelLeft + BUTTON_MARGIN, startY + BUTTON_HEIGHT + BUTTON_MARGIN, buttonWidth, BUTTON_HEIGHT)
                .tooltip(Tooltip.of(Text.translatable("pushdozer.tooltip.height_no_limit")))
                .build();
        customHeightButton = ButtonWidget.builder(
                getButtonText(PushdozerConfig.HeightMode.CUSTOM),
                button -> selectHeightMode(PushdozerConfig.HeightMode.CUSTOM))
                .dimensions(panelLeft + BUTTON_MARGIN * 2 + buttonWidth, startY + BUTTON_HEIGHT + BUTTON_MARGIN, buttonWidth, BUTTON_HEIGHT)
                .tooltip(Tooltip.of(Text.translatable("pushdozer.tooltip.height_custom")))
                .build();

        // Slider
        int currentHeight = config.getLockedHeight();
        heightSlider = new HeightConfigSlider(
                panelLeft + BUTTON_MARGIN,
                startY + (BUTTON_HEIGHT + BUTTON_MARGIN) * 2,
                buttonFullWidth,
                BUTTON_HEIGHT,
                Text.translatable("pushdozer.config.height_value", currentHeight),
                (currentHeight + 64) / 384.0
        );

        // Done button
        ButtonWidget doneButton = ButtonWidget.builder(
                        Text.translatable("pushdozer.button.done"),
                        button -> {
                            hide();
                            parent.showMainPanel();
                        })
                .dimensions(panelLeft + BUTTON_MARGIN, panelTop + PANEL_HEIGHT - BUTTON_HEIGHT - BUTTON_MARGIN, buttonFullWidth, BUTTON_HEIGHT)
                .build();

        widgets.add(followPlayerButton);
        widgets.add(lockOnceButton);
        widgets.add(noLimitButton);
        widgets.add(customHeightButton);
        widgets.add(heightSlider);
        widgets.add(doneButton);
    }

    /**
     * Get button text (with selected marker)
     */
    private Text getButtonText(PushdozerConfig.HeightMode mode) {
        PushdozerConfig.HeightMode currentMode = config.getHeightMode();
        boolean isLockedOnce = config.isLockedOnceMode();

        String prefix;
        if (mode == PushdozerConfig.HeightMode.LOCKED_ONCE) {
            prefix = isLockedOnce ? "☑ " : "";
        } else if (mode == PushdozerConfig.HeightMode.CUSTOM) {
            prefix = (currentMode == mode && !isLockedOnce) ? "☑ " : "";
        } else {
            prefix = (currentMode == mode) ? "☑ " : "";
        }

        Text baseText = switch (mode) {
            case FOLLOW_PLAYER -> Text.translatable("pushdozer.config.height_follow_player");
            case LOCKED_ONCE -> Text.translatable("pushdozer.config.height_locked_once");
            case NO_LIMIT -> Text.translatable("pushdozer.config.height_no_limit");
            case CUSTOM -> Text.translatable("pushdozer.config.height_custom");
        };
        return Text.literal(prefix).append(baseText);
    }

    /**
     * Pre-calculate title position to optimize rendering performance.
     */
    private void initializeTitlePosition() {
        titleText = Text.translatable("pushdozer.panel.height_config.title");

        if (parent.getClient() != null) {
            int titleWidth = parent.getClient().textRenderer.getWidth(titleText);
            titleX = panelLeft + (PANEL_WIDTH - titleWidth) / 2;
            titleY = panelTop + (TITLE_HEIGHT - parent.getClient().textRenderer.fontHeight) / 2;
        }
    }

    /**
     * Select specified height mode.
     *
     * @param mode Height mode to select
     */
    private void selectHeightMode(PushdozerConfig.HeightMode mode) {
        if (mode == PushdozerConfig.HeightMode.LOCKED_ONCE) {
            // Lock to player height: Write current player's foot block height to config and switch to LOCKED_ONCE
            if (parent.getClient() != null && parent.getClient().player != null) {
                int y = parent.getClient().player.getBlockY() - 1; // Player's foot block height
                config.setLockedHeight(y);
                config.setHeightMode(PushdozerConfig.HeightMode.CUSTOM);
                config.setLockedOnceMode(true);
                updateButtonStates();
                heightSlider.active = false;
                PushdozerMod.saveConfig();
                parent.getClient().player.sendMessage(Text.translatable("pushdozer.message.height_mode_locked_once", y), true);
            }
        } else {
            config.setHeightMode(mode);
            config.setLockedOnceMode(false);
            updateButtonStates();
            heightSlider.active = mode == PushdozerConfig.HeightMode.CUSTOM;
            PushdozerMod.saveConfig();
            if (parent.getClient() != null && parent.getClient().player != null) {
                Text message = switch (mode) {
                    case FOLLOW_PLAYER -> Text.translatable("pushdozer.message.height_mode_follow_player");
                    case NO_LIMIT -> Text.translatable("pushdozer.message.height_mode_no_limit");
                    case CUSTOM -> {
                        int customHeight = config.getLockedHeight();
                        yield Text.translatable("pushdozer.message.height_mode_custom", customHeight);
                    }
                    default -> throw new IllegalStateException("Unexpected value: " + mode);
                };
                parent.getClient().player.sendMessage(message, true);
            }
        }
    }

    /**
     * Update button states.
     */
    private void updateButtonStates() {
        followPlayerButton.setMessage(getButtonText(PushdozerConfig.HeightMode.FOLLOW_PLAYER));
        lockOnceButton.setMessage(getButtonText(PushdozerConfig.HeightMode.LOCKED_ONCE));
        noLimitButton.setMessage(getButtonText(PushdozerConfig.HeightMode.NO_LIMIT));
        customHeightButton.setMessage(getButtonText(PushdozerConfig.HeightMode.CUSTOM));
    }

    /**
     * Show the panel.
     */
    public void show() {
        visible = true;
        // Recalculate position in case window size changed
        this.panelLeft = (parent.getScreenWidth() - PANEL_WIDTH) / 2;
        this.panelTop = (parent.getScreenHeight() - PANEL_HEIGHT) / 2;
        initializeWidgets(); // Reinitialize buttons to ensure correct position
        initializeTitlePosition();
        updateButtonStates();
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
     * @return true if the panel is visible
     */
    public boolean isVisible() {
        return visible;
    }

    /**
     * Render all panel components.
     *
     * @param context Draw context
     * @param mouseX Mouse X coordinate
     * @param mouseY Mouse Y coordinate
     * @param delta Frame delta time
     */
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        if (!visible) return;

        renderBackground(context);
        renderButtons(context, mouseX, mouseY, delta);
        renderTitle(context);
    }

    /**
     * Render background overlay and panel background.
     *
     * @param context Draw context
     */
    private void renderBackground(DrawContext context) {
        // Draw panel background (semi-transparent)
        context.fill(panelLeft, panelTop, panelLeft + PANEL_WIDTH, panelTop + PANEL_HEIGHT, COLOR_PANEL_BG);

        // Draw title bar background
        context.fill(panelLeft, panelTop, panelLeft + PANEL_WIDTH, panelTop + TITLE_HEIGHT, COLOR_TITLE_BG);

        // Draw panel border (after title background to ensure border is not obscured)
        drawBorder(context, panelLeft, panelTop);
    }

    private static void drawBorder(DrawContext context, int x, int y) {
        // top
        context.fill(x, y, x + HeightConfigPanel.PANEL_WIDTH, y + 1, HeightConfigPanel.COLOR_PANEL_BORDER);
        // bottom
        context.fill(x, y + HeightConfigPanel.PANEL_HEIGHT - 1, x + HeightConfigPanel.PANEL_WIDTH, y + HeightConfigPanel.PANEL_HEIGHT, HeightConfigPanel.COLOR_PANEL_BORDER);
        // left
        context.fill(x, y, x + 1, y + HeightConfigPanel.PANEL_HEIGHT, HeightConfigPanel.COLOR_PANEL_BORDER);
        // right
        context.fill(x + HeightConfigPanel.PANEL_WIDTH - 1, y, x + HeightConfigPanel.PANEL_WIDTH, y + HeightConfigPanel.PANEL_HEIGHT, HeightConfigPanel.COLOR_PANEL_BORDER);
    }

    /**
     * Render title text.
     *
     * @param context Draw context
     */
    private void renderTitle(DrawContext context) {
        if (titleText == null) {
            titleText = Text.translatable("pushdozer.panel.height_config.title");
        }
        if (parent.getClient() == null) {
            return;
        }

        Text displayTitle = titleText.copy().formatted(Formatting.BOLD, Formatting.YELLOW);
        int titleWidth = parent.getClient().textRenderer.getWidth(displayTitle);
        int x = panelLeft + (PANEL_WIDTH - titleWidth) / 2;
        int y = panelTop + (TITLE_HEIGHT - parent.getClient().textRenderer.fontHeight) / 2;
        context.drawText(parent.getClient().textRenderer, displayTitle, x, y, 0xFFFFFFFF, true);
    }

    /**
     * Render all buttons.
     *
     * @param context Draw context
     * @param mouseX Mouse X coordinate
     * @param mouseY Mouse Y coordinate
     * @param delta Frame delta time
     */
    private void renderButtons(DrawContext context, int mouseX, int mouseY, float delta) {
        // First reset all button focus states
        if (followPlayerButton != null) {
            followPlayerButton.setFocused(false);
        }
        if (lockOnceButton != null) {
            lockOnceButton.setFocused(false);
        }
        if (noLimitButton != null) {
            noLimitButton.setFocused(false);
        }
        if (customHeightButton != null) {
            customHeightButton.setFocused(false);
        }

        // Then set the currently selected button's focus state
        PushdozerConfig.HeightMode currentMode = config.getHeightMode();
        boolean isLockedOnce = config.isLockedOnceMode();

        if (currentMode == PushdozerConfig.HeightMode.FOLLOW_PLAYER && followPlayerButton != null) {
            followPlayerButton.setFocused(true);
        }
        if (isLockedOnce && lockOnceButton != null) {
            lockOnceButton.setFocused(true);
        }
        if (currentMode == PushdozerConfig.HeightMode.NO_LIMIT && noLimitButton != null) {
            noLimitButton.setFocused(true);
        }
        if (currentMode == PushdozerConfig.HeightMode.CUSTOM && !isLockedOnce && customHeightButton != null) {
            customHeightButton.setFocused(true);
        }

        // Finally render all buttons (let buttons handle their own text rendering)
        for (Element widget : widgets) {
            if (widget instanceof Drawable) {
                ((Drawable) widget).render(context, mouseX, mouseY, delta);
            }
        }
    }

    /**
     * Handle mouse click events.
     */
    public boolean mouseClicked(Click click) {
        double mouseX = click.x();
        double mouseY = click.y();
        for (Element widget : widgets) {
            if (widget instanceof ClickableWidget clickable) {
                if (clickable.isMouseOver(mouseX, mouseY)) {
                    return clickable.mouseClicked(click, false);
                }
            }
        }
        return false;
    }

    /**
     * Handle mouse drag events.
     */
    public boolean mouseDragged(Click click, double deltaX, double deltaY) {
        double mouseX = click.x();
        double mouseY = click.y();
        for (Element widget : widgets) {
            if (widget instanceof ClickableWidget clickable) {
                if (clickable.isMouseOver(mouseX, mouseY)) {
                    return clickable.mouseDragged(click, deltaX, deltaY);
                }
            }
        }
        return false;
    }

    /**
     * Handle mouse release events.
     */
    public boolean mouseReleased(Click click) {
        double mouseX = click.x();
        double mouseY = click.y();
        for (Element widget : widgets) {
            if (widget instanceof ClickableWidget clickable) {
                if (clickable.isMouseOver(mouseX, mouseY)) {
                    return clickable.mouseReleased(click);
                }
            }
        }
        return false;
    }

    /**
     * Height configuration slider
     */
    private class HeightConfigSlider extends SliderWidget {
        public HeightConfigSlider(int x, int y, int width, int height, Text text, double value) {
            super(x, y, width, height, text, value);
        }

        @Override
        protected void updateMessage() {
            int height = (int) Math.round(this.value * 384) - 64;
            setMessage(Text.translatable("pushdozer.config.height_value", height));
        }

        @Override
        protected void applyValue() {
            int height = (int) Math.round(this.value * 384) - 64;
            config.setLockedHeight(height);
        }
    }
} 