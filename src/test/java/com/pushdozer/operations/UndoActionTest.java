package com.pushdozer.operations;

import com.pushdozer.PushdozerTestBase;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class UndoActionTest extends PushdozerTestBase {

    @Test
    void isValid_requiresMatchingListSizes() {
        List<BlockPos> positions = List.of(BlockPos.ORIGIN);
        List<BlockState> oneState = List.of(mock(BlockState.class));
        List<BlockState> twoStates = List.of(mock(BlockState.class), mock(BlockState.class));

        assertTrue(new UndoAction(UndoAction.ActionType.BREAK, World.OVERWORLD, positions, oneState, oneState).isValid());
        assertFalse(new UndoAction(UndoAction.ActionType.BREAK, World.OVERWORLD, positions, oneState, twoStates).isValid());
        assertFalse(new UndoAction(UndoAction.ActionType.BREAK, World.OVERWORLD, null, oneState, oneState).isValid());
    }

    @Test
    void getExecutionPositions_includesBoundaryPositionsInOrder() {
        BlockPos core = new BlockPos(1, 2, 3);
        BlockPos boundary = new BlockPos(4, 5, 6);
        BlockState state = mock(BlockState.class);

        UndoAction action = new UndoAction(
            UndoAction.ActionType.PLACE,
            World.OVERWORLD,
            List.of(core),
            List.of(state),
            List.of(state),
            Set.of(boundary),
            List.of(state),
            List.of(state)
        );

        assertEquals(2, action.getExecutionPositions().size());
        assertEquals(boundary, action.getExecutionPositions().get(1));
        assertEquals(2, action.getTotalBlockCount());
    }

    @Test
    void getExecutionOriginalStates_concatenatesBoundaryStates() {
        BlockState coreState = mock(BlockState.class);
        BlockState boundaryState = mock(BlockState.class);

        UndoAction action = new UndoAction(
            UndoAction.ActionType.SMOOTH,
            World.OVERWORLD,
            List.of(BlockPos.ORIGIN),
            List.of(coreState),
            List.of(coreState),
            Set.of(new BlockPos(0, 1, 0)),
            List.of(boundaryState),
            List.of(boundaryState)
        );

        List<BlockState> allOriginal = action.getExecutionOriginalStates();
        assertEquals(2, allOriginal.size());
        assertTrue(allOriginal.contains(coreState));
        assertTrue(allOriginal.contains(boundaryState));
    }

    @Test
    void constructorWithoutBoundaryDefaultsToEmptyBoundaryData() {
        UndoAction action = new UndoAction(
            UndoAction.ActionType.BREAK,
            World.OVERWORLD,
            new ArrayList<>(),
            new ArrayList<>(),
            new ArrayList<>()
        );

        assertTrue(action.getBoundaryPositions().isEmpty());
        assertTrue(action.getBoundaryOriginalStates().isEmpty());
        assertTrue(action.getBoundaryNewStates().isEmpty());
    }

    @Test
    void orderedBoundarySet_preservesInsertionOrder() {
        Set<BlockPos> ordered = UndoAction.orderedBoundarySet(
            List.of(new BlockPos(1, 2, 3), new BlockPos(4, 5, 6))
        );
        assertEquals(List.of(new BlockPos(1, 2, 3), new BlockPos(4, 5, 6)), List.copyOf(ordered));
    }
}
