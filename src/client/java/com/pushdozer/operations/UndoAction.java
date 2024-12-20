package com.pushdozer.operations;

import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;

import java.util.List;

public class UndoAction {
    public enum ActionType {
        PLACE,
        DESTROY,
        SMOOTH  // 新增平滑操作类型
    }

    private final ActionType type;
    private final List<BlockPos> positions;
    private final List<BlockState> originalStates;
    private final List<BlockState> newStates;

    public UndoAction(ActionType type, List<BlockPos> positions, List<BlockState> originalStates, List<BlockState> newStates) {
        this.type = type;
        this.positions = positions;
        this.originalStates = originalStates;
        this.newStates = newStates;
    }

    public ActionType getType() {
        return type;
    }

    public List<BlockPos> getPositions() {
        return positions;
    }

    public List<BlockState> getOriginalStates() {
        return originalStates;
    }

    public List<BlockState> getNewStates() {
        return newStates;
    }
}
