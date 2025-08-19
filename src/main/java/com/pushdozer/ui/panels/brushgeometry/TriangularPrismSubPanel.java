package com.pushdozer.ui.panels.brushgeometry;

import com.pushdozer.config.PushdozerConfig;
import com.pushdozer.ui.screens.PushdozerConfigScreen;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;

/**
 * 三棱柱配置子面板
 * 包含边长和高度滑动条，用于调整三棱柱的尺寸
 */
public class TriangularPrismSubPanel extends GeometrySubPanel {
    private CustomSliderWidget sideLengthSlider;
    private CustomSliderWidget heightSlider;

    public TriangularPrismSubPanel(PushdozerConfigScreen parent, PushdozerConfig config) {
        super(parent, config);
    }

    @Override
    public void initPanel() {
        // 边长滑动条
        sideLengthSlider = addSlider(0, Text.translatable("pushdozer.config.side_length"), config.getTriangularPrismSideLength());
        // 高度滑动条
        heightSlider = addSlider(1, Text.translatable("pushdozer.config.height"), config.getTriangularPrismHeight());
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        if (!isVisible()) return;

        // 绘制面板背景、标题背景和边框（优化的渲染顺序）
        renderPanelBackground(context);

        // 渲染标题文本
        renderTitle(context, Text.translatable("pushdozer.panel.triangular_prism.title"));

        // 渲染边长滑动条、高度滑动条和确认按钮
        sideLengthSlider.render(context, mouseX, mouseY, delta);
        heightSlider.render(context, mouseX, mouseY, delta);
        confirmButton.render(context, mouseX, mouseY, delta);
    }

    @Override
    public void saveConfig() {
        try {
            // 获取滑动条的当前值
            if (sideLengthSlider != null && heightSlider != null) {
                int sideLength = getSliderValue(sideLengthSlider);
                int height = getSliderValue(heightSlider);
                config.setTriangularPrismSideLength(sideLength);
                config.setTriangularPrismHeight(height);
            }

            // 保存配置
            config.save();

            // 使用翻译键显示保存成功消息
            parent.showErrorMessage(Text.translatable("pushdozer.config.saved").getString());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    protected void updatePreview() {
        // 获取当前滑动条的值
        int sideLength = getSliderValue(sideLengthSlider);
        int height = getSliderValue(heightSlider);

        // 更新配置但不保存
        config.setTriangularPrismSideLength(sideLength);
        config.setTriangularPrismHeight(height);

        // 通知父屏幕更新预览
        parent.updatePreview();
    }
}
