package com.pushdozer.ui.panels.brushgeometry;

import com.pushdozer.config.PushdozerConfig;
import com.pushdozer.ui.screens.PushdozerConfigScreen;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;

/**
 * Triangular prism configuration sub-panel
 * Contains side length and height sliders to adjust the dimensions of the triangular prism
 */
public class TriangularPrismSubPanel extends GeometrySubPanel {
    private CustomSliderWidget sideLengthSlider;
    private CustomSliderWidget heightSlider;

    public TriangularPrismSubPanel(PushdozerConfigScreen parent, PushdozerConfig config) {
        super(parent, config);
    }

    @Override
    public void initPanel() {
        // Side length slider
        sideLengthSlider = addSlider(0, Text.translatable("pushdozer.config.side_length"), config.getTriangularPrismSideLength());
        // Height slider
        heightSlider = addSlider(1, Text.translatable("pushdozer.config.height"), config.getTriangularPrismHeight());
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        if (!isVisible()) return;

        // Render panel background, title background and border (optimized rendering order)
        renderPanelBackground(context);

        sideLengthSlider.render(context, mouseX, mouseY, delta);
        heightSlider.render(context, mouseX, mouseY, delta);
        confirmButton.render(context, mouseX, mouseY, delta);
        renderTitle(context, Text.translatable("pushdozer.panel.triangular_prism.title"));
    }

    @Override
    public void saveConfig() {
        if (sideLengthSlider != null && heightSlider != null) {
            config.setTriangularPrismSideLength(getSliderValue(sideLengthSlider));
            config.setTriangularPrismHeight(getSliderValue(heightSlider));
        }
        persistPanelConfig();
    }

    @Override
    protected void updatePreview() {
        // Get current slider values
        int sideLength = getSliderValue(sideLengthSlider);
        int height = getSliderValue(heightSlider);

        // Update configuration without saving
        config.setTriangularPrismSideLength(sideLength);
        config.setTriangularPrismHeight(height);

        // Notify parent screen to update preview
        parent.updatePreview();
    }
}
