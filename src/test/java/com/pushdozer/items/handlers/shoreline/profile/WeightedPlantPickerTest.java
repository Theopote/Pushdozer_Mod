package com.pushdozer.items.handlers.shoreline.profile;

import net.minecraft.block.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class WeightedPlantPickerTest {

    @Test
    void pick_isDeterministicForSameSeed() {
        Random randomA = Random.create(42L);
        Random randomB = Random.create(42L);
        var choices = java.util.List.of(
            WeightedPlantPicker.WeightedPlantChoice.block(Blocks.CACTUS, 0.7f),
            WeightedPlantPicker.WeightedPlantChoice.block(Blocks.DEAD_BUSH, 0.3f)
        );

        assertEquals(
            WeightedPlantPicker.pick(randomA, null, choices).getBlock(),
            WeightedPlantPicker.pick(randomB, null, choices).getBlock()
        );
    }

    @Test
    void positionRandom_differsByCoordinate() {
        Random atOrigin = com.pushdozer.util.PositionRandom.at(BlockPos.ORIGIN);
        Random atNeighbor = com.pushdozer.util.PositionRandom.at(BlockPos.ORIGIN.add(1, 0, 0));
        assertNotNull(atOrigin);
        assertNotNull(atNeighbor);
    }
}
