package com.pushdozer.operations;

import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.List;

/**
 * Records block writes that actually succeeded on the server.
 */
public record AppliedChangeResult(
    List<BlockPos> positions,
    List<BlockState> originalStates,
    List<BlockState> appliedStates
) {
    public static AppliedChangeResult empty() {
        return new AppliedChangeResult(List.of(), List.of(), List.of());
    }

    public boolean isEmpty() {
        return positions.isEmpty();
    }

    public AppliedChangeResult mergedWith(AppliedChangeResult other) {
        if (other.isEmpty()) {
            return this;
        }
        if (isEmpty()) {
            return other;
        }
        List<BlockPos> mergedPositions = new ArrayList<>(positions.size() + other.positions.size());
        List<BlockState> mergedOriginals = new ArrayList<>(originalStates.size() + other.originalStates.size());
        List<BlockState> mergedApplied = new ArrayList<>(appliedStates.size() + other.appliedStates.size());
        mergedPositions.addAll(positions);
        mergedOriginals.addAll(originalStates);
        mergedApplied.addAll(appliedStates);
        mergedPositions.addAll(other.positions);
        mergedOriginals.addAll(other.originalStates);
        mergedApplied.addAll(other.appliedStates);
        return new AppliedChangeResult(mergedPositions, mergedOriginals, mergedApplied);
    }

    static final class Builder {
        private final List<BlockPos> positions = new ArrayList<>();
        private final List<BlockState> originalStates = new ArrayList<>();
        private final List<BlockState> appliedStates = new ArrayList<>();

        void addSuccess(BlockPos pos, BlockState original, BlockState applied) {
            positions.add(pos);
            originalStates.add(original);
            appliedStates.add(applied);
        }

        AppliedChangeResult build() {
            return new AppliedChangeResult(
                List.copyOf(positions),
                List.copyOf(originalStates),
                List.copyOf(appliedStates)
            );
        }

        void mergeFrom(AppliedChangeResult result) {
            positions.addAll(result.positions());
            originalStates.addAll(result.originalStates());
            appliedStates.addAll(result.appliedStates());
        }
    }
}
