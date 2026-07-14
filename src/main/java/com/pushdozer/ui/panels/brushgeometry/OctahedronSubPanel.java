package com.pushdozer.ui.panels.brushgeometry;

import com.pushdozer.config.PushdozerConfig;
import com.pushdozer.ui.screens.PushdozerConfigScreen;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;

/**
 * Octahedron configuration sub-panel
 */
public class OctahedronSubPanel extends GeometrySubPanel {
    private CustomSliderWidget radiusSlider;

    public OctahedronSubPanel(PushdozerConfigScreen parent, PushdozerConfig config) {
        super(parent, config);
    }

    @Override
    public void initPanel() {
        // Radius slider
        radiusSlider = addSlider(0, Text.translatable("pushdozer.config.radius"), config.getOctahedronRadius());
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        if (!isVisible()) return;

        // Render panel background, title background and border (optimized rendering order)
        renderPanelBackground(context);

        radiusSlider.render(context, mouseX, mouseY, delta);
        confirmButton.render(context, mouseX, mouseY, delta);
        renderTitle(context, Text.translatable("pushdozer.panel.octahedron.title"));
    }

    @Override
    public void saveConfig() {
        if (radiusSlider != null) {
            config.setOctahedronRadius(getSliderValue(radiusSlider));
        }
        persistPanelConfig();
    }



    @Override
    protected void updatePreview() {
        // Get current slider value
        int radius = getSliderValue(radiusSlider);

        // Update configuration without saving
        config.setOctahedronRadius(radius);

        // Notify parent screen to update preview
        parent.updatePreview();
    }
} 