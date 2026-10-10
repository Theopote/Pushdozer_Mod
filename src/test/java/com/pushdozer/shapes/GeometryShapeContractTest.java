package com.pushdozer.shapes;

import com.pushdozer.PushdozerTestBase;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GeometryShapeContractTest extends PushdozerTestBase {

    private static final BlockPos[] TEST_CENTERS = {
        BlockPos.ORIGIN,
        new BlockPos(0, 64, 0),
        new BlockPos(-100, -32, -100),
        new BlockPos(100, 128, 100)
    };

    enum ShapeKind {
        SPHERE, ELLIPSOID, CYLINDER, CONE, BOX, OCTAHEDRON, TETRAHEDRON, TRIANGULAR_PRISM
    }

    @ParameterizedTest
    @EnumSource(ShapeKind.class)
    void layerUnionEqualsBlockPositions(ShapeKind kind) {
        for (BlockPos center : TEST_CENTERS) {
            GeometryShape shape = create(kind, 5, center);
            ShapeTestSupport.assertLayerUnionEqualsBlockPositions(shape, center);
        }
    }

    @ParameterizedTest
    @EnumSource(ShapeKind.class)
    void iteratorMatchesBlockPositions(ShapeKind kind) {
        for (BlockPos center : TEST_CENTERS) {
            GeometryShape shape = create(kind, 5, center);
            ShapeTestSupport.assertIteratorMatchesBlockPositions(shape);
        }
    }

    @ParameterizedTest
    @EnumSource(ShapeKind.class)
    void blockInsideMatchesGeneratedSet(ShapeKind kind) {
        for (BlockPos center : TEST_CENTERS) {
            GeometryShape shape = create(kind, 5, center);
            ShapeTestSupport.assertAllBlocksInside(shape);
        }
    }

    @ParameterizedTest
    @EnumSource(ShapeKind.class)
    void radiusQueryMatchesShapeIntersection(ShapeKind kind) {
        BlockPos center = new BlockPos(0, 64, 0);
        GeometryShape shape = create(kind, 8, center);
        ShapeTestSupport.assertRadiusQueryMatchesShape(shape, center, 3);
    }

    @ParameterizedTest
    @EnumSource(ShapeKind.class)
    void isWithinBoundsUsesBasePos(ShapeKind kind) {
        BlockPos storedCenter = new BlockPos(0, 64, 0);
        BlockPos queryCenter = new BlockPos(100, 64, 100);
        GeometryShape shape = create(kind, 5, storedCenter);
        ShapeTestSupport.assertIsWithinBoundsUsesBasePos(shape, storedCenter, queryCenter);
    }

    @ParameterizedTest
    @EnumSource(ShapeKind.class)
    void setCenterMovesShape(ShapeKind kind) {
        BlockPos original = new BlockPos(0, 64, 0);
        BlockPos moved = new BlockPos(10, 70, 10);
        GeometryShape shape = create(kind, 5, original);

        List<BlockPos> beforeMove = shape.getBlockPositions();
        shape.setCenter(moved);
        List<BlockPos> afterMove = shape.getBlockPositions();

        assertFalse(beforeMove.equals(afterMove));
        assertTrue(afterMove.contains(moved));
    }

    @ParameterizedTest
    @EnumSource(ShapeKind.class)
    void largeSizeLayerSmoke(ShapeKind kind) {
        BlockPos center = new BlockPos(0, 64, 0);
        GeometryShape shape = create(kind, 16, center);
        ShapeTestSupport.assertLargeShapeLayerSmoke(shape, center, expectedLayers(kind, 16));
    }

    @Test
    void farRadiusQueryReturnsEmpty() {
        BlockPos center = new BlockPos(0, 64, 0);
        Vec3d farQuery = Vec3d.ofCenter(new BlockPos(20, 64, 0));

        for (ShapeKind kind : ShapeKind.values()) {
            GeometryShape shape = create(kind, 5, center);
            assertTrue(shape.getBlocksInRadius(farQuery, 2).isEmpty(), kind.name());
        }
    }

    private static GeometryShape create(ShapeKind kind, int size, BlockPos center) {
        return switch (kind) {
            case SPHERE -> new SphereShape(size, Vec3d.ofCenter(center));
            case ELLIPSOID -> new EllipsoidShape(size, size, size, center);
            case CYLINDER -> new CylinderShape(size, size, center);
            case CONE -> new ConeShape(size, size, center);
            case BOX -> new BoxShape(size, size, size, center);
            case OCTAHEDRON -> new OctahedronShape(size, center);
            case TETRAHEDRON -> new TetrahedronShape(size, center);
            case TRIANGULAR_PRISM -> new TriangularPrismShape(size, size, center);
        };
    }

    private static int expectedLayers(ShapeKind kind, int size) {
        return switch (kind) {
            case SPHERE, ELLIPSOID, OCTAHEDRON, TETRAHEDRON -> 0;
            case CYLINDER, CONE, BOX, TRIANGULAR_PRISM -> size;
        };
    }
}
