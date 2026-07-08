package com.pushdozer.config.domain;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.annotations.Expose;
import com.pushdozer.config.PushdozerConfig;

/**
 * Handles migration from the legacy flat JSON format to nested domain sub-configs.
 */
public final class LegacyConfigMigration {
    private static final Gson GSON = new GsonBuilder()
        .excludeFieldsWithoutExposeAnnotation()
        .create();

    private static final String[] LEGACY_FLAT_KEYS = {
        "radius", "length", "width", "height", "shape", "geometryType",
        "displayMode", "maxOperationDistance", "placeMode", "plantType",
        "shorelineType", "workMode", "smoothStrength", "smoothVariant"
    };

    private LegacyConfigMigration() {}

    public static boolean isLegacyFlatFormat(JsonObject obj) {
        if (obj.has("brush")) {
            return false;
        }
        for (String key : LEGACY_FLAT_KEYS) {
            if (obj.has(key)) {
                return true;
            }
        }
        return false;
    }

    public static void applyFlatJson(PushdozerConfig config, JsonObject obj) {
        FlatConfigSnapshot flat = GSON.fromJson(obj, FlatConfigSnapshot.class);
        if (flat == null) {
            return;
        }

        if (flat.workMode != null) {
            config.setWorkMode(flat.workMode);
        }

        copyBrushFields(flat, config.getBrush());

        SurfaceConfig surface = config.getSurface();
        copySurfaceFields(flat, surface);

        PlantingConfig planting = config.getPlanting();
        copyPlantingFields(flat, planting);

        ShorelineConfig shoreline = config.getShoreline();
        copyShorelineFields(flat, shoreline);

        PreviewConfig preview = config.getPreview();
        copyPreviewFields(flat, preview);
    }

    private static void copyBrushFields(FlatConfigSnapshot flat, BrushConfig brush) {
        if (flat.breakableBlockIds != null) {
            BrushFieldHelper.setBreakableBlockIds(brush, flat.breakableBlockIds);
        }
        if (flat.ignoredBlockIds != null) {
            brush.getIgnoredBlockIds().clear();
            brush.getIgnoredBlockIds().addAll(flat.ignoredBlockIds);
            brush.markIgnoredBlocksCacheDirty();
        }
        if (flat.shape != null) brush.setShape(flat.shape);
        if (flat.geometryType != null) brush.setGeometryType(flat.geometryType);
        if (flat.radius != null) brush.setRadius(flat.radius);
        if (flat.length != null) brush.setLength(flat.length);
        if (flat.width != null) brush.setWidth(flat.width);
        if (flat.height != null) brush.setHeight(flat.height);
        if (flat.sphereRadius != null) brush.setSphereRadius(flat.sphereRadius);
        if (flat.cylinderRadius != null) brush.setCylinderRadius(flat.cylinderRadius);
        if (flat.coneRadius != null) brush.setConeRadius(flat.coneRadius);
        if (flat.octahedronRadius != null) brush.setOctahedronRadius(flat.octahedronRadius);
        if (flat.tetrahedronEdgeLength != null) brush.setTetrahedronEdgeLength(flat.tetrahedronEdgeLength);
        if (flat.triangularPrismSideLength != null) brush.setTriangularPrismSideLength(flat.triangularPrismSideLength);
        if (flat.boxHeight != null) brush.setBoxHeight(flat.boxHeight);
        if (flat.cylinderHeight != null) brush.setCylinderHeight(flat.cylinderHeight);
        if (flat.coneHeight != null) brush.setConeHeight(flat.coneHeight);
        if (flat.sphereHeight != null) brush.setSphereHeight(flat.sphereHeight);
        if (flat.octahedronHeight != null) brush.setOctahedronHeight(flat.octahedronHeight);
        if (flat.tetrahedronHeight != null) brush.setTetrahedronHeight(flat.tetrahedronHeight);
        if (flat.triangularPrismHeight != null) brush.setTriangularPrismHeight(flat.triangularPrismHeight);
        if (flat.ellipsoidHeight != null) brush.setEllipsoidHeight(flat.ellipsoidHeight);
        if (flat.lockedHeight != null) brush.setLockedHeight(flat.lockedHeight);
        if (flat.heightMode != null) brush.setHeightMode(flat.heightMode);
        if (flat.isLockedOnceMode != null) brush.setLockedOnceMode(flat.isLockedOnceMode);
    }

    private static void copySurfaceFields(FlatConfigSnapshot flat, SurfaceConfig surface) {
        if (flat.placeMode != null) surface.setPlaceMode(flat.placeMode);
        if (flat.selectedNaturalBlockId != null) surface.setSelectedNaturalBlockId(flat.selectedNaturalBlockId);
        if (flat.smoothStrength != null) surface.setSmoothStrength(flat.smoothStrength);
        if (flat.smoothVariant != null) surface.setSmoothVariant(flat.smoothVariant);
        if (flat.roughnessStrength != null) surface.setRoughnessStrength(flat.roughnessStrength);
        if (flat.smoothingIntensity != null) surface.setSmoothingIntensity(flat.smoothingIntensity);
        if (flat.noiseSeed != null) surface.setNoiseSeed(flat.noiseSeed);
        if (flat.noiseAutoScale != null) surface.setNoiseAutoScale(flat.noiseAutoScale);
        if (flat.noiseFrequency != null) surface.setNoiseFrequency(flat.noiseFrequency);
        if (flat.noisePersistence != null) surface.setNoisePersistence(flat.noisePersistence);
        if (flat.noiseOctaves != null) surface.setNoiseOctaves(flat.noiseOctaves);
        if (flat.surfaceConvertBlocks != null) {
            surface.getSurfaceConvertBlocks().clear();
            surface.getSurfaceConvertBlocks().addAll(flat.surfaceConvertBlocks);
        }
    }

    private static void copyPlantingFields(FlatConfigSnapshot flat, PlantingConfig planting) {
        if (flat.plantType != null) planting.setPlantType(flat.plantType);
        if (flat.customPlantBlockIds != null) {
            PlantingFieldHelper.setCustomPlantBlockIds(planting, flat.customPlantBlockIds);
        }
        if (flat.plantDensity != null) planting.setPlantDensity(flat.plantDensity);
        if (flat.selectedTree != null) planting.setSelectedTree(flat.selectedTree);
        if (flat.selectedFlowerGroup != null) planting.setSelectedFlowerGroup(flat.selectedFlowerGroup);
        if (flat.respectBiomes != null) planting.setRespectBiomes(flat.respectBiomes);
        if (flat.clusterScale != null) planting.setClusterScale(flat.clusterScale);
    }

    private static void copyShorelineFields(FlatConfigSnapshot flat, ShorelineConfig shoreline) {
        if (flat.shorelineType != null) shoreline.setShorelineType(flat.shorelineType);
        if (flat.shorelineWidth != null) shoreline.setShorelineWidth(flat.shorelineWidth);
        if (flat.plantVegetationEnabled != null) shoreline.setPlantVegetationEnabled(flat.plantVegetationEnabled);
        if (flat.vegetationDensity != null) shoreline.setVegetationDensity(flat.vegetationDensity);
        if (flat.customShorelineBlocks != null) shoreline.setCustomShorelineBlocks(flat.customShorelineBlocks);
        if (flat.customShorelinePlants != null) shoreline.setCustomShorelinePlants(flat.customShorelinePlants);
        if (flat.shorelineHeightAboveEnabled != null) shoreline.setShorelineHeightAboveEnabled(flat.shorelineHeightAboveEnabled);
        if (flat.shorelineHeightBelowEnabled != null) shoreline.setShorelineHeightBelowEnabled(flat.shorelineHeightBelowEnabled);
    }

    private static void copyPreviewFields(FlatConfigSnapshot flat, PreviewConfig preview) {
        if (flat.displayMode != null) preview.setDisplayMode(flat.displayMode);
        if (flat.maxOperationDistance != null) preview.setMaxOperationDistance(flat.maxOperationDistance);
    }

    public static void ensureDefaults(PushdozerConfig config) {
        if (config.getWorkMode() == null) {
            config.setWorkMode(PushdozerConfig.WorkMode.EXCAVATE);
        }
        if (config.getDisplayMode() == null) {
            config.setDisplayMode(PushdozerConfig.DisplayMode.WIREFRAME);
        }
        if (config.getHeightMode() == null) {
            if (config.getLockedHeight() != 0) {
                config.setHeightMode(PushdozerConfig.HeightMode.CUSTOM);
            } else {
                config.setHeightMode(PushdozerConfig.HeightMode.NO_LIMIT);
            }
        }
        if (config.getPlantType() == null) {
            config.setPlantType(PushdozerConfig.PlantType.TREES);
        }

        PushdozerConfig.WorkMode wm = config.getWorkMode();
        if (wm == PushdozerConfig.WorkMode.SMOOTH_RAISE) {
            config.setSmoothVariant(PushdozerConfig.SmoothVariant.RAISE);
            config.setWorkMode(PushdozerConfig.WorkMode.SMOOTH);
        } else if (wm == PushdozerConfig.WorkMode.SMOOTH_LOWER) {
            config.setSmoothVariant(PushdozerConfig.SmoothVariant.LOWER);
            config.setWorkMode(PushdozerConfig.WorkMode.SMOOTH);
        } else if (wm == PushdozerConfig.WorkMode.ADAPTIVE_SMOOTH) {
            config.setSmoothVariant(PushdozerConfig.SmoothVariant.ADAPTIVE);
            config.setWorkMode(PushdozerConfig.WorkMode.SMOOTH);
        }

        clampBrushDimensions(config);
        config.getBrush().markIgnoredBlocksCacheDirty();
        config.getBrush().rebuildIgnoredBlocksCache();
    }

    public static void clampBrushDimensions(PushdozerConfig config) {
        config.getBrush().clampBrushDimensions();
    }

    /** Gson DTO mirroring the legacy flat JSON layout. */
    private static class FlatConfigSnapshot {
        @Expose PushdozerConfig.WorkMode workMode;
        @Expose Set<String> breakableBlockIds;
        @Expose List<String> ignoredBlockIds;
        @Expose String shape;
        @Expose PushdozerConfig.GeometryType geometryType;
        @Expose Integer radius;
        @Expose Integer length;
        @Expose Integer width;
        @Expose Integer height;
        @Expose Integer sphereRadius;
        @Expose Integer cylinderRadius;
        @Expose Integer coneRadius;
        @Expose Integer octahedronRadius;
        @Expose Integer tetrahedronEdgeLength;
        @Expose Integer triangularPrismSideLength;
        @Expose Integer boxHeight;
        @Expose Integer cylinderHeight;
        @Expose Integer coneHeight;
        @Expose Integer sphereHeight;
        @Expose Integer octahedronHeight;
        @Expose Integer tetrahedronHeight;
        @Expose Integer triangularPrismHeight;
        @Expose Integer ellipsoidHeight;
        @Expose Integer lockedHeight;
        @Expose PushdozerConfig.HeightMode heightMode;
        @Expose Boolean isLockedOnceMode;
        @Expose PushdozerConfig.PlaceMode placeMode;
        @Expose String selectedNaturalBlockId;
        @Expose Float smoothStrength;
        @Expose PushdozerConfig.SmoothVariant smoothVariant;
        @Expose Float roughnessStrength;
        @Expose Float smoothingIntensity;
        @Expose Long noiseSeed;
        @Expose Boolean noiseAutoScale;
        @Expose Float noiseFrequency;
        @Expose Float noisePersistence;
        @Expose Integer noiseOctaves;
        @Expose List<SurfaceConfig.SurfaceConvertBlock> surfaceConvertBlocks;
        @Expose PushdozerConfig.PlantType plantType;
        @Expose Set<String> customPlantBlockIds;
        @Expose Float plantDensity;
        @Expose PushdozerConfig.TreeSpecies selectedTree;
        @Expose PushdozerConfig.FlowerGroup selectedFlowerGroup;
        @Expose Boolean respectBiomes;
        @Expose Float clusterScale;
        @Expose PushdozerConfig.ShorelineType shorelineType;
        @Expose Integer shorelineWidth;
        @Expose Boolean plantVegetationEnabled;
        @Expose Float vegetationDensity;
        @Expose Set<String> customShorelineBlocks;
        @Expose Set<String> customShorelinePlants;
        @Expose Boolean shorelineHeightAboveEnabled;
        @Expose Boolean shorelineHeightBelowEnabled;
        @Expose PushdozerConfig.DisplayMode displayMode;
        @Expose Integer maxOperationDistance;
    }

    /** Package-private helpers for fields without public setters. */
    static final class BrushFieldHelper {
        private BrushFieldHelper() {}

        static void setBreakableBlockIds(BrushConfig brush, Set<String> ids) {
            brush.applyBreakableBlockIds(ids);
        }
    }

    static final class PlantingFieldHelper {
        private PlantingFieldHelper() {}

        static void setCustomPlantBlockIds(PlantingConfig planting, Set<String> ids) {
            planting.applyCustomPlantBlockIds(ids);
        }
    }
}
