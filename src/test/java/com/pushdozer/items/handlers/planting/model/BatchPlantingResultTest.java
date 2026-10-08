package com.pushdozer.items.handlers.planting.model;

import com.pushdozer.PushdozerTestBase;
import net.minecraft.block.Blocks;
import net.minecraft.block.enums.DoubleBlockHalf;
import net.minecraft.state.property.Properties;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BatchPlantingResultTest extends PushdozerTestBase {

    @Test
    void getTallPlantPairs_detectsLowerAndUpperIndices() {
        BatchPlantingResult result = new BatchPlantingResult();
        BlockPos lower = new BlockPos(1, 64, 1);
        BlockPos upper = lower.up();

        result.addSimplePlant(lower, Blocks.AIR.getDefaultState(),
            Blocks.SUNFLOWER.getDefaultState().with(Properties.DOUBLE_BLOCK_HALF, DoubleBlockHalf.LOWER));
        result.addSimplePlant(upper, Blocks.AIR.getDefaultState(),
            Blocks.SUNFLOWER.getDefaultState().with(Properties.DOUBLE_BLOCK_HALF, DoubleBlockHalf.UPPER));

        List<int[]> pairs = result.getTallPlantPairs();
        assertEquals(1, pairs.size());
        assertEquals(0, pairs.get(0)[0]);
        assertEquals(1, pairs.get(0)[1]);
    }
}
