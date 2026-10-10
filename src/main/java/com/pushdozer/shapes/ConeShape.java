package com.pushdozer.shapes;

import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;

/**
 * 圆锥体形状类
 */
public class ConeShape implements GeometryShape {
    private final int baseRadius;
    private final int height;
    private final double[] layerRadiusSquared;
    private Vec3d coneCenter;
    private BlockPos center;

    public ConeShape(int baseRadius, int height, BlockPos center) {
        if (baseRadius <= 0 || height <= 0) {
            throw new IllegalArgumentException("Cone base radius and height must be positive");
        }
        this.baseRadius = baseRadius;
        this.height = height;
        this.center = center;
        this.coneCenter = Vec3d.ofCenter(center);
        this.layerRadiusSquared = computeLayerRadiusSquared();
    }

    @Override
    public Box getBoundingBox(BlockPos basePos) {
        Vec3d worldCenter = resolveWorldCenter(basePos);
        int minY = computeMinY(basePos);
        int maxY = computeMaxY(basePos);
        return new Box(
            worldCenter.x - baseRadius, minY, worldCenter.z - baseRadius,
            worldCenter.x + baseRadius, maxY + 1, worldCenter.z + baseRadius
        );
    }

    @Override
    public void renderOutline(MatrixStack matrices, VertexConsumer vertexConsumer, Vec3d center, float red, float green, float blue, float alpha) {
        // 圆锥体线框渲染 - 由WireframeRenderer处理
    }

    @Override
    public void renderSolid(MatrixStack matrices, VertexConsumer vertexConsumer, Vec3d center, float red, float green, float blue, float alpha) {
        // 圆锥体实体渲染 - 由PointCloudRenderer处理
    }

    @Override
    public boolean isInside(Vec3d pos) {
        return isInsideAt(pos, coneCenter, center);
    }

    @Override
    public boolean isInside(BlockPos pos) {
        return isInside(Vec3d.ofCenter(pos));
    }

    @Override
    public int getMinY(BlockPos basePos) {
        return computeMinY(basePos);
    }

    @Override
    public int getMaxY(BlockPos basePos) {
        return computeMaxY(basePos);
    }

    @Override
    public List<BlockPos> getBlocksInLayer(BlockPos basePos, int y) {
        List<BlockPos> positions = new ArrayList<>();
        int minY = computeMinY(basePos);
        int maxY = computeMaxY(basePos);
        if (y < minY || y > maxY) {
            return positions;
        }

        int layerIndex = y - minY;
        double radiusSquared = layerRadiusSquared[layerIndex];
        if (radiusSquared <= 0) {
            positions.add(new BlockPos(basePos.getX(), y, basePos.getZ()));
            return positions;
        }

        int scanRadius = (int) Math.ceil(Math.sqrt(radiusSquared));
        for (int x = basePos.getX() - scanRadius; x <= basePos.getX() + scanRadius; x++) {
            for (int z = basePos.getZ() - scanRadius; z <= basePos.getZ() + scanRadius; z++) {
                BlockPos pos = new BlockPos(x, y, z);
                if (isInside(pos)) {
                    positions.add(pos);
                }
            }
        }
        return positions;
    }

    @Override
    public List<BlockPos> getBlockPositions() {
        List<BlockPos> positions = new ArrayList<>();
        int minY = getMinY(center);
        int maxY = getMaxY(center);
        for (int y = minY; y <= maxY; y++) {
            positions.addAll(getBlocksInLayer(center, y));
        }
        return positions;
    }

    @Override
    public List<BlockPos> getBlocksInRadius(Vec3d queryCenter, int maxDistance) {
        List<BlockPos> positions = new ArrayList<>();
        BlockPos queryBlock = BlockPos.ofFloored(queryCenter);
        double maxDistanceSquared = (double) maxDistance * maxDistance;

        Box coneBounds = getBoundingBox(center);
        int minX = Math.max(queryBlock.getX() - maxDistance, (int) Math.floor(coneBounds.minX));
        int maxX = Math.min(queryBlock.getX() + maxDistance, (int) Math.ceil(coneBounds.maxX));
        int minY = Math.max(queryBlock.getY() - maxDistance, (int) Math.floor(coneBounds.minY));
        int maxY = Math.min(queryBlock.getY() + maxDistance, (int) Math.ceil(coneBounds.maxY));
        int minZ = Math.max(queryBlock.getZ() - maxDistance, (int) Math.floor(coneBounds.minZ));
        int maxZ = Math.min(queryBlock.getZ() + maxDistance, (int) Math.ceil(coneBounds.maxZ));

        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    if (isInside(pos) && Vec3d.ofCenter(pos).squaredDistanceTo(queryCenter) <= maxDistanceSquared) {
                        positions.add(pos);
                    }
                }
            }
        }

        return positions;
    }

    @Override
    public boolean isWithinBounds(BlockPos pos, BlockPos basePos) {
        return isInsideAt(Vec3d.ofCenter(pos), resolveWorldCenter(basePos), basePos);
    }

    @Override
    public void setCenter(BlockPos newCenter) {
        this.center = newCenter;
        this.coneCenter = Vec3d.ofCenter(newCenter);
    }

    @Override
    public List<BlockPos> getBlocks() {
        return getBlockPositions();
    }

    @Override
    public Iterator<BlockPos> getBlocksIterator() {
        return new LayerBlockIterator(center, getMinY(center), getMaxY(center));
    }

    @Override
    public BlockPos getCenter() {
        return center;
    }

    public int getBaseRadius() {
        return baseRadius;
    }

    public int getHeight() {
        return height;
    }

    public float getPreviewBottomY(BlockPos basePos) {
        return computeMinY(basePos) - (basePos.getY() + 0.5f);
    }

    public float getPreviewTopY(BlockPos basePos) {
        return computeMaxY(basePos) + 1 - (basePos.getY() + 0.5f);
    }

    private int computeMinY(BlockPos basePos) {
        return basePos.getY() - (height - 1) / 2;
    }

    private int computeMaxY(BlockPos basePos) {
        return computeMinY(basePos) + height - 1;
    }

    private double radiusAtLayer(int layerIndex) {
        if (height == 1) {
            return baseRadius;
        }
        double t = (double) layerIndex / (height - 1);
        return baseRadius * (1.0 - t);
    }

    private double radiusAtY(Vec3d pos, BlockPos basePos) {
        if (height == 1) {
            return baseRadius;
        }
        double minCenterY = computeMinY(basePos) + 0.5;
        double maxCenterY = computeMaxY(basePos) + 0.5;
        double t = (pos.y - minCenterY) / (maxCenterY - minCenterY);
        t = Math.max(0.0, Math.min(1.0, t));
        return baseRadius * (1.0 - t);
    }

    private boolean isInsideAt(Vec3d pos, Vec3d axisCenter, BlockPos basePos) {
        int minY = computeMinY(basePos);
        int maxY = computeMaxY(basePos);
        if (pos.y < minY || pos.y >= maxY + 1) {
            return false;
        }

        double dx = pos.x - axisCenter.x;
        double dz = pos.z - axisCenter.z;
        double radiusAtY = radiusAtY(pos, basePos);
        return dx * dx + dz * dz <= radiusAtY * radiusAtY;
    }

    private Vec3d resolveWorldCenter(BlockPos basePos) {
        return coneCenter.add(
            basePos.getX() - center.getX(),
            basePos.getY() - center.getY(),
            basePos.getZ() - center.getZ()
        );
    }

    private double[] computeLayerRadiusSquared() {
        double[] radiiSquared = new double[height];
        for (int layerIndex = 0; layerIndex < height; layerIndex++) {
            double radius = radiusAtLayer(layerIndex);
            radiiSquared[layerIndex] = radius * radius;
        }
        return radiiSquared;
    }

    private final class LayerBlockIterator implements Iterator<BlockPos> {
        private final BlockPos basePos;
        private final int maxY;
        private int currentY;
        private Iterator<BlockPos> currentLayer = List.<BlockPos>of().iterator();

        private LayerBlockIterator(BlockPos basePos, int minY, int maxY) {
            this.basePos = basePos;
            this.maxY = maxY;
            this.currentY = minY - 1;
            advanceLayer();
        }

        @Override
        public boolean hasNext() {
            while (!currentLayer.hasNext() && currentY < maxY) {
                advanceLayer();
            }
            return currentLayer.hasNext();
        }

        @Override
        public BlockPos next() {
            if (!hasNext()) {
                throw new NoSuchElementException();
            }
            return currentLayer.next();
        }

        private void advanceLayer() {
            currentY++;
            if (currentY <= maxY) {
                currentLayer = getBlocksInLayer(basePos, currentY).iterator();
            }
        }
    }
}
