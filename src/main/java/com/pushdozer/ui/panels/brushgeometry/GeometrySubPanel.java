package com.pushdozer.ui.panels.brushgeometry;

import com.pushdozer.config.PushdozerConfig;
import com.pushdozer.ui.screens.PushdozerConfigScreen;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * GeometrySubPanel 是所有几何形状子面板的基类，负责管理公共的配置和组件。
 * 具体的几何形状（如 Box, Sphere 等）将继承自此类并实现特定的逻辑。
 */
public abstract class GeometrySubPanel {
    protected PushdozerConfigScreen parent;        // 父屏幕，用于交互和布局
    protected PushdozerConfig config;              // 配置对象，存储各种设置
    protected List<Element> widgets = new ArrayList<>(); // 存储面板中的所有组件
    protected ButtonWidget confirmButton;          // 确认按钮
    protected TextRenderer textRenderer;           // 文本渲染器字段

    // 布局常量
    protected static final int PANEL_WIDTH = 210;           // 面板的宽度（统一加宽40）
    protected static final int TITLE_HEIGHT = 20;           // 标题高度常量
    // 滑动条和按钮宽度按面板宽度与边距动态计算
    protected static final int SLIDER_HEIGHT = 20;          // 滑动条高度
    protected static final int WIDGET_MARGIN_VERTICAL = 5;  // 组件之间的垂直间距
    protected static final int WIDGET_MARGIN_HORIZONTAL = 5; // 组件水平边距
    protected static final int CONFIRM_BUTTON_HEIGHT = 20;  // 确认按钮高度
    protected static final int CONFIRM_BUTTON_MARGIN = 5;   // 确认按钮边距
    protected static final int MAX_VALUE = 64;               // 最大值常量
    
    protected int panelLeft, panelTop;              // 面板的左上角坐标
    private boolean visible = false;                // 面板是否可见

    /**
     * 构造函数，初始化父屏幕和配置对象。
     *
     * @param parent  父屏幕对象
     * @param config  配置对象
     */
    public GeometrySubPanel(PushdozerConfigScreen parent, PushdozerConfig config) {
        this.parent = parent;
        this.config = config;
        this.textRenderer = MinecraftClient.getInstance().textRenderer;
    }

    /**
     * 初始化面板，包括计算位置和创建组件。
     * 此方法会在屏幕首次打开和每次窗口尺寸改变时被调用。
     */
    public void init() {
        // 清空旧的组件，为重新创建做准备
        this.widgets.clear();

        // 调用子类和基类的初始化逻辑
        this.initPanel();
        this.initConfirmButton();
        
        // 计算面板位置（居中），使用动态高度
        this.panelLeft = (parent.getScreenWidth() - PANEL_WIDTH) / 2;
        this.panelTop = (parent.getScreenHeight() - getPanelHeight()) / 2;
        
        // 重新初始化控件位置，确保位置正确
        updateWidgetPositions();
    }

    /**
     * 获取面板高度，根据控件数量动态计算
     */
    protected int getPanelHeight() {
        // 计算动态高度：标题高度 + 控件总高度 + 确认按钮高度 + 确认按钮下边距
        int contentHeight = calculateContentHeight();
        return TITLE_HEIGHT + contentHeight + CONFIRM_BUTTON_HEIGHT + CONFIRM_BUTTON_MARGIN;
    }

    /**
     * 计算内容区域高度
     */
    protected int calculateContentHeight() {
        int widgetCount = widgets.size() - 1; // 减去确认按钮
        if (widgetCount <= 0) {
            return WIDGET_MARGIN_VERTICAL * 2; // 至少保留上下边距
        }
        
        // 计算控件总高度：
        // - 控件本身的高度：widgetCount * SLIDER_HEIGHT
        // - 控件之间的间距：(widgetCount - 1) * WIDGET_MARGIN_VERTICAL
        // - 第一个控件与标题的间距：WIDGET_MARGIN_VERTICAL
        // - 最后一个控件与确认按钮的间距：WIDGET_MARGIN_VERTICAL
        return widgetCount * SLIDER_HEIGHT + (widgetCount - 1) * WIDGET_MARGIN_VERTICAL + WIDGET_MARGIN_VERTICAL + WIDGET_MARGIN_VERTICAL;
    }

    /**
     * 更新控件位置
     */
    protected void updateWidgetPositions() {
        // 重新计算确认按钮位置
        if (confirmButton != null) {
            int confirmButtonY = panelTop + getPanelHeight() - CONFIRM_BUTTON_HEIGHT - CONFIRM_BUTTON_MARGIN;
            confirmButton.setPosition(panelLeft + WIDGET_MARGIN_HORIZONTAL, confirmButtonY);
            confirmButton.setWidth(PANEL_WIDTH - (2 * WIDGET_MARGIN_HORIZONTAL));
        }
    }

    /**
     * 初始化面板的方法，由子类实现具体的组件添加逻辑。
     */
    public abstract void initPanel();

    /**
     * 渲染面板的方法，由子类实现具体的渲染逻辑。
     *
     * @param context  渲染上下文
     * @param mouseX   鼠标X坐标
     * @param mouseY   鼠标Y坐标
     * @param delta    渲染间隔时间
     */
    public abstract void render(DrawContext context, int mouseX, int mouseY, float delta);

    /**
     * 保存配置的方法，由子类实现具体的保存逻辑。
     */
    public abstract void saveConfig();

    /**
     * CustomSliderWidget 是自定义的滑动条组件，继承自 SliderWidget。
     * 它用于在界面上显示和调整特定的数值参数（如长度、宽度、高度、半径等）。
     */
    protected static class CustomSliderWidget extends SliderWidget {
        private final String label;                    // 滑动条的标签，例如 "长度"
        private final int min;                         // 滑动条的最小值
        private final int max;                         // 滑动条的最大值
        private final Consumer<Integer> onValueChange; // 值改变时的回调

        /**
         * 构造函数，初始化滑动条的属性。
         *
         * @param x              滑动条的X坐标
         * @param y              滑动条的Y坐标
         * @param width          滑动条的宽度
         * @param height         滑动条的高度
         * @param label          滑动条的标签
         * @param min            滑动条的最小值
         * @param max            滑动条的最大值
         * @param initialValue   滑动条的初始值
         * @param onValueChange  值改变时的回调
         */
        public CustomSliderWidget(int x, int y, int width, int height, String label, int min, int max, 
                                int initialValue, Consumer<Integer> onValueChange) {
            super(x, y, width, height, Text.empty(), (double) (initialValue - min) / (max - min));
            this.label = label;
            this.min = min;
            this.max = max;
            this.onValueChange = onValueChange;
            updateMessage(); // 首次调用以设置初始文本
        }

        /**
         * 更新滑动条上显示的消息，显示当前的数值。
         */
        @Override
        protected void updateMessage() {
            this.setMessage(Text.of(this.label + ": " + this.getIntValue()));
        }

        /**
         * 当滑动条的值改变时调用，用于更新显示和执行相关逻辑。
         */
        @Override
        protected void applyValue() {
            if (this.onValueChange != null) {
                this.onValueChange.accept(getIntValue()); // 执行回调，并传入当前值
            }
        }

        /**
         * 获取滑动条当前的整数值，根据滑动条的进度计算。
         *
         * @return 当前的整数值
         */
        public int getIntValue() {
            return (int) Math.round(MathHelper.lerp(this.value, this.min, this.max));
        }
    }

    /**
     * 添加滑动条
     * @param index 滑动条索引
     * @param label 滑动条标签
     * @param value 初始值
     * @return 创建的滑动条
     */
    protected CustomSliderWidget addSlider(int index, Text label, int value) {
        // 计算滑动条位置：标题下方 + 索引 * (滑动条高度 + 间距) + 间距
        int y = panelTop + TITLE_HEIGHT + WIDGET_MARGIN_VERTICAL + (index * (SLIDER_HEIGHT + WIDGET_MARGIN_VERTICAL));
        CustomSliderWidget slider = new CustomSliderWidget(
            panelLeft + WIDGET_MARGIN_HORIZONTAL,
            y,
            PANEL_WIDTH - (2 * WIDGET_MARGIN_HORIZONTAL),
            SLIDER_HEIGHT,
            label.getString(),
            1,
            MAX_VALUE,
            value,
            (newValue) -> this.updatePreview() // 传递一个 Lambda 作为回调
        );
        widgets.add(slider);
        return slider;
    }

    /**
     * 获取滑动条的当前值。
     *
     * @param slider 要获取值的滑动条
     * @return 滑动条的当前整数值
     */
    protected int getSliderValue(CustomSliderWidget slider) {
        return slider.getIntValue();
    }

    /**
     * 显示面板。
     */
    public void show() {
        visible = true;
        // 重新计算位置以防窗口大小改变
        this.panelLeft = (parent.getScreenWidth() - PANEL_WIDTH) / 2;
        this.panelTop = (parent.getScreenHeight() - getPanelHeight()) / 2;
        init(); // 重新初始化控件，确保位置正确
    }

    /**
     * 隐藏面板。
     */
    public void hide() {
        visible = false;
    }

    /**
     * 检查面板是否可见。
     *
     * @return 如果面板可见，则返回 true；否则返回 false
     */
    public boolean isVisible() {
        return visible;
    }

    /**
     * 初始化确认按钮，将其添加到面板中。
     */
    protected void initConfirmButton() {
        // 计算确认按钮位置
        int confirmButtonY = panelTop + getPanelHeight() - CONFIRM_BUTTON_HEIGHT - CONFIRM_BUTTON_MARGIN;
        
        confirmButton = ButtonWidget.builder(
            Text.translatable("pushdozer.button.done"), 
            button -> {
                saveConfig();
                closeSubPanel();
            })
            .dimensions(
                panelLeft + WIDGET_MARGIN_HORIZONTAL,
                confirmButtonY,
                PANEL_WIDTH - (2 * WIDGET_MARGIN_HORIZONTAL),
                CONFIRM_BUTTON_HEIGHT)
            .build();
        widgets.add(confirmButton);
    }

    /**
     * 关闭子面板，隐藏当前面板并通知父屏幕隐藏子面板。
     */
    protected void closeSubPanel() {
        hide();                   // 隐藏当前面板
        parent.hideSubPanel();    // 通知父屏幕隐藏子面板
        parent.showMainPanel();   // 显示主界面
    }

    /**
     * 获取面板中的所有组件。
     *
     * @return 组件列表
     */
    public List<Element> getWidgets() {
        return widgets;
    }

    /**
     * 处理鼠标点击事件。
     */
    public boolean mouseClicked(Click click) {
        for (Element widget : widgets) {
            if (widget.mouseClicked(click, false)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 处理鼠标拖动事件。
     */
    public boolean mouseDragged(Click click, double deltaX, double deltaY) {
        for (Element widget : widgets) {
            if (widget.mouseDragged(click, deltaX, deltaY)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 渲染面板背景和边框（优化的渲染顺序）
     * @param context 绘图上下文
     */
    protected void renderPanelBackground(DrawContext context) {
        // 1. 绘制面板背景（半透明）
        context.fill(panelLeft, panelTop, panelLeft + PANEL_WIDTH, panelTop + getPanelHeight(), 0xC0101010);
        
        // 2. 绘制标题栏背景
        context.fill(panelLeft, panelTop, panelLeft + PANEL_WIDTH, panelTop + TITLE_HEIGHT, 0xE0303030);
        
        // 3. 绘制面板边框（在标题背景之后，确保边框不被遮挡）
        drawBorder(context, panelLeft, panelTop, getPanelHeight());
    }

    protected static void drawBorder(DrawContext context, int x, int y, int height) {
        // top
        context.fill(x, y, x + GeometrySubPanel.PANEL_WIDTH, y + 1, -1);
        // bottom
        context.fill(x, y + height - 1, x + GeometrySubPanel.PANEL_WIDTH, y + height, -1);
        // left
        context.fill(x, y, x + 1, y + height, -1);
        // right
        context.fill(x + GeometrySubPanel.PANEL_WIDTH - 1, y, x + GeometrySubPanel.PANEL_WIDTH, y + height, -1);
    }

    /**
     * 渲染标题文本
     * @param context 绘图上下文
     * @param title 标题文本
     */
    protected void renderTitle(DrawContext context, Text title) {
        if (textRenderer != null) {
            int titleWidth = textRenderer.getWidth(title);
            context.drawText(
                textRenderer,
                title,
                panelLeft + (PANEL_WIDTH - titleWidth) / 2,
                panelTop + WIDGET_MARGIN_VERTICAL,
                0xFFFFFF,
                false
            );
        }
    }

    /**
     * 更新预览的抽象方法，由子类实现具体的预览逻辑
     */
    protected abstract void updatePreview();
}