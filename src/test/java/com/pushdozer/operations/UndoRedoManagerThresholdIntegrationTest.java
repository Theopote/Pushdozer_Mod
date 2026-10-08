package com.pushdozer.operations;

import com.pushdozer.PushdozerTestBase;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class UndoRedoManagerThresholdIntegrationTest extends PushdozerTestBase {

    private final TerrainOperationScheduler scheduler = TerrainOperationScheduler.getInstance();

    @AfterEach
    void resetScheduler() {
        scheduler.resetForTests();
    }

    private static class CountingManager extends UndoRedoManager {
        final AtomicInteger blockUpdateSyncCalls = new AtomicInteger();

        @Override
        protected void syncBlockUpdatesToClient(ServerWorld serverWorld, ServerPlayerEntity serverPlayer,
                                                List<BlockPos> validPositions) {
            blockUpdateSyncCalls.incrementAndGet();
        }
    }

    private static ServerWorld mockServerWorld(MinecraftServer server) {
        ServerWorld world = mock(ServerWorld.class);
        when(world.getServer()).thenReturn(server);
        when(world.isChunkLoaded(anyLong())).thenReturn(true);
        when(world.setBlockState(any(), any(), anyInt())).thenReturn(true);
        when(world.getBottomY()).thenReturn(-64);
        when(world.getHeight()).thenReturn(384);
        when(world.getRegistryKey()).thenReturn(World.OVERWORLD);
        BlockState sharedState = Blocks.STONE.getDefaultState();
        when(world.getBlockState(any())).thenReturn(sharedState);
        when(world.getLightingProvider()).thenReturn(mock(net.minecraft.world.chunk.light.LightingProvider.class));
        return world;
    }

    private static UndoAction actionOfSize(int count) {
        List<BlockPos> positions = new ArrayList<>(count);
        List<BlockState> original = new ArrayList<>(count);
        List<BlockState> updated = new ArrayList<>(count);
        BlockState sharedState = Blocks.STONE.getDefaultState();
        for (int i = 0; i < count; i++) {
            positions.add(new BlockPos(i, 64, 0));
            original.add(sharedState);
            updated.add(sharedState);
        }
        return new UndoAction(UndoAction.ActionType.BREAK, World.OVERWORLD, positions, original, updated);
    }

    @Test
    void executeUndoRedoAction_4095Positions_usesBlockUpdateSyncPath() {
        CountingManager manager = new CountingManager();
        MinecraftServer server = mock(MinecraftServer.class);
        ServerWorld world = mockServerWorld(server);

        doAnswer(invocation -> {
            Runnable r = invocation.getArgument(0);
            r.run();
            return null;
        }).when(server).execute(any(Runnable.class));

        ServerPlayerEntity player = mock(ServerPlayerEntity.class);
        when(player.getUuid()).thenReturn(UUID.randomUUID());
        when(player.getName()).thenReturn(net.minecraft.text.Text.literal("test"));

        AtomicBoolean finished = new AtomicBoolean(false);
        manager.executeUndoRedoAction(actionOfSize(4095), player, world, true, ok -> finished.set(true));

        assertTrue(finished.get());
        assertEquals(1, manager.blockUpdateSyncCalls.get());
    }

    @Test
    void executeUndoRedoAction_4096Positions_usesBlockUpdateSyncPath() {
        CountingManager manager = new CountingManager();
        MinecraftServer server = mock(MinecraftServer.class);
        ServerWorld world = mockServerWorld(server);

        doAnswer(invocation -> {
            Runnable r = invocation.getArgument(0);
            r.run();
            return null;
        }).when(server).execute(any(Runnable.class));

        ServerPlayerEntity player = mock(ServerPlayerEntity.class);
        when(player.getUuid()).thenReturn(UUID.randomUUID());
        when(player.getName()).thenReturn(net.minecraft.text.Text.literal("test"));

        AtomicBoolean finished = new AtomicBoolean(false);
        manager.executeUndoRedoAction(actionOfSize(4096), player, world, true, ok -> finished.set(true));

        assertTrue(finished.get());
        assertEquals(1, manager.blockUpdateSyncCalls.get());
    }
}
