package com.pushdozer.shapes;

import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Memory-conscious helpers for geometry shape contract checks.
 * Prefer single-set algorithms over building multiple full voxel collections.
 */
public final class ShapeTestSupport {
    private ShapeTestSupport() {
    }

    public static void assertLayerUnionEqualsBlockPositions(GeometryShape shape, BlockPos center) {
        Set<BlockPos> remaining = new HashSet<>(shape.getBlockPositions());
        for (int y = shape.getMinY(center); y <= shape.getMaxY(center); y++) {
            final int layerY = y;
            for (BlockPos pos : shape.getBlocksInLayer(center, layerY)) {
                assertTrue(remaining.remove(pos), () -> "Unexpected layer block at y=" + layerY + ": " + pos);
            }
        }
        assertTrue(remaining.isEmpty(), () -> remaining.size() + " blocks missing from layer union");
    }

    public static void assertIteratorMatchesBlockPositions(GeometryShape shape) {
        int expected = shape.getBlockPositions().size();
        int count = 0;
        Iterator<BlockPos> iterator = shape.getBlocksIterator();
        while (iterator.hasNext()) {
            BlockPos pos = iterator.next();
            assertTrue(shape.isInside(pos), () -> "Iterator block not inside: " + pos);
            count++;
        }
        assertEquals(expected, count);
    }

    public static void assertAllBlocksInside(GeometryShape shape) {
        for (BlockPos pos : shape.getBlockPositions()) {
            assertTrue(shape.isInside(pos), () -> "Block not inside: " + pos);
        }
    }

    public static void assertRadiusQueryMatchesShape(GeometryShape shape, BlockPos center, int maxDistance) {
        Vec3d queryCenter = Vec3d.ofCenter(center);
        double maxDistanceSquared = (double) maxDistance * maxDistance;
        Set<BlockPos> expected = new HashSet<>();
        for (BlockPos pos : shape.getBlockPositions()) {
            if (Vec3d.ofCenter(pos).squaredDistanceTo(queryCenter) <= maxDistanceSquared) {
                expected.add(pos);
            }
        }
        assertEquals(expected, new HashSet<>(shape.getBlocksInRadius(queryCenter, maxDistance)));
    }

    public static void assertIsWithinBoundsUsesBasePos(GeometryShape shape, BlockPos storedCenter, BlockPos queryCenter) {
        assertTrue(shape.isWithinBounds(queryCenter, queryCenter));
        assertFalse(shape.isWithinBounds(storedCenter, queryCenter));
        for (BlockPos pos : shape.getBlockPositions()) {
            assertTrue(shape.isWithinBounds(pos, storedCenter), () -> "Block not within stored bounds: " + pos);
        }
    }

    public static void assertLargeShapeLayerSmoke(GeometryShape shape, BlockPos center, int expectedLayers) {
        assertFalse(shape.getBlockPositions().isEmpty());
        assertLayerUnionEqualsBlockPositions(shape, center);
        if (expectedLayers > 0) {
            long layers = shape.getBlockPositions().stream().map(BlockPos::getY).distinct().count();
            assertEquals(expectedLayers, layers);
        }
    }
}
