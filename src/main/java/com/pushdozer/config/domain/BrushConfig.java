package com.pushdozer.config.domain;

import com.google.gson.annotations.Expose;
import com.pushdozer.PushdozerMod;
import com.pushdozer.config.PushdozerConfig;
import com.pushdozer.util.RegistryBlocks;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

public class BrushConfig {
    public static final int MIN_BRUSH_RADIUS = 1;
    public static final int MAX_BRUSH_RADIUS = 64;

    @Expose
    private Set<String> breakableBlockIds = new HashSet<>();
    @Expose
    private List<String> ignoredBlockIds = new ArrayList<>();
    @Expose(serialize = false, deserialize = false)
    private Set<Block> ignoredBlocks = new HashSet<>();
    @Expose(serialize = false, deserialize = false)
    private boolean ignoredBlocksCacheDirty = true;
    @Expose
    private String shape = "Box";
    @Expose
    private PushdozerConfig.GeometryType geometryType = PushdozerConfig.GeometryType.BOX;
    @Expose
    private int radius = 5;
    @Expose
    private int length = 5;
    @Expose
    private int width = 5;
    @Expose
    private int height = 5;
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
    private PushdozerConfig.HeightMode heightMode = PushdozerConfig.HeightMode.NO_LIMIT;
    @Expose
    private boolean isLockedOnceMode = false;

    private ConfigChangeNotifier onChange = () -> {};

    public static int clampBrushSize(int size) {
        return Math.max(MIN_BRUSH_RADIUS, Math.min(MAX_BRUSH_RADIUS, size));
    }

    public static boolean isBrushSizeAllowed(int size) {
        return size >= MIN_BRUSH_RADIUS && size <= MAX_BRUSH_RADIUS;
    }

    public void setOnChange(ConfigChangeNotifier onChange) {
        this.onChange = onChange != null ? onChange : () -> {};
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
        this.geometryType = PushdozerConfig.GeometryType.fromString(shape);
        onChange.onConfigChanged();
    }

    public PushdozerConfig.GeometryType getGeometryType() {
        return geometryType;
    }

    public void setGeometryType(PushdozerConfig.GeometryType geometryType) {
        this.geometryType = geometryType;
        this.shape = geometryType.getShapeString();
        onChange.onConfigChanged();
    }

    public int getRadius() {
        return radius;
    }

    public void setRadius(int radius) {
        this.radius = clampBrushSize(radius);
        onChange.onConfigChanged();
    }

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
        onChange.onConfigChanged();
    }

    public int getWidth() {
        return width;
    }

    public void setWidth(int width) {
        this.width = clampBrushSize(width);
        onChange.onConfigChanged();
    }

    public int getHeight() {
        return height;
    }

    public void setHeight(int height) {
        this.height = clampBrushSize(height);
        onChange.onConfigChanged();
    }

    public int getBoxHeight() {
        return boxHeight;
    }

    public void setBoxHeight(int boxHeight) {
        this.boxHeight = clampBrushSize(boxHeight);
        onChange.onConfigChanged();
    }

    public int getCylinderHeight() {
        return cylinderHeight;
    }

    public void setCylinderHeight(int cylinderHeight) {
        this.cylinderHeight = clampBrushSize(cylinderHeight);
        onChange.onConfigChanged();
    }

    public int getConeHeight() {
        return coneHeight;
    }

    public void setConeHeight(int coneHeight) {
        this.coneHeight = clampBrushSize(coneHeight);
        onChange.onConfigChanged();
    }

    public int getEllipsoidHeight() {
        return ellipsoidHeight;
    }

    public void setEllipsoidHeight(int ellipsoidHeight) {
        this.ellipsoidHeight = clampBrushSize(ellipsoidHeight);
        onChange.onConfigChanged();
    }

    public int getSphereRadius() {
        return sphereRadius;
    }

    public void setSphereRadius(int sphereRadius) {
        this.sphereRadius = clampBrushSize(sphereRadius);
        onChange.onConfigChanged();
    }

    public int getCylinderRadius() {
        return cylinderRadius;
    }

    public void setCylinderRadius(int cylinderRadius) {
        this.cylinderRadius = clampBrushSize(cylinderRadius);
        onChange.onConfigChanged();
    }

    public int getConeRadius() {
        return coneRadius;
    }

    public void setConeRadius(int coneRadius) {
        this.coneRadius = clampBrushSize(coneRadius);
        onChange.onConfigChanged();
    }

    public int getOctahedronRadius() {
        return octahedronRadius;
    }

    public void setOctahedronRadius(int octahedronRadius) {
        this.octahedronRadius = clampBrushSize(octahedronRadius);
        onChange.onConfigChanged();
    }

    public int getTetrahedronEdgeLength() {
        return tetrahedronEdgeLength;
    }

    public void setTetrahedronEdgeLength(int tetrahedronEdgeLength) {
        this.tetrahedronEdgeLength = clampBrushSize(tetrahedronEdgeLength);
        onChange.onConfigChanged();
    }

    public int getTriangularPrismSideLength() {
        return triangularPrismSideLength;
    }

    public void setTriangularPrismSideLength(int triangularPrismSideLength) {
        this.triangularPrismSideLength = clampBrushSize(triangularPrismSideLength);
        onChange.onConfigChanged();
    }

    public int getTriangularPrismHeight() {
        return triangularPrismHeight;
    }

    public void setTriangularPrismHeight(int triangularPrismHeight) {
        this.triangularPrismHeight = clampBrushSize(triangularPrismHeight);
        onChange.onConfigChanged();
    }

    public int getLockedHeight() {
        return lockedHeight;
    }

    public void setLockedHeight(int height) {
        this.lockedHeight = height;
    }

    public PushdozerConfig.HeightMode getHeightMode() {
        return heightMode;
    }

    public void setHeightMode(PushdozerConfig.HeightMode heightMode) {
        this.heightMode = heightMode;
    }

    public boolean isLockedOnceMode() {
        return isLockedOnceMode;
    }

    public void setLockedOnceMode(boolean lockedOnceMode) {
        this.isLockedOnceMode = lockedOnceMode;
    }

    public List<String> getIgnoredBlockIds() {
        return ignoredBlockIds;
    }

    public Set<Block> getIgnoredBlocks() {
        if (ignoredBlocksCacheDirty) {
            rebuildIgnoredBlocksCache();
        }
        return ignoredBlocks;
    }

    public boolean isBlockIgnored(Block block) {
        if (ignoredBlocksCacheDirty) {
            rebuildIgnoredBlocksCache();
        }
        return ignoredBlocks.contains(block);
    }

    public void rebuildIgnoredBlocksCache() {
        ignoredBlocks.clear();
        for (String blockId : ignoredBlockIds) {
            Identifier identifier = Identifier.tryParse(blockId);
            if (identifier != null) {
                Block block = RegistryBlocks.getIfPresent(identifier);
                if (block != Blocks.AIR) {
                    ignoredBlocks.add(block);
                }
            } else {
                PushdozerMod.LOGGER.warn("无法解析忽略方块ID: {}", blockId);
            }
        }
        ignoredBlocksCacheDirty = false;
    }

    public void markIgnoredBlocksCacheDirty() {
        ignoredBlocksCacheDirty = true;
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
        onChange.onConfigChanged();
    }

    public void setBreakableBlockIds(Set<String> blockIds) {
        this.breakableBlockIds = blockIds != null ? new HashSet<>(blockIds) : new HashSet<>();
        onChange.onConfigChanged();
    }

    public void setIgnoredBlockIds(List<String> blockIds) {
        this.ignoredBlockIds = blockIds != null ? new ArrayList<>(blockIds) : new ArrayList<>();
        markIgnoredBlocksCacheDirty();
        onChange.onConfigChanged();
    }

    public void setSphereHeight(int sphereHeight) {
        this.sphereHeight = clampBrushSize(sphereHeight);
        onChange.onConfigChanged();
    }

    public void setOctahedronHeight(int octahedronHeight) {
        this.octahedronHeight = clampBrushSize(octahedronHeight);
        onChange.onConfigChanged();
    }

    public void setTetrahedronHeight(int tetrahedronHeight) {
        this.tetrahedronHeight = clampBrushSize(tetrahedronHeight);
        onChange.onConfigChanged();
    }

    void clampAllDimensions() {
        radius = clampBrushSize(radius);
        length = clampBrushSize(length);
        width = clampBrushSize(width);
        height = clampBrushSize(height);
        boxHeight = clampBrushSize(boxHeight);
        sphereRadius = clampBrushSize(sphereRadius);
        sphereHeight = clampBrushSize(sphereHeight);
        cylinderRadius = clampBrushSize(cylinderRadius);
        cylinderHeight = clampBrushSize(cylinderHeight);
        coneRadius = clampBrushSize(coneRadius);
        coneHeight = clampBrushSize(coneHeight);
        octahedronRadius = clampBrushSize(octahedronRadius);
        octahedronHeight = clampBrushSize(octahedronHeight);
        ellipsoidHeight = clampBrushSize(ellipsoidHeight);
        tetrahedronEdgeLength = clampBrushSize(tetrahedronEdgeLength);
        tetrahedronHeight = clampBrushSize(tetrahedronHeight);
        triangularPrismSideLength = clampBrushSize(triangularPrismSideLength);
        triangularPrismHeight = clampBrushSize(triangularPrismHeight);
    }
}
