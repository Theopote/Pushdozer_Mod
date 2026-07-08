package com.pushdozer.ui.panels.workmode;

import com.pushdozer.config.PushdozerConfig;
import com.pushdozer.ui.screens.PushdozerConfigScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

/**
 * 撒骨粉配置面板
 * 暂时不需要配置参数
 */
public class BoneMealConfigPanel extends WorkModeConfigPanel {

    public BoneMealConfigPanel(PushdozerConfigScreen parent, PushdozerConfig config) {
        super(parent, config);
    }

    @Override
    protected Text getTitleText() {
        return Text.translatable("pushdozer.mode.bone_meal");
    }

    @Override
    protected void initializeWidgets() {
        widgets.clear();

        // 计算内容区域起始位置
        int contentLeft = panelLeft + WIDGET_MARGIN;
        int contentTop = panelTop + TITLE_HEIGHT + WIDGET_MARGIN;
        int contentWidth = PANEL_WIDTH - (WIDGET_MARGIN * 2);

        // 创建提示信息标签（使用按钮作为标签，但禁用点击）
        ButtonWidget infoLabel = ButtonWidget.builder(
            Text.translatable("pushdozer.info.bone_meal.no_config"),
            button -> {}) // 空操作，不执行任何功能
            .dimensions(contentLeft, contentTop, contentWidth, WIDGET_HEIGHT)
            .build();
        
        // 禁用按钮，使其看起来像标签
        infoLabel.active = false;
        widgets.add(infoLabel);

        // 注意：确认按钮会在基类的initializeConfirmButton()中添加
    }

    @Override
    public void saveConfig() {
        persistPanelConfig();
    }
} 