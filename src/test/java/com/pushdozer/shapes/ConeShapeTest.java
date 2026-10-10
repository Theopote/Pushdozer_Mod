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

class ConeShapeTest extends PushdozerTestBase {

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 3, 4, 5, 8, 16, 64})
    void coneHeightMatchesConfiguration(int height) {
        ConeShape shape = new ConeShape(3, height, BlockPos.ORIGIN);

        long layers = shape.getBlockPositions().stream()
            .map(BlockPos::getY)
            .distinct()
            .count();

        assertEquals(height, layers);
    }

    @Test
    void evenHeightNoExtraLayer() {
        ConeShape shape = new ConeShape(3, 4, BlockPos.ORIGIN);

        assertEquals(4, shape.getBlockPositions().stream().map(BlockPos::getY).distinct().count());
        assertEquals(-1, shape.getMinY(BlockPos.ORIGIN));
        assertEquals(2, shape.getMaxY(BlockPos.ORIGIN));
    }

    @Test
    void bottomLayerHasFullRadius() {
        ConeShape shape = new ConeShape(10, 5, BlockPos.ORIGIN);
        int bottomY = shape.getMinY(BlockPos.ORIGIN);

        BlockPos onBaseEdge = new BlockPos(10, bottomY, 0);
        assertTrue(shape.getBlocksInLayer(BlockPos.ORIGIN, bottomY).contains(onBaseEdge));
        assertTrue(shape.isInside(onBaseEdge));
    }

    @Test
    void topLayerConvergesToApex() {
        ConeShape shape = new ConeShape(10, 5, BlockPos.ORIGIN);
        int topY = shape.getMaxY(BlockPos.ORIGIN);

        List<BlockPos> topLayer = shape.getBlocksInLayer(BlockPos.ORIGIN, topY);
        assertEquals(1, topLayer.size());
        assertEquals(BlockPos.ORIGIN.up(topY), topLayer.getFirst());
    }

    @Test
    void layerUnionEqualsBlockPositions() {
        ConeShape shape = new ConeShape(5, 4, BlockPos.ORIGIN);

        Set<BlockPos> all = new HashSet<>(shape.getBlockPositions());
        Set<BlockPos> layers = new HashSet<>();
        for (int y = shape.getMinY(BlockPos.ORIGIN); y <= shape.getMaxY(BlockPos.ORIGIN); y++) {
            layers.addAll(shape.getBlocksInLayer(BlockPos.ORIGIN, y));
        }

        assertEquals(all, layers);
    }

    @Test
    void blockCenterSampling() {
        ConeShape shape = new ConeShape(5, 5, new BlockPos(0, 64, 0));

        for (BlockPos pos : shape.getBlockPositions()) {
            assertTrue(shape.isInside(pos));
            assertTrue(shape.isInside(Vec3d.ofCenter(pos)));
        }
    }

    @Test
    void getBlocksInRadiusRespectsQueryCenter() {
        BlockPos storedCenter = new BlockPos(0, 64, 0);
        ConeShape shape = new ConeShape(5, 4, storedCenter);
        Vec3d queryCenter = Vec3d.ofCenter(storedCenter.east(4));

        List<BlockPos> nearQuery = shape.getBlocksInRadius(queryCenter, 2);
        List<BlockPos> fullShape = shape.getBlockPositions();

        assertTrue(nearQuery.size() < fullShape.size());
        for (BlockPos pos : nearQuery) {
            assertTrue(shape.isInside(pos));
            assertTrue(Vec3d.ofCenter(pos).squaredDistanceTo(queryCenter) <= 4.0);
        }
    }

    @Test
    void isWithinBoundsUsesBasePos() {
        BlockPos storedCenter = new BlockPos(0, 64, 0);
        BlockPos queryCenter = new BlockPos(100, 64, 100);
        ConeShape shape = new ConeShape(5, 4, storedCenter);

        assertTrue(shape.isWithinBounds(queryCenter, queryCenter));
        assertFalse(shape.isWithinBounds(storedCenter, queryCenter));

        for (BlockPos pos : shape.getBlockPositions()) {
            assertTrue(shape.isWithinBounds(pos, storedCenter));
        }
    }

    @Test
    void boundingBoxContainsAllBlocks() {
        BlockPos center = new BlockPos(100, 64, 100);
        ConeShape shape = new ConeShape(8, 4, center);
        Box box = shape.getBoundingBox(center);

        for (BlockPos pos : shape.getBlockPositions()) {
            assertTrue(pos.getX() >= Math.floor(box.minX) && pos.getX() < Math.ceil(box.maxX));
            assertTrue(pos.getY() >= Math.floor(box.minY) && pos.getY() < Math.ceil(box.maxY));
            assertTrue(pos.getZ() >= Math.floor(box.minZ) && pos.getZ() < Math.ceil(box.maxZ));
        }
    }

    @Test
    void previewBoundsMatchVoxelExtent() {
        BlockPos center = new BlockPos(0, 64, 0);
        ConeShape shape = new ConeShape(5, 4, center);

        float previewHeight = shape.getPreviewTopY(center) - shape.getPreviewBottomY(center);
        int voxelHeight = shape.getMaxY(center) - shape.getMinY(center) + 1;

        assertEquals(voxelHeight, previewHeight, 0.001f);
    }

    @Test
    void setCenterMovesShape() {
        BlockPos original = new BlockPos(0, 64, 0);
        BlockPos moved = new BlockPos(10, 70, 10);
        ConeShape shape = new ConeShape(3, 4, original);

        Set<BlockPos> beforeMove = new HashSet<>(shape.getBlockPositions());
        shape.setCenter(moved);
        Set<BlockPos> afterMove = new HashSet<>(shape.getBlockPositions());

        assertFalse(beforeMove.equals(afterMove));
        assertTrue(afterMove.contains(moved));
    }

    @Test
    void negativeWorldCoordinates() {
        BlockPos center = new BlockPos(0, -5, 0);
        ConeShape shape = new ConeShape(3, 4, center);

        assertEquals(-6, shape.getMinY(center));
        assertEquals(-3, shape.getMaxY(center));
        assertEquals(4, shape.getBlockPositions().stream().map(BlockPos::getY).distinct().count());
    }

    @Test
    void heightOneIsFullDisk() {
        ConeShape shape = new ConeShape(5, 1, BlockPos.ORIGIN);

        assertEquals(1, shape.getBlockPositions().stream().map(BlockPos::getY).distinct().count());
        assertTrue(shape.isInside(new BlockPos(5, 0, 0)));
        assertFalse(shape.isInside(new BlockPos(6, 0, 0)));
    }

    @Test
    void iteratorMatchesBlockPositions() {
        ConeShape shape = new ConeShape(5, 4, BlockPos.ORIGIN);

        Set<BlockPos> iterated = new HashSet<>();
        Iterator<BlockPos> iterator = shape.getBlocksIterator();
        while (iterator.hasNext()) {
            iterated.add(iterator.next());
        }

        assertEquals(new HashSet<>(shape.getBlockPositions()), iterated);
    }

    @Test
    void largeParametersLayerUnionMatchesBlockPositions() {
        BlockPos center = new BlockPos(0, 64, 0);
        ConeShape shape = new ConeShape(64, 64, center);

        Set<BlockPos> layered = new HashSet<>();
        for (int y = shape.getMinY(center); y <= shape.getMaxY(center); y++) {
            layered.addAll(shape.getBlocksInLayer(center, y));
        }

        assertEquals(new HashSet<>(shape.getBlockPositions()), layered);
        assertEquals(64, shape.getBlockPositions().stream().map(BlockPos::getY).distinct().count());
    }

    @Test
    void rejectsInvalidDimensions() {
        assertThrows(IllegalArgumentException.class, () -> new ConeShape(0, 5, BlockPos.ORIGIN));
        assertThrows(IllegalArgumentException.class, () -> new ConeShape(5, 0, BlockPos.ORIGIN));
    }
}
