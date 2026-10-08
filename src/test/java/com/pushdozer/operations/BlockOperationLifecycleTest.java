package com.pushdozer.operations;

import com.pushdozer.PushdozerTestBase;
import net.minecraft.block.Blocks;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;
import net.minecraft.world.chunk.light.LightingProvider;
import org.junit.jupiter.api.AfterEach;
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
import static org.mockito.Mockito.when;

class BlockOperationLifecycleTest extends PushdozerTestBase {

    private final TerrainOperationScheduler scheduler = TerrainOperationScheduler.getInstance();

    @AfterEach
    void resetScheduler() {
        scheduler.resetForTests();
    }

    @Test
    void applyTerrainChanges_keepsLockUntilLargePostProcessCompletes() {
        DeferredServerWorld deferred = new DeferredServerWorld();
        when(deferred.world.getBlockState(any())).thenReturn(Blocks.STONE.getDefaultState());
        when(deferred.world.getLightingProvider()).thenReturn(mock(LightingProvider.class));

        List<BlockPos> positions = new ArrayList<>(BlockOperation.LARGE_POST_PROCESS_THRESHOLD);
        List<net.minecraft.block.BlockState> states = new ArrayList<>(BlockOperation.LARGE_POST_PROCESS_THRESHOLD);
        for (int i = 0; i < BlockOperation.LARGE_POST_PROCESS_THRESHOLD; i++) {
            positions.add(new BlockPos(i % 16, 64, i / 16));
            states.add(Blocks.STONE.getDefaultState());
        }

        ChunkPos targetChunk = new ChunkPos(positions.getFirst());
        AtomicBoolean operationFinished = new AtomicBoolean(false);

        assertTrue(BlockOperation.applyTerrainChanges(deferred.world, positions, states, () -> operationFinished.set(true)));
        assertTrue(scheduler.isChunkBusy(deferred.world, targetChunk));
        assertFalse(operationFinished.get());

        deferred.runAllPendingTasks();

        assertTrue(operationFinished.get());
        assertFalse(scheduler.isChunkBusy(deferred.world, targetChunk));
    }

    @Test
    void applyTerrainChanges_releasesLockWhenCallbackThrows() {
        DeferredServerWorld deferred = new DeferredServerWorld();
        when(deferred.world.getBlockState(any())).thenReturn(Blocks.STONE.getDefaultState());
        when(deferred.world.getLightingProvider()).thenReturn(mock(LightingProvider.class));

        BlockPos pos = new BlockPos(0, 64, 0);
        ChunkPos targetChunk = new ChunkPos(pos);

        try {
            assertTrue(BlockOperation.applyTerrainChanges(
                deferred.world,
                List.of(pos),
                List.of(Blocks.DIRT.getDefaultState()),
                applied -> {
                    throw new RuntimeException("callback failure");
                }
            ));
            deferred.runAllPendingTasks();
        } catch (RuntimeException ignored) {
            // finishOperation must still release the lock even when the callback throws
        }

        assertFalse(scheduler.isChunkBusy(deferred.world, targetChunk));
    }

    @Test
    void twoPhaseTerrainOperation_reusesSameLockForVegetationPhase() {
        DeferredServerWorld deferred = new DeferredServerWorld();
        when(deferred.world.getBlockState(any())).thenReturn(Blocks.STONE.getDefaultState());
        when(deferred.world.getLightingProvider()).thenReturn(mock(LightingProvider.class));

        BlockPos terrainPos = new BlockPos(0, 64, 0);
        BlockPos vegetationPos = new BlockPos(1, 64, 0);
        ChunkPos sharedChunk = new ChunkPos(terrainPos);

        var token = BlockOperation.beginTerrainOperation(deferred.world, List.of(terrainPos));
        assertTrue(token.isPresent());

        AtomicBoolean completed = new AtomicBoolean(false);
        BlockOperation.applyTerrainPhase(token.get(), List.of(terrainPos), List.of(Blocks.DIRT.getDefaultState()), () -> {
            assertTrue(scheduler.isChunkBusy(deferred.world, sharedChunk));
            assertTrue(BlockOperation.extendTerrainOperation(token.get(), List.of(vegetationPos)));
            BlockOperation.applyTerrainPhase(token.get(), List.of(vegetationPos), List.of(Blocks.AIR.getDefaultState()), () -> {
                completed.set(true);
                BlockOperation.releaseTerrainOperation(token.get());
            });
        });

        deferred.runAllPendingTasks();

        assertTrue(completed.get());
        assertFalse(scheduler.isChunkBusy(deferred.world, sharedChunk));
    }

    private static final class DeferredServerWorld {
        private final ServerWorld world = mock(ServerWorld.class);
        private final List<Runnable> pendingTasks = new ArrayList<>();

        private DeferredServerWorld() {
            MinecraftServer server = mock(MinecraftServer.class);
            when(world.getServer()).thenReturn(server);
            when(world.isChunkLoaded(anyLong())).thenReturn(true);
            when(world.setBlockState(any(), any(), anyInt())).thenReturn(true);
            when(world.getRegistryKey()).thenReturn(World.OVERWORLD);
            when(world.getBottomY()).thenReturn(-64);
            when(world.getHeight()).thenReturn(384);
            doAnswer(invocation -> {
                pendingTasks.add(invocation.getArgument(0));
                return null;
            }).when(server).execute(any(Runnable.class));
        }

        private void runAllPendingTasks() {
            while (!pendingTasks.isEmpty()) {
                List<Runnable> batch = new ArrayList<>(pendingTasks);
                pendingTasks.clear();
                batch.forEach(Runnable::run);
            }
        }
    }
}
