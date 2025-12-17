package com.pushdozer.ui.screens;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.gui.screen.Screen;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import com.pushdozer.PushdozerMod;
import com.pushdozer.config.PushdozerConfig;
import com.pushdozer.items.PushdozerItem;
import com.pushdozer.ui.panels.*;
import com.pushdozer.ui.panels.brushgeometry.BoxSubPanel;
import com.pushdozer.ui.panels.brushgeometry.ConeSubPanel;
import com.pushdozer.ui.panels.brushgeometry.CylinderSubPanel;
import com.pushdozer.ui.panels.brushgeometry.EllipsoidSubPanel;
import com.pushdozer.ui.panels.brushgeometry.GeometrySelectionPanel;
import com.pushdozer.ui.panels.brushgeometry.GeometrySubPanel;
import com.pushdozer.ui.panels.brushgeometry.OctahedronSubPanel;
import com.pushdozer.ui.panels.brushgeometry.SphereSubPanel;
import com.pushdozer.ui.panels.brushgeometry.TetrahedronSubPanel;
import com.pushdozer.ui.panels.brushgeometry.TriangularPrismSubPanel;
import com.pushdozer.ui.panels.workmode.BatchPlantConfigPanel;
import com.pushdozer.ui.panels.workmode.BoneMealConfigPanel;
import com.pushdozer.ui.panels.workmode.ExcavateConfigPanel;
import com.pushdozer.ui.panels.workmode.PlaceConfigPanel;
import com.pushdozer.ui.panels.workmode.ShorelineProcessConfigPanel;
import com.pushdozer.ui.panels.workmode.SurfaceConvertConfigPanel;
import com.pushdozer.ui.panels.workmode.WorkModeConfigPanel;
import com.pushdozer.ui.panels.workmode.SmoothConfigPanel;
import com.pushdozer.ui.panels.workmode.SurfaceRoughenConfigPanel;
import com.pushdozer.ui.panels.workmode.WorkModeSelectionPanel;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.Drawable;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.client.input.KeyInput;
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
    private ButtonWidget displayModeButton;        // 显示模式按钮
    private GeometrySubPanel currentSubPanel;      // 当前显示的几何子面板

    // 面板尺寸常量
    private static final int PANEL_WIDTH = 280;    // 主面板加宽40
    private static final int TITLE_HEIGHT = 20;    // 标题高度

    // 面板位置
    public int panelLeft, panelTop;

    // 记录界面打开时间
    private long openTime;
    // 关闭延迟时间（毫秒）
    private static final long CLOSE_DELAY = 150;

    // 几何类型选项列表
    // 旧的几何体选择相关代码已移除

    private boolean mainPanelVisible = true;       // 主面板是否可见

    // 最大操作距离滑动条
    private CustomDistanceSlider distanceSlider;
    // 工作模式相关
    private ButtonWidget workModeButton;
    private ButtonWidget workModeConfigButton;
    private WorkModeSelectionPanel workModeSelectionPanel;
    private final List<ButtonWidget> workModeOptions = new ArrayList<>();
    private boolean isWorkModeDropdownOpen = false;
    
    // 工作模式配置面板
    private WorkModeConfigPanel currentWorkModeConfigPanel;

    // 几何体类型选择组件
    // 几何体按钮和选择面板
    private ButtonWidget geometryButton;
    private ButtonWidget geometryConfigButton;
    private GeometrySelectionPanel geometrySelectionPanel;

    // 标高配置按钮
    private ButtonWidget heightConfigButton;
    private HeightConfigPanel heightConfigPanel;

    /**
     * 构造函数
     * @param config Pushdozer配置对象
     */
    public PushdozerConfigScreen(PushdozerConfig config) {
        super(Text.translatable("pushdozer.config.title"));
        this.config = config; // 初始化配置对象
        MinecraftClient client = MinecraftClient.getInstance();
        // 检查玩家是否手持 Pushdozer 物品
        if (client.player != null && !(client.player.getMainHandStack().getItem() instanceof PushdozerItem)) {
            client.setScreen(null);
            return;
        }
        this.openTime = System.currentTimeMillis();

    }


    /**
     * 初始方法，设置UI组件
     */
    @Override
    protected void init() {
        super.init();
        // 检查配置是否正确初始化
        if (config == null) {
            PushdozerMod.LOGGER.error(Text.translatable("pushdozer.error.config_null").getString());
            return;
        }
        
        // 保存当前子面板状态
        GeometrySubPanel previousSubPanel = currentSubPanel;
        boolean wasSubPanelVisible = previousSubPanel != null && previousSubPanel.isVisible();
        String previousPanelType = getString(wasSubPanelVisible, previousSubPanel);

        // 初始化所有UI组件
        initializeComponents();

        // 如果之前有子面板在显示，恢复子面板状态
        if (wasSubPanelVisible && previousPanelType != null) {
            switch (previousPanelType) {
                case "Box":
                    showBoxConfigPanel();
                    break;
                case "Sphere":
                    showSphereConfigPanel();
                    break;
                case "Octahedron":
                    showOctahedronConfigPanel();
                    break;
                case "Cylinder":
                    showCylinderConfigPanel();
                    break;
                case "Cone":
                    showConeConfigPanel();
                    break;
                case "Ellipsoid":
                    showEllipsoidConfigPanel();
                    break;
            }
        }
    }

    private static @Nullable String getString(boolean wasSubPanelVisible, GeometrySubPanel previousSubPanel) {
        String previousPanelType = null;
        if (wasSubPanelVisible) {
            switch (previousSubPanel) {
                case BoxSubPanel boxSubPanel -> previousPanelType = "Box";
                case SphereSubPanel sphereSubPanel -> previousPanelType = "Sphere";
                case OctahedronSubPanel octahedronSubPanel -> previousPanelType = "Octahedron";
                case CylinderSubPanel cylinderSubPanel -> previousPanelType = "Cylinder";
                case ConeSubPanel coneSubPanel -> previousPanelType = "Cone";
                case EllipsoidSubPanel ellipsoidSubPanel -> previousPanelType = "Ellipsoid";
                default -> {
                }
            }
        }
        return previousPanelType;
    }

    // 将init()方法中的UI组件初始化逻辑移到这个新方法中
    private void initializeComponents() {
        // 确保显示模式有默认值
        if (config.getDisplayMode() == null) {
            PushdozerMod.LOGGER.error(Text.translatable("pushdozer.error.display_mode_null").getString());
            config.setDisplayMode(PushdozerConfig.DisplayMode.WIREFRAME);
        }
        
        // 设置默认工作模式
        if (config.getWorkMode() == null) {
            config.setWorkMode(PushdozerConfig.WorkMode.EXCAVATE);
        }

        // 计算面板位置（水平+垂直居中）
        panelLeft = (this.width - PANEL_WIDTH) / 2;
        panelTop = (this.height - computeMainPanelHeight()) / 2;

        // 更新内容区域的位置和尺寸（面板背景左右各20，控件再内缩5 → contentInset=25）
        int contentInset = 25;
        int contentLeft = panelLeft + contentInset; // 控件距面板背景左右各5
        int contentTop = panelTop + TITLE_HEIGHT + 5; // 标题下方间距5
        int rowHeight = 20;
        int verticalGap = 5;
        int finalButtonSpacing = 10; // 按钮之间的间距

        // 第一行：几何体类型下拉列表和配置按钮
        int buttonSpacing = 10;
        int availableWidth = PANEL_WIDTH - (contentInset * 2); // 可用宽度：面板宽度-左右控件边距
        int geomConfigButtonWidth = 60; // 配置按钮固定宽度
        int dropdownWidth = availableWidth - geomConfigButtonWidth - buttonSpacing; // 填满剩余
        // 行总宽度即可用宽度
        // 从左侧开始铺满

        // 创建几何体按钮
        geometryButton = this.addDrawableChild(ButtonWidget.builder(
            getGeometryButtonText(),
            button -> showGeometrySelectionPanel())
            .dimensions(contentLeft, contentTop, dropdownWidth, rowHeight)
            .tooltip(Tooltip.of(Text.translatable("pushdozer.tooltip.brush_shape_selection")))
            .build());
        
        // 创建几何体选择面板
        geometrySelectionPanel = new GeometrySelectionPanel(this, config, this::onGeometryTypeChanged);
        
        // 创建工作模式选择面板
        workModeSelectionPanel = new WorkModeSelectionPanel(this, config, this::onWorkModeChanged);
        
        // 创建配置按钮
        geometryConfigButton = this.addDrawableChild(ButtonWidget.builder(
            Text.translatable("pushdozer.button.config"),
            button -> showGeometryConfigPanel())
            .dimensions(contentLeft + dropdownWidth + buttonSpacing, contentTop, geomConfigButtonWidth, rowHeight)
            .tooltip(Tooltip.of(Text.translatable("pushdozer.tooltip.geometry_config")))
            .build());

        // 第二行：工作模式按钮和配置按钮
        workModeButton = this.addDrawableChild(ButtonWidget.builder(
            getWorkModeText(), 
            button -> showWorkModeSelectionPanel())
            .dimensions(contentLeft, contentTop + rowHeight + verticalGap, dropdownWidth, rowHeight)
            .tooltip(Tooltip.of(Text.translatable("pushdozer.tooltip.work_mode")))
            .build());
        
        // 创建工作模式配置按钮
        workModeConfigButton = this.addDrawableChild(ButtonWidget.builder(
            Text.translatable("pushdozer.button.config"),
            button -> showWorkModeConfigPanel())
            .dimensions(contentLeft + dropdownWidth + buttonSpacing, contentTop + rowHeight + verticalGap, geomConfigButtonWidth, rowHeight)
            .tooltip(Tooltip.of(Text.translatable("pushdozer.tooltip.work_mode_config")))
            .build());



        // 第四行：最大操作距离滑动条（铺满可用宽度）
        distanceSlider = new CustomDistanceSlider(
                contentLeft,
            contentTop + (rowHeight + verticalGap) * 3,
                availableWidth,
            rowHeight,
            Text.translatable("pushdozer.label.maximum_operation_distance", config.getMaxOperationDistance()),
            config.getMaxOperationDistance() / (float)PushdozerConfig.MAX_OPERATION_DISTANCE
        );
        distanceSlider.setTooltip(Tooltip.of(Text.translatable("pushdozer.tooltip.distance_slider")));
        this.addDrawableChild(distanceSlider);

        // 第三行：显示模式按钮（铺满可用宽度）
        displayModeButton = this.addDrawableChild(ButtonWidget.builder(
            getDisplayModeButtonText(), 
            button -> toggleDisplayMode())
            .dimensions(contentLeft, contentTop + (rowHeight + verticalGap) * 2, availableWidth, rowHeight)
            .tooltip(Tooltip.of(Text.translatable("pushdozer.tooltip.display_mode")))
            .build());

        // 第五行：标高配置按钮和应用并关闭按钮
        int heightConfigButtonWidth = (availableWidth - finalButtonSpacing) / 2; // 平均分配宽度
        int applyCloseButtonWidth = availableWidth - finalButtonSpacing - heightConfigButtonWidth; // 剩余宽度给应用关闭按钮

        // 添加标高配置按钮
        heightConfigButton = this.addDrawableChild(ButtonWidget.builder(
            Text.translatable("pushdozer.config.height_config"),
            button -> showHeightConfigPanel())
            .dimensions(contentLeft, contentTop + (rowHeight + verticalGap) * 4, heightConfigButtonWidth, rowHeight)
            .tooltip(Tooltip.of(Text.translatable("pushdozer.tooltip.height_config")))
            .build());

        // 添加应用并关闭按钮
        applyAndCloseButton = this.addDrawableChild(ButtonWidget.builder(
            Text.translatable("pushdozer.config.save_and_close"),
            button -> applyAndClose())
            .dimensions(contentLeft + heightConfigButtonWidth + finalButtonSpacing,
                       contentTop + (rowHeight + verticalGap) * 4, 
                       applyCloseButtonWidth, 
                       rowHeight)
            .tooltip(Tooltip.of(Text.translatable("pushdozer.tooltip.save_close")))
            .build());
    }

    // 计算主面板理论高度，用于在初始化阶段进行垂直居中
    private int computeMainPanelHeight() {
        int rowHeight = 20;
        int verticalGap = 5;
        int numRows = 5; // 几何/模式/显示模式/距离滑条/底部按钮
        int numGaps = numRows - 1;
        int topMarginBelowTitle = 5;
        int bottomMargin = 5;
        return TITLE_HEIGHT + topMarginBelowTitle + (rowHeight * numRows) + (verticalGap * numGaps) + bottomMargin;
    }

    /**
     * 显示几何体选择面板
     */
    private void showGeometrySelectionPanel() {
        if (geometrySelectionPanel != null) {
            geometrySelectionPanel.show();
        }
    }

    /**
     * 显示工作模式选择面板
     */
    private void showWorkModeSelectionPanel() {
        if (workModeSelectionPanel != null) {
            workModeSelectionPanel.show();
        }
    }

    /**
     * 显示工作模式配置面板
     */
    private void showWorkModeConfigPanel() {
        // 隐藏工作模式选择面板
        if (workModeSelectionPanel != null) {
            workModeSelectionPanel.hide();
        }
        
        // 根据当前工作模式显示不同的配置面板
        PushdozerConfig.WorkMode currentMode = config.getWorkMode();
        
        switch (currentMode) {
            case EXCAVATE:
                currentWorkModeConfigPanel = new ExcavateConfigPanel(this, config);
                break;
            case PLACE:
                currentWorkModeConfigPanel = new PlaceConfigPanel(this, config);
                break;
            case SMOOTH:
                currentWorkModeConfigPanel = new SmoothConfigPanel(this, config);
                break;
            case SMOOTH_RAISE, ADAPTIVE_SMOOTH, SMOOTH_LOWER:
                // 兼容旧平滑入口：直接打开统一平滑面板
                currentWorkModeConfigPanel = new SmoothConfigPanel(this, config);
                break;
            case SURFACE_ROUGHEN:
                currentWorkModeConfigPanel = new SurfaceRoughenConfigPanel(this, config);
                break;
            case SURFACE_CONVERT:
                currentWorkModeConfigPanel = new SurfaceConvertConfigPanel(this, config);
                break;
            case BONE_MEAL:
                currentWorkModeConfigPanel = new BoneMealConfigPanel(this, config);
                break;
            case BATCH_PLANT:
                currentWorkModeConfigPanel = new BatchPlantConfigPanel(this, config);
                break;
            case SHORELINE_PROCESS:
                currentWorkModeConfigPanel = new ShorelineProcessConfigPanel(this, config);
                break;
            default:
                showErrorMessage(Text.translatable("pushdozer.error.unknown_work_mode", currentMode.getDisplayText().getString()).getString());
                return;
        }
        
            currentWorkModeConfigPanel.show();
            hideMainPanel();
    }

    /**
     * 几何体类型改变时的回调
     */
    private void onGeometryTypeChanged(PushdozerConfig.GeometryType geometryType) {
        config.setGeometryType(geometryType);
        PushdozerMod.saveConfig();
        
        // 更新按钮文本
        if (geometryButton != null) {
            geometryButton.setMessage(getGeometryButtonText());
        }
        
        // 添加形状切换成功的提示
        showErrorMessage(Text.translatable("pushdozer.message.shape_switch", geometryType.getDisplayText().getString()).getString());
        
        // 强制更新预览
        updatePreview();
    }

    /**
     * 工作模式改变时的回调
     */
    private void onWorkModeChanged(PushdozerConfig.WorkMode workMode) {
        config.setWorkMode(workMode);
        PushdozerMod.saveConfig();
        
        // 更新按钮文本
        if (workModeButton != null) {
            workModeButton.setMessage(getWorkModeText());
        }
        
        // 添加工作模式切换成功的提示
        showErrorMessage(Text.translatable("pushdozer.message.working_mode_switch", workMode.getDisplayText().getString()).getString());
        
        // 强制更新预览
        updatePreview();
    }

    /**
     * 显示几何体配置面板
     */
    private void showGeometryConfigPanel() {
        PushdozerConfig.GeometryType currentType = config.getGeometryType();
        switch (currentType) {
            case BOX:
                showBoxConfigPanel();
                break;
            case SPHERE:
                showSphereConfigPanel();
                break;
            case OCTAHEDRON:
                showOctahedronConfigPanel();
                break;
            case CYLINDER:
                showCylinderConfigPanel();
                break;
            case CONE:
                showConeConfigPanel();
                break;
            case ELLIPSOID:
                showEllipsoidConfigPanel();
                break;
            case TETRAHEDRON:
                showTetrahedronConfigPanel();
                break;
            case TRIANGULAR_PRISM:
                showTriangularPrismConfigPanel();
                break;
            default:
                // 默认显示长方体配置面板
                showBoxConfigPanel();
                break;
        }
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
     * 显示正八面体配置面板
     */
    private void showOctahedronConfigPanel() {
        currentSubPanel = new OctahedronSubPanel(this, config);
        showSubPanel();
    }

    /**
     * 显示圆柱体配置面板
     */
    private void showCylinderConfigPanel() {
        currentSubPanel = new CylinderSubPanel(this, config);
        showSubPanel();
    }

    /**
     * 显示圆锥体配置面板
     */
    private void showConeConfigPanel() {
        currentSubPanel = new ConeSubPanel(this, config);
        showSubPanel();
    }

    /**
     * 显示椭球体配置面板
     */
    private void showEllipsoidConfigPanel() {
        currentSubPanel = new EllipsoidSubPanel(this, config);
        showSubPanel();
    }

    /**
     * 显示正四面体配置面板
     */
    private void showTetrahedronConfigPanel() {
        currentSubPanel = new TetrahedronSubPanel(this, config);
        showSubPanel();
    }

    /**
     * 显示三棱柱配置面板
     */
    private void showTriangularPrismConfigPanel() {
        currentSubPanel = new TriangularPrismSubPanel(this, config);
        showSubPanel();
    }

    /**
     * 显示子面板
     */
    private void showSubPanel() {
        if (currentSubPanel != null) {
            currentSubPanel.init();
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
        if (geometryButton != null) geometryButton.visible = false;
        if (geometryConfigButton != null) geometryConfigButton.visible = false;
        if (workModeButton != null) workModeButton.visible = false;
        if (workModeConfigButton != null) workModeConfigButton.visible = false;
        if (displayModeButton != null) displayModeButton.visible = false;
        if (distanceSlider != null) distanceSlider.visible = false;
        if (applyAndCloseButton != null) applyAndCloseButton.visible = false;
        if (heightConfigButton != null) heightConfigButton.visible = false;
        // 隐藏几何体选择面板
        if (geometrySelectionPanel != null) geometrySelectionPanel.hide();
        // 隐藏工作模式选择面板
        if (workModeSelectionPanel != null) workModeSelectionPanel.hide();
    }

    /**
     * 每帧调用的方法，用于检测按键
     */
    @Override
    public void tick() {
        super.tick();
        // 检查 K 键是否被按下，并且经过了延迟时间
        if (InputUtil.isKeyPressed(MinecraftClient.getInstance().getWindow(), GLFW.GLFW_KEY_K) &&
            System.currentTimeMillis() - openTime > CLOSE_DELAY) {
            this.close();
        }
    }


    /**
     * 处理按键事件
     */
    @Override
    public boolean keyPressed(KeyInput input) {
        int keyCode = input.key();
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            // 优先处理标高配置面板
            if (heightConfigPanel != null && heightConfigPanel.isVisible()) {
                heightConfigPanel.hide();
                showMainPanel();
                return true;
            }
            
            // 优先处理几何体选择面板
            if (geometrySelectionPanel != null && geometrySelectionPanel.isVisible()) {
                geometrySelectionPanel.hide(); // 取消选择，保持原来的几何体
                return true;
            }
            
            // 处理工作模式选择面板
            if (workModeSelectionPanel != null && workModeSelectionPanel.isVisible()) {
                workModeSelectionPanel.hide(); // 取消选择，保持原来的工作模式
                return true;
            }

            // 处理工作模式配置面板
            if (currentWorkModeConfigPanel != null && currentWorkModeConfigPanel.isVisible()) {
                // 相当于按下确定键：保存配置并关闭
                currentWorkModeConfigPanel.saveConfig();
                currentWorkModeConfigPanel.hide();
                showMainPanel();
                return true;
            }
            
            // 处理几何体配置面板
            if (currentSubPanel != null && currentSubPanel.isVisible()) {
                // 相当于按下确定键：保存配置并关闭
                currentSubPanel.saveConfig();
                hideSubPanel();
                showMainPanel();
                return true;
            }
        }

        // 处理上下箭头键调整最大操作距离
        if (keyCode == GLFW.GLFW_KEY_UP || keyCode == GLFW.GLFW_KEY_DOWN) {
            if (this.client != null && this.client.player != null) {
                int currentDistance = config.getMaxOperationDistance();
                int newDistance;
                
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

        return super.keyPressed(input);
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
        // 检查是否有任何子面板可见
        boolean geometryPanelVisible = geometrySelectionPanel != null && geometrySelectionPanel.isVisible();
        boolean workModePanelVisible = workModeSelectionPanel != null && workModeSelectionPanel.isVisible();
        boolean workModeConfigPanelVisible = currentWorkModeConfigPanel != null && currentWorkModeConfigPanel.isVisible();
        boolean configSubPanelVisible = currentSubPanel != null && currentSubPanel.isVisible();
        boolean heightConfigPanelVisible = heightConfigPanel != null && heightConfigPanel.isVisible();
        
        // 只有在所有子面板都不可见时才渲染主界面
        if (!geometryPanelVisible && !workModePanelVisible && !workModeConfigPanelVisible && !configSubPanelVisible && !heightConfigPanelVisible) {
            // 计算新板高度
            int panelHeight = applyAndCloseButton.getY() + applyAndCloseButton.getHeight() + 5 - panelTop; // 减少底部间距到5像素
            
            // 绘制半透明主面板背景
            context.fill(panelLeft + 20, panelTop, panelLeft + PANEL_WIDTH - 20, panelTop + panelHeight, 0x80000000);
            
            // 绘制半透明标题背景
            context.fill(panelLeft + 20, panelTop, panelLeft + PANEL_WIDTH - 20, panelTop + TITLE_HEIGHT, 0xE0303030);
            
            // 绘制边框
            drawBorder(context, panelLeft + 20, panelTop, panelHeight);
            
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

            // 渲染所有UI元素
            for (Element child : this.children()) {
                if (child instanceof Drawable) {
                    ((Drawable) child).render(context, mouseX, mouseY, delta);
                }
            }
            
            // 渲染下拉列表选项
            if (isWorkModeDropdownOpen) {
                renderWorkModeOptions(context, mouseX, mouseY, delta);
            }
        }

        // 只在子面板显示时渲染它
        if (currentSubPanel != null && currentSubPanel.isVisible()) {
            currentSubPanel.render(context, mouseX, mouseY, delta);
        }

        // 最后渲染几何体选择面板，确保它在最上层
        if (geometryPanelVisible) {
            geometrySelectionPanel.render(context, mouseX, mouseY, delta);
        }

        // 渲染工作模式选择面板
        if (workModePanelVisible) {
            workModeSelectionPanel.render(context, mouseX, mouseY, delta);
        }

        // 渲染工作模式配置面板
        if (workModeConfigPanelVisible) {
            currentWorkModeConfigPanel.render(context, mouseX, mouseY, delta);
        }

        // 渲染标高配置面板
        if (heightConfigPanelVisible) {
            heightConfigPanel.render(context, mouseX, mouseY, delta);
        }
    }

    private static void drawBorder(DrawContext context, int x, int y, int height) {
        // top
        context.fill(x, y, x + 240, y + 1, -1);
        // bottom
        context.fill(x, y + height - 1, x + 240, y + height, -1);
        // left
        context.fill(x, y, x + 1, y + height, -1);
        // right
        context.fill(x + 240 - 1, y, x + 240, y + height, -1);
    }

    private String getDisplayModeText() {
        // 确保显示模式不为null
        if (config.getDisplayMode() == null) {
            config.setDisplayMode(PushdozerConfig.DisplayMode.WIREFRAME);
        }
        return config.getDisplayMode().getDisplayText().getString();
    }

    private void toggleDisplayMode() {
        // 确保显示模式不为null
        if (config.getDisplayMode() == null) {
            config.setDisplayMode(PushdozerConfig.DisplayMode.WIREFRAME);
        }
        
        PushdozerConfig.DisplayMode[] modes = PushdozerConfig.DisplayMode.values();
        int nextIndex = (config.getDisplayMode().ordinal() + 1) % modes.length;
        PushdozerConfig.DisplayMode newMode = modes[nextIndex];
        config.setDisplayMode(newMode);
        displayModeButton.setMessage(getDisplayModeButtonText());
        PushdozerMod.saveConfig();
        showErrorMessage(Text.translatable("pushdozer.message.display_mode_switch", getDisplayModeText()).getString());
    }

    private void toggleWorkModeDropdown() {
        isWorkModeDropdownOpen = false;
        workModeOptions.forEach(this::remove);
        workModeOptions.clear();
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
     * 重写背景渲染方法，阻止默认的半透明模糊背景
     */
    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
        // 不调用super.renderBackground()，这样就不会绘制默认的半透明背景
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

    private void applyAndClose() {
        // 确保在关闭前保存所有配置
        config.save();
        this.close();
    }

    private Text getGeometryButtonText() {
        return Text.translatable("pushdozer.button.geometry_format", config.getGeometryType().getDisplayText());
    }

    private Text getWorkModeText() {
        return Text.translatable("pushdozer.button.work_mode_format", config.getWorkMode().getDisplayText());
    }

    private Text getDisplayModeButtonText() {
        // 确保显示模式不为null
        if (config.getDisplayMode() == null) {
            config.setDisplayMode(PushdozerConfig.DisplayMode.WIREFRAME);
        }
        return Text.translatable("pushdozer.button.display_mode_format", config.getDisplayMode().getDisplayText());
    }

    /**
     * 显示主面板
     */
    public void showMainPanel() {
        mainPanelVisible = true;
        // 显示主界面上的所有组件
        if (geometryButton != null) geometryButton.visible = true;
        if (geometryConfigButton != null) geometryConfigButton.visible = true;
        if (workModeButton != null) workModeButton.visible = true;
        if (workModeConfigButton != null) workModeConfigButton.visible = true;
        if (displayModeButton != null) displayModeButton.visible = true;
        if (distanceSlider != null) distanceSlider.visible = true;
        if (applyAndCloseButton != null) applyAndCloseButton.visible = true;
        if (heightConfigButton != null) heightConfigButton.visible = true;

        // 隐藏子面板
        hideSubPanel();
        // 隐藏几何体选择面板
        if (geometrySelectionPanel != null) geometrySelectionPanel.hide();
        // 隐藏工作模式选择面板
        if (workModeSelectionPanel != null) workModeSelectionPanel.hide();
        // 隐藏工作模式配置面板
        if (currentWorkModeConfigPanel != null) currentWorkModeConfigPanel.hide();
        // 隐藏标高配置面板
        if (heightConfigPanel != null) heightConfigPanel.hide();
    }

    /**
     * 显示标高配置面板
     */
    private void showHeightConfigPanel() {
        if (heightConfigPanel == null) {
            heightConfigPanel = new HeightConfigPanel(this, config);
        }
        heightConfigPanel.show();
        hideMainPanel();
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
    public boolean mouseClicked(Click click, boolean doubleClick) {
        double mouseX = click.x();
        double mouseY = click.y();
        int button = click.button();
        // 如果标高配置面板打开，优先处理它的事件
        if (heightConfigPanel != null && heightConfigPanel.isVisible()) {
            if (heightConfigPanel.mouseClicked(mouseX, mouseY, button)) {
                return true;
            }
        }

        // 如果几何体选择面板打开，优先处理它的事件
        if (geometrySelectionPanel != null && geometrySelectionPanel.isVisible()) {
            if (geometrySelectionPanel.mouseClicked(mouseX, mouseY, button)) {
                return true;
            }
        }

        // 如果工作模式配置面板打开，优先处理它的事件
        if (currentWorkModeConfigPanel != null && currentWorkModeConfigPanel.isVisible()) {
            if (currentWorkModeConfigPanel.mouseClicked(mouseX, mouseY, button)) {
                return true;
            }
        }

        // 如果工作模式选择面板打开，优先处理它的事件
        if (workModeSelectionPanel != null && workModeSelectionPanel.isVisible()) {
            if (workModeSelectionPanel.mouseClicked(mouseX, mouseY, button)) {
                return true;
            }
        }

        // 处理工作模式下拉列表
        if (isWorkModeDropdownOpen) {
            for (ButtonWidget option : workModeOptions) {
                if (option.isMouseOver(mouseX, mouseY)) {
                    option.onPress(click);
                    return true;
                }
            }
            toggleWorkModeDropdown();
        }
        
        // 处理子面板事件
        if (currentSubPanel != null && currentSubPanel.isVisible()) {
            for (Element widget : currentSubPanel.getWidgets()) {
                if (widget instanceof ClickableWidget clickable) {
                    if (clickable.isMouseOver(mouseX, mouseY)) {
                        return clickable.mouseClicked(click, doubleClick);
                    }
                }
            }
        }
        
        return super.mouseClicked(click, doubleClick);
    }

    @Override
    public boolean mouseDragged(Click click, double deltaX, double deltaY) {
        double mouseX = click.x();
        double mouseY = click.y();
        int button = click.button();
        // 处理标高配置面板事件
        if (heightConfigPanel != null && heightConfigPanel.isVisible()) {
            if (heightConfigPanel.mouseDragged(mouseX, mouseY, button, deltaX, deltaY)) {
                return true;
            }
        }

        // 处理几何体选择面板事件
        if (geometrySelectionPanel != null && geometrySelectionPanel.isVisible()) {
            if (geometrySelectionPanel.mouseDragged(mouseX, mouseY, button, deltaX, deltaY)) {
                return true;
            }
        }

        // 处理工作模式配置面板事件
        if (currentWorkModeConfigPanel != null && currentWorkModeConfigPanel.isVisible()) {
            if (currentWorkModeConfigPanel.mouseDragged(mouseX, mouseY, button, deltaX, deltaY)) {
                return true;
            }
        }

        // 处理工作模式选择面板事件
        if (workModeSelectionPanel != null && workModeSelectionPanel.isVisible()) {
            if (workModeSelectionPanel.mouseDragged(mouseX, mouseY, button, deltaX, deltaY)) {
                return true;
            }
        }
        
        if (currentSubPanel != null && currentSubPanel.isVisible()) {
            for (Element widget : currentSubPanel.getWidgets()) {
                if (widget instanceof ClickableWidget clickable) {
                    if (clickable.isMouseOver(mouseX, mouseY)) {
                        return clickable.mouseDragged(click, deltaX, deltaY);
                    }
                }
            }
        }
        return super.mouseDragged(click, deltaX, deltaY);
    }

    @Override
    public boolean mouseReleased(Click click) {
        double mouseX = click.x();
        double mouseY = click.y();
        int button = click.button();
        // 处理标高配置面板事件
        if (heightConfigPanel != null && heightConfigPanel.isVisible()) {
            if (heightConfigPanel.mouseReleased(mouseX, mouseY, button)) {
                return true;
            }
        }

        // 处理几何体选择面板事件
        if (geometrySelectionPanel != null && geometrySelectionPanel.isVisible()) {
            if (geometrySelectionPanel.mouseReleased(mouseX, mouseY, button)) {
                return true;
            }
        }

        // 处理工作模式配置面板事件
        if (currentWorkModeConfigPanel != null && currentWorkModeConfigPanel.isVisible()) {
            if (currentWorkModeConfigPanel.mouseReleased(mouseX, mouseY, button)) {
                return true;
            }
        }

        // 处理工作模式选择面板事件
        if (workModeSelectionPanel != null && workModeSelectionPanel.isVisible()) {
            if (workModeSelectionPanel.mouseReleased(mouseX, mouseY, button)) {
                return true;
            }
        }
        
        if (currentSubPanel != null && currentSubPanel.isVisible()) {
            for (Element widget : currentSubPanel.getWidgets()) {
                if (widget instanceof ClickableWidget clickable) {
                    if (clickable.isMouseOver(mouseX, mouseY)) {
                        return clickable.mouseReleased(click);
                    }
                }
            }
        }
        return super.mouseReleased(click);
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