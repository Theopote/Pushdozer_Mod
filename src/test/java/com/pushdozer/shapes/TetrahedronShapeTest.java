package com.pushdozer.shapes;

import com.pushdozer.PushdozerTestBase;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TetrahedronShapeTest extends PushdozerTestBase {

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 3, 4, 8, 16, 32})
    void layerUnionEqualsBlockPositions(int edgeLength) {
        TetrahedronShape shape = new TetrahedronShape(edgeLength, BlockPos.ORIGIN);

        Set<BlockPos> layered = new HashSet<>();
        for (int y = shape.getMinY(BlockPos.ORIGIN); y <= shape.getMaxY(BlockPos.ORIGIN); y++) {
            layered.addAll(shape.getBlocksInLayer(BlockPos.ORIGIN, y));
        }

        assertEquals(new HashSet<>(shape.getBlockPositions()), layered);
    }

    @Test
    void edgeLength1NotEmpty() {
        TetrahedronShape shape = new TetrahedronShape(1, BlockPos.ORIGIN);

        assertFalse(shape.getBlockPositions().isEmpty());
        assertTrue(shape.isInside(BlockPos.ORIGIN));
    }

    @Test
    void largerEdgeLengthIsSuperset() {
        TetrahedronShape small = new TetrahedronShape(1, BlockPos.ORIGIN);
        TetrahedronShape large = new TetrahedronShape(8, BlockPos.ORIGIN);

        Set<BlockPos> smallBlocks = new HashSet<>(small.getBlockPositions());
        Set<BlockPos> largeBlocks = new HashSet<>(large.getBlockPositions());

        assertTrue(largeBlocks.containsAll(smallBlocks));
        assertTrue(largeBlocks.size() > smallBlocks.size());
    }

    @Test
    void boundingBoxUsesVertexScale() {
        int edgeLength = 8;
        TetrahedronShape shape = new TetrahedronShape(edgeLength, BlockPos.ORIGIN);
        double expectedScale = edgeLength / (2.0 * Math.sqrt(2.0));
        double wrongHalfSize = edgeLength / 2.0;

        Box box = shape.getBoundingBox(BlockPos.ORIGIN);
        Vec3d worldCenter = Vec3d.ofCenter(BlockPos.ORIGIN);

        assertEquals(worldCenter.x - expectedScale, box.minX, 1e-9);
        assertEquals(worldCenter.x + expectedScale, box.maxX, 1e-9);
        assertTrue(expectedScale < wrongHalfSize);
    }

    @Test
    void blockInsideMatchesGeneratedSet() {
        TetrahedronShape shape = new TetrahedronShape(4, new BlockPos(0, 64, 0));
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
        TetrahedronShape shape = new TetrahedronShape(3, storedCenter);
        Vec3d queryCenter = Vec3d.ofCenter(new BlockPos(20, 64, 0));

        assertTrue(shape.getBlocksInRadius(queryCenter, 2).isEmpty());
    }

    @Test
    void getBlocksInRadiusSubsetOfShape() {
        BlockPos center = new BlockPos(0, 64, 0);
        TetrahedronShape shape = new TetrahedronShape(8, center);
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
        TetrahedronShape shape = new TetrahedronShape(5, storedCenter);

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
        TetrahedronShape shape = new TetrahedronShape(4, original);

        Set<BlockPos> beforeMove = new HashSet<>(shape.getBlockPositions());
        shape.setCenter(moved);
        Set<BlockPos> afterMove = new HashSet<>(shape.getBlockPositions());

        assertFalse(beforeMove.equals(afterMove));
        assertTrue(afterMove.contains(moved));
    }

    @Test
    void negativeWorldCoordinates() {
        BlockPos center = new BlockPos(-100, -32, -100);
        TetrahedronShape shape = new TetrahedronShape(4, center);

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
        TetrahedronShape shape = new TetrahedronShape(4, center);

        Set<BlockPos> layered = new HashSet<>();
        for (int y = shape.getMinY(center); y <= shape.getMaxY(center); y++) {
            layered.addAll(shape.getBlocksInLayer(center, y));
        }

        assertEquals(new HashSet<>(shape.getBlockPositions()), layered);
        assertFalse(shape.getBlockPositions().isEmpty());
    }

    @Test
    void noDuplicateBlocks() {
        TetrahedronShape shape = new TetrahedronShape(5, BlockPos.ORIGIN);
        List<BlockPos> positions = shape.getBlockPositions();

        assertEquals(positions.size(), new HashSet<>(positions).size());
    }

    @Test
    void iteratorMatchesBlockPositions() {
        TetrahedronShape shape = new TetrahedronShape(5, BlockPos.ORIGIN);

        Set<BlockPos> iterated = new HashSet<>();
        Iterator<BlockPos> iterator = shape.getBlocksIterator();
        while (iterator.hasNext()) {
            iterated.add(iterator.next());
        }

        assertEquals(new HashSet<>(shape.getBlockPositions()), iterated);
    }

    @Test
    void rejectsInvalidEdgeLength() {
        assertThrows(IllegalArgumentException.class, () -> new TetrahedronShape(0, BlockPos.ORIGIN));
        assertThrows(IllegalArgumentException.class, () -> new TetrahedronShape(0.5, BlockPos.ORIGIN));
        assertThrows(IllegalArgumentException.class, () -> new TetrahedronShape(-1, BlockPos.ORIGIN));
    }
}
