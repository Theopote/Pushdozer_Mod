package com.pushdozer.ui.panels.workmode;

import com.pushdozer.PushdozerMod;
import com.pushdozer.config.PushdozerConfig;
import com.pushdozer.ui.screens.PushdozerConfigScreen;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.text.Text;

/**
 * 表面粗糙模式配置面板 (UI/UX 简化版)
 * <p>
 * 优化点:
 * 1. 【UI简化】默认隐藏高级噪声参数，仅在关闭"自动缩放"时显示，降低认知负担。
 * 2. 【动态布局】当"自动缩放"开启时，高级参数完全隐藏，面板自动收缩，界面更紧凑。
 * 3. 【交互改进】重构了控件初始化逻辑，使其能动态响应"自动缩放"的开关，无灰色禁用项。
 */
public class SurfaceRoughenConfigPanel extends WorkModeConfigPanel {
    
    // 高级控件引用，仅在自动缩放关闭时创建
    private SliderWidget freqSlider, perSlider, octSlider;
    private ButtonWidget autoScaleBtn;

    public SurfaceRoughenConfigPanel(PushdozerConfigScreen parent, PushdozerConfig config) {
        super(parent, config);
    }

    @Override
    protected Text getTitleText() {
        return Text.translatable("pushdozer.mode.surface_roughen");
    }

    @Override
    protected void initializeWidgets() {
        widgets.clear();

        int contentLeft = panelLeft + WIDGET_MARGIN;
        int contentTop = panelTop + TITLE_HEIGHT + WIDGET_MARGIN;
        int contentWidth = PANEL_WIDTH - (WIDGET_MARGIN * 2);

        // 1. 粗糙强度滑动条 (核心控件，始终显示)
        SliderWidget strengthSlider = createStrengthSlider(contentLeft, contentTop, contentWidth);
        widgets.add(strengthSlider);
        contentTop += WIDGET_HEIGHT + WIDGET_MARGIN;

        // 2. 平滑强度滑动条 (核心控件，始终显示)
        SliderWidget smoothingSlider = createSmoothingSlider(contentLeft, contentTop, contentWidth);
        widgets.add(smoothingSlider);
        contentTop += WIDGET_HEIGHT + WIDGET_MARGIN;

        // 3. 自动缩放开关 (核心控件，始终显示)
        autoScaleBtn = ButtonWidget.builder(
            getAutoScaleLabel(config.isNoiseAutoScale()),
            b -> {
                config.setNoiseAutoScale(!config.isNoiseAutoScale());
                // 重新渲染控件以更新布局
                this.show(); 
            }
        ).dimensions(contentLeft, contentTop, contentWidth, WIDGET_HEIGHT).build();
        autoScaleBtn.setTooltip(Tooltip.of(Text.translatable("pushdozer.tooltip.noise_auto_scale")));
        widgets.add(autoScaleBtn);
        contentTop += WIDGET_HEIGHT + WIDGET_MARGIN;

        // 4. 高级参数 (仅在自动缩放关闭时显示)
        if (!config.isNoiseAutoScale()) {
            // 噪声频率
            freqSlider = createFrequencySlider(contentLeft, contentTop, contentWidth);
            widgets.add(freqSlider);
            contentTop += WIDGET_HEIGHT + WIDGET_MARGIN;

            // 噪声持久性
            perSlider = createPersistenceSlider(contentLeft, contentTop, contentWidth);
            widgets.add(perSlider);
            contentTop += WIDGET_HEIGHT + WIDGET_MARGIN;

            // 噪声八度
            octSlider = createOctavesSlider(contentLeft, contentTop, contentWidth);
            widgets.add(octSlider);
            contentTop += WIDGET_HEIGHT + WIDGET_MARGIN;
        }

        // 5. 重置按钮
        ButtonWidget resetBtn = ButtonWidget.builder(
            Text.translatable("pushdozer.button.reset"),
            b -> {
                config.setRoughnessStrength(0.5f);
                config.setSmoothingIntensity(0.5f); // 重置平滑强度
                config.setNoiseAutoScale(true); // 重置时默认开启自动缩放
                // 默认值也应重置
                config.setNoiseFrequency(0.02f);
                config.setNoisePersistence(0.5f);
                config.setNoiseOctaves(4);
                
                // 重新初始化控件以更新显示
                this.show();
            }
        ).dimensions(contentLeft, contentTop, contentWidth, WIDGET_HEIGHT).build();
        resetBtn.setTooltip(Tooltip.of(Text.translatable("pushdozer.tooltip.reset")));
        widgets.add(resetBtn);
    }
    


    // 将控件创建逻辑提取为独立方法，使 initializeWidgets 更清晰
    private SliderWidget createStrengthSlider(int x, int y, int w) {
        float currentStrength = config.getRoughnessStrength();
        float minStrength = 0.1f;
        float maxStrength = 2.0f;
        SliderWidget strengthSlider = new SliderWidget(
                x,
                y,
                w,
                WIDGET_HEIGHT,
                getStrengthText(currentStrength),
                (currentStrength - minStrength) / (maxStrength - minStrength)
        ) {
            @Override
            protected void updateMessage() {
                float strength = (float) (this.value * (maxStrength - minStrength) + minStrength);
                setMessage(getStrengthText(strength));
            }

            @Override
            protected void applyValue() {
                float strength = (float) (this.value * (maxStrength - minStrength) + minStrength);
                config.setRoughnessStrength(strength);
            }
        };
        strengthSlider.setTooltip(Tooltip.of(Text.translatable("pushdozer.tooltip.roughness_strength")));
        return strengthSlider;
    }

    private SliderWidget createSmoothingSlider(int x, int y, int w) {
        float currentSmoothing = config.getSmoothingIntensity();
        float minSmoothing = 0.0f;
        float maxSmoothing = 1.0f;
        SliderWidget smoothingSlider = new SliderWidget(
                x,
                y,
                w,
                WIDGET_HEIGHT,
                getSmoothingText(currentSmoothing),
                (currentSmoothing - minSmoothing) / (maxSmoothing - minSmoothing)
        ) {
            @Override
            protected void updateMessage() {
                float smoothing = (float) (this.value * (maxSmoothing - minSmoothing) + minSmoothing);
                setMessage(getSmoothingText(smoothing));
            }

            @Override
            protected void applyValue() {
                float smoothing = (float) (this.value * (maxSmoothing - minSmoothing) + minSmoothing);
                config.setSmoothingIntensity(smoothing);
            }
        };
        smoothingSlider.setTooltip(Tooltip.of(Text.translatable("pushdozer.tooltip.smoothing_intensity")));
        return smoothingSlider;
    }

    private SliderWidget createFrequencySlider(int x, int y, int w) {
        float fMin = 0.01f, fMax = 0.15f;
        float currentFreq = clamp(config.getNoiseFrequency(), fMin, fMax);
        SliderWidget slider = new SliderWidget(
            x, y, w, WIDGET_HEIGHT,
            Text.translatable("pushdozer.config.noise_frequency", String.format("%.3f", currentFreq)),
            (currentFreq - fMin) / (fMax - fMin)
        ) {
            @Override
            protected void updateMessage() {
                float v = (float)(this.value * (fMax - fMin) + fMin);
                setMessage(Text.translatable("pushdozer.config.noise_frequency", String.format("%.3f", v)));
            }

            @Override
            protected void applyValue() {
                float v = (float)(this.value * (fMax - fMin) + fMin);
                config.setNoiseFrequency(v);
            }
        };
        slider.setTooltip(Tooltip.of(Text.translatable("pushdozer.tooltip.noise_frequency")));
        return slider;
    }

    private SliderWidget createPersistenceSlider(int x, int y, int w) {
        float pMin = 0.1f, pMax = 0.9f;
        float currentPer = clamp(config.getNoisePersistence(), pMin, pMax);
        SliderWidget slider = new SliderWidget(
            x, y, w, WIDGET_HEIGHT,
            Text.translatable("pushdozer.config.noise_persistence", String.format("%.2f", currentPer)),
            (currentPer - pMin) / (pMax - pMin)
        ) {
            @Override
            protected void updateMessage() {
                float v = (float)(this.value * (pMax - pMin) + pMin);
                setMessage(Text.translatable("pushdozer.config.noise_persistence", String.format("%.2f", v)));
            }

            @Override
            protected void applyValue() {
                float v = (float)(this.value * (pMax - pMin) + pMin);
                config.setNoisePersistence(v);
            }
        };
        slider.setTooltip(Tooltip.of(Text.translatable("pushdozer.tooltip.noise_persistence")));
        return slider;
    }
    
    private SliderWidget createOctavesSlider(int x, int y, int w) {
        int oMin = 1, oMax = 6;
        int currentOct = Math.max(oMin, Math.min(oMax, config.getNoiseOctaves()));
        SliderWidget octSlider = new SliderWidget(
                x, y, w, WIDGET_HEIGHT,
            Text.translatable("pushdozer.config.noise_octaves", String.valueOf(currentOct)),
            (currentOct - oMin) / (double)(oMax - oMin)
        ) {
            @Override
            protected void updateMessage() {
                int v = (int)Math.round(this.value * (oMax - oMin) + oMin);
                setMessage(Text.translatable("pushdozer.config.noise_octaves", String.valueOf(v)));
            }

            @Override
            protected void applyValue() {
                int v = (int)Math.round(this.value * (oMax - oMin) + oMin);
                config.setNoiseOctaves(v);
            }
        };
        octSlider.setTooltip(Tooltip.of(Text.translatable("pushdozer.tooltip.noise_octaves")));
        return octSlider;
    }

    private Text getStrengthText(float strength) {
        return Text.translatable("pushdozer.config.roughness_strength", String.format("%.2f", strength));
    }

    private Text getSmoothingText(float smoothing) {
        return Text.translatable("pushdozer.config.smoothing_intensity", String.format("%.2f", smoothing));
    }

    private Text getAutoScaleLabel(boolean enabled) {
        String state = enabled ? Text.translatable("pushdozer.common.on").getString() : Text.translatable("pushdozer.common.off").getString();
        return Text.translatable("pushdozer.config.noise_auto_scale", state);
    }

    private float clamp(float v, float min, float max) { 
        return Math.max(min, Math.min(max, v)); 
    }

    @Override
    public void saveConfig() {
        try {
            config.save();
            parent.showErrorMessage(Text.translatable("pushdozer.config.saved").getString());
        } catch (Exception e) {
            PushdozerMod.LOGGER.error("Failed to save surface roughen config", e);
        }
    }
}
