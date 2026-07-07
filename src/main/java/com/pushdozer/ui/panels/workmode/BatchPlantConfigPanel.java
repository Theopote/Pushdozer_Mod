package com.pushdozer.ui.panels.workmode;

import com.pushdozer.config.PushdozerConfig;
import com.pushdozer.ui.screens.PushdozerConfigScreen;
import com.pushdozer.ui.screens.MultiSelectPlantSelectionScreen;
import net.minecraft.client.gui.widget.CyclingButtonWidget;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.text.Text;
import net.minecraft.block.Block;

import java.util.function.Consumer;
import java.util.List;

/**
 * Configuration panel for batch planting settings
 */
public class BatchPlantConfigPanel extends WorkModeConfigPanel {

    public BatchPlantConfigPanel(PushdozerConfigScreen parent, PushdozerConfig config) {
        super(parent, config);
    }

    @Override
    protected void initializeWidgets() {
        widgets.clear();
        int currentY = panelTop + TITLE_HEIGHT + WIDGET_MARGIN;

        // Plant Type Selection
        addPlantTypeButton(currentY);
        currentY += WIDGET_HEIGHT + WIDGET_MARGIN;

        // Type-Specific Options
        currentY = addTypeSpecificOptions(currentY);

        // Common Parameters
        currentY = addCommonParameters(currentY);

        // 注意：确认按钮会在基类的initializeConfirmButton()中添加
    }

    private void addPlantTypeButton(int yPos) {
        CyclingButtonWidget<PushdozerConfig.PlantType> plantTypeButton = CyclingButtonWidget
                .builder(
                        PushdozerConfig.PlantType::getDisplayText,
                        config.getPlantType()
                )
                .values(PushdozerConfig.PlantType.values())
                .build(
                        panelLeft + WIDGET_MARGIN,
                        yPos,
                        PANEL_WIDTH - (WIDGET_MARGIN * 2),
                        WIDGET_HEIGHT,
                        Text.translatable("pushdozer.config.plant_type"),
                        (button, type) -> {
                            // 防抖逻辑：仅在值实际变化时重渲染
                            if (config.getPlantType() != type) {
                                config.setPlantType(type);
                                this.show();
                            }
                        }
                );
        widgets.add(plantTypeButton);
    }

    private int addTypeSpecificOptions(int yPos) {
        // 防御性检查，确保plantType不为null
        PushdozerConfig.PlantType plantType = config.getPlantType();
        if (plantType == null) {
            plantType = PushdozerConfig.PlantType.TREES;
            config.setPlantType(plantType);
        }
        
        switch (plantType) {
            case TREES -> {
                CyclingButtonWidget<PushdozerConfig.TreeSpecies> treeSpeciesButton = CyclingButtonWidget
                        .builder(
                                PushdozerConfig.TreeSpecies::getDisplayText,
                                config.getSelectedTree()
                        )
                        .values(PushdozerConfig.TreeSpecies.values())
                        .build(
                                panelLeft + WIDGET_MARGIN,
                                yPos,
                                PANEL_WIDTH - (WIDGET_MARGIN * 2),
                                WIDGET_HEIGHT,
                                Text.translatable("pushdozer.config.tree_species"),
                                (btn, species) -> config.setSelectedTree(species)
                        );
                widgets.add(treeSpeciesButton);
                yPos += WIDGET_HEIGHT + WIDGET_MARGIN;
            }
            case FLOWERS -> {
                CyclingButtonWidget<PushdozerConfig.FlowerGroup> flowerGroupButton = CyclingButtonWidget
                        .builder(
                                PushdozerConfig.FlowerGroup::getDisplayText,
                                config.getSelectedFlowerGroup()
                        )
                        .values(PushdozerConfig.FlowerGroup.values())
                        .build(
                                panelLeft + WIDGET_MARGIN,
                                yPos,
                                PANEL_WIDTH - (WIDGET_MARGIN * 2),
                                WIDGET_HEIGHT,
                                Text.translatable("pushdozer.config.flower_group"),
                                (btn, group) -> config.setSelectedFlowerGroup(group)
                        );
                widgets.add(flowerGroupButton);
                yPos += WIDGET_HEIGHT + WIDGET_MARGIN;
            }
            case GRASS -> {
                // 草类型目前没有特殊配置选项
            }
            case CUSTOM -> {
                ButtonWidget selectPlantsButton = ButtonWidget.builder(
                        Text.translatable("pushdozer.config.select_plant_types"),
                        button -> openPlantSelectionScreen()
                ).dimensions(
                        panelLeft + WIDGET_MARGIN,
                        yPos,
                        PANEL_WIDTH - (WIDGET_MARGIN * 2),
                        WIDGET_HEIGHT
                ).build();
                selectPlantsButton.setTooltip(Tooltip.of(Text.translatable("pushdozer.tooltip.select_plant_types")));
                widgets.add(selectPlantsButton);
                yPos += WIDGET_HEIGHT + WIDGET_MARGIN;
            }
        }
        return yPos;
    }
    
    private void openPlantSelectionScreen() {
        List<Block> currentlySelected = config.getCustomPlantBlocks();
        MultiSelectPlantSelectionScreen plantScreen = new MultiSelectPlantSelectionScreen(
            parent,
            this::onPlantsSelected,
            currentlySelected
        );
        if (parent.getClient() != null) {
            parent.getClient().setScreen(plantScreen);
        }
    }
    
    private void onPlantsSelected(List<Block> selectedBlocks) {
        config.setCustomPlantBlocks(selectedBlocks);
        // 重新显示面板以更新UI
        this.show();
    }

    private int addCommonParameters(int yPos) {
        // Plant Density Slider
        CustomSlider densitySlider = new CustomSlider(
                panelLeft + WIDGET_MARGIN,
                yPos,
                PANEL_WIDTH - (WIDGET_MARGIN * 2),
                WIDGET_HEIGHT,
                "pushdozer.config.plant_density",
                config.getPlantDensity(),
                config::setPlantDensity
        );
        densitySlider.setTooltip(Tooltip.of(Text.translatable("pushdozer.config.plant_density.tooltip")));
        widgets.add(densitySlider);
        yPos += WIDGET_HEIGHT + WIDGET_MARGIN;

        // Cluster Scale Slider
        CustomSlider clusterSlider = new CustomSlider(
                panelLeft + WIDGET_MARGIN,
                yPos,
                PANEL_WIDTH - (WIDGET_MARGIN * 2),
                WIDGET_HEIGHT,
                "pushdozer.config.cluster_scale",
                config.getClusterScale(),
                config::setClusterScale
        );
        clusterSlider.setTooltip(Tooltip.of(Text.translatable("pushdozer.config.cluster_scale.tooltip")));
        widgets.add(clusterSlider);
        yPos += WIDGET_HEIGHT + WIDGET_MARGIN;


        return yPos;
    }

    @Override
    protected Text getTitleText() {
        return Text.translatable("pushdozer.panel.batch_plant.title");
    }

    @Override
    public void saveConfig() {
        // Configuration is saved automatically through widget callbacks
    }
    
    /**
     * Custom slider widget for float-based configuration values
     */
    private static class CustomSlider extends SliderWidget {
        private final Consumer<Float> onValueChanged;
        private final String key;
        
        public CustomSlider(int x, int y, int width, int height, String key, float value, Consumer<Float> onValueChanged) {
            super(x, y, width, height, Text.translatable(key), value);
            this.onValueChanged = onValueChanged;
            this.key = key;
            updateMessage();
        }
        
        @Override
        protected void updateMessage() {
            // 直接显示百分比，不添加额外的百分号
            setMessage(Text.translatable(key + ".value", String.format("%d", (int)(this.value * 100))));
        }
        
        @Override
        protected void applyValue() {
            onValueChanged.accept((float) value);
        }
    }
}