package com.pushdozer.items.handlers;

import net.minecraft.util.math.BlockPos;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Two-pass directional terrain editing: apply signed Gaussian offset to build H1,
 * spatially smooth H1 into H2, then apply edge falloff and per-stroke delta limits.
 */
final class DirectionalTerrainSmoother {

    private static final float MIN_SIGMA = 1.0f;
    private static final float GAUSSIAN_KERNEL_RADIUS_FACTOR = 2.5f;

    private DirectionalTerrainSmoother() {
    }

    record Params(
        int direction,
        float heightDelta,
        float smoothBlend,
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
        Map<BlockPos, Float> h1Field = buildH1Field(sampleColumns, brushCenter, params);
        Map<BlockPos, Integer> results = new HashMap<>();
        for (BlockPos columnXZ : modifyColumns) {
            AbstractTerrainToolHandler.TerrainColumn column = sampleColumns.get(columnXZ);
            if (column == null) {
                continue;
            }
            results.put(columnXZ, computeTargetFromFields(
                column.getOriginalHeight(), columnXZ, brushCenter, h1Field, params));
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
        Map<BlockPos, Float> h1Field = buildH1Field(sampleColumns, brushCenter, params);
        return computeTargetFromFields(
            currentColumn.getOriginalHeight(), columnXZ, brushCenter, h1Field, params);
    }

    private static Map<BlockPos, Float> buildH1Field(
        Map<BlockPos, AbstractTerrainToolHandler.TerrainColumn> sampleColumns,
        BlockPos brushCenter,
        Params params
    ) {
        Map<BlockPos, Float> h1Field = new HashMap<>();
        for (Map.Entry<BlockPos, AbstractTerrainToolHandler.TerrainColumn> entry : sampleColumns.entrySet()) {
            BlockPos columnXZ = entry.getKey();
            float originalHeight = entry.getValue().getOriginalHeight();
            float centerFalloff = gaussianCenterFalloff(columnXZ, brushCenter, params.brushRadius);
            float h1 = originalHeight + params.direction * params.heightDelta * centerFalloff;
            h1Field.put(columnXZ, h1);
        }
        return h1Field;
    }

    private static int computeTargetFromFields(
        float originalHeight,
        BlockPos columnXZ,
        BlockPos brushCenter,
        Map<BlockPos, Float> h1Field,
        Params params
    ) {
        float h1 = h1Field.getOrDefault(columnXZ, originalHeight);
        float smoothedH1 = smoothFieldAt(h1Field, columnXZ, params.brushRadius);
        float blend = Math.max(0.0f, Math.min(1.0f, params.smoothBlend));
        float h2 = h1 * (1.0f - blend) + smoothedH1 * blend;

        float edgeFalloff = AbstractTerrainToolHandler.calculateBrushEdgeFalloff(
            columnXZ, brushCenter, params.brushRadius);
        float rawDelta = (h2 - originalHeight) * edgeFalloff;
        float delta = clampDirectionalDelta(rawDelta, params.direction, params.maxDeltaPerStroke);

        int target = Math.round(originalHeight + delta);
        if (params.direction > 0) {
            return Math.max(target, Math.round(originalHeight));
        }
        return Math.min(target, Math.round(originalHeight));
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
