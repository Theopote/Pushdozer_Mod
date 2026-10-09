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
 * 椭球体形状类
 */
public class EllipsoidShape implements GeometryShape {
    private final int radiusX;
    private final int radiusY;
    private final int radiusZ;
    private final double invRadiusXSquared;
    private final double invRadiusYSquared;
    private final double invRadiusZSquared;
    private Vec3d ellipsoidCenter;
    private BlockPos center;

    public EllipsoidShape(int radiusX, int radiusY, int radiusZ, BlockPos center) {
        this.radiusX = radiusX;
        this.radiusY = radiusY;
        this.radiusZ = radiusZ;
        this.invRadiusXSquared = 1.0 / (radiusX * radiusX);
        this.invRadiusYSquared = 1.0 / (radiusY * radiusY);
        this.invRadiusZSquared = 1.0 / (radiusZ * radiusZ);
        this.center = center;
        this.ellipsoidCenter = Vec3d.ofCenter(center);
    }

    @Override
    public Box getBoundingBox(BlockPos basePos) {
        Vec3d worldCenter = resolveWorldCenter(basePos);
        return new Box(
            worldCenter.x - radiusX, worldCenter.y - radiusY, worldCenter.z - radiusZ,
            worldCenter.x + radiusX, worldCenter.y + radiusY, worldCenter.z + radiusZ
        );
    }

    @Override
    public void renderOutline(MatrixStack matrices, VertexConsumer vertexConsumer, Vec3d center, float red, float green, float blue, float alpha) {
        // 椭球体线框渲染 - 由WireframeRenderer处理
    }

    @Override
    public void renderSolid(MatrixStack matrices, VertexConsumer vertexConsumer, Vec3d center, float red, float green, float blue, float alpha) {
        // 椭球体实体渲染 - 由PointCloudRenderer处理
    }

    @Override
    public boolean isInside(Vec3d pos) {
        return ellipsoidEquation(pos, ellipsoidCenter) <= 1.0;
    }

    @Override
    public boolean isInside(BlockPos pos) {
        return ellipsoidEquation(Vec3d.ofCenter(pos), ellipsoidCenter) <= 1.0;
    }

    @Override
    public int getMinY(BlockPos basePos) {
        return (int) Math.floor(getBoundingBox(basePos).minY);
    }

    @Override
    public int getMaxY(BlockPos basePos) {
        return (int) Math.floor(getBoundingBox(basePos).maxY);
    }

    @Override
    public List<BlockPos> getBlocksInLayer(BlockPos basePos, int y) {
        List<BlockPos> positions = new ArrayList<>();
        double dy = y + 0.5 - resolveWorldCenter(basePos).y;
        if (Math.abs(dy) > radiusY) {
            return positions;
        }

        double yFactor = 1.0 - (dy * dy) * invRadiusYSquared;
        if (yFactor < 0) {
            return positions;
        }

        int maxX = (int) Math.ceil(radiusX * Math.sqrt(yFactor));
        int maxZ = (int) Math.ceil(radiusZ * Math.sqrt(yFactor));
        for (int x = basePos.getX() - maxX; x <= basePos.getX() + maxX; x++) {
            for (int z = basePos.getZ() - maxZ; z <= basePos.getZ() + maxZ; z++) {
                BlockPos pos = new BlockPos(x, y, z);
                if (isInside(pos)) {
                    positions.add(pos);
                }
            }
        }

        return positions;
    }

    @Override
    public List<BlockPos> getBlocksInRadius(Vec3d queryCenter, int maxDistance) {
        List<BlockPos> positions = new ArrayList<>();
        BlockPos queryBlock = BlockPos.ofFloored(queryCenter);
        double maxDistanceSquared = (double) maxDistance * maxDistance;

        int minX = Math.max(queryBlock.getX() - maxDistance, (int) Math.floor(ellipsoidCenter.x - radiusX));
        int maxX = Math.min(queryBlock.getX() + maxDistance, (int) Math.ceil(ellipsoidCenter.x + radiusX));
        int minY = Math.max(queryBlock.getY() - maxDistance, (int) Math.floor(ellipsoidCenter.y - radiusY));
        int maxY = Math.min(queryBlock.getY() + maxDistance, (int) Math.ceil(ellipsoidCenter.y + radiusY));
        int minZ = Math.max(queryBlock.getZ() - maxDistance, (int) Math.floor(ellipsoidCenter.z - radiusZ));
        int maxZ = Math.min(queryBlock.getZ() + maxDistance, (int) Math.ceil(ellipsoidCenter.z + radiusZ));

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
        return ellipsoidEquation(Vec3d.ofCenter(pos), resolveWorldCenter(basePos)) <= 1.0;
    }

    @Override
    public void setCenter(BlockPos newCenter) {
        this.center = newCenter;
        this.ellipsoidCenter = Vec3d.ofCenter(newCenter);
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

    public int getRadiusX() {
        return radiusX;
    }

    public int getRadiusY() {
        return radiusY;
    }

    public int getRadiusZ() {
        return radiusZ;
    }

    private double ellipsoidEquation(Vec3d point, Vec3d referenceCenter) {
        double dx = point.x - referenceCenter.x;
        double dy = point.y - referenceCenter.y;
        double dz = point.z - referenceCenter.z;
        return dx * dx * invRadiusXSquared + dy * dy * invRadiusYSquared + dz * dz * invRadiusZSquared;
    }

    private Vec3d resolveWorldCenter(BlockPos basePos) {
        return ellipsoidCenter.add(
            basePos.getX() - center.getX(),
            basePos.getY() - center.getY(),
            basePos.getZ() - center.getZ()
        );
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
