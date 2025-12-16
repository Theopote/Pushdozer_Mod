package com.pushdozer.ui.panels.workmode;

import com.pushdozer.PushdozerMod;
import com.pushdozer.config.PushdozerConfig;
import com.pushdozer.ui.screens.PushdozerConfigScreen;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.text.Text;

/**
 * 平滑模式配置面板
 * 第一到第三行：三种平滑变体（自适应/提升/降低）单选按钮
 * 第四行：平滑强度滑动条
 * 底部：确定按钮
 */
public class SmoothConfigPanel extends WorkModeConfigPanel {

    private ButtonWidget adaptiveButton;
    private ButtonWidget raiseButton;
    private ButtonWidget lowerButton;
    private SliderWidget strengthSlider;

    public SmoothConfigPanel(PushdozerConfigScreen parent, PushdozerConfig config) {
        super(parent, config);
    }

    @Override
    protected Text getTitleText() {
        return Text.translatable("pushdozer.panel.smooth.title");
    }

    @Override
    protected void initializeWidgets() {
        widgets.clear();

        int contentLeft = panelLeft + WIDGET_MARGIN;
        int contentTop = panelTop + TITLE_HEIGHT + WIDGET_MARGIN;
        int contentWidth = PANEL_WIDTH - (WIDGET_MARGIN * 2);

        // 三个变体按钮（单选）
        adaptiveButton = ButtonWidget.builder(
                getVariantButtonText(PushdozerConfig.SmoothVariant.ADAPTIVE),
                btn -> selectVariant(PushdozerConfig.SmoothVariant.ADAPTIVE)
        ).dimensions(contentLeft, contentTop, contentWidth, WIDGET_HEIGHT).build();
        widgets.add(adaptiveButton);

        raiseButton = ButtonWidget.builder(
                getVariantButtonText(PushdozerConfig.SmoothVariant.RAISE),
                btn -> selectVariant(PushdozerConfig.SmoothVariant.RAISE)
        ).dimensions(contentLeft, contentTop + (WIDGET_HEIGHT + WIDGET_MARGIN), contentWidth, WIDGET_HEIGHT).build();
        widgets.add(raiseButton);

        lowerButton = ButtonWidget.builder(
                getVariantButtonText(PushdozerConfig.SmoothVariant.LOWER),
                btn -> selectVariant(PushdozerConfig.SmoothVariant.LOWER)
        ).dimensions(contentLeft, contentTop + 2 * (WIDGET_HEIGHT + WIDGET_MARGIN), contentWidth, WIDGET_HEIGHT).build();
        widgets.add(lowerButton);

        // 强度滑动条（第4行）
        float currentStrength = config.getSmoothStrength();
        strengthSlider = new SliderWidget(
                contentLeft,
                contentTop + 3 * (WIDGET_HEIGHT + WIDGET_MARGIN),
                contentWidth,
                WIDGET_HEIGHT,
                getStrengthText(currentStrength),
                (currentStrength - 0.1f) / 0.9f // 0.1-1.0 → 0-1
        ) {
            @Override
            protected void updateMessage() {
                float strength = (float) (this.value * 0.9f + 0.1f);
                setMessage(getStrengthText(strength));
            }

            @Override
            protected void applyValue() {
                float strength = (float) (this.value * 0.9f + 0.1f);
                try {
                    config.setSmoothStrength(strength);
                } catch (Exception e) {
                    PushdozerMod.LOGGER.error("Failed to update smooth strength", e);
                }
            }
        };
        strengthSlider.setTooltip(Tooltip.of(Text.translatable("pushdozer.tooltip.smooth_strength")));
        widgets.add(strengthSlider);
    }

    protected void renderWidgets(DrawContext context, int mouseX, int mouseY, float delta) {
        // 先重置所有按钮的焦点状态
        for (Element widget : widgets) {
            if (widget instanceof ButtonWidget button) {
                button.setFocused(false);
            }
        }
        
        // 然后设置当前选中按钮的焦点状态
        PushdozerConfig.SmoothVariant currentVariant = config.getSmoothVariant();
        
        for (Element widget : widgets) {
            if (widget instanceof ButtonWidget button) {
                // 检查是否为变体按钮（排除强度滑动条和确认按钮）
                if (button == adaptiveButton || button == raiseButton || button == lowerButton) {
                    boolean isSelected;
                    if (button == adaptiveButton) {
                        isSelected = (currentVariant == PushdozerConfig.SmoothVariant.ADAPTIVE);
                    } else if (button == raiseButton) {
                        isSelected = (currentVariant == PushdozerConfig.SmoothVariant.RAISE);
                    } else {
                        isSelected = (currentVariant == PushdozerConfig.SmoothVariant.LOWER);
                    }
                    
                    if (isSelected) {
                        // 使用ButtonWidget的内置按下状态
                        button.setFocused(true);
                    }
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

    private void selectVariant(PushdozerConfig.SmoothVariant variant) {
        try {
            config.setSmoothVariant(variant);
            updateVariantButtons();
        } catch (Exception e) {
            PushdozerMod.LOGGER.error("Failed to set smooth variant", e);
        }
    }

    private void updateVariantButtons() {
        if (adaptiveButton != null) {
            adaptiveButton.setMessage(getVariantButtonText(PushdozerConfig.SmoothVariant.ADAPTIVE));
        }
        if (raiseButton != null) {
            raiseButton.setMessage(getVariantButtonText(PushdozerConfig.SmoothVariant.RAISE));
        }
        if (lowerButton != null) {
            lowerButton.setMessage(getVariantButtonText(PushdozerConfig.SmoothVariant.LOWER));
        }
    }

    private Text getVariantButtonText(PushdozerConfig.SmoothVariant variant) {
        boolean selected = config.getSmoothVariant() == variant;
        String prefix = selected ? "☑ " : ""; // 仅选中项带前缀
        return Text.literal(prefix).append(variant.getDisplayText());
    }

    private Text getStrengthText(float strength) {
        return Text.translatable("pushdozer.config.smooth_strength", String.format("%.2f", strength));
    }

    @Override
    public void saveConfig() {
        try {
            config.save();
            parent.showErrorMessage(Text.translatable("pushdozer.config.saved").getString());
        } catch (Exception e) {
            PushdozerMod.LOGGER.error("Failed to save smooth config", e);
        }
    }
}


