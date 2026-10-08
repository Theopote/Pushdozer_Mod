package com.pushdozer.items.handlers;

import net.minecraft.util.math.BlockPos;

import java.util.Map;
import java.util.Set;

/**
 * Directional terrain editing: Gaussian mound/depression plus neighborhood smooth on original
 * heights, with edge falloff applied to the combined delta.
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
        Map<BlockPos, Integer> results = new java.util.HashMap<>();
        for (BlockPos columnXZ : modifyColumns) {
            AbstractTerrainToolHandler.TerrainColumn column = sampleColumns.get(columnXZ);
            if (column == null) {
                continue;
            }
            results.put(columnXZ, computeTargetHeight(
                sampleColumns, column, columnXZ, brushCenter, params));
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
        float originalHeight = currentColumn.getOriginalHeight();
        float edgeFalloff = AbstractTerrainToolHandler.calculateBrushEdgeFalloff(
            columnXZ, brushCenter, params.brushRadius);
        float centerFalloff = gaussianCenterFalloff(columnXZ, brushCenter, params.brushRadius);
        float moundDelta = params.direction * params.heightDelta * centerFalloff * edgeFalloff;

        float smoothedOriginal = smoothOriginalHeights(sampleColumns, columnXZ, params.brushRadius);
        float terrainDelta = (smoothedOriginal - originalHeight) * params.terrainSmoothWeight * edgeFalloff;
        float delta = combineDirectionalDeltas(moundDelta, terrainDelta, params.direction);
        delta = clampDirectionalDelta(delta, params.direction, params.maxDeltaPerStroke);

        int target = Math.round(originalHeight + delta);
        if (params.direction > 0) {
            return Math.max(target, Math.round(originalHeight));
        }
        return Math.min(target, Math.round(originalHeight));
    }

    private static float combineDirectionalDeltas(float moundDelta, float terrainDelta, int direction) {
        if (direction > 0) {
            return Math.max(0.0f, moundDelta) + Math.max(0.0f, terrainDelta);
        }
        return Math.min(0.0f, moundDelta) + Math.min(0.0f, terrainDelta);
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
