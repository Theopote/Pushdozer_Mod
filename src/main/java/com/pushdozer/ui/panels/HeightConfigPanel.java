package com.pushdozer.ui.panels;

import com.pushdozer.PushdozerMod;
import com.pushdozer.config.PushdozerConfig;
import com.pushdozer.ui.screens.PushdozerConfigScreen;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.Drawable;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;

/**
 * 标高配置面板，用于配置标高限制模式。
 * <p>
 * 该面板提供了一个可视化的界面，允许用户选择标高限制模式。
 * 面板会在屏幕中央显示，并支持鼠标交互。
 */
public class HeightConfigPanel {
    // 颜色常量
    private static final int COLOR_PANEL_BG = 0xC0101010;     // 面板背景
    private static final int COLOR_PANEL_BORDER = 0xFFFFFFFF; // 面板边框
    private static final int COLOR_TITLE_BG = 0xE0303030;     // 标题栏背景
    private static final int COLOR_WHITE = 0xFFFFFF;          // 白色文本
    // 移除未使用的选中颜色常量，实际高亮由渲染逻辑内联颜色实现

    // 尺寸常量
    private static final int PANEL_WIDTH = 250; // 统一加宽40
    private static final int PANEL_HEIGHT = 125;
    private static final int BUTTON_HEIGHT = 20;
    private static final int BUTTON_MARGIN = 5;
    private static final int TITLE_HEIGHT = 20;

    // 核心字段
    private final PushdozerConfigScreen parent;
    private final PushdozerConfig config;
    private final List<Element> widgets = new ArrayList<>();

    // 状态字段
    private boolean visible = false;
    private int panelLeft, panelTop;

    // 预计算的标题位置（性能优化）
    private int titleX, titleY;
    private Text titleText;

    // 控件
    private ButtonWidget followPlayerButton;
    private ButtonWidget lockOnceButton;
    private ButtonWidget noLimitButton;
    private ButtonWidget customHeightButton;
    private SliderWidget heightSlider;

    /**
     * 构造函数，初始化标高配置面板。
     * 
     * @param parent 父级配置屏幕
     * @param config 配置对象
     */
    public HeightConfigPanel(PushdozerConfigScreen parent, PushdozerConfig config) {
        this.parent = parent;
        this.config = config;

        // 计算面板位置（屏幕中央）
        this.panelLeft = (parent.getScreenWidth() - PANEL_WIDTH) / 2;
        this.panelTop = (parent.getScreenHeight() - PANEL_HEIGHT) / 2;

        initializeWidgets();
        initializeTitlePosition();
    }

    /**
     * 初始化所有按钮控件。
     */
    private void initializeWidgets() {
        widgets.clear();

        int startY = panelTop + TITLE_HEIGHT + BUTTON_MARGIN; // 留出标题空间和间距
        int buttonWidth = (PANEL_WIDTH - (BUTTON_MARGIN * 3)) / 2; // 两列
        int buttonFullWidth = PANEL_WIDTH - (BUTTON_MARGIN * 2);

        // 第一行：跟随玩家标高、锁定到玩家标高
        followPlayerButton = ButtonWidget.builder(
                getButtonText(PushdozerConfig.HeightMode.FOLLOW_PLAYER),
                button -> selectHeightMode(PushdozerConfig.HeightMode.FOLLOW_PLAYER))
                .dimensions(panelLeft + BUTTON_MARGIN, startY, buttonWidth, BUTTON_HEIGHT)
                .tooltip(Tooltip.of(Text.translatable("pushdozer.tooltip.height_follow_player")))
                .build();
        lockOnceButton = ButtonWidget.builder(
                getButtonText(PushdozerConfig.HeightMode.LOCKED_ONCE),
                button -> selectHeightMode(PushdozerConfig.HeightMode.LOCKED_ONCE))
                .dimensions(panelLeft + BUTTON_MARGIN * 2 + buttonWidth, startY, buttonWidth, BUTTON_HEIGHT)
                .tooltip(Tooltip.of(Text.translatable("pushdozer.tooltip.height_locked_once")))
                .build();

        // 第二行：标高不限、自定义标高
        noLimitButton = ButtonWidget.builder(
                getButtonText(PushdozerConfig.HeightMode.NO_LIMIT),
                button -> selectHeightMode(PushdozerConfig.HeightMode.NO_LIMIT))
                .dimensions(panelLeft + BUTTON_MARGIN, startY + BUTTON_HEIGHT + BUTTON_MARGIN, buttonWidth, BUTTON_HEIGHT)
                .tooltip(Tooltip.of(Text.translatable("pushdozer.tooltip.height_no_limit")))
                .build();
        customHeightButton = ButtonWidget.builder(
                getButtonText(PushdozerConfig.HeightMode.CUSTOM),
                button -> selectHeightMode(PushdozerConfig.HeightMode.CUSTOM))
                .dimensions(panelLeft + BUTTON_MARGIN * 2 + buttonWidth, startY + BUTTON_HEIGHT + BUTTON_MARGIN, buttonWidth, BUTTON_HEIGHT)
                .tooltip(Tooltip.of(Text.translatable("pushdozer.tooltip.height_custom")))
                .build();

        // 滑动条
        int currentHeight = config.getLockedHeight();
        heightSlider = new HeightConfigSlider(
                panelLeft + BUTTON_MARGIN,
                startY + (BUTTON_HEIGHT + BUTTON_MARGIN) * 2,
                buttonFullWidth,
                BUTTON_HEIGHT,
                Text.translatable("pushdozer.config.height_value", currentHeight),
                (currentHeight + 64) / 384.0
        );

        // 确定按钮
        ButtonWidget doneButton = ButtonWidget.builder(
                        Text.translatable("pushdozer.button.done"),
                        button -> {
                            hide();
                            parent.showMainPanel();
                        })
                .dimensions(panelLeft + BUTTON_MARGIN, panelTop + PANEL_HEIGHT - BUTTON_HEIGHT - BUTTON_MARGIN, buttonFullWidth, BUTTON_HEIGHT)
                .build();

        widgets.add(followPlayerButton);
        widgets.add(lockOnceButton);
        widgets.add(noLimitButton);
        widgets.add(customHeightButton);
        widgets.add(heightSlider);
        widgets.add(doneButton);
    }

    /**
     * 获取按钮文本（带选中标记）
     */
    private Text getButtonText(PushdozerConfig.HeightMode mode) {
        PushdozerConfig.HeightMode currentMode = config.getHeightMode();
        boolean isLockedOnce = config.isLockedOnceMode();
        
        String prefix;
        if (mode == PushdozerConfig.HeightMode.LOCKED_ONCE) {
            prefix = isLockedOnce ? "☑ " : "";
        } else if (mode == PushdozerConfig.HeightMode.CUSTOM) {
            prefix = (currentMode == mode && !isLockedOnce) ? "☑ " : "";
        } else {
            prefix = (currentMode == mode) ? "☑ " : "";
        }
        
        Text baseText = switch (mode) {
            case FOLLOW_PLAYER -> Text.translatable("pushdozer.config.height_follow_player");
            case LOCKED_ONCE -> Text.translatable("pushdozer.config.height_locked_once");
            case NO_LIMIT -> Text.translatable("pushdozer.config.height_no_limit");
            case CUSTOM -> Text.translatable("pushdozer.config.height_custom");
        };
        return Text.literal(prefix).append(baseText);
    }

    /**
     * 预计算标题位置以优化渲染性能。
     */
    private void initializeTitlePosition() {
        titleText = Text.translatable("pushdozer.panel.height_config.title");

        if (parent.getClient() != null) {
            int titleWidth = parent.getClient().textRenderer.getWidth(titleText);
            titleX = panelLeft + (PANEL_WIDTH - titleWidth) / 2;
            titleY = panelTop + (TITLE_HEIGHT - parent.getClient().textRenderer.fontHeight) / 2;
        }
    }

    /**
     * 选择指定的标高模式。
     * 
     * @param mode 要选择的标高模式
     */
    private void selectHeightMode(PushdozerConfig.HeightMode mode) {
        if (mode == PushdozerConfig.HeightMode.LOCKED_ONCE) {
            // 锁定到玩家标高：将当前玩家脚下方块高度写入 config 并切换到 LOCKED_ONCE
            if (parent.getClient() != null && parent.getClient().player != null) {
                int y = parent.getClient().player.getBlockY() - 1; // 玩家脚下方块标高
                config.setLockedHeight(y);
                config.setHeightMode(PushdozerConfig.HeightMode.CUSTOM);
                config.setLockedOnceMode(true);
                updateButtonStates();
                heightSlider.active = false;
                PushdozerMod.saveConfig();
                parent.getClient().player.sendMessage(Text.translatable("pushdozer.message.height_mode_locked_once", y), true);
            }
        } else {
            config.setHeightMode(mode);
            config.setLockedOnceMode(false);
            updateButtonStates();
            heightSlider.active = mode == PushdozerConfig.HeightMode.CUSTOM;
            PushdozerMod.saveConfig();
            if (parent.getClient() != null && parent.getClient().player != null) {
                Text message = switch (mode) {
                    case FOLLOW_PLAYER -> Text.translatable("pushdozer.message.height_mode_follow_player");
                    case NO_LIMIT -> Text.translatable("pushdozer.message.height_mode_no_limit");
                    case CUSTOM -> {
                        int customHeight = config.getLockedHeight();
                        yield Text.translatable("pushdozer.message.height_mode_custom", customHeight);
                    }
                    default -> throw new IllegalStateException("Unexpected value: " + mode);
                };
                parent.getClient().player.sendMessage(message, true);
            }
        }
    }

    /**
     * 更新按钮状态。
     */
    private void updateButtonStates() {
        followPlayerButton.setMessage(getButtonText(PushdozerConfig.HeightMode.FOLLOW_PLAYER));
        lockOnceButton.setMessage(getButtonText(PushdozerConfig.HeightMode.LOCKED_ONCE));
        noLimitButton.setMessage(getButtonText(PushdozerConfig.HeightMode.NO_LIMIT));
        customHeightButton.setMessage(getButtonText(PushdozerConfig.HeightMode.CUSTOM));
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
        updateButtonStates();
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
        renderTitle(context);
        renderButtons(context, mouseX, mouseY, delta);
    }

    /**
     * 渲染背景遮罩和面板背景。
     * 
     * @param context 绘制上下文
     */
    private void renderBackground(DrawContext context) {
        // 绘制面板背景（半透明）
        context.fill(panelLeft, panelTop, panelLeft + PANEL_WIDTH, panelTop + PANEL_HEIGHT, COLOR_PANEL_BG);

        // 绘制标题栏背景
        context.fill(panelLeft, panelTop, panelLeft + PANEL_WIDTH, panelTop + TITLE_HEIGHT, COLOR_TITLE_BG);

        // 绘制面板边框（在标题背景之后，确保边框不被遮挡）
        context.drawBorder(panelLeft, panelTop, PANEL_WIDTH, PANEL_HEIGHT, COLOR_PANEL_BORDER);
    }

    /**
     * 渲染标题文本。
     * 
     * @param context 绘制上下文
     */
    private void renderTitle(DrawContext context) {
        if (titleText != null && parent.getClient() != null) {
            context.drawTextWithShadow(parent.getClient().textRenderer, titleText, titleX, titleY, COLOR_WHITE);
        }
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
        for (Element widget : widgets) {
            if (widget instanceof Drawable) {
                ((Drawable) widget).render(context, mouseX, mouseY, delta);
            }
        }
        PushdozerConfig.HeightMode currentMode = config.getHeightMode();
        boolean isLockedOnce = config.isLockedOnceMode();
        // 高亮当前选中的按钮
        if (currentMode == PushdozerConfig.HeightMode.FOLLOW_PLAYER && followPlayerButton != null) {
            context.fill(
                followPlayerButton.getX(), 
                followPlayerButton.getY(),
                followPlayerButton.getX() + followPlayerButton.getWidth(),
                followPlayerButton.getY() + followPlayerButton.getHeight(),
                0xFF888888
            );
            context.fill(
                followPlayerButton.getX()+1, 
                followPlayerButton.getY()+1,
                followPlayerButton.getX() + followPlayerButton.getWidth()-1,
                followPlayerButton.getY() + followPlayerButton.getHeight()-1,
                0xFF555555
            );
        }
        if (isLockedOnce && lockOnceButton != null) {
            context.fill(
                lockOnceButton.getX(), 
                lockOnceButton.getY(),
                lockOnceButton.getX() + lockOnceButton.getWidth(),
                lockOnceButton.getY() + lockOnceButton.getHeight(),
                0xFF888888
            );
            context.fill(
                lockOnceButton.getX()+1, 
                lockOnceButton.getY()+1,
                lockOnceButton.getX() + lockOnceButton.getWidth()-1,
                lockOnceButton.getY() + lockOnceButton.getHeight()-1,
                0xFF555555
            );
        }
        if (currentMode == PushdozerConfig.HeightMode.NO_LIMIT && noLimitButton != null) {
            context.fill(
                noLimitButton.getX() , 
                noLimitButton.getY() ,
                noLimitButton.getX() + noLimitButton.getWidth() ,
                noLimitButton.getY() + noLimitButton.getHeight() ,
                0xFF888888
            );
            context.fill(
                noLimitButton.getX()+1, 
                noLimitButton.getY()+1,
                noLimitButton.getX() + noLimitButton.getWidth()-1,
                noLimitButton.getY() + noLimitButton.getHeight()-1,
                0xFF555555
            );
        }
        if (currentMode == PushdozerConfig.HeightMode.CUSTOM && !isLockedOnce && customHeightButton != null) {
            context.fill(
                customHeightButton.getX(), 
                customHeightButton.getY(),
                customHeightButton.getX() + customHeightButton.getWidth(),
                customHeightButton.getY() + customHeightButton.getHeight(),
                0xFF888888
            );
            context.fill(
                customHeightButton.getX()+1, 
                customHeightButton.getY()+1,
                customHeightButton.getX() + customHeightButton.getWidth()-1,
                customHeightButton.getY() + customHeightButton.getHeight()-1,
                0xFF555555
            );
        }
    }

    /**
     * 处理鼠标点击事件。
     * 
     * @param mouseX 鼠标 X 坐标
     * @param mouseY 鼠标 Y 坐标
     * @param button 鼠标按钮
     * @return 如果事件被处理则返回 true
     */
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        for (Element widget : widgets) {
            if (widget instanceof ClickableWidget clickable) {
                if (clickable.isMouseOver(mouseX, mouseY)) {
                    return clickable.mouseClicked(mouseX, mouseY, button);
                }
            }
        }
        return false;
    }

    /**
     * 处理鼠标拖拽事件。
     * 
     * @param mouseX 鼠标 X 坐标
     * @param mouseY 鼠标 Y 坐标
     * @param button 鼠标按钮
     * @param deltaX X 方向移动距离
     * @param deltaY Y 方向移动距离
     * @return 如果事件被处理则返回 true
     */
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        for (Element widget : widgets) {
            if (widget instanceof ClickableWidget clickable) {
                if (clickable.isMouseOver(mouseX, mouseY)) {
                    return clickable.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
                }
            }
        }
        return false;
    }

    /**
     * 处理鼠标释放事件。
     * 
     * @param mouseX 鼠标 X 坐标
     * @param mouseY 鼠标 Y 坐标
     * @param button 鼠标按钮
     * @return 如果事件被处理则返回 true
     */
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        for (Element widget : widgets) {
            if (widget instanceof ClickableWidget clickable) {
                if (clickable.isMouseOver(mouseX, mouseY)) {
                    return clickable.mouseReleased(mouseX, mouseY, button);
                }
            }
        }
        return false;
    }

    /**
     * 标高配置滑动条
     */
    private class HeightConfigSlider extends SliderWidget {
        public HeightConfigSlider(int x, int y, int width, int height, Text text, double value) {
            super(x, y, width, height, text, value);
        }

        @Override
        protected void updateMessage() {
            int height = (int) Math.round(this.value * 384) - 64;
            setMessage(Text.translatable("pushdozer.config.height_value", height));
        }

        @Override
        protected void applyValue() {
            int height = (int) Math.round(this.value * 384) - 64;
            config.setLockedHeight(height);
        }
    }
} 