package com.pushdozer.shapes;

import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class BoxShape implements GeometryShape {
    private Box box;
    private BlockPos center;

    public BoxShape(int length, int width, int height, BlockPos center) {
        this.center = center;
        updateBox(length, width, height);
    }

    private void updateBox(int length, int width, int height) {
        double centerX = center.getX() + 0.5;
        double centerY = center.getY() + 0.5;
        double centerZ = center.getZ() + 0.5;
        
        double halfLength = length / 2.0;
        double halfWidth = width / 2.0;
        double halfHeight = height / 2.0;
        
        this.box = new Box(
            centerX - halfLength, centerY - halfHeight, centerZ - halfWidth,
            centerX + halfLength, centerY + halfHeight, centerZ + halfWidth
        );
    }

    @Override
    public Box getBoundingBox(BlockPos basePos) {
        return this.box.offset(basePos.getX() - center.getX(), basePos.getY() - center.getY(), basePos.getZ() - center.getZ());
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
        return box.contains(pos);
    }

    @Override
    public boolean isInside(BlockPos pos) {
        return box.contains(Vec3d.ofCenter(pos));
    }

    @Override
    public int getMinY(BlockPos basePos) {
        return (int) (basePos.getY() + box.minY - center.getY());
    }

    @Override
    public int getMaxY(BlockPos basePos) {
        return (int) (basePos.getY() + box.maxY - center.getY());
    }

    @Override
    public List<BlockPos> getBlocksInLayer(BlockPos basePos, int y) {
        List<BlockPos> blocks = new ArrayList<>();
        int minX = (int) Math.round(box.minX);
        int maxX = (int) Math.round(box.maxX - 1);
        int minZ = (int) Math.round(box.minZ);
        int maxZ = (int) Math.round(box.maxZ - 1);

        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                blocks.add(new BlockPos(basePos.getX() + x - center.getX(), y, basePos.getZ() + z - center.getZ()));
            }
        }
        return blocks;
    }

    @Override
    public List<BlockPos> getBlocksInRadius(Vec3d center, int maxDistance) {
        return getBlocks();
    }

    @Override
    public boolean isWithinBounds(BlockPos pos, BlockPos basePos) {
        Box offsetBox = box.offset(basePos.getX() - center.getX(), basePos.getY() - center.getY(), basePos.getZ() - center.getZ());
        return offsetBox.contains(Vec3d.ofCenter(pos));
    }

    @Override
    public void setCenter(BlockPos newCenter) {
        Vec3d offset = new Vec3d(
            newCenter.getX() - center.getX(),
            newCenter.getY() - center.getY(),
            newCenter.getZ() - center.getZ()
        );
        this.box = this.box.offset(offset.x, offset.y, offset.z);
        this.center = newCenter;
    }

    @Override
    public List<BlockPos> getBlocks() {
        List<BlockPos> blocks = new ArrayList<>();
        int minX = (int) Math.round(box.minX);
        int maxX = (int) Math.round(box.maxX - 1);
        int minY = (int) Math.round(box.minY);
        int maxY = (int) Math.round(box.maxY - 1);
        int minZ = (int) Math.round(box.minZ);
        int maxZ = (int) Math.round(box.maxZ - 1);

        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    blocks.add(new BlockPos(x, y, z));
                }
            }
        }
        return blocks;
    }

    @Override
    public Iterator<BlockPos> getBlocksIterator() {
        return getBlocks().iterator();
    }

    @Override
    public BlockPos getCenter() {
        return center;
    }

    @Override
    public List<BlockPos> getBlockPositions() {
        return getBlocks();
    }
}