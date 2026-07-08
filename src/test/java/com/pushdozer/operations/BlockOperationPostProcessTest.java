package com.pushdozer.operations;

import com.pushdozer.PushdozerTestBase;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.chunk.light.LightingProvider;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BlockOperationPostProcessTest extends PushdozerTestBase {

    @Test
    void postProcessBlockChanges_smallOperation_checksEveryBlockAndNeighbors() {
        ServerWorld world = mock(ServerWorld.class);
        LightingProvider lighting = mock(LightingProvider.class);
        when(world.getLightingProvider()).thenReturn(lighting);
        when(world.isChunkLoaded(anyLong())).thenReturn(true);

        List<BlockPos> positions = List.of(
            new BlockPos(0, 64, 0),
            new BlockPos(1, 64, 0),
            new BlockPos(2, 64, 0)
        );
        List<BlockState> states = List.of(
            Blocks.STONE.getDefaultState(),
            Blocks.STONE.getDefaultState(),
            Blocks.STONE.getDefaultState()
        );
        when(world.getBlockState(any())).thenReturn(Blocks.STONE.getDefaultState());

        BlockOperation.postProcessBlockChanges(world, positions, states);

        verify(lighting, times(positions.size())).checkBlock(any(BlockPos.class));
        // BlockOperation updates neighbors for pos and pos.down().
        verify(world, times(positions.size() * 2)).updateNeighbors(any(BlockPos.class), any());
        verify(world, times(positions.size())).updateComparators(any(BlockPos.class), any());
    }

    @Test
    void postProcessBlockChanges_largeOperation_usesColumnTopLightChecks() {
        ServerWorld world = mock(ServerWorld.class);
        LightingProvider lighting = mock(LightingProvider.class);
        when(world.getLightingProvider()).thenReturn(lighting);
        when(world.isChunkLoaded(anyLong())).thenReturn(true);

        // Build exactly threshold positions, all within 2 columns (x,z).
        List<BlockPos> positions = new ArrayList<>(BlockOperation.LARGE_POST_PROCESS_THRESHOLD);
        List<BlockState> states = new ArrayList<>(BlockOperation.LARGE_POST_PROCESS_THRESHOLD);
        for (int i = 0; i < BlockOperation.LARGE_POST_PROCESS_THRESHOLD; i++) {
            int x = (i % 2);
            int z = 0;
            int y = 60 + (i % 10);
            positions.add(new BlockPos(x, y, z));
            states.add(Blocks.STONE.getDefaultState());
        }

        BlockOperation.postProcessBlockChanges(world, positions, states);

        // Large path should not do per-block neighbor updates.
        verify(world, never()).updateNeighbors(any(BlockPos.class), any());
        verify(world, never()).updateComparators(any(BlockPos.class), any());

        // Expect 2 columns → 2 top positions + 2 top+up = 4 lighting checks.
        ArgumentCaptor<BlockPos> posCaptor = ArgumentCaptor.forClass(BlockPos.class);
        verify(lighting, times(4)).checkBlock(posCaptor.capture());

        // Verify the top Y per column was used (and the top+1 check).
        List<BlockPos> checked = posCaptor.getAllValues();
        // Our input generation yields top Y=68 for x=0 (even i only), and top Y=69 for x=1 (odd i only).
        long has00Top = checked.stream().filter(p -> p.equals(new BlockPos(0, 68, 0))).count();
        long has10Top = checked.stream().filter(p -> p.equals(new BlockPos(1, 69, 0))).count();
        long has00Up = checked.stream().filter(p -> p.equals(new BlockPos(0, 69, 0))).count();
        long has10Up = checked.stream().filter(p -> p.equals(new BlockPos(1, 70, 0))).count();
        assertEquals(1, has00Top);
        assertEquals(1, has10Top);
        assertEquals(1, has00Up);
        assertEquals(1, has10Up);
    }
}

