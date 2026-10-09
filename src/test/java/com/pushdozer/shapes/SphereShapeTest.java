package com.pushdozer.shapes;

import com.pushdozer.PushdozerTestBase;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SphereShapeTest extends PushdozerTestBase {

    @ParameterizedTest
    @ValueSource(ints = {1, 3, 8})
    void blockPositionsMatchInsideCheck(int radius) {
        assertBlockPositionsConsistentWithInside(new BlockPos(0, 64, 0), radius, true);
    }

    @Test
    void largeRadiusBlockPositionsMatchInsideCheck() {
        BlockPos center = new BlockPos(0, 64, 0);
        int radius = 64;
        SphereShape sphere = new SphereShape(radius, Vec3d.ofCenter(center));

        for (BlockPos pos : sphere.getBlockPositions()) {
            assertTrue(sphere.isInside(pos));
        }

        Set<BlockPos> layered = new HashSet<>();
        for (int y = sphere.getMinY(center); y <= sphere.getMaxY(center); y++) {
            layered.addAll(sphere.getBlocksInLayer(center, y));
        }
        assertEquals(new HashSet<>(sphere.getBlockPositions()), layered);
    }

    private static void assertBlockPositionsConsistentWithInside(BlockPos center, int radius, boolean scanBoundingBox) {
        SphereShape sphere = new SphereShape(radius, Vec3d.ofCenter(center));
        Set<BlockPos> positions = new HashSet<>(sphere.getBlockPositions());

        for (BlockPos pos : positions) {
            assertTrue(sphere.isInside(pos), () -> "Block " + pos + " should be inside radius " + radius);
        }

        if (!scanBoundingBox) {
            return;
        }

        Box box = sphere.getBoundingBox(center);
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
                    if (sphere.isInside(pos)) {
                        assertTrue(
                            positions.contains(pos),
                            () -> "Inside block " + pos + " missing from block positions for radius " + radius
                        );
                    }
                }
            }
        }
    }

    @Test
    void boundingBoxDoesNotDoubleOffsetWorldCenter() {
        BlockPos center = new BlockPos(100, 64, 100);
        double radius = 3;
        SphereShape sphere = new SphereShape(radius, Vec3d.ofCenter(center));

        Box box = sphere.getBoundingBox(center);
        Vec3d worldCenter = Vec3d.ofCenter(center);

        assertEquals(worldCenter.x - radius, box.minX, 0.001);
        assertEquals(worldCenter.y - radius, box.minY, 0.001);
        assertEquals(worldCenter.z - radius, box.minZ, 0.001);
        assertEquals(worldCenter.x + radius, box.maxX, 0.001);
        assertEquals(worldCenter.y + radius, box.maxY, 0.001);
        assertEquals(worldCenter.z + radius, box.maxZ, 0.001);
    }

    @Test
    void heightRangeCorrectForNegativeCoordinates() {
        BlockPos center = new BlockPos(0, -5, 0);
        SphereShape sphere = new SphereShape(3, Vec3d.ofCenter(center));

        assertEquals(-8, sphere.getMinY(center));
        assertEquals(-2, sphere.getMaxY(center));
    }

    @Test
    void layerUnionEqualsBlockPositions() {
        BlockPos center = new BlockPos(0, 64, 0);
        SphereShape sphere = new SphereShape(3, Vec3d.ofCenter(center));

        Set<BlockPos> layered = new HashSet<>();
        for (int y = sphere.getMinY(center); y <= sphere.getMaxY(center); y++) {
            layered.addAll(sphere.getBlocksInLayer(center, y));
        }

        assertEquals(new HashSet<>(sphere.getBlockPositions()), layered);
    }

    @Test
    void layerIncludesTruncationBoundaryBlocks() {
        BlockPos center = new BlockPos(0, 64, 0);
        SphereShape sphere = new SphereShape(3, Vec3d.ofCenter(center));

        BlockPos boundary = new BlockPos(1, 66, 2);
        assertTrue(sphere.isInside(boundary));
        assertTrue(
            sphere.getBlocksInLayer(center, 66).contains(boundary),
            "Layer at y=2 should include (1,2) where x^2+z^2=5"
        );
    }

    @Test
    void getBlocksMatchesBlockPositions() {
        BlockPos center = new BlockPos(0, 64, 0);
        SphereShape sphere = new SphereShape(3, Vec3d.ofCenter(center));

        assertEquals(new HashSet<>(sphere.getBlockPositions()), new HashSet<>(sphere.getBlocks()));
    }

    @Test
    void setCenterMovesGeneratedShape() {
        BlockPos original = new BlockPos(0, 64, 0);
        BlockPos moved = new BlockPos(10, 70, 10);
        SphereShape sphere = new SphereShape(3, Vec3d.ofCenter(original));

        Set<BlockPos> beforeMove = new HashSet<>(sphere.getBlockPositions());
        sphere.setCenter(moved);
        Set<BlockPos> afterMove = new HashSet<>(sphere.getBlockPositions());

        assertFalse(beforeMove.equals(afterMove));
        for (BlockPos pos : afterMove) {
            assertTrue(sphere.isInside(pos));
        }
        assertTrue(afterMove.contains(moved));
    }

    @Test
    void getBlocksInRadiusRespectsQueryCenterAndDistance() {
        BlockPos center = new BlockPos(0, 64, 0);
        SphereShape sphere = new SphereShape(5, Vec3d.ofCenter(center));
        Vec3d queryCenter = Vec3d.ofCenter(center);

        List<BlockPos> withinTwo = sphere.getBlocksInRadius(queryCenter, 2);
        List<BlockPos> fullShape = sphere.getBlockPositions();

        assertTrue(withinTwo.size() < fullShape.size());
        for (BlockPos pos : withinTwo) {
            assertTrue(Vec3d.ofCenter(pos).squaredDistanceTo(queryCenter) <= 4.0);
            assertTrue(sphere.isInside(pos));
        }

        List<BlockPos> wholeSphere = sphere.getBlocksInRadius(queryCenter, 10);
        assertEquals(new HashSet<>(fullShape), new HashSet<>(wholeSphere));
    }

    @Test
    void isWithinBoundsMatchesIsInside() {
        BlockPos center = new BlockPos(0, 64, 0);
        SphereShape sphere = new SphereShape(3, Vec3d.ofCenter(center));

        for (BlockPos pos : sphere.getBlockPositions()) {
            assertTrue(sphere.isWithinBounds(pos, center));
        }

        BlockPos outside = center.up(10);
        assertFalse(sphere.isInside(outside));
        assertFalse(sphere.isWithinBounds(outside, center));
    }

    @Test
    void rejectsNullCenterUpdate() {
        SphereShape sphere = new SphereShape(2, Vec3d.ofCenter(BlockPos.ORIGIN));
        org.junit.jupiter.api.Assertions.assertThrows(
            IllegalArgumentException.class,
            () -> sphere.setCenter(null)
        );
    }
}
