package com.pushdozer.items.handlers;

import com.pushdozer.PushdozerTestBase;
import com.pushdozer.operations.BlockOperation;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BoneMealHandlerTest extends PushdozerTestBase {

    @Test
    void recordChanges_mergesMultipleGrowthStagesIntoFinalState() {
        BlockPos pos = new BlockPos(4, 64, 4);
        BlockState stage1 = Blocks.WHEAT.getDefaultState().with(net.minecraft.state.property.Properties.AGE_7, 1);
        BlockState stage2 = Blocks.WHEAT.getDefaultState().with(net.minecraft.state.property.Properties.AGE_7, 2);
        BlockState stage4 = Blocks.WHEAT.getDefaultState().with(net.minecraft.state.property.Properties.AGE_7, 4);
        BlockState original = Blocks.WHEAT.getDefaultState().with(net.minecraft.state.property.Properties.AGE_7, 0);

        World world = mock(World.class);
        when(world.getBlockState(pos)).thenReturn(stage1, stage2, stage4);

        Map<BlockPos, BlockState> before = Map.of(pos, original);
        Map<BlockPos, BlockOperation.BlockChange> changes = new LinkedHashMap<>();

        Set<BlockPos> check = Set.of(pos);
        BoneMealHandler.recordChanges(check, before, world, changes);
        BoneMealHandler.recordChanges(check, before, world, changes);
        BoneMealHandler.recordChanges(check, before, world, changes);

        assertEquals(1, changes.size());
        BlockOperation.BlockChange change = changes.get(pos);
        assertEquals(original, change.before());
        assertEquals(stage4, change.after());
    }

    @Test
    void collectLockPositions_includesExpandedTreeCanopyArea() {
        List<BlockPos> targets = List.of(new BlockPos(0, 64, 0));
        Set<BlockPos> locks = BoneMealHandler.collectLockPositions(targets);

        assertTrue(locks.contains(new BlockPos(8, 64 + 32, 8)));
        assertTrue(locks.contains(new BlockPos(-8, 64 - 4, -8)));
        assertTrue(locks.size() > 100);
    }
}
