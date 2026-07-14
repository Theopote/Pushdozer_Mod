package com.pushdozer.ui.panels.brushgeometry;

import com.pushdozer.config.PushdozerConfig;
import com.pushdozer.ui.screens.PushdozerConfigScreen;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;

/**
 * SphereSubPanel class
 * This class extends GeometrySubPanel and handles the configuration interface for spheres.
 * It contains a radius slider to adjust the size of the sphere.
 */
public class SphereSubPanel extends GeometrySubPanel {
    // Radius slider
    private CustomSliderWidget radiusSlider;

    /**
     * Constructor
     * @param parent Parent configuration screen
     * @param config Pushdozer config object
     */
    public SphereSubPanel(PushdozerConfigScreen parent, PushdozerConfig config) {
        super(parent, config);
    }

    /**
     * Initialize panel
     * Create and add radius slider and confirm button
     */
    @Override
    public void initPanel() {
        radiusSlider = addSlider(0, Text.translatable("pushdozer.config.radius"), config.getSphereRadius());

    }

    /**
     * Render panel
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

        radiusSlider.render(context, mouseX, mouseY, delta);
        confirmButton.render(context, mouseX, mouseY, delta);
        renderTitle(context, Text.translatable("pushdozer.panel.sphere.title"));
    }

    /**
     * Save configuration
     * Get slider values and update configuration
     */
    @Override
    public void saveConfig() {
        int radius = getSliderValue(radiusSlider);
        config.setSphereRadius(radius);
        persistPanelConfig();
    }



    @Override
    protected void updatePreview() {
        // Get current slider value
        int radius = getSliderValue(radiusSlider);

        // Update configuration without saving
        config.setSphereRadius(radius);

        // Notify parent screen to update preview
        parent.updatePreview();
    }
}