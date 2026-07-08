package com.pushdozer.items.handlers.planting.model;

import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.List;

public final class BatchPlantingResult {
    private final List<BlockPos> allPositions = new ArrayList<>();
    private final List<BlockState> allOriginalStates = new ArrayList<>();
    private final List<BlockState> allNewStates = new ArrayList<>();
    private final List<BlockPos> simplePlantPositions = new ArrayList<>();
    private final List<BlockState> simplePlantNewStates = new ArrayList<>();
    private int simplePlantCount = 0;
    private int treeCount = 0;

    public void addSimplePlant(BlockPos pos, BlockState original, BlockState newState) {
        allPositions.add(pos);
        allOriginalStates.add(original);
        allNewStates.add(newState);
        simplePlantPositions.add(pos);
        simplePlantNewStates.add(newState);
        simplePlantCount++;
    }

    public void addTreeBlock(BlockPos pos, BlockState original, BlockState newState) {
        allPositions.add(pos);
        allOriginalStates.add(original);
        allNewStates.add(newState);
    }

    public void incrementTreeCount() {
        treeCount++;
    }

    public List<BlockPos> getAllPositions() {
        return allPositions;
    }

    public List<BlockState> getAllOriginalStates() {
        return allOriginalStates;
    }

    public List<BlockState> getAllNewStates() {
        return allNewStates;
    }

    public boolean hasSimplePlants() {
        return !simplePlantPositions.isEmpty();
    }

    public List<BlockPos> getSimplePlantPositions() {
        return simplePlantPositions;
    }

    public List<BlockState> getSimplePlantNewStates() {
        return simplePlantNewStates;
    }

    public boolean isEmpty() {
        return allPositions.isEmpty();
    }

    public int getTotalCount() {
        return simplePlantCount + treeCount;
    }

    public int getSimplePlantCount() {
        return simplePlantCount;
    }

    public int getTreeCount() {
        return treeCount;
    }
}
