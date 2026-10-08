package com.pushdozer.items.handlers;

import com.pushdozer.config.PushdozerConfig;
import com.pushdozer.shapes.GeometryShape;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Base handler for directional smooth raise/lower using {@link DirectionalTerrainSmoother}.
 */
abstract class AbstractDirectionalSmoothHandler extends AbstractTerrainToolHandler {

    private static final float GAUSSIAN_KERNEL_RADIUS_FACTOR = 2.5f;

    protected abstract int getDirection();

    protected abstract float getMaxDeltaPerStroke();

    /** Legacy bump scale at brush center (strength 1.0): raise 2.0, lower 1.5. */
    protected abstract float getBumpScale();

    @Override
    protected int getSamplePaddingBlocks(PushdozerConfig config) {
        int brushRadius = getEffectiveBrushRadius(config);
        float sigma = brushRadius * getSigmaFactor(brushRadius);
        if (sigma < 1.0f) {
            sigma = 1.0f;
        }
        float kernelRadius = sigma * GAUSSIAN_KERNEL_RADIUS_FACTOR;
        return (int) Math.ceil(kernelRadius);
    }

    @Override
    protected void processTerrain(World world, GeometryShape shape, BlockPos brushCenter, PushdozerConfig config,
                                  List<BlockPos> affectedPositions,
                                  List<BlockState> originalStates,
                                  List<BlockState> newStates) {
        Set<BlockPos> modifyColumns = collectModifyColumnPositions(shape);
        if (modifyColumns.isEmpty()) {
            return;
        }

        int padding = getSamplePaddingBlocks(config);
        Set<BlockPos> samplePositions = expandColumnPositions(modifyColumns, padding);
        int searchStartY = shape.getMaxY(brushCenter);
        Map<BlockPos, TerrainColumn> sampleColumns =
            collectTerrainColumnsForPositions(world, samplePositions, searchStartY);

        if (sampleColumns.isEmpty()) {
            return;
        }

        collectVegetationInBrushShape(world, shape, affectedPositions, originalStates, newStates);

        DirectionalTerrainSmoother.Params params = buildParams(config);
        Map<BlockPos, Integer> targetHeights = DirectionalTerrainSmoother.computeTargetHeights(
            sampleColumns, modifyColumns, brushCenter, params);

        for (Map.Entry<BlockPos, Integer> entry : targetHeights.entrySet()) {
            BlockPos columnXZ = entry.getKey();
            TerrainColumn column = sampleColumns.get(columnXZ);
            if (column == null) {
                continue;
            }
            applyHeightChange(world, columnXZ, column, entry.getValue(),
                affectedPositions, originalStates, newStates);
        }
    }

    @Override
    protected int calculateTargetHeight(Map<BlockPos, TerrainColumn> columns,
                                        TerrainColumn currentColumn,
                                        BlockPos columnXZ,
                                        BlockPos brushCenter) {
        return DirectionalTerrainSmoother.computeTargetHeight(
            columns, currentColumn, columnXZ, brushCenter, buildParams(config));
    }

    protected DirectionalTerrainSmoother.Params buildParams(PushdozerConfig config) {
        float strength = config.getSmoothStrength();
        float heightDelta = strength * getBumpScale();
        return new DirectionalTerrainSmoother.Params(
            getDirection(),
            heightDelta,
            applySmootherstep(strength),
            getMaxDeltaPerStroke(),
            getEffectiveBrushRadius(config)
        );
    }
}
