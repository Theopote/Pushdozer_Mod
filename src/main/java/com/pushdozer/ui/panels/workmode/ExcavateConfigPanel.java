package com.pushdozer.ui.panels.workmode;

import com.pushdozer.config.PushdozerConfig;
import com.pushdozer.ui.screens.BlockSelectionScreen;
import com.pushdozer.ui.screens.PushdozerConfigScreen;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

/**
 * 挖掘模式配置面板
 * 包含选择允许破坏的方块功能
 */
public class ExcavateConfigPanel extends WorkModeConfigPanel {

    public ExcavateConfigPanel(PushdozerConfigScreen parent, PushdozerConfig config) {
        super(parent, config);
    }

    @Override
    protected Text getTitleText() {
        return Text.translatable("pushdozer.mode.excavate");
    }

    @Override
    protected void initializeWidgets() {
        widgets.clear();

        // 计算内容区域起始位置
        int contentLeft = panelLeft + WIDGET_MARGIN;
        int contentTop = panelTop + TITLE_HEIGHT + WIDGET_MARGIN;
        int contentWidth = PANEL_WIDTH - (WIDGET_MARGIN * 2);

        // 选择允许破坏的方块按钮
        ButtonWidget selectBlocksButton = ButtonWidget.builder(
                        Text.translatable("pushdozer.config.block_selection"),
                        button -> openBlockSelectionScreen())
                .dimensions(contentLeft, contentTop, contentWidth, WIDGET_HEIGHT)
                .tooltip(Tooltip.of(Text.translatable("pushdozer.tooltip.block_selection")))
                .build();
        widgets.add(selectBlocksButton);

        // 注意：确认按钮会在基类的initializeConfirmButton()中添加
    }

    /**
     * 打开方块选择屏幕
     */
    private void openBlockSelectionScreen() {
        if (parent.getClient() != null) {
            BlockSelectionScreen blockSelectionScreen = new BlockSelectionScreen(parent, config);
            parent.getClient().setScreen(blockSelectionScreen);
        }
    }

    @Override
    public void saveConfig() {
        persistPanelConfig();
    }
} 