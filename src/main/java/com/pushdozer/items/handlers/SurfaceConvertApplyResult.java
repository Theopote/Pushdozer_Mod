package com.pushdozer.items.handlers;

import net.minecraft.block.Block;
import net.minecraft.util.math.BlockPos;

import java.util.List;
import java.util.Set;

/**
 * Diagnostic result for surface convert apply (used by GameTest; not shown to players).
 */
public record SurfaceConvertApplyResult(
    Stage stage,
    Set<BlockPos> columns,
    BlockPos resolvedSurface,
    Block sourceBlock,
    Block targetBlock,
    List<BlockPos> plannedPositions,
    int appliedBlockCount
) {
    public enum Stage {
        APPLIED,
        INVALID_MATERIAL,
        NO_COLUMNS,
        NO_VALID_SURFACE,
        NO_PLANNED_CHANGES,
        NOT_SERVER_WORLD,
        REGION_BUSY,
        WRITE_NOT_APPLIED
    }

    public boolean success() {
        return stage == Stage.APPLIED;
    }

    public static SurfaceConvertApplyResult invalidMaterial() {
        return new SurfaceConvertApplyResult(
            Stage.INVALID_MATERIAL, Set.of(), null, null, null, List.of(), 0);
    }

    public static SurfaceConvertApplyResult noColumns() {
        return new SurfaceConvertApplyResult(
            Stage.NO_COLUMNS, Set.of(), null, null, null, List.of(), 0);
    }

    public static SurfaceConvertApplyResult noValidSurface(Set<BlockPos> columns) {
        return new SurfaceConvertApplyResult(
            Stage.NO_VALID_SURFACE, columns, null, null, null, List.of(), 0);
    }

    public static SurfaceConvertApplyResult noPlannedChanges(
        Set<BlockPos> columns, BlockPos resolvedSurface, Block sourceBlock, Block targetBlock) {
        return new SurfaceConvertApplyResult(
            Stage.NO_PLANNED_CHANGES, columns, resolvedSurface, sourceBlock, targetBlock, List.of(), 0);
    }

    public static SurfaceConvertApplyResult notServerWorld(List<BlockPos> plannedPositions) {
        return new SurfaceConvertApplyResult(
            Stage.NOT_SERVER_WORLD, Set.of(), null, null, null, plannedPositions, 0);
    }

    public static SurfaceConvertApplyResult regionBusy(
        Set<BlockPos> columns, BlockPos resolvedSurface, List<BlockPos> plannedPositions) {
        return new SurfaceConvertApplyResult(
            Stage.REGION_BUSY, columns, resolvedSurface, null, null, plannedPositions, 0);
    }

    public static SurfaceConvertApplyResult writeNotApplied(
        Set<BlockPos> columns, BlockPos resolvedSurface, Block sourceBlock, Block targetBlock,
        List<BlockPos> plannedPositions) {
        return new SurfaceConvertApplyResult(
            Stage.WRITE_NOT_APPLIED, columns, resolvedSurface, sourceBlock, targetBlock, plannedPositions, 0);
    }

    public static SurfaceConvertApplyResult applied(
        Set<BlockPos> columns, BlockPos resolvedSurface, Block sourceBlock, Block targetBlock,
        List<BlockPos> plannedPositions, int appliedBlockCount) {
        return new SurfaceConvertApplyResult(
            Stage.APPLIED, columns, resolvedSurface, sourceBlock, targetBlock, plannedPositions, appliedBlockCount);
    }

    public String describeFailure() {
        return switch (stage) {
            case APPLIED -> "applied";
            case INVALID_MATERIAL -> "no valid surface-convert materials in config";
            case NO_COLUMNS -> "brush shape collected no X/Z columns";
            case NO_VALID_SURFACE -> "resolveConvertibleSurface found no convertible block in columns "
                + columns;
            case NO_PLANNED_CHANGES -> "surface resolved at " + resolvedSurface
                + " (source=" + sourceBlock + ", target=" + targetBlock + ") but nothing was scheduled";
            case NOT_SERVER_WORLD -> "world is not ServerWorld";
            case REGION_BUSY -> "terrain operation scheduler rejected chunk lock for "
                + plannedPositions.size() + " planned writes at " + resolvedSurface;
            case WRITE_NOT_APPLIED -> "planned " + plannedPositions.size() + " writes at " + resolvedSurface
                + " (source=" + sourceBlock + " -> target=" + targetBlock
                + ") but BlockOperation reported zero applied blocks";
        };
    }
}
