package com.pushdozer.shapes;

import com.pushdozer.PushdozerTestBase;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GeometryShapeTest extends PushdozerTestBase {

    @Test
    void boxShape_containsCenterAndRejectsOutsideBlocks() {
        BlockPos center = new BlockPos(10, 64, 10);
        BoxShape box = new BoxShape(4, 4, 4, center);

        assertTrue(box.isInside(center));
        assertTrue(box.isInside(center.east(1)));
        assertFalse(box.isInside(center.east(10)));
    }

    @Test
    void sphereShape_containsCenterAndRejectsFarBlocks() {
        BlockPos center = new BlockPos(0, 64, 0);
        SphereShape sphere = new SphereShape(3, Vec3d.ofCenter(center));

        assertTrue(sphere.isInside(center));
        assertTrue(sphere.isInside(center.up(2)));
        assertFalse(sphere.isInside(center.up(5)));
    }

    @Test
    void sphereShape_rejectsNullCenterUpdate() {
        SphereShape sphere = new SphereShape(2, Vec3d.ofCenter(BlockPos.ORIGIN));
        org.junit.jupiter.api.Assertions.assertThrows(
            IllegalArgumentException.class,
            () -> sphere.setCenter(null)
        );
    }
}
