package com.pushdozer.items.handlers;

import net.minecraft.util.math.BlockPos;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Directional smooth raise/lower: neighborhood smooth on original heights, optional directional
 * mound, spatial blend of the planned field, then a single edge-falloff on the final delta.
 */
final class DirectionalTerrainSmoother {

    private static final float MIN_SIGMA = 1.0f;
    private static final float GAUSSIAN_KERNEL_RADIUS_FACTOR = 2.5f;

    private DirectionalTerrainSmoother() {
    }

    record Params(
        int direction,
        float heightDelta,
        float terrainSmoothWeight,
        float spatialSmoothBlend,
        float maxDeltaPerStroke,
        int brushRadius
    ) {
    }

    static Map<BlockPos, Integer> computeTargetHeights(
        Map<BlockPos, AbstractTerrainToolHandler.TerrainColumn> sampleColumns,
        Set<BlockPos> modifyColumns,
        BlockPos brushCenter,
        Params params
    ) {
        Map<BlockPos, Float> plannedHeights = buildPlannedHeights(sampleColumns, brushCenter, params);
        Map<BlockPos, Float> spatiallySmoothed = smoothHeightField(plannedHeights, params.brushRadius);

        Map<BlockPos, Integer> results = new HashMap<>();
        for (BlockPos columnXZ : modifyColumns) {
            AbstractTerrainToolHandler.TerrainColumn column = sampleColumns.get(columnXZ);
            if (column == null) {
                continue;
            }
            results.put(columnXZ, computeTargetFromPlanned(
                column.getOriginalHeight(), columnXZ, brushCenter,
                plannedHeights, spatiallySmoothed, params));
        }
        return results;
    }

    static int computeTargetHeight(
        Map<BlockPos, AbstractTerrainToolHandler.TerrainColumn> sampleColumns,
        AbstractTerrainToolHandler.TerrainColumn currentColumn,
        BlockPos columnXZ,
        BlockPos brushCenter,
        Params params
    ) {
        Map<BlockPos, Float> plannedHeights = buildPlannedHeights(sampleColumns, brushCenter, params);
        Map<BlockPos, Float> spatiallySmoothed = smoothHeightField(plannedHeights, params.brushRadius);
        return computeTargetFromPlanned(
            currentColumn.getOriginalHeight(), columnXZ, brushCenter,
            plannedHeights, spatiallySmoothed, params);
    }

    private static Map<BlockPos, Float> buildPlannedHeights(
        Map<BlockPos, AbstractTerrainToolHandler.TerrainColumn> sampleColumns,
        BlockPos brushCenter,
        Params params
    ) {
        Map<BlockPos, Float> planned = new HashMap<>();
        for (Map.Entry<BlockPos, AbstractTerrainToolHandler.TerrainColumn> entry : sampleColumns.entrySet()) {
            BlockPos columnXZ = entry.getKey();
            float originalHeight = entry.getValue().getOriginalHeight();
            float centerFalloff = gaussianCenterFalloff(columnXZ, brushCenter, params.brushRadius);
            float moundDelta = params.direction * params.heightDelta * centerFalloff;

            float smoothedOriginal = smoothOriginalHeights(sampleColumns, columnXZ, params.brushRadius);
            float neighborhoodSmoothDelta = (smoothedOriginal - originalHeight) * params.terrainSmoothWeight;

            planned.put(columnXZ, originalHeight + moundDelta + neighborhoodSmoothDelta);
        }
        return planned;
    }

    private static int computeTargetFromPlanned(
        float originalHeight,
        BlockPos columnXZ,
        BlockPos brushCenter,
        Map<BlockPos, Float> plannedHeights,
        Map<BlockPos, Float> spatiallySmoothed,
        Params params
    ) {
        float planned = plannedHeights.getOrDefault(columnXZ, originalHeight);
        float smoothed = spatiallySmoothed.getOrDefault(columnXZ, planned);
        float blend = Math.max(0.0f, Math.min(1.0f, params.spatialSmoothBlend));
        float finalHeight = planned * (1.0f - blend) + smoothed * blend;

        float edgeFalloff = AbstractTerrainToolHandler.calculateBrushEdgeFalloff(
            columnXZ, brushCenter, params.brushRadius);
        float rawDelta = (finalHeight - originalHeight) * edgeFalloff;
        float delta = clampDirectionalDelta(rawDelta, params.direction, params.maxDeltaPerStroke);

        int target = Math.round(originalHeight + delta);
        if (params.direction > 0) {
            return Math.max(target, Math.round(originalHeight));
        }
        return Math.min(target, Math.round(originalHeight));
    }

    private static Map<BlockPos, Float> smoothHeightField(Map<BlockPos, Float> field, int brushRadius) {
        Map<BlockPos, Float> smoothed = new HashMap<>();
        for (BlockPos columnXZ : field.keySet()) {
            smoothed.put(columnXZ, smoothFieldAt(field, columnXZ, brushRadius));
        }
        return smoothed;
    }

    private static float clampDirectionalDelta(float rawDelta, int direction, float maxDeltaPerStroke) {
        if (direction > 0) {
            return Math.max(0.0f, Math.min(rawDelta, maxDeltaPerStroke));
        }
        return Math.min(0.0f, Math.max(rawDelta, -maxDeltaPerStroke));
    }

    private static float gaussianCenterFalloff(BlockPos columnXZ, BlockPos brushCenter, int brushRadius) {
        BlockPos brushCenterXZ = new BlockPos(brushCenter.getX(), 0, brushCenter.getZ());
        double distanceSq = columnXZ.getSquaredDistance(brushCenterXZ);

        float sigmaFactor = getSigmaFactor(brushRadius);
        float sigma = brushRadius * sigmaFactor;
        if (sigma < MIN_SIGMA) {
            sigma = MIN_SIGMA;
        }

        double twoSigmaSquared = 2.0 * sigma * sigma;
        return (float) Math.exp(-distanceSq / twoSigmaSquared);
    }

    private static float smoothFieldAt(Map<BlockPos, Float> field, BlockPos columnXZ, int brushRadius) {
        float sigmaFactor = getSigmaFactor(brushRadius);
        float sigma = brushRadius * sigmaFactor;
        if (sigma < MIN_SIGMA) {
            sigma = MIN_SIGMA;
        }

        double twoSigmaSquared = 2.0 * sigma * sigma;
        float kernelRadius = sigma * GAUSSIAN_KERNEL_RADIUS_FACTOR;
        float maxDistanceSq = kernelRadius * kernelRadius;

        BlockPos currentCenterXZ = new BlockPos(columnXZ.getX(), 0, columnXZ.getZ());
        float totalWeight = 0.0f;
        float weightedSum = 0.0f;

        for (Map.Entry<BlockPos, Float> entry : field.entrySet()) {
            BlockPos neighborXZ = entry.getKey();
            double distanceSq = neighborXZ.getSquaredDistance(currentCenterXZ);
            if (distanceSq > maxDistanceSq) {
                continue;
            }

            float weight = (float) Math.exp(-distanceSq / twoSigmaSquared);
            weightedSum += entry.getValue() * weight;
            totalWeight += weight;
        }

        if (totalWeight <= 0.0f) {
            Float local = field.get(columnXZ);
            return local != null ? local : 0.0f;
        }
        return weightedSum / totalWeight;
    }

    private static float smoothOriginalHeights(
        Map<BlockPos, AbstractTerrainToolHandler.TerrainColumn> sampleColumns,
        BlockPos columnXZ,
        int brushRadius
    ) {
        float sigmaFactor = getSigmaFactor(brushRadius);
        float sigma = brushRadius * sigmaFactor;
        if (sigma < MIN_SIGMA) {
            sigma = MIN_SIGMA;
        }

        double twoSigmaSquared = 2.0 * sigma * sigma;
        float kernelRadius = sigma * GAUSSIAN_KERNEL_RADIUS_FACTOR;
        float maxDistanceSq = kernelRadius * kernelRadius;

        BlockPos currentCenterXZ = new BlockPos(columnXZ.getX(), 0, columnXZ.getZ());
        float totalWeight = 0.0f;
        float weightedSum = 0.0f;

        for (Map.Entry<BlockPos, AbstractTerrainToolHandler.TerrainColumn> entry : sampleColumns.entrySet()) {
            BlockPos neighborXZ = entry.getKey();
            double distanceSq = neighborXZ.getSquaredDistance(currentCenterXZ);
            if (distanceSq > maxDistanceSq) {
                continue;
            }

            float weight = (float) Math.exp(-distanceSq / twoSigmaSquared);
            weightedSum += entry.getValue().getOriginalHeight() * weight;
            totalWeight += weight;
        }

        if (totalWeight <= 0.0f) {
            AbstractTerrainToolHandler.TerrainColumn local = sampleColumns.get(columnXZ);
            return local != null ? local.getOriginalHeight() : 0.0f;
        }
        return weightedSum / totalWeight;
    }

    private static float getSigmaFactor(int brushRadius) {
        if (brushRadius <= 5) {
            return 0.5f;
        }
        if (brushRadius <= 10) {
            return 0.4f;
        }
        return 0.35f;
    }
}
