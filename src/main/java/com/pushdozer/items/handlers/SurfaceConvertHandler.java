package com.pushdozer.items.handlers;

import com.pushdozer.PushdozerMod;
import com.pushdozer.config.PushdozerConfig;
import com.pushdozer.items.handlers.surface.NaturalTerrainClassifier;
import com.pushdozer.items.handlers.surface.SurfaceConvertMaterialSelector;
import com.pushdozer.items.handlers.surface.SurfacePlantSurvival;
import com.pushdozer.items.handlers.terrain.TerrainSurfaceQueries;
import com.pushdozer.items.handlers.vegetation.PlantBlockClassifier;
import com.pushdozer.operations.BlockOperation;
import com.pushdozer.operations.UndoAction;
import com.pushdozer.shapes.GeometryShape;
import com.pushdozer.util.ShapeUtil;
import com.pushdozer.util.TerrainOperationFeedback;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Surface convert: replace natural terrain at brush X/Z columns with configured materials.
 */
public class SurfaceConvertHandler implements TerrainToolHandler {

    public SurfaceConvertHandler() {
    }

    public void handleSurfaceConvert(PlayerEntity player, World world, PushdozerConfig config) {
        if (world.isClient()) {
            return;
        }

        BlockPos basePos = ShapeUtil.getTargetBlockPos(player, config);
        GeometryShape shape = ShapeUtil.createShape(player, config, basePos);
        if (shape == null) {
            return;
        }

        applySurfaceConvert(world, player, shape, basePos, config);
    }

    /**
     * Applies surface convert at an explicit brush center (used by GameTest and direct callers).
     *
     * @return false when target chunks are locked by another in-flight operation
     */
    public boolean applySurfaceConvert(World world, PlayerEntity player, GeometryShape shape,
                                       BlockPos brushCenter, PushdozerConfig config) {
        SurfaceConvertMaterialSelector.SelectionContext materials = SurfaceConvertMaterialSelector.prepare(config);
        if (materials.isEmpty()) {
            TerrainOperationFeedback.notifyInvalidSurfaceConvertConfig(player);
            return false;
        }

        List<BlockPos> affectedPositions = new ArrayList<>();
        List<BlockState> originalStates = new ArrayList<>();
        List<BlockState> newStates = new ArrayList<>();

        convertSurface(world, shape, brushCenter, config, materials, affectedPositions, originalStates, newStates);

        if (affectedPositions.isEmpty()) {
            return false;
        }
        if (!(world instanceof ServerWorld serverWorld)) {
            return false;
        }

        AtomicBoolean appliedAny = new AtomicBoolean(false);
        if (!BlockOperation.applyTerrainChanges(serverWorld, affectedPositions, newStates, applied -> {
            if (applied.isEmpty()) {
                return;
            }
            appliedAny.set(true);
            UndoAction undoAction = new UndoAction(
                UndoAction.ActionType.SURFACE_CONVERT,
                serverWorld.getRegistryKey(),
                applied.positions(),
                applied.originalStates(),
                applied.appliedStates()
            );
            PushdozerMod.pushUndoAction(player, undoAction);
        })) {
            TerrainOperationFeedback.notifyRegionBusy(player);
            return false;
        }
        if (affectedPositions.size() <= BlockOperation.SYNC_BLOCK_LIMIT) {
            return appliedAny.get();
        }
        return true;
    }

    private void convertSurface(World world, GeometryShape shape, BlockPos brushCenter, PushdozerConfig config,
                                SurfaceConvertMaterialSelector.SelectionContext materials,
                                List<BlockPos> affectedPositions,
                                List<BlockState> originalStates,
                                List<BlockState> newStates) {
        Set<BlockPos> columns = AbstractTerrainToolHandler.collectModifyColumnPositions(shape);
        if (columns.isEmpty()) {
            return;
        }

        int searchStartY = shape.getMaxY(brushCenter);
        int maxBelowSurfaceDepth = config.getSurfaceConvertMaxBelowSurfaceDepth();
        boolean allowArtificial = config.isConvertArtificialSurfaces();

        Map<BlockPos, PlannedSurfaceChange> plannedChanges = new LinkedHashMap<>();

        for (BlockPos columnXZ : columns) {
            BlockPos groundPos = resolveConvertibleSurface(
                world, columnXZ, searchStartY, maxBelowSurfaceDepth, allowArtificial);
            if (groundPos == null) {
                continue;
            }

            BlockState sourceState = world.getBlockState(groundPos);

            Block targetBlock = SurfaceConvertMaterialSelector.selectBlock(materials, columnXZ);
            if (targetBlock == null) {
                continue;
            }

            BlockState targetState = targetBlock.getDefaultState();
            if (sourceState.getBlock() == targetBlock) {
                continue;
            }

            plannedChanges.put(groundPos.toImmutable(), new PlannedSurfaceChange(sourceState, targetState));
        }

        Set<BlockPos> scheduled = new HashSet<>();
        for (Map.Entry<BlockPos, PlannedSurfaceChange> entry : plannedChanges.entrySet()) {
            BlockPos surfacePos = entry.getKey();
            PlannedSurfaceChange change = entry.getValue();
            scheduleChange(surfacePos, change.originalState(), change.targetState(),
                affectedPositions, originalStates, newStates, scheduled);
            collectPlantUpdates(world, surfacePos, change.targetState(),
                affectedPositions, originalStates, newStates, scheduled);
        }
    }

    private static void collectPlantUpdates(World world, BlockPos surfacePos, BlockState newSurfaceState,
                                            List<BlockPos> affectedPositions,
                                            List<BlockState> originalStates,
                                            List<BlockState> newStates,
                                            Set<BlockPos> scheduled) {
        BlockPos plantPos = surfacePos.up();
        BlockState plantState = world.getBlockState(plantPos);
        if (plantState.isAir()) {
            return;
        }
        if (!PlantBlockClassifier.isPlantOrDecoration(plantState)
            && !PlantBlockClassifier.hasExistingPlantOrDecoration(plantState.getBlock())) {
            return;
        }

        if (SurfacePlantSurvival.canSurviveOn(plantState, newSurfaceState)) {
            return;
        }

        scheduleChange(plantPos, plantState, Blocks.AIR.getDefaultState(),
            affectedPositions, originalStates, newStates, scheduled);

        BlockPos upperPos = SurfacePlantSurvival.upperPartPos(plantState, plantPos);
        if (upperPos != null) {
            BlockState upperState = world.getBlockState(upperPos);
            if (!upperState.isAir()) {
                scheduleChange(upperPos, upperState, Blocks.AIR.getDefaultState(),
                    affectedPositions, originalStates, newStates, scheduled);
            }
        }
    }

    private static void scheduleChange(BlockPos pos, BlockState originalState, BlockState newState,
                                       List<BlockPos> affectedPositions,
                                       List<BlockState> originalStates,
                                       List<BlockState> newStates,
                                       Set<BlockPos> scheduled) {
        if (!scheduled.add(pos)) {
            return;
        }
        affectedPositions.add(pos);
        originalStates.add(originalState);
        newStates.add(newState);
    }

    private record PlannedSurfaceChange(BlockState originalState, BlockState targetState) {
    }

    static BlockPos resolveConvertibleSurface(World world, BlockPos columnXZ, int searchStartY,
                                              int maxBelowSurfaceDepth, boolean allowArtificial) {
        BlockPos candidate = TerrainSurfaceQueries.findGroundBlock(world, columnXZ.withY(searchStartY));
        int attempts = 0;
        while (candidate != null && attempts < 16) {
            attempts++;

            if (TerrainSurfaceQueries.isWater(world, candidate)) {
                return null;
            }

            BlockState state = world.getBlockState(candidate);
            if (TerrainSurfaceQueries.isIgnoredBlock(state)
                || NaturalTerrainClassifier.isProtectedSource(state)
                || !NaturalTerrainClassifier.isConvertibleSource(state, allowArtificial)) {
                candidate = candidate.down();
                if (candidate.getY() < world.getBottomY()) {
                    return null;
                }
                continue;
            }

            if (!TerrainSurfaceQueries.isWithinSurfaceDepth(
                world, candidate.getX(), candidate.getZ(), candidate.getY(), maxBelowSurfaceDepth)) {
                return null;
            }

            return candidate;
        }
        return null;
    }
}
