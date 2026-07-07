package com.pushdozer.config;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.annotations.Expose;
import com.pushdozer.PushdozerMod;
import com.pushdozer.network.ClientNetworkHandler;

import net.fabricmc.api.EnvType;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

public class PushdozerConfig {
    private static final String CONFIG_FILE_NAME = "pushdozer_config.json";
    private static final Gson GSON = new GsonBuilder()
        .setPrettyPrinting()
        .excludeFieldsWithoutExposeAnnotation()
        .create();
    public static final int MAX_OPERATION_DISTANCE = 99;
    /** 笔刷几何尺寸下限（与 UI 滑块一致） */
    public static final int MIN_BRUSH_RADIUS = 1;
    /** 笔刷几何尺寸上限（与 UI 滑块一致；半径 64 的球体约 109 万方块） */
    public static final int MAX_BRUSH_RADIUS = 64;

    public static int clampBrushSize(int size) {
        return Math.max(MIN_BRUSH_RADIUS, Math.min(MAX_BRUSH_RADIUS, size));
    }

    public static boolean isBrushSizeAllowed(int size) {
        return size >= MIN_BRUSH_RADIUS && size <= MAX_BRUSH_RADIUS;
    }

    public enum WorkMode {
        EXCAVATE("pushdozer.mode.excavate"),
        PLACE("pushdozer.mode.place"),
        // 新增统一的平滑模式（合并自适应/提升/降低）
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

    // 平滑模式的具体变体（用于在统一的平滑模式下选择算法）
    public enum SmoothVariant {
        ADAPTIVE, // 自适应平滑
        RAISE,    // 平滑提升
        LOWER;    // 平滑降低

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
         POINT_CLOUD("pushdozer.display_mode.point_cloud");

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
            // 向后兼容旧的形状名称
            if ("sphere".equalsIgnoreCase(shapeString)) {
                return SPHERE;
            } else if ("box".equalsIgnoreCase(shapeString)) {
                return BOX;
            }
            return BOX; // 默认值
        }
    }

    public enum HeightMode {
        FOLLOW_PLAYER,    // 跟随玩家标高
        LOCKED_ONCE,      // 锁定到玩家标高（一次性）
        NO_LIMIT,         // 标高不限
        CUSTOM            // 自定义标高
    }

    @Expose
    private WorkMode workMode = WorkMode.EXCAVATE;
    @Expose
    private DisplayMode displayMode = DisplayMode.WIREFRAME; // 确保有一个默认值
    @Expose
    private int maxOperationDistance = 20; // 默认值
    @Expose
    private Set<String> breakableBlockIds = new HashSet<>();
    @Expose
    private List<String> ignoredBlockIds = new ArrayList<>(List.of(
        //"minecraft:grass",
        //"minecraft:fern",
        //"minecraft:dead_bush",
        //"minecraft:sapling",
        //"minecraft:seagrass",
        //"minecraft:tall_seagrass",
        //"minecraft:tall_grass"
    ));
    
    // 缓存的忽略方块集合，用于高性能检查
    @Expose(serialize = false, deserialize = false)
    private Set<Block> ignoredBlocks = new HashSet<>();
    
    // 标记是否需要重新构建缓存
    @Expose(serialize = false, deserialize = false)
    private boolean ignoredBlocksCacheDirty = true;
    
    @Expose
    private String shape = "Box";
    @Expose
    private GeometryType geometryType = GeometryType.BOX;
    @Expose
    private int radius = 5;
    @Expose
    private int length = 5;
    @Expose
    private int width = 5;
    @Expose
    private int height = 5;
    
    // 为每个几何体添加独立的半径参数
    @Expose
    private int sphereRadius = 5;
    @Expose
    private int cylinderRadius = 5;
    @Expose
    private int coneRadius = 5;
    @Expose
    private int octahedronRadius = 5;
    @Expose
    private int tetrahedronEdgeLength = 5;
    @Expose
    private int triangularPrismSideLength = 5;
    
    // 为每个几何体添加独立的高度参数
    @Expose
    private int boxHeight = 5;
    @Expose
    private int cylinderHeight = 5;
    @Expose
    private int coneHeight = 5;
    @Expose
    private int sphereHeight = 5;
    @Expose
    private int octahedronHeight = 5;
    @Expose
    private int tetrahedronHeight = 5;
    @Expose
    private int triangularPrismHeight = 5;
    @Expose
    private int ellipsoidHeight = 5;
    @Expose
    private int lockedHeight = 0;
    @Expose
    private HeightMode heightMode = HeightMode.NO_LIMIT;
    @Expose
    private boolean isLockedOnceMode = false; // 新增：标记是否为 LOCKED_ONCE 模式

    // 铺设模式配置
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
    @Expose
    private PlaceMode placeMode = PlaceMode.ADAPTIVE_BIOME;
    
    // 自然方块选择配置
    @Expose
    private String selectedNaturalBlockId = "minecraft:stone";

    // 平滑强度配置（用于平滑提升、平滑降低、自适应平滑）
    @Expose
    private float smoothStrength = 0.5f;       // 平滑强度 (0.1-1.0)

    // 统一平滑模式的子类型（变体）
    @Expose
    private SmoothVariant smoothVariant = SmoothVariant.ADAPTIVE;

    // 粗糙强度配置（用于表面粗糙）
    @Expose
    private float roughnessStrength = 0.5f;    // 粗糙强度 (0.1-1.0)

    // 表面粗糙：平滑强度配置（用于控制原始地形保留程度）
    @Expose
    private float smoothingIntensity = 0.5f;    // 平滑强度 (0.0-1.0)，0=保留原始形状，1=完全重塑

    // 噪声种子配置（用于表面粗糙）
    @Expose
    private long noiseSeed = System.currentTimeMillis();  // 噪声种子，用于可重现的噪声

    // 表面粗糙：噪声参数（可选，默认启用自适应按半径缩放）
    @Expose
    private boolean noiseAutoScale = true;      // 是否按笔刷半径自适应噪声参数
    @Expose
    private float noiseFrequency = 0.02f;       // 自定义噪声频率 (建议范围 0.01-0.12)
    @Expose
    private float noisePersistence = 0.5f;      // 自定义噪声持久性 (建议范围 0.1-0.9)
    @Expose
    private int noiseOctaves = 4;               // 自定义噪声八度数 (建议范围 1-6)

    // 新的表层转换配置 - 支持多方块配置
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

    // 表层转换方块列表（最多5个）
    @Expose
    private List<SurfaceConvertBlock> surfaceConvertBlocks = new ArrayList<>(List.of(
            new SurfaceConvertBlock("minecraft:grass_block", 100.0f)
    ));

    // 批量种植模式配置
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
    @Expose
    private PlantType plantType = PlantType.TREES;
    
    // 自定义植物列表（存储方块ID）
    @Expose
    private Set<String> customPlantBlockIds = new HashSet<>();
    @Expose
    private float plantDensity = 0.3f;  // 种植密度 (0.1-1.0)

    // 水岸处理模式配置
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
    
    @Expose
    private ShorelineType shorelineType = ShorelineType.ADAPTIVE;
    @Expose
    private int shorelineWidth = 3;  // 水体检测范围 (1-10)
    
    // 水岸植物种植配置
    @Expose
    private boolean plantVegetationEnabled = true;  // 是否启用植物种植
    @Expose
    private float vegetationDensity = 0.1f;  // 植物种植密度 (0.0-1.0)
    
    // 自定义水岸类型配置
    @Expose
    private Set<String> customShorelineBlocks = new HashSet<>();  // 自定义水岸方块ID集合
    @Expose
    private Set<String> customShorelinePlants = new HashSet<>();  // 自定义水岸植物ID集合

    // 水岸处理标高限制配置
    @Expose
    private boolean shorelineHeightAboveEnabled = false;  // 是否启用标高上操作
    @Expose
    private boolean shorelineHeightBelowEnabled = false;  // 是否启用标高下操作

    // 新增：具体的树木类型
    public enum TreeSpecies {
        OAK, SPRUCE, BIRCH, JUNGLE, ACACIA, DARK_OAK, BIOME_ADAPTIVE;
        public net.minecraft.text.Text getDisplayText() {
            return net.minecraft.text.Text.translatable("pushdozer.tree_species." + this.name().toLowerCase());
        }
    }

    // 新增：具体的花朵组
    public enum FlowerGroup {
        PLAINS_FLOWERS, FOREST_FLOWERS, ALL_FLOWERS, BIOME_ADAPTIVE;
        public net.minecraft.text.Text getDisplayText() {
            return net.minecraft.text.Text.translatable("pushdozer.flower_group." + this.name().toLowerCase());
        }
    }

    @Expose
    private TreeSpecies selectedTree = TreeSpecies.BIOME_ADAPTIVE;
    @Expose
    private FlowerGroup selectedFlowerGroup = FlowerGroup.BIOME_ADAPTIVE;
    @Expose
    private boolean respectBiomes = true;
    @Expose
    private float clusterScale = 0.05f;

    public PushdozerConfig() {
        Set<String> placeableBlockIds = new HashSet<>();
        placeableBlockIds.add("minecraft:stone");
        placeableBlockIds.add("minecraft:dirt");
        // 确保在构造函数中设置默认值
    }

    // Getter 和 Setter 方法

    public WorkMode getWorkMode() {
        return workMode;
    }

    public void setWorkMode(WorkMode workMode) {
        this.workMode = workMode;
        notifyListeners();
    }

    public DisplayMode getDisplayMode() {
        return displayMode;
    }

    public void setDisplayMode(DisplayMode displayMode) {
        this.displayMode = displayMode;
        save(); // 确保在更改后保存配置
        notifyListeners();
    }

    public int getMaxOperationDistance() {
        return maxOperationDistance;
    }

    public void setMaxOperationDistance(int distance) {
        this.maxOperationDistance = distance;
        notifyListeners();
    }

    public boolean isBlockBreakable(Block block) {
        String blockId = Registries.BLOCK.getId(block).toString();
        return breakableBlockIds.contains(blockId);
    }

    public String getShape() {
        return shape;
    }

    public void setShape(String shape) {
        this.shape = shape;
        this.geometryType = GeometryType.fromString(shape);
        notifyListeners();
    }

    public GeometryType getGeometryType() {
        return geometryType;
    }

    public void setGeometryType(GeometryType geometryType) {
        this.geometryType = geometryType;
        this.shape = geometryType.getShapeString();
        notifyListeners();
    }

    public int getRadius() {
        return radius;
    }

    public void setRadius(int radius) {
        this.radius = clampBrushSize(radius);
        notifyListeners();
    }

    /**
     * 配置中所有笔刷尺寸参数的最大值，用于服务端权限校验。
     */
    public int getLargestBrushDimension() {
        int max = radius;
        max = Math.max(max, length);
        max = Math.max(max, width);
        max = Math.max(max, height);
        max = Math.max(max, boxHeight);
        max = Math.max(max, sphereRadius);
        max = Math.max(max, sphereHeight);
        max = Math.max(max, cylinderRadius);
        max = Math.max(max, cylinderHeight);
        max = Math.max(max, coneRadius);
        max = Math.max(max, coneHeight);
        max = Math.max(max, octahedronRadius);
        max = Math.max(max, octahedronHeight);
        max = Math.max(max, ellipsoidHeight);
        max = Math.max(max, tetrahedronEdgeLength);
        max = Math.max(max, tetrahedronHeight);
        max = Math.max(max, triangularPrismSideLength);
        max = Math.max(max, triangularPrismHeight);
        return max;
    }

    public int getLength() {
        return length;
    }

    public void setLength(int length) {
        this.length = clampBrushSize(length);
        notifyListeners();
    }

    public int getWidth() {
        return width;
    }

    public void setWidth(int width) {
        this.width = clampBrushSize(width);
        notifyListeners();
    }

    public int getHeight() {
        return height;
    }

    public void setHeight(int height) {
        this.height = clampBrushSize(height);
        notifyListeners();
    }

    // 为每个几何体添加独立的高度getter和setter方法
    public int getBoxHeight() {
        return boxHeight;
    }

    public void setBoxHeight(int boxHeight) {
        this.boxHeight = clampBrushSize(boxHeight);
        notifyListeners();
    }

    public int getCylinderHeight() {
        return cylinderHeight;
    }

    public void setCylinderHeight(int cylinderHeight) {
        this.cylinderHeight = clampBrushSize(cylinderHeight);
        notifyListeners();
    }

    public int getConeHeight() {
        return coneHeight;
    }

    public void setConeHeight(int coneHeight) {
        this.coneHeight = clampBrushSize(coneHeight);
        notifyListeners();
    }

    public int getEllipsoidHeight() {
        return ellipsoidHeight;
    }

    public void setEllipsoidHeight(int ellipsoidHeight) {
        this.ellipsoidHeight = clampBrushSize(ellipsoidHeight);
        notifyListeners();
    }

    // 为每个几何体添加独立的半径getter和setter方法
    public int getSphereRadius() {
        return sphereRadius;
    }

    public void setSphereRadius(int sphereRadius) {
        this.sphereRadius = clampBrushSize(sphereRadius);
        notifyListeners();
    }

    public int getCylinderRadius() {
        return cylinderRadius;
    }

    public void setCylinderRadius(int cylinderRadius) {
        this.cylinderRadius = clampBrushSize(cylinderRadius);
        notifyListeners();
    }

    public int getConeRadius() {
        return coneRadius;
    }

    public void setConeRadius(int coneRadius) {
        this.coneRadius = clampBrushSize(coneRadius);
        notifyListeners();
    }

    public int getOctahedronRadius() {
        return octahedronRadius;
    }

    public void setOctahedronRadius(int octahedronRadius) {
        this.octahedronRadius = clampBrushSize(octahedronRadius);
        notifyListeners();
    }

    public int getTetrahedronEdgeLength() {
        return tetrahedronEdgeLength;
    }

    public void setTetrahedronEdgeLength(int tetrahedronEdgeLength) {
        this.tetrahedronEdgeLength = clampBrushSize(tetrahedronEdgeLength);
        notifyListeners();
    }

    public int getTriangularPrismSideLength() {
        return triangularPrismSideLength;
    }

    public void setTriangularPrismSideLength(int triangularPrismSideLength) {
        this.triangularPrismSideLength = clampBrushSize(triangularPrismSideLength);
        notifyListeners();
    }

    public int getTetrahedronHeight() {
        return tetrahedronHeight;
    }

    public void setTetrahedronHeight(int tetrahedronHeight) {
        this.tetrahedronHeight = clampBrushSize(tetrahedronHeight);
        notifyListeners();
    }

    public int getTriangularPrismHeight() {
        return triangularPrismHeight;
    }

    public void setTriangularPrismHeight(int triangularPrismHeight) {
        this.triangularPrismHeight = clampBrushSize(triangularPrismHeight);
        notifyListeners();
    }

    public int getLockedHeight() {
        return lockedHeight;
    }

    public void setLockedHeight(int height) {
        this.lockedHeight = height;
    }

    public HeightMode getHeightMode() {
        return heightMode;
    }

    public void setHeightMode(HeightMode heightMode) {
        this.heightMode = heightMode;
    }

    public boolean isLockedOnceMode() {
        return isLockedOnceMode;
    }

    public void setLockedOnceMode(boolean lockedOnceMode) {
        this.isLockedOnceMode = lockedOnceMode;
    }

    // 铺设模式配置的 getter 和 setter
    public PlaceMode getPlaceMode() {
        return placeMode;
    }

    public void setPlaceMode(PlaceMode placeMode) {
        this.placeMode = placeMode;
        notifyListeners();
    }

    public void setSelectedNaturalBlockId(String blockId) {
        this.selectedNaturalBlockId = blockId;
        notifyListeners();
    }
    
    public String getSelectedNaturalBlockId() {
        return this.selectedNaturalBlockId;
    }

    public Block getSelectedNaturalBlock() {
        try {
            return Registries.BLOCK.get(Identifier.of(selectedNaturalBlockId));
        } catch (Exception e) {
            return Blocks.STONE; // 默认返回石头
        }
    }

    // 平滑强度配置的 getter 和 setter
    public float getSmoothStrength() {
        return smoothStrength;
    }

    public void setSmoothStrength(float smoothStrength) {
        this.smoothStrength = Math.max(0.1f, Math.min(1.0f, smoothStrength));
        notifyListeners();
    }

    // 统一平滑模式：变体 getter/setter
    public SmoothVariant getSmoothVariant() {
        return smoothVariant == null ? SmoothVariant.ADAPTIVE : smoothVariant;
    }

    public void setSmoothVariant(SmoothVariant variant) {
        this.smoothVariant = (variant == null) ? SmoothVariant.ADAPTIVE : variant;
        notifyListeners();
    }

    // 粗糙强度配置的 getter 和 setter
    public float getRoughnessStrength() {
        return roughnessStrength;
    }

    public void setRoughnessStrength(float roughnessStrength) {
        this.roughnessStrength = Math.max(0.1f, Math.min(2.0f, roughnessStrength));
        notifyListeners();
    }

    // 平滑强度配置的 getter 和 setter
    public float getSmoothingIntensity() {
        return smoothingIntensity;
    }

    public void setSmoothingIntensity(float smoothingIntensity) {
        this.smoothingIntensity = Math.max(0.0f, Math.min(1.0f, smoothingIntensity));
        notifyListeners();
    }

    // 噪声种子配置的 getter 和 setter
    public long getNoiseSeed() {
        return noiseSeed;
    }

    // 表面粗糙：噪声参数 getter/setter
    public boolean isNoiseAutoScale() { return noiseAutoScale; }
    public void setNoiseAutoScale(boolean auto) { this.noiseAutoScale = auto; notifyListeners(); }
    public float getNoiseFrequency() { return noiseFrequency; }
    public void setNoiseFrequency(float value) { this.noiseFrequency = Math.max(0.005f, Math.min(0.2f, value)); notifyListeners(); }
    public float getNoisePersistence() { return noisePersistence; }
    public void setNoisePersistence(float value) { this.noisePersistence = Math.max(0.05f, Math.min(0.95f, value)); notifyListeners(); }
    public int getNoiseOctaves() { return noiseOctaves; }
    public void setNoiseOctaves(int value) { this.noiseOctaves = Math.max(1, Math.min(6, value)); notifyListeners(); }

    // 新的表层转换配置的 getter 和 setter
    public List<SurfaceConvertBlock> getSurfaceConvertBlocks() {
        return surfaceConvertBlocks;
    }

    // 批量种植模式配置的 getter 和 setter
    public PlantType getPlantType() {
        return plantType;
    }

    public void setPlantType(PlantType plantType) {
        this.plantType = plantType;
        notifyListeners();
    }

    public float getPlantDensity() {
        return plantDensity;
    }

    public void setPlantDensity(float plantDensity) {
        this.plantDensity = Math.max(0.1f, Math.min(1.0f, plantDensity));
        notifyListeners();
    }

    // 水岸处理模式配置的 getter 和 setter
    public ShorelineType getShorelineType() {
        return shorelineType;
    }

    public void setShorelineType(ShorelineType shorelineType) {
        this.shorelineType = shorelineType;
        notifyListeners();
    }

    public int getShorelineWidth() {
        return shorelineWidth;
    }

    public void setShorelineWidth(int shorelineWidth) {
        this.shorelineWidth = Math.max(1, Math.min(10, shorelineWidth));
        notifyListeners();
    }

    // 水岸植物种植配置的 getter 和 setter
    public boolean isPlantVegetationEnabled() {
        return plantVegetationEnabled;
    }

    public void setPlantVegetationEnabled(boolean enabled) {
        this.plantVegetationEnabled = enabled;
        notifyListeners();
    }

    public float getVegetationDensity() {
        return vegetationDensity;
    }

    public void setVegetationDensity(float density) {
        this.vegetationDensity = Math.max(0.0f, Math.min(1.0f, density));
        notifyListeners();
    }

    // 自定义水岸类型配置的 getter 和 setter
    public Set<String> getCustomShorelineBlocks() {
        return customShorelineBlocks;
    }

    public void setCustomShorelineBlocks(Set<String> blockIds) {
        this.customShorelineBlocks = blockIds;
        notifyListeners();
    }

    public Set<String> getCustomShorelinePlants() {
        return customShorelinePlants;
    }

    public void setCustomShorelinePlants(Set<String> plantIds) {
        this.customShorelinePlants = plantIds;
        notifyListeners();
    }

    // 便捷方法：获取自定义水岸方块列表
    public List<Block> getCustomShorelineBlockList() {
        return customShorelineBlocks.stream()
            .map(Identifier::tryParse)
            .filter(Objects::nonNull)
            .map(Registries.BLOCK::get)
            .collect(Collectors.toList());
    }

    // 便捷方法：获取自定义水岸植物列表
    public List<Block> getCustomShorelinePlantList() {
        return customShorelinePlants.stream()
            .map(Identifier::tryParse)
            .filter(Objects::nonNull)
            .map(Registries.BLOCK::get)
            .collect(Collectors.toList());
    }

    // 水岸处理标高限制配置的getter和setter方法
    public boolean isShorelineHeightAboveEnabled() {
        return shorelineHeightAboveEnabled;
    }

    public void setShorelineHeightAboveEnabled(boolean enabled) {
        this.shorelineHeightAboveEnabled = enabled;
        // 如果启用标高上操作，则禁用标高下操作
        if (enabled) {
            this.shorelineHeightBelowEnabled = false;
        }
        notifyListeners();
    }

    public boolean isShorelineHeightBelowEnabled() {
        return shorelineHeightBelowEnabled;
    }

    public void setShorelineHeightBelowEnabled(boolean enabled) {
        this.shorelineHeightBelowEnabled = enabled;
        // 如果启用标高下操作，则禁用标高上操作
        if (enabled) {
            this.shorelineHeightAboveEnabled = false;
        }
        notifyListeners();
    }

    // 新增 getter/setter
    public TreeSpecies getSelectedTree() {
        return selectedTree;
    }
    public void setSelectedTree(TreeSpecies selectedTree) {
        this.selectedTree = selectedTree;
    }
    public FlowerGroup getSelectedFlowerGroup() {
        return selectedFlowerGroup;
    }
    public void setSelectedFlowerGroup(FlowerGroup selectedFlowerGroup) {
        this.selectedFlowerGroup = selectedFlowerGroup;
    }
    public boolean shouldRespectBiomes() {
        return respectBiomes;
    }
    public void setRespectBiomes(boolean respectBiomes) {
        this.respectBiomes = respectBiomes;
    }
    public float getClusterScale() {
        return clusterScale;
    }
    public void setClusterScale(float clusterScale) {
        this.clusterScale = clusterScale;
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
        notifyListeners();
    }

    private void notifyListeners() {
        // 实现配置变更通知逻辑
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
            ensureDefaults(config);
            return config;
        }

        try {
            PushdozerConfig config = GSON.fromJson(json, PushdozerConfig.class);
            if (config == null) {
                config = new PushdozerConfig();
            }
            ensureDefaults(config);
            return config;
        } catch (Exception e) {
            PushdozerMod.LOGGER.error("Failed to parse Pushdozer configuration JSON", e);
            PushdozerConfig config = new PushdozerConfig();
            ensureDefaults(config);
            return config;
        }
    }

    public static void ensureDefaults(PushdozerConfig config) {
        if (config.getWorkMode() == null) {
            config.setWorkMode(WorkMode.EXCAVATE);
        }
        if (config.getDisplayMode() == null) {
            config.setDisplayMode(DisplayMode.WIREFRAME);
        }
        if (config.getHeightMode() == null) {
            if (config.getLockedHeight() != 0) {
                config.setHeightMode(HeightMode.CUSTOM);
            } else {
                config.setHeightMode(HeightMode.NO_LIMIT);
            }
        }
        if (config.getPlantType() == null) {
            config.setPlantType(PlantType.TREES);
        }

        try {
            WorkMode wm = config.getWorkMode();
            if (wm == WorkMode.SMOOTH_RAISE) {
                config.setSmoothVariant(SmoothVariant.RAISE);
                config.setWorkMode(WorkMode.SMOOTH);
            } else if (wm == WorkMode.SMOOTH_LOWER) {
                config.setSmoothVariant(SmoothVariant.LOWER);
                config.setWorkMode(WorkMode.SMOOTH);
            } else if (wm == WorkMode.ADAPTIVE_SMOOTH) {
                config.setSmoothVariant(SmoothVariant.ADAPTIVE);
                config.setWorkMode(WorkMode.SMOOTH);
            }
        } catch (Exception e) {
            PushdozerMod.LOGGER.warn("Failed to migrate old smooth work modes", e);
        }

        clampBrushDimensions(config);
        config.ignoredBlocksCacheDirty = true;
        config.rebuildIgnoredBlocksCache();
    }

    /** 将 JSON/旧配置中的笔刷尺寸限制在合法范围内（绕过 setter 直接写字段时也能生效）。 */
    private static void clampBrushDimensions(PushdozerConfig config) {
        config.radius = clampBrushSize(config.radius);
        config.length = clampBrushSize(config.length);
        config.width = clampBrushSize(config.width);
        config.height = clampBrushSize(config.height);
        config.boxHeight = clampBrushSize(config.boxHeight);
        config.sphereRadius = clampBrushSize(config.sphereRadius);
        config.sphereHeight = clampBrushSize(config.sphereHeight);
        config.cylinderRadius = clampBrushSize(config.cylinderRadius);
        config.cylinderHeight = clampBrushSize(config.cylinderHeight);
        config.coneRadius = clampBrushSize(config.coneRadius);
        config.coneHeight = clampBrushSize(config.coneHeight);
        config.octahedronRadius = clampBrushSize(config.octahedronRadius);
        config.octahedronHeight = clampBrushSize(config.octahedronHeight);
        config.ellipsoidHeight = clampBrushSize(config.ellipsoidHeight);
        config.tetrahedronEdgeLength = clampBrushSize(config.tetrahedronEdgeLength);
        config.tetrahedronHeight = clampBrushSize(config.tetrahedronHeight);
        config.triangularPrismSideLength = clampBrushSize(config.triangularPrismSideLength);
        config.triangularPrismHeight = clampBrushSize(config.triangularPrismHeight);
    }

    public static PushdozerConfig load() {
        Path configDir = FabricLoader.getInstance().getConfigDir();
        File configFile = configDir.resolve(CONFIG_FILE_NAME).toFile();

        PushdozerConfig config = null;

        if (configFile.exists()) {
            try (FileReader reader = new FileReader(configFile)) {
                config = GSON.fromJson(reader, PushdozerConfig.class);
                PushdozerMod.LOGGER.info("Configuration has been loaded");
            } catch (IOException e) {
                PushdozerMod.LOGGER.error("An error occurred while loading the Pushdozer configuration", e);
            }
        }

        if (config == null) {
            config = new PushdozerConfig();
            PushdozerMod.LOGGER.info("Creating new default configuration");
        }

        ensureDefaults(config);

        // 保存配置文件
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

    public List<String> getIgnoredBlockIds() {
        return ignoredBlockIds;
    }
    
    /**
     * 获取缓存的忽略方块集合，用于高性能检查
     */
    public Set<Block> getIgnoredBlocks() {
        if (ignoredBlocksCacheDirty) {
            rebuildIgnoredBlocksCache();
        }
        return ignoredBlocks;
    }
    
    /**
     * 检查方块是否在忽略列表中（高性能版本）
     */
    public boolean isBlockIgnored(Block block) {
        if (ignoredBlocksCacheDirty) {
            rebuildIgnoredBlocksCache();
        }
        return ignoredBlocks.contains(block);
    }

    /**
     * 重新构建忽略方块缓存
     */
    public void rebuildIgnoredBlocksCache() {
        ignoredBlocks.clear();
        for (String blockId : ignoredBlockIds) {
            try {
                Identifier identifier = Identifier.tryParse(blockId);
                if (identifier != null) {
                    Block block = Registries.BLOCK.get(identifier);
                    if (block != Blocks.AIR) { // 确保不是空气方块
                        ignoredBlocks.add(block);
                    }
                }
            } catch (Exception e) {
                PushdozerMod.LOGGER.warn("无法解析忽略方块ID: {}", blockId);
            }
        }
        ignoredBlocksCacheDirty = false;
    }

    public List<Block> getBreakableBlocks() {
        return breakableBlockIds.stream()
            .map(Identifier::tryParse)
            .filter(Objects::nonNull)
            .map(Registries.BLOCK::get)
            .collect(Collectors.toList());
    }

    public void setBreakableBlocks(List<Block> blocks) {
        this.breakableBlockIds = blocks.stream()
            .map(block -> Registries.BLOCK.getId(block).toString())
            .collect(Collectors.toSet());
        notifyListeners();
    }
    
    private static PushdozerConfig instance = null;

    // 添加获取单例实例的方法
    public static PushdozerConfig getInstance() {
        if (instance == null) {
            instance = load();
        }
        return instance;
    }
}