package com.pushdozer.ui.panels.brushgeometry;

import com.pushdozer.config.PushdozerConfig;
import com.pushdozer.ui.screens.PushdozerConfigScreen;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.input.MouseInput;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * 几何体选择面板，用于显示和选择不同的几何类型。
 * <p>
 * 该面板提供了一个可视化的界面，允许用户从可用的几何类型中进行选择。
 * 面板会在屏幕中央显示，并支持鼠标交互。
 */
public class GeometrySelectionPanel {
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
    private final Consumer<PushdozerConfig.GeometryType> onSelectionChanged;
    private final List<Element> widgets = new ArrayList<>();

    // 状态字段
    private boolean visible = false;
    private int panelLeft, panelTop;

    // 预计算的标题位置（性能优化）
    private int titleX, titleY;
    private Text titleText;

    /**
     * 构造函数，初始化几何体选择面板。
     * * @param parent 父级配置屏幕
     * @param config 配置对象
     * @param onSelectionChanged 选择变化时的回调函数
     */
    public GeometrySelectionPanel(PushdozerConfigScreen parent, PushdozerConfig config,
                                  Consumer<PushdozerConfig.GeometryType> onSelectionChanged) {
        this.parent = parent;
        this.config = config;
        this.onSelectionChanged = onSelectionChanged;

        // 计算面板位置（屏幕中央）
        this.panelLeft = (parent.getScreenWidth() - PANEL_WIDTH) / 2;
        this.panelTop = (parent.getScreenHeight() - PANEL_HEIGHT) / 2;

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
        PushdozerConfig.GeometryType[] types = PushdozerConfig.GeometryType.values();

        // 计算每个按钮的宽度，考虑左右边距和按钮之间的间距
        // (PANEL_WIDTH - 2 * BUTTON_MARGIN - BUTTON_MARGIN) / 2
        // = (面板总宽度 - 左边距 - 右边距 - 中间按钮间距) / 2
        int buttonWidth = (PANEL_WIDTH - (BUTTON_MARGIN * 3)) / 2;

        for (int i = 0; i < types.length; i++) {
            PushdozerConfig.GeometryType type = types[i];

            // 计算当前按钮的行和列
            int row = i / 2; // 整除得到行号
            int col = i % 2; // 取模得到列号 (0 或 1)

            // 计算按钮的 X 坐标
            int buttonX = panelLeft + BUTTON_MARGIN + (col * (buttonWidth + BUTTON_MARGIN));

            // 计算按钮的 Y 坐标
            int buttonY = startY + (row * (BUTTON_HEIGHT + BUTTON_MARGIN));

            ButtonWidget button = ButtonWidget.builder(
                    getButtonText(type),
                    btn -> selectGeometry(type)
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
    private Text getButtonText(PushdozerConfig.GeometryType type) {
        String prefix = (config.getGeometryType() == type) ? "☑ " : "";
        return Text.literal(prefix).append(type.getDisplayText());
    }

    /**
     * 更新按钮状态
     */
    private void updateButtonStates() {
        for (int i = 0; i < widgets.size(); i++) {
            Element widget = widgets.get(i);
            if (widget instanceof ButtonWidget button) {
                PushdozerConfig.GeometryType type = PushdozerConfig.GeometryType.values()[i];
                button.setMessage(getButtonText(type));
            }
        }
    }

    /**
     * 预计算标题位置以优化渲染性能。
     */
    private void initializeTitlePosition() {
        titleText = Text.translatable("pushdozer.panel.brush_shape_selection.title");

        if (parent.getClient() != null) {
            int titleWidth = parent.getClient().textRenderer.getWidth(titleText);
            titleX = panelLeft + (PANEL_WIDTH - titleWidth) / 2;
            titleY = panelTop + (TITLE_HEIGHT - parent.getClient().textRenderer.fontHeight) / 2;
        }
    }

    /**
     * 选择指定的几何类型。
     * * @param type 要选择的几何类型
     */
    private void selectGeometry(PushdozerConfig.GeometryType type) {
        config.setGeometryType(type);
        updateButtonStates();
        if (onSelectionChanged != null) {
            onSelectionChanged.accept(type);
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
     * * @return 如果面板可见则返回 true
     */
    public boolean isVisible() {
        return visible;
    }

    /**
     * 渲染面板的所有组件。
     * * @param context 绘制上下文
     * @param mouseX 鼠标 X 坐标
     * @param mouseY 鼠标 Y 坐标
     * @param delta 帧间隔时间
     */
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        if (!visible) return;

        renderBackground(context);
        renderTitle(context);
        renderButtons(context, mouseX, mouseY, delta);
    }

    /**
     * 渲染背景遮罩和面板背景。
     * * @param context 绘制上下文
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
        context.fill(x, y, x + GeometrySelectionPanel.PANEL_WIDTH, y + 1, GeometrySelectionPanel.COLOR_PANEL_BORDER);
        // bottom
        context.fill(x, y + GeometrySelectionPanel.PANEL_HEIGHT - 1, x + GeometrySelectionPanel.PANEL_WIDTH, y + GeometrySelectionPanel.PANEL_HEIGHT, GeometrySelectionPanel.COLOR_PANEL_BORDER);
        // left
        context.fill(x, y, x + 1, y + GeometrySelectionPanel.PANEL_HEIGHT, GeometrySelectionPanel.COLOR_PANEL_BORDER);
        // right
        context.fill(x + GeometrySelectionPanel.PANEL_WIDTH - 1, y, x + GeometrySelectionPanel.PANEL_WIDTH, y + GeometrySelectionPanel.PANEL_HEIGHT, GeometrySelectionPanel.COLOR_PANEL_BORDER);
    }

    /**
     * 渲染标题文本。
     * * @param context 绘制上下文
     */
    private void renderTitle(DrawContext context) {
        // 5. 绘制标题文本（使用预计算的位置）
        if (parent.getClient() != null) {
            context.drawText(
                    parent.getClient().textRenderer,
                    titleText,
                    titleX,
                    titleY,
                    COLOR_WHITE,
                    false
            );
        }
    }

    /**
     * 渲染所有按钮。
     * * @param context 绘制上下文
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
        PushdozerConfig.GeometryType currentType = config.getGeometryType();
        
        PushdozerConfig.GeometryType[] types = PushdozerConfig.GeometryType.values();
        for (int i = 0; i < widgets.size() && i < types.length; i++) {
            Element widget = widgets.get(i);
            if (widget instanceof ButtonWidget button) {
                button.setFocused(types[i] == currentType);
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
     * 处理鼠标点击事件。
     * * @param mouseX 鼠标 X 坐标
     * @param mouseY 鼠标 Y 坐标
     * @param button 鼠标按钮
     * @return 如果事件被处理则返回 true
     */
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!visible) return false;

        // 检查是否点击了面板外部
        if (mouseX < panelLeft || mouseX > panelLeft + PANEL_WIDTH ||
                mouseY < panelTop || mouseY > panelTop + PANEL_HEIGHT) {
            hide();
            return true;
        }

        // 使用事件委托处理按钮点击
        Click click = new Click(mouseX, mouseY, new MouseInput(button, 0));
        handleMouseEvent(clickable -> clickable.mouseClicked(click, false));
        return true;
    }

    /**
     * 处理鼠标拖拽事件。
     * * @param mouseX 鼠标 X 坐标
     * @param mouseY 鼠标 Y 坐标
     * @param button 鼠标按钮
     * @param deltaX X 轴偏移量
     * @param deltaY Y 轴偏移量
     * @return 如果事件被处理则返回 true
     */
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (!visible) return false;

        Click click = new Click(mouseX, mouseY, new MouseInput(button, 0));
        return handleMouseEvent(clickable -> clickable.mouseDragged(click, deltaX, deltaY));
    }

    /**
     * 处理鼠标释放事件。
     * * @param mouseX 鼠标 X 坐标
     * @param mouseY 鼠标 Y 坐标
     * @param button 鼠标按钮
     * @return 如果事件被处理则返回 true
     */
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (!visible) return false;

        Click click = new Click(mouseX, mouseY, new MouseInput(button, 0));
        return handleMouseEvent(clickable -> clickable.mouseReleased(click));
    }

    /**
     * 事件委托机制，统一处理鼠标事件。
     * * @param eventHandler 事件处理函数
     * @return 如果事件被处理则返回 true
     */
    private boolean handleMouseEvent(Function<ClickableWidget, Boolean> eventHandler) {
        for (Element widget : widgets) {
            if (widget instanceof ClickableWidget clickable) {
                if (eventHandler.apply(clickable)) {
                    return true;
                }
            }
        }
        return false;
    }
}
