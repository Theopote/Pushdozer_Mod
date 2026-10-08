package com.pushdozer.operations;

import com.pushdozer.PushdozerTestBase;
import net.minecraft.block.Blocks;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import net.minecraft.world.chunk.light.LightingProvider;

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
import static org.mockito.Mockito.when;

class BlockOperationSchedulerIntegrationTest extends PushdozerTestBase {

    private final TerrainOperationScheduler scheduler = TerrainOperationScheduler.getInstance();

    @AfterEach
    void resetScheduler() {
        scheduler.resetForTests();
    }

    @Test
    void applyTerrainChanges_rejectsSecondOperationWhileFirstIsInFlight() {
        MinecraftServer server = mock(MinecraftServer.class);
        ServerWorld world = mock(ServerWorld.class);
        when(world.getServer()).thenReturn(server);
        when(world.isChunkLoaded(anyLong())).thenReturn(true);
        when(world.setBlockState(any(), any(), anyInt())).thenReturn(true);
        when(world.getRegistryKey()).thenReturn(World.OVERWORLD);
        when(world.getBottomY()).thenReturn(-64);
        when(world.getHeight()).thenReturn(384);
        when(world.getLightingProvider()).thenReturn(mock(LightingProvider.class));
        when(world.getBlockState(any())).thenReturn(Blocks.STONE.getDefaultState());

        List<Runnable> pendingTasks = new ArrayList<>();
        doAnswer(invocation -> {
            pendingTasks.add(invocation.getArgument(0));
            return null;
        }).when(server).execute(any(Runnable.class));

        List<BlockPos> positions = new ArrayList<>(1500);
        List<net.minecraft.block.BlockState> states = new ArrayList<>(1500);
        for (int i = 0; i < 1500; i++) {
            positions.add(new BlockPos(i % 16, 64, i / 16));
            states.add(Blocks.STONE.getDefaultState());
        }

        AtomicBoolean firstCompleted = new AtomicBoolean(false);
        assertTrue(BlockOperation.applyTerrainChanges(world, positions, states, () -> firstCompleted.set(true)));
        assertFalse(BlockOperation.applyTerrainChanges(world, positions, states, () -> {}));

        while (!firstCompleted.get() && !pendingTasks.isEmpty()) {
            List<Runnable> batch = new ArrayList<>(pendingTasks);
            pendingTasks.clear();
            batch.forEach(Runnable::run);
        }
        assertTrue(firstCompleted.get());
    }
}
