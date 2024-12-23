package com.pushdozer.shapes;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;

public class SphereShape implements GeometryShape {
    private final double radius;
    private Vec3d sphereCenter;
    private BlockPos center;

    public SphereShape(double radius, Vec3d center) {
        this.radius = radius;
        this.sphereCenter = center;
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
        this.sphereCenter = Vec3d.ofCenter(newCenter);
    }

    @Override
    public void renderOutline(MatrixStack matrices, VertexConsumer vertexConsumer, Vec3d center, float red, float green, float blue, float alpha) {
        matrices.push();
        matrices.translate(center.x, center.y, center.z);

        int segments = 64; // 增加分段数以获得更平滑的圆
        float r = (float) radius;

        // 绘制 XY 平面的圆
        drawCircle(matrices, vertexConsumer, segments, r, 0, red, green, blue, alpha);

        // 绘制 XZ 平面的圆
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(90));
        drawCircle(matrices, vertexConsumer, segments, r, 0, red, green, blue, alpha);

        // 绘制 YZ 平面的圆
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(90));
        drawCircle(matrices, vertexConsumer, segments, r, 0, red, green, blue, alpha);

        matrices.pop();
    }

    @Override
    public void renderSolid(MatrixStack matrices, VertexConsumer vertexConsumer, Vec3d center, float red, float green, float blue, float alpha) {
        matrices.push();
        matrices.translate(center.x, center.y, center.z);

        int segments = 32;
        for (int i = 0; i < segments; i++) {
            float theta1 = (float) (i * Math.PI * 2 / segments);
            float theta2 = (float) ((i + 1) * Math.PI * 2 / segments);

            for (int j = 0; j < segments / 2; j++) {
                float phi1 = (float) (j * Math.PI / (segments / 2));
                float phi2 = (float) ((j + 1) * Math.PI / (segments / 2));

                renderSphereFace(matrices, vertexConsumer, theta1, theta2, phi1, phi2, red, green, blue, alpha);
            }
        }

        matrices.pop();
    }

    @Override
    public boolean isInside(Vec3d pos) {
        return pos.squaredDistanceTo(sphereCenter) <= radius * radius;
    }

    @Override
    public boolean isInside(BlockPos pos) {
        return new Vec3d(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5).squaredDistanceTo(sphereCenter) <= radius * radius;
    }
    
    @Override
    public int getMinY(BlockPos basePos) {
        return (int) (basePos.getY() - radius);
    }

    @Override
    public int getMaxY(BlockPos basePos) {
        return (int) (basePos.getY() + radius);
    }

    @Override
    public List<BlockPos> getBlocksInLayer(BlockPos basePos, int y) {
        List<BlockPos> blocks = new ArrayList<>();
        int layerY = y - basePos.getY();
        if (Math.abs(layerY) > radius) {
            return blocks; // 该层没有方块
        }
        int layerRadius = (int) Math.sqrt(radius * radius - layerY * layerY);
        for (int x = -layerRadius; x <= layerRadius; x++) {
            int zLimit = (int) Math.sqrt(layerRadius * layerRadius - x * x);
            for (int z = -zLimit; z <= zLimit; z++) {
                blocks.add(new BlockPos(basePos.getX() + x, y, basePos.getZ() + z));
            }
        }
        return blocks;
    }

    @Override
    public List<BlockPos> getBlocksInRadius(Vec3d center, int maxDistance) {
        List<BlockPos> blocks = new ArrayList<>();
        int minX = (int) Math.floor(sphereCenter.x - radius);
        int maxX = (int) Math.ceil(sphereCenter.x + radius);
        int minY = (int) Math.floor(sphereCenter.y - radius);
        int maxY = (int) Math.ceil(sphereCenter.y + radius);
        int minZ = (int) Math.floor(sphereCenter.z - radius);
        int maxZ = (int) Math.ceil(sphereCenter.z + radius);

        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    if (sphereCenter.squaredDistanceTo(Vec3d.ofCenter(pos)) <= radius * radius) {
                        blocks.add(pos);
                    }
                }
            }
        }

        return blocks;
    }

    @Override
    public Box getBoundingBox(BlockPos basePos) {
        Vec3d worldCenter = sphereCenter.add(Vec3d.ofCenter(basePos));
        return new Box(
            worldCenter.x - radius, worldCenter.y - radius, worldCenter.z - radius,
            worldCenter.x + radius, worldCenter.y + radius, worldCenter.z + radius
        );
    }

    @Override
    public boolean isWithinBounds(BlockPos pos, BlockPos centerPos) {
        return pos.getSquaredDistance(centerPos) <= radius * radius;
    }

    @Override
    public List<BlockPos> getBlocks() {
        return getBlocksInRadius(sphereCenter, (int) Math.ceil(radius));
    }

    @Override
    public Iterator<BlockPos> getBlocksIterator() {
        return getBlocks().iterator();
    }

    @Override
    public List<BlockPos> getBlockPositions() {
        List<BlockPos> blocks = new ArrayList<>();
        int minX = (int) Math.floor(sphereCenter.x - radius);
        int maxX = (int) Math.ceil(sphereCenter.x + radius);
        int minY = (int) Math.floor(sphereCenter.y - radius);
        int maxY = (int) Math.ceil(sphereCenter.y + radius);
        int minZ = (int) Math.floor(sphereCenter.z - radius);
        int maxZ = (int) Math.ceil(sphereCenter.z + radius);

        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    if (isInside(pos)) {
                        blocks.add(pos);
                    }
                }
            }
        }
        return blocks;
    }

    public Vec3d getCenterVec3d() {
        return sphereCenter;
    }

    public double getRadius() {
        return radius;
    }

    private void drawCircle(MatrixStack matrices, VertexConsumer vertexConsumer, int segments, float radius, float y, float red, float green, float blue, float alpha) {
        float theta = 0;
        float step = (float) (2 * Math.PI / segments);

        for (int i = 0; i <= segments; i++) {
            float x1 = radius * (float) Math.cos(theta);
            float z1 = radius * (float) Math.sin(theta);
            float x2 = radius * (float) Math.cos(theta + step);
            float z2 = radius * (float) Math.sin(theta + step);

            Vec3d normal1 = new Vec3d(x1, y, z1).normalize();
            Vec3d normal2 = new Vec3d(x2, y, z2).normalize();

            vertexConsumer.vertex(matrices.peek().getPositionMatrix(), x1, y, z1)
                .color(red, green, blue, alpha)
                .normal((float)normal1.x, (float)normal1.y, (float)normal1.z);

            vertexConsumer.vertex(matrices.peek().getPositionMatrix(), x2, y, z2)
                .color(red, green, blue, alpha)
                .normal((float)normal2.x, (float)normal2.y, (float)normal2.z);

            theta += step;
        }
    }

    private void renderSphereFace(MatrixStack matrices, VertexConsumer vertexConsumer, float theta1, float theta2, float phi1, float phi2, float red, float green, float blue, float alpha) {
        Vec3d v1 = getSpherePoint(theta1, phi1);
        Vec3d v2 = getSpherePoint(theta1, phi2);
        Vec3d v3 = getSpherePoint(theta2, phi2);
        Vec3d v4 = getSpherePoint(theta2, phi1);

        Vec3d normal1 = v1.normalize();
        Vec3d normal2 = v2.normalize();
        Vec3d normal3 = v3.normalize();
        Vec3d normal4 = v4.normalize();

        addVertex(matrices, vertexConsumer, v1, normal1, red, green, blue, alpha, theta1, phi1);
        addVertex(matrices, vertexConsumer, v2, normal2, red, green, blue, alpha, theta1, phi2);
        addVertex(matrices, vertexConsumer, v3, normal3, red, green, blue, alpha, theta2, phi2);

        addVertex(matrices, vertexConsumer, v1, normal1, red, green, blue, alpha, theta1, phi1);
        addVertex(matrices, vertexConsumer, v3, normal3, red, green, blue, alpha, theta2, phi2);
        addVertex(matrices, vertexConsumer, v4, normal4, red, green, blue, alpha, theta2, phi1);
    }

    private void addVertex(MatrixStack matrices, VertexConsumer vertexConsumer, Vec3d pos, Vec3d normal, float red, float green, float blue, float alpha, float u, float v) {
        vertexConsumer.vertex(matrices.peek().getPositionMatrix(), (float)pos.x, (float)pos.y, (float)pos.z)
            .color(red, green, blue, alpha)
            .texture(u / (float) (2 * Math.PI), v / (float) Math.PI)
            .overlay(OverlayTexture.DEFAULT_UV)
            .light(15728880)
            .normal((float)normal.x, (float)normal.y, (float)normal.z);
    }

    private Vec3d getSpherePoint(float theta, float phi) {
        float x = (float) (radius * Math.sin(phi) * Math.cos(theta));
        float y = (float) (radius * Math.cos(phi));
        float z = (float) (radius * Math.sin(phi) * Math.sin(theta));
        return new Vec3d(x, y, z);
    }
}