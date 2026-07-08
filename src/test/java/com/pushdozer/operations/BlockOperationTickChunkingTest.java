package com.pushdozer.operations;

import com.pushdozer.PushdozerTestBase;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BlockOperationTickChunkingTest extends PushdozerTestBase {

    @Test
    void batchSetBlockStates_usesServerExecuteOnceWhenJustOverPerTickLimit() {
        int totalBlocks = BlockOperation.BLOCKS_PER_TICK + 1; // 1025
        List<BlockPos> positions = new ArrayList<>(totalBlocks);
        List<BlockState> states = new ArrayList<>(totalBlocks);
        BlockState stone = Blocks.STONE.getDefaultState();
        for (int i = 0; i < totalBlocks; i++) {
            positions.add(new BlockPos(i, 64, 0));
            states.add(stone);
        }

        ServerWorld world = mock(ServerWorld.class);
        MinecraftServer server = mock(MinecraftServer.class);
        when(world.getServer()).thenReturn(server);
        when(world.isChunkLoaded(anyLong())).thenReturn(true);
        when(world.setBlockState(any(), any(), anyInt())).thenReturn(true);
        doAnswer(invocation -> {
            Runnable task = invocation.getArgument(0);
            task.run();
            return null;
        }).when(server).execute(any());

        AtomicBoolean completed = new AtomicBoolean(false);
        boolean sync = BlockOperation.batchSetBlockStates(
            positions, states, world, BlockOperation.BULK_WRITE_FLAGS, () -> completed.set(true)
        );

        assertFalse(sync);
        assertTrue(completed.get());
        verify(server, times(1)).execute(any());
        verify(world, times(totalBlocks)).setBlockState(any(), any(), anyInt());
    }

    @Test
    void batchSetBlockStates_usesServerExecuteTwiceWhenOverTwoTicks() {
        int totalBlocks = (2 * BlockOperation.BLOCKS_PER_TICK) + 1; // 2049
        List<BlockPos> positions = new ArrayList<>(totalBlocks);
        List<BlockState> states = new ArrayList<>(totalBlocks);
        BlockState stone = Blocks.STONE.getDefaultState();
        for (int i = 0; i < totalBlocks; i++) {
            positions.add(new BlockPos(i, 64, 0));
            states.add(stone);
        }

        ServerWorld world = mock(ServerWorld.class);
        MinecraftServer server = mock(MinecraftServer.class);
        when(world.getServer()).thenReturn(server);
        when(world.isChunkLoaded(anyLong())).thenReturn(true);
        when(world.setBlockState(any(), any(), anyInt())).thenReturn(true);
        doAnswer(invocation -> {
            Runnable task = invocation.getArgument(0);
            task.run();
            return null;
        }).when(server).execute(any());

        AtomicBoolean completed = new AtomicBoolean(false);
        boolean sync = BlockOperation.batchSetBlockStates(
            positions, states, world, BlockOperation.BULK_WRITE_FLAGS, () -> completed.set(true)
        );

        assertFalse(sync);
        assertTrue(completed.get());
        verify(server, times(2)).execute(any());
        verify(world, times(totalBlocks)).setBlockState(any(), any(), anyInt());
    }
}

