package com.pushdozer.ui.panels.workmode;

import com.pushdozer.config.PushdozerConfig;
import com.pushdozer.ui.screens.PushdozerConfigScreen;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * 工作模式选择面板，用于显示和选择不同的工作模式。
 * <p>
 * 该面板提供了一个可视化的界面，允许用户从可用的工作模式中进行选择。
 * 面板会在屏幕中央显示，并支持鼠标交互。
 */
public class WorkModeSelectionPanel {
    // 颜色常量
    private static final int COLOR_PANEL_BG = 0xC0101010;     // 面板背景
    private static final int COLOR_PANEL_BORDER = 0xFFFFFFFF; // 面板边框
    private static final int COLOR_TITLE_BG = 0xE0303030;     // 标题栏背景
    private static final int COLOR_WHITE = 0xFFFFFF;          // 白色文本
    // 移除未使用的选中颜色常量，实际高亮由渲染逻辑内联颜色实现

    // 尺寸常量
    private static final int PANEL_WIDTH = 210; // 统一加宽40
    private static final int PANEL_HEIGHT = 125;
    private static final int BUTTON_HEIGHT = 20;
    private static final int BUTTON_MARGIN = 5;
    private static final int TITLE_HEIGHT = 20;

    // 核心字段
    private final PushdozerConfigScreen parent;
    private final PushdozerConfig config;
    private final Consumer<PushdozerConfig.WorkMode> onSelectionChanged;
    private final List<Element> widgets = new ArrayList<>();
    // 仅展示的工作模式列表（隐藏旧的三个平滑模式项）
    private List<PushdozerConfig.WorkMode> displayModes;

    // 状态字段
    private boolean visible = false;
    private int panelLeft, panelTop;

    // 预计算的标题位置（性能优化）
    private int titleX, titleY;
    private Text titleText;

    /**
     * 构造函数，初始化工作模式选择面板。
     * 
     * @param parent 父级配置屏幕
     * @param config 配置对象
     * @param onSelectionChanged 选择变化时的回调函数
     */
    public WorkModeSelectionPanel(PushdozerConfigScreen parent, PushdozerConfig config,
                                  Consumer<PushdozerConfig.WorkMode> onSelectionChanged) {
        this.parent = parent;
        this.config = config;
        this.onSelectionChanged = onSelectionChanged;

        // 计算面板位置（屏幕中央）
        this.panelLeft = (parent.getScreenWidth() - PANEL_WIDTH) / 2;
        this.panelTop = (parent.getScreenHeight() - PANEL_HEIGHT) / 2;

        // 构建展示列表
        this.displayModes = getDisplayWorkModes();

        initializeWidgets();
        initializeTitlePosition();
    }

    /**
     * 初始化所有按钮控件。
     * 调整为每行放置两个按钮。
     */
    private void initializeWidgets() {
        widgets.clear();

        int startY = panelTop + TITLE_HEIGHT + BUTTON_MARGIN; // 留出标题空间和间距
        List<PushdozerConfig.WorkMode> modes = displayModes;

        // 计算每个按钮的宽度，考虑左右边距和按钮之间的间距
        // (PANEL_WIDTH - 2 * BUTTON_MARGIN - BUTTON_MARGIN) / 2
        // = (面板总宽度 - 左边距 - 右边距 - 中间按钮间距) / 2
        int buttonWidth = (PANEL_WIDTH - (BUTTON_MARGIN * 3)) / 2;

        for (int i = 0; i < modes.size(); i++) {
            PushdozerConfig.WorkMode mode = modes.get(i);

            // 计算当前按钮的行和列
            int row = i / 2; // 整除得到行号
            int col = i % 2; // 取模得到列号 (0 或 1)

            // 计算按钮的 X 坐标
            int buttonX = panelLeft + BUTTON_MARGIN + (col * (buttonWidth + BUTTON_MARGIN));

            // 计算按钮的 Y 坐标
            int buttonY = startY + (row * (BUTTON_HEIGHT + BUTTON_MARGIN));

            ButtonWidget button = ButtonWidget.builder(
                    getButtonText(mode),
                    btn -> selectWorkMode(mode)
            ).dimensions(
                    buttonX,
                    buttonY,
                    buttonWidth,
                    BUTTON_HEIGHT
            ).build();

            widgets.add(button);
        }
    }

    /**
     * 获取按钮文本（带选中标记）
     */
    private Text getButtonText(PushdozerConfig.WorkMode mode) {
        String prefix = (config.getWorkMode() == mode) ? "☑ " : "";
        return Text.literal(prefix).append(mode.getDisplayText());
    }

    /**
     * 更新按钮状态
     */
    private void updateButtonStates() {
        for (int i = 0; i < widgets.size(); i++) {
            Element widget = widgets.get(i);
            if (widget instanceof ButtonWidget button) {
                PushdozerConfig.WorkMode mode = displayModes.get(i);
                button.setMessage(getButtonText(mode));
            }
        }
    }

    /**
     * 预计算标题位置以优化渲染性能。
     */
    private void initializeTitlePosition() {
        titleText = Text.translatable("pushdozer.panel.work_mode_selection.title");

        if (parent.getClient() != null) {
            int titleWidth = parent.getClient().textRenderer.getWidth(titleText);
            titleX = panelLeft + (PANEL_WIDTH - titleWidth) / 2;
            titleY = panelTop + (TITLE_HEIGHT - parent.getClient().textRenderer.fontHeight) / 2;
        }
    }

    /**
     * 选择指定的工作模式。
     * 
     * @param mode 要选择的工作模式
     */
    private void selectWorkMode(PushdozerConfig.WorkMode mode) {
        config.setWorkMode(mode);
        updateButtonStates();
        if (onSelectionChanged != null) {
            onSelectionChanged.accept(mode);
        }
        hide();
    }

    /**
     * 显示面板。
     */
    public void show() {
        visible = true;
        // 重新计算位置以防窗口大小改变
        this.panelLeft = (parent.getScreenWidth() - PANEL_WIDTH) / 2;
        this.panelTop = (parent.getScreenHeight() - PANEL_HEIGHT) / 2;
        initializeWidgets(); // 重新初始化按钮，确保位置正确
        initializeTitlePosition();
        updateButtonStates(); // 更新按钮状态
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
     * @return 如果面板可见则返回 true
     */
    public boolean isVisible() {
        return visible;
    }

    /**
     * 渲染面板的所有组件。
     * 
     * @param context 绘制上下文
     * @param mouseX 鼠标 X 坐标
     * @param mouseY 鼠标 Y 坐标
     * @param delta 帧间隔时间
     */
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        if (!visible) return;

        renderBackground(context);
        renderButtons(context, mouseX, mouseY, delta);
        renderTitle(context);
    }

    /**
     * 渲染背景遮罩和面板背景。
     * 
     * @param context 绘制上下文
     */
    private void renderBackground(DrawContext context) {
        // 1. 绘制半透明背景遮罩 (原代码中被注释掉了，这里保留原样)
        // context.fill(0, 0, parent.getScreenWidth(), parent.getScreenHeight(), COLOR_OVERLAY);

        // 2. 绘制面板背景（半透明）
        context.fill(panelLeft, panelTop, panelLeft + PANEL_WIDTH, panelTop + PANEL_HEIGHT, COLOR_PANEL_BG);

        // 3. 绘制标题栏背景
        context.fill(panelLeft, panelTop, panelLeft + PANEL_WIDTH, panelTop + TITLE_HEIGHT, COLOR_TITLE_BG);

        // 4. 绘制面板边框（在标题背景之后，确保边框不被遮挡）
        drawBorder(context, panelLeft, panelTop);
    }

    private static void drawBorder(DrawContext context, int x, int y) {
        // top
        context.fill(x, y, x + WorkModeSelectionPanel.PANEL_WIDTH, y + 1, WorkModeSelectionPanel.COLOR_PANEL_BORDER);
        // bottom
        context.fill(x, y + WorkModeSelectionPanel.PANEL_HEIGHT - 1, x + WorkModeSelectionPanel.PANEL_WIDTH, y + WorkModeSelectionPanel.PANEL_HEIGHT, WorkModeSelectionPanel.COLOR_PANEL_BORDER);
        // left
        context.fill(x, y, x + 1, y + WorkModeSelectionPanel.PANEL_HEIGHT, WorkModeSelectionPanel.COLOR_PANEL_BORDER);
        // right
        context.fill(x + WorkModeSelectionPanel.PANEL_WIDTH - 1, y, x + WorkModeSelectionPanel.PANEL_WIDTH, y + WorkModeSelectionPanel.PANEL_HEIGHT, WorkModeSelectionPanel.COLOR_PANEL_BORDER);
    }

    /**
     * 渲染标题文本。
     * 
     * @param context 绘制上下文
     */
    private void renderTitle(DrawContext context) {
        if (titleText == null) {
            titleText = Text.translatable("pushdozer.panel.work_mode_selection.title");
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
     * 渲染所有按钮。
     * 
     * @param context 绘制上下文
     * @param mouseX 鼠标 X 坐标
     * @param mouseY 鼠标 Y 坐标
     * @param delta 帧间隔时间
     */
    private void renderButtons(DrawContext context, int mouseX, int mouseY, float delta) {
        // 先重置所有按钮的焦点状态
        for (Element widget : widgets) {
            if (widget instanceof ButtonWidget button) {
                button.setFocused(false);
            }
        }
        
        // 然后设置当前选中按钮的焦点状态
        PushdozerConfig.WorkMode currentMode = config.getWorkMode();
        
        for (int i = 0; i < widgets.size(); i++) {
            Element widget = widgets.get(i);
            if (widget instanceof ButtonWidget button) {
                boolean isSelected = displayModes.get(i) == currentMode;
                if (isSelected) {
                    // 使用ButtonWidget的内置按下状态
                    button.setFocused(true);
                }
            }
        }
        
        // 最后渲染所有按钮（让按钮自己处理文字渲染）
        for (Element widget : widgets) {
            if (widget instanceof net.minecraft.client.gui.Drawable) {
                ((net.minecraft.client.gui.Drawable) widget).render(context, mouseX, mouseY, delta);
            }
        }
    }

    /**
     * 返回需要显示在工作模式选择面板中的模式列表
     * 隐藏旧的三个平滑模式（SMOOTH_RAISE、SMOOTH_LOWER、ADAPTIVE_SMOOTH），仅保留统一的SMOOTH
     */
    private List<PushdozerConfig.WorkMode> getDisplayWorkModes() {
        List<PushdozerConfig.WorkMode> list = new ArrayList<>();
        for (PushdozerConfig.WorkMode m : PushdozerConfig.WorkMode.values()) {
            switch (m) {
                case SMOOTH_RAISE, SMOOTH_LOWER, ADAPTIVE_SMOOTH -> {
                    // 跳过旧平滑
                }
                default -> list.add(m);
            }
        }
        // 确保统一平滑模式存在
        if (!list.contains(PushdozerConfig.WorkMode.SMOOTH)) {
            list.add(PushdozerConfig.WorkMode.SMOOTH);
        }
        return list;
    }

    /**
     * 处理鼠标点击事件。
     */
    public boolean mouseClicked(Click click) {
        if (!visible) return false;

        double mouseX = click.x();
        double mouseY = click.y();

        // 检查点击是否在面板内
        if (mouseX >= panelLeft && mouseX <= panelLeft + PANEL_WIDTH &&
            mouseY >= panelTop && mouseY <= panelTop + PANEL_HEIGHT) {

            return handleMouseEvent(widget -> widget.mouseClicked(click, false));
        }

        // 点击在面板外部，关闭面板
        hide();
        return true;
    }

    /**
     * 处理鼠标拖拽事件。
     */
    public boolean mouseDragged(Click click, double deltaX, double deltaY) {
        if (!visible) return false;
        return handleMouseEvent(widget -> widget.mouseDragged(click, deltaX, deltaY));
    }

    /**
     * 处理鼠标释放事件。
     */
    public boolean mouseReleased(Click click) {
        if (!visible) return false;
        return handleMouseEvent(widget -> widget.mouseReleased(click));
    }

    /**
     * 通用的鼠标事件处理方法。
     * 
     * @param eventHandler 事件处理函数
     * @return 如果事件被处理则返回 true
     */
    private boolean handleMouseEvent(Function<ClickableWidget, Boolean> eventHandler) {
        for (Element widget : widgets) {
            if (widget instanceof ClickableWidget clickableWidget) {
                if (eventHandler.apply(clickableWidget)) {
                    return true;
                }
            }
        }
        return false;
    }
} 