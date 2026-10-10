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
 * 正八面体形状类
 */
public class OctahedronShape implements GeometryShape {
    private final int radius;
    private BlockPos center;

    public OctahedronShape(int radius, BlockPos center) {
        if (radius <= 0) {
            throw new IllegalArgumentException("Octahedron radius must be positive");
        }
        this.radius = radius;
        this.center = center;
    }

    @Override
    public Box getBoundingBox(BlockPos basePos) {
        return new Box(
            basePos.getX() - radius,
            basePos.getY() - radius,
            basePos.getZ() - radius,
            basePos.getX() + radius + 1,
            basePos.getY() + radius + 1,
            basePos.getZ() + radius + 1
        );
    }

    @Override
    public void renderOutline(MatrixStack matrices, VertexConsumer vertexConsumer, Vec3d center, float red, float green, float blue, float alpha) {
        // 正八面体线框渲染 - 由WireframeRenderer处理
    }

    @Override
    public void renderSolid(MatrixStack matrices, VertexConsumer vertexConsumer, Vec3d center, float red, float green, float blue, float alpha) {
        // 正八面体实体渲染 - 由PointCloudRenderer处理
    }

    @Override
    public boolean isInside(Vec3d pos) {
        return isInsideAtVec3d(pos, center);
    }

    @Override
    public boolean isInside(BlockPos pos) {
        return isInsideAtBlock(pos, center);
    }

    @Override
    public int getMinY(BlockPos basePos) {
        return basePos.getY() - radius;
    }

    @Override
    public int getMaxY(BlockPos basePos) {
        return basePos.getY() + radius;
    }

    @Override
    public List<BlockPos> getBlocksInLayer(BlockPos basePos, int y) {
        List<BlockPos> positions = new ArrayList<>();
        int yDistance = Math.abs(y - basePos.getY());
        int maxXZ = radius - yDistance;

        if (maxXZ >= 0) {
            for (int x = basePos.getX() - maxXZ; x <= basePos.getX() + maxXZ; x++) {
                for (int z = basePos.getZ() - maxXZ; z <= basePos.getZ() + maxXZ; z++) {
                    int distance = Math.abs(x - basePos.getX()) + yDistance + Math.abs(z - basePos.getZ());
                    if (distance <= radius) {
                        positions.add(new BlockPos(x, y, z));
                    }
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

        Box octahedronBounds = getBoundingBox(center);
        int minX = Math.max(queryBlock.getX() - maxDistance, (int) Math.floor(octahedronBounds.minX));
        int maxX = Math.min(queryBlock.getX() + maxDistance, (int) Math.ceil(octahedronBounds.maxX));
        int minY = Math.max(queryBlock.getY() - maxDistance, (int) Math.floor(octahedronBounds.minY));
        int maxY = Math.min(queryBlock.getY() + maxDistance, (int) Math.ceil(octahedronBounds.maxY));
        int minZ = Math.max(queryBlock.getZ() - maxDistance, (int) Math.floor(octahedronBounds.minZ));
        int maxZ = Math.min(queryBlock.getZ() + maxDistance, (int) Math.ceil(octahedronBounds.maxZ));

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
        return isInsideAtBlock(pos, basePos);
    }

    @Override
    public void setCenter(BlockPos center) {
        this.center = center;
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
        for (int y = getMinY(center); y <= getMaxY(center); y++) {
            positions.addAll(getBlocksInLayer(center, y));
        }
        return positions;
    }

    public int getRadius() {
        return radius;
    }

    private int manhattanBlockDistance(BlockPos pos, BlockPos reference) {
        return Math.abs(pos.getX() - reference.getX())
            + Math.abs(pos.getY() - reference.getY())
            + Math.abs(pos.getZ() - reference.getZ());
    }

    private double manhattanVecDistance(Vec3d pos, BlockPos reference) {
        return Math.abs(pos.x - reference.getX())
            + Math.abs(pos.y - reference.getY())
            + Math.abs(pos.z - reference.getZ());
    }

    private boolean isInsideAtBlock(BlockPos pos, BlockPos basePos) {
        return manhattanBlockDistance(pos, basePos) <= radius;
    }

    private boolean isInsideAtVec3d(Vec3d pos, BlockPos basePos) {
        return manhattanVecDistance(pos, basePos) <= radius;
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
