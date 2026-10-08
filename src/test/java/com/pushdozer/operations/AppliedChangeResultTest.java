package com.pushdozer.operations;

import com.pushdozer.PushdozerTestBase;
import net.minecraft.block.Blocks;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AppliedChangeResultTest extends PushdozerTestBase {

    @Test
    void mergedWithCombinesBothResults() {
        AppliedChangeResult first = new AppliedChangeResult(
            java.util.List.of(new BlockPos(0, 64, 0)),
            java.util.List.of(Blocks.STONE.getDefaultState()),
            java.util.List.of(Blocks.DIRT.getDefaultState())
        );
        AppliedChangeResult second = new AppliedChangeResult(
            java.util.List.of(new BlockPos(1, 64, 0)),
            java.util.List.of(Blocks.GRASS_BLOCK.getDefaultState()),
            java.util.List.of(Blocks.SAND.getDefaultState())
        );

        AppliedChangeResult merged = first.mergedWith(second);

        assertEquals(2, merged.positions().size());
        assertEquals(new BlockPos(0, 64, 0), merged.positions().get(0));
        assertEquals(new BlockPos(1, 64, 0), merged.positions().get(1));
    }

    @Test
    void emptyMergedWithReturnsOther() {
        AppliedChangeResult other = new AppliedChangeResult(
            java.util.List.of(new BlockPos(0, 64, 0)),
            java.util.List.of(Blocks.STONE.getDefaultState()),
            java.util.List.of(Blocks.AIR.getDefaultState())
        );

        assertEquals(other, AppliedChangeResult.empty().mergedWith(other));
        assertTrue(AppliedChangeResult.empty().isEmpty());
    }
}
