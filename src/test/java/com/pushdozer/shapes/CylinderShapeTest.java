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
import static org.junit.jupiter.api.Assertions.assertTrue;

class CylinderShapeTest extends PushdozerTestBase {

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 3, 4, 5, 6, 8, 16})
    void cylinderHeightMatchesConfiguration(int height) {
        CylinderShape shape = new CylinderShape(3, height, BlockPos.ORIGIN);

        long layers = shape.getBlockPositions().stream()
            .map(BlockPos::getY)
            .distinct()
            .count();

        assertEquals(height, layers);
    }

    @Test
    void cylinderLayersMatchFullShape() {
        CylinderShape shape = new CylinderShape(5, 4, BlockPos.ORIGIN);

        Set<BlockPos> all = new HashSet<>(shape.getBlockPositions());
        Set<BlockPos> layers = new HashSet<>();

        for (int y = shape.getMinY(BlockPos.ORIGIN); y <= shape.getMaxY(BlockPos.ORIGIN); y++) {
            layers.addAll(shape.getBlocksInLayer(BlockPos.ORIGIN, y));
        }

        assertEquals(all, layers);
    }

    @Test
    void evenHeightNoExtraLayer() {
        CylinderShape shape = new CylinderShape(3, 4, BlockPos.ORIGIN);

        long layers = shape.getBlockPositions().stream()
            .map(BlockPos::getY)
            .distinct()
            .count();

        assertEquals(4, layers);
        assertEquals(-1, shape.getMinY(BlockPos.ORIGIN));
        assertEquals(2, shape.getMaxY(BlockPos.ORIGIN));
    }

    @Test
    void blockCenterSampling() {
        BlockPos center = new BlockPos(0, 64, 0);
        CylinderShape shape = new CylinderShape(5, 4, center);

        for (BlockPos pos : shape.getBlockPositions()) {
            assertTrue(shape.isInside(pos));
            assertTrue(shape.isInside(Vec3d.ofCenter(pos)));
        }
    }

    @Test
    void getBlocksInRadiusRespectsQueryCenter() {
        BlockPos storedCenter = new BlockPos(0, 64, 0);
        CylinderShape shape = new CylinderShape(5, 4, storedCenter);
        Vec3d queryCenter = Vec3d.ofCenter(storedCenter.east(4));

        List<BlockPos> nearQuery = shape.getBlocksInRadius(queryCenter, 2);
        List<BlockPos> fullShape = shape.getBlockPositions();

        assertTrue(nearQuery.size() < fullShape.size());
        for (BlockPos pos : nearQuery) {
            assertTrue(shape.isInside(pos));
            assertTrue(Vec3d.ofCenter(pos).squaredDistanceTo(queryCenter) <= 4.0);
        }

        for (BlockPos pos : fullShape) {
            if (Vec3d.ofCenter(pos).squaredDistanceTo(queryCenter) > 4.0) {
                assertFalse(nearQuery.contains(pos));
            }
        }
    }

    @Test
    void isWithinBoundsUsesBasePos() {
        BlockPos storedCenter = new BlockPos(0, 64, 0);
        BlockPos queryCenter = new BlockPos(100, 64, 100);
        CylinderShape shape = new CylinderShape(5, 4, storedCenter);

        assertTrue(shape.isWithinBounds(queryCenter, queryCenter));
        assertFalse(shape.isWithinBounds(storedCenter, queryCenter));

        for (BlockPos pos : shape.getBlockPositions()) {
            assertTrue(shape.isWithinBounds(pos, storedCenter));
        }
    }

    @Test
    void boundingBoxContainsAllBlocks() {
        BlockPos center = new BlockPos(100, 64, 100);
        CylinderShape shape = new CylinderShape(8, 4, center);
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
        CylinderShape shape = new CylinderShape(5, 4, center);

        float previewHeight = shape.getPreviewTopY(center) - shape.getPreviewBottomY(center);
        int voxelHeight = shape.getMaxY(center) - shape.getMinY(center) + 1;

        assertEquals(voxelHeight, previewHeight, 0.001f);
    }

    @Test
    void setCenterMovesShape() {
        BlockPos original = new BlockPos(0, 64, 0);
        BlockPos moved = new BlockPos(10, 70, 10);
        CylinderShape shape = new CylinderShape(3, 4, original);

        Set<BlockPos> beforeMove = new HashSet<>(shape.getBlockPositions());
        shape.setCenter(moved);
        Set<BlockPos> afterMove = new HashSet<>(shape.getBlockPositions());

        assertFalse(beforeMove.equals(afterMove));
        assertTrue(afterMove.contains(moved));
        for (BlockPos pos : afterMove) {
            assertTrue(shape.isInside(pos));
        }
    }

    @Test
    void negativeWorldCoordinates() {
        BlockPos center = new BlockPos(0, -5, 0);
        CylinderShape shape = new CylinderShape(3, 4, center);

        assertEquals(-6, shape.getMinY(center));
        assertEquals(-3, shape.getMaxY(center));
        assertEquals(4, shape.getBlockPositions().stream().map(BlockPos::getY).distinct().count());
    }

    @Test
    void noDuplicateBlocks() {
        CylinderShape shape = new CylinderShape(5, 4, BlockPos.ORIGIN);
        List<BlockPos> positions = shape.getBlockPositions();

        assertEquals(positions.size(), new HashSet<>(positions).size());
    }

    @Test
    void largeRadiusLayerUnionMatchesBlockPositions() {
        BlockPos center = new BlockPos(0, 64, 0);
        CylinderShape shape = new CylinderShape(32, 32, center);
        ShapeTestSupport.assertLargeShapeLayerSmoke(shape, center, 32);
    }

    @Test
    void iteratorMatchesBlockPositions() {
        CylinderShape shape = new CylinderShape(5, 4, BlockPos.ORIGIN);

        Set<BlockPos> iterated = new HashSet<>();
        Iterator<BlockPos> iterator = shape.getBlocksIterator();
        while (iterator.hasNext()) {
            iterated.add(iterator.next());
        }

        assertEquals(new HashSet<>(shape.getBlockPositions()), iterated);
    }
}
