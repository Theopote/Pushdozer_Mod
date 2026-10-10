package com.pushdozer.shapes;

import com.pushdozer.PushdozerTestBase;
import net.minecraft.util.math.BlockPos;
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

class OctahedronShapeTest extends PushdozerTestBase {

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 3, 8, 32, 64})
    void layerUnionEqualsBlockPositions(int radius) {
        OctahedronShape shape = new OctahedronShape(radius, BlockPos.ORIGIN);

        Set<BlockPos> layered = new HashSet<>();
        for (int y = shape.getMinY(BlockPos.ORIGIN); y <= shape.getMaxY(BlockPos.ORIGIN); y++) {
            layered.addAll(shape.getBlocksInLayer(BlockPos.ORIGIN, y));
        }

        assertEquals(new HashSet<>(shape.getBlockPositions()), layered);
    }

    @Test
    void sixAxisVerticesExist() {
        int radius = 3;
        OctahedronShape shape = new OctahedronShape(radius, BlockPos.ORIGIN);
        Set<BlockPos> blocks = new HashSet<>(shape.getBlockPositions());

        assertTrue(blocks.contains(new BlockPos(radius, 0, 0)));
        assertTrue(blocks.contains(new BlockPos(-radius, 0, 0)));
        assertTrue(blocks.contains(new BlockPos(0, radius, 0)));
        assertTrue(blocks.contains(new BlockPos(0, -radius, 0)));
        assertTrue(blocks.contains(new BlockPos(0, 0, radius)));
        assertTrue(blocks.contains(new BlockPos(0, 0, -radius)));
    }

    @Test
    void isInsideVec3dNoTruncation() {
        OctahedronShape shape = new OctahedronShape(5, BlockPos.ORIGIN);

        Vec3d justOutside = new Vec3d(5.9, 0, 0);
        Vec3d onBoundary = new Vec3d(5.0, 0, 0);

        assertFalse(shape.isInside(justOutside));
        assertTrue(shape.isInside(onBoundary));
    }

    @Test
    void blockInsideMatchesGeneratedSet() {
        OctahedronShape shape = new OctahedronShape(5, new BlockPos(0, 64, 0));
        Set<BlockPos> blocks = new HashSet<>(shape.getBlockPositions());

        for (BlockPos pos : blocks) {
            assertTrue(shape.isInside(pos));
        }

        for (int x = -6; x <= 6; x++) {
            for (int y = 63; y <= 65; y++) {
                for (int z = -6; z <= 6; z++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    assertEquals(blocks.contains(pos), shape.isInside(pos));
                }
            }
        }
    }

    @Test
    void getBlocksInRadiusEmptyWhenFar() {
        BlockPos storedCenter = new BlockPos(0, 64, 0);
        OctahedronShape shape = new OctahedronShape(3, storedCenter);
        Vec3d queryCenter = Vec3d.ofCenter(new BlockPos(20, 64, 0));

        assertTrue(shape.getBlocksInRadius(queryCenter, 2).isEmpty());
    }

    @Test
    void getBlocksInRadiusSubsetOfShape() {
        BlockPos center = new BlockPos(0, 64, 0);
        OctahedronShape shape = new OctahedronShape(5, center);
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
        OctahedronShape shape = new OctahedronShape(5, storedCenter);

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
        OctahedronShape shape = new OctahedronShape(3, original);

        Set<BlockPos> beforeMove = new HashSet<>(shape.getBlockPositions());
        shape.setCenter(moved);
        Set<BlockPos> afterMove = new HashSet<>(shape.getBlockPositions());

        assertFalse(beforeMove.equals(afterMove));
        assertTrue(afterMove.contains(moved.up(3)));
        assertTrue(afterMove.contains(moved.down(3)));
    }

    @Test
    void negativeWorldCoordinates() {
        BlockPos center = new BlockPos(0, -5, 0);
        OctahedronShape shape = new OctahedronShape(3, center);

        assertEquals(-8, shape.getMinY(center));
        assertEquals(-2, shape.getMaxY(center));
    }

    @Test
    void noDuplicateBlocks() {
        OctahedronShape shape = new OctahedronShape(5, BlockPos.ORIGIN);
        List<BlockPos> positions = shape.getBlockPositions();

        assertEquals(positions.size(), new HashSet<>(positions).size());
    }

    @Test
    void iteratorMatchesBlockPositions() {
        OctahedronShape shape = new OctahedronShape(5, BlockPos.ORIGIN);

        Set<BlockPos> iterated = new HashSet<>();
        Iterator<BlockPos> iterator = shape.getBlocksIterator();
        while (iterator.hasNext()) {
            iterated.add(iterator.next());
        }

        assertEquals(new HashSet<>(shape.getBlockPositions()), iterated);
    }

    @Test
    void rejectsInvalidRadius() {
        assertThrows(IllegalArgumentException.class, () -> new OctahedronShape(0, BlockPos.ORIGIN));
        assertThrows(IllegalArgumentException.class, () -> new OctahedronShape(-1, BlockPos.ORIGIN));
    }
}
