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

public class TriangularPrismShape implements GeometryShape {
    private final int sideLength;
    private final int height;
    private final double triangleHeight;
    private final double[] edgeAnchorX;
    private final double[] edgeAnchorZ;
    private final double[] edgeNormalX;
    private final double[] edgeNormalZ;
    private Vec3d prismCenter;
    private BlockPos center;

    public TriangularPrismShape(int sideLength, int height, BlockPos center) {
        if (sideLength < 1 || height < 1) {
            throw new IllegalArgumentException("Triangular prism side length and height must be at least 1");
        }
        this.sideLength = sideLength;
        this.height = height;
        this.triangleHeight = sideLength * Math.sqrt(3.0) / 2.0;
        this.center = center;
        this.prismCenter = Vec3d.ofCenter(center);
        double[] anchorX = new double[3];
        double[] anchorZ = new double[3];
        double[] normalX = new double[3];
        double[] normalZ = new double[3];
        computeEdgeHalfPlanes(anchorX, anchorZ, normalX, normalZ);
        this.edgeAnchorX = anchorX;
        this.edgeAnchorZ = anchorZ;
        this.edgeNormalX = normalX;
        this.edgeNormalZ = normalZ;
    }

    public TriangularPrismShape(int sideLength, int height, Vec3d center) {
        this(sideLength, height, new BlockPos(
            (int) Math.floor(center.x),
            (int) Math.floor(center.y),
            (int) Math.floor(center.z)
        ));
        this.prismCenter = center;
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

        Vec3d[] bottomVertices = getBottomVertices();
        Vec3d[] topVertices = getTopVertices();

        drawEdge(vertexConsumer, matrices, bottomVertices[0], bottomVertices[1], red, green, blue, alpha);
        drawEdge(vertexConsumer, matrices, bottomVertices[1], bottomVertices[2], red, green, blue, alpha);
        drawEdge(vertexConsumer, matrices, bottomVertices[2], bottomVertices[0], red, green, blue, alpha);

        drawEdge(vertexConsumer, matrices, topVertices[0], topVertices[1], red, green, blue, alpha);
        drawEdge(vertexConsumer, matrices, topVertices[1], topVertices[2], red, green, blue, alpha);
        drawEdge(vertexConsumer, matrices, topVertices[2], topVertices[0], red, green, blue, alpha);

        drawEdge(vertexConsumer, matrices, bottomVertices[0], topVertices[0], red, green, blue, alpha);
        drawEdge(vertexConsumer, matrices, bottomVertices[1], topVertices[1], red, green, blue, alpha);
        drawEdge(vertexConsumer, matrices, bottomVertices[2], topVertices[2], red, green, blue, alpha);

        matrices.pop();
    }

    @Override
    public void renderSolid(MatrixStack matrices, VertexConsumer vertexConsumer, Vec3d center, float red, float green, float blue, float alpha) {
        matrices.push();
        matrices.translate(center.x, center.y, center.z);

        Vec3d[] bottomVertices = getBottomVertices();
        Vec3d[] topVertices = getTopVertices();

        renderTriangleFace(vertexConsumer, matrices, bottomVertices[0], bottomVertices[1], bottomVertices[2], red, green, blue, alpha);
        renderTriangleFace(vertexConsumer, matrices, topVertices[0], topVertices[1], topVertices[2], red, green, blue, alpha);

        renderRectangleFace(vertexConsumer, matrices, bottomVertices[0], bottomVertices[1], topVertices[1], topVertices[0], red, green, blue, alpha);
        renderRectangleFace(vertexConsumer, matrices, bottomVertices[1], bottomVertices[2], topVertices[2], topVertices[1], red, green, blue, alpha);
        renderRectangleFace(vertexConsumer, matrices, bottomVertices[2], bottomVertices[0], topVertices[0], topVertices[2], red, green, blue, alpha);

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
        int minY = computeMinY(basePos);
        int maxY = computeMaxY(basePos);
        return new Box(
            worldCenter.x - sideLength / 2.0, minY, worldCenter.z - triangleHeight / 3.0,
            worldCenter.x + sideLength / 2.0, maxY + 1, worldCenter.z + 2.0 * triangleHeight / 3.0
        );
    }

    @Override
    public int getMinY(BlockPos basePos) {
        return computeMinY(basePos);
    }

    @Override
    public int getMaxY(BlockPos basePos) {
        return computeMaxY(basePos);
    }

    @Override
    public List<BlockPos> getBlocksInLayer(BlockPos basePos, int y) {
        if (y < computeMinY(basePos) || y > computeMaxY(basePos)) {
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

    public int getSideLength() {
        return sideLength;
    }

    public int getHeight() {
        return height;
    }

    public double getTriangleHeight() {
        return triangleHeight;
    }

    public double getCrossSectionVertexX(int index) {
        return switch (index) {
            case 0 -> 0.0;
            case 1 -> -sideLength / 2.0;
            case 2 -> sideLength / 2.0;
            default -> throw new IndexOutOfBoundsException("Triangle vertex index must be 0..2");
        };
    }

    public double getCrossSectionVertexZ(int index) {
        return switch (index) {
            case 0 -> 2.0 * triangleHeight / 3.0;
            case 1, 2 -> -triangleHeight / 3.0;
            default -> throw new IndexOutOfBoundsException("Triangle vertex index must be 0..2");
        };
    }

    public float getPreviewBottomY(BlockPos basePos) {
        return computeMinY(basePos) - (basePos.getY() + 0.5f);
    }

    public float getPreviewTopY(BlockPos basePos) {
        return computeMaxY(basePos) + 1 - (basePos.getY() + 0.5f);
    }

    @Override
    public List<BlockPos> getBlockPositions() {
        List<BlockPos> blocks = new ArrayList<>();
        for (int y = getMinY(center); y <= getMaxY(center); y++) {
            blocks.addAll(getBlocksInLayer(center, y));
        }
        return blocks;
    }

    private int computeMinY(BlockPos basePos) {
        return basePos.getY() - (height - 1) / 2;
    }

    private int computeMaxY(BlockPos basePos) {
        return computeMinY(basePos) + height - 1;
    }

    private int computeMinX(BlockPos basePos) {
        return (int) Math.floor(resolveWorldCenter(basePos).x - sideLength / 2.0);
    }

    private int computeMaxX(BlockPos basePos) {
        return (int) Math.floor(resolveWorldCenter(basePos).x + sideLength / 2.0);
    }

    private int computeMinZ(BlockPos basePos) {
        return (int) Math.floor(resolveWorldCenter(basePos).z - triangleHeight / 3.0);
    }

    private int computeMaxZ(BlockPos basePos) {
        return (int) Math.floor(resolveWorldCenter(basePos).z + 2.0 * triangleHeight / 3.0);
    }

    private Vec3d resolveWorldCenter(BlockPos basePos) {
        return prismCenter.add(
            basePos.getX() - center.getX(),
            basePos.getY() - center.getY(),
            basePos.getZ() - center.getZ()
        );
    }

    private boolean isInsideAt(Vec3d pos, BlockPos basePos) {
        int minY = computeMinY(basePos);
        int maxY = computeMaxY(basePos);
        if (pos.y < minY || pos.y >= maxY + 1) {
            return false;
        }

        Vec3d worldCenter = resolveWorldCenter(basePos);
        return isInsideTriangleRelative(pos.x - worldCenter.x, pos.z - worldCenter.z);
    }

    private boolean isInsideTriangleRelative(double x, double z) {
        double tol = sideLength * 1e-9;
        for (int i = 0; i < 3; i++) {
            double dx = x - edgeAnchorX[i];
            double dz = z - edgeAnchorZ[i];
            if (dx * edgeNormalX[i] + dz * edgeNormalZ[i] < -tol) {
                return false;
            }
        }
        return true;
    }

    private void computeEdgeHalfPlanes(double[] anchorX, double[] anchorZ, double[] normalX, double[] normalZ) {
        double[] vertexX = {
            getCrossSectionVertexX(0),
            getCrossSectionVertexX(1),
            getCrossSectionVertexX(2)
        };
        double[] vertexZ = {
            getCrossSectionVertexZ(0),
            getCrossSectionVertexZ(1),
            getCrossSectionVertexZ(2)
        };

        for (int i = 0; i < 3; i++) {
            int next = (i + 1) % 3;
            anchorX[i] = vertexX[i];
            anchorZ[i] = vertexZ[i];
            double edgeX = vertexX[next] - vertexX[i];
            double edgeZ = vertexZ[next] - vertexZ[i];
            normalX[i] = -edgeZ;
            normalZ[i] = edgeX;
        }
    }

    private Vec3d[] getBottomVertices() {
        float bottomY = getPreviewBottomY(center);
        return new Vec3d[]{
            new Vec3d(getCrossSectionVertexX(0), bottomY, getCrossSectionVertexZ(0)),
            new Vec3d(getCrossSectionVertexX(1), bottomY, getCrossSectionVertexZ(1)),
            new Vec3d(getCrossSectionVertexX(2), bottomY, getCrossSectionVertexZ(2))
        };
    }

    private Vec3d[] getTopVertices() {
        float topY = getPreviewTopY(center);
        return new Vec3d[]{
            new Vec3d(getCrossSectionVertexX(0), topY, getCrossSectionVertexZ(0)),
            new Vec3d(getCrossSectionVertexX(1), topY, getCrossSectionVertexZ(1)),
            new Vec3d(getCrossSectionVertexX(2), topY, getCrossSectionVertexZ(2))
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

    private void renderRectangleFace(VertexConsumer vertexConsumer, MatrixStack matrices,
                                   Vec3d v1, Vec3d v2, Vec3d v3, Vec3d v4,
                                   float red, float green, float blue, float alpha) {
        Vec3d edge1 = v2.subtract(v1);
        Vec3d edge2 = v3.subtract(v1);
        Vec3d normal = edge1.crossProduct(edge2).normalize();

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
