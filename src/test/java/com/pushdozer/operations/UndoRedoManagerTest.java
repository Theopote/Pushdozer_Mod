package com.pushdozer.operations;

import com.pushdozer.PushdozerTestBase;
import com.pushdozer.test.TestFixtures;
import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

class UndoRedoManagerTest extends PushdozerTestBase {

    private UndoRedoManager manager;
    private PlayerEntity player;
    private UUID playerId;

    @BeforeEach
    void setUp() {
        manager = new UndoRedoManager();
        playerId = UUID.randomUUID();
        player = TestFixtures.mockPlayer(playerId);
    }

    @Test
    void pushUndoAction_capsStackAtThirtyEntries() {
        for (int i = 0; i < 31; i++) {
            manager.pushUndoAction(player, sampleAction(i));
        }

        assertEquals(30, manager.getUndoStackSize(player));
    }

    @Test
    void pushUndoAction_accumulatesUndoStack() {
        UndoAction first = sampleAction(1);
        UndoAction second = sampleAction(2);

        manager.pushUndoAction(player, first);
        manager.pushUndoAction(player, second);

        assertEquals(2, manager.getUndoStackSize(player));
    }

    private static UndoAction sampleAction(int seed) {
        BlockPos pos = new BlockPos(seed, 64, seed);
        BlockState state = mock(BlockState.class);
        return new UndoAction(
            UndoAction.ActionType.BREAK,
            List.of(pos),
            List.of(state),
            List.of(state)
        );
    }
}
