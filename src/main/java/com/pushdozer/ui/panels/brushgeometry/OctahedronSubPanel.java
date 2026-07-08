package com.pushdozer.ui.panels.brushgeometry;

import com.pushdozer.config.PushdozerConfig;
import com.pushdozer.ui.screens.PushdozerConfigScreen;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;

/**
 * 正八面体配置子面板
 */
public class OctahedronSubPanel extends GeometrySubPanel {
    private CustomSliderWidget radiusSlider;

    public OctahedronSubPanel(PushdozerConfigScreen parent, PushdozerConfig config) {
        super(parent, config);
    }

    @Override
    public void initPanel() {
        // 半径滑动条
        radiusSlider = addSlider(0, Text.translatable("pushdozer.config.radius"), config.getOctahedronRadius());
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        if (!isVisible()) return;

        // 绘制面板背景、标题背景和边框（优化的渲染顺序）
        renderPanelBackground(context);

        // 渲染标题文本
        renderTitle(context, Text.translatable("pushdozer.panel.octahedron.title"));

        // 渲染半径滑动条和确认按钮
        radiusSlider.render(context, mouseX, mouseY, delta);
        confirmButton.render(context, mouseX, mouseY, delta);
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
        // 获取当前滑动条的值
        int radius = getSliderValue(radiusSlider);

        // 更新配置但不保存
        config.setOctahedronRadius(radius);

        // 通知父屏幕更新预览
        parent.updatePreview();
    }
} 