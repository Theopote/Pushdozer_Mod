package com.pushdozer.ui.panels.brushgeometry;

import com.pushdozer.config.PushdozerConfig;
import com.pushdozer.ui.screens.PushdozerConfigScreen;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;

/**
 * Ellipsoid configuration sub-panel
 */
public class EllipsoidSubPanel extends GeometrySubPanel {
    private CustomSliderWidget lengthSlider;
    private CustomSliderWidget widthSlider;
    private CustomSliderWidget heightSlider;

    public EllipsoidSubPanel(PushdozerConfigScreen parent, PushdozerConfig config) {
        super(parent, config);
    }

    @Override
    public void initPanel() {
        // Length slider (X-axis radius)
        lengthSlider = addSlider(0, Text.translatable("pushdozer.dimension.length"), config.getLength());

        // Width slider (Z-axis radius)
        widthSlider = addSlider(1, Text.translatable("pushdozer.dimension.width"), config.getWidth());

        // Height slider (Y-axis radius)
        heightSlider = addSlider(2, Text.translatable("pushdozer.dimension.height"), config.getEllipsoidHeight());
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        if (!isVisible()) return;

        // Render panel background, title background and border (optimized rendering order)
        renderPanelBackground(context);

        lengthSlider.render(context, mouseX, mouseY, delta);
        widthSlider.render(context, mouseX, mouseY, delta);
        heightSlider.render(context, mouseX, mouseY, delta);
        confirmButton.render(context, mouseX, mouseY, delta);
        renderTitle(context, Text.translatable("pushdozer.panel.ellipsoid.title"));
    }

    @Override
    public void saveConfig() {
        if (lengthSlider != null) {
            config.setLength(getSliderValue(lengthSlider));
        }
        if (widthSlider != null) {
            config.setWidth(getSliderValue(widthSlider));
        }
        if (heightSlider != null) {
            config.setEllipsoidHeight(getSliderValue(heightSlider));
        }
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
        config.setEllipsoidHeight(height);

        // Notify parent screen to update preview
        parent.updatePreview();
    }
} 