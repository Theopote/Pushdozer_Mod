package com.pushdozer.items.handlers.shoreline;

import com.pushdozer.PushdozerTestBase;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShorelineHorizontalDistanceTest extends PushdozerTestBase {

    @Test
    void compute_assignsHorizontalDistanceFromWater() {
        Set<BlockPos> water = Set.of(new BlockPos(0, 1, 0));
        Map<BlockPos, Integer> distances = ShorelineHorizontalDistance.compute(water, 5);

        assertEquals(1, distances.get(new BlockPos(1, 0, 0)));
        assertEquals(2, distances.get(new BlockPos(2, 0, 0)));
        assertFalse(distances.containsKey(new BlockPos(0, 0, 0)));
    }

    @Test
    void compute_treatsVerticalWaterAsSingleColumn() {
        Set<BlockPos> water = Set.of(new BlockPos(0, 1, 0), new BlockPos(0, 2, 0));
        Map<BlockPos, Integer> distances = ShorelineHorizontalDistance.compute(water, 5);

        assertEquals(1, distances.get(new BlockPos(1, 0, 0)));
        assertFalse(distances.containsKey(new BlockPos(0, 0, 0)));
    }
}
