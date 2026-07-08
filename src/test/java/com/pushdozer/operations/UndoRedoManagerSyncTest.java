package com.pushdozer.operations;

import com.pushdozer.PushdozerTestBase;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.chunk.light.LightingProvider;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class UndoRedoManagerSyncTest extends PushdozerTestBase {

    private static class TestableUndoRedoManager extends UndoRedoManager {
        final AtomicInteger smallSyncCalls = new AtomicInteger();
        final AtomicInteger largeSyncCalls = new AtomicInteger();
        final AtomicInteger sendChunksCalls = new AtomicInteger();

        @Override
        protected void syncSmallOperation(ServerWorld serverWorld, ServerPlayerEntity serverPlayer, List<BlockPos> validPositions) {
            smallSyncCalls.incrementAndGet();
        }

        @Override
        protected void syncLargeOperation(ServerWorld serverWorld, ServerPlayerEntity serverPlayer, List<BlockPos> validPositions,
                                          LightingProvider lightProvider) {
            largeSyncCalls.incrementAndGet();
            // Call sendChunks once to emulate fast sync, but skip ChunkData packet construction.
            sendChunksCalls.incrementAndGet();
        }
    }

    @Test
    void syncUndoChangesToClient_usesSmallPath_whenBelowThreshold() {
        TestableUndoRedoManager manager = new TestableUndoRedoManager();
        ServerWorld world = mock(ServerWorld.class);
        when(world.getLightingProvider()).thenReturn(mock(LightingProvider.class));

        PlayerEntity player = mock(ServerPlayerEntity.class);

        List<BlockPos> positions = List.of(
            new BlockPos(0, 64, 0),
            new BlockPos(1, 64, 0)
        );

        manager.syncUndoChangesToClient(world, player, positions, false, true);

        assertEquals(1, manager.smallSyncCalls.get());
        assertEquals(0, manager.largeSyncCalls.get());
    }

    @Test
    void syncUndoChangesToClient_usesLargePath_whenAtOrAboveThreshold() {
        TestableUndoRedoManager manager = new TestableUndoRedoManager();
        ServerWorld world = mock(ServerWorld.class);
        when(world.getLightingProvider()).thenReturn(mock(LightingProvider.class));
        when(world.getServer()).thenReturn(mock(MinecraftServer.class));

        PlayerEntity player = mock(ServerPlayerEntity.class);

        // positions list doesn't need to be huge here because we pass isLarge=true explicitly
        List<BlockPos> positions = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            positions.add(new BlockPos(i, 64, 0));
        }

        manager.syncUndoChangesToClient(world, player, positions, true, true);

        assertEquals(0, manager.smallSyncCalls.get());
        assertEquals(1, manager.largeSyncCalls.get());
        assertEquals(1, manager.sendChunksCalls.get());
    }
}

