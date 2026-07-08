package com.pushdozer.ui.panels.workmode;

import com.pushdozer.config.PushdozerConfig;
import com.pushdozer.ui.screens.PushdozerConfigScreen;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.ArrayList;
import java.util.List;

/**
 * 工作模式配置面板基类
 * 为不同的工作模式提供配置界面
 */
public abstract class WorkModeConfigPanel {
    // 颜色常量
    protected static final int COLOR_PANEL_BG = 0xC0101010;     // 面板背景
    protected static final int COLOR_PANEL_BORDER = 0xFFFFFFFF; // 面板边框
    protected static final int COLOR_TITLE_BG = 0xE0303030;     // 标题栏背景
    protected static final int COLOR_WHITE = 0xFFFFFF;          // 白色文本

    // 尺寸常量
    protected static final int PANEL_WIDTH = 210; // 统一加宽40
    protected static final int PANEL_HEIGHT = 140;
    protected static final int TITLE_HEIGHT = 20;
    protected static final int WIDGET_HEIGHT = 20;
    protected static final int WIDGET_MARGIN = 5;
    protected static final int CONFIRM_BUTTON_HEIGHT = 20;
    protected static final int CONFIRM_BUTTON_MARGIN = 5;

    // 核心字段
    protected final PushdozerConfigScreen parent;
    protected final PushdozerConfig config;
    protected final List<Element> widgets = new ArrayList<>();
    protected ButtonWidget confirmButton;          // 确认按钮

    // 状态字段
    protected boolean visible = false;
    protected int panelLeft, panelTop;

    // 预计算的标题位置（性能优化）
    protected int titleX, titleY;
    protected Text titleText;

    /**
     * 构造函数
     */
    public WorkModeConfigPanel(PushdozerConfigScreen parent, PushdozerConfig config) {
        this.parent = parent;
        this.config = config;

        // 先计算面板位置（屏幕中央）
        this.panelLeft = (parent.getScreenWidth() - PANEL_WIDTH) / 2;
        // 临时计算面板顶部位置，稍后会重新计算
        this.panelTop = (parent.getScreenHeight() - PANEL_HEIGHT) / 2;

        initializeTitlePosition();
        initializeWidgets();
        initializeConfirmButton();
        
        // 重新计算面板位置，使用实际的高度
        this.panelTop = (parent.getScreenHeight() - getPanelHeight()) / 2;
        
        // 重新计算所有控件的位置，确保位置正确
        recalculateAllWidgetPositions();
    }

    /**
     * 初始化控件，由子类实现具体逻辑
     */
    protected abstract void initializeWidgets();

    /**
     * 获取面板标题，由子类实现
     */
    protected abstract Text getTitleText();

    /**
     * 获取面板高度，子类可以重写以提供动态高度
     */
    protected int getPanelHeight() {
        // 计算动态高度：确认按钮位置 + 确认按钮高度 + 确认按钮下边距
        int confirmButtonY = calculateConfirmButtonY();
        return confirmButtonY + CONFIRM_BUTTON_HEIGHT + CONFIRM_BUTTON_MARGIN - panelTop;
    }

    /**
     * 计算确认按钮的Y位置
     */
    protected int calculateConfirmButtonY() {
        // 计算内容控件数量（不包括确认按钮）
        int contentWidgetCount = widgets.size();
        if (confirmButton != null && widgets.contains(confirmButton)) {
            contentWidgetCount = widgets.size() - 1; // 减去确认按钮
        }
        
        if (contentWidgetCount > 0) {
            // 计算最后一个内容控件的位置
            int lastWidgetY = panelTop + TITLE_HEIGHT + WIDGET_MARGIN + (contentWidgetCount - 1) * (WIDGET_HEIGHT + WIDGET_MARGIN) + WIDGET_HEIGHT;
            // 确认按钮位置：最后一个控件底部下方5像素
            return lastWidgetY + CONFIRM_BUTTON_MARGIN;
        } else {
            // 没有内容控件时，确认按钮紧贴标题栏
            return panelTop + TITLE_HEIGHT + WIDGET_MARGIN + CONFIRM_BUTTON_MARGIN;
        }
    }

    /**
     * 初始化确认按钮
     */
    protected void initializeConfirmButton() {
        // 计算确认按钮位置
        int confirmButtonY = calculateConfirmButtonY();
        
        confirmButton = ButtonWidget.builder(
            Text.translatable("pushdozer.button.done"), 
            button -> {
                saveConfig();
                closeSubPanel();
            })
            .dimensions(
                panelLeft + WIDGET_MARGIN,
                confirmButtonY,
                PANEL_WIDTH - (WIDGET_MARGIN * 2),
                CONFIRM_BUTTON_HEIGHT)
            .build();
        widgets.add(confirmButton);
    }

    /**
     * 保存配置，由子类实现
     */
    public abstract void saveConfig();

    /**
     * 将当前配置写入磁盘并提示保存成功（IO 失败已在 {@link PushdozerConfig#save()} 内处理）。
     */
    protected void persistPanelConfig() {
        config.save();
        parent.showErrorMessage(Text.translatable("pushdozer.config.saved").getString());
    }

    /**
     * 关闭子面板
     */
    protected void closeSubPanel() {
        hide();                   // 隐藏当前面板
        parent.showMainPanel();   // 显示主界面
    }

    /**
     * 预计算标题位置以优化渲染性能
     */
    protected void initializeTitlePosition() {
        titleText = getTitleText();
        TextRenderer textRenderer = parent.resolveTextRenderer();
        if (textRenderer != null) {
            titleX = panelLeft + (PANEL_WIDTH - textRenderer.getWidth(titleText)) / 2;
            titleY = panelTop + (TITLE_HEIGHT - textRenderer.fontHeight) / 2;
        }
    }

    /**
     * 显示面板
     */
    public void show() {
        visible = true;
        
        // 先计算面板位置
        this.panelLeft = (parent.getScreenWidth() - PANEL_WIDTH) / 2;
        
        // 清空控件列表，确保高度计算准确
        widgets.clear();
        confirmButton = null;
        
        // 先初始化控件，但不包括确认按钮
        initializeWidgets();
        
        // 然后初始化确认按钮
        initializeConfirmButton();
        
        // 现在可以正确计算面板高度和位置
        this.panelTop = (parent.getScreenHeight() - getPanelHeight()) / 2;
        
        // 重新计算所有控件的位置，因为panelTop已经确定
        recalculateAllWidgetPositions();
    }
    
    /**
     * 重新计算所有控件的位置
     */
    protected void recalculateAllWidgetPositions() {
        // 重新计算标题位置
        initializeTitlePosition();
        
        // 重新计算确认按钮位置
        if (confirmButton != null) {
            int confirmButtonY = calculateConfirmButtonY();
            int confirmButtonX = panelLeft + WIDGET_MARGIN;
            int confirmButtonWidth = PANEL_WIDTH - (WIDGET_MARGIN * 2);
            confirmButton.setPosition(confirmButtonX, confirmButtonY);
            confirmButton.setWidth(confirmButtonWidth);
        }
        
        // 重新计算所有内容控件的位置
        recalculateContentWidgetPositions();
    }
    
    /**
     * 重新计算内容控件的位置
     */
    protected void recalculateContentWidgetPositions() {
        int currentY = panelTop + TITLE_HEIGHT + WIDGET_MARGIN;
        
        for (Element widget : widgets) {
            if (widget != confirmButton) {
                if (widget instanceof net.minecraft.client.gui.widget.ClickableWidget clickableWidget) {
                    clickableWidget.setPosition(clickableWidget.getX(), currentY);
                    currentY += WIDGET_HEIGHT + WIDGET_MARGIN;
                }
            }
        }
    }

    /**
     * 隐藏面板
     */
    public void hide() {
        visible = false;
    }

    /**
     * 检查面板是否可见
     */
    public boolean isVisible() {
        return visible;
    }

    /**
     * 渲染面板的所有组件
     */
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        if (!visible) return;

        renderBackground(context);
        renderWidgets(context, mouseX, mouseY, delta);
        renderTitle(context);
    }

    /**
     * 渲染背景
     */
    protected void renderBackground(DrawContext context) {
        // 绘制面板背景（半透明）
        context.fill(panelLeft, panelTop, panelLeft + PANEL_WIDTH, panelTop + getPanelHeight(), COLOR_PANEL_BG);

        // 绘制标题栏背景
        context.fill(panelLeft, panelTop, panelLeft + PANEL_WIDTH, panelTop + TITLE_HEIGHT, COLOR_TITLE_BG);

        // 绘制面板边框
        drawBorder(context, panelLeft, panelTop, getPanelHeight());
    }

    protected static void drawBorder(DrawContext context, int x, int y, int height) {
        // top
        context.fill(x, y, x + WorkModeConfigPanel.PANEL_WIDTH, y + 1, WorkModeConfigPanel.COLOR_PANEL_BORDER);
        // bottom
        context.fill(x, y + height - 1, x + WorkModeConfigPanel.PANEL_WIDTH, y + height, WorkModeConfigPanel.COLOR_PANEL_BORDER);
        // left
        context.fill(x, y, x + 1, y + height, WorkModeConfigPanel.COLOR_PANEL_BORDER);
        // right
        context.fill(x + WorkModeConfigPanel.PANEL_WIDTH - 1, y, x + WorkModeConfigPanel.PANEL_WIDTH, y + height, WorkModeConfigPanel.COLOR_PANEL_BORDER);
    }

    /**
     * 渲染标题文本
     */
    protected void renderTitle(DrawContext context) {
        if (titleText == null) {
            titleText = getTitleText();
        }
        TextRenderer textRenderer = parent.resolveTextRenderer();
        if (textRenderer == null) {
            return;
        }

        Text displayTitle = titleText.copy().formatted(Formatting.BOLD, Formatting.YELLOW);
        int titleWidth = textRenderer.getWidth(displayTitle);
        int x = panelLeft + (PANEL_WIDTH - titleWidth) / 2;
        int y = panelTop + (TITLE_HEIGHT - textRenderer.fontHeight) / 2;
        context.drawText(textRenderer, displayTitle, x, y, 0xFFFFFFFF, true);
    }

    /**
     * 渲染所有控件
     */
    protected void renderWidgets(DrawContext context, int mouseX, int mouseY, float delta) {
        for (Element widget : widgets) {
            if (widget instanceof net.minecraft.client.gui.Drawable) {
                ((net.minecraft.client.gui.Drawable) widget).render(context, mouseX, mouseY, delta);
            }
        }
    }

    /**
     * 处理鼠标点击事件
     */
    public boolean mouseClicked(Click click) {
        if (!visible) return false;

        double mouseX = click.x();
        double mouseY = click.y();

        // 检查点击是否在面板内
        if (mouseX >= panelLeft && mouseX <= panelLeft + PANEL_WIDTH &&
            mouseY >= panelTop && mouseY <= panelTop + getPanelHeight()) {

            // 将事件传递给控件
            for (Element widget : widgets) {
                if (widget instanceof net.minecraft.client.gui.widget.ClickableWidget clickableWidget) {
                    if (clickableWidget.mouseClicked(click, false)) {
                        return true;
                    }
                }
            }
            return true;
        }

        // 点击在面板外部，关闭面板
        hide();
        parent.showMainPanel();
        return true;
    }

    /**
     * 处理鼠标拖拽事件
     */
    public boolean mouseDragged(Click click, double deltaX, double deltaY) {
        if (!visible) return false;

        double mouseX = click.x();
        double mouseY = click.y();
        for (Element widget : widgets) {
            if (widget instanceof net.minecraft.client.gui.widget.ClickableWidget clickableWidget) {
                if (clickableWidget.isMouseOver(mouseX, mouseY)) {
                    if (clickableWidget.mouseDragged(click, deltaX, deltaY)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /**
     * 处理鼠标释放事件
     */
    public boolean mouseReleased(Click click) {
        if (!visible) return false;

        double mouseX = click.x();
        double mouseY = click.y();
        for (Element widget : widgets) {
            if (widget instanceof net.minecraft.client.gui.widget.ClickableWidget clickableWidget) {
                if (clickableWidget.isMouseOver(mouseX, mouseY)) {
                    if (clickableWidget.mouseReleased(click)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /**
     * 获取控件列表，供外部访问
     */
    public List<Element> getWidgets() {
        return widgets;
    }

    @FunctionalInterface
    protected interface SliderApplyValue {
        void apply(SliderWidget slider);
    }
} 