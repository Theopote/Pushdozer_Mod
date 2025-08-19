package com.pushdozer.ui.panels.workmode;

import com.pushdozer.config.PushdozerConfig;
import com.pushdozer.ui.screens.PushdozerConfigScreen;
import com.pushdozer.ui.screens.EntityBlockSelectionScreen;
import com.pushdozer.ui.screens.MultiSelectPlantSelectionScreen;
import net.minecraft.client.gui.widget.CyclingButtonWidget;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.client.gui.widget.CheckboxWidget;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.text.Text;
import net.minecraft.block.Block;
import net.minecraft.registry.Registries;

import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import net.minecraft.client.gui.Element;

/**
 * 水岸处理配置面板
 */
public class ShorelineProcessConfigPanel extends WorkModeConfigPanel {

    // 标高限制复选框
    private CheckboxWidget heightAboveCheckbox;
    private CheckboxWidget heightBelowCheckbox;

    public ShorelineProcessConfigPanel(PushdozerConfigScreen parent, PushdozerConfig config) {
        super(parent, config);
    }

    @Override
    protected void initializeWidgets() {
        // 清空旧的控件
        widgets.clear();
        
        // 参考表层处理配置面板的布局逻辑
        // 注意：这里不使用panelTop，因为它在show()方法中还没有正确计算
        // 控件位置会在recalculateContentWidgetPositions中正确设置
        int contentLeft = WIDGET_MARGIN; // 临时位置，会在recalculateContentWidgetPositions中调整
        int contentTop = TITLE_HEIGHT + WIDGET_MARGIN; // 临时位置，会在recalculateContentWidgetPositions中调整
        int contentWidth = PANEL_WIDTH - (WIDGET_MARGIN * 2);

        // 水岸类型选择按钮
        CyclingButtonWidget<PushdozerConfig.ShorelineType> shorelineTypeButton = CyclingButtonWidget.builder(PushdozerConfig.ShorelineType::getDisplayText)
                .values(PushdozerConfig.ShorelineType.values())
                .initially(config.getShorelineType())
                .build(contentLeft, contentTop, contentWidth, WIDGET_HEIGHT,
                        Text.translatable("pushdozer.config.shoreline_type"),
                        (button, shorelineType) -> {
                            config.setShorelineType(shorelineType);
                            // 重新初始化面板以更新按钮显示
                            this.show();
                        });
        shorelineTypeButton.setTooltip(Tooltip.of(Text.translatable("pushdozer.config.shoreline_type.tooltip")));
        widgets.add(shorelineTypeButton);
        contentTop += WIDGET_HEIGHT + WIDGET_MARGIN;

        // 水体检测范围滑动条
        CustomWidthSlider widthSlider = new CustomWidthSlider(
                contentLeft, contentTop, contentWidth, WIDGET_HEIGHT,
                config.getShorelineWidth(),
                config::setShorelineWidth
        );
        // 添加Tooltip，解释该选项的作用
        widthSlider.setTooltip(Tooltip.of(Text.translatable("pushdozer.config.shoreline_width.tooltip")));
        widgets.add(widthSlider);
        contentTop += WIDGET_HEIGHT + WIDGET_MARGIN;

        // 植物种植开关或自定义选择按钮
        if (config.getShorelineType() == PushdozerConfig.ShorelineType.CUSTOM) {
            // 自定义水岸类型：显示选择方块和选择植物按钮
            int halfWidth = (contentWidth - WIDGET_MARGIN) / 2;
            
            // 选择方块按钮
            ButtonWidget selectBlocksButton = ButtonWidget.builder(
                    Text.translatable("pushdozer.config.custom_shoreline.select_blocks"),
                    button -> openBlockSelectionScreen()
            )
                    .dimensions(contentLeft, contentTop, halfWidth, WIDGET_HEIGHT)
                    .build();
            selectBlocksButton.setTooltip(Tooltip.of(Text.translatable("pushdozer.config.custom_shoreline.select_blocks.tooltip")));
            widgets.add(selectBlocksButton);
            
            // 选择植物按钮 - 确保与选择方块按钮在同一行
            ButtonWidget selectPlantsButton = ButtonWidget.builder(
                    Text.translatable("pushdozer.config.custom_shoreline.select_plants"),
                    button -> openPlantSelectionScreen()
            )
                    .dimensions(contentLeft + halfWidth + WIDGET_MARGIN, contentTop, halfWidth, WIDGET_HEIGHT)
                    .build();
            selectPlantsButton.setTooltip(Tooltip.of(Text.translatable("pushdozer.config.custom_shoreline.select_plants.tooltip")));
            widgets.add(selectPlantsButton);
            contentTop += WIDGET_HEIGHT + WIDGET_MARGIN;
        } else {
            // 非自定义水岸类型：显示植物种植开关
            CyclingButtonWidget<Boolean> plantVegetationButton = CyclingButtonWidget.onOffBuilder()
                    .initially(config.isPlantVegetationEnabled())
                    .build(contentLeft, contentTop, contentWidth, WIDGET_HEIGHT,
                            Text.translatable("pushdozer.config.plant_vegetation"),
                            (button, enabled) -> {
                                config.setPlantVegetationEnabled(enabled);
                                // 重新初始化控件以更新植物密度滑块
                                this.show();
                            });
            plantVegetationButton.setTooltip(Tooltip.of(Text.translatable("pushdozer.config.plant_vegetation.tooltip")));
            widgets.add(plantVegetationButton);
            contentTop += WIDGET_HEIGHT + WIDGET_MARGIN;
        }

        // 植物密度滑动条（在自定义水岸类型时始终显示，其他类型时仅在启用植物种植时显示）
        if (config.getShorelineType() == PushdozerConfig.ShorelineType.CUSTOM || config.isPlantVegetationEnabled()) {
            CustomVegetationDensitySlider densitySlider = new CustomVegetationDensitySlider(
                    contentLeft, contentTop, contentWidth, WIDGET_HEIGHT,
                    config.getVegetationDensity(),
                    config::setVegetationDensity
            );
            densitySlider.setTooltip(Tooltip.of(Text.translatable("pushdozer.config.vegetation_density.tooltip")));
            widgets.add(densitySlider);
            contentTop += WIDGET_HEIGHT + WIDGET_MARGIN;
        }

        // 标高限制选项（倒数第二行，确定按钮的上面一行）
        int halfWidthForChecks = (contentWidth - WIDGET_MARGIN) / 2;

        // 标高上操作复选框（左侧）
        heightAboveCheckbox = CheckboxWidget.builder(
                Text.translatable("pushdozer.config.shoreline_height_above"),
                parent.getTextRenderer()
        )
                .pos(contentLeft, contentTop)
                .checked(config.isShorelineHeightAboveEnabled())
                .callback((widget, checked) -> {
                    if (checked) {
                        // 如果启用标高上操作，则禁用标高下操作
                        config.setShorelineHeightBelowEnabled(false);
                    }
                    config.setShorelineHeightAboveEnabled(checked);
                    // 重新初始化面板以更新复选框状态
                    this.show();
                })
                .build();
        heightAboveCheckbox.setTooltip(Tooltip.of(Text.translatable("pushdozer.config.shoreline_height_above.tooltip")));
        widgets.add(heightAboveCheckbox);

        // 标高下操作复选框（右侧，与上一个在同一行）
        heightBelowCheckbox = CheckboxWidget.builder(
                Text.translatable("pushdozer.config.shoreline_height_below"),
                parent.getTextRenderer()
        )
                .pos(contentLeft + halfWidthForChecks + WIDGET_MARGIN, contentTop)
                .checked(config.isShorelineHeightBelowEnabled())
                .callback((widget, checked) -> {
                    if (checked) {
                        // 如果启用标高下操作，则禁用标高上操作
                        config.setShorelineHeightAboveEnabled(false);
                    }
                    config.setShorelineHeightBelowEnabled(checked);
                    // 重新初始化面板以更新复选框状态
                    this.show();
                })
                .build();
        heightBelowCheckbox.setTooltip(Tooltip.of(Text.translatable("pushdozer.config.shoreline_height_below.tooltip")));
        widgets.add(heightBelowCheckbox);
        
        // 注意：确认按钮会在基类的initializeConfirmButton()中添加，距离最后一个控件5像素
    }

    /**
     * 重写父类的方法，防止父类重新计算控件位置时破坏我们的自定义布局
     */
    @Override
    protected void recalculateContentWidgetPositions() {
        // 重新计算所有控件的位置，保持我们的自定义布局
        int contentLeft = panelLeft + WIDGET_MARGIN;
        int contentTop = panelTop + TITLE_HEIGHT + WIDGET_MARGIN; // 距离标题5像素
        int contentWidth = PANEL_WIDTH - (WIDGET_MARGIN * 2);
        
        // 用于跟踪自定义水岸类型的按钮
        boolean foundFirstButton = false;
        
        // 重新计算每个控件的位置
        for (Element widget : widgets) {
            if (widget != confirmButton && widget instanceof net.minecraft.client.gui.widget.ClickableWidget clickableWidget) {
                // 根据控件类型设置位置
                if (widget instanceof CyclingButtonWidget) {
                    // 水岸类型选择按钮或植物种植开关
                    clickableWidget.setPosition(contentLeft, contentTop);
                    contentTop += WIDGET_HEIGHT + WIDGET_MARGIN;
                } else if (widget instanceof CustomWidthSlider || widget instanceof CustomVegetationDensitySlider) {
                    // 滑动条
                    clickableWidget.setPosition(contentLeft, contentTop);
                    contentTop += WIDGET_HEIGHT + WIDGET_MARGIN;
                } else if (widget instanceof ButtonWidget) {
                    // 按钮（选择方块、选择植物）
                    int halfWidth = (contentWidth - WIDGET_MARGIN) / 2;
                    // 检查是否是自定义水岸类型的两个按钮
                    if (config.getShorelineType() == PushdozerConfig.ShorelineType.CUSTOM) {
                        // 自定义水岸类型：两个按钮在同一行
                        if (!foundFirstButton) {
                            // 这是选择方块按钮（左侧）
                            clickableWidget.setPosition(contentLeft, contentTop);
                            foundFirstButton = true;
                        } else {
                            // 这是选择植物按钮（右侧）
                            clickableWidget.setPosition(contentLeft + halfWidth + WIDGET_MARGIN, contentTop);
                            contentTop += WIDGET_HEIGHT + WIDGET_MARGIN;
                            foundFirstButton = false; // 重置，为下一组按钮做准备
                        }
                    } else {
                        // 其他按钮按默认方式排列
                        clickableWidget.setPosition(contentLeft, contentTop);
                        contentTop += WIDGET_HEIGHT + WIDGET_MARGIN;
                    }
                } else if (widget instanceof CheckboxWidget) {
                    // 复选框（标高限制选项）
                    int halfWidthForChecks = (contentWidth - WIDGET_MARGIN) / 2;
                    if (widget == heightAboveCheckbox) {
                        clickableWidget.setPosition(contentLeft, contentTop);
                    } else {
                        clickableWidget.setPosition(contentLeft + halfWidthForChecks + WIDGET_MARGIN, contentTop);
                        contentTop += WIDGET_HEIGHT + WIDGET_MARGIN;
                    }
                } else {
                    // 其他控件按默认方式排列
                    clickableWidget.setPosition(contentLeft, contentTop);
                    contentTop += WIDGET_HEIGHT + WIDGET_MARGIN;
                }
            }
        }
    }

    /**
     * 重写父类的方法，正确计算确认按钮位置，确保距离最后一个控件5像素
     */
    @Override
    protected int calculateConfirmButtonY() {
        // 根据当前配置计算实际的控件布局
        int contentTop = panelTop + TITLE_HEIGHT + WIDGET_MARGIN;
        
        // 水岸类型选择按钮
        contentTop += WIDGET_HEIGHT + WIDGET_MARGIN;
        
        // 水体检测范围滑动条
        contentTop += WIDGET_HEIGHT + WIDGET_MARGIN;
        
        // 植物种植开关或自定义选择按钮
        if (config.getShorelineType() == PushdozerConfig.ShorelineType.CUSTOM) {
            // 自定义水岸类型：两个按钮在同一行
            contentTop += WIDGET_HEIGHT + WIDGET_MARGIN;
        } else {
            // 非自定义水岸类型：植物种植开关
            contentTop += WIDGET_HEIGHT + WIDGET_MARGIN;
        }
        
        // 植物密度滑动条（在自定义水岸类型时始终显示，其他类型时仅在启用植物种植时显示）
        if (config.getShorelineType() == PushdozerConfig.ShorelineType.CUSTOM || config.isPlantVegetationEnabled()) {
            contentTop += WIDGET_HEIGHT + WIDGET_MARGIN;
        }
        
        // 标高限制选项（两个复选框在同一行）
        contentTop += WIDGET_HEIGHT + WIDGET_MARGIN;
        
        // 确认按钮位置：最后一个控件底部下方5像素
        return contentTop ;
    }

    @Override
    protected Text getTitleText() {
        return Text.translatable("pushdozer.panel.shoreline_process.title");
    }



    @Override
    public void saveConfig() {
        // 确保配置被保存到文件
        config.save();
    }

    /**
     * 自定义宽度滑动条
     */
    private static class CustomWidthSlider extends SliderWidget {
        private final Consumer<Integer> onValueChanged;

        public CustomWidthSlider(int x, int y, int width, int height, int value, Consumer<Integer> onValueChanged) {
            super(x, y, width, height, Text.translatable("pushdozer.config.shoreline_width"), (double) value / 10.0);
            this.onValueChanged = onValueChanged;
        }

        @Override
        protected void updateMessage() {
            setMessage(Text.translatable("pushdozer.config.shoreline_width.value",
                String.format("%.1f", value * 10)));
        }

        @Override
        protected void applyValue() {
            onValueChanged.accept((int) (value * 10));
        }
    }

    /**
     * 自定义植物密度滑动条
     */
    private static class CustomVegetationDensitySlider extends SliderWidget {
        private final Consumer<Float> onValueChanged;

        public CustomVegetationDensitySlider(int x, int y, int width, int height, float value, Consumer<Float> onValueChanged) {
            super(x, y, width, height, Text.translatable("pushdozer.config.vegetation_density"), value);
            this.onValueChanged = onValueChanged;
        }

        @Override
        protected void updateMessage() {
            setMessage(Text.translatable("pushdozer.config.vegetation_density.value",
                String.format("%.1f", value * 100)));
        }

        @Override
        protected void applyValue() {
            onValueChanged.accept((float) value);
        }
    }

    /**
     * 打开方块选择界面
     */
    private void openBlockSelectionScreen() {
        // 使用实体方块选择界面，传入自定义初始选中和确认回调
        List<Block> initial = config.getCustomShorelineBlockList();
        EntityBlockSelectionScreen screen = new EntityBlockSelectionScreen(
            parent,
            config,
            initial,
            selectedSet -> {
                Set<String> ids = selectedSet.stream()
                    .map(b -> Registries.BLOCK.getId(b).toString())
                    .collect(java.util.stream.Collectors.toSet());
                config.setCustomShorelineBlocks(ids);
                config.save();
            }
        );
        if (parent.getClient() != null) {
            parent.getClient().setScreen(screen);
        }
    }

    /**
     * 打开植物选择界面
     */
    private void openPlantSelectionScreen() {
        // 创建MultiSelectPlantSelectionScreen，传入当前选中的自定义植物
        List<Block> currentSelectedPlants = config.getCustomShorelinePlantList();
        MultiSelectPlantSelectionScreen plantSelectionScreen = new MultiSelectPlantSelectionScreen(
            parent, 
            selectedPlants -> {
                // 将选中的植物ID保存到配置中
                Set<String> plantIds = selectedPlants.stream()
                    .map(plant -> Registries.BLOCK.getId(plant).toString())
                    .collect(java.util.stream.Collectors.toSet());
                config.setCustomShorelinePlants(plantIds);
                config.save();
            },
            currentSelectedPlants
        );
        
        // 打开植物选择界面
        if (parent.getClient() != null) {
            parent.getClient().setScreen(plantSelectionScreen);
        }
    }


} 