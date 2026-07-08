package com.pushdozer.operations;

import com.pushdozer.PushdozerTestBase;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.chunk.light.LightingProvider;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UndoRedoManagerLargeSyncBehaviorTest extends PushdozerTestBase {

    private static class CountingUndoRedoManager extends UndoRedoManager {
        final AtomicInteger sendChunksCalls = new AtomicInteger();

        @Override
        protected void sendChunks(ServerWorld serverWorld, ServerPlayerEntity serverPlayer, Set<ChunkPos> chunks,
                                  LightingProvider lightProvider, String reason) {
            sendChunksCalls.incrementAndGet();
        }
    }

    @Test
    void syncLargeOperation_sendsImmediatelyAndSchedulesDelayedResync() {
        CountingUndoRedoManager manager = new CountingUndoRedoManager();

        ServerWorld world = mock(ServerWorld.class);
        MinecraftServer server = mock(MinecraftServer.class);
        when(world.getServer()).thenReturn(server);
        LightingProvider lighting = mock(LightingProvider.class);

        // Run scheduled tasks immediately in test.
        doAnswer(invocation -> {
            Runnable r = invocation.getArgument(0);
            r.run();
            return null;
        }).when(server).execute(any(Runnable.class));

        ServerPlayerEntity player = mock(ServerPlayerEntity.class);

        List<BlockPos> positions = List.of(
            new BlockPos(0, 64, 0),
            new BlockPos(32, 64, 0) // different chunk in X (chunk 2)
        );

        manager.syncLargeOperation(world, player, positions, lighting);

        // One immediate sendChunks + one delayed sendChunks via server.execute.
        assertEquals(2, manager.sendChunksCalls.get());
        verify(server, times(1)).execute(any(Runnable.class));
    }
}

