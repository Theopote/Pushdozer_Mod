package com.pushdozer.operations;

import com.pushdozer.PushdozerTestBase;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

class UndoRedoManagerSyncTest extends PushdozerTestBase {

    private static class TestableUndoRedoManager extends UndoRedoManager {
        final AtomicInteger blockUpdateSyncCalls = new AtomicInteger();

        @Override
        protected void syncBlockUpdatesToClient(ServerWorld serverWorld, ServerPlayerEntity serverPlayer,
                                                List<BlockPos> validPositions) {
            blockUpdateSyncCalls.incrementAndGet();
        }
    }

    @Test
    void syncUndoChangesToClient_alwaysUsesBlockUpdatePath() {
        TestableUndoRedoManager manager = new TestableUndoRedoManager();
        ServerWorld world = mock(ServerWorld.class);
        PlayerEntity player = mock(ServerPlayerEntity.class);

        List<BlockPos> positions = List.of(
            new BlockPos(0, 64, 0),
            new BlockPos(1, 64, 0)
        );

        manager.syncUndoChangesToClient(world, player, positions, true);
        assertEquals(1, manager.blockUpdateSyncCalls.get());
    }

    @Test
    void syncUndoChangesToClient_usesSamePathForLargeOperations() {
        TestableUndoRedoManager manager = new TestableUndoRedoManager();
        ServerWorld world = mock(ServerWorld.class);
        PlayerEntity player = mock(ServerPlayerEntity.class);

        List<BlockPos> positions = new ArrayList<>();
        for (int i = 0; i < 5000; i++) {
            positions.add(new BlockPos(i, 64, 0));
        }

        manager.syncUndoChangesToClient(world, player, positions, true);
        assertEquals(1, manager.blockUpdateSyncCalls.get());
    }
}
