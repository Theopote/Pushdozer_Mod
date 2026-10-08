package com.pushdozer.items.handlers.planting.model;

import com.pushdozer.operations.AppliedChangeResult;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

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

    public void reconcileSimplePlants(AppliedChangeResult applied) {
        Set<BlockPos> plannedSimple = new HashSet<>(simplePlantPositions);
        for (int i = allPositions.size() - 1; i >= 0; i--) {
            if (plannedSimple.contains(allPositions.get(i))) {
                allPositions.remove(i);
                allOriginalStates.remove(i);
                allNewStates.remove(i);
            }
        }

        simplePlantPositions.clear();
        simplePlantNewStates.clear();
        simplePlantCount = 0;

        for (int i = 0; i < applied.positions().size(); i++) {
            BlockPos pos = applied.positions().get(i);
            BlockState original = applied.originalStates().get(i);
            BlockState newState = applied.appliedStates().get(i);
            allPositions.add(pos);
            allOriginalStates.add(original);
            allNewStates.add(newState);
            simplePlantPositions.add(pos);
            simplePlantNewStates.add(newState);
            simplePlantCount++;
        }
    }
}
