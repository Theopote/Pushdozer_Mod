package com.pushdozer.config.domain;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import com.pushdozer.PushdozerMod;
import com.pushdozer.config.PushdozerConfig;
import org.jetbrains.annotations.NotNull;

/**
 * Legacy flat JSON migration and default value fixes.
 */
public final class LegacyConfigMigration {
    private static final Gson GSON = new Gson();

    private LegacyConfigMigration() {
    }

    public static boolean isLegacyFlatFormat(JsonObject obj) {
        return !obj.has("brush")
            && (obj.has("radius")
            || obj.has("geometryType")
            || obj.has("displayMode")
            || obj.has("plantType")
            || obj.has("shorelineType")
            || obj.has("smoothStrength"));
    }

    public static void applyFlatJson(PushdozerConfig config, JsonObject obj) {
        try {
            LegacyFlatSnapshot snapshot = GSON.fromJson(obj, LegacyFlatSnapshot.class);
            if (snapshot == null) {
                return;
            }
            applySnapshot(config, snapshot);
        } catch (JsonSyntaxException e) {
            PushdozerMod.LOGGER.error("Failed to migrate legacy flat Pushdozer configuration", e);
        }
    }

    private static void applySnapshot(PushdozerConfig config, LegacyFlatSnapshot snapshot) {
        if (snapshot.workMode != null) {
            config.setWorkMode(snapshot.workMode);
        }

        PreviewConfig preview = config.getPreview();
        if (snapshot.displayMode != null) {
            preview.setDisplayMode(snapshot.displayMode);
        }
        preview.setMaxOperationDistance(snapshot.maxOperationDistance);

        BrushConfig brush = config.getBrush();
        if (snapshot.breakableBlockIds != null) {
            brush.setBreakableBlockIds(snapshot.breakableBlockIds);
        }
        if (snapshot.ignoredBlockIds != null) {
            brush.setIgnoredBlockIds(snapshot.ignoredBlockIds);
        }
        if (snapshot.shape != null) {
            brush.setShape(snapshot.shape);
        }
        if (snapshot.geometryType != null) {
            brush.setGeometryType(snapshot.geometryType);
        }
        brush.setRadius(snapshot.radius);
        brush.setLength(snapshot.length);
        brush.setWidth(snapshot.width);
        brush.setHeight(snapshot.height);
        brush.setSphereRadius(snapshot.sphereRadius);
        brush.setCylinderRadius(snapshot.cylinderRadius);
        brush.setConeRadius(snapshot.coneRadius);
        brush.setOctahedronRadius(snapshot.octahedronRadius);
        brush.setTetrahedronEdgeLength(snapshot.tetrahedronEdgeLength);
        brush.setTriangularPrismSideLength(snapshot.triangularPrismSideLength);
        brush.setBoxHeight(snapshot.boxHeight);
        brush.setCylinderHeight(snapshot.cylinderHeight);
        brush.setConeHeight(snapshot.coneHeight);
        brush.setSphereHeight(snapshot.sphereHeight);
        brush.setOctahedronHeight(snapshot.octahedronHeight);
        brush.setTetrahedronHeight(snapshot.tetrahedronHeight);
        brush.setTriangularPrismHeight(snapshot.triangularPrismHeight);
        brush.setEllipsoidHeight(snapshot.ellipsoidHeight);
        brush.setLockedHeight(snapshot.lockedHeight);
        if (snapshot.heightMode != null) {
            brush.setHeightMode(snapshot.heightMode);
        }
        brush.setLockedOnceMode(snapshot.isLockedOnceMode);

        SurfaceConfig surface = getSurfaceConfig(config, snapshot);
        if (snapshot.surfaceConvertBlocks != null) {
            surface.getSurfaceConvertBlocks().clear();
            surface.getSurfaceConvertBlocks().addAll(snapshot.surfaceConvertBlocks);
        }

        PlantingConfig planting = config.getPlanting();
        if (snapshot.plantType != null) {
            planting.setPlantType(snapshot.plantType);
        }
        if (snapshot.customPlantBlockIds != null) {
            planting.setCustomPlantBlockIds(snapshot.customPlantBlockIds);
        }
        planting.setPlantDensity(snapshot.plantDensity);
        if (snapshot.selectedTree != null) {
            planting.setSelectedTree(snapshot.selectedTree);
        }
        if (snapshot.selectedFlowerGroup != null) {
            planting.setSelectedFlowerGroup(snapshot.selectedFlowerGroup);
        }
        planting.setRespectBiomes(snapshot.respectBiomes);
        planting.setClusterScale(snapshot.clusterScale);

        ShorelineConfig shoreline = config.getShoreline();
        if (snapshot.shorelineType != null) {
            shoreline.setShorelineType(snapshot.shorelineType);
        }
        shoreline.setShorelineWidth(snapshot.shorelineWidth);
        shoreline.setPlantVegetationEnabled(snapshot.plantVegetationEnabled);
        shoreline.setVegetationDensity(snapshot.vegetationDensity);
        if (snapshot.customShorelineBlocks != null) {
            shoreline.setCustomShorelineBlocks(snapshot.customShorelineBlocks);
        }
        if (snapshot.customShorelinePlants != null) {
            shoreline.setCustomShorelinePlants(snapshot.customShorelinePlants);
        }
        shoreline.setShorelineHeightAboveEnabled(snapshot.shorelineHeightAboveEnabled);
        shoreline.setShorelineHeightBelowEnabled(snapshot.shorelineHeightBelowEnabled);
    }

    private static @NotNull SurfaceConfig getSurfaceConfig(PushdozerConfig config, LegacyFlatSnapshot snapshot) {
        SurfaceConfig surface = config.getSurface();
        if (snapshot.placeMode != null) {
            surface.setPlaceMode(snapshot.placeMode);
        }
        if (snapshot.selectedNaturalBlockId != null) {
            surface.setSelectedNaturalBlockId(snapshot.selectedNaturalBlockId);
        }
        surface.setSmoothStrength(snapshot.smoothStrength);
        if (snapshot.smoothVariant != null) {
            surface.setSmoothVariant(snapshot.smoothVariant);
        }
        surface.setRoughnessStrength(snapshot.roughnessStrength);
        surface.setSmoothingIntensity(snapshot.smoothingIntensity);
        surface.setNoiseAutoScale(snapshot.noiseAutoScale);
        surface.setNoiseFrequency(snapshot.noiseFrequency);
        surface.setNoisePersistence(snapshot.noisePersistence);
        surface.setNoiseOctaves(snapshot.noiseOctaves);
        return surface;
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

        config.getBrush().clampAllDimensions();
        config.getBrush().markIgnoredBlocksCacheDirty();
        config.getBrush().rebuildIgnoredBlocksCache();
        config.getSurface().ensureSurfaceConvertDefaults();
    }

    /** Legacy flat JSON field snapshot, used only for migration. */
    private static final class LegacyFlatSnapshot {
        PushdozerConfig.WorkMode workMode;
        PushdozerConfig.DisplayMode displayMode;
        int maxOperationDistance = 20;
        java.util.Set<String> breakableBlockIds;
        java.util.List<String> ignoredBlockIds;
        String shape;
        PushdozerConfig.GeometryType geometryType;
        int radius = 5;
        int length = 5;
        int width = 5;
        int height = 5;
        int sphereRadius = 5;
        int cylinderRadius = 5;
        int coneRadius = 5;
        int octahedronRadius = 5;
        int tetrahedronEdgeLength = 5;
        int triangularPrismSideLength = 5;
        int boxHeight = 5;
        int cylinderHeight = 5;
        int coneHeight = 5;
        int sphereHeight = 5;
        int octahedronHeight = 5;
        int tetrahedronHeight = 5;
        int triangularPrismHeight = 5;
        int ellipsoidHeight = 5;
        int lockedHeight;
        PushdozerConfig.HeightMode heightMode;
        boolean isLockedOnceMode;
        PushdozerConfig.PlaceMode placeMode;
        String selectedNaturalBlockId;
        float smoothStrength = 0.5f;
        PushdozerConfig.SmoothVariant smoothVariant;
        float roughnessStrength = 0.5f;
        float smoothingIntensity = 0.5f;
        boolean noiseAutoScale = true;
        float noiseFrequency = 0.02f;
        float noisePersistence = 0.5f;
        int noiseOctaves = 4;
        java.util.List<SurfaceConfig.SurfaceConvertBlock> surfaceConvertBlocks;
        PushdozerConfig.PlantType plantType;
        java.util.Set<String> customPlantBlockIds;
        float plantDensity = 0.3f;
        PushdozerConfig.TreeSpecies selectedTree;
        PushdozerConfig.FlowerGroup selectedFlowerGroup;
        boolean respectBiomes = true;
        float clusterScale = 0.05f;
        PushdozerConfig.ShorelineType shorelineType;
        int shorelineWidth = 3;
        boolean plantVegetationEnabled = true;
        float vegetationDensity = 0.1f;
        java.util.Set<String> customShorelineBlocks;
        java.util.Set<String> customShorelinePlants;
        boolean shorelineHeightAboveEnabled;
        boolean shorelineHeightBelowEnabled;
    }
}
