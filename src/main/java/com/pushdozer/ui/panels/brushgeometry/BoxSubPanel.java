package com.pushdozer.ui.panels.brushgeometry;

import com.pushdozer.config.PushdozerConfig;
import com.pushdozer.ui.screens.PushdozerConfigScreen;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;

/**
 * BoxSubPanel 类
 * 这个类继承自 GeometrySubPanel，用于处理长方体形状的配置界面。
 * 它包含了长度、宽度和高度三个滑动条，用于调整长方体的尺寸。
 */
public class BoxSubPanel extends GeometrySubPanel {
    // 长度、宽度和高度的滑动条
    private CustomSliderWidget lengthSlider;
    private CustomSliderWidget widthSlider;
    private CustomSliderWidget heightSlider;

    /**
     * 构造函数
     * @param parent 父级配置屏幕
     * @param config Pushdozer配置对象
     */
    public BoxSubPanel(PushdozerConfigScreen parent, PushdozerConfig config) {
        super(parent, config);
    }

    /**
     * 初始化面板
     * 创建并添加长度、宽度和高度的滑动条，以及确认按钮
     */
    @Override
    public void initPanel() {
        lengthSlider = addSlider(0, Text.translatable("pushdozer.config.length"), config.getLength());
        widthSlider = addSlider(1, Text.translatable("pushdozer.config.width"), config.getWidth());
        heightSlider = addSlider(2, Text.translatable("pushdozer.config.height"), config.getBoxHeight());
        
        System.out.println("BoxSubPanel initialized");
    }

    /**
     * 渲染面板（使用优化的渲染顺序）
     * @param context 绘图上下文
     * @param mouseX 鼠标X坐标
     * @param mouseY 鼠标Y坐标
     * @param delta 时间增量
     */
    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        if (!isVisible()) return;

        // 1-3. 绘制面板背景、标题背景和边框（优化的渲染顺序）
        renderPanelBackground(context);

        lengthSlider.render(context, mouseX, mouseY, delta);
        widthSlider.render(context, mouseX, mouseY, delta);
        heightSlider.render(context, mouseX, mouseY, delta);
        confirmButton.render(context, mouseX, mouseY, delta);
        renderTitle(context, Text.translatable("pushdozer.panel.box.title"));
    }

    /**
     * 保存配置
     * 获取滑动条的值并更新配置
     */
    @Override
    public void saveConfig() {
        config.setLength(getSliderValue(lengthSlider));
        config.setWidth(getSliderValue(widthSlider));
        config.setBoxHeight(getSliderValue(heightSlider));
        persistPanelConfig();
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
        config.setBoxHeight(height);

        // 通知父屏幕更新预览
        parent.updatePreview();
    }
}