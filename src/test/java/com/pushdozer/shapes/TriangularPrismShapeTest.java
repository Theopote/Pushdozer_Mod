package com.pushdozer.shapes;

import com.pushdozer.PushdozerTestBase;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TriangularPrismShapeTest extends PushdozerTestBase {

    @ParameterizedTest
    @CsvSource({
        "1,1", "2,2", "3,3", "4,4", "5,5", "8,8", "16,16", "32,32",
        "3,5", "5,3", "8,4"
    })
    void layerUnionEqualsBlockPositions(int sideLength, int height) {
        TriangularPrismShape shape = new TriangularPrismShape(sideLength, height, BlockPos.ORIGIN);

        Set<BlockPos> layered = new HashSet<>();
        for (int y = shape.getMinY(BlockPos.ORIGIN); y <= shape.getMaxY(BlockPos.ORIGIN); y++) {
            layered.addAll(shape.getBlocksInLayer(BlockPos.ORIGIN, y));
        }

        assertEquals(new HashSet<>(shape.getBlockPositions()), layered);
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 3, 4, 5, 8, 16, 32})
    void heightMatchesConfiguration(int height) {
        TriangularPrismShape shape = new TriangularPrismShape(5, height, BlockPos.ORIGIN);

        long layers = shape.getBlockPositions().stream()
            .map(BlockPos::getY)
            .distinct()
            .count();

        assertEquals(height, layers);
    }

    @Test
    void crossSectionCentroidAtOrigin() {
        TriangularPrismShape shape = new TriangularPrismShape(6, 4, BlockPos.ORIGIN);

        double avgX = 0.0;
        double avgZ = 0.0;
        for (int i = 0; i < 3; i++) {
            avgX += shape.getCrossSectionVertexX(i);
            avgZ += shape.getCrossSectionVertexZ(i);
        }
        avgX /= 3.0;
        avgZ /= 3.0;

        assertEquals(0.0, avgX, 1e-9);
        assertEquals(0.0, avgZ, 1e-9);
    }

    @Test
    void boundingBoxUsesTriangleExtents() {
        int sideLength = 8;
        TriangularPrismShape shape = new TriangularPrismShape(sideLength, 4, BlockPos.ORIGIN);
        double triangleHeight = sideLength * Math.sqrt(3.0) / 2.0;
        Vec3d worldCenter = Vec3d.ofCenter(BlockPos.ORIGIN);

        Box box = shape.getBoundingBox(BlockPos.ORIGIN);

        assertEquals(worldCenter.x - sideLength / 2.0, box.minX, 1e-9);
        assertEquals(worldCenter.x + sideLength / 2.0, box.maxX, 1e-9);
        assertEquals(worldCenter.z - triangleHeight / 3.0, box.minZ, 1e-9);
        assertEquals(worldCenter.z + 2.0 * triangleHeight / 3.0, box.maxZ, 1e-9);
        assertTrue(sideLength / 2.0 < sideLength);
    }

    @Test
    void blockInsideMatchesGeneratedSet() {
        TriangularPrismShape shape = new TriangularPrismShape(5, 4, new BlockPos(0, 64, 0));
        Set<BlockPos> blocks = new HashSet<>(shape.getBlockPositions());

        for (BlockPos pos : blocks) {
            assertTrue(shape.isInside(pos));
        }

        Box box = shape.getBoundingBox(shape.getCenter());
        int minX = (int) Math.floor(box.minX) - 1;
        int maxX = (int) Math.ceil(box.maxX) + 1;
        int minY = (int) Math.floor(box.minY) - 1;
        int maxY = (int) Math.ceil(box.maxY) + 1;
        int minZ = (int) Math.floor(box.minZ) - 1;
        int maxZ = (int) Math.ceil(box.maxZ) + 1;

        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    assertEquals(blocks.contains(pos), shape.isInside(pos));
                }
            }
        }
    }

    @Test
    void getBlocksInRadiusEmptyWhenFar() {
        BlockPos storedCenter = new BlockPos(0, 64, 0);
        TriangularPrismShape shape = new TriangularPrismShape(5, 4, storedCenter);
        Vec3d queryCenter = Vec3d.ofCenter(new BlockPos(20, 64, 0));

        assertTrue(shape.getBlocksInRadius(queryCenter, 2).isEmpty());
    }

    @Test
    void getBlocksInRadiusSubsetOfShape() {
        BlockPos center = new BlockPos(0, 64, 0);
        TriangularPrismShape shape = new TriangularPrismShape(8, 4, center);
        Vec3d queryCenter = Vec3d.ofCenter(center);

        List<BlockPos> withinTwo = shape.getBlocksInRadius(queryCenter, 2);
        Set<BlockPos> fullShape = new HashSet<>(shape.getBlockPositions());

        assertTrue(withinTwo.size() < fullShape.size());
        for (BlockPos pos : withinTwo) {
            assertTrue(fullShape.contains(pos));
            assertTrue(Vec3d.ofCenter(pos).squaredDistanceTo(queryCenter) <= 4.0);
        }
    }

    @Test
    void isWithinBoundsUsesBasePos() {
        BlockPos storedCenter = new BlockPos(0, 64, 0);
        BlockPos queryCenter = new BlockPos(100, 64, 100);
        TriangularPrismShape shape = new TriangularPrismShape(5, 4, storedCenter);

        assertTrue(shape.isWithinBounds(queryCenter, queryCenter));
        assertFalse(shape.isWithinBounds(storedCenter, queryCenter));

        for (BlockPos pos : shape.getBlockPositions()) {
            assertTrue(shape.isWithinBounds(pos, storedCenter));
        }
    }

    @Test
    void setCenterMovesShape() {
        BlockPos original = new BlockPos(0, 64, 0);
        BlockPos moved = new BlockPos(10, 70, 10);
        TriangularPrismShape shape = new TriangularPrismShape(5, 4, original);

        Set<BlockPos> beforeMove = new HashSet<>(shape.getBlockPositions());
        shape.setCenter(moved);
        Set<BlockPos> afterMove = new HashSet<>(shape.getBlockPositions());

        assertFalse(beforeMove.equals(afterMove));
        assertTrue(afterMove.contains(moved));
    }

    @Test
    void negativeWorldCoordinates() {
        BlockPos center = new BlockPos(-100, -32, -100);
        TriangularPrismShape shape = new TriangularPrismShape(4, 3, center);

        Set<BlockPos> layered = new HashSet<>();
        for (int y = shape.getMinY(center); y <= shape.getMaxY(center); y++) {
            layered.addAll(shape.getBlocksInLayer(center, y));
        }

        assertEquals(new HashSet<>(shape.getBlockPositions()), layered);
        assertFalse(shape.getBlockPositions().isEmpty());
    }

    @Test
    void positiveHighCoordinates() {
        BlockPos center = new BlockPos(100, 128, 100);
        TriangularPrismShape shape = new TriangularPrismShape(4, 3, center);

        Set<BlockPos> layered = new HashSet<>();
        for (int y = shape.getMinY(center); y <= shape.getMaxY(center); y++) {
            layered.addAll(shape.getBlocksInLayer(center, y));
        }

        assertEquals(new HashSet<>(shape.getBlockPositions()), layered);
        assertFalse(shape.getBlockPositions().isEmpty());
    }

    @Test
    void previewVerticesMatchShape() {
        BlockPos basePos = BlockPos.ORIGIN;
        TriangularPrismShape shape = new TriangularPrismShape(6, 4, basePos);
        double triangleHeight = shape.getTriangleHeight();

        assertEquals(0.0, shape.getCrossSectionVertexX(0), 1e-9);
        assertEquals(-3.0, shape.getCrossSectionVertexX(1), 1e-9);
        assertEquals(3.0, shape.getCrossSectionVertexX(2), 1e-9);
        assertEquals(2.0 * triangleHeight / 3.0, shape.getCrossSectionVertexZ(0), 1e-9);
        assertEquals(-triangleHeight / 3.0, shape.getCrossSectionVertexZ(1), 1e-9);
        assertEquals(-triangleHeight / 3.0, shape.getCrossSectionVertexZ(2), 1e-9);
        assertEquals(-1.5f, shape.getPreviewBottomY(basePos), 1e-4f);
        assertEquals(2.5f, shape.getPreviewTopY(basePos), 1e-4f);
    }

    @Test
    void noDuplicateBlocks() {
        TriangularPrismShape shape = new TriangularPrismShape(5, 4, BlockPos.ORIGIN);
        List<BlockPos> positions = shape.getBlockPositions();

        assertEquals(positions.size(), new HashSet<>(positions).size());
    }

    @Test
    void iteratorMatchesBlockPositions() {
        TriangularPrismShape shape = new TriangularPrismShape(5, 4, BlockPos.ORIGIN);

        Set<BlockPos> iterated = new HashSet<>();
        Iterator<BlockPos> iterator = shape.getBlocksIterator();
        while (iterator.hasNext()) {
            iterated.add(iterator.next());
        }

        assertEquals(new HashSet<>(shape.getBlockPositions()), iterated);
    }

    @Test
    void rejectsInvalidDimensions() {
        assertThrows(IllegalArgumentException.class, () -> new TriangularPrismShape(0, 4, BlockPos.ORIGIN));
        assertThrows(IllegalArgumentException.class, () -> new TriangularPrismShape(5, 0, BlockPos.ORIGIN));
        assertThrows(IllegalArgumentException.class, () -> new TriangularPrismShape(-1, 4, BlockPos.ORIGIN));
    }
}
