package com.pushdozer.shapes;

import com.pushdozer.PushdozerTestBase;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EllipsoidShapeTest extends PushdozerTestBase {

    @ParameterizedTest
    @CsvSource({
        "3,5,8",
        "8,4,6",
        "5,5,5"
    })
    void layerUnionEqualsBlockPositions(int radiusX, int radiusY, int radiusZ) {
        BlockPos center = new BlockPos(0, 64, 0);
        EllipsoidShape ellipsoid = new EllipsoidShape(radiusX, radiusY, radiusZ, center);

        Set<BlockPos> layered = new HashSet<>();
        for (int y = ellipsoid.getMinY(center); y <= ellipsoid.getMaxY(center); y++) {
            layered.addAll(ellipsoid.getBlocksInLayer(center, y));
        }

        assertEquals(new HashSet<>(ellipsoid.getBlockPositions()), layered);
    }

    @Test
    void topAndBottomLayersIncluded() {
        BlockPos center = new BlockPos(0, 64, 0);
        int radiusY = 5;
        EllipsoidShape ellipsoid = new EllipsoidShape(5, radiusY, 5, center);

        BlockPos top = center.up(radiusY);
        BlockPos bottom = center.down(radiusY);

        assertTrue(ellipsoid.isInside(top));
        assertTrue(ellipsoid.isInside(bottom));
        assertTrue(ellipsoid.getBlocksInLayer(center, top.getY()).contains(top));
        assertTrue(ellipsoid.getBlocksInLayer(center, bottom.getY()).contains(bottom));
    }

    @Test
    void equalRadiiMatchesSphere() {
        BlockPos center = new BlockPos(0, 64, 0);
        int radius = 5;

        SphereShape sphere = new SphereShape(radius, Vec3d.ofCenter(center));
        EllipsoidShape ellipsoid = new EllipsoidShape(radius, radius, radius, center);

        assertEquals(
            new HashSet<>(sphere.getBlockPositions()),
            new HashSet<>(ellipsoid.getBlockPositions())
        );
    }

    @ParameterizedTest
    @CsvSource({
        "3,5,8",
        "8,4,6"
    })
    void blockCenterSampling(int radiusX, int radiusY, int radiusZ) {
        BlockPos center = new BlockPos(0, 64, 0);
        EllipsoidShape ellipsoid = new EllipsoidShape(radiusX, radiusY, radiusZ, center);
        Set<BlockPos> positions = new HashSet<>(ellipsoid.getBlockPositions());

        for (BlockPos pos : positions) {
            assertTrue(ellipsoid.isInside(pos));
        }

        Box box = ellipsoid.getBoundingBox(center);
        int minX = (int) Math.floor(box.minX);
        int maxX = (int) Math.ceil(box.maxX);
        int minY = (int) Math.floor(box.minY);
        int maxY = (int) Math.ceil(box.maxY);
        int minZ = (int) Math.floor(box.minZ);
        int maxZ = (int) Math.ceil(box.maxZ);

        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    if (ellipsoid.isInside(pos)) {
                        assertTrue(positions.contains(pos));
                    }
                }
            }
        }
    }

    @Test
    void boundingBoxContainsAllBlocks() {
        BlockPos center = new BlockPos(100, 64, 100);
        EllipsoidShape ellipsoid = new EllipsoidShape(8, 4, 6, center);
        Box box = ellipsoid.getBoundingBox(center);

        for (BlockPos pos : ellipsoid.getBlockPositions()) {
            assertTrue(pos.getX() >= Math.floor(box.minX) && pos.getX() <= Math.floor(box.maxX));
            assertTrue(pos.getY() >= Math.floor(box.minY) && pos.getY() <= Math.floor(box.maxY));
            assertTrue(pos.getZ() >= Math.floor(box.minZ) && pos.getZ() <= Math.floor(box.maxZ));
        }
    }

    @Test
    void isWithinBoundsUsesBasePos() {
        BlockPos storedCenter = new BlockPos(0, 64, 0);
        BlockPos queryCenter = new BlockPos(100, 64, 100);
        EllipsoidShape ellipsoid = new EllipsoidShape(5, 5, 5, storedCenter);

        assertTrue(ellipsoid.isWithinBounds(queryCenter, queryCenter));
        assertFalse(ellipsoid.isWithinBounds(storedCenter, queryCenter));

        for (BlockPos pos : ellipsoid.getBlockPositions()) {
            assertTrue(ellipsoid.isWithinBounds(pos, storedCenter));
        }
    }

    @Test
    void setCenterMovesShape() {
        BlockPos original = new BlockPos(0, 64, 0);
        BlockPos moved = new BlockPos(10, 70, 10);
        EllipsoidShape ellipsoid = new EllipsoidShape(3, 5, 4, original);

        Set<BlockPos> beforeMove = new HashSet<>(ellipsoid.getBlockPositions());
        ellipsoid.setCenter(moved);
        Set<BlockPos> afterMove = new HashSet<>(ellipsoid.getBlockPositions());

        assertFalse(beforeMove.equals(afterMove));
        for (BlockPos pos : afterMove) {
            assertTrue(ellipsoid.isInside(pos));
        }
        assertTrue(afterMove.contains(moved));
    }

    @Test
    void getBlocksInRadiusRespectsDistance() {
        BlockPos center = new BlockPos(0, 64, 0);
        EllipsoidShape ellipsoid = new EllipsoidShape(5, 5, 5, center);
        Vec3d queryCenter = Vec3d.ofCenter(center);

        List<BlockPos> withinTwo = ellipsoid.getBlocksInRadius(queryCenter, 2);
        List<BlockPos> fullShape = ellipsoid.getBlockPositions();

        assertTrue(withinTwo.size() < fullShape.size());
        for (BlockPos pos : withinTwo) {
            assertTrue(Vec3d.ofCenter(pos).squaredDistanceTo(queryCenter) <= 4.0);
            assertTrue(ellipsoid.isInside(pos));
        }

        List<BlockPos> wholeEllipsoid = ellipsoid.getBlocksInRadius(queryCenter, 10);
        assertEquals(new HashSet<>(fullShape), new HashSet<>(wholeEllipsoid));
    }

    @Test
    void anisotropicRadiiExtendsAlongConfiguredAxes() {
        BlockPos center = new BlockPos(0, 64, 0);
        EllipsoidShape ellipsoid = new EllipsoidShape(8, 3, 4, center);

        assertTrue(ellipsoid.isInside(center.east(8)));
        assertFalse(ellipsoid.isInside(center.east(9)));
        assertTrue(ellipsoid.isInside(center.up(3)));
        assertFalse(ellipsoid.isInside(center.up(4)));
        assertTrue(ellipsoid.isInside(center.south(4)));
        assertFalse(ellipsoid.isInside(center.south(5)));
    }

    @Test
    void largeRadiusLayerUnionMatchesBlockPositions() {
        BlockPos center = new BlockPos(0, 64, 0);
        EllipsoidShape ellipsoid = new EllipsoidShape(32, 32, 32, center);
        ShapeTestSupport.assertLargeShapeLayerSmoke(ellipsoid, center, 0);
    }

    @Test
    void iteratorMatchesBlockPositions() {
        BlockPos center = new BlockPos(0, 64, 0);
        EllipsoidShape ellipsoid = new EllipsoidShape(5, 5, 5, center);

        Set<BlockPos> iterated = new HashSet<>();
        Iterator<BlockPos> iterator = ellipsoid.getBlocksIterator();
        while (iterator.hasNext()) {
            iterated.add(iterator.next());
        }

        assertEquals(new HashSet<>(ellipsoid.getBlockPositions()), iterated);
    }
}
