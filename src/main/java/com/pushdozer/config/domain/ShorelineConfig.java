package com.pushdozer.config.domain;

import com.google.gson.annotations.Expose;
import com.pushdozer.config.PushdozerConfig;
import net.minecraft.block.Block;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

public class ShorelineConfig {
    @Expose
    private PushdozerConfig.ShorelineType shorelineType = PushdozerConfig.ShorelineType.ADAPTIVE;
    @Expose
    private int shorelineWidth = 3;
    @Expose
    private boolean plantVegetationEnabled = true;
    @Expose
    private float vegetationDensity = 0.1f;
    @Expose
    private Set<String> customShorelineBlocks = new HashSet<>();
    @Expose
    private Set<String> customShorelinePlants = new HashSet<>();
    @Expose
    private boolean shorelineHeightAboveEnabled = false;
    @Expose
    private boolean shorelineHeightBelowEnabled = false;

    private ConfigChangeNotifier onChange = () -> {};

    public void setOnChange(ConfigChangeNotifier onChange) {
        this.onChange = onChange != null ? onChange : () -> {};
    }

    public PushdozerConfig.ShorelineType getShorelineType() {
        return shorelineType;
    }

    public void setShorelineType(PushdozerConfig.ShorelineType shorelineType) {
        this.shorelineType = shorelineType;
        onChange.onConfigChanged();
    }

    public int getShorelineWidth() {
        return shorelineWidth;
    }

    public void setShorelineWidth(int shorelineWidth) {
        this.shorelineWidth = Math.max(1, Math.min(10, shorelineWidth));
        onChange.onConfigChanged();
    }

    public boolean isPlantVegetationEnabled() {
        return plantVegetationEnabled;
    }

    public void setPlantVegetationEnabled(boolean enabled) {
        this.plantVegetationEnabled = enabled;
        onChange.onConfigChanged();
    }

    public float getVegetationDensity() {
        return vegetationDensity;
    }

    public void setVegetationDensity(float density) {
        this.vegetationDensity = Math.max(0.0f, Math.min(1.0f, density));
        onChange.onConfigChanged();
    }

    public void setCustomShorelineBlocks(Set<String> blockIds) {
        this.customShorelineBlocks = blockIds;
        onChange.onConfigChanged();
    }

    public void setCustomShorelinePlants(Set<String> plantIds) {
        this.customShorelinePlants = plantIds;
        onChange.onConfigChanged();
    }

    public List<Block> getCustomShorelineBlockList() {
        return customShorelineBlocks.stream()
            .map(Identifier::tryParse)
            .filter(Objects::nonNull)
            .map(Registries.BLOCK::get)
            .collect(Collectors.toList());
    }

    public List<Block> getCustomShorelinePlantList() {
        return customShorelinePlants.stream()
            .map(Identifier::tryParse)
            .filter(Objects::nonNull)
            .map(Registries.BLOCK::get)
            .collect(Collectors.toList());
    }

    public boolean isShorelineHeightAboveEnabled() {
        return shorelineHeightAboveEnabled;
    }

    public void setShorelineHeightAboveEnabled(boolean enabled) {
        this.shorelineHeightAboveEnabled = enabled;
        if (enabled) {
            this.shorelineHeightBelowEnabled = false;
        }
        onChange.onConfigChanged();
    }

    public boolean isShorelineHeightBelowEnabled() {
        return shorelineHeightBelowEnabled;
    }

    public void setShorelineHeightBelowEnabled(boolean enabled) {
        this.shorelineHeightBelowEnabled = enabled;
        if (enabled) {
            this.shorelineHeightAboveEnabled = false;
        }
        onChange.onConfigChanged();
    }
}
