package com.pushdozer.ui.panels.brushgeometry;

import com.pushdozer.config.PushdozerConfig;
import com.pushdozer.ui.screens.PushdozerConfigScreen;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;

/**
 * 椭球体配置子面板
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
        // 长度滑动条（X轴半径）
        lengthSlider = addSlider(0, Text.translatable("pushdozer.dimension.length"), config.getLength());
        
        // 宽度滑动条（Z轴半径）
        widthSlider = addSlider(1, Text.translatable("pushdozer.dimension.width"), config.getWidth());
        
        // 高度滑动条（Y轴半径）
        heightSlider = addSlider(2, Text.translatable("pushdozer.dimension.height"), config.getEllipsoidHeight());
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        if (!isVisible()) return;

        // 绘制面板背景、标题背景和边框（优化的渲染顺序）
        renderPanelBackground(context);

        // 渲染标题文本
        renderTitle(context, Text.translatable("pushdozer.panel.ellipsoid.title"));

        // 渲染长度、宽度和高度滑动条以及确认按钮
        lengthSlider.render(context, mouseX, mouseY, delta);
        widthSlider.render(context, mouseX, mouseY, delta);
        heightSlider.render(context, mouseX, mouseY, delta);
        confirmButton.render(context, mouseX, mouseY, delta);
    }

    @Override
    public void saveConfig() {
        try {
            // 获取滑动条的当前值
            if (lengthSlider != null) {
                int length = getSliderValue(lengthSlider);
                config.setLength(length);
            }
            if (widthSlider != null) {
                int width = getSliderValue(widthSlider);
                config.setWidth(width);
            }
            if (heightSlider != null) {
                int height = getSliderValue(heightSlider);
                config.setEllipsoidHeight(height);
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
        int length = getSliderValue(lengthSlider);
        int width = getSliderValue(widthSlider);
        int height = getSliderValue(heightSlider);

        // 更新配置但不保存
        config.setLength(length);
        config.setWidth(width);
        config.setEllipsoidHeight(height);

        // 通知父屏幕更新预览
        parent.updatePreview();
    }
} 