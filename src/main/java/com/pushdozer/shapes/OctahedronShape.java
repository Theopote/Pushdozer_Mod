package com.pushdozer.shapes;

import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * 正八面体形状类
 */
public class OctahedronShape implements GeometryShape {
    private final int radius;
    private BlockPos center;

    public OctahedronShape(int radius, BlockPos center) {
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
        int distance = (int)(Math.abs(pos.x - center.getX()) + 
                           Math.abs(pos.y - center.getY()) + 
                           Math.abs(pos.z - center.getZ()));
        return distance <= radius;
    }

    @Override
    public boolean isInside(BlockPos pos) {
        int distance = Math.abs(pos.getX() - center.getX()) + 
                      Math.abs(pos.getY() - center.getY()) + 
                      Math.abs(pos.getZ() - center.getZ());
        return distance <= radius;
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
    public List<BlockPos> getBlocksInRadius(Vec3d center, int maxDistance) {
        List<BlockPos> positions = new ArrayList<>();
        BlockPos centerPos = BlockPos.ofFloored(center);
        
        for (int x = centerPos.getX() - maxDistance; x <= centerPos.getX() + maxDistance; x++) {
            for (int y = centerPos.getY() - maxDistance; y <= centerPos.getY() + maxDistance; y++) {
                for (int z = centerPos.getZ() - maxDistance; z <= centerPos.getZ() + maxDistance; z++) {
                    int distance = Math.abs(x - centerPos.getX()) + 
                                  Math.abs(y - centerPos.getY()) + 
                                  Math.abs(z - centerPos.getZ());
                    if (distance <= Math.min(radius, maxDistance)) {
                        positions.add(new BlockPos(x, y, z));
                    }
                }
            }
        }
        
        return positions;
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

    @Override
    public List<BlockPos> getBlockPositions() {
        List<BlockPos> positions = new ArrayList<>();
        
        int centerX = center.getX();
        int centerY = center.getY();
        int centerZ = center.getZ();
        
        // 正八面体的距离计算：使用曼哈顿距离（L1距离）
        for (int x = centerX - radius; x <= centerX + radius; x++) {
            for (int y = centerY - radius; y <= centerY + radius; y++) {
                for (int z = centerZ - radius; z <= centerZ + radius; z++) {
                    // 计算到中心的曼哈顿距离
                    int distance = Math.abs(x - centerX) + Math.abs(y - centerY) + Math.abs(z - centerZ);
                    
                    if (distance <= radius) {
                        positions.add(new BlockPos(x, y, z));
                    }
                }
            }
        }
        
        return positions;
    }

    public int getRadius() {
        return radius;
    }
} 