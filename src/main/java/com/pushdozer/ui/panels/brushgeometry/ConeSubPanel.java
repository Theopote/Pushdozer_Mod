package com.pushdozer.ui.panels.brushgeometry;

import com.pushdozer.config.PushdozerConfig;
import com.pushdozer.ui.screens.PushdozerConfigScreen;
import net.minecraft.client.gui.DrawContext;

import net.minecraft.text.Text;

/**
 * 圆锥体配置子面板
 */
public class ConeSubPanel extends GeometrySubPanel {
    private CustomSliderWidget radiusSlider;
    private CustomSliderWidget heightSlider;

    public ConeSubPanel(PushdozerConfigScreen parent, PushdozerConfig config) {
        super(parent, config);
    }

    @Override
    public void initPanel() {
        // 底部半径滑动条
        radiusSlider = addSlider(0, Text.translatable("pushdozer.config.radius"), config.getConeRadius());
        
        // 高度滑动条
        heightSlider = addSlider(1, Text.translatable("pushdozer.dimension.height"), config.getConeHeight());
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        if (!isVisible()) return;

        // 绘制面板背景、标题背景和边框（优化的渲染顺序）
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
        // 获取当前滑动条的值
        int radius = getSliderValue(radiusSlider);
        int height = getSliderValue(heightSlider);

        // 更新配置但不保存
        config.setConeRadius(radius);
        config.setConeHeight(height);

        // 通知父屏幕更新预览
        parent.updatePreview();
    }
} 