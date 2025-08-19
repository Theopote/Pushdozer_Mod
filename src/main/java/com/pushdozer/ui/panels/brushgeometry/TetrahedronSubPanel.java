package com.pushdozer.ui.panels.brushgeometry;

import com.pushdozer.config.PushdozerConfig;
import com.pushdozer.ui.screens.PushdozerConfigScreen;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;

/**
 * 正四面体配置子面板
 * 包含边长滑动条，用于调整正四面体的大小
 */
public class TetrahedronSubPanel extends GeometrySubPanel {
    private CustomSliderWidget edgeLengthSlider;

    public TetrahedronSubPanel(PushdozerConfigScreen parent, PushdozerConfig config) {
        super(parent, config);
    }

    @Override
    public void initPanel() {
        // 边长滑动条
        edgeLengthSlider = addSlider(0, Text.translatable("pushdozer.config.edge_length"), config.getTetrahedronEdgeLength());
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        if (!isVisible()) return;

        // 绘制面板背景、标题背景和边框（优化的渲染顺序）
        renderPanelBackground(context);

        // 渲染标题文本
        renderTitle(context, Text.translatable("pushdozer.panel.tetrahedron.title"));

        // 渲染边长滑动条和确认按钮
        edgeLengthSlider.render(context, mouseX, mouseY, delta);
        confirmButton.render(context, mouseX, mouseY, delta);
    }

    @Override
    public void saveConfig() {
        try {
            // 获取滑动条的当前值
            if (edgeLengthSlider != null) {
                int edgeLength = getSliderValue(edgeLengthSlider);
                config.setTetrahedronEdgeLength(edgeLength);
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
        int edgeLength = getSliderValue(edgeLengthSlider);

        // 更新配置但不保存
        config.setTetrahedronEdgeLength(edgeLength);

        // 通知父屏幕更新预览
        parent.updatePreview();
    }
}
