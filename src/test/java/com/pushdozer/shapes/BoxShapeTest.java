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

class BoxShapeTest extends PushdozerTestBase {

    @ParameterizedTest
    @CsvSource({
        "1,1,1",
        "2,2,2",
        "3,3,3",
        "4,4,4",
        "5,5,5",
        "8,8,8",
        "16,16,16",
        "64,64,64",
        "4,3,5",
        "8,4,6"
    })
    void blockCountEqualsDimensions(int length, int width, int height) {
        BoxShape shape = new BoxShape(length, width, height, BlockPos.ORIGIN);
        assertEquals((long) length * width * height, shape.getBlocks().size());
    }

    @Test
    void layerUnionEqualsBlockPositions() {
        BoxShape shape = new BoxShape(4, 3, 5, BlockPos.ORIGIN);

        Set<BlockPos> all = new HashSet<>(shape.getBlockPositions());
        Set<BlockPos> layers = new HashSet<>();
        for (int y = shape.getMinY(BlockPos.ORIGIN); y <= shape.getMaxY(BlockPos.ORIGIN); y++) {
            layers.addAll(shape.getBlocksInLayer(BlockPos.ORIGIN, y));
        }

        assertEquals(all, layers);
    }

    @Test
    void isInsideMatchesBlockSet() {
        BoxShape shape = new BoxShape(4, 4, 4, new BlockPos(0, 64, 0));
        Set<BlockPos> blocks = new HashSet<>(shape.getBlocks());

        for (BlockPos pos : blocks) {
            assertTrue(shape.isInside(pos));
        }

        Box box = shape.getBoundingBox(new BlockPos(0, 64, 0));
        int minX = (int) Math.floor(box.minX);
        int maxX = (int) Math.ceil(box.maxX);
        int minY = (int) Math.floor(box.minY);
        int maxY = (int) Math.ceil(box.maxY);
        int minZ = (int) Math.floor(box.minZ);
        int maxZ = (int) Math.ceil(box.maxZ);

        for (int x = minX - 1; x <= maxX; x++) {
            for (int y = minY - 1; y <= maxY; y++) {
                for (int z = minZ - 1; z <= maxZ; z++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    if (blocks.contains(pos)) {
                        assertTrue(shape.isInside(pos));
                    } else {
                        assertFalse(shape.isInside(pos));
                    }
                }
            }
        }
    }

    @Test
    void evenSizeNoOffset() {
        BlockPos center = new BlockPos(0, 64, 0);
        BoxShape shape = new BoxShape(4, 4, 4, center);

        BlockPos inside = new BlockPos(2, 64, 2);
        BlockPos outside = new BlockPos(-2, 64, 0);

        assertTrue(shape.getBlocks().contains(inside));
        assertTrue(shape.isInside(inside));
        assertFalse(shape.getBlocks().contains(outside));
        assertFalse(shape.isInside(outside));
    }

    @Test
    void getBlocksInLayerRespectsY() {
        BlockPos center = new BlockPos(0, 64, 0);
        BoxShape shape = new BoxShape(3, 3, 3, center);

        assertTrue(shape.getBlocksInLayer(center, 100).isEmpty());
        assertFalse(shape.getBlocksInLayer(center, 64).isEmpty());
    }

    @Test
    void minMaxYMatchVoxelLayers() {
        BlockPos center = new BlockPos(0, 64, 0);
        BoxShape shape = new BoxShape(4, 4, 4, center);

        int minY = shape.getBlocks().stream().mapToInt(BlockPos::getY).min().orElseThrow();
        int maxY = shape.getBlocks().stream().mapToInt(BlockPos::getY).max().orElseThrow();

        assertEquals(minY, shape.getMinY(center));
        assertEquals(maxY, shape.getMaxY(center));
    }

    @Test
    void getBlocksInRadiusRespectsQuery() {
        BlockPos center = new BlockPos(0, 64, 0);
        BoxShape shape = new BoxShape(8, 8, 4, center);
        Vec3d queryCenter = Vec3d.ofCenter(center);

        List<BlockPos> withinTwo = shape.getBlocksInRadius(queryCenter, 2);
        List<BlockPos> fullShape = shape.getBlocks();

        assertTrue(withinTwo.size() < fullShape.size());
        for (BlockPos pos : withinTwo) {
            assertTrue(shape.isInside(pos));
            assertTrue(Vec3d.ofCenter(pos).squaredDistanceTo(queryCenter) <= 4.0);
        }
    }

    @Test
    void isWithinBoundsUsesBasePos() {
        BlockPos storedCenter = new BlockPos(0, 64, 0);
        BlockPos queryCenter = new BlockPos(100, 64, 100);
        BoxShape shape = new BoxShape(5, 5, 4, storedCenter);

        assertTrue(shape.isWithinBounds(queryCenter, queryCenter));
        assertFalse(shape.isWithinBounds(storedCenter, queryCenter));

        for (BlockPos pos : shape.getBlocks()) {
            assertTrue(shape.isWithinBounds(pos, storedCenter));
        }
    }

    @Test
    void setCenterMovesShape() {
        BlockPos original = new BlockPos(0, 64, 0);
        BlockPos moved = new BlockPos(10, 70, 10);
        BoxShape shape = new BoxShape(3, 3, 3, original);

        Set<BlockPos> beforeMove = new HashSet<>(shape.getBlocks());
        shape.setCenter(moved);
        Set<BlockPos> afterMove = new HashSet<>(shape.getBlocks());

        assertFalse(beforeMove.equals(afterMove));
        assertTrue(afterMove.contains(moved));
    }

    @Test
    void negativeWorldCoordinates() {
        BlockPos center = new BlockPos(0, -5, 0);
        BoxShape shape = new BoxShape(4, 4, 4, center);

        assertEquals(-6, shape.getMinY(center));
        assertEquals(-3, shape.getMaxY(center));
        assertEquals(64, shape.getBlocks().size());
    }

    @Test
    void boundingBoxContainsAllBlocks() {
        BlockPos center = new BlockPos(100, 64, 100);
        BoxShape shape = new BoxShape(8, 6, 4, center);
        Box box = shape.getBoundingBox(center);

        for (BlockPos pos : shape.getBlocks()) {
            assertTrue(pos.getX() >= Math.floor(box.minX) && pos.getX() < Math.ceil(box.maxX));
            assertTrue(pos.getY() >= Math.floor(box.minY) && pos.getY() < Math.ceil(box.maxY));
            assertTrue(pos.getZ() >= Math.floor(box.minZ) && pos.getZ() < Math.ceil(box.maxZ));
        }
    }

    @Test
    void iteratorMatchesBlockPositions() {
        BoxShape shape = new BoxShape(4, 4, 4, BlockPos.ORIGIN);

        Set<BlockPos> iterated = new HashSet<>();
        Iterator<BlockPos> iterator = shape.getBlocksIterator();
        while (iterator.hasNext()) {
            iterated.add(iterator.next());
        }

        assertEquals(new HashSet<>(shape.getBlockPositions()), iterated);
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 3, 4, 5, 8, 16, 32})
    void cubicDimensionsMatchLayerCount(int size) {
        BoxShape shape = new BoxShape(size, size, size, BlockPos.ORIGIN);
        long layers = shape.getBlocks().stream().map(BlockPos::getY).distinct().count();
        assertEquals(size, layers);
    }

    @Test
    void rejectsInvalidDimensions() {
        assertThrows(IllegalArgumentException.class, () -> new BoxShape(0, 3, 3, BlockPos.ORIGIN));
        assertThrows(IllegalArgumentException.class, () -> new BoxShape(3, 0, 3, BlockPos.ORIGIN));
        assertThrows(IllegalArgumentException.class, () -> new BoxShape(3, 3, 0, BlockPos.ORIGIN));
    }
}
