package com.pushdozer.ui.panels.workmode;

import com.pushdozer.config.PushdozerConfig;
import com.pushdozer.ui.screens.NaturalBlockSelectionScreen;
import com.pushdozer.ui.screens.PushdozerConfigScreen;
import net.minecraft.block.Block;
import net.minecraft.registry.Registries;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

/**
 * 铺设模式配置面板
 * 包含自适应生物群系、石头和自然方块选择选项
 */
public class PlaceConfigPanel extends WorkModeConfigPanel {
    
    private ButtonWidget adaptiveBiomeButton;
    private ButtonWidget naturalBlockButton;

    public PlaceConfigPanel(PushdozerConfigScreen parent, PushdozerConfig config) {
        super(parent, config);
    }

    @Override
    protected Text getTitleText() {
        return Text.translatable("pushdozer.mode.place");
    }

    @Override
    protected void initializeWidgets() {
        widgets.clear();

        int contentLeft = panelLeft + WIDGET_MARGIN;
        int contentTop = panelTop + TITLE_HEIGHT + WIDGET_MARGIN;
        int contentWidth = PANEL_WIDTH - (WIDGET_MARGIN * 2);

        // 自适应生物群系单选按钮
        adaptiveBiomeButton = ButtonWidget.builder(
            getButtonText(),
            button -> selectPlaceMode())
            .dimensions(contentLeft, contentTop, contentWidth, WIDGET_HEIGHT)
            .tooltip(Tooltip.of(Text.translatable("pushdozer.tooltip.place_mode")))
            .build();
        widgets.add(adaptiveBiomeButton);

        // 自然方块选择按钮
        naturalBlockButton = ButtonWidget.builder(
            getNaturalBlockButtonText(),
            button -> openNaturalBlockSelection())
            .dimensions(contentLeft, contentTop + WIDGET_HEIGHT + WIDGET_MARGIN, contentWidth, WIDGET_HEIGHT)
            .tooltip(Tooltip.of(Text.translatable("pushdozer.tooltip.natural_block_selection")))
            .build();
        widgets.add(naturalBlockButton);

        updateButtonStates();
        
        // 注意：确认按钮会在基类的initializeConfirmButton()中添加
    }

    /**
     * 选择铺设模式
     */
    private void selectPlaceMode() {
        config.setPlaceMode(PushdozerConfig.PlaceMode.ADAPTIVE_BIOME);
        updateButtonStates();
    }

    /**
     * 打开自然方块选择屏幕
     */
    private void openNaturalBlockSelection() {
        // 先设置为自然方块模式
        config.setPlaceMode(PushdozerConfig.PlaceMode.NATURAL_BLOCK);
        
        // 打开自然方块选择屏幕
        NaturalBlockSelectionScreen selectionScreen = new NaturalBlockSelectionScreen(
            parent, 
            this::onNaturalBlockSelected
        );
        
        if (parent.getClient() != null) {
            parent.getClient().setScreen(selectionScreen);
        }
    }

    /**
     * 自然方块选择回调
     */
    private void onNaturalBlockSelected(Block selectedBlock) {
        // 使用注册表获取方块的唯一标识符
        String blockId = Registries.BLOCK.getId(selectedBlock).toString();
        config.setSelectedNaturalBlockId(blockId);
        updateButtonStates();
    }

    /**
     * 获取按钮文本（带选中标记）
     */
    private Text getButtonText() {
        String prefix = (config.getPlaceMode() == PushdozerConfig.PlaceMode.ADAPTIVE_BIOME) ? "☑ " : "";
        return Text.literal(prefix).append(PushdozerConfig.PlaceMode.ADAPTIVE_BIOME.getDisplayText());
    }

    /**
     * 获取自然方块按钮文本
     */
    private Text getNaturalBlockButtonText() {
        String prefix = (config.getPlaceMode() == PushdozerConfig.PlaceMode.NATURAL_BLOCK) ? "☑ " : "";
        String buttonText = Text.translatable("pushdozer.place_mode.natural_block").getString();
        
        // 如果当前选中的是自然方块模式，显示选中的方块名称
        if (config.getPlaceMode() == PushdozerConfig.PlaceMode.NATURAL_BLOCK) {
            Block selectedBlock = config.getSelectedNaturalBlock();
            String blockName = Text.translatable(selectedBlock.getTranslationKey()).getString();
            buttonText += ": " + blockName;
        }
        
        return Text.literal(prefix + buttonText);
    }

    /**
     * 更新按钮状态
     */
    private void updateButtonStates() {
        if (adaptiveBiomeButton != null) {
            adaptiveBiomeButton.setMessage(getButtonText());
        }
        if (naturalBlockButton != null) {
            naturalBlockButton.setMessage(getNaturalBlockButtonText());
        }
    }

    @Override
    protected void renderWidgets(DrawContext context, int mouseX, int mouseY, float delta) {
        // 先给选中的按钮添加高亮背景
        PushdozerConfig.PlaceMode currentMode = config.getPlaceMode();
        
        if (currentMode == PushdozerConfig.PlaceMode.ADAPTIVE_BIOME && adaptiveBiomeButton != null) {
            context.fill(
                adaptiveBiomeButton.getX(), 
                adaptiveBiomeButton.getY(),
                adaptiveBiomeButton.getX() + adaptiveBiomeButton.getWidth(),
                adaptiveBiomeButton.getY() + adaptiveBiomeButton.getHeight(),
                0xFF888888 // 边框颜色
            );
            context.fill(
                adaptiveBiomeButton.getX()+1, 
                adaptiveBiomeButton.getY()+1,
                adaptiveBiomeButton.getX() + adaptiveBiomeButton.getWidth()-1,
                adaptiveBiomeButton.getY() + adaptiveBiomeButton.getHeight()-1,
                0xFF555555 // 背景颜色
            );
        }
        
        if (currentMode == PushdozerConfig.PlaceMode.NATURAL_BLOCK && naturalBlockButton != null) {
            context.fill(
                naturalBlockButton.getX(), 
                naturalBlockButton.getY(),
                naturalBlockButton.getX() + naturalBlockButton.getWidth(),
                naturalBlockButton.getY() + naturalBlockButton.getHeight(),
                0xFF888888 // 边框颜色
            );
            context.fill(
                naturalBlockButton.getX()+1, 
                naturalBlockButton.getY()+1,
                naturalBlockButton.getX() + naturalBlockButton.getWidth()-1,
                naturalBlockButton.getY() + naturalBlockButton.getHeight()-1,
                0xFF555555 // 背景颜色
            );
        }

        // 然后再渲染所有组件，让它们画在背景之上
        super.renderWidgets(context, mouseX, mouseY, delta);
    }

    @Override
    public void saveConfig() {
        try {
            // 保存配置
            config.save();
            
            // 显示保存成功消息
            parent.showErrorMessage(Text.translatable("pushdozer.config.saved").getString());
        } catch (Exception e) {
            // 向用户显示错误信息
            parent.showErrorMessage(Text.translatable("pushdozer.config.save_failed").getString());
            e.printStackTrace();
        }
    }
} 