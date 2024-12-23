package com.pushdozer.ui.panels;

import com.pushdozer.config.PushdozerConfig;
import com.pushdozer.ui.screens.PushdozerConfigScreen;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;

import java.util.ArrayList;
import java.util.List;

/**
 * GeometrySubPanel 是所有几何形状子面板的基类，负责管理公共的配置和组件。
 * 具体的几何形状（如 Box, Sphere 等）将继承自此类并实现特定的逻辑。
 */
public abstract class GeometrySubPanel {
    protected PushdozerConfigScreen parent;        // 父屏幕，用于交互和布局
    protected PushdozerConfig config;              // 配置对象，存储各种设置
    protected List<Element> widgets = new ArrayList<>(); // 存储面板中的所有组件
    protected ButtonWidget confirmButton;          // 确认按钮
    protected TextRenderer textRenderer;  // 添加文本渲染器字段

    protected static final int PANEL_WIDTH = 170;   // 面板的宽度
    protected static final int PANEL_HEIGHT = 130;  // 面板的高度
    protected static final int TITLE_HEIGHT = 20;  // 添加标题高度常量
    protected static final int SLIDER_WIDTH = 150;  // 滑动条宽度
    protected static final int SLIDER_HEIGHT = 20;  // 滑动条高度
    protected static final int SLIDER_MARGIN = 5;  // 滑动条之间的垂直间距
    protected static final int MAX_VALUE = 32;     // 添加最大值常量
    protected int panelLeft, panelTop;              // 面板的左上角坐标

    private boolean visible = false;                // 面板是否可见

    /**
     * 构造函数，初始化父屏幕和配置对象，并计算面板的位置。
     *
     * @param parent  父屏幕对象
     * @param config  配置对象
     */
    public GeometrySubPanel(PushdozerConfigScreen parent, PushdozerConfig config) {
        this.parent = parent;
        this.config = config;
        this.panelLeft = (parent.getScreenWidth() - PANEL_WIDTH) / 2;
        this.panelTop = (parent.getScreenHeight() - PANEL_HEIGHT) / 2;
        this.textRenderer = MinecraftClient.getInstance().textRenderer;  // 初始化文本渲染器
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
    protected abstract void saveConfig();


    /**
     * CustomSliderWidget 是自定义的滑动条组件，继承自 SliderWidget。
     * 它用于在界面上显示和调整特定的数值参数（如长度、宽度、高度、半径等）。
     */
    protected static class CustomSliderWidget extends SliderWidget {
        private final String label; // 滑动条的标签，例如 "长度"
        private final int min;      // 滑动条的最小值
        private final int max;      // 滑动条的最大值
        private boolean dragging = false; // 标记滑动条是否正在被拖动
        private final GeometrySubPanel parent; // 添加对父面板的引用

        /**
         * 构造函数，初始化滑动条的属性。
         *
         * @param x            滑动条的X坐标
         * @param y            滑动条的Y坐标
         * @param width        滑动条的宽度
         * @param height       滑动条的高度
         * @param label        滑动条的标签
         * @param min          滑动条的最小值
         * @param max          滑动条的最大值
         * @param initialValue 滑动条的初始值
         */
        public CustomSliderWidget(int x, int y, int width, int height, String label, int min, int max, int initialValue, GeometrySubPanel parent) {
            super(x, y, width, height, Text.of(label + ": " + initialValue), (double) (initialValue - min) / (max - min));
            this.label = label;
            this.min = min;
            this.max = max;
            this.parent = parent;
            this.updateMessage();
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
            updateMessage();
            System.out.println("Slider [" + label + "] value changed to: " + getIntValue());
            
            // 实时更新预览
            parent.updatePreview();
        }


        /**
         * 获取滑动条当前的整数值，根据滑动条的进度计算。
         *
         * @return 当前的整数值
         */
        public int getIntValue() {
            return (int) Math.round(MathHelper.lerp(this.value, this.min, this.max));
        }

        /**
         * 处理鼠标点击事件。
         *
         * @param mouseX 鼠标X坐标
         * @param mouseY 鼠标Y坐标
         * @param button 鼠标按钮
         * @return 是否处理了事件
         */
        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            if (this.active && this.visible) {
                if (this.isValidClickButton(button)) {
                    if (mouseX >= this.getX() && mouseX < this.getX() + this.width &&
                            mouseY >= this.getY() && mouseY < this.getY() + this.height) {
                        this.setValueFromMouse(mouseX);
                        this.dragging = true; // 开始拖动
                        return true;
                    }
                }
            }
            return false;
        }

        /**
         * 处理鼠标拖动事件。
         *
         * @param mouseX 鼠标X坐标
         * @param mouseY 鼠标Y坐标
         * @param button 鼠标按钮
         * @param deltaX X轴移动距离
         * @param deltaY Y轴移动距离
         * @return 是否处理了事件
         */
        @Override
        public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
            if (this.dragging) {
                this.setValueFromMouse(mouseX);
                return true;
            }
            return false;
        }

        /**
         * 处理鼠标释放事件。
         *
         * @param mouseX 鼠标X坐标
         * @param mouseY 鼠标Y坐标
         * @param button 鼠标按钮
         * @return 是否处理了事件
         */
        @Override
        public boolean mouseReleased(double mouseX, double mouseY, int button) {
            if (this.dragging) {
                this.dragging = false;
                return true;
            }
            return super.mouseReleased(mouseX, mouseY, button);
        }

        /**
         * 根据鼠标位置设置滑动条的值。
         *
         * @param mouseX 鼠标X坐标
         */
        private void setValueFromMouse(double mouseX) {
            double newValue = (mouseX - (this.getX() + 4)) / (double) (this.width - 8);
            this.setCustomValue(MathHelper.clamp(newValue, 0.0, 1.0));
        }


        /**
         * 设置滑动条的自定义值。
         *
         * @param value 新的值
         */
        private void setCustomValue(double value) {
            if (this.value != value) {
                this.value = value;
                this.applyValue();
            }
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
        int y = panelTop + TITLE_HEIGHT + 10 + (index * (SLIDER_HEIGHT + 5));
        CustomSliderWidget slider = new CustomSliderWidget(
            panelLeft + 10,
            y,
            PANEL_WIDTH - 20,
            SLIDER_HEIGHT,
            label.getString(),  // 转换为字符串
            1,                  // 最小值
            MAX_VALUE,         // 最大值
            value,             // 当前值
            this              // 父面板引用
        );
        widgets.add(slider);
        return slider;
    }

    /**
     * 获取总的滑动条数量，由子类实现
     */
    protected abstract int getTotalSliders();

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
        confirmButton = ButtonWidget.builder(
            Text.translatable("pushdozer.button.done"), 
            button -> {
                saveConfig();
                closeSubPanel();
            })
            .dimensions(panelLeft + (PANEL_WIDTH - SLIDER_WIDTH) / 2, 
                       panelTop + PANEL_HEIGHT - 25, 
                       SLIDER_WIDTH, 
                       20)
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
     *
     * @param mouseX 鼠标X坐标
     * @param mouseY 鼠标Y坐标
     * @param button 鼠标按钮
     * @return 是否处理了事件
     */
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        for (Element widget : widgets) {
            if (widget.mouseClicked(mouseX, mouseY, button)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 处理鼠标拖动事件。
     *
     * @param mouseX 鼠标X坐标
     * @param mouseY 鼠标Y坐标
     * @param button 鼠标按钮
     * @param deltaX X轴移动距离
     * @param deltaY Y轴移动距离
     * @return 是否处理了事件
     */
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        for (Element widget : widgets) {
            if (widget instanceof CustomSliderWidget && widget.mouseDragged(mouseX, mouseY, button, deltaX, deltaY)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 处理鼠标释放事件。
     *
     * @param mouseX 鼠标X坐标
     * @param mouseY 鼠标Y坐标
     * @param button 鼠标按钮
     * @return 是否处理了事件
     */
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        for (Element widget : widgets) {
            if (widget.mouseReleased(mouseX, mouseY, button)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 渲染标题
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
                panelTop + 5,
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