package com.pushdozer.operations;

import com.pushdozer.PushdozerTestBase;
import net.minecraft.block.BlockState;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.chunk.light.LightingProvider;
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

    private static class CountingManager extends UndoRedoManager {
        final AtomicInteger smallCalls = new AtomicInteger();
        final AtomicInteger largeCalls = new AtomicInteger();

        @Override
        protected void syncSmallOperation(ServerWorld serverWorld, ServerPlayerEntity serverPlayer, List<BlockPos> validPositions) {
            smallCalls.incrementAndGet();
        }

        @Override
        protected void syncLargeOperation(ServerWorld serverWorld, ServerPlayerEntity serverPlayer, List<BlockPos> validPositions,
                                          LightingProvider lightProvider) {
            largeCalls.incrementAndGet();
        }
    }

    private static ServerWorld mockServerWorld(MinecraftServer server) {
        ServerWorld world = mock(ServerWorld.class);
        when(world.getServer()).thenReturn(server);
        when(world.isChunkLoaded(anyLong())).thenReturn(true);
        when(world.setBlockState(any(), any(), anyInt())).thenReturn(true);
        when(world.getBottomY()).thenReturn(-64);
        when(world.getHeight()).thenReturn(384);
        when(world.getLightingProvider()).thenReturn(mock(LightingProvider.class));
        when(world.getBlockState(any())).thenReturn(mock(BlockState.class));
        return world;
    }

    private static UndoAction actionOfSize(int count) {
        List<BlockPos> positions = new ArrayList<>(count);
        List<BlockState> original = new ArrayList<>(count);
        List<BlockState> updated = new ArrayList<>(count);
        BlockState state = mock(BlockState.class);
        for (int i = 0; i < count; i++) {
            // Spread across a few chunks, but all will be treated as loaded in mocks.
            positions.add(new BlockPos(i, 64, 0));
            original.add(state);
            updated.add(state);
        }
        return new UndoAction(UndoAction.ActionType.BREAK, positions, original, updated);
    }

    @Test
    void executeUndoRedoAction_4095Positions_usesSmallSyncPath() {
        CountingManager manager = new CountingManager();
        MinecraftServer server = mock(MinecraftServer.class);
        ServerWorld world = mockServerWorld(server);

        // Run scheduled tasks immediately so the method completes in test.
        doAnswer(invocation -> {
            Runnable r = invocation.getArgument(0);
            r.run();
            return null;
        }).when(server).execute(any(Runnable.class));

        UUID playerId = UUID.randomUUID();
        ServerPlayerEntity player = mock(ServerPlayerEntity.class);
        when(player.getUuid()).thenReturn(playerId);
        when(player.getName()).thenReturn(net.minecraft.text.Text.literal("test"));

        AtomicBoolean finished = new AtomicBoolean(false);
        manager.executeUndoRedoAction(actionOfSize(4095), player, (World) world, true, ok -> finished.set(true));

        assertTrue(finished.get());
        assertEquals(1, manager.smallCalls.get());
        assertEquals(0, manager.largeCalls.get());
    }

    @Test
    void executeUndoRedoAction_4096Positions_usesLargeSyncPath() {
        CountingManager manager = new CountingManager();
        MinecraftServer server = mock(MinecraftServer.class);
        ServerWorld world = mockServerWorld(server);

        doAnswer(invocation -> {
            Runnable r = invocation.getArgument(0);
            r.run();
            return null;
        }).when(server).execute(any(Runnable.class));

        UUID playerId = UUID.randomUUID();
        ServerPlayerEntity player = mock(ServerPlayerEntity.class);
        when(player.getUuid()).thenReturn(playerId);
        when(player.getName()).thenReturn(net.minecraft.text.Text.literal("test"));

        AtomicBoolean finished = new AtomicBoolean(false);
        manager.executeUndoRedoAction(actionOfSize(4096), player, (World) world, true, ok -> finished.set(true));

        assertTrue(finished.get());
        assertEquals(0, manager.smallCalls.get());
        assertEquals(1, manager.largeCalls.get());
    }
}

