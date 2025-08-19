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
 * 椭球体形状类
 */
public class EllipsoidShape implements GeometryShape {
    private final int radiusX;
    private final int radiusY;
    private final int radiusZ;
    private BlockPos center;

    public EllipsoidShape(int radiusX, int radiusY, int radiusZ, BlockPos center) {
        this.radiusX = radiusX;
        this.radiusY = radiusY;
        this.radiusZ = radiusZ;
        this.center = center;
    }

    @Override
    public Box getBoundingBox(BlockPos basePos) {
        return new Box(
            basePos.getX() - radiusX,
            basePos.getY() - radiusY,
            basePos.getZ() - radiusZ,
            basePos.getX() + radiusX + 1,
            basePos.getY() + radiusY + 1,
            basePos.getZ() + radiusZ + 1
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
        double dx = pos.x - center.getX();
        double dy = pos.y - center.getY();
        double dz = pos.z - center.getZ();
        
        // 椭球体方程: (x/a)² + (y/b)² + (z/c)² <= 1
        double equation = (dx * dx) / (radiusX * radiusX) + 
                         (dy * dy) / (radiusY * radiusY) + 
                         (dz * dz) / (radiusZ * radiusZ);
        
        return equation <= 1.0;
    }

    @Override
    public boolean isInside(BlockPos pos) {
        double dx = pos.getX() - center.getX();
        double dy = pos.getY() - center.getY();
        double dz = pos.getZ() - center.getZ();
        
        // 椭球体方程: (x/a)² + (y/b)² + (z/c)² <= 1
        double equation = (dx * dx) / (radiusX * radiusX) + 
                         (dy * dy) / (radiusY * radiusY) + 
                         (dz * dz) / (radiusZ * radiusZ);
        
        return equation <= 1.0;
    }

    @Override
    public int getMinY(BlockPos basePos) {
        return basePos.getY() - radiusY;
    }

    @Override
    public int getMaxY(BlockPos basePos) {
        return basePos.getY() + radiusY;
    }

    @Override
    public List<BlockPos> getBlocksInLayer(BlockPos basePos, int y) {
        List<BlockPos> positions = new ArrayList<>();
        
        double dy = y - basePos.getY();
        if (Math.abs(dy) <= radiusY) {
            // 计算在当前Y层的椭圆半径
            double yFactor = 1.0 - (dy * dy) / (radiusY * radiusY);
            if (yFactor > 0) {
                int maxX = (int) Math.ceil(radiusX * Math.sqrt(yFactor));
                int maxZ = (int) Math.ceil(radiusZ * Math.sqrt(yFactor));
                
                for (int x = basePos.getX() - maxX; x <= basePos.getX() + maxX; x++) {
                    for (int z = basePos.getZ() - maxZ; z <= basePos.getZ() + maxZ; z++) {
                        if (isInside(new BlockPos(x, y, z))) {
                            positions.add(new BlockPos(x, y, z));
                        }
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
        
        int minX = Math.max(centerPos.getX() - maxDistance, this.center.getX() - radiusX);
        int maxX = Math.min(centerPos.getX() + maxDistance, this.center.getX() + radiusX);
        int minY = Math.max(centerPos.getY() - maxDistance, this.center.getY() - radiusY);
        int maxY = Math.min(centerPos.getY() + maxDistance, this.center.getY() + radiusY);
        int minZ = Math.max(centerPos.getZ() - maxDistance, this.center.getZ() - radiusZ);
        int maxZ = Math.min(centerPos.getZ() + maxDistance, this.center.getZ() + radiusZ);
        
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    if (isInside(pos)) {
                        double distance = Math.sqrt(
                            Math.pow(x - centerPos.getX(), 2) +
                            Math.pow(y - centerPos.getY(), 2) +
                            Math.pow(z - centerPos.getZ(), 2)
                        );
                        if (distance <= maxDistance) {
                            positions.add(pos);
                        }
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
        
        // 椭球体：三个轴的半径可以不同
        for (int x = centerX - radiusX; x <= centerX + radiusX; x++) {
            for (int y = centerY - radiusY; y <= centerY + radiusY; y++) {
                for (int z = centerZ - radiusZ; z <= centerZ + radiusZ; z++) {
                    double dx = x - centerX;
                    double dy = y - centerY;
                    double dz = z - centerZ;
                    
                    // 椭球体方程: (x/a)² + (y/b)² + (z/c)² <= 1
                    double equation = (dx * dx) / (radiusX * radiusX) + 
                                     (dy * dy) / (radiusY * radiusY) + 
                                     (dz * dz) / (radiusZ * radiusZ);
                    
                    if (equation <= 1.0) {
                        positions.add(new BlockPos(x, y, z));
                    }
                }
            }
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
} 