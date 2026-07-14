package com.pushdozer.ui.panels.brushgeometry;

import com.pushdozer.config.PushdozerConfig;
import com.pushdozer.ui.screens.PushdozerConfigScreen;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;

/**
 * BoxSubPanel class
 * This class extends GeometrySubPanel and handles the configuration interface for box shapes.
 * It contains three sliders for length, width, and height to adjust the dimensions of the box.
 */
public class BoxSubPanel extends GeometrySubPanel {
    // Length, width, and height sliders
    private CustomSliderWidget lengthSlider;
    private CustomSliderWidget widthSlider;
    private CustomSliderWidget heightSlider;

    /**
     * Constructor
     * @param parent Parent configuration screen
     * @param config Pushdozer config object
     */
    public BoxSubPanel(PushdozerConfigScreen parent, PushdozerConfig config) {
        super(parent, config);
    }

    /**
     * Initialize panel
     * Create and add sliders for length, width, and height, as well as confirm button
     */
    @Override
    public void initPanel() {
        lengthSlider = addSlider(0, Text.translatable("pushdozer.config.length"), config.getLength());
        widthSlider = addSlider(1, Text.translatable("pushdozer.config.width"), config.getWidth());
        heightSlider = addSlider(2, Text.translatable("pushdozer.config.height"), config.getBoxHeight());
        
        System.out.println("BoxSubPanel initialized");
    }

    /**
     * Render panel (using optimized rendering order)
     * @param context Draw context
     * @param mouseX Mouse X coordinate
     * @param mouseY Mouse Y coordinate
     * @param delta Time delta
     */
    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        if (!isVisible()) return;

        // 1-3. Render panel background, title background and border (optimized rendering order)
        renderPanelBackground(context);

        lengthSlider.render(context, mouseX, mouseY, delta);
        widthSlider.render(context, mouseX, mouseY, delta);
        heightSlider.render(context, mouseX, mouseY, delta);
        confirmButton.render(context, mouseX, mouseY, delta);
        renderTitle(context, Text.translatable("pushdozer.panel.box.title"));
    }

    /**
     * Save configuration
     * Get slider values and update configuration
     */
    @Override
    public void saveConfig() {
        config.setLength(getSliderValue(lengthSlider));
        config.setWidth(getSliderValue(widthSlider));
        config.setBoxHeight(getSliderValue(heightSlider));
        persistPanelConfig();
    }

    @Override
    protected void updatePreview() {
        // Get current slider values
        int length = getSliderValue(lengthSlider);
        int width = getSliderValue(widthSlider);
        int height = getSliderValue(heightSlider);

        // Update configuration without saving
        config.setLength(length);
        config.setWidth(width);
        config.setBoxHeight(height);

        // Notify parent screen to update preview
        parent.updatePreview();
    }
}