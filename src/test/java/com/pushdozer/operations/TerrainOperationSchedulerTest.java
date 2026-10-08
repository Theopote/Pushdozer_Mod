package com.pushdozer.operations;

import com.pushdozer.PushdozerTestBase;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TerrainOperationSchedulerTest extends PushdozerTestBase {

    private final TerrainOperationScheduler scheduler = TerrainOperationScheduler.getInstance();

    @AfterEach
    void resetScheduler() {
        scheduler.resetForTests();
    }

    @Test
    void tryAcquire_allowsNonOverlappingOperations() {
        ServerWorld world = mockWorld();
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();

        assertTrue(scheduler.tryAcquire(world, first, List.of(new BlockPos(0, 64, 0))));
        assertTrue(scheduler.tryAcquire(world, second, List.of(new BlockPos(32, 64, 0))));

        scheduler.release(world, first);
        scheduler.release(world, second);
    }

    @Test
    void tryAcquire_rejectsOverlappingChunkFromDifferentOperation() {
        ServerWorld world = mockWorld();
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        BlockPos pos = new BlockPos(8, 64, 8);

        assertTrue(scheduler.tryAcquire(world, first, List.of(pos)));
        assertFalse(scheduler.tryAcquire(world, second, List.of(pos.add(1, 0, 0))));

        scheduler.release(world, first);
        assertTrue(scheduler.tryAcquire(world, second, List.of(pos.add(1, 0, 0))));
        scheduler.release(world, second);
    }

    @Test
    void release_allowsReacquireAfterCompletion() {
        ServerWorld world = mockWorld();
        UUID operationId = UUID.randomUUID();
        BlockPos pos = new BlockPos(0, 64, 0);

        assertTrue(scheduler.tryAcquire(world, operationId, List.of(pos)));
        scheduler.release(world, operationId);
        assertFalse(scheduler.isChunkBusy(world, new ChunkPos(pos)));
        assertTrue(scheduler.tryAcquire(world, UUID.randomUUID(), List.of(pos)));
    }

    private static ServerWorld mockWorld() {
        ServerWorld world = mock(ServerWorld.class);
        when(world.getRegistryKey()).thenReturn(World.OVERWORLD);
        return world;
    }
}
