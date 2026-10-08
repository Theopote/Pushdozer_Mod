package com.pushdozer.operations;

import net.minecraft.block.BlockState;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class UndoAction {
    public enum ActionType {
        PLACE,
        BREAK,
        SMOOTH,
        SMOOTH_RAISE,
        SMOOTH_LOWER,
        SURFACE_ROUGHEN,
        SURFACE_CONVERT,
        BONE_MEAL,
        BATCH_PLANT,
        SHORELINE_PROCESS
    }

    private final ActionType type;
    private final RegistryKey<World> worldKey;
    private final List<BlockPos> positions;
    private final List<BlockState> originalStates;
    private final List<BlockState> newStates;
    private final List<BlockPos> boundaryPositions;
    private final List<BlockState> boundaryOriginalStates;
    private final List<BlockState> boundaryNewStates;

    public UndoAction(ActionType type, RegistryKey<World> worldKey,
                      List<BlockPos> positions, List<BlockState> originalStates, List<BlockState> newStates) {
        this(type, worldKey, positions, originalStates, newStates,
            List.of(), List.of(), List.of());
    }

    public UndoAction(ActionType type, RegistryKey<World> worldKey,
                      List<BlockPos> positions, List<BlockState> originalStates, List<BlockState> newStates,
                      Iterable<BlockPos> boundaryPositions, List<BlockState> boundaryOriginalStates,
                      List<BlockState> boundaryNewStates) {
        this.type = type;
        this.worldKey = worldKey;
        this.positions = positions;
        this.originalStates = originalStates;
        this.newStates = newStates;
        this.boundaryPositions = boundaryPositions == null
            ? List.of()
            : collectBoundaryList(boundaryPositions);
        this.boundaryOriginalStates = boundaryOriginalStates != null ? boundaryOriginalStates : List.of();
        this.boundaryNewStates = boundaryNewStates != null ? boundaryNewStates : List.of();
    }

    public ActionType getType() {
        return type;
    }

    public RegistryKey<World> getWorldKey() {
        return worldKey;
    }

    public boolean matchesWorld(ServerWorld world) {
        return world.getRegistryKey().equals(worldKey);
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

    /**
     * Ordered positions for undo/redo execution (core changes first, then boundary neighbors).
     */
    public List<BlockPos> getExecutionPositions() {
        List<BlockPos> all = new ArrayList<>(positions.size() + boundaryPositions.size());
        all.addAll(positions);
        all.addAll(boundaryPositions);
        return all;
    }

    /**
     * Original states aligned with {@link #getExecutionPositions()}.
     */
    public List<BlockState> getExecutionOriginalStates() {
        List<BlockState> all = new ArrayList<>(originalStates.size() + boundaryOriginalStates.size());
        all.addAll(originalStates);
        all.addAll(boundaryOriginalStates);
        return all;
    }

    /**
     * New states aligned with {@link #getExecutionPositions()}.
     */
    public List<BlockState> getExecutionNewStates() {
        List<BlockState> all = new ArrayList<>(newStates.size() + boundaryNewStates.size());
        all.addAll(newStates);
        all.addAll(boundaryNewStates);
        return all;
    }

    /** @deprecated Prefer {@link #getExecutionPositions()} for ordered execution. */
    @Deprecated
    public List<BlockPos> getAllPositions() {
        return getExecutionPositions();
    }

    /** @deprecated Prefer {@link #getExecutionOriginalStates()}. */
    @Deprecated
    public List<BlockState> getAllOriginalStates() {
        return getExecutionOriginalStates();
    }

    /** @deprecated Prefer {@link #getExecutionNewStates()}. */
    @Deprecated
    public List<BlockState> getAllNewStates() {
        return getExecutionNewStates();
    }

    public List<BlockPos> getBoundaryPositions() {
        return boundaryPositions;
    }

    public List<BlockState> getBoundaryOriginalStates() {
        return boundaryOriginalStates;
    }

    public List<BlockState> getBoundaryNewStates() {
        return boundaryNewStates;
    }

    public boolean isValid() {
        if (worldKey == null || positions == null || originalStates == null || newStates == null) {
            return false;
        }
        if (positions.size() != originalStates.size() || positions.size() != newStates.size()) {
            return false;
        }
        return boundaryPositions.size() == boundaryOriginalStates.size()
            && boundaryPositions.size() == boundaryNewStates.size();
    }

    public int getTotalBlockCount() {
        return positions.size() + boundaryPositions.size();
    }

    /**
     * Preserves insertion order so boundary states stay aligned with positions.
     */
    public static Set<BlockPos> orderedBoundarySet(Iterable<BlockPos> positions) {
        LinkedHashSet<BlockPos> ordered = new LinkedHashSet<>();
        for (BlockPos pos : positions) {
            ordered.add(pos);
        }
        return ordered;
    }

    private static List<BlockPos> collectBoundaryList(Iterable<BlockPos> boundaryPositions) {
        List<BlockPos> list = new ArrayList<>();
        for (BlockPos pos : boundaryPositions) {
            list.add(pos);
        }
        return list;
    }
}
