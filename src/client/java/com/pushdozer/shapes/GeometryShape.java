package com.pushdozer.shapes;

import java.util.Iterator;
import java.util.List;

import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

public interface GeometryShape {
    Box getBoundingBox(BlockPos basePos);
    void renderOutline(MatrixStack matrices, VertexConsumer vertexConsumer, Vec3d center, float red, float green, float blue, float alpha);
    void renderSolid(MatrixStack matrices, VertexConsumer vertexConsumer, Vec3d center, float red, float green, float blue, float alpha);
    boolean isInside(Vec3d pos);
    boolean isInside(BlockPos pos);
    
    int getMinY(BlockPos basePos);
    int getMaxY(BlockPos basePos);
    List<BlockPos> getBlocksInLayer(BlockPos basePos, int y);
    List<BlockPos> getBlocksInRadius(Vec3d center, int maxDistance);
    
    boolean isWithinBounds(BlockPos pos, BlockPos basePos);
    void setCenter(BlockPos center);
    List<BlockPos> getBlocks();
    Iterator<BlockPos> getBlocksIterator();
    BlockPos getCenter();
    List<BlockPos> getBlockPositions();
}