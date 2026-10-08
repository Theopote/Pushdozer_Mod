package com.pushdozer.operations;

import com.pushdozer.PushdozerTestBase;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.chunk.light.LightingProvider;
import net.minecraft.server.MinecraftServer;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
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
        when(world.getBottomY()).thenReturn(-64);
        when(world.getHeight()).thenReturn(384);

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
        verify(world, atLeast(positions.size())).updateNeighbors(any(BlockPos.class), any());
        verify(world, atLeast(positions.size())).updateComparators(any(BlockPos.class), any());
    }

    private static ServerWorld mockWorldWithImmediateScheduling() {
        ServerWorld world = mock(ServerWorld.class);
        MinecraftServer server = mock(MinecraftServer.class);
        when(world.getServer()).thenReturn(server);
        doAnswer(invocation -> {
            invocation.getArgument(0, Runnable.class).run();
            return null;
        }).when(server).execute(any(Runnable.class));
        when(world.isChunkLoaded(anyLong())).thenReturn(true);
        when(world.getBottomY()).thenReturn(-64);
        when(world.getHeight()).thenReturn(384);
        when(world.getBlockState(any())).thenReturn(Blocks.STONE.getDefaultState());
        return world;
    }

    @Test
    void postProcessBlockChanges_largeOperation_relightsEveryChangedBlock() {
        ServerWorld world = mockWorldWithImmediateScheduling();
        LightingProvider lighting = mock(LightingProvider.class);
        when(world.getLightingProvider()).thenReturn(lighting);

        List<BlockPos> positions = new ArrayList<>(BlockOperation.LARGE_POST_PROCESS_THRESHOLD);
        List<BlockState> states = new ArrayList<>(BlockOperation.LARGE_POST_PROCESS_THRESHOLD);
        for (int i = 0; i < BlockOperation.LARGE_POST_PROCESS_THRESHOLD; i++) {
            int x = i % 2;
            int y = 60 + (i % 10);
            positions.add(new BlockPos(x, y, 0));
            states.add(Blocks.STONE.getDefaultState());
        }

        BlockOperation.postProcessBlockChanges(world, positions, states);

        verify(lighting, times(BlockOperation.LARGE_POST_PROCESS_THRESHOLD)).checkBlock(any(BlockPos.class));
        verify(world, atLeast(1)).updateNeighbors(any(BlockPos.class), any());
    }

    @Test
    void postProcessBlockChanges_largeOperation_checksUndergroundChangesNotOnlyColumnTop() {
        ServerWorld world = mockWorldWithImmediateScheduling();
        LightingProvider lighting = mock(LightingProvider.class);
        when(world.getLightingProvider()).thenReturn(lighting);

        List<BlockPos> positions = new ArrayList<>(BlockOperation.LARGE_POST_PROCESS_THRESHOLD);
        List<BlockState> states = new ArrayList<>(BlockOperation.LARGE_POST_PROCESS_THRESHOLD);
        positions.add(new BlockPos(0, 60, 0));
        states.add(Blocks.AIR.getDefaultState());
        for (int i = 1; i < BlockOperation.LARGE_POST_PROCESS_THRESHOLD; i++) {
            positions.add(new BlockPos(0, 100, 0));
            states.add(Blocks.STONE.getDefaultState());
        }

        BlockOperation.postProcessBlockChanges(world, positions, states);

        ArgumentCaptor<BlockPos> posCaptor = ArgumentCaptor.forClass(BlockPos.class);
        verify(lighting, times(BlockOperation.LARGE_POST_PROCESS_THRESHOLD)).checkBlock(posCaptor.capture());
        assertTrue(posCaptor.getAllValues().contains(new BlockPos(0, 60, 0)),
            "Underground changed blocks must be relit, not only column tops");
    }
}
