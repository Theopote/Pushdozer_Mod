package com.pushdozer.operations;

import com.pushdozer.PushdozerTestBase;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.enums.DoubleBlockHalf;
import net.minecraft.state.property.Properties;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BlockOperationTest extends PushdozerTestBase {

    @Test
    void batchSetBlockStates_rejectsMismatchedListsAndStillCompletes() {
        AtomicBoolean completed = new AtomicBoolean(false);
        ServerWorld world = mock(ServerWorld.class);

        boolean sync = BlockOperation.batchSetBlockStates(
            List.of(BlockPos.ORIGIN),
            List.of(),
            world,
            BlockOperation.BULK_WRITE_FLAGS,
            () -> completed.set(true)
        );

        assertTrue(sync);
        assertTrue(completed.get());
    }

    @Test
    void batchSetBlockStates_appliesSmallOperationsSynchronously() {
        List<BlockPos> positions = List.of(
            new BlockPos(0, 64, 0),
            new BlockPos(1, 64, 0),
            new BlockPos(2, 64, 0)
        );
        BlockState stone = Blocks.STONE.getDefaultState();
        List<BlockState> states = List.of(stone, stone, stone);
        ServerWorld world = mock(ServerWorld.class);
        when(world.isChunkLoaded(anyLong())).thenReturn(true);
        when(world.getBlockState(any())).thenReturn(Blocks.DIRT.getDefaultState());
        when(world.setBlockState(any(), any(), anyInt())).thenReturn(true);

        AtomicBoolean completed = new AtomicBoolean(false);
        boolean sync = BlockOperation.batchSetBlockStates(
            positions, states, world, BlockOperation.BULK_WRITE_FLAGS, () -> completed.set(true)
        );

        assertTrue(sync);
        assertTrue(completed.get());
        verify(world, times(3)).setBlockState(any(), any(), anyInt());
    }

    @Test
    void batchSetBlockStates_schedulesLargeOperationsAcrossTicks() {
        int totalBlocks = BlockOperation.SYNC_BLOCK_LIMIT + 1;
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
        when(world.getBlockState(any())).thenReturn(Blocks.DIRT.getDefaultState());
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
        verify(world, times(totalBlocks)).setBlockState(any(), any(), anyInt());
    }

    @Test
    void batchSetBlockStates_recordsOnlySuccessfulWrites() {
        BlockPos successPos = new BlockPos(0, 64, 0);
        BlockPos failedPos = new BlockPos(1, 64, 0);
        BlockState stone = Blocks.STONE.getDefaultState();
        ServerWorld world = mock(ServerWorld.class);
        when(world.isChunkLoaded(anyLong())).thenReturn(true);
        when(world.setBlockState(eq(successPos), any(), anyInt())).thenReturn(true);
        when(world.setBlockState(eq(failedPos), any(), anyInt())).thenReturn(false);
        when(world.getBlockState(any())).thenReturn(Blocks.DIRT.getDefaultState());

        AtomicReference<AppliedChangeResult> captured = new AtomicReference<>();
        BlockOperation.batchSetBlockStates(
            List.of(successPos, failedPos),
            List.of(stone, stone),
            world,
            BlockOperation.BULK_WRITE_FLAGS,
            captured::set
        );

        assertEquals(1, captured.get().positions().size());
        assertEquals(successPos, captured.get().positions().getFirst());
    }

    @Test
    void batchSetBlockStates_skipsTallPlantPairWhenUpperIsBlocked() {
        BlockPos lower = new BlockPos(0, 64, 0);
        BlockPos upper = lower.up();
        BlockState lowerPlant = Blocks.SUNFLOWER.getDefaultState().with(Properties.DOUBLE_BLOCK_HALF, DoubleBlockHalf.LOWER);
        BlockState upperPlant = Blocks.SUNFLOWER.getDefaultState().with(Properties.DOUBLE_BLOCK_HALF, DoubleBlockHalf.UPPER);
        ServerWorld world = mock(ServerWorld.class);
        when(world.isChunkLoaded(anyLong())).thenReturn(true);
        when(world.getBlockState(lower)).thenReturn(Blocks.AIR.getDefaultState());
        when(world.getBlockState(lower.down())).thenReturn(Blocks.GRASS_BLOCK.getDefaultState());
        when(world.getBlockState(upper)).thenReturn(Blocks.STONE.getDefaultState());

        AtomicReference<AppliedChangeResult> captured = new AtomicReference<>();
        BlockOperation.batchSetBlockStates(
            List.of(lower, upper),
            List.of(lowerPlant, upperPlant),
            world,
            BlockOperation.BULK_WRITE_FLAGS,
            List.of(new int[]{0, 1}),
            captured::set
        );

        assertTrue(captured.get().isEmpty());
        verify(world, times(0)).setBlockState(any(), any(), anyInt());
    }

    @Test
    void bulkWriteFlags_containsExpectedBits() {
        int flags = BlockOperation.BULK_WRITE_FLAGS;
        assertTrue((flags & net.minecraft.block.Block.NOTIFY_LISTENERS) != 0);
        assertTrue((flags & net.minecraft.block.Block.FORCE_STATE) != 0);
        assertTrue((flags & net.minecraft.block.Block.SKIP_DROPS) != 0);
    }
}
