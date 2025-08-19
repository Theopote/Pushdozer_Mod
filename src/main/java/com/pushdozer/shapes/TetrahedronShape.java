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

public class TetrahedronShape implements GeometryShape {
    private final double edgeLength; // 正四面体的边长
    private Vec3d tetrahedronCenter;
    private BlockPos center;

    public TetrahedronShape(double edgeLength, BlockPos center) {
        this.edgeLength = edgeLength;
        this.center = center;
        this.tetrahedronCenter = Vec3d.ofCenter(center);
    }

    public TetrahedronShape(double edgeLength, Vec3d center) {
        this.edgeLength = edgeLength;
        this.tetrahedronCenter = center;
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
        this.tetrahedronCenter = Vec3d.ofCenter(newCenter);
    }

    @Override
    public void renderOutline(MatrixStack matrices, VertexConsumer vertexConsumer, Vec3d center, float red, float green, float blue, float alpha) {
        matrices.push();
        matrices.translate(center.x, center.y, center.z);

        // 正四面体的4个顶点
        Vec3d[] vertices = getTetrahedronVertices();

        // 绘制6条边
        drawEdge(vertexConsumer, matrices, vertices[0], vertices[1], red, green, blue, alpha);
        drawEdge(vertexConsumer, matrices, vertices[0], vertices[2], red, green, blue, alpha);
        drawEdge(vertexConsumer, matrices, vertices[0], vertices[3], red, green, blue, alpha);
        drawEdge(vertexConsumer, matrices, vertices[1], vertices[2], red, green, blue, alpha);
        drawEdge(vertexConsumer, matrices, vertices[1], vertices[3], red, green, blue, alpha);
        drawEdge(vertexConsumer, matrices, vertices[2], vertices[3], red, green, blue, alpha);

        matrices.pop();
    }

    @Override
    public void renderSolid(MatrixStack matrices, VertexConsumer vertexConsumer, Vec3d center, float red, float green, float blue, float alpha) {
        matrices.push();
        matrices.translate(center.x, center.y, center.z);

        // 正四面体的4个顶点
        Vec3d[] vertices = getTetrahedronVertices();

        // 渲染4个三角形面
        renderTriangleFace(vertexConsumer, matrices, vertices[0], vertices[1], vertices[2], red, green, blue, alpha);
        renderTriangleFace(vertexConsumer, matrices, vertices[0], vertices[2], vertices[3], red, green, blue, alpha);
        renderTriangleFace(vertexConsumer, matrices, vertices[0], vertices[3], vertices[1], red, green, blue, alpha);
        renderTriangleFace(vertexConsumer, matrices, vertices[1], vertices[3], vertices[2], red, green, blue, alpha);

        matrices.pop();
    }

    @Override
    public boolean isInside(Vec3d pos) {
        // 正四面体内部检测 - 使用重心坐标法
        Vec3d relativePos = pos.subtract(tetrahedronCenter);
        return isPointInTetrahedron(relativePos.x, relativePos.y, relativePos.z);
    }
    
    /**
     * 检查点是否在正四面体内部
     * @param x 点的X坐标（相对于中心）
     * @param y 点的Y坐标（相对于中心）
     * @param z 点的Z坐标（相对于中心）
     * @return 是否在正四面体内部
     */
    private boolean isPointInTetrahedron(double x, double y, double z) {
        // 正四面体的4个顶点（相对于中心）
        double scale = edgeLength / (2.0 * Math.sqrt(2.0));
        
        Vec3d[] vertices = {
            new Vec3d(scale, scale, scale),                    // 顶点1
            new Vec3d(scale, -scale, -scale),                  // 顶点2
            new Vec3d(-scale, scale, -scale),                  // 顶点3
            new Vec3d(-scale, -scale, scale)                   // 顶点4
        };
        
        // 使用重心坐标法判断点是否在四面体内部
        return isPointInTetrahedronByBarycentric(x, y, z, vertices);
    }
    
    /**
     * 使用重心坐标法判断点是否在四面体内部
     */
    private boolean isPointInTetrahedronByBarycentric(double x, double y, double z, Vec3d[] vertices) {
        // 计算四面体的体积
        double volume = calculateTetrahedronVolume(vertices[0], vertices[1], vertices[2], vertices[3]);
        
        // 计算四个子四面体的体积
        double volume1 = calculateTetrahedronVolume(new Vec3d(x, y, z), vertices[1], vertices[2], vertices[3]);
        double volume2 = calculateTetrahedronVolume(vertices[0], new Vec3d(x, y, z), vertices[2], vertices[3]);
        double volume3 = calculateTetrahedronVolume(vertices[0], vertices[1], new Vec3d(x, y, z), vertices[3]);
        double volume4 = calculateTetrahedronVolume(vertices[0], vertices[1], vertices[2], new Vec3d(x, y, z));
        
        // 如果四个子四面体体积之和等于原四面体体积，则点在四面体内部
        double totalVolume = volume1 + volume2 + volume3 + volume4;
        return Math.abs(totalVolume - volume) < 0.001;
    }
    
    /**
     * 计算四面体的体积
     */
    private double calculateTetrahedronVolume(Vec3d v1, Vec3d v2, Vec3d v3, Vec3d v4) {
        // 使用行列式计算四面体体积
        // V = |det(v2-v1, v3-v1, v4-v1)| / 6
        
        Vec3d edge1 = v2.subtract(v1);
        Vec3d edge2 = v3.subtract(v1);
        Vec3d edge3 = v4.subtract(v1);
        
        // 计算行列式
        double det = edge1.x * (edge2.y * edge3.z - edge2.z * edge3.y) -
                    edge1.y * (edge2.x * edge3.z - edge2.z * edge3.x) +
                    edge1.z * (edge2.x * edge3.y - edge2.y * edge3.x);
        
        return Math.abs(det) / 6.0;
    }

    @Override
    public boolean isInside(BlockPos pos) {
        return isInside(Vec3d.ofCenter(pos));
    }

    @Override
    public Box getBoundingBox(BlockPos basePos) {
        double halfSize = edgeLength / 2.0;
        return new Box(
            basePos.getX() - halfSize, basePos.getY() - halfSize, basePos.getZ() - halfSize,
            basePos.getX() + halfSize, basePos.getY() + halfSize, basePos.getZ() + halfSize
        );
    }

    @Override
    public int getMinY(BlockPos basePos) {
        return (int) (basePos.getY() - edgeLength / 2.0);
    }

    @Override
    public int getMaxY(BlockPos basePos) {
        return (int) (basePos.getY() + edgeLength / 2.0);
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

    public double getRadius() {
        return edgeLength;
    }

    public double getEdgeLength() {
        return edgeLength;
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

    private Vec3d[] getTetrahedronVertices() {
        // 正四面体的4个顶点
        // 使用正确的正四面体顶点坐标，边长为a的正四面体：
        // 顶点1: (a/(2*sqrt(2)), a/(2*sqrt(2)), a/(2*sqrt(2)))
        // 顶点2: (a/(2*sqrt(2)), -a/(2*sqrt(2)), -a/(2*sqrt(2)))
        // 顶点3: (-a/(2*sqrt(2)), a/(2*sqrt(2)), -a/(2*sqrt(2)))
        // 顶点4: (-a/(2*sqrt(2)), -a/(2*sqrt(2)), a/(2*sqrt(2)))
        double scale = edgeLength / (2.0 * Math.sqrt(2.0));
        
        return new Vec3d[]{
            new Vec3d(scale, scale, scale),                    // 顶点1
            new Vec3d(scale, -scale, -scale),                  // 顶点2
            new Vec3d(-scale, scale, -scale),                  // 顶点3
            new Vec3d(-scale, -scale, scale)                   // 顶点4
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
