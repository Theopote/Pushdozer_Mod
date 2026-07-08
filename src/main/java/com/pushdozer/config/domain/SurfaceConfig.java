package com.pushdozer.config.domain;

import com.google.gson.annotations.Expose;
import com.pushdozer.config.PushdozerConfig;
import com.pushdozer.util.RegistryBlocks;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;

import java.util.ArrayList;
import java.util.List;

public class SurfaceConfig {
    public static class SurfaceConvertBlock {
        @Expose
        private String blockId;
        @Expose
        private float percentage;

        public SurfaceConvertBlock(String blockId, float percentage) {
            this.blockId = blockId;
            this.percentage = percentage;
        }

        public String getBlockId() {
            return blockId;
        }

        public float getPercentage() {
            return percentage;
        }

        public void setPercentage(float percentage) {
            this.percentage = Math.max(0.0f, Math.min(100.0f, percentage));
        }
    }

    @Expose
    private PushdozerConfig.PlaceMode placeMode = PushdozerConfig.PlaceMode.ADAPTIVE_BIOME;
    @Expose
    private String selectedNaturalBlockId = "minecraft:stone";
    @Expose
    private float smoothStrength = 0.5f;
    @Expose
    private PushdozerConfig.SmoothVariant smoothVariant = PushdozerConfig.SmoothVariant.ADAPTIVE;
    @Expose
    private float roughnessStrength = 0.5f;
    @Expose
    private float smoothingIntensity = 0.5f;
    @Expose
    private long noiseSeed = System.currentTimeMillis();
    @Expose
    private boolean noiseAutoScale = true;
    @Expose
    private float noiseFrequency = 0.02f;
    @Expose
    private float noisePersistence = 0.5f;
    @Expose
    private int noiseOctaves = 4;
    @Expose
    private List<SurfaceConvertBlock> surfaceConvertBlocks = new ArrayList<>(List.of(
        new SurfaceConvertBlock("minecraft:grass_block", 100.0f)
    ));

    private ConfigChangeNotifier onChange = () -> {};

    public void setOnChange(ConfigChangeNotifier onChange) {
        this.onChange = onChange != null ? onChange : () -> {};
    }

    public PushdozerConfig.PlaceMode getPlaceMode() {
        return placeMode;
    }

    public void setPlaceMode(PushdozerConfig.PlaceMode placeMode) {
        this.placeMode = placeMode;
        onChange.onConfigChanged();
    }

    public void setSelectedNaturalBlockId(String blockId) {
        this.selectedNaturalBlockId = blockId;
        onChange.onConfigChanged();
    }

    public Block getSelectedNaturalBlock() {
        return RegistryBlocks.resolve(selectedNaturalBlockId, Blocks.STONE);
    }

    public float getSmoothStrength() {
        return smoothStrength;
    }

    public void setSmoothStrength(float smoothStrength) {
        this.smoothStrength = Math.max(0.1f, Math.min(1.0f, smoothStrength));
        onChange.onConfigChanged();
    }

    public PushdozerConfig.SmoothVariant getSmoothVariant() {
        return smoothVariant == null ? PushdozerConfig.SmoothVariant.ADAPTIVE : smoothVariant;
    }

    public void setSmoothVariant(PushdozerConfig.SmoothVariant variant) {
        this.smoothVariant = (variant == null) ? PushdozerConfig.SmoothVariant.ADAPTIVE : variant;
        onChange.onConfigChanged();
    }

    public float getRoughnessStrength() {
        return roughnessStrength;
    }

    public void setRoughnessStrength(float roughnessStrength) {
        this.roughnessStrength = Math.max(0.1f, Math.min(2.0f, roughnessStrength));
        onChange.onConfigChanged();
    }

    public float getSmoothingIntensity() {
        return smoothingIntensity;
    }

    public void setSmoothingIntensity(float smoothingIntensity) {
        this.smoothingIntensity = Math.max(0.0f, Math.min(1.0f, smoothingIntensity));
        onChange.onConfigChanged();
    }

    public long getNoiseSeed() {
        return noiseSeed;
    }

    public boolean isNoiseAutoScale() {
        return noiseAutoScale;
    }

    public void setNoiseAutoScale(boolean auto) {
        this.noiseAutoScale = auto;
        onChange.onConfigChanged();
    }

    public float getNoiseFrequency() {
        return noiseFrequency;
    }

    public void setNoiseFrequency(float value) {
        this.noiseFrequency = Math.max(0.005f, Math.min(0.2f, value));
        onChange.onConfigChanged();
    }

    public float getNoisePersistence() {
        return noisePersistence;
    }

    public void setNoisePersistence(float value) {
        this.noisePersistence = Math.max(0.05f, Math.min(0.95f, value));
        onChange.onConfigChanged();
    }

    public int getNoiseOctaves() {
        return noiseOctaves;
    }

    public void setNoiseOctaves(int value) {
        this.noiseOctaves = Math.max(1, Math.min(6, value));
        onChange.onConfigChanged();
    }

    public List<SurfaceConvertBlock> getSurfaceConvertBlocks() {
        return surfaceConvertBlocks;
    }
}
