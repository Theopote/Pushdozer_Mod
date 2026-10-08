package com.pushdozer.util;

import com.pushdozer.PushdozerTestBase;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class WorldBoundsTest extends PushdozerTestBase {

    @Test
    void isBuildableY_usesBottomYAndHeight() {
        World world = mock(World.class);
        when(world.getBottomY()).thenReturn(-64);
        when(world.getHeight()).thenReturn(384);

        assertTrue(WorldBounds.isBuildableY(world, -64));
        assertTrue(WorldBounds.isBuildableY(world, 319));
        assertFalse(WorldBounds.isBuildableY(world, 320));
        assertFalse(WorldBounds.isBuildableY(world, -65));
    }

    @Test
    void isBuildablePos_checksYCoordinate() {
        World world = mock(World.class);
        when(world.getBottomY()).thenReturn(-64);
        when(world.getHeight()).thenReturn(384);

        assertTrue(WorldBounds.isBuildablePos(world, new BlockPos(0, 64, 0)));
        assertFalse(WorldBounds.isBuildablePos(world, new BlockPos(0, 320, 0)));
    }
}
