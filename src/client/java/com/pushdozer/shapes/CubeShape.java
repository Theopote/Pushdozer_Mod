package com.pushdozer.shapes;

import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class CubeShape implements GeometryShape {
    private final BlockPos start;
    private final BlockPos end;
    private BlockPos center;

    public CubeShape(BlockPos start, BlockPos end) {
        this.start = start;
        this.end = end;
        this.center = new BlockPos(
            (start.getX() + end.getX()) / 2,
            (start.getY() + end.getY()) / 2,
            (start.getZ() + end.getZ()) / 2
        );
    }

    @Override
    public List<BlockPos> getBlockPositions() {
        List<BlockPos> positions = new ArrayList<>();
        for (int x = Math.min(start.getX(), end.getX()); x <= Math.max(start.getX(), end.getX()); x++) {
            for (int y = Math.min(start.getY(), end.getY()); y <= Math.max(start.getY(), end.getY()); y++) {
                for (int z = Math.min(start.getZ(), end.getZ()); z <= Math.max(start.getZ(), end.getZ()); z++) {
                    positions.add(new BlockPos(x, y, z));
                }
            }
        }
        return positions;
    }

    @Override
    public Box getBoundingBox(BlockPos basePos) {
        Vec3d startVec = new Vec3d(start.getX(), start.getY(), start.getZ());
        Vec3d endVec = new Vec3d(end.getX() + 1, end.getY() + 1, end.getZ() + 1);
        return new Box(startVec, endVec);
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
        return pos.x >= start.getX() && pos.x <= end.getX() &&
               pos.y >= start.getY() && pos.y <= end.getY() &&
               pos.z >= start.getZ() && pos.z <= end.getZ();
    }

    @Override
    public boolean isInside(BlockPos pos) {
        return pos.getX() >= start.getX() && pos.getX() <= end.getX() &&
               pos.getY() >= start.getY() && pos.getY() <= end.getY() &&
               pos.getZ() >= start.getZ() && pos.getZ() <= end.getZ();
    }

    @Override
    public int getMinY(BlockPos basePos) {
        return Math.min(start.getY(), end.getY());
    }

    @Override
    public int getMaxY(BlockPos basePos) {
        return Math.max(start.getY(), end.getY());
    }

    @Override
    public List<BlockPos> getBlocksInLayer(BlockPos basePos, int y) {
        List<BlockPos> layerBlocks = new ArrayList<>();
        for (int x = Math.min(start.getX(), end.getX()); x <= Math.max(start.getX(), end.getX()); x++) {
            for (int z = Math.min(start.getZ(), end.getZ()); z <= Math.max(start.getZ(), end.getZ()); z++) {
                layerBlocks.add(new BlockPos(x, y, z));
            }
        }
        return layerBlocks;
    }

    @Override
    public List<BlockPos> getBlocksInRadius(Vec3d center, int maxDistance) {
        // 实现获取指定半径内的方块逻辑
        return new ArrayList<>();
    }

    @Override
    public boolean isWithinBounds(BlockPos pos, BlockPos basePos) {
        return isInside(pos);
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
        return getBlockPositions().iterator();
    }

    @Override
    public BlockPos getCenter() {
        return center;
    }
}