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

public class PlantingConfig {
    @Expose
    private PushdozerConfig.PlantType plantType = PushdozerConfig.PlantType.TREES;
    @Expose
    private Set<String> customPlantBlockIds = new HashSet<>();
    @Expose
    private float plantDensity = 0.3f;
    @Expose
    private PushdozerConfig.TreeSpecies selectedTree = PushdozerConfig.TreeSpecies.BIOME_ADAPTIVE;
    @Expose
    private PushdozerConfig.FlowerGroup selectedFlowerGroup = PushdozerConfig.FlowerGroup.BIOME_ADAPTIVE;
    @Expose
    private boolean respectBiomes = true;
    @Expose
    private float clusterScale = 0.05f;

    private ConfigChangeNotifier onChange = () -> {};

    public void setOnChange(ConfigChangeNotifier onChange) {
        this.onChange = onChange != null ? onChange : () -> {};
    }

    public PushdozerConfig.PlantType getPlantType() {
        return plantType;
    }

    public void setPlantType(PushdozerConfig.PlantType plantType) {
        this.plantType = plantType;
        onChange.onConfigChanged();
    }

    public float getPlantDensity() {
        return plantDensity;
    }

    public void setPlantDensity(float plantDensity) {
        this.plantDensity = Math.max(0.1f, Math.min(1.0f, plantDensity));
        onChange.onConfigChanged();
    }

    public PushdozerConfig.TreeSpecies getSelectedTree() {
        return selectedTree;
    }

    public void setSelectedTree(PushdozerConfig.TreeSpecies selectedTree) {
        this.selectedTree = selectedTree;
        onChange.onConfigChanged();
    }

    public PushdozerConfig.FlowerGroup getSelectedFlowerGroup() {
        return selectedFlowerGroup;
    }

    public void setSelectedFlowerGroup(PushdozerConfig.FlowerGroup selectedFlowerGroup) {
        this.selectedFlowerGroup = selectedFlowerGroup;
        onChange.onConfigChanged();
    }

    public boolean isRespectBiomes() {
        return respectBiomes;
    }

    public void setRespectBiomes(boolean respectBiomes) {
        this.respectBiomes = respectBiomes;
        onChange.onConfigChanged();
    }

    public float getClusterScale() {
        return clusterScale;
    }

    public void setClusterScale(float clusterScale) {
        this.clusterScale = clusterScale;
        onChange.onConfigChanged();
    }

    public List<Block> getCustomPlantBlocks() {
        return customPlantBlockIds.stream()
            .map(Identifier::tryParse)
            .filter(Objects::nonNull)
            .map(Registries.BLOCK::get)
            .collect(Collectors.toList());
    }

    public void setCustomPlantBlocks(List<Block> blocks) {
        this.customPlantBlockIds = blocks.stream()
            .map(block -> Registries.BLOCK.getId(block).toString())
            .collect(Collectors.toSet());
        onChange.onConfigChanged();
    }
}
