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

public class BoxShape implements GeometryShape {
    private final int length;
    private final int width;
    private final int height;
    private BlockPos center;

    public BoxShape(int length, int width, int height, BlockPos center) {
        if (length <= 0 || width <= 0 || height <= 0) {
            throw new IllegalArgumentException("Box length, width, and height must be positive");
        }
        this.length = length;
        this.width = width;
        this.height = height;
        this.center = center;
    }

    @Override
    public Box getBoundingBox(BlockPos basePos) {
        return new Box(
            computeMinX(basePos), computeMinY(basePos), computeMinZ(basePos),
            computeMaxX(basePos) + 1, computeMaxY(basePos) + 1, computeMaxZ(basePos) + 1
        );
    }

    @Override
    public void renderOutline(MatrixStack matrices, VertexConsumer vertexConsumer, Vec3d center, float red, float green, float blue, float alpha) {
        // 实现轮廓渲染逻辑
    }

    @Override
    public void renderSolid(MatrixStack matrices, VertexConsumer vertexConsumer, Vec3d center, float red, float green, float blue, float alpha) {
        // 实现实体渲染逻辑
    }

    @Override
    public boolean isInside(Vec3d pos) {
        return isInsideAt(pos, center);
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
        if (y < computeMinY(basePos) || y > computeMaxY(basePos)) {
            return List.of();
        }

        List<BlockPos> blocks = new ArrayList<>();
        for (int x = computeMinX(basePos); x <= computeMaxX(basePos); x++) {
            for (int z = computeMinZ(basePos); z <= computeMaxZ(basePos); z++) {
                blocks.add(new BlockPos(x, y, z));
            }
        }
        return blocks;
    }

    @Override
    public List<BlockPos> getBlocksInRadius(Vec3d queryCenter, int maxDistance) {
        List<BlockPos> blocks = new ArrayList<>();
        BlockPos queryBlock = BlockPos.ofFloored(queryCenter);
        double maxDistanceSquared = (double) maxDistance * maxDistance;

        Box boxBounds = getBoundingBox(center);
        int minX = Math.max(queryBlock.getX() - maxDistance, (int) Math.floor(boxBounds.minX));
        int maxX = Math.min(queryBlock.getX() + maxDistance, (int) Math.ceil(boxBounds.maxX));
        int minY = Math.max(queryBlock.getY() - maxDistance, (int) Math.floor(boxBounds.minY));
        int maxY = Math.min(queryBlock.getY() + maxDistance, (int) Math.ceil(boxBounds.maxY));
        int minZ = Math.max(queryBlock.getZ() - maxDistance, (int) Math.floor(boxBounds.minZ));
        int maxZ = Math.min(queryBlock.getZ() + maxDistance, (int) Math.ceil(boxBounds.maxZ));

        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    if (isInside(pos) && Vec3d.ofCenter(pos).squaredDistanceTo(queryCenter) <= maxDistanceSquared) {
                        blocks.add(pos);
                    }
                }
            }
        }

        return blocks;
    }

    @Override
    public boolean isWithinBounds(BlockPos pos, BlockPos basePos) {
        return isInsideAt(Vec3d.ofCenter(pos), basePos);
    }

    @Override
    public void setCenter(BlockPos newCenter) {
        this.center = newCenter;
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
        List<BlockPos> blocks = new ArrayList<>();
        for (int y = computeMinY(center); y <= computeMaxY(center); y++) {
            blocks.addAll(getBlocksInLayer(center, y));
        }
        return blocks;
    }

    private int computeMinX(BlockPos basePos) {
        return basePos.getX() - (length - 1) / 2;
    }

    private int computeMaxX(BlockPos basePos) {
        return computeMinX(basePos) + length - 1;
    }

    private int computeMinY(BlockPos basePos) {
        return basePos.getY() - (height - 1) / 2;
    }

    private int computeMaxY(BlockPos basePos) {
        return computeMinY(basePos) + height - 1;
    }

    private int computeMinZ(BlockPos basePos) {
        return basePos.getZ() - (width - 1) / 2;
    }

    private int computeMaxZ(BlockPos basePos) {
        return computeMinZ(basePos) + width - 1;
    }

    private boolean isInsideAt(Vec3d pos, BlockPos basePos) {
        return pos.x >= computeMinX(basePos) && pos.x < computeMaxX(basePos) + 1
            && pos.y >= computeMinY(basePos) && pos.y < computeMaxY(basePos) + 1
            && pos.z >= computeMinZ(basePos) && pos.z < computeMaxZ(basePos) + 1;
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
