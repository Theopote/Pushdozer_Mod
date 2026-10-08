package com.pushdozer.ui.panels.workmode;

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
 * 第一到第四行：四种平滑变体（普通/自适应/提升/降低）单选按钮
 * 第五行：平滑强度滑动条
 */
public class SmoothConfigPanel extends WorkModeConfigPanel {

    private ButtonWidget standardButton;
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
        int rowHeight = WIDGET_HEIGHT + WIDGET_MARGIN;

        standardButton = ButtonWidget.builder(
                getVariantButtonText(PushdozerConfig.SmoothVariant.STANDARD),
                btn -> selectVariant(PushdozerConfig.SmoothVariant.STANDARD)
        ).dimensions(contentLeft, contentTop, contentWidth, WIDGET_HEIGHT).build();
        widgets.add(standardButton);

        adaptiveButton = ButtonWidget.builder(
                getVariantButtonText(PushdozerConfig.SmoothVariant.ADAPTIVE),
                btn -> selectVariant(PushdozerConfig.SmoothVariant.ADAPTIVE)
        ).dimensions(contentLeft, contentTop + rowHeight, contentWidth, WIDGET_HEIGHT).build();
        widgets.add(adaptiveButton);

        raiseButton = ButtonWidget.builder(
                getVariantButtonText(PushdozerConfig.SmoothVariant.RAISE),
                btn -> selectVariant(PushdozerConfig.SmoothVariant.RAISE)
        ).dimensions(contentLeft, contentTop + 2 * rowHeight, contentWidth, WIDGET_HEIGHT).build();
        widgets.add(raiseButton);

        lowerButton = ButtonWidget.builder(
                getVariantButtonText(PushdozerConfig.SmoothVariant.LOWER),
                btn -> selectVariant(PushdozerConfig.SmoothVariant.LOWER)
        ).dimensions(contentLeft, contentTop + 3 * rowHeight, contentWidth, WIDGET_HEIGHT).build();
        widgets.add(lowerButton);

        float currentStrength = config.getSmoothStrength();
        strengthSlider = new SliderWidget(
                contentLeft,
                contentTop + 4 * rowHeight,
                contentWidth,
                WIDGET_HEIGHT,
                getStrengthText(currentStrength),
                (currentStrength - 0.1f) / 0.9f
        ) {
            @Override
            protected void updateMessage() {
                float strength = (float) (this.value * 0.9f + 0.1f);
                setMessage(getStrengthText(strength));
            }

            @Override
            protected void applyValue() {
                float strength = (float) (this.value * 0.9f + 0.1f);
                config.setSmoothStrength(strength);
            }
        };
        updateStrengthSliderPresentation();
        widgets.add(strengthSlider);
    }

    protected void renderWidgets(DrawContext context, int mouseX, int mouseY, float delta) {
        for (Element widget : widgets) {
            if (widget instanceof ButtonWidget button) {
                button.setFocused(false);
            }
        }

        PushdozerConfig.SmoothVariant currentVariant = config.getSmoothVariant();

        for (Element widget : widgets) {
            if (widget instanceof ButtonWidget button) {
                if (button == standardButton || button == adaptiveButton
                    || button == raiseButton || button == lowerButton) {
                    boolean isSelected = switch (currentVariant) {
                        case STANDARD -> button == standardButton;
                        case ADAPTIVE -> button == adaptiveButton;
                        case RAISE -> button == raiseButton;
                        case LOWER -> button == lowerButton;
                    };
                    if (isSelected) {
                        button.setFocused(true);
                    }
                }
            }
        }

        for (Element widget : widgets) {
            if (widget instanceof net.minecraft.client.gui.Drawable drawable) {
                drawable.render(context, mouseX, mouseY, delta);
            }
        }
    }

    private void selectVariant(PushdozerConfig.SmoothVariant variant) {
        config.setSmoothVariant(variant);
        updateVariantButtons();
        updateStrengthSliderPresentation();
    }

    private void updateVariantButtons() {
        if (standardButton != null) {
            standardButton.setMessage(getVariantButtonText(PushdozerConfig.SmoothVariant.STANDARD));
        }
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
        String prefix = selected ? "☑ " : "";
        return Text.literal(prefix).append(variant.getDisplayText());
    }

    private Text getStrengthText(float strength) {
        if (usesDirectionalDepthLabel()) {
            return Text.translatable("pushdozer.config.directional_depth", String.format("%.2f", strength));
        }
        return Text.translatable("pushdozer.config.smooth_strength", String.format("%.2f", strength));
    }

    private boolean usesDirectionalDepthLabel() {
        PushdozerConfig.SmoothVariant variant = config.getSmoothVariant();
        return variant == PushdozerConfig.SmoothVariant.RAISE
            || variant == PushdozerConfig.SmoothVariant.LOWER;
    }

    private void updateStrengthSliderPresentation() {
        if (strengthSlider == null) {
            return;
        }
        strengthSlider.setMessage(getStrengthText(config.getSmoothStrength()));
        if (usesDirectionalDepthLabel()) {
            strengthSlider.setTooltip(Tooltip.of(Text.translatable("pushdozer.tooltip.directional_depth")));
        } else {
            strengthSlider.setTooltip(Tooltip.of(Text.translatable("pushdozer.tooltip.smooth_strength")));
        }
    }

    @Override
    public void saveConfig() {
        persistPanelConfig();
    }
}
