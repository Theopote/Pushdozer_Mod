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
import java.util.NoSuchElementException;

public class TetrahedronShape implements GeometryShape {
    private final double edgeLength;
    private final double scale;
    private Vec3d tetrahedronCenter;
    private BlockPos center;

    public TetrahedronShape(double edgeLength, BlockPos center) {
        if (edgeLength < 1) {
            throw new IllegalArgumentException("Tetrahedron edge length must be at least 1");
        }
        this.edgeLength = edgeLength;
        this.scale = edgeLength / (2.0 * Math.sqrt(2.0));
        this.center = center;
        this.tetrahedronCenter = Vec3d.ofCenter(center);
    }

    public TetrahedronShape(double edgeLength, Vec3d center) {
        if (edgeLength < 1) {
            throw new IllegalArgumentException("Tetrahedron edge length must be at least 1");
        }
        this.edgeLength = edgeLength;
        this.scale = edgeLength / (2.0 * Math.sqrt(2.0));
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

        Vec3d[] vertices = getTetrahedronVertices();

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

        Vec3d[] vertices = getTetrahedronVertices();

        renderTriangleFace(vertexConsumer, matrices, vertices[0], vertices[1], vertices[2], red, green, blue, alpha);
        renderTriangleFace(vertexConsumer, matrices, vertices[0], vertices[2], vertices[3], red, green, blue, alpha);
        renderTriangleFace(vertexConsumer, matrices, vertices[0], vertices[3], vertices[1], red, green, blue, alpha);
        renderTriangleFace(vertexConsumer, matrices, vertices[1], vertices[3], vertices[2], red, green, blue, alpha);

        matrices.pop();
    }

    @Override
    public boolean isInside(Vec3d pos) {
        return isInsideAt(pos, center);
    }

    @Override
    public boolean isInside(BlockPos pos) {
        return isInsideAt(Vec3d.ofCenter(pos), center);
    }

    @Override
    public Box getBoundingBox(BlockPos basePos) {
        Vec3d worldCenter = resolveWorldCenter(basePos);
        return new Box(
            worldCenter.x - scale, worldCenter.y - scale, worldCenter.z - scale,
            worldCenter.x + scale, worldCenter.y + scale, worldCenter.z + scale
        );
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
        if (y < getMinY(basePos) || y > getMaxY(basePos)) {
            return List.of();
        }

        List<BlockPos> blocks = new ArrayList<>();
        for (int x = computeMinX(basePos); x <= computeMaxX(basePos); x++) {
            for (int z = computeMinZ(basePos); z <= computeMaxZ(basePos); z++) {
                BlockPos pos = new BlockPos(x, y, z);
                if (isInsideAt(Vec3d.ofCenter(pos), basePos)) {
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

        Box shapeBounds = getBoundingBox(center);
        int minX = Math.max(queryBlock.getX() - maxDistance, (int) Math.floor(shapeBounds.minX));
        int maxX = Math.min(queryBlock.getX() + maxDistance, (int) Math.ceil(shapeBounds.maxX));
        int minY = Math.max(queryBlock.getY() - maxDistance, (int) Math.floor(shapeBounds.minY));
        int maxY = Math.min(queryBlock.getY() + maxDistance, (int) Math.ceil(shapeBounds.maxY));
        int minZ = Math.max(queryBlock.getZ() - maxDistance, (int) Math.floor(shapeBounds.minZ));
        int maxZ = Math.min(queryBlock.getZ() + maxDistance, (int) Math.ceil(shapeBounds.maxZ));

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
    public boolean isWithinBounds(BlockPos pos, BlockPos basePos) {
        return isInsideAt(Vec3d.ofCenter(pos), basePos);
    }

    @Override
    public List<BlockPos> getBlocks() {
        return getBlockPositions();
    }

    @Override
    public Iterator<BlockPos> getBlocksIterator() {
        return new LayerBlockIterator(center, getMinY(center), getMaxY(center));
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
        for (int y = getMinY(center); y <= getMaxY(center); y++) {
            blocks.addAll(getBlocksInLayer(center, y));
        }
        return blocks;
    }

    private Vec3d resolveWorldCenter(BlockPos basePos) {
        return tetrahedronCenter.add(
            basePos.getX() - center.getX(),
            basePos.getY() - center.getY(),
            basePos.getZ() - center.getZ()
        );
    }

    private int computeMinX(BlockPos basePos) {
        return (int) Math.floor(resolveWorldCenter(basePos).x - scale);
    }

    private int computeMaxX(BlockPos basePos) {
        return (int) Math.floor(resolveWorldCenter(basePos).x + scale);
    }

    private int computeMinZ(BlockPos basePos) {
        return (int) Math.floor(resolveWorldCenter(basePos).z - scale);
    }

    private int computeMaxZ(BlockPos basePos) {
        return (int) Math.floor(resolveWorldCenter(basePos).z + scale);
    }

    private boolean isInsideRelative(double x, double y, double z) {
        double tol = scale * 1e-9;
        return x + y + z >= -scale - tol
            && x - y - z >= -scale - tol
            && -x + y - z >= -scale - tol
            && -x - y + z >= -scale - tol;
    }

    private boolean isInsideAt(Vec3d pos, BlockPos basePos) {
        Vec3d worldCenter = resolveWorldCenter(basePos);
        return isInsideRelative(pos.x - worldCenter.x, pos.y - worldCenter.y, pos.z - worldCenter.z);
    }

    private Vec3d[] getTetrahedronVertices() {
        return new Vec3d[]{
            new Vec3d(scale, scale, scale),
            new Vec3d(scale, -scale, -scale),
            new Vec3d(-scale, scale, -scale),
            new Vec3d(-scale, -scale, scale)
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
        Vec3d edge1 = v2.subtract(v1);
        Vec3d edge2 = v3.subtract(v1);
        Vec3d normal = edge1.crossProduct(edge2).normalize();

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

    private final class LayerBlockIterator implements Iterator<BlockPos> {
        private final BlockPos basePos;
        private final int maxY;
        private int currentY;
        private Iterator<BlockPos> currentLayer = List.<BlockPos>of().iterator();

        private LayerBlockIterator(BlockPos basePos, int minY, int maxY) {
            this.basePos = basePos;
            this.maxY = maxY;
            this.currentY = minY - 1;
            advanceLayer();
        }

        @Override
        public boolean hasNext() {
            while (!currentLayer.hasNext() && currentY < maxY) {
                advanceLayer();
            }
            return currentLayer.hasNext();
        }

        @Override
        public BlockPos next() {
            if (!hasNext()) {
                throw new NoSuchElementException();
            }
            return currentLayer.next();
        }

        private void advanceLayer() {
            currentY++;
            if (currentY <= maxY) {
                currentLayer = getBlocksInLayer(basePos, currentY).iterator();
            }
        }
    }
}
