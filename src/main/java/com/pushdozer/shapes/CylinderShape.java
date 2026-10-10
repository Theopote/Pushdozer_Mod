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
 * 圆柱体形状类
 */
public class CylinderShape implements GeometryShape {
    private final int radius;
    private final int radiusSquared;
    private final int height;
    private final List<BlockPos> xzOffsets;
    private Vec3d cylinderCenter;
    private BlockPos center;

    public CylinderShape(int radius, int height, BlockPos center) {
        this.radius = radius;
        this.radiusSquared = radius * radius;
        this.height = height;
        this.center = center;
        this.cylinderCenter = Vec3d.ofCenter(center);
        this.xzOffsets = computeXzOffsets();
    }

    @Override
    public Box getBoundingBox(BlockPos basePos) {
        Vec3d worldCenter = resolveWorldCenter(basePos);
        int minY = computeMinY(basePos);
        int maxY = computeMaxY(basePos);
        return new Box(
            worldCenter.x - radius, minY, worldCenter.z - radius,
            worldCenter.x + radius, maxY + 1, worldCenter.z + radius
        );
    }

    @Override
    public void renderOutline(MatrixStack matrices, VertexConsumer vertexConsumer, Vec3d center, float red, float green, float blue, float alpha) {
        // 圆柱体线框渲染 - 由WireframeRenderer处理
    }

    @Override
    public void renderSolid(MatrixStack matrices, VertexConsumer vertexConsumer, Vec3d center, float red, float green, float blue, float alpha) {
        // 圆柱体实体渲染 - 由PointCloudRenderer处理
    }

    @Override
    public boolean isInside(Vec3d pos) {
        return isInsideAt(pos, cylinderCenter, center);
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
        if (y < computeMinY(basePos) || y > computeMaxY(basePos)) {
            return positions;
        }

        for (BlockPos offset : xzOffsets) {
            positions.add(new BlockPos(basePos.getX() + offset.getX(), y, basePos.getZ() + offset.getZ()));
        }
        return positions;
    }

    @Override
    public List<BlockPos> getBlocksInRadius(Vec3d queryCenter, int maxDistance) {
        List<BlockPos> positions = new ArrayList<>();
        BlockPos queryBlock = BlockPos.ofFloored(queryCenter);
        double maxDistanceSquared = (double) maxDistance * maxDistance;

        Box cylinderBounds = getBoundingBox(center);
        int minX = Math.max(queryBlock.getX() - maxDistance, (int) Math.floor(cylinderBounds.minX));
        int maxX = Math.min(queryBlock.getX() + maxDistance, (int) Math.ceil(cylinderBounds.maxX));
        int minY = Math.max(queryBlock.getY() - maxDistance, (int) Math.floor(cylinderBounds.minY));
        int maxY = Math.min(queryBlock.getY() + maxDistance, (int) Math.ceil(cylinderBounds.maxY));
        int minZ = Math.max(queryBlock.getZ() - maxDistance, (int) Math.floor(cylinderBounds.minZ));
        int maxZ = Math.min(queryBlock.getZ() + maxDistance, (int) Math.ceil(cylinderBounds.maxZ));

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
        this.cylinderCenter = Vec3d.ofCenter(newCenter);
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

    public int getRadius() {
        return radius;
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

    private boolean isInsideAt(Vec3d pos, Vec3d axisCenter, BlockPos basePos) {
        double dx = pos.x - axisCenter.x;
        double dz = pos.z - axisCenter.z;
        if (dx * dx + dz * dz > radiusSquared) {
            return false;
        }
        int minY = computeMinY(basePos);
        int maxY = computeMaxY(basePos);
        return pos.y >= minY && pos.y < maxY + 1;
    }

    private Vec3d resolveWorldCenter(BlockPos basePos) {
        return cylinderCenter.add(
            basePos.getX() - center.getX(),
            basePos.getY() - center.getY(),
            basePos.getZ() - center.getZ()
        );
    }

    private List<BlockPos> computeXzOffsets() {
        List<BlockPos> offsets = new ArrayList<>();
        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                if (x * x + z * z <= radiusSquared) {
                    offsets.add(new BlockPos(x, 0, z));
                }
            }
        }
        return List.copyOf(offsets);
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
