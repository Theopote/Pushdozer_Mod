package com.pushdozer.ui.screens;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.gui.screen.Screen;
import org.lwjgl.glfw.GLFW;

import com.pushdozer.PushdozerMod;
import com.pushdozer.config.PushdozerConfig;
import com.pushdozer.items.PushdozerItem;
import com.pushdozer.ui.panels.BoxSubPanel;
import com.pushdozer.ui.panels.GeometrySubPanel;
import com.pushdozer.ui.panels.SphereSubPanel;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.Drawable;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.client.gui.tooltip.Tooltip;

/**
 * PushdozerConfigScreen 类
 * 用于创建和管理 Pushdozer 工具的配置界面
 */
public class PushdozerConfigScreen extends Screen {
    // Pushdozer 配置对象
    private final PushdozerConfig config;
    
    // UI 组件
    private ButtonWidget applyAndCloseButton;      // 应用并关闭按钮
    private ButtonWidget geometryTypeButton;       // 几何类型按钮
    private ButtonWidget displayModeButton;        // 显示模式按钮
    private ButtonWidget selectBlocksButton;       // 选择方块按钮
    private GeometrySubPanel currentSubPanel;      // 当前显示的几何子面板

    // 面板尺寸常量
    private static final int PANEL_WIDTH = 240;    // 
    private static final int TITLE_HEIGHT = 20;    // 标题高度
    private static final int SCREEN_MARGIN = 30;   // 屏幕边距
    private static final int GEOMETRY_TYPE_HEIGHT = 20; // 几何类型选项高度

    // 面板位置
    public int panelLeft, panelTop;

    // 记录界面打开时间
    private long openTime;
    // 关闭延迟时间（毫秒）
    private static final long CLOSE_DELAY = 150;

    // 几何类型选项列表
    private final List<ButtonWidget> geometryTypeOptions = new ArrayList<>();
    private boolean isDropdownOpen = false;        // 列表是否打开

    private boolean mainPanelVisible = true;       // 主面板是否可见

    // 最大操作距离滑动条
    private CustomDistanceSlider distanceSlider;
    // 工作模式相关
    private ButtonWidget workModeButton;
    private final List<ButtonWidget> workModeOptions = new ArrayList<>();
    private boolean isWorkModeDropdownOpen = false;

    // 几何体类型按钮
    private ButtonWidget boxButton;
    private ButtonWidget sphereButton;
    private ButtonWidget boxConfigButton;
    private ButtonWidget sphereConfigButton;

    // 标高锁定按钮
    private ButtonWidget heightLockButton;
    private boolean isHeightLocked = false;   // 标高锁定状态

    /**
     * 构造函数
     * @param config Pushdozer配置对象
     */
    public PushdozerConfigScreen(PushdozerConfig config) {
        super(Text.of("Pushdozer Configuration"));
        this.config = config; // 初始化配置对象
        MinecraftClient client = MinecraftClient.getInstance();
        // 检查玩家是否手持 Pushdozer 物品
        if (client.player != null && !(client.player.getMainHandStack().getItem() instanceof PushdozerItem)) {
            client.setScreen(null);
            return;
        }
        this.openTime = System.currentTimeMillis();
        // 从配置中读取标高锁定状态
        this.isHeightLocked = config.isHeightLocked();
    }


    /**
     * 初始方法，设置UI组件
     */
    @Override
    protected void init() {
        super.init();
        // 检查配置是否正确初始化
        if (config == null) {
            PushdozerMod.LOGGER.error("PushdozerConfigScreen: config is null");
            return;
        }
        
        // 保存当前子面板状态
        GeometrySubPanel previousSubPanel = currentSubPanel;
        boolean wasSubPanelVisible = previousSubPanel != null && previousSubPanel.isVisible();
        String previousPanelType = wasSubPanelVisible ? 
            (previousSubPanel instanceof BoxSubPanel ? "Box" : "Sphere") : null;

        // 初始化所有UI组件
        initializeComponents();

        // 如果之前有子面板在显示，恢复子面板状态
        if (wasSubPanelVisible && previousPanelType != null) {
            if (previousPanelType.equals("Box")) {
                showBoxConfigPanel();
            } else if (previousPanelType.equals("Sphere")) {
                showSphereConfigPanel();
            }
        }
    }

    // 将init()方法中的UI组件初始化逻辑移到这个新方法中
    private void initializeComponents() {
        if (config.getDisplayMode() == null) {
            PushdozerMod.LOGGER.error(Text.translatable("pushdozer.error.display_mode_null").getString());
            return;
        }
        
        // 设置默认工作模式
        if (config.getWorkMode() == null) {
            config.setWorkMode(PushdozerConfig.WorkMode.DESTROY);
        }
        
        Text currentWorkMode = config.getWorkMode().getDisplayText();
        // 计算面板位置
        panelLeft = (this.width - PANEL_WIDTH) / 2;
        panelTop = SCREEN_MARGIN;

        // 更新内容区域的位置和尺寸
        int contentLeft = panelLeft + 10; // 从15改为20，给主面板多留10像素边距
        int contentTop = panelTop + TITLE_HEIGHT + 10;
        int rowHeight = 20;
        int verticalGap = 5;
        int buttonWidth = 60;
        int configButtonWidth = 40;
        
        // 计算按钮的位置
        int availableWidth = PANEL_WIDTH - 40; // 从30改为40，因为面板边距增加了10像素
        int totalButtonWidth = buttonWidth * 2 + configButtonWidth * 2 - 10; // 总按钮宽度保持不变
        int leftMargin = (availableWidth - totalButtonWidth) / 2; // 计算居中边距
        
        // 计算按钮X坐标
        int boxButtonX = contentLeft + leftMargin;
        int boxConfigButtonX = boxButtonX + buttonWidth;
        int sphereButtonX = boxConfigButtonX + configButtonWidth + 10;
        int sphereConfigButtonX = sphereButtonX + buttonWidth;
        
        // 删除第一行的标签，直接添加按钮
        boxButton = this.addDrawableChild(ButtonWidget.builder(
            Text.translatable("pushdozer.button.box_unchecked"), 
            button -> toggleGeometryType("Box"))
            .dimensions(boxButtonX, contentTop, buttonWidth, rowHeight)
            .tooltip(Tooltip.of(Text.translatable("pushdozer.tooltip.box")))
            .build());
        
        // 长方体配置按钮
        boxConfigButton = this.addDrawableChild(ButtonWidget.builder(
            Text.translatable("pushdozer.button.config"), 
            button -> showBoxConfigPanel())
            .dimensions(boxConfigButtonX, contentTop, configButtonWidth, rowHeight)
            .tooltip(Tooltip.of(Text.translatable("pushdozer.tooltip.box_config")))
            .build());

        // 球形按钮
        sphereButton = this.addDrawableChild(ButtonWidget.builder(
            Text.of("☐ Sphere"), 
            button -> toggleGeometryType("Sphere"))
            .dimensions(sphereButtonX, contentTop, buttonWidth, rowHeight)
            .tooltip(Tooltip.of(Text.translatable("pushdozer.tooltip.sphere")))
            .build());

        // 球形配置按钮
        sphereConfigButton = this.addDrawableChild(ButtonWidget.builder(
            Text.translatable("pushdozer.button.config"), 
            button -> showSphereConfigPanel())
            .dimensions(sphereConfigButtonX, contentTop, configButtonWidth, rowHeight)
            .tooltip(Tooltip.of(Text.translatable("pushdozer.tooltip.sphere_config")))
            .build());

        updateGeometryTypeButtons();

        // 第二行：工作模式按钮（删除标签）
        int workModeButtonWidth = sphereConfigButtonX + configButtonWidth - boxButtonX;
        workModeButton = this.addDrawableChild(ButtonWidget.builder(
            getWorkModeText(), 
            button -> toggleWorkMode())
            .dimensions(boxButtonX, contentTop + rowHeight + verticalGap, workModeButtonWidth, rowHeight)
            .tooltip(Tooltip.of(Text.translatable("pushdozer.tooltip.work_mode")))
            .build());

        // 第三行：显示模式按钮（删除标签）
        displayModeButton = this.addDrawableChild(ButtonWidget.builder(
            config.getDisplayMode().getDisplayText(), 
            button -> toggleDisplayMode())
            .dimensions(boxButtonX, contentTop + (rowHeight + verticalGap) * 2, workModeButtonWidth, rowHeight)
            .tooltip(Tooltip.of(Text.translatable("pushdozer.tooltip.display_mode")))
            .build());

        // 第四行：最大操作距离滑动条
        distanceSlider = this.addDrawableChild(new CustomDistanceSlider(
            boxButtonX,
            contentTop + (rowHeight + verticalGap) * 3,
            workModeButtonWidth,
            rowHeight,
            Text.translatable("pushdozer.label.maximum_operation_distance", config.getMaxOperationDistance()),
            config.getMaxOperationDistance() / (float)PushdozerConfig.MAX_OPERATION_DISTANCE
        ));
        distanceSlider.setTooltip(Tooltip.of(Text.translatable("pushdozer.tooltip.distance_slider")));

        // 第五行：选择允许被破坏的方块按钮
        selectBlocksButton = this.addDrawableChild(ButtonWidget.builder(
            Text.translatable("pushdozer.config.block_selection"),
            button -> openBlockSelectionScreen())
            .dimensions(boxButtonX, contentTop + (rowHeight + verticalGap) * 4, workModeButtonWidth, rowHeight)
            .tooltip(Tooltip.of(Text.translatable("pushdozer.tooltip.block_selection")))
            .build());

        // 第六行：标高锁定按钮和应用并关闭按钮
        int buttonSpacing = 10; // 按钮之间的间距
        int totalAvailableWidth = workModeButtonWidth; // 总可用宽度与上面的按钮相同
        int heightLockButtonWidth = (totalAvailableWidth - buttonSpacing) / 2; // 平均分配宽度
        int applyCloseButtonWidth = totalAvailableWidth - buttonSpacing - heightLockButtonWidth; // 剩余宽度给应用关闭按钮

        // 添加标高锁定按钮 - 使用配置中的状态
        heightLockButton = this.addDrawableChild(ButtonWidget.builder(
            Text.translatable(isHeightLocked ? 
                "pushdozer.config.height_lock" : "pushdozer.config.height_free"),
            button -> toggleHeightLock())
            .dimensions(boxButtonX, contentTop + (rowHeight + verticalGap) * 5, heightLockButtonWidth, rowHeight)
            .tooltip(Tooltip.of(Text.translatable("pushdozer.tooltip.height_lock")))
            .build());

        // 添加应用并关闭按钮
        applyAndCloseButton = this.addDrawableChild(ButtonWidget.builder(
            Text.translatable("pushdozer.config.save_and_close"),
            button -> applyAndClose())
            .dimensions(boxButtonX + heightLockButtonWidth + buttonSpacing, 
                       contentTop + (rowHeight + verticalGap) * 5, 
                       applyCloseButtonWidth, 
                       rowHeight)
            .tooltip(Tooltip.of(Text.translatable("pushdozer.tooltip.save_close")))
            .build());
    }

    /**
     * 切换笔刷形状
     */
    private void toggleGeometryType(String shape) {
        config.setShape(shape);
        updateGeometryTypeButtons();
        PushdozerMod.saveConfig();
        
        // 添加形状切换成功的提示
        showErrorMessage(Text.translatable("pushdozer.message.shape_switch", shape).getString());
    }

    /**
     * 更新笔刷形状按钮的显示状态
     */
    private void updateGeometryTypeButtons() {
        String currentShape = config.getShape();
        
        // 更新按钮状态
        boxButton.setMessage(Text.translatable(currentShape.equals("Box") ? 
            "pushdozer.button.box_checked" : "pushdozer.button.box_unchecked"));
        sphereButton.setMessage(Text.translatable(currentShape.equals("Sphere") ? 
            "pushdozer.button.sphere_checked" : "pushdozer.button.sphere_unchecked"));
        
        // 强制更新预览
        updatePreview();
    }


    /**
     * 显示长方体配置面板
     */
    private void showBoxConfigPanel() {
        currentSubPanel = new BoxSubPanel(this, config);
        showSubPanel();
    }

    /**
     * 显示球体配置面板
     */
    private void showSphereConfigPanel() {
        currentSubPanel = new SphereSubPanel(this, config);
        showSubPanel();
    }

    /**
     * 显示子面板
     */
    private void showSubPanel() {
        if (currentSubPanel != null) {
            currentSubPanel.initPanel();
            currentSubPanel.show();
            hideMainPanel();
        }
    }

    /**
     * 隐藏主面板
     */
    private void hideMainPanel() {
        mainPanelVisible = false;
        // 隐藏主界面上的所有组件
        if (boxButton != null) boxButton.visible = false;
        if (sphereButton != null) sphereButton.visible = false;
        if (boxConfigButton != null) boxConfigButton.visible = false;
        if (sphereConfigButton != null) sphereConfigButton.visible = false;
        if (workModeButton != null) workModeButton.visible = false;
        if (displayModeButton != null) displayModeButton.visible = false;
        if (distanceSlider != null) distanceSlider.visible = false;
        if (selectBlocksButton != null) selectBlocksButton.visible = false;
        if (heightLockButton != null) heightLockButton.visible = false;
        if (applyAndCloseButton != null) applyAndCloseButton.visible = false;
    }

    /**
     * 每帧调用的方法，用于检测按键
     */
    @Override
    public void tick() {
        super.tick();
        // 检查 K 键是否被按下，并且经过了延迟时间
        if (InputUtil.isKeyPressed(MinecraftClient.getInstance().getWindow().getHandle(), GLFW.GLFW_KEY_K) &&
            System.currentTimeMillis() - openTime > CLOSE_DELAY) {
            this.close();
        }
    }


    /**
     * 处理按键事件
     */
    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            if (currentSubPanel != null) {
                hideSubPanel();
                return true;
            }
        }

        // 处理上下箭头键调整最大操作距离
        if (keyCode == GLFW.GLFW_KEY_UP || keyCode == GLFW.GLFW_KEY_DOWN) {
            if (this.client != null && this.client.player != null) {
                int currentDistance = config.getMaxOperationDistance();
                int newDistance = currentDistance;
                
                // 上箭头增加距离，下箭头减少距离
                if (keyCode == GLFW.GLFW_KEY_UP) {
                    newDistance = Math.min(currentDistance + 1, PushdozerConfig.MAX_OPERATION_DISTANCE);
                } else {
                    newDistance = Math.max(currentDistance - 1, 1);
                }
                
                if (newDistance != currentDistance) {
                    // 更新滑动条的值，它会自动更新配置和显示消息
                    if (distanceSlider != null) {
                        distanceSlider.setCustomValue(newDistance / (float)PushdozerConfig.MAX_OPERATION_DISTANCE);
                    }
                    return true;
                }
            }
        }

        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    /**
     * 关闭界面时调用的方法
     */
    @Override
    public void close() {
        PushdozerMod.saveConfig();
        super.close();
    }

    /**
     * 渲染方法，用于绘制整个界面
     */
    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        if (mainPanelVisible) {
            // 计算新板高度
            int panelHeight = applyAndCloseButton.getY() + applyAndCloseButton.getHeight() + 10 - panelTop;
            
            // 绘制半透明主面板背景
            context.fill(panelLeft + 5, panelTop, panelLeft + PANEL_WIDTH - 5, panelTop + panelHeight, 0x80000000);
            
            // 绘制半透明标题背景
            context.fill(panelLeft + 5, panelTop, panelLeft + PANEL_WIDTH - 5, panelTop + TITLE_HEIGHT, 0xE0303030);
            
            // 绘制边框
            context.drawBorder(panelLeft + 5, panelTop, PANEL_WIDTH - 10, panelHeight, 0xFFFFFFFF);
            
            // 绘制标题
            if (this.textRenderer != null) {
                Text title = Text.translatable("pushdozer.config.title")
                    .formatted(Formatting.BOLD, Formatting.YELLOW);
                int titleWidth = this.textRenderer.getWidth(title);
                context.drawText(this.textRenderer, title,
                    panelLeft + (PANEL_WIDTH - titleWidth) / 2, 
                    panelTop + (TITLE_HEIGHT - this.textRenderer.fontHeight) / 2, 
                    0xFFFFFF, false);
            }

            // 渲染他UI元素
            for (Element child : this.children()) {
                if (child instanceof Drawable) {
                    ((Drawable) child).render(context, mouseX, mouseY, delta);
                    
                    // 如果是按钮，额外渲染文字
                    if (child instanceof ButtonWidget button) {
                        renderButtonText(context, button, mouseX, mouseY);
                    }
                }
            }

            // ... 渲染其他主界面元素 ...
            boxButton.render(context, mouseX, mouseY, delta);
            sphereButton.render(context, mouseX, mouseY, delta);
            boxConfigButton.render(context, mouseX, mouseY, delta);
            sphereConfigButton.render(context, mouseX, mouseY, delta);
        }

        // 只在子面板显示时渲染它
        if (currentSubPanel != null && currentSubPanel.isVisible()) {
            currentSubPanel.render(context, mouseX, mouseY, delta);
        }

        // 始终渲染应用并关闭按钮
        applyAndCloseButton.render(context, mouseX, mouseY, delta);

        // 在最后渲染下拉列表选项，确保它们显示在其他所有元素之上
        if (mainPanelVisible) {
            if (isDropdownOpen) {
                renderDropdownOptions(context, mouseX, mouseY, delta);
            }

            if (isWorkModeDropdownOpen) {
                renderWorkModeOptions(context, mouseX, mouseY, delta);
            }
        }
    }

    private void renderButtonText(DrawContext context, ButtonWidget button, int mouseX, int mouseY) {
        // 移除这个方法，因为它可能导致重复渲染文字
    }

    private void renderDropdownOptions(DrawContext context, int mouseX, int mouseY, float delta) {
        int dropdownHeight = geometryTypeOptions.size() * GEOMETRY_TYPE_HEIGHT;
        context.fill(geometryTypeButton.getX(), geometryTypeButton.getY() + GEOMETRY_TYPE_HEIGHT,
                     geometryTypeButton.getX() + geometryTypeButton.getWidth(), 
                     geometryTypeButton.getY() + GEOMETRY_TYPE_HEIGHT + dropdownHeight,
                     0xE0303030);

        for (ButtonWidget option : geometryTypeOptions) {
            option.render(context, mouseX, mouseY, delta);
            renderButtonText(context, option, mouseX, mouseY);
        }
    }

    private String getDisplayModeText() {
        return config.getDisplayMode().getDisplayText().getString();
    }

    private void toggleDisplayMode() {
        PushdozerConfig.DisplayMode[] modes = PushdozerConfig.DisplayMode.values();
        int nextIndex = (config.getDisplayMode().ordinal() + 1) % modes.length;
        PushdozerConfig.DisplayMode newMode = modes[nextIndex];
        config.setDisplayMode(newMode);
        displayModeButton.setMessage(Text.of(getDisplayModeText()));
        PushdozerMod.saveConfig();
        showErrorMessage(Text.translatable("pushdozer.message.display_mode_switch", getDisplayModeText()).getString());
    }

    private void toggleWorkModeDropdown(boolean open) {
        isWorkModeDropdownOpen = open;
        if (isWorkModeDropdownOpen) {
            for (PushdozerConfig.WorkMode mode : PushdozerConfig.WorkMode.values()) {
                addWorkModeOption(mode.getDisplayText().getString(), mode.ordinal());
            }
        } else {
            workModeOptions.forEach(this::remove);
            workModeOptions.clear();
        }
    }

    private void addWorkModeOption(String mode, int index) {
        ButtonWidget option = this.addDrawableChild(ButtonWidget.builder(Text.of(mode), button -> {
            selectWorkMode(mode);
            toggleWorkModeDropdown(false);
        })
        .dimensions(workModeButton.getX(), 
                    workModeButton.getY() + 20 + (index * 20), 
                    workModeButton.getWidth(), 
                    20)
        .build());
        workModeOptions.add(option);
    }

    private void selectWorkMode(String mode) {
        for (PushdozerConfig.WorkMode workMode : PushdozerConfig.WorkMode.values()) {
            if (workMode.getDisplayText().getString().equals(mode)) {
                config.setWorkMode(workMode);
                workModeButton.setMessage(Text.of(mode));
                break;
            }
        }
    }

    private void renderWorkModeOptions(DrawContext context, int mouseX, int mouseY, float delta) {
        int dropdownHeight = workModeOptions.size() * 20;
        context.fill(workModeButton.getX(), workModeButton.getY() + 20,
                     workModeButton.getX() + workModeButton.getWidth(), 
                     workModeButton.getY() + 20 + dropdownHeight,
                     0xE0303030);

        for (ButtonWidget option : workModeOptions) {
            option.render(context, mouseX, mouseY, delta);
        }
    }

    /**
     * 确定界面是否应该暂停游戏
     */
    @Override
    public boolean shouldPause() {
        return false;
    }

    /**
     * 显示错误消息
     * @param message 错误消息内容
     */
    public void showErrorMessage(String message) {
        if (this.client != null && this.client.player != null) {
            this.client.player.sendMessage(Text.literal(message), false);
        }
    }

    public int getScreenWidth() {
        return this.width;
    }

    public int getScreenHeight() {
        return this.height;
    }

    private void openBlockSelectionScreen() {
        if (this.client != null) {
            BlockSelectionScreen blockSelectionScreen = new BlockSelectionScreen(this, config);
            this.client.setScreen(blockSelectionScreen);
        }
    }

    private void applyAndClose() {
        // 确保在关闭前保存所有配置
        config.setHeightLocked(isHeightLocked);
        if (isHeightLocked && this.client != null && this.client.player != null) {
            config.setLockedHeight(this.client.player.getBlockY());
        }
        config.save();
        this.close();
    }

    private Text getWorkModeText() {
        return config.getWorkMode().getDisplayText();
    }


    private void toggleWorkMode() {
        PushdozerConfig.WorkMode[] modes = PushdozerConfig.WorkMode.values();
        int nextIndex = (config.getWorkMode().ordinal() + 1) % modes.length;
        PushdozerConfig.WorkMode newMode = modes[nextIndex];
        config.setWorkMode(newMode);
        workModeButton.setMessage(getWorkModeText());
        PushdozerMod.saveConfig();
        showErrorMessage(Text.translatable("pushdozer.message.working_mode_switch", getWorkModeText().getString()).getString());
    }


    private void toggleDropdown(boolean open) {
        isDropdownOpen = open;
        if (isDropdownOpen) {
            // 显示下拉选
            geometryTypeOptions.forEach(option -> option.visible = true);
        } else {
            // 藏下拉选项
            geometryTypeOptions.forEach(option -> option.visible = false);
        }
    }


    /**
     * 示主面板
     */
    public void showMainPanel() {
        mainPanelVisible = true;
        // 显示主面上的所有组件
        boxButton.visible = true;
        sphereButton.visible = true;
        boxConfigButton.visible = true;
        sphereConfigButton.visible = true;
        workModeButton.visible = true;
        displayModeButton.visible = true;
        distanceSlider.visible = true;
        selectBlocksButton.visible = true;
        heightLockButton.visible = true;      // 添加这行
        applyAndCloseButton.visible = true;
    }

    private void toggleHeightLock() {
        isHeightLocked = !isHeightLocked;
        heightLockButton.setMessage(Text.translatable(isHeightLocked ? 
            "pushdozer.config.height_lock" : "pushdozer.config.height_free"));
        
        // 如果切换到锁定状态，记录当前玩家的Y坐标
        if (isHeightLocked && this.client != null && this.client.player != null) {
            config.setLockedHeight(this.client.player.getBlockY());
            // 使用翻译键显示提示消息
            this.client.player.sendMessage(
                Text.translatable("pushdozer.message.locked_height", config.getLockedHeight()), 
                true
            );
        }
        
        config.setHeightLocked(isHeightLocked);
        PushdozerMod.saveConfig();
    }

    /**
     * 更新几何体预览
     */
    public void updatePreview() {
        // 移除发送消息的代码
        // 如果将来需要添加其他预览更新逻辑,可以在这里添加
    }

    /**
     * 获取MinecraftClient实例
     */
    public MinecraftClient getClient() {
        return this.client;
    }
    /**
     * 隐藏子面板
     */
    public void hideSubPanel() {
        if (currentSubPanel != null) {
            currentSubPanel.hide();
            currentSubPanel = null;
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (isDropdownOpen) {
            for (ButtonWidget option : geometryTypeOptions) {
                if (option.isMouseOver(mouseX, mouseY)) {
                    option.onPress();
                    return true;
                }
            }
            toggleDropdown(false);
        }
        if (isWorkModeDropdownOpen) {
            for (ButtonWidget option : workModeOptions) {
                if (option.isMouseOver(mouseX, mouseY)) {
                    option.onPress();
                    return true;
                }
            }
            toggleWorkModeDropdown(false);
        }
        if (currentSubPanel != null && currentSubPanel.isVisible()) {
            for (Element widget : currentSubPanel.getWidgets()) {
                if (widget instanceof ClickableWidget clickable) {
                    if (clickable.isMouseOver(mouseX, mouseY)) {
                        return clickable.mouseClicked(mouseX, mouseY, button);
                    }
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (currentSubPanel != null && currentSubPanel.isVisible()) {
            for (Element widget : currentSubPanel.getWidgets()) {
                if (widget instanceof ClickableWidget clickable) {
                    if (clickable.isMouseOver(mouseX, mouseY)) {
                        return clickable.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
                    }
                }
            }
        }
        return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (currentSubPanel != null && currentSubPanel.isVisible()) {
            for (Element widget : currentSubPanel.getWidgets()) {
                if (widget instanceof ClickableWidget clickable) {
                    if (clickable.isMouseOver(mouseX, mouseY)) {
                        return clickable.mouseReleased(mouseX, mouseY, button);
                    }
                }
            }
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    /**
     * 自定义距离滑动条类，提供公共方法来设置值
     */
    private class CustomDistanceSlider extends SliderWidget {
        public CustomDistanceSlider(int x, int y, int width, int height, Text text, double value) {
            super(x, y, width, height, text, value);
        }

        @Override
        protected void updateMessage() {
            setMessage(Text.translatable("pushdozer.label.maximum_operation_distance", 
                (int)(this.value * PushdozerConfig.MAX_OPERATION_DISTANCE)));
        }

        @Override
        protected void applyValue() {
            int newDistance = (int)(this.value * PushdozerConfig.MAX_OPERATION_DISTANCE);
            config.setMaxOperationDistance(newDistance);
        }

        public void setCustomValue(double value) {
            if (this.value != value) {
                this.value = value;
                this.updateMessage();
                this.applyValue();
            }
        }
    }
}