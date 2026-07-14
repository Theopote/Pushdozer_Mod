package com.pushdozer.ui.screens;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.gui.screen.Screen;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import com.pushdozer.PushdozerMod;
import com.pushdozer.config.PushdozerConfig;
import com.pushdozer.items.PushdozerItem;
import com.pushdozer.ui.panels.*;
import com.pushdozer.ui.panels.brushgeometry.BoxSubPanel;
import com.pushdozer.ui.panels.brushgeometry.ConeSubPanel;
import com.pushdozer.ui.panels.brushgeometry.CylinderSubPanel;
import com.pushdozer.ui.panels.brushgeometry.EllipsoidSubPanel;
import com.pushdozer.ui.panels.brushgeometry.GeometrySelectionPanel;
import com.pushdozer.ui.panels.brushgeometry.GeometrySubPanel;
import com.pushdozer.ui.panels.brushgeometry.OctahedronSubPanel;
import com.pushdozer.ui.panels.brushgeometry.SphereSubPanel;
import com.pushdozer.ui.panels.brushgeometry.TetrahedronSubPanel;
import com.pushdozer.ui.panels.brushgeometry.TriangularPrismSubPanel;
import com.pushdozer.ui.panels.workmode.BatchPlantConfigPanel;
import com.pushdozer.ui.panels.workmode.BoneMealConfigPanel;
import com.pushdozer.ui.panels.workmode.ExcavateConfigPanel;
import com.pushdozer.ui.panels.workmode.PlaceConfigPanel;
import com.pushdozer.ui.panels.workmode.ShorelineProcessConfigPanel;
import com.pushdozer.ui.panels.workmode.SurfaceConvertConfigPanel;
import com.pushdozer.ui.panels.workmode.WorkModeConfigPanel;
import com.pushdozer.ui.panels.workmode.SmoothConfigPanel;
import com.pushdozer.ui.panels.workmode.SurfaceRoughenConfigPanel;
import com.pushdozer.ui.panels.workmode.WorkModeSelectionPanel;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.Drawable;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.client.input.KeyInput;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.client.gui.tooltip.Tooltip;

/**
 * PushdozerConfigScreen class
 * Used to create and manage the Pushdozer tool configuration interface
 */
public class PushdozerConfigScreen extends Screen {
    // Pushdozer configuration object
    private final PushdozerConfig config;

    // UI components
    private ButtonWidget applyAndCloseButton;      // Apply and close button
    private ButtonWidget displayModeButton;        // Display mode button
    private GeometrySubPanel currentSubPanel;      // Currently displayed geometry sub-panel

    // Panel size constants
    private static final int PANEL_WIDTH = 280;    // Main panel widened by 40
    private static final int TITLE_HEIGHT = 20;    // Title height

    // Panel position
    public int panelLeft, panelTop;

    // Record interface open time
    private long openTime;
    // Close delay time (milliseconds)
    private static final long CLOSE_DELAY = 150;

    // Geometry type option list
    // Old geometry selection related code has been removed

    private boolean mainPanelVisible = true;       // Whether main panel is visible

    // Maximum operation distance slider
    private CustomDistanceSlider distanceSlider;
    // Work mode related
    private ButtonWidget workModeButton;
    private ButtonWidget workModeConfigButton;
    private WorkModeSelectionPanel workModeSelectionPanel;
    private final List<ButtonWidget> workModeOptions = new ArrayList<>();
    private boolean isWorkModeDropdownOpen = false;

    // Work mode config panel
    private WorkModeConfigPanel currentWorkModeConfigPanel;

    // Geometry type selection components
    // Geometry button and selection panel
    private ButtonWidget geometryButton;
    private ButtonWidget geometryConfigButton;
    private GeometrySelectionPanel geometrySelectionPanel;

    // Height config button
    private ButtonWidget heightConfigButton;
    private HeightConfigPanel heightConfigPanel;

    /**
     * Constructor
     * @param config Pushdozer config object
     */
    public PushdozerConfigScreen(PushdozerConfig config) {
        super(Text.translatable("pushdozer.config.title"));
        this.config = config; // Initialize config object
        MinecraftClient client = MinecraftClient.getInstance();
        // Check if player is holding Pushdozer item
        if (client.player != null && !(client.player.getMainHandStack().getItem() instanceof PushdozerItem)) {
            client.setScreen(null);
            return;
        }
        this.openTime = System.currentTimeMillis();

    }


    /**
     * Init method, sets up UI components
     */
    @Override
    protected void init() {
        super.init();
        // Check if config is properly initialized
        if (config == null) {
            PushdozerMod.LOGGER.error(Text.translatable("pushdozer.error.config_null").getString());
            return;
        }

        // Save current sub-panel state
        GeometrySubPanel previousSubPanel = currentSubPanel;
        boolean wasSubPanelVisible = previousSubPanel != null && previousSubPanel.isVisible();
        String previousPanelType = getString(wasSubPanelVisible, previousSubPanel);

        // Initialize all UI components
        initializeComponents();

        // If a sub-panel was visible before, restore sub-panel state
        if (wasSubPanelVisible && previousPanelType != null) {
            switch (previousPanelType) {
                case "Box":
                    showBoxConfigPanel();
                    break;
                case "Sphere":
                    showSphereConfigPanel();
                    break;
                case "Octahedron":
                    showOctahedronConfigPanel();
                    break;
                case "Cylinder":
                    showCylinderConfigPanel();
                    break;
                case "Cone":
                    showConeConfigPanel();
                    break;
                case "Ellipsoid":
                    showEllipsoidConfigPanel();
                    break;
            }
        }
    }

    private static @Nullable String getString(boolean wasSubPanelVisible, GeometrySubPanel previousSubPanel) {
        String previousPanelType = null;
        if (wasSubPanelVisible) {
            switch (previousSubPanel) {
                case BoxSubPanel boxSubPanel -> previousPanelType = "Box";
                case SphereSubPanel sphereSubPanel -> previousPanelType = "Sphere";
                case OctahedronSubPanel octahedronSubPanel -> previousPanelType = "Octahedron";
                case CylinderSubPanel cylinderSubPanel -> previousPanelType = "Cylinder";
                case ConeSubPanel coneSubPanel -> previousPanelType = "Cone";
                case EllipsoidSubPanel ellipsoidSubPanel -> previousPanelType = "Ellipsoid";
                default -> {
                }
            }
        }
        return previousPanelType;
    }

    // Move UI component initialization logic from init() method to this new method
    private void initializeComponents() {
        // Ensure display mode has a default value
        if (config.getDisplayMode() == null) {
            PushdozerMod.LOGGER.error(Text.translatable("pushdozer.error.display_mode_null").getString());
            config.setDisplayMode(PushdozerConfig.DisplayMode.WIREFRAME);
        }

        // Set default work mode
        if (config.getWorkMode() == null) {
            config.setWorkMode(PushdozerConfig.WorkMode.EXCAVATE);
        }

        // Calculate panel position (horizontal + vertical center)
        panelLeft = (this.width - PANEL_WIDTH) / 2;
        panelTop = (this.height - computeMainPanelHeight()) / 2;

        // Update content area position and size (panel background left/right 20, controls inset 5 more → contentInset=25)
        int contentInset = 25;
        int contentLeft = panelLeft + contentInset; // Controls 5 pixels from panel background left/right
        int contentTop = panelTop + TITLE_HEIGHT + 5; // 5 pixel gap below title
        int rowHeight = 20;
        int verticalGap = 5;
        int finalButtonSpacing = 10; // Spacing between buttons

        // First row: Geometry type dropdown and config button
        int buttonSpacing = 10;
        int availableWidth = PANEL_WIDTH - (contentInset * 2); // Available width: panel width - left/right control margins
        int geomConfigButtonWidth = 60; // Config button fixed width
        int dropdownWidth = availableWidth - geomConfigButtonWidth - buttonSpacing; // Fill remaining space
        // Total row width is available width
        // Fill from left side

        // Create geometry button
        geometryButton = this.addDrawableChild(ButtonWidget.builder(
            getGeometryButtonText(),
            button -> showGeometrySelectionPanel())
            .dimensions(contentLeft, contentTop, dropdownWidth, rowHeight)
            .tooltip(Tooltip.of(Text.translatable("pushdozer.tooltip.brush_shape_selection")))
            .build());

        // Create geometry selection panel
        geometrySelectionPanel = new GeometrySelectionPanel(this, config, this::onGeometryTypeChanged);

        // Create work mode selection panel
        workModeSelectionPanel = new WorkModeSelectionPanel(this, config, this::onWorkModeChanged);

        // Create config button
        geometryConfigButton = this.addDrawableChild(ButtonWidget.builder(
            Text.translatable("pushdozer.button.config"),
            button -> showGeometryConfigPanel())
            .dimensions(contentLeft + dropdownWidth + buttonSpacing, contentTop, geomConfigButtonWidth, rowHeight)
            .tooltip(Tooltip.of(Text.translatable("pushdozer.tooltip.geometry_config")))
            .build());

        // Second row: Work mode button and config button
        workModeButton = this.addDrawableChild(ButtonWidget.builder(
            getWorkModeText(),
            button -> showWorkModeSelectionPanel())
            .dimensions(contentLeft, contentTop + rowHeight + verticalGap, dropdownWidth, rowHeight)
            .tooltip(Tooltip.of(Text.translatable("pushdozer.tooltip.work_mode")))
            .build());

        // Create work mode config button
        workModeConfigButton = this.addDrawableChild(ButtonWidget.builder(
            Text.translatable("pushdozer.button.config"),
            button -> showWorkModeConfigPanel())
            .dimensions(contentLeft + dropdownWidth + buttonSpacing, contentTop + rowHeight + verticalGap, geomConfigButtonWidth, rowHeight)
            .tooltip(Tooltip.of(Text.translatable("pushdozer.tooltip.work_mode_config")))
            .build());



        // Fourth row: Maximum operation distance slider (fill available width)
        distanceSlider = new CustomDistanceSlider(
                contentLeft,
            contentTop + (rowHeight + verticalGap) * 3,
                availableWidth,
            rowHeight,
            Text.translatable("pushdozer.label.maximum_operation_distance", config.getMaxOperationDistance()),
            config.getMaxOperationDistance() / (float)PushdozerConfig.MAX_OPERATION_DISTANCE
        );
        distanceSlider.setTooltip(Tooltip.of(Text.translatable("pushdozer.tooltip.distance_slider")));
        this.addDrawableChild(distanceSlider);

        // Third row: Display mode button (fill available width)
        displayModeButton = this.addDrawableChild(ButtonWidget.builder(
            getDisplayModeButtonText(),
            button -> toggleDisplayMode())
            .dimensions(contentLeft, contentTop + (rowHeight + verticalGap) * 2, availableWidth, rowHeight)
            .tooltip(Tooltip.of(Text.translatable("pushdozer.tooltip.display_mode")))
            .build());

        // Fifth row: Height config button and apply and close button
        int heightConfigButtonWidth = (availableWidth - finalButtonSpacing) / 2; // Evenly distribute width
        int applyCloseButtonWidth = availableWidth - finalButtonSpacing - heightConfigButtonWidth; // Remaining width for apply close button

        // Add height config button
        heightConfigButton = this.addDrawableChild(ButtonWidget.builder(
            Text.translatable("pushdozer.config.height_config"),
            button -> showHeightConfigPanel())
            .dimensions(contentLeft, contentTop + (rowHeight + verticalGap) * 4, heightConfigButtonWidth, rowHeight)
            .tooltip(Tooltip.of(Text.translatable("pushdozer.tooltip.height_config")))
            .build());

        // Add apply and close button
        applyAndCloseButton = this.addDrawableChild(ButtonWidget.builder(
            Text.translatable("pushdozer.config.save_and_close"),
            button -> applyAndClose())
            .dimensions(contentLeft + heightConfigButtonWidth + finalButtonSpacing,
                       contentTop + (rowHeight + verticalGap) * 4,
                       applyCloseButtonWidth,
                       rowHeight)
            .tooltip(Tooltip.of(Text.translatable("pushdozer.tooltip.save_close")))
            .build());
    }

    // Calculate main panel theoretical height for vertical centering during initialization
    private int computeMainPanelHeight() {
        int rowHeight = 20;
        int verticalGap = 5;
        int numRows = 5; // Geometry/mode/display mode/distance slider/bottom buttons
        int numGaps = numRows - 1;
        int topMarginBelowTitle = 5;
        int bottomMargin = 5;
        return TITLE_HEIGHT + topMarginBelowTitle + (rowHeight * numRows) + (verticalGap * numGaps) + bottomMargin;
    }

    /**
     * Display geometry selection panel
     */
    private void showGeometrySelectionPanel() {
        if (geometrySelectionPanel != null) {
            geometrySelectionPanel.show();
        }
    }

    /**
     * Display work mode selection panel
     */
    private void showWorkModeSelectionPanel() {
        if (workModeSelectionPanel != null) {
            workModeSelectionPanel.show();
        }
    }

    /**
     * Display work mode config panel
     */
    private void showWorkModeConfigPanel() {
        // Hide work mode selection panel
        if (workModeSelectionPanel != null) {
            workModeSelectionPanel.hide();
        }

        // Display different config panels based on current work mode
        PushdozerConfig.WorkMode currentMode = config.getWorkMode();

        switch (currentMode) {
            case EXCAVATE:
                currentWorkModeConfigPanel = new ExcavateConfigPanel(this, config);
                break;
            case PLACE:
                currentWorkModeConfigPanel = new PlaceConfigPanel(this, config);
                break;
            case SMOOTH:
                currentWorkModeConfigPanel = new SmoothConfigPanel(this, config);
                break;
            case SMOOTH_RAISE, ADAPTIVE_SMOOTH, SMOOTH_LOWER:
                // Legacy smooth entry compatibility: open unified smooth panel directly
                currentWorkModeConfigPanel = new SmoothConfigPanel(this, config);
                break;
            case SURFACE_ROUGHEN:
                currentWorkModeConfigPanel = new SurfaceRoughenConfigPanel(this, config);
                break;
            case SURFACE_CONVERT:
                currentWorkModeConfigPanel = new SurfaceConvertConfigPanel(this, config);
                break;
            case BONE_MEAL:
                currentWorkModeConfigPanel = new BoneMealConfigPanel(this, config);
                break;
            case BATCH_PLANT:
                currentWorkModeConfigPanel = new BatchPlantConfigPanel(this, config);
                break;
            case SHORELINE_PROCESS:
                currentWorkModeConfigPanel = new ShorelineProcessConfigPanel(this, config);
                break;
            default:
                showErrorMessage(Text.translatable("pushdozer.error.unknown_work_mode", currentMode.getDisplayText().getString()).getString());
                return;
        }

            currentWorkModeConfigPanel.show();
            hideMainPanel();
    }

    /**
     * Callback when geometry type changes
     */
    private void onGeometryTypeChanged(PushdozerConfig.GeometryType geometryType) {
        config.setGeometryType(geometryType);
        PushdozerMod.saveConfig();

        // Update button text
        if (geometryButton != null) {
            geometryButton.setMessage(getGeometryButtonText());
        }

        // Add shape switch success message
        showErrorMessage(Text.translatable("pushdozer.message.shape_switch", geometryType.getDisplayText().getString()).getString());

        // Force update preview
        updatePreview();
    }

    /**
     * Callback when work mode changes
     */
    private void onWorkModeChanged(PushdozerConfig.WorkMode workMode) {
        config.setWorkMode(workMode);
        PushdozerMod.saveConfig();

        // Update button text
        if (workModeButton != null) {
            workModeButton.setMessage(getWorkModeText());
        }

        // Add work mode switch success message
        showErrorMessage(Text.translatable("pushdozer.message.working_mode_switch", workMode.getDisplayText().getString()).getString());

        // Force update preview
        updatePreview();
    }

    /**
     * Display geometry config panel
     */
    private void showGeometryConfigPanel() {
        PushdozerConfig.GeometryType currentType = config.getGeometryType();
        switch (currentType) {
            case BOX:
                showBoxConfigPanel();
                break;
            case SPHERE:
                showSphereConfigPanel();
                break;
            case OCTAHEDRON:
                showOctahedronConfigPanel();
                break;
            case CYLINDER:
                showCylinderConfigPanel();
                break;
            case CONE:
                showConeConfigPanel();
                break;
            case ELLIPSOID:
                showEllipsoidConfigPanel();
                break;
            case TETRAHEDRON:
                showTetrahedronConfigPanel();
                break;
            case TRIANGULAR_PRISM:
                showTriangularPrismConfigPanel();
                break;
            default:
                // Default to box config panel
                showBoxConfigPanel();
                break;
        }
    }


    /**
     * Display box config panel
     */
    private void showBoxConfigPanel() {
        currentSubPanel = new BoxSubPanel(this, config);
        showSubPanel();
    }

    /**
     * Display sphere config panel
     */
    private void showSphereConfigPanel() {
        currentSubPanel = new SphereSubPanel(this, config);
        showSubPanel();
    }

    /**
     * Display octahedron config panel
     */
    private void showOctahedronConfigPanel() {
        currentSubPanel = new OctahedronSubPanel(this, config);
        showSubPanel();
    }

    /**
     * Display cylinder config panel
     */
    private void showCylinderConfigPanel() {
        currentSubPanel = new CylinderSubPanel(this, config);
        showSubPanel();
    }

    /**
     * Display cone config panel
     */
    private void showConeConfigPanel() {
        currentSubPanel = new ConeSubPanel(this, config);
        showSubPanel();
    }

    /**
     * Display ellipsoid config panel
     */
    private void showEllipsoidConfigPanel() {
        currentSubPanel = new EllipsoidSubPanel(this, config);
        showSubPanel();
    }

    /**
     * Display tetrahedron config panel
     */
    private void showTetrahedronConfigPanel() {
        currentSubPanel = new TetrahedronSubPanel(this, config);
        showSubPanel();
    }

    /**
     * Display triangular prism config panel
     */
    private void showTriangularPrismConfigPanel() {
        currentSubPanel = new TriangularPrismSubPanel(this, config);
        showSubPanel();
    }

    /**
     * Display sub-panel
     */
    private void showSubPanel() {
        if (currentSubPanel != null) {
            currentSubPanel.init();
            currentSubPanel.show();
            hideMainPanel();
        }
    }

    /**
     * Hide main panel
     */
    private void hideMainPanel() {
        mainPanelVisible = false;
        // Hide all components on main interface
        if (geometryButton != null) geometryButton.visible = false;
        if (geometryConfigButton != null) geometryConfigButton.visible = false;
        if (workModeButton != null) workModeButton.visible = false;
        if (workModeConfigButton != null) workModeConfigButton.visible = false;
        if (displayModeButton != null) displayModeButton.visible = false;
        if (distanceSlider != null) distanceSlider.visible = false;
        if (applyAndCloseButton != null) applyAndCloseButton.visible = false;
        if (heightConfigButton != null) heightConfigButton.visible = false;
        // Hide geometry selection panel
        if (geometrySelectionPanel != null) geometrySelectionPanel.hide();
        // Hide work mode selection panel
        if (workModeSelectionPanel != null) workModeSelectionPanel.hide();
    }

    /**
     * Method called every frame, used to detect key presses
     */
    @Override
    public void tick() {
        super.tick();
        // Check if K key is pressed and delay time has passed
        if (InputUtil.isKeyPressed(MinecraftClient.getInstance().getWindow(), GLFW.GLFW_KEY_K) &&
            System.currentTimeMillis() - openTime > CLOSE_DELAY) {
            this.close();
        }
    }


    /**
     * Handle key press events
     */
    @Override
    public boolean keyPressed(KeyInput input) {
        int keyCode = input.key();
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            // Handle height config panel first
            if (heightConfigPanel != null && heightConfigPanel.isVisible()) {
                heightConfigPanel.hide();
                showMainPanel();
                return true;
            }

            // Handle geometry selection panel first
            if (geometrySelectionPanel != null && geometrySelectionPanel.isVisible()) {
                geometrySelectionPanel.hide(); // Cancel selection, keep original geometry
                return true;
            }

            // Handle work mode selection panel
            if (workModeSelectionPanel != null && workModeSelectionPanel.isVisible()) {
                workModeSelectionPanel.hide(); // Cancel selection, keep original work mode
                return true;
            }

            // Handle work mode config panel
            if (currentWorkModeConfigPanel != null && currentWorkModeConfigPanel.isVisible()) {
                // Equivalent to pressing confirm: save config and close
                currentWorkModeConfigPanel.saveConfig();
                currentWorkModeConfigPanel.hide();
                showMainPanel();
                return true;
            }

            // Handle geometry config panel
            if (currentSubPanel != null && currentSubPanel.isVisible()) {
                // Equivalent to pressing confirm: save config and close
                currentSubPanel.saveConfig();
                hideSubPanel();
                showMainPanel();
                return true;
            }
        }

        // Handle up/down arrow keys to adjust maximum operation distance
        if (keyCode == GLFW.GLFW_KEY_UP || keyCode == GLFW.GLFW_KEY_DOWN) {
            if (this.client != null && this.client.player != null) {
                int currentDistance = config.getMaxOperationDistance();
                int newDistance;

                // Up arrow increases distance, down arrow decreases distance
                if (keyCode == GLFW.GLFW_KEY_UP) {
                    newDistance = Math.min(currentDistance + 1, PushdozerConfig.MAX_OPERATION_DISTANCE);
                } else {
                    newDistance = Math.max(currentDistance - 1, 1);
                }

                if (newDistance != currentDistance) {
                    // Update slider value, it will automatically update config and display message
                    if (distanceSlider != null) {
                        distanceSlider.setCustomValue(newDistance / (float)PushdozerConfig.MAX_OPERATION_DISTANCE);
                    }
                    return true;
                }
            }
        }

        return super.keyPressed(input);
    }

    /**
     * Method called when closing the interface
     */
    @Override
    public void close() {
        PushdozerMod.saveConfig();
        super.close();
    }

    /**
     * Render method, used to draw the entire interface
     */
    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        // Check if any sub-panel is visible
        boolean geometryPanelVisible = geometrySelectionPanel != null && geometrySelectionPanel.isVisible();
        boolean workModePanelVisible = workModeSelectionPanel != null && workModeSelectionPanel.isVisible();
        boolean workModeConfigPanelVisible = currentWorkModeConfigPanel != null && currentWorkModeConfigPanel.isVisible();
        boolean configSubPanelVisible = currentSubPanel != null && currentSubPanel.isVisible();
        boolean heightConfigPanelVisible = heightConfigPanel != null && heightConfigPanel.isVisible();

        // Only render main interface when all sub-panels are not visible
        if (!geometryPanelVisible && !workModePanelVisible && !workModeConfigPanelVisible && !configSubPanelVisible && !heightConfigPanelVisible) {
            // Calculate new panel height
            int panelHeight = applyAndCloseButton.getY() + applyAndCloseButton.getHeight() + 5 - panelTop; // Reduce bottom margin to 5 pixels

            // Draw semi-transparent main panel background
            context.fill(panelLeft + 20, panelTop, panelLeft + PANEL_WIDTH - 20, panelTop + panelHeight, 0x80000000);

            // Draw semi-transparent title background
            context.fill(panelLeft + 20, panelTop, panelLeft + PANEL_WIDTH - 20, panelTop + TITLE_HEIGHT, 0xE0303030);

            // Draw border
            drawBorder(context, panelLeft + 20, panelTop, panelHeight);

            // Draw title
            if (this.textRenderer != null) {
                Text title = Text.translatable("pushdozer.config.title")
                    .formatted(Formatting.BOLD, Formatting.YELLOW);
                int titleWidth = this.textRenderer.getWidth(title);
                context.drawText(this.textRenderer, title,
                    panelLeft + (PANEL_WIDTH - titleWidth) / 2,
                    panelTop + (TITLE_HEIGHT - this.textRenderer.fontHeight) / 2,
                    0xFFFFFF, false);
            }

            // Render all UI elements
            for (Element child : this.children()) {
                if (child instanceof Drawable) {
                    ((Drawable) child).render(context, mouseX, mouseY, delta);
                }
            }

            // Render dropdown list options
            if (isWorkModeDropdownOpen) {
                renderWorkModeOptions(context, mouseX, mouseY, delta);
            }
        }

        // Only render sub-panel when it's visible
        if (currentSubPanel != null && currentSubPanel.isVisible()) {
            currentSubPanel.render(context, mouseX, mouseY, delta);
        }

        // Render geometry selection panel last to ensure it's on top
        if (geometryPanelVisible) {
            geometrySelectionPanel.render(context, mouseX, mouseY, delta);
        }

        // Render work mode selection panel
        if (workModePanelVisible) {
            workModeSelectionPanel.render(context, mouseX, mouseY, delta);
        }

        // Render work mode config panel
        if (workModeConfigPanelVisible) {
            currentWorkModeConfigPanel.render(context, mouseX, mouseY, delta);
        }

        // Render height config panel
        if (heightConfigPanelVisible) {
            heightConfigPanel.render(context, mouseX, mouseY, delta);
        }
    }

    private static void drawBorder(DrawContext context, int x, int y, int height) {
        // top
        context.fill(x, y, x + 240, y + 1, -1);
        // bottom
        context.fill(x, y + height - 1, x + 240, y + height, -1);
        // left
        context.fill(x, y, x + 1, y + height, -1);
        // right
        context.fill(x + 240 - 1, y, x + 240, y + height, -1);
    }

    private String getDisplayModeText() {
        // 确保显示模式不为null
        if (config.getDisplayMode() == null) {
            config.setDisplayMode(PushdozerConfig.DisplayMode.WIREFRAME);
        }
        return config.getDisplayMode().getDisplayText().getString();
    }

    private void toggleDisplayMode() {
        // 确保显示模式不为null
        if (config.getDisplayMode() == null) {
            config.setDisplayMode(PushdozerConfig.DisplayMode.WIREFRAME);
        }
        
        PushdozerConfig.DisplayMode[] modes = PushdozerConfig.DisplayMode.values();
        int nextIndex = (config.getDisplayMode().ordinal() + 1) % modes.length;
        PushdozerConfig.DisplayMode newMode = modes[nextIndex];
        config.setDisplayMode(newMode);
        displayModeButton.setMessage(getDisplayModeButtonText());
        PushdozerMod.saveConfig();
        showErrorMessage(Text.translatable("pushdozer.message.display_mode_switch", getDisplayModeText()).getString());
    }

    private void toggleWorkModeDropdown() {
        isWorkModeDropdownOpen = false;
        workModeOptions.forEach(this::remove);
        workModeOptions.clear();
    }

    private void renderWorkModeOptions(DrawContext context, int mouseX, int mouseY, float delta) {
        int dropdownHeight = workModeOptions.size() * 20;
        context.fill(workModeButton.getX(), workModeButton.getY() + 20,
                     workModeButton.getX() + workModeButton.getWidth(), 
                     workModeButton.getY() + 20 + dropdownHeight,
                     0xE0303030);

        for (ButtonWidget option : workModeOptions) {
            option.render(context, mouseX, mouseY, delta);
        }
    }

    /**
     * Determine if the interface should pause the game
     */
    @Override
    public boolean shouldPause() {
        return false;
    }

    /**
     * Override background render method, prevent default semi-transparent blurred background
     */
    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
        // Don't call super.renderBackground(), so default semi-transparent background won't be drawn
    }

    /**
     * Display error message
     * @param message Error message content
     */
    public void showErrorMessage(String message) {
        if (this.client != null && this.client.player != null) {
            this.client.player.sendMessage(Text.literal(message), false);
        }
    }

    public int getScreenWidth() {
        return this.width;
    }

    public int getScreenHeight() {
        return this.height;
    }

    private void applyAndClose() {
        // 确保在关闭前保存所有配置
        config.save();
        this.close();
    }

    private Text getGeometryButtonText() {
        return Text.translatable("pushdozer.button.geometry_format", config.getGeometryType().getDisplayText());
    }

    private Text getWorkModeText() {
        return Text.translatable("pushdozer.button.work_mode_format", config.getWorkMode().getDisplayText());
    }

    private Text getDisplayModeButtonText() {
        // 确保显示模式不为null
        if (config.getDisplayMode() == null) {
            config.setDisplayMode(PushdozerConfig.DisplayMode.WIREFRAME);
        }
        return Text.translatable("pushdozer.button.display_mode_format", config.getDisplayMode().getDisplayText());
    }

    /**
     * Display main panel
     */
    public void showMainPanel() {
        mainPanelVisible = true;
        // Show all components on main interface
        if (geometryButton != null) geometryButton.visible = true;
        if (geometryConfigButton != null) geometryConfigButton.visible = true;
        if (workModeButton != null) workModeButton.visible = true;
        if (workModeConfigButton != null) workModeConfigButton.visible = true;
        if (displayModeButton != null) displayModeButton.visible = true;
        if (distanceSlider != null) distanceSlider.visible = true;
        if (applyAndCloseButton != null) applyAndCloseButton.visible = true;
        if (heightConfigButton != null) heightConfigButton.visible = true;

        // Hide sub-panel
        hideSubPanel();
        // Hide geometry selection panel
        if (geometrySelectionPanel != null) geometrySelectionPanel.hide();
        // Hide work mode selection panel
        if (workModeSelectionPanel != null) workModeSelectionPanel.hide();
        // Hide work mode config panel
        if (currentWorkModeConfigPanel != null) currentWorkModeConfigPanel.hide();
        // Hide height config panel
        if (heightConfigPanel != null) heightConfigPanel.hide();
    }

    /**
     * Display height config panel
     */
    private void showHeightConfigPanel() {
        if (heightConfigPanel == null) {
            heightConfigPanel = new HeightConfigPanel(this, config);
        }
        heightConfigPanel.show();
        hideMainPanel();
    }

    /**
     * Update geometry preview
     */
    public void updatePreview() {
        // Removed message sending code
        // If other preview update logic is needed in the future, can be added here
    }

    /**
     * Get MinecraftClient instance
     */
    public MinecraftClient getClient() {
        return this.client;
    }

    public TextRenderer resolveTextRenderer() {
        if (this.client != null) {
            return this.client.textRenderer;
        }
        return MinecraftClient.getInstance().textRenderer;
    }
    /**
     * Hide sub-panel
     */
    public void hideSubPanel() {
        if (currentSubPanel != null) {
            currentSubPanel.hide();
            currentSubPanel = null;
        }
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubleClick) {
        double mouseX = click.x();
        double mouseY = click.y();
        // If height config panel is open, handle its events first
        if (heightConfigPanel != null && heightConfigPanel.isVisible()) {
            if (heightConfigPanel.mouseClicked(click)) {
                return true;
            }
        }

        // If geometry selection panel is open, handle its events first
        if (geometrySelectionPanel != null && geometrySelectionPanel.isVisible()) {
            if (geometrySelectionPanel.mouseClicked(click)) {
                return true;
            }
        }

        // If work mode config panel is open, handle its events first
        if (currentWorkModeConfigPanel != null && currentWorkModeConfigPanel.isVisible()) {
            if (currentWorkModeConfigPanel.mouseClicked(click)) {
                return true;
            }
        }

        // If work mode selection panel is open, handle its events first
        if (workModeSelectionPanel != null && workModeSelectionPanel.isVisible()) {
            if (workModeSelectionPanel.mouseClicked(click)) {
                return true;
            }
        }

        // Handle work mode dropdown list
        if (isWorkModeDropdownOpen) {
            for (ButtonWidget option : workModeOptions) {
                if (option.isMouseOver(mouseX, mouseY)) {
                    option.onPress(click);
                    return true;
                }
            }
            toggleWorkModeDropdown();
        }

        // Handle sub-panel events
        if (currentSubPanel != null && currentSubPanel.isVisible()) {
            for (Element widget : currentSubPanel.getWidgets()) {
                if (widget instanceof ClickableWidget clickable) {
                    if (clickable.isMouseOver(mouseX, mouseY)) {
                        return clickable.mouseClicked(click, doubleClick);
                    }
                }
            }
        }

        return super.mouseClicked(click, doubleClick);
    }

    @Override
    public boolean mouseDragged(Click click, double deltaX, double deltaY) {
        double mouseX = click.x();
        double mouseY = click.y();
        // Handle height config panel events
        if (heightConfigPanel != null && heightConfigPanel.isVisible()) {
            if (heightConfigPanel.mouseDragged(click, deltaX, deltaY)) {
                return true;
            }
        }

        // Handle geometry selection panel events
        if (geometrySelectionPanel != null && geometrySelectionPanel.isVisible()) {
            if (geometrySelectionPanel.mouseDragged(click, deltaX, deltaY)) {
                return true;
            }
        }

        // Handle work mode config panel events
        if (currentWorkModeConfigPanel != null && currentWorkModeConfigPanel.isVisible()) {
            if (currentWorkModeConfigPanel.mouseDragged(click, deltaX, deltaY)) {
                return true;
            }
        }

        // Handle work mode selection panel events
        if (workModeSelectionPanel != null && workModeSelectionPanel.isVisible()) {
            if (workModeSelectionPanel.mouseDragged(click, deltaX, deltaY)) {
                return true;
            }
        }

        if (currentSubPanel != null && currentSubPanel.isVisible()) {
            for (Element widget : currentSubPanel.getWidgets()) {
                if (widget instanceof ClickableWidget clickable) {
                    if (clickable.isMouseOver(mouseX, mouseY)) {
                        return clickable.mouseDragged(click, deltaX, deltaY);
                    }
                }
            }
        }
        return super.mouseDragged(click, deltaX, deltaY);
    }

    @Override
    public boolean mouseReleased(Click click) {
        double mouseX = click.x();
        double mouseY = click.y();
        // Handle height config panel events
        if (heightConfigPanel != null && heightConfigPanel.isVisible()) {
            if (heightConfigPanel.mouseReleased(click)) {
                return true;
            }
        }

        // Handle geometry selection panel events
        if (geometrySelectionPanel != null && geometrySelectionPanel.isVisible()) {
            if (geometrySelectionPanel.mouseReleased(click)) {
                return true;
            }
        }

        // Handle work mode config panel events
        if (currentWorkModeConfigPanel != null && currentWorkModeConfigPanel.isVisible()) {
            if (currentWorkModeConfigPanel.mouseReleased(click)) {
                return true;
            }
        }

        // Handle work mode selection panel events
        if (workModeSelectionPanel != null && workModeSelectionPanel.isVisible()) {
            if (workModeSelectionPanel.mouseReleased(click)) {
                return true;
            }
        }

        if (currentSubPanel != null && currentSubPanel.isVisible()) {
            for (Element widget : currentSubPanel.getWidgets()) {
                if (widget instanceof ClickableWidget clickable) {
                    if (clickable.isMouseOver(mouseX, mouseY)) {
                        return clickable.mouseReleased(click);
                    }
                }
            }
        }
        return super.mouseReleased(click);
    }

    /**
     * Custom distance slider class, provides public method to set value
     */
    private class CustomDistanceSlider extends SliderWidget {
        public CustomDistanceSlider(int x, int y, int width, int height, Text text, double value) {
            super(x, y, width, height, text, value);
        }

        @Override
        protected void updateMessage() {
            setMessage(Text.translatable("pushdozer.label.maximum_operation_distance",
                (int)(this.value * PushdozerConfig.MAX_OPERATION_DISTANCE)));
        }

        @Override
        protected void applyValue() {
            int newDistance = (int)(this.value * PushdozerConfig.MAX_OPERATION_DISTANCE);
            config.setMaxOperationDistance(newDistance);
        }

        public void setCustomValue(double value) {
            if (this.value != value) {
                this.value = value;
                this.updateMessage();
                this.applyValue();
            }
        }
    }
}