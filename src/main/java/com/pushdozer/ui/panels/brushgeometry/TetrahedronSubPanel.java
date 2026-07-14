package com.pushdozer.ui.panels.brushgeometry;

import com.pushdozer.config.PushdozerConfig;
import com.pushdozer.ui.screens.PushdozerConfigScreen;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;

/**
 * Tetrahedron configuration sub-panel
 * Contains edge length slider to adjust the size of the tetrahedron
 */
public class TetrahedronSubPanel extends GeometrySubPanel {
    private CustomSliderWidget edgeLengthSlider;

    public TetrahedronSubPanel(PushdozerConfigScreen parent, PushdozerConfig config) {
        super(parent, config);
    }

    @Override
    public void initPanel() {
        // Edge length slider
        edgeLengthSlider = addSlider(0, Text.translatable("pushdozer.config.edge_length"), config.getTetrahedronEdgeLength());
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        if (!isVisible()) return;

        // Render panel background, title background and border (optimized rendering order)
        renderPanelBackground(context);

        edgeLengthSlider.render(context, mouseX, mouseY, delta);
        confirmButton.render(context, mouseX, mouseY, delta);
        renderTitle(context, Text.translatable("pushdozer.panel.tetrahedron.title"));
    }

    @Override
    public void saveConfig() {
        if (edgeLengthSlider != null) {
            config.setTetrahedronEdgeLength(getSliderValue(edgeLengthSlider));
        }
        persistPanelConfig();
    }

    @Override
    protected void updatePreview() {
        // Get current slider value
        int edgeLength = getSliderValue(edgeLengthSlider);

        // Update configuration without saving
        config.setTetrahedronEdgeLength(edgeLength);

        // Notify parent screen to update preview
        parent.updatePreview();
    }
}
