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
 * 圆柱体形状类
 */
public class CylinderShape implements GeometryShape {
    private final int radius;
    private final int height;
    private BlockPos center;

    public CylinderShape(int radius, int height, BlockPos center) {
        this.radius = radius;
        this.height = height;
        this.center = center;
    }

    @Override
    public Box getBoundingBox(BlockPos basePos) {
        return new Box(
            basePos.getX() - radius,
            basePos.getY() - height / 2,
            basePos.getZ() - radius,
            basePos.getX() + radius + 1,
            basePos.getY() + height / 2 + 1,
            basePos.getZ() + radius + 1
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
        double dx = pos.x - center.getX();
        double dz = pos.z - center.getZ();
        double distanceXZ = Math.sqrt(dx * dx + dz * dz);
        
        return distanceXZ <= radius && 
               pos.y >= center.getY() - height / 2 && 
               pos.y <= center.getY() + height / 2;
    }

    @Override
    public boolean isInside(BlockPos pos) {
        double dx = pos.getX() - center.getX();
        double dz = pos.getZ() - center.getZ();
        double distanceXZ = Math.sqrt(dx * dx + dz * dz);
        
        return distanceXZ <= radius && 
               pos.getY() >= center.getY() - height / 2 && 
               pos.getY() <= center.getY() + height / 2;
    }

    @Override
    public int getMinY(BlockPos basePos) {
        return basePos.getY() - height / 2;
    }

    @Override
    public int getMaxY(BlockPos basePos) {
        return basePos.getY() + height / 2;
    }

    @Override
    public List<BlockPos> getBlocksInLayer(BlockPos basePos, int y) {
        List<BlockPos> positions = new ArrayList<>();
        
        // 检查Y坐标是否在高度范围内（世界坐标）
        if (y >= basePos.getY() - height / 2 && y <= basePos.getY() + height / 2) {
            for (int x = basePos.getX() - radius; x <= basePos.getX() + radius; x++) {
                for (int z = basePos.getZ() - radius; z <= basePos.getZ() + radius; z++) {
                    double dx = x - basePos.getX();
                    double dz = z - basePos.getZ();
                    double distance = Math.sqrt(dx * dx + dz * dz);
                    
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
        
        int cylinderCenterY = this.center.getY();
        int minY = cylinderCenterY - height / 2;
        int maxY = cylinderCenterY + height / 2;
        
        int searchRadius = Math.min(radius, maxDistance);
        
        for (int y = Math.max(minY, centerPos.getY() - maxDistance); y <= Math.min(maxY, centerPos.getY() + maxDistance); y++) {
            for (int x = this.center.getX() - searchRadius; x <= this.center.getX() + searchRadius; x++) {
                for (int z = this.center.getZ() - searchRadius; z <= this.center.getZ() + searchRadius; z++) {
                    double dx = x - this.center.getX();
                    double dz = z - this.center.getZ();
                    double distance = Math.sqrt(dx * dx + dz * dz);
                    
                    if (distance <= searchRadius) {
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
        
        int minY = centerY - height / 2;
        int maxY = centerY + height / 2;
        
        for (int y = minY; y <= maxY; y++) {
            for (int x = centerX - radius; x <= centerX + radius; x++) {
                for (int z = centerZ - radius; z <= centerZ + radius; z++) {
                    double dx = x - centerX;
                    double dz = z - centerZ;
                    double distance = Math.sqrt(dx * dx + dz * dz);
                    
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

    public int getHeight() {
        return height;
    }
} 