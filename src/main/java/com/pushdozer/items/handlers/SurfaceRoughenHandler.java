package com.pushdozer.items.handlers;

import com.pushdozer.config.PushdozerConfig;
import com.pushdozer.operations.UndoAction;
import com.pushdozer.shapes.GeometryShape;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Surface roughen: reshapes toward a smoothed baseline, then adds seeded Perlin noise.
 * All height change (smooth + noise) is scaled by a single brush-edge falloff.
 */
public class SurfaceRoughenHandler extends AbstractTerrainToolHandler {

    private static final float DEFAULT_NOISE_FREQUENCY = 0.02f;
    private static final float DEFAULT_NOISE_PERSISTENCE = 0.5f;
    /** At roughness strength 1.0, peak noise offset is about +/- this many blocks (before material modifiers). */
    private static final float MAX_ROUGHNESS_AMPLITUDE = 5.0f;
    private static final float GAUSSIAN_KERNEL_RADIUS_FACTOR = 2.5f;
    private static final float MIN_SIGMA = 1.0f;

    private SeededPerlinNoise operationNoise;
    private Map<Long, Float> operationNoiseCache;
    private int cachedNoiseSignature = Integer.MIN_VALUE;

    public SurfaceRoughenHandler() {
    }

    public void handleSurfaceRoughen(PlayerEntity player, World world, PushdozerConfig config) {
        beginOperationNoise(config);
        try {
            handleOperation(player, world, UndoAction.ActionType.SURFACE_ROUGHEN, config);
        } finally {
            operationNoise = null;
            operationNoiseCache = null;
            cachedNoiseSignature = Integer.MIN_VALUE;
        }
    }

    void beginOperationNoise(PushdozerConfig config) {
        this.config = config;
        operationNoise = new SeededPerlinNoise(config.getNoiseSeed());
        operationNoiseCache = new HashMap<>();
        cachedNoiseSignature = Integer.MIN_VALUE;
    }

    @Override
    protected int getSamplePaddingBlocks(PushdozerConfig config) {
        int brushRadius = getEffectiveBrushRadius(config);
        float sigma = brushRadius * getSigmaFactor(brushRadius);
        if (sigma < MIN_SIGMA) {
            sigma = MIN_SIGMA;
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

        Map<BlockPos, Integer> targetHeights = new HashMap<>();
        for (BlockPos columnXZ : modifyColumns) {
            TerrainColumn column = sampleColumns.get(columnXZ);
            if (column == null || isWaterSurfaceColumn(world, columnXZ, column)) {
                continue;
            }
            targetHeights.put(columnXZ,
                calculateTargetHeight(sampleColumns, column, columnXZ, brushCenter));
        }

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
        int brushRadius = getEffectiveBrushRadius(config);
        float originalHeight = currentColumn.originalHeight();

        float smoothedHeight = calculateSmoothedHeight(columns, columnXZ, brushCenter, brushRadius);
        if (Float.isNaN(smoothedHeight)) {
            smoothedHeight = originalHeight;
        }

        float smoothingIntensity = config.getSmoothingIntensity();
        float smoothDelta = smoothingIntensity * (smoothedHeight - originalHeight);
        float noiseDelta = computeNoiseDelta(currentColumn, columnXZ);

        float falloff = calculateBrushEdgeFalloff(columnXZ, brushCenter, brushRadius);
        float targetHeight = originalHeight + falloff * (smoothDelta + noiseDelta);

        return Math.round(targetHeight);
    }

    private float computeNoiseDelta(TerrainColumn currentColumn, BlockPos columnXZ) {
        float roughnessStrength = config.getRoughnessStrength();
        float roughnessAmount = Math.min(roughnessStrength * 3.0f, MAX_ROUGHNESS_AMPLITUDE);
        roughnessAmount *= getMaterialRoughnessMultiplier(currentColumn.mainBlockState());

        float noiseValue = sampleOperationNoise(columnXZ.getX(), columnXZ.getZ());
        return noiseValue * roughnessAmount;
    }

    private static float getMaterialRoughnessMultiplier(BlockState mainBlock) {
        if (mainBlock.isOf(Blocks.SAND)) {
            return 0.7f;
        }
        if (mainBlock.isOf(Blocks.STONE) || mainBlock.isOf(Blocks.DEEPSLATE)) {
            return 1.2f;
        }
        if (mainBlock.isOf(Blocks.GRASS_BLOCK)) {
            return 0.9f;
        }
        return 1.0f;
    }

    private float sampleOperationNoise(int worldX, int worldZ) {
        ensureOperationNoiseReady();

        float frequency = getNoiseFrequency();
        float persistence = getNoisePersistence();
        int octaves = getNoiseOctaves();
        int signature = noiseSignature(frequency, persistence, octaves);

        if (signature != cachedNoiseSignature) {
            operationNoiseCache.clear();
            cachedNoiseSignature = signature;
        }

        long cacheKey = packNoiseCacheKey(worldX, worldZ);
        Float cached = operationNoiseCache.get(cacheKey);
        if (cached != null) {
            return cached;
        }

        float result = operationNoise.sample(worldX, worldZ, frequency, persistence, octaves);
        operationNoiseCache.put(cacheKey, result);
        return result;
    }

    private void ensureOperationNoiseReady() {
        if (operationNoise == null) {
            operationNoise = new SeededPerlinNoise(config.getNoiseSeed());
        }
        if (operationNoiseCache == null) {
            operationNoiseCache = new HashMap<>();
        }
    }

    static long packNoiseCacheKey(int worldX, int worldZ) {
        return ((long) worldX << 32) ^ (worldZ & 0xFFFFFFFFL);
    }

    private static int noiseSignature(float frequency, float persistence, int octaves) {
        int freqBits = Float.floatToIntBits(frequency);
        int persistBits = Float.floatToIntBits(persistence);
        return 31 * (31 * octaves + freqBits) + persistBits;
    }

    private boolean isWaterSurfaceColumn(World world, BlockPos columnXZ, TerrainColumn column) {
        BlockPos surfacePos = new BlockPos(columnXZ.getX(), column.originalHeight(), columnXZ.getZ());
        return isWater(world, surfacePos);
    }

    private float getNoiseFrequency() {
        if (!config.isNoiseAutoScale()) {
            return config.getNoiseFrequency();
        }
        int r = Math.max(1, getEffectiveBrushRadius(config));
        float scale = (float) Math.clamp(12.0 / r, 0.5, 4.0);
        float freq = DEFAULT_NOISE_FREQUENCY * scale;
        return Math.clamp(freq, 0.01f, 0.15f);
    }

    private float getNoisePersistence() {
        if (!config.isNoiseAutoScale()) {
            return config.getNoisePersistence();
        }
        return DEFAULT_NOISE_PERSISTENCE;
    }

    private int getNoiseOctaves() {
        if (!config.isNoiseAutoScale()) {
            return config.getNoiseOctaves();
        }
        int r = Math.max(1, getEffectiveBrushRadius(config));
        return 3 + Math.clamp((r - 6) / 8, 0, 2);
    }
}
