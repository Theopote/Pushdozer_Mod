package com.pushdozer.ui.panels.brushgeometry;

import com.pushdozer.config.PushdozerConfig;
import com.pushdozer.ui.screens.PushdozerConfigScreen;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;

/**
 * SphereSubPanel 类
 * 这个类继承自 GeometrySubPanel，用于处理球形的配置界面。
 * 它包含一个半径滑动条，用于调整球体的大小。
 */
public class SphereSubPanel extends GeometrySubPanel {
    // 半径滑动条
    private CustomSliderWidget radiusSlider;

    /**
     * 构造函数
     * @param parent 父级配置屏幕
     * @param config Pushdozer配置对象
     */
    public SphereSubPanel(PushdozerConfigScreen parent, PushdozerConfig config) {
        super(parent, config);
    }

    /**
     * 初始化面板
     * 创建并添加半径滑动条和确认按钮
     */
    @Override
    public void initPanel() {
        radiusSlider = addSlider(0, Text.translatable("pushdozer.config.radius"), config.getSphereRadius());

    }

    /**
     * 渲染面板
     * @param context 绘图��下文
     * @param mouseX 鼠标X坐标
     * @param mouseY 鼠标Y坐标
     * @param delta 时间增量
     */
    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        if (!isVisible()) return;

        // 1-3. 绘制面板背景、标题背景和边框（优化的渲染顺序）
        renderPanelBackground(context);

        radiusSlider.render(context, mouseX, mouseY, delta);
        confirmButton.render(context, mouseX, mouseY, delta);
        renderTitle(context, Text.translatable("pushdozer.panel.sphere.title"));
    }

    /**
     * 保存配置
     * 获取滑动条的值并更新配置
     */
    @Override
    public void saveConfig() {
        int radius = getSliderValue(radiusSlider);
        config.setSphereRadius(radius);
        persistPanelConfig();
    }



    @Override
    protected void updatePreview() {
        // 获取当前滑动条的值
        int radius = getSliderValue(radiusSlider);

        // 更新配置但不保存
        config.setSphereRadius(radius);

        // 通知父屏幕更新预览
        parent.updatePreview();
    }
}