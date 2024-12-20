package com.pushdozer.ui.panels;

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
        heightSlider = addSlider(2, Text.translatable("pushdozer.config.height"), config.getHeight());
        
        initConfirmButton();
        System.out.println("BoxSubPanel initialized");
    }

    @Override
    protected int getTotalSliders() {
        return 3; // 只有三个滑动条：长度、宽度和高度
    }

    /**
     * 渲染面板
     * @param context 绘图上下文
     * @param mouseX 鼠标X坐标
     * @param mouseY 鼠标Y坐标
     * @param delta 时间增量
     */
    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        if (!isVisible()) return;

        // 绘制面板背景
        context.fill(panelLeft, panelTop, panelLeft + PANEL_WIDTH, panelTop + PANEL_HEIGHT, 0xC0101010);

        // 渲染标题
        renderTitle(context, Text.translatable("pushdozer.panel.box.title"));

        // 绘制面板边框
        context.drawBorder(panelLeft, panelTop, PANEL_WIDTH, PANEL_HEIGHT, 0xFFFFFFFF);

        // 渲染各个滑动条和确认按钮
        lengthSlider.render(context, mouseX, mouseY, delta);
        widthSlider.render(context, mouseX, mouseY, delta);
        heightSlider.render(context, mouseX, mouseY, delta);
        confirmButton.render(context, mouseX, mouseY, delta);
    }

    /**
     * 保存配置
     * 获取滑动条的值并更新配置
     */
    @Override
    protected void saveConfig() {
        System.out.println("Saving config in BoxSubPanel");
        try {
            // 获取滑动条的当前值
            int length = getSliderValue(lengthSlider);
            int width = getSliderValue(widthSlider);
            int height = getSliderValue(heightSlider);

            System.out.println("Slider values: length=" + length + ", width=" + width + ", height=" + height);

            // 更新配置
            config.setLength(length);
            config.setWidth(width);
            config.setHeight(height);
            
            System.out.println("Values set in config");

            // 保存配置
            config.save();
            System.out.println("Config saved");

            // 使用翻译键显示保存成功消息
            parent.showErrorMessage(Text.translatable("pushdozer.config.saved").getString());
            System.out.println("Error message shown");

            // 隐藏子面板
            parent.hideSubPanel();
            System.out.println("Sub panel hidden");
        } catch (Exception e) {
            System.out.println("Unexpected error: " + e.getMessage());
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
        config.setHeight(height);

        // 通知父屏幕更新预览
        parent.updatePreview();
    }
}