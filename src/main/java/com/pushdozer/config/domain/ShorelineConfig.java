package com.pushdozer.config.domain;

import com.google.gson.annotations.Expose;
import com.pushdozer.config.PushdozerConfig;
import net.minecraft.block.Block;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

public class ShorelineConfig {
    public static final int MAX_SHORELINE_WIDTH = 10;

    @Expose
    private PushdozerConfig.ShorelineType shorelineType = PushdozerConfig.ShorelineType.ADAPTIVE;
    @Expose
    private int shorelineWidth = 3;
    @Expose
    private boolean plantVegetationEnabled = true;
    @Expose
    private float vegetationDensity = 0.1f;
    @Expose
    private List<String> customShorelineBlocks = new ArrayList<>();
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

    public void normalize() {
        shorelineWidth = Math.max(1, Math.min(MAX_SHORELINE_WIDTH, shorelineWidth));
        if (customShorelineBlocks == null) {
            customShorelineBlocks = new ArrayList<>();
        } else {
            customShorelineBlocks = customShorelineBlocks.stream()
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toCollection(ArrayList::new));
        }
        if (customShorelinePlants == null) {
            customShorelinePlants = new HashSet<>();
        }
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
        this.shorelineWidth = Math.max(1, Math.min(MAX_SHORELINE_WIDTH, shorelineWidth));
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

    public void setCustomShorelineBlocks(Collection<String> blockIds) {
        if (blockIds == null) {
            this.customShorelineBlocks = new ArrayList<>();
        } else {
            this.customShorelineBlocks = blockIds.stream()
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toCollection(ArrayList::new));
        }
        onChange.onConfigChanged();
    }

    public void setCustomShorelinePlants(Set<String> plantIds) {
        this.customShorelinePlants = plantIds != null ? plantIds : new HashSet<>();
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
