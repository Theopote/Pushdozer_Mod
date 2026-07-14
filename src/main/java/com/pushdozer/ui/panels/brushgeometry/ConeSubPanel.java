package com.pushdozer.ui.panels.brushgeometry;

import com.pushdozer.config.PushdozerConfig;
import com.pushdozer.ui.screens.PushdozerConfigScreen;
import net.minecraft.client.gui.DrawContext;

import net.minecraft.text.Text;

/**
 * Cone configuration sub-panel
 */
public class ConeSubPanel extends GeometrySubPanel {
    private CustomSliderWidget radiusSlider;
    private CustomSliderWidget heightSlider;

    public ConeSubPanel(PushdozerConfigScreen parent, PushdozerConfig config) {
        super(parent, config);
    }

    @Override
    public void initPanel() {
        // Base radius slider
        radiusSlider = addSlider(0, Text.translatable("pushdozer.config.radius"), config.getConeRadius());

        // Height slider
        heightSlider = addSlider(1, Text.translatable("pushdozer.dimension.height"), config.getConeHeight());
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        if (!isVisible()) return;

        // Render panel background, title background and border (optimized rendering order)
        renderPanelBackground(context);

        radiusSlider.render(context, mouseX, mouseY, delta);
        heightSlider.render(context, mouseX, mouseY, delta);
        confirmButton.render(context, mouseX, mouseY, delta);
        renderTitle(context, Text.translatable("pushdozer.panel.cone.title"));
    }

    @Override
    public void saveConfig() {
        if (radiusSlider != null) {
            config.setConeRadius(getSliderValue(radiusSlider));
        }
        if (heightSlider != null) {
            config.setConeHeight(getSliderValue(heightSlider));
        }
        persistPanelConfig();
    }



    @Override
    protected void updatePreview() {
        // Get current slider values
        int radius = getSliderValue(radiusSlider);
        int height = getSliderValue(heightSlider);

        // Update configuration without saving
        config.setConeRadius(radius);
        config.setConeHeight(height);

        // Notify parent screen to update preview
        parent.updatePreview();
    }
} 