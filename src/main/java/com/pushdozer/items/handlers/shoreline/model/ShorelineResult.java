package com.pushdozer.items.handlers.shoreline.model;

import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;

import java.util.List;

public final class ShorelineResult {
    public final List<BlockPos> affectedPositions;
    public final List<BlockState> originalStates;
    public final List<BlockState> newStates;
    public final List<BlockPos> vegetationPositions;
    public final int processedCount;
    public final int iterationCount;

    public ShorelineResult(List<BlockPos> affectedPositions, List<BlockState> originalStates,
                           List<BlockState> newStates, List<BlockPos> vegetationPositions,
                           int processedCount, int iterationCount) {
        this.affectedPositions = affectedPositions;
        this.originalStates = originalStates;
        this.newStates = newStates;
        this.vegetationPositions = vegetationPositions;
        this.processedCount = processedCount;
        this.iterationCount = iterationCount;
    }
}
