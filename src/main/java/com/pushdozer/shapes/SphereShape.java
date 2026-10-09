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
    private final double radiusSquared;
    private Vec3d sphereCenter;
    private BlockPos center;

    public SphereShape(double radius, Vec3d center) {
        this.radius = radius;
        this.radiusSquared = radius * radius;
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

        int segments = 64;
        float r = (float) radius;

        // XZ plane
        drawCircle(matrices, vertexConsumer, segments, r, 0, red, green, blue, alpha);

        // XY plane
        matrices.push();
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(90));
        drawCircle(matrices, vertexConsumer, segments, r, 0, red, green, blue, alpha);
        matrices.pop();

        // YZ plane
        matrices.push();
        matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(90));
        drawCircle(matrices, vertexConsumer, segments, r, 0, red, green, blue, alpha);
        matrices.pop();

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
        return pos.squaredDistanceTo(sphereCenter) <= radiusSquared;
    }

    @Override
    public boolean isInside(BlockPos pos) {
        return Vec3d.ofCenter(pos).squaredDistanceTo(sphereCenter) <= radiusSquared;
    }

    @Override
    public int getMinY(BlockPos basePos) {
        return (int) Math.floor(getBoundingBox(basePos).minY);
    }

    @Override
    public int getMaxY(BlockPos basePos) {
        return (int) Math.floor(getBoundingBox(basePos).maxY);
    }

    @Override
    public List<BlockPos> getBlocksInLayer(BlockPos basePos, int y) {
        List<BlockPos> blocks = new ArrayList<>();
        int layerY = y - basePos.getY();
        if (Math.abs(layerY) > radius) {
            return blocks;
        }

        double horizontalRadiusSquared = radiusSquared - (double) layerY * layerY;
        if (horizontalRadiusSquared < 0) {
            return blocks;
        }

        int xRadius = (int) Math.ceil(Math.sqrt(horizontalRadiusSquared));
        for (int x = -xRadius; x <= xRadius; x++) {
            for (int z = -xRadius; z <= xRadius; z++) {
                BlockPos pos = new BlockPos(basePos.getX() + x, y, basePos.getZ() + z);
                if (isInside(pos)) {
                    blocks.add(pos);
                }
            }
        }
        return blocks;
    }

    @Override
    public List<BlockPos> getBlocksInRadius(Vec3d queryCenter, int maxDistance) {
        List<BlockPos> blocks = new ArrayList<>();
        BlockPos queryBlock = BlockPos.ofFloored(queryCenter);
        double maxDistanceSquared = (double) maxDistance * maxDistance;

        int minX = Math.max(queryBlock.getX() - maxDistance, (int) Math.floor(sphereCenter.x - radius));
        int maxX = Math.min(queryBlock.getX() + maxDistance, (int) Math.ceil(sphereCenter.x + radius));
        int minY = Math.max(queryBlock.getY() - maxDistance, (int) Math.floor(sphereCenter.y - radius));
        int maxY = Math.min(queryBlock.getY() + maxDistance, (int) Math.ceil(sphereCenter.y + radius));
        int minZ = Math.max(queryBlock.getZ() - maxDistance, (int) Math.floor(sphereCenter.z - radius));
        int maxZ = Math.min(queryBlock.getZ() + maxDistance, (int) Math.ceil(sphereCenter.z + radius));

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
    public Box getBoundingBox(BlockPos basePos) {
        Vec3d worldCenter = resolveWorldCenter(basePos);
        return new Box(
            worldCenter.x - radius, worldCenter.y - radius, worldCenter.z - radius,
            worldCenter.x + radius, worldCenter.y + radius, worldCenter.z + radius
        );
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
        return getBlockPositions().iterator();
    }

    @Override
    public List<BlockPos> getBlockPositions() {
        List<BlockPos> blocks = new ArrayList<>();
        int minY = getMinY(center);
        int maxY = getMaxY(center);
        for (int y = minY; y <= maxY; y++) {
            blocks.addAll(getBlocksInLayer(center, y));
        }
        return blocks;
    }

    public Vec3d getCenterVec3d() {
        return sphereCenter;
    }

    public double getRadius() {
        return radius;
    }

    private Vec3d resolveWorldCenter(BlockPos basePos) {
        return sphereCenter.add(
            basePos.getX() - center.getX(),
            basePos.getY() - center.getY(),
            basePos.getZ() - center.getZ()
        );
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
