package com.pushdozer.config;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import com.google.gson.annotations.Expose;
import com.pushdozer.PushdozerMod;
import com.pushdozer.config.domain.BrushConfig;
import com.pushdozer.config.domain.LegacyConfigMigration;
import com.pushdozer.config.domain.PlantingConfig;
import com.pushdozer.config.domain.PreviewConfig;
import com.pushdozer.config.domain.ShorelineConfig;
import com.pushdozer.config.domain.SurfaceConfig;
import com.pushdozer.network.ClientNetworkHandler;

import net.fabricmc.api.EnvType;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.block.Block;
import net.minecraft.text.Text;

public class PushdozerConfig {
    private static final String CONFIG_FILE_NAME = "pushdozer_config.json";
    private static final Gson GSON = new GsonBuilder()
        .setPrettyPrinting()
        .excludeFieldsWithoutExposeAnnotation()
        .create();
    public static final int MAX_OPERATION_DISTANCE = 99;

    public static final int MIN_BRUSH_RADIUS = BrushConfig.MIN_BRUSH_RADIUS;
    public static final int MAX_BRUSH_RADIUS = BrushConfig.MAX_BRUSH_RADIUS;

    public static int clampBrushSize(int size) {
        return BrushConfig.clampBrushSize(size);
    }

    public static boolean isBrushSizeAllowed(int size) {
        return BrushConfig.isBrushSizeAllowed(size);
    }

    public enum WorkMode {
        EXCAVATE("pushdozer.mode.excavate"),
        PLACE("pushdozer.mode.place"),
        SMOOTH("pushdozer.mode.smooth"),
        SMOOTH_RAISE("pushdozer.mode.smooth_raise"),
        SMOOTH_LOWER("pushdozer.mode.smooth_lower"),
        SURFACE_ROUGHEN("pushdozer.mode.surface_roughen"),
        ADAPTIVE_SMOOTH("pushdozer.mode.adaptive_smooth"),
        SURFACE_CONVERT("pushdozer.mode.surface_convert"),
        BONE_MEAL("pushdozer.mode.bone_meal"),
        BATCH_PLANT("pushdozer.mode.batch_plant"),
        SHORELINE_PROCESS("pushdozer.mode.shoreline_process");

        private final String translationKey;

        WorkMode(String translationKey) {
            this.translationKey = translationKey;
        }

        public Text getDisplayText() {
            return Text.translatable(translationKey);
        }
    }

    public enum SmoothVariant {
        ADAPTIVE,
        RAISE,
        LOWER;

        public Text getDisplayText() {
            return switch (this) {
                case ADAPTIVE -> Text.translatable("pushdozer.mode.adaptive_smooth");
                case RAISE -> Text.translatable("pushdozer.mode.smooth_raise");
                case LOWER -> Text.translatable("pushdozer.mode.smooth_lower");
            };
        }
    }

    public enum DisplayMode {
        NONE("pushdozer.display_mode.none"),
        WIREFRAME("pushdozer.display_mode.wireframe"),
        POINT_CLOUD("pushdozer.display_mode.point_cloud"),
        SURFACE("pushdozer.display_mode.surface");

        private final String translationKey;

        DisplayMode(String translationKey) {
            this.translationKey = translationKey;
        }

        public Text getDisplayText() {
            return Text.translatable(translationKey);
        }
    }

    public enum GeometryType {
        SPHERE("pushdozer.geometry.sphere"),
        BOX("pushdozer.geometry.box"),
        OCTAHEDRON("pushdozer.geometry.octahedron"),
        CYLINDER("pushdozer.geometry.cylinder"),
        CONE("pushdozer.geometry.cone"),
        ELLIPSOID("pushdozer.geometry.ellipsoid"),
        TETRAHEDRON("pushdozer.geometry.tetrahedron"),
        TRIANGULAR_PRISM("pushdozer.geometry.triangular_prism");

        private final String translationKey;

        GeometryType(String translationKey) {
            this.translationKey = translationKey;
        }

        public Text getDisplayText() {
            return Text.translatable(translationKey);
        }

        public String getShapeString() {
            return this.name().toLowerCase();
        }

        public static GeometryType fromString(String shapeString) {
            for (GeometryType type : values()) {
                if (type.name().equalsIgnoreCase(shapeString) ||
                    type.getShapeString().equalsIgnoreCase(shapeString)) {
                    return type;
                }
            }
            if ("sphere".equalsIgnoreCase(shapeString)) {
                return SPHERE;
            } else if ("box".equalsIgnoreCase(shapeString)) {
                return BOX;
            }
            return BOX;
        }
    }

    public enum HeightMode {
        FOLLOW_PLAYER,
        LOCKED_ONCE,
        NO_LIMIT,
        CUSTOM
    }

    public enum PlaceMode {
        ADAPTIVE_BIOME("pushdozer.place_mode.adaptive_biome"),
        NATURAL_BLOCK("pushdozer.place_mode.natural_block");

        private final String translationKey;

        PlaceMode(String translationKey) {
            this.translationKey = translationKey;
        }

        public Text getDisplayText() {
            return Text.translatable(translationKey);
        }
    }

    public enum PlantType {
        TREES("pushdozer.plant_type.trees"),
        FLOWERS("pushdozer.plant_type.flowers"),
        GRASS("pushdozer.plant_type.grass"),
        CUSTOM("pushdozer.plant_type.custom");

        private final String translationKey;

        PlantType(String translationKey) {
            this.translationKey = translationKey;
        }

        public Text getDisplayText() {
            return Text.translatable(translationKey);
        }
    }

    public enum ShorelineType {
        BEACH("pushdozer.shoreline_type.beach"),
        EMBANKMENT("pushdozer.shoreline_type.embankment"),
        ADAPTIVE("pushdozer.shoreline_type.adaptive"),
        MUDDY("pushdozer.shoreline_type.muddy"),
        ROCKY("pushdozer.shoreline_type.rocky"),
        CUSTOM("pushdozer.shoreline_type.custom");

        private final String translationKey;

        ShorelineType(String translationKey) {
            this.translationKey = translationKey;
        }

        public Text getDisplayText() {
            return Text.translatable(translationKey);
        }
    }

    public enum TreeSpecies {
        OAK, SPRUCE, BIRCH, JUNGLE, ACACIA, DARK_OAK, BIOME_ADAPTIVE;

        public Text getDisplayText() {
            return Text.translatable("pushdozer.tree_species." + this.name().toLowerCase());
        }
    }

    public enum FlowerGroup {
        PLAINS_FLOWERS, FOREST_FLOWERS, ALL_FLOWERS, BIOME_ADAPTIVE;

        public Text getDisplayText() {
            return Text.translatable("pushdozer.flower_group." + this.name().toLowerCase());
        }
    }

    @Expose
    private WorkMode workMode = WorkMode.EXCAVATE;
    @Expose
    private BrushConfig brush = new BrushConfig();
    @Expose
    private SurfaceConfig surface = new SurfaceConfig();
    @Expose
    private PlantingConfig planting = new PlantingConfig();
    @Expose
    private ShorelineConfig shoreline = new ShorelineConfig();
    @Expose
    private PreviewConfig preview = new PreviewConfig();

    public PushdozerConfig() {
        wireChangeListeners();
    }

    public BrushConfig getBrush() {
        return brush;
    }

    public SurfaceConfig getSurface() {
        return surface;
    }

    public PlantingConfig getPlanting() {
        return planting;
    }

    public ShorelineConfig getShoreline() {
        return shoreline;
    }

    public PreviewConfig getPreview() {
        return preview;
    }

    void wireChangeListeners() {
        com.pushdozer.config.domain.ConfigChangeNotifier notifier = this::notifyListeners;
        brush.setOnChange(notifier);
        surface.setOnChange(notifier);
        planting.setOnChange(notifier);
        shoreline.setOnChange(notifier);
        preview.setOnChange(notifier);
    }

    public WorkMode getWorkMode() {
        return workMode;
    }

    public void setWorkMode(WorkMode workMode) {
        this.workMode = workMode;
        notifyListeners();
    }

    public DisplayMode getDisplayMode() {
        return preview.getDisplayMode();
    }

    public void setDisplayMode(DisplayMode displayMode) {
        preview.setDisplayMode(displayMode);
        save();
        notifyListeners();
    }

    public int getMaxOperationDistance() {
        return preview.getMaxOperationDistance();
    }

    public void setMaxOperationDistance(int distance) {
        preview.setMaxOperationDistance(distance);
        notifyListeners();
    }

    public boolean isBlockBreakable(Block block) {
        return brush.isBlockBreakable(block);
    }

    public String getShape() {
        return brush.getShape();
    }

    public void setShape(String shape) {
        brush.setShape(shape);
    }

    public GeometryType getGeometryType() {
        return brush.getGeometryType();
    }

    public void setGeometryType(GeometryType geometryType) {
        brush.setGeometryType(geometryType);
    }

    public int getRadius() {
        return brush.getRadius();
    }

    public void setRadius(int radius) {
        brush.setRadius(radius);
    }

    public int getLargestBrushDimension() {
        return brush.getLargestBrushDimension();
    }

    public int getLength() {
        return brush.getLength();
    }

    public void setLength(int length) {
        brush.setLength(length);
    }

    public int getWidth() {
        return brush.getWidth();
    }

    public void setWidth(int width) {
        brush.setWidth(width);
    }

    public int getHeight() {
        return brush.getHeight();
    }

    public void setHeight(int height) {
        brush.setHeight(height);
    }

    public int getBoxHeight() {
        return brush.getBoxHeight();
    }

    public void setBoxHeight(int boxHeight) {
        brush.setBoxHeight(boxHeight);
    }

    public int getCylinderHeight() {
        return brush.getCylinderHeight();
    }

    public void setCylinderHeight(int cylinderHeight) {
        brush.setCylinderHeight(cylinderHeight);
    }

    public int getConeHeight() {
        return brush.getConeHeight();
    }

    public void setConeHeight(int coneHeight) {
        brush.setConeHeight(coneHeight);
    }

    public int getEllipsoidHeight() {
        return brush.getEllipsoidHeight();
    }

    public void setEllipsoidHeight(int ellipsoidHeight) {
        brush.setEllipsoidHeight(ellipsoidHeight);
    }

    public int getSphereRadius() {
        return brush.getSphereRadius();
    }

    public void setSphereRadius(int sphereRadius) {
        brush.setSphereRadius(sphereRadius);
    }

    public int getCylinderRadius() {
        return brush.getCylinderRadius();
    }

    public void setCylinderRadius(int cylinderRadius) {
        brush.setCylinderRadius(cylinderRadius);
    }

    public int getConeRadius() {
        return brush.getConeRadius();
    }

    public void setConeRadius(int coneRadius) {
        brush.setConeRadius(coneRadius);
    }

    public int getOctahedronRadius() {
        return brush.getOctahedronRadius();
    }

    public void setOctahedronRadius(int octahedronRadius) {
        brush.setOctahedronRadius(octahedronRadius);
    }

    public int getTetrahedronEdgeLength() {
        return brush.getTetrahedronEdgeLength();
    }

    public void setTetrahedronEdgeLength(int tetrahedronEdgeLength) {
        brush.setTetrahedronEdgeLength(tetrahedronEdgeLength);
    }

    public int getTriangularPrismSideLength() {
        return brush.getTriangularPrismSideLength();
    }

    public void setTriangularPrismSideLength(int triangularPrismSideLength) {
        brush.setTriangularPrismSideLength(triangularPrismSideLength);
    }

    public int getTriangularPrismHeight() {
        return brush.getTriangularPrismHeight();
    }

    public void setTriangularPrismHeight(int triangularPrismHeight) {
        brush.setTriangularPrismHeight(triangularPrismHeight);
    }

    public int getLockedHeight() {
        return brush.getLockedHeight();
    }

    public void setLockedHeight(int height) {
        brush.setLockedHeight(height);
    }

    public HeightMode getHeightMode() {
        return brush.getHeightMode();
    }

    public void setHeightMode(HeightMode heightMode) {
        brush.setHeightMode(heightMode);
    }

    public boolean isLockedOnceMode() {
        return brush.isLockedOnceMode();
    }

    public void setLockedOnceMode(boolean lockedOnceMode) {
        brush.setLockedOnceMode(lockedOnceMode);
    }

    public PlaceMode getPlaceMode() {
        return surface.getPlaceMode();
    }

    public void setPlaceMode(PlaceMode placeMode) {
        surface.setPlaceMode(placeMode);
    }

    public void setSelectedNaturalBlockId(String blockId) {
        surface.setSelectedNaturalBlockId(blockId);
    }

    public Block getSelectedNaturalBlock() {
        return surface.getSelectedNaturalBlock();
    }

    public float getSmoothStrength() {
        return surface.getSmoothStrength();
    }

    public void setSmoothStrength(float smoothStrength) {
        surface.setSmoothStrength(smoothStrength);
    }

    public SmoothVariant getSmoothVariant() {
        return surface.getSmoothVariant();
    }

    public void setSmoothVariant(SmoothVariant variant) {
        surface.setSmoothVariant(variant);
    }

    public float getRoughnessStrength() {
        return surface.getRoughnessStrength();
    }

    public void setRoughnessStrength(float roughnessStrength) {
        surface.setRoughnessStrength(roughnessStrength);
    }

    public float getSmoothingIntensity() {
        return surface.getSmoothingIntensity();
    }

    public void setSmoothingIntensity(float smoothingIntensity) {
        surface.setSmoothingIntensity(smoothingIntensity);
    }

    public long getNoiseSeed() {
        return surface.getNoiseSeed();
    }

    public boolean isNoiseAutoScale() {
        return surface.isNoiseAutoScale();
    }

    public void setNoiseAutoScale(boolean auto) {
        surface.setNoiseAutoScale(auto);
    }

    public float getNoiseFrequency() {
        return surface.getNoiseFrequency();
    }

    public void setNoiseFrequency(float value) {
        surface.setNoiseFrequency(value);
    }

    public float getNoisePersistence() {
        return surface.getNoisePersistence();
    }

    public void setNoisePersistence(float value) {
        surface.setNoisePersistence(value);
    }

    public int getNoiseOctaves() {
        return surface.getNoiseOctaves();
    }

    public void setNoiseOctaves(int value) {
        surface.setNoiseOctaves(value);
    }

    public List<SurfaceConfig.SurfaceConvertBlock> getSurfaceConvertBlocks() {
        return surface.getSurfaceConvertBlocks();
    }

    public PlantType getPlantType() {
        return planting.getPlantType();
    }

    public void setPlantType(PlantType plantType) {
        planting.setPlantType(plantType);
    }

    public float getPlantDensity() {
        return planting.getPlantDensity();
    }

    public void setPlantDensity(float plantDensity) {
        planting.setPlantDensity(plantDensity);
    }

    public ShorelineType getShorelineType() {
        return shoreline.getShorelineType();
    }

    public void setShorelineType(ShorelineType shorelineType) {
        shoreline.setShorelineType(shorelineType);
    }

    public int getShorelineWidth() {
        return shoreline.getShorelineWidth();
    }

    public void setShorelineWidth(int shorelineWidth) {
        shoreline.setShorelineWidth(shorelineWidth);
    }

    public boolean isPlantVegetationEnabled() {
        return shoreline.isPlantVegetationEnabled();
    }

    public void setPlantVegetationEnabled(boolean enabled) {
        shoreline.setPlantVegetationEnabled(enabled);
    }

    public float getVegetationDensity() {
        return shoreline.getVegetationDensity();
    }

    public void setVegetationDensity(float density) {
        shoreline.setVegetationDensity(density);
    }

    public void setCustomShorelineBlocks(Set<String> blockIds) {
        shoreline.setCustomShorelineBlocks(blockIds);
    }

    public void setCustomShorelinePlants(Set<String> plantIds) {
        shoreline.setCustomShorelinePlants(plantIds);
    }

    public List<Block> getCustomShorelineBlockList() {
        return shoreline.getCustomShorelineBlockList();
    }

    public List<Block> getCustomShorelinePlantList() {
        return shoreline.getCustomShorelinePlantList();
    }

    public boolean isShorelineHeightAboveEnabled() {
        return shoreline.isShorelineHeightAboveEnabled();
    }

    public void setShorelineHeightAboveEnabled(boolean enabled) {
        shoreline.setShorelineHeightAboveEnabled(enabled);
    }

    public boolean isShorelineHeightBelowEnabled() {
        return shoreline.isShorelineHeightBelowEnabled();
    }

    public void setShorelineHeightBelowEnabled(boolean enabled) {
        shoreline.setShorelineHeightBelowEnabled(enabled);
    }

    public TreeSpecies getSelectedTree() {
        return planting.getSelectedTree();
    }

    public void setSelectedTree(TreeSpecies selectedTree) {
        planting.setSelectedTree(selectedTree);
    }

    public FlowerGroup getSelectedFlowerGroup() {
        return planting.getSelectedFlowerGroup();
    }

    public void setSelectedFlowerGroup(FlowerGroup selectedFlowerGroup) {
        planting.setSelectedFlowerGroup(selectedFlowerGroup);
    }

    public float getClusterScale() {
        return planting.getClusterScale();
    }

    public void setClusterScale(float clusterScale) {
        planting.setClusterScale(clusterScale);
    }

    public List<Block> getCustomPlantBlocks() {
        return planting.getCustomPlantBlocks();
    }

    public void setCustomPlantBlocks(List<Block> blocks) {
        planting.setCustomPlantBlocks(blocks);
    }

    public List<String> getIgnoredBlockIds() {
        return brush.getIgnoredBlockIds();
    }

    public boolean isBlockIgnored(Block block) {
        return brush.isBlockIgnored(block);
    }

    public List<Block> getBreakableBlocks() {
        return brush.getBreakableBlocks();
    }

    public void setBreakableBlocks(List<Block> blocks) {
        brush.setBreakableBlocks(blocks);
    }

    public void notifyListeners() {
        // Implement config change notification logic
    }

    public void save() {
        Path configDir = FabricLoader.getInstance().getConfigDir();
        File configFile = configDir.resolve(CONFIG_FILE_NAME).toFile();

        try (FileWriter writer = new FileWriter(configFile)) {
            GSON.toJson(this, writer);
            PushdozerMod.LOGGER.info("Pushdozer configuration is saved");
        } catch (IOException e) {
            PushdozerMod.LOGGER.error("An error occurred while saving the Pushdozer configuration", e);
        }

        if (FabricLoader.getInstance().getEnvironmentType() == EnvType.CLIENT) {
            ClientNetworkHandler.sendConfigSync(this);
        }
    }

    public String toJson() {
        return GSON.toJson(this);
    }

    public static PushdozerConfig fromJson(String json) {
        if (json == null || json.isBlank()) {
            PushdozerConfig config = new PushdozerConfig();
            LegacyConfigMigration.ensureDefaults(config);
            return config;
        }

        try {
            JsonObject obj = JsonParser.parseString(json).getAsJsonObject();
            PushdozerConfig config;
            if (LegacyConfigMigration.isLegacyFlatFormat(obj)) {
                config = new PushdozerConfig();
                LegacyConfigMigration.applyFlatJson(config, obj);
            } else {
                config = GSON.fromJson(json, PushdozerConfig.class);
                if (config == null) {
                    config = new PushdozerConfig();
                } else {
                    config.wireChangeListeners();
                }
            }
            LegacyConfigMigration.ensureDefaults(config);
            return config;
        } catch (JsonSyntaxException e) {
            PushdozerMod.LOGGER.error("Failed to parse Pushdozer configuration JSON", e);
            PushdozerConfig config = new PushdozerConfig();
            LegacyConfigMigration.ensureDefaults(config);
            return config;
        }
    }

    public static void ensureDefaults(PushdozerConfig config) {
        LegacyConfigMigration.ensureDefaults(config);
    }

    public static PushdozerConfig load() {
        Path configDir = FabricLoader.getInstance().getConfigDir();
        File configFile = configDir.resolve(CONFIG_FILE_NAME).toFile();

        PushdozerConfig config = null;

        if (configFile.exists()) {
            try (FileReader reader = new FileReader(configFile)) {
                JsonObject obj = JsonParser.parseReader(reader).getAsJsonObject();
                if (LegacyConfigMigration.isLegacyFlatFormat(obj)) {
                    config = new PushdozerConfig();
                    LegacyConfigMigration.applyFlatJson(config, obj);
                } else {
                    config = GSON.fromJson(obj, PushdozerConfig.class);
                }
                PushdozerMod.LOGGER.info("Configuration has been loaded");
            } catch (IOException e) {
                PushdozerMod.LOGGER.error("An error occurred while loading the Pushdozer configuration", e);
            }
        }

        if (config == null) {
            config = new PushdozerConfig();
            PushdozerMod.LOGGER.info("Creating new default configuration");
        } else {
            config.wireChangeListeners();
        }

        LegacyConfigMigration.ensureDefaults(config);

        try {
            if (!configFile.exists()) {
                configFile.getParentFile().mkdirs();
                configFile.createNewFile();
            }
            try (FileWriter writer = new FileWriter(configFile)) {
                GSON.toJson(config, writer);
            }
        } catch (IOException e) {
            PushdozerMod.LOGGER.error("Failed to save default configuration", e);
        }

        return config;
    }

    private static PushdozerConfig instance = null;

    public static PushdozerConfig getInstance() {
        if (instance == null) {
            instance = load();
        }
        return instance;
    }
}
