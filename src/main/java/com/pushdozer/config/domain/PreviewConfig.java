package com.pushdozer.config.domain;

import com.google.gson.annotations.Expose;
import com.pushdozer.config.PushdozerConfig;

public class PreviewConfig {
    @Expose
    private PushdozerConfig.DisplayMode displayMode = PushdozerConfig.DisplayMode.WIREFRAME;
    @Expose
    private int maxOperationDistance = 20;

    private ConfigChangeNotifier onChange = () -> {};

    public void setOnChange(ConfigChangeNotifier onChange) {
        this.onChange = onChange != null ? onChange : () -> {};
    }

    public PushdozerConfig.DisplayMode getDisplayMode() {
        return displayMode;
    }

    public void setDisplayMode(PushdozerConfig.DisplayMode displayMode) {
        this.displayMode = displayMode;
        onChange.onConfigChanged();
    }

    public int getMaxOperationDistance() {
        return maxOperationDistance;
    }

    public void setMaxOperationDistance(int distance) {
        this.maxOperationDistance = distance;
        onChange.onConfigChanged();
    }
}
