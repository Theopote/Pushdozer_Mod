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
            this.percentage = Math.clamp(percentage, 0.0f, 100.0f);
        }
    }

    @Expose
    private PushdozerConfig.PlaceMode placeMode = PushdozerConfig.PlaceMode.ADAPTIVE_BIOME;
    @Expose
    private String selectedNaturalBlockId = "minecraft:stone";
    @Expose
    private float smoothStrength = 0.5f;
    @Expose
    private float directionalSmoothBlend = 0.65f;
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
    @Expose
    private boolean convertArtificialSurfaces = false;
    @Expose
    private PushdozerConfig.SurfaceConvertDistribution surfaceConvertDistribution =
        PushdozerConfig.SurfaceConvertDistribution.PATCHY;
    @Expose
    private int surfaceConvertMaxBelowSurfaceDepth = 3;

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
        this.smoothStrength = Math.clamp(smoothStrength, 0.1f, 1.0f);
        onChange.onConfigChanged();
    }

    public float getDirectionalSmoothBlend() {
        return directionalSmoothBlend;
    }

    public void setDirectionalSmoothBlend(float directionalSmoothBlend) {
        this.directionalSmoothBlend = Math.clamp(directionalSmoothBlend, 0.0f, 1.0f);
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
        this.roughnessStrength = Math.clamp(roughnessStrength, 0.1f, 2.0f);
        onChange.onConfigChanged();
    }

    public float getSmoothingIntensity() {
        return smoothingIntensity;
    }

    public void setSmoothingIntensity(float smoothingIntensity) {
        this.smoothingIntensity = Math.clamp(smoothingIntensity, 0.0f, 1.0f);
        onChange.onConfigChanged();
    }

    public long getNoiseSeed() {
        return noiseSeed;
    }

    public void setNoiseSeed(long noiseSeed) {
        this.noiseSeed = noiseSeed;
        onChange.onConfigChanged();
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
        this.noiseFrequency = Math.clamp(value, 0.005f, 0.2f);
        onChange.onConfigChanged();
    }

    public float getNoisePersistence() {
        return noisePersistence;
    }

    public void setNoisePersistence(float value) {
        this.noisePersistence = Math.clamp(value, 0.05f, 0.95f);
        onChange.onConfigChanged();
    }

    public int getNoiseOctaves() {
        return noiseOctaves;
    }

    public void setNoiseOctaves(int value) {
        this.noiseOctaves = Math.clamp(value, 1, 6);
        onChange.onConfigChanged();
    }

    public List<SurfaceConvertBlock> getSurfaceConvertBlocks() {
        return surfaceConvertBlocks;
    }

    public void ensureSurfaceConvertDefaults() {
        if (surfaceConvertBlocks == null) {
            surfaceConvertBlocks = new ArrayList<>();
        }
        if (surfaceConvertBlocks.isEmpty()) {
            surfaceConvertBlocks.add(new SurfaceConvertBlock("minecraft:grass_block", 100.0f));
        }
        if (surfaceConvertDistribution == null) {
            surfaceConvertDistribution = PushdozerConfig.SurfaceConvertDistribution.PATCHY;
        }
    }

    public boolean isConvertArtificialSurfaces() {
        return convertArtificialSurfaces;
    }

    public void setConvertArtificialSurfaces(boolean convertArtificialSurfaces) {
        this.convertArtificialSurfaces = convertArtificialSurfaces;
        onChange.onConfigChanged();
    }

    public PushdozerConfig.SurfaceConvertDistribution getSurfaceConvertDistribution() {
        return surfaceConvertDistribution == null
            ? PushdozerConfig.SurfaceConvertDistribution.PATCHY
            : surfaceConvertDistribution;
    }

    public void setSurfaceConvertDistribution(PushdozerConfig.SurfaceConvertDistribution distribution) {
        this.surfaceConvertDistribution = distribution == null
            ? PushdozerConfig.SurfaceConvertDistribution.PATCHY
            : distribution;
        onChange.onConfigChanged();
    }

    public int getSurfaceConvertMaxBelowSurfaceDepth() {
        return surfaceConvertMaxBelowSurfaceDepth;
    }

    public void setSurfaceConvertMaxBelowSurfaceDepth(int depth) {
        this.surfaceConvertMaxBelowSurfaceDepth = Math.clamp(depth, -1, 16);
        onChange.onConfigChanged();
    }
}
