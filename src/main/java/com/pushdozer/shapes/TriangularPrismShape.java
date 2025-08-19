package com.pushdozer.shapes;

import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class TriangularPrismShape implements GeometryShape {
    private final double sideLength; // 等边三角形的边长
    private final double height;
    private Vec3d prismCenter;
    private BlockPos center;

    public TriangularPrismShape(double sideLength, double height, BlockPos center) {
        this.sideLength = sideLength;
        this.height = height;
        this.center = center;
        this.prismCenter = Vec3d.ofCenter(center);
    }

    public TriangularPrismShape(double sideLength, double height, Vec3d center) {
        this.sideLength = sideLength;
        this.height = height;
        this.prismCenter = center;
        this.center = new BlockPos((int) Math.floor(center.x), (int) Math.floor(center.y), (int) Math.floor(center.z));
    }

    @Override
    public BlockPos getCenter() {
        return center;
    }

    @Override
    public void setCenter(BlockPos newCenter) {
        if (newCenter == null) {
            throw new IllegalArgumentException("The central location cannot be empty");
        }
        this.center = newCenter;
        this.prismCenter = Vec3d.ofCenter(newCenter);
    }

    @Override
    public void renderOutline(MatrixStack matrices, VertexConsumer vertexConsumer, Vec3d center, float red, float green, float blue, float alpha) {
        matrices.push();
        matrices.translate(center.x, center.y, center.z);

        // 三棱柱的6个顶点
        Vec3d[] bottomVertices = getBottomVertices();
        Vec3d[] topVertices = getTopVertices();

        // 绘制底面三角形的边
        drawEdge(vertexConsumer, matrices, bottomVertices[0], bottomVertices[1], red, green, blue, alpha);
        drawEdge(vertexConsumer, matrices, bottomVertices[1], bottomVertices[2], red, green, blue, alpha);
        drawEdge(vertexConsumer, matrices, bottomVertices[2], bottomVertices[0], red, green, blue, alpha);

        // 绘制顶面三角形的边
        drawEdge(vertexConsumer, matrices, topVertices[0], topVertices[1], red, green, blue, alpha);
        drawEdge(vertexConsumer, matrices, topVertices[1], topVertices[2], red, green, blue, alpha);
        drawEdge(vertexConsumer, matrices, topVertices[2], topVertices[0], red, green, blue, alpha);

        // 绘制连接底面和顶面的边
        drawEdge(vertexConsumer, matrices, bottomVertices[0], topVertices[0], red, green, blue, alpha);
        drawEdge(vertexConsumer, matrices, bottomVertices[1], topVertices[1], red, green, blue, alpha);
        drawEdge(vertexConsumer, matrices, bottomVertices[2], topVertices[2], red, green, blue, alpha);

        matrices.pop();
    }

    @Override
    public void renderSolid(MatrixStack matrices, VertexConsumer vertexConsumer, Vec3d center, float red, float green, float blue, float alpha) {
        matrices.push();
        matrices.translate(center.x, center.y, center.z);

        // 三棱柱的6个顶点
        Vec3d[] bottomVertices = getBottomVertices();
        Vec3d[] topVertices = getTopVertices();

        // 渲染底面三角形
        renderTriangleFace(vertexConsumer, matrices, bottomVertices[0], bottomVertices[1], bottomVertices[2], red, green, blue, alpha);

        // 渲染顶面三角形
        renderTriangleFace(vertexConsumer, matrices, topVertices[0], topVertices[1], topVertices[2], red, green, blue, alpha);

        // 渲染3个矩形侧面
        renderRectangleFace(vertexConsumer, matrices, bottomVertices[0], bottomVertices[1], topVertices[1], topVertices[0], red, green, blue, alpha);
        renderRectangleFace(vertexConsumer, matrices, bottomVertices[1], bottomVertices[2], topVertices[2], topVertices[1], red, green, blue, alpha);
        renderRectangleFace(vertexConsumer, matrices, bottomVertices[2], bottomVertices[0], topVertices[0], topVertices[2], red, green, blue, alpha);

        matrices.pop();
    }

    @Override
    public boolean isInside(Vec3d pos) {
        // 三棱柱内部检测 - 使用三角形检测
        Vec3d relativePos = pos.subtract(prismCenter);
        
        // 检查Y坐标是否在高度范围内
        if (Math.abs(relativePos.y) > height / 2) {
            return false;
        }
        
        // 检查XZ平面是否在等边三角形内
        return isPointInTriangle(relativePos.x, relativePos.z, sideLength);
    }
    
    /**
     * 检查点是否在等边三角形内
     * @param x 点的X坐标
     * @param z 点的Z坐标
     * @param sideLength 等边三角形的边长
     * @return 是否在三角形内
     */
    private boolean isPointInTriangle(double x, double z, double sideLength) {
        // 等边三角形的高
        double height = sideLength * Math.sqrt(3) / 2;
        
        // 三角形的三个顶点（以中心为原点）
        double[] xCoords = {0, -sideLength/2, sideLength/2};
        double[] zCoords = {height/2, -height/2, -height/2};
        
        // 使用重心坐标法判断点是否在三角形内
        double area = height * sideLength / 2; // 三角形面积
        
        // 计算三个子三角形的面积
        double area1 = Math.abs((xCoords[1] - x) * (zCoords[2] - z) - (xCoords[2] - x) * (zCoords[1] - z)) / 2;
        double area2 = Math.abs((xCoords[2] - x) * (zCoords[0] - z) - (xCoords[0] - x) * (zCoords[2] - z)) / 2;
        double area3 = Math.abs((xCoords[0] - x) * (zCoords[1] - z) - (xCoords[1] - x) * (zCoords[0] - z)) / 2;
        
        // 如果三个子三角形面积之和等于原三角形面积，则点在三角形内
        return Math.abs(area1 + area2 + area3 - area) < 0.001;
    }

    @Override
    public boolean isInside(BlockPos pos) {
        return isInside(Vec3d.ofCenter(pos));
    }

    @Override
    public Box getBoundingBox(BlockPos basePos) {
        return new Box(
            basePos.getX() - sideLength, basePos.getY() - height / 2, basePos.getZ() - sideLength,
            basePos.getX() + sideLength, basePos.getY() + height / 2, basePos.getZ() + sideLength
        );
    }

    @Override
    public int getMinY(BlockPos basePos) {
        return (int) (basePos.getY() - height / 2);
    }

    @Override
    public int getMaxY(BlockPos basePos) {
        return (int) (basePos.getY() + height / 2);
    }

    @Override
    public List<BlockPos> getBlocksInLayer(BlockPos basePos, int y) {
        List<BlockPos> blocks = new ArrayList<>();
        Box boundingBox = getBoundingBox(basePos);
        
        for (int x = (int) boundingBox.minX; x <= boundingBox.maxX; x++) {
            for (int z = (int) boundingBox.minZ; z <= boundingBox.maxZ; z++) {
                BlockPos pos = new BlockPos(x, y, z);
                if (isInside(pos)) {
                    blocks.add(pos);
                }
            }
        }
        
        return blocks;
    }

    @Override
    public List<BlockPos> getBlocksInRadius(Vec3d center, int maxDistance) {
        List<BlockPos> blocks = new ArrayList<>();
        BlockPos basePos = new BlockPos((int) center.x, (int) center.y, (int) center.z);
        
        for (int y = getMinY(basePos); y <= getMaxY(basePos); y++) {
            blocks.addAll(getBlocksInLayer(basePos, y));
        }
        
        return blocks;
    }

    @Override
    public boolean isWithinBounds(BlockPos pos, BlockPos basePos) {
        return isInside(pos);
    }

    @Override
    public List<BlockPos> getBlocks() {
        return getBlockPositions();
    }

    @Override
    public Iterator<BlockPos> getBlocksIterator() {
        return getBlocks().iterator();
    }

    public double getSideLength() {
        return sideLength;
    }

    public double getHeight() {
        return height;
    }

    @Override
    public List<BlockPos> getBlockPositions() {
        List<BlockPos> blocks = new ArrayList<>();
        Box boundingBox = getBoundingBox(center);
        
        for (int x = (int) boundingBox.minX; x <= boundingBox.maxX; x++) {
            for (int y = (int) boundingBox.minY; y <= boundingBox.maxY; y++) {
                for (int z = (int) boundingBox.minZ; z <= boundingBox.maxZ; z++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    if (isInside(pos)) {
                        blocks.add(pos);
                    }
                }
            }
        }
        
        return blocks;
    }

    private Vec3d[] getBottomVertices() {
        // 等边三角形的3个顶点
        // 等边三角形的高 = sideLength * sqrt(3) / 2
        double triangleHeight = sideLength * Math.sqrt(3) / 2;
        
        return new Vec3d[]{
            new Vec3d(0, -this.height / 2, triangleHeight / 2),                    // 顶部顶点
            new Vec3d(-sideLength / 2, -this.height / 2, -triangleHeight / 2),     // 左下顶点
            new Vec3d(sideLength / 2, -this.height / 2, -triangleHeight / 2)       // 右下顶点
        };
    }

    private Vec3d[] getTopVertices() {
        // 等边三角形的3个顶点
        // 等边三角形的高 = sideLength * sqrt(3) / 2
        double triangleHeight = sideLength * Math.sqrt(3) / 2;
        
        return new Vec3d[]{
            new Vec3d(0, this.height / 2, triangleHeight / 2),                    // 顶部顶点
            new Vec3d(-sideLength / 2, this.height / 2, -triangleHeight / 2),     // 左下顶点
            new Vec3d(sideLength / 2, this.height / 2, -triangleHeight / 2)       // 右下顶点
        };
    }

    private void drawEdge(VertexConsumer vertexConsumer, MatrixStack matrices, Vec3d start, Vec3d end, 
                         float red, float green, float blue, float alpha) {
        Vec3d normal = end.subtract(start).normalize();
        
        vertexConsumer.vertex(matrices.peek().getPositionMatrix(), (float) start.x, (float) start.y, (float) start.z)
            .color(red, green, blue, alpha)
            .normal((float) normal.x, (float) normal.y, (float) normal.z);
        
        vertexConsumer.vertex(matrices.peek().getPositionMatrix(), (float) end.x, (float) end.y, (float) end.z)
            .color(red, green, blue, alpha)
            .normal((float) normal.x, (float) normal.y, (float) normal.z);
    }

    private void renderTriangleFace(VertexConsumer vertexConsumer, MatrixStack matrices, 
                                  Vec3d v1, Vec3d v2, Vec3d v3, 
                                  float red, float green, float blue, float alpha) {
        // 计算法线
        Vec3d edge1 = v2.subtract(v1);
        Vec3d edge2 = v3.subtract(v1);
        Vec3d normal = edge1.crossProduct(edge2).normalize();
        
        // 渲染三角形
        addVertex(vertexConsumer, matrices, v1, normal, red, green, blue, alpha);
        addVertex(vertexConsumer, matrices, v2, normal, red, green, blue, alpha);
        addVertex(vertexConsumer, matrices, v3, normal, red, green, blue, alpha);
    }

    private void renderRectangleFace(VertexConsumer vertexConsumer, MatrixStack matrices, 
                                   Vec3d v1, Vec3d v2, Vec3d v3, Vec3d v4, 
                                   float red, float green, float blue, float alpha) {
        // 计算法线
        Vec3d edge1 = v2.subtract(v1);
        Vec3d edge2 = v3.subtract(v1);
        Vec3d normal = edge1.crossProduct(edge2).normalize();
        
        // 渲染矩形（两个三角形）
        addVertex(vertexConsumer, matrices, v1, normal, red, green, blue, alpha);
        addVertex(vertexConsumer, matrices, v2, normal, red, green, blue, alpha);
        addVertex(vertexConsumer, matrices, v3, normal, red, green, blue, alpha);
        
        addVertex(vertexConsumer, matrices, v1, normal, red, green, blue, alpha);
        addVertex(vertexConsumer, matrices, v3, normal, red, green, blue, alpha);
        addVertex(vertexConsumer, matrices, v4, normal, red, green, blue, alpha);
    }

    private void addVertex(VertexConsumer vertexConsumer, MatrixStack matrices, Vec3d pos, Vec3d normal, 
                          float red, float green, float blue, float alpha) {
        vertexConsumer.vertex(matrices.peek().getPositionMatrix(), (float) pos.x, (float) pos.y, (float) pos.z)
            .color(red, green, blue, alpha)
            .texture(0.5f, 0.5f)
            .overlay(OverlayTexture.DEFAULT_UV)
            .light(15728880)
            .normal((float) normal.x, (float) normal.y, (float) normal.z);
    }
}
