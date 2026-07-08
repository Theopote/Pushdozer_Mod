package com.pushdozer.items.handlers.planting.model;

import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.List;

public final class TreeGenerationResult {
    public final List<BlockPos> affectedPositions = new ArrayList<>();
    public final List<BlockState> originalStates = new ArrayList<>();
    public final List<BlockState> newStates = new ArrayList<>();

    public void addChange(BlockPos pos, BlockState original, BlockState newState) {
        affectedPositions.add(pos);
        originalStates.add(original);
        newStates.add(newState);
    }

    public boolean isEmpty() {
        return affectedPositions.isEmpty();
    }
}
