package com.pushdozer.items.handlers;

import com.pushdozer.config.PushdozerConfig;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Adaptive smooth mode: removes small-scale noise while preserving large landforms.
 * Uses multi-scale bilateral Gaussian blur with slope/laplacian feature protection.
 */
public class AdaptiveSmoothHandler extends AbstractTerrainToolHandler {

    private static final float BILATERAL_KERNEL_RADIUS_FACTOR = 2.0f;
    private static final float[] SCALE_FACTORS = {0.5f, 1.0f, 1.5f};
    private static final float[] SCALE_WEIGHTS = {0.5f, 0.4f, 0.1f};

    private static final float LAPLACIAN_THRESHOLD = 1.5f;
    private static final float SLOPE_THRESHOLD = 2.0f;
    private static final float FEATURE_PROTECTION_FACTOR = 0.35f;

    private static final int OFFSETS_CACHE_MAX_ENTRIES = 16;
    private static final Map<Integer, List<BlockPos>> OFFSETS_CACHE =
        Collections.synchronizedMap(new LinkedHashMap<>(16, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<Integer, List<BlockPos>> eldest) {
                return size() > OFFSETS_CACHE_MAX_ENTRIES;
            }
        });

    public AdaptiveSmoothHandler() {
    }

    @Override
    protected int getSamplePaddingBlocks(PushdozerConfig config) {
        int baseRadius = getEffectiveBrushRadius(config);
        float maxScale = SCALE_FACTORS[SCALE_FACTORS.length - 1];
        int scaledRadius = Math.max(1, Math.round(baseRadius * maxScale));
        float spatialSigma = scaledRadius / 2.0f;
        float kernelRadius = spatialSigma * BILATERAL_KERNEL_RADIUS_FACTOR;
        return (int) Math.ceil(kernelRadius);
    }

    @Override
    protected int calculateTargetHeight(Map<BlockPos, TerrainColumn> columns,
                                        TerrainColumn currentColumn,
                                        BlockPos columnXZ,
                                        BlockPos brushCenter) {
        int brushRadius = getEffectiveBrushRadius(config);
        float originalHeight = currentColumn.originalHeight();

        float multiScaleHeight = calculateMultiScaleSmoothedHeight(columns, currentColumn, columnXZ, brushCenter);
        float protectedHeight = applyFeatureProtection(
            originalHeight, multiScaleHeight, detectStructuralFeature(columns, currentColumn, columnXZ));

        float mappedStrength = applySmootherstep(config.getSmoothStrength());
        float falloff = calculateBrushEdgeFalloff(columnXZ, brushCenter, brushRadius);
        float targetHeight = blendHeightWithStrengthAndFalloff(
            originalHeight, protectedHeight, mappedStrength, falloff);

        return Math.round(targetHeight);
    }

    private float calculateBilateralSmoothedHeight(Map<BlockPos, TerrainColumn> columns,
                                                   TerrainColumn currentColumn,
                                                   BlockPos columnXZ,
                                                   int brushRadius) {
        float totalWeight = 0;
        float weightedHeightSum = 0;

        float spatialSigma = brushRadius / 2.0f;
        float twoSpatialSigmaSquared = 2.0f * spatialSigma * spatialSigma;
        float kernelRadius = spatialSigma * BILATERAL_KERNEL_RADIUS_FACTOR;

        float heightSigma = getAdaptiveHeightSigma(brushRadius);
        float twoHeightSigmaSquared = 2.0f * heightSigma * heightSigma;

        for (BlockPos offset : getOffsetsForRadius(kernelRadius)) {
            BlockPos neighborColumnXZ = new BlockPos(
                columnXZ.getX() + offset.getX(), 0, columnXZ.getZ() + offset.getZ());
            TerrainColumn neighborColumn = columns.get(neighborColumnXZ);
            if (neighborColumn == null) {
                continue;
            }

            double spatialDistanceSq = offset.getX() * offset.getX() + offset.getZ() * offset.getZ();
            float heightDiff = Math.abs(neighborColumn.originalHeight() - currentColumn.originalHeight());

            float spatialWeight = (float) Math.exp(-spatialDistanceSq / twoSpatialSigmaSquared);
            float heightWeight = (float) Math.exp(-heightDiff * heightDiff / twoHeightSigmaSquared);
            float bilateralWeight = spatialWeight * heightWeight;

            weightedHeightSum += neighborColumn.originalHeight() * bilateralWeight;
            totalWeight += bilateralWeight;
        }

        if (totalWeight <= 0) {
            return currentColumn.originalHeight();
        }

        return weightedHeightSum / totalWeight;
    }

    private float getAdaptiveHeightSigma(int brushRadius) {
        if (brushRadius <= 5) {
            return 1.5f;
        } else if (brushRadius <= 10) {
            return 2.5f;
        } else {
            return 3.5f;
        }
    }

    private float calculateMultiScaleSmoothedHeight(Map<BlockPos, TerrainColumn> columns,
                                                    TerrainColumn currentColumn,
                                                    BlockPos columnXZ,
                                                    BlockPos brushCenter) {
        int baseRadius = getEffectiveBrushRadius(config);
        float weightedSum = 0f;

        for (int i = 0; i < SCALE_FACTORS.length; i++) {
            int scaledRadius = Math.max(1, Math.round(baseRadius * SCALE_FACTORS[i]));
            float smoothedHeight = calculateBilateralSmoothedHeight(
                columns, currentColumn, columnXZ, scaledRadius);
            weightedSum += smoothedHeight * SCALE_WEIGHTS[i];
        }

        return weightedSum;
    }

    private boolean detectStructuralFeature(Map<BlockPos, TerrainColumn> columns,
                                            TerrainColumn currentColumn,
                                            BlockPos columnXZ) {
        float centerHeight = currentColumn.originalHeight();

        Float north = getCardinalHeight(columns, columnXZ, 0, -1);
        Float south = getCardinalHeight(columns, columnXZ, 0, 1);
        Float east = getCardinalHeight(columns, columnXZ, 1, 0);
        Float west = getCardinalHeight(columns, columnXZ, -1, 0);

        if (north == null || south == null || east == null || west == null) {
            return false;
        }

        float laplacian = 4 * centerHeight - (north + south + east + west);
        float slope = Math.max(
            Math.max(Math.abs(centerHeight - north), Math.abs(centerHeight - south)),
            Math.max(Math.abs(centerHeight - east), Math.abs(centerHeight - west))
        );

        return Math.abs(laplacian) > LAPLACIAN_THRESHOLD && slope > SLOPE_THRESHOLD;
    }

    private static Float getCardinalHeight(Map<BlockPos, TerrainColumn> columns, BlockPos columnXZ, int dx, int dz) {
        TerrainColumn neighbor = columns.get(new BlockPos(columnXZ.getX() + dx, 0, columnXZ.getZ() + dz));
        return neighbor == null ? null : (float) neighbor.originalHeight();
    }

    private float applyFeatureProtection(float originalHeight, float smoothedHeight, boolean structural) {
        if (!structural) {
            return smoothedHeight;
        }
        float p = FEATURE_PROTECTION_FACTOR;
        return originalHeight * p + smoothedHeight * (1.0f - p);
    }

    private List<BlockPos> getOffsetsForRadius(float radius) {
        int maxR = Math.max(1, Math.round(radius));
        List<BlockPos> cached = OFFSETS_CACHE.get(maxR);
        if (cached != null) {
            return cached;
        }

        List<BlockPos> offsets = new ArrayList<>();
        int r2 = maxR * maxR;
        for (int dz = -maxR; dz <= maxR; dz++) {
            for (int dx = -maxR; dx <= maxR; dx++) {
                if (dx * dx + dz * dz <= r2) {
                    offsets.add(new BlockPos(dx, 0, dz));
                }
            }
        }
        List<BlockPos> immutable = List.copyOf(offsets);
        OFFSETS_CACHE.put(maxR, immutable);
        return immutable;
    }
}
