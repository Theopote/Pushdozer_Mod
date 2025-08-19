package com.pushdozer.items.handlers;

import com.pushdozer.config.PushdozerConfig;
import net.minecraft.util.math.BlockPos;
import java.util.*;

/**
 * 自适应平滑模式处理器
 * 只平滑，不提升也不降低整体高度。抹平小的颠簸和坑洼，让粗糙的地形变得平缓，但保留大的地貌特征（如悬崖）。
 * 核心算法：双边高斯模糊 (Bilateral Gaussian Blur)
 */
public class AdaptiveSmoothHandler extends AbstractTerrainToolHandler {

    // 与兼容实现保持一致的参数：限制双边核半径，避免全域遍历
    private static final float BILATERAL_KERNEL_RADIUS_FACTOR = 2.0f;

    // 多尺度参数（偏向保留大地貌，仅抹小起伏）
    private static final float[] SCALE_FACTORS = {0.5f, 1.0f, 1.5f};
    private static final float[] SCALE_WEIGHTS = {0.5f, 0.4f, 0.1f};

    // 特征检测与保护
    private static final float RIDGE_THRESHOLD = 2.0f;
    private static final float VALLEY_THRESHOLD = -2.0f;
    private static final float FEATURE_PROTECTION_FACTOR = 0.35f; // 更偏向保形

    // 邻域偏移缓存，按半径平方缓存离散偏移，降低 N^2 遍历
    private static final java.util.Map<Integer, java.util.List<BlockPos>> OFFSETS_CACHE = new java.util.HashMap<>();

    public AdaptiveSmoothHandler(PushdozerConfig config) {
        super(config);
    }

    /**
     * 计算自适应平滑的目标高度
     * 核心算法：双边高斯模糊
     */
    @Override
        protected int calculateTargetHeight(Map<BlockPos, TerrainColumn> columns,
                                     TerrainColumn currentColumn,
                                     BlockPos columnXZ,
                                     BlockPos brushCenter) {
        float smoothStrength = config.getSmoothStrength();

        // 1) 多尺度平滑（结合不同尺度的双边平滑）
        float multiScaleHeight = calculateMultiScaleSmoothedHeight(columns, currentColumn, columnXZ, brushCenter);

        // 2) 地形特征检测与保护（保留山脊/谷地的大结构）
        TerrainFeature feature = detectTerrainFeature(columns, currentColumn, columnXZ);
        float protectedHeight = applyFeatureProtection(currentColumn.getOriginalHeight(), multiScaleHeight, feature);

        // 3) 使用 smootherstep 在原始高度和受保护平滑高度间做非线性插值
        float t = Math.max(0f, Math.min(1f, smoothStrength));
        float s = t * t * t * (t * (t * 6f - 15f) + 10f);
        float targetHeight = currentColumn.getOriginalHeight() * (1.0f - s) + protectedHeight * s;

        return Math.round(targetHeight);
    }

    /**
     * 计算双边高斯模糊平滑高度
     * 权重不仅取决于空间距离，还取决于高度差
     */
    private float calculateBilateralSmoothedHeight(Map<BlockPos, TerrainColumn> columns, 
                                                 TerrainColumn currentColumn,
                                                 BlockPos columnXZ, 
                                                 BlockPos brushCenter, 
                                                 int brushRadius) {
        float totalWeight = 0;
        float weightedHeightSum = 0;

        // 空间高斯核参数（以当前列为中心）
        float spatialSigma = brushRadius / 2.0f;
        float twoSpatialSigmaSquared = 2.0f * spatialSigma * spatialSigma;
        float kernelRadius = spatialSigma * BILATERAL_KERNEL_RADIUS_FACTOR;
        
        // 高度差高斯核参数（控制双边滤波的强度）— 自适应
        float heightSigma = getAdaptiveHeightSigma(brushRadius);
        float twoHeightSigmaSquared = 2.0f * heightSigma * heightSigma;
        
        // 创建2D平面上的笔刷中心点
        BlockPos brushCenterXZ = new BlockPos(brushCenter.getX(), 0, brushCenter.getZ());
        
        // 计算中心点到操作范围中心的距离（用于边缘衰减）
        float falloff = calculateFalloffWithoutSqrt(columnXZ, brushCenterXZ, brushRadius);
        
        // 遍历核邻域偏移，避免对 columns 全域遍历
        for (BlockPos offset : getOffsetsForRadius(kernelRadius)) {
            BlockPos neighborColumnXZ = new BlockPos(columnXZ.getX() + offset.getX(), 0, columnXZ.getZ() + offset.getZ());
            TerrainColumn neighborColumn = columns.get(neighborColumnXZ);
            if (neighborColumn == null) continue;

            // 与当前处理列的距离平方（整数偏移确保为整数）
            double spatialDistanceSq = offset.getX() * offset.getX() + offset.getZ() * offset.getZ();
            
            // 计算高度差
            float heightDiff = Math.abs(neighborColumn.getOriginalHeight() - currentColumn.getOriginalHeight());
            
            // 空间高斯权重（相对当前列）
            float spatialWeight = (float) Math.exp(-spatialDistanceSq / twoSpatialSigmaSquared);
            
            // 高度差高斯权重（双边滤波的核心）
            float heightWeight = (float) Math.exp(-heightDiff * heightDiff / twoHeightSigmaSquared);
            
            // 双边权重 = 空间权重 × 高度权重
            float bilateralWeight = spatialWeight * heightWeight;
            
            // 应用边缘衰减
            bilateralWeight *= falloff;
            
            weightedHeightSum += neighborColumn.getOriginalHeight() * bilateralWeight;
            totalWeight += bilateralWeight;
        }

        if (totalWeight <= 0) {
            return currentColumn.getOriginalHeight();
        }

        return weightedHeightSum / totalWeight;
    }

    /**
     * 自适应高度敏感度：随半径调整，保持不同规模下的手感一致
     */
    private float getAdaptiveHeightSigma(int brushRadius) {
        if (brushRadius <= 5) {
            return 2.0f;
        } else if (brushRadius <= 10) {
            return 3.0f;
        } else {
            return 4.0f;
        }
    }

    /**
     * 多尺度平滑：结合不同尺度的双边平滑结果
     */
    private float calculateMultiScaleSmoothedHeight(Map<BlockPos, TerrainColumn> columns,
                                                   TerrainColumn currentColumn,
                                                   BlockPos columnXZ,
                                                   BlockPos brushCenter) {
        float totalWeight = 0f;
        float weightedSum = 0f;

        for (int i = 0; i < SCALE_FACTORS.length; i++) {
            float scale = SCALE_FACTORS[i];
            float weight = SCALE_WEIGHTS[i];

            int scaledRadius = Math.max(1, Math.round(config.getRadius() * scale));
            float smoothedHeight = calculateBilateralSmoothedHeight(columns, currentColumn, columnXZ, brushCenter, scaledRadius);

            weightedSum += smoothedHeight * weight;
            totalWeight += weight;
        }

        return totalWeight > 0 ? weightedSum / totalWeight : currentColumn.getOriginalHeight();
    }

    /**
     * 地形特征检测：识别山脊/谷地，以保护大地貌
     */
    private TerrainFeature detectTerrainFeature(Map<BlockPos, TerrainColumn> columns,
                                               TerrainColumn currentColumn,
                                               BlockPos columnXZ) {
        float centerHeight = currentColumn.getOriginalHeight();
        java.util.List<Float> neighborHeights = new java.util.ArrayList<>();

        // 使用固定小半径（2格）检测局部相对高度
        for (BlockPos offset : getOffsetsForRadius(2.0f)) {
            if (offset.getX() == 0 && offset.getZ() == 0) continue;
            BlockPos neighborXZ = new BlockPos(columnXZ.getX() + offset.getX(), 0, columnXZ.getZ() + offset.getZ());
            TerrainColumn neighbor = columns.get(neighborXZ);
            if (neighbor != null) {
                neighborHeights.add((float) neighbor.getOriginalHeight());
            }
        }

        if (neighborHeights.isEmpty()) return TerrainFeature.FLAT;

        float avgNeighbor = 0f;
        for (float h : neighborHeights) avgNeighbor += h;
        avgNeighbor /= neighborHeights.size();

        float diff = centerHeight - avgNeighbor;
        if (diff > RIDGE_THRESHOLD) return TerrainFeature.RIDGE;
        if (diff < VALLEY_THRESHOLD) return TerrainFeature.VALLEY;
        return TerrainFeature.FLAT;
    }

    /**
     * 应用特征保护：对山脊/谷地减弱平滑强度
     */
    private float applyFeatureProtection(float originalHeight, float smoothedHeight, TerrainFeature feature) {
        if (feature == TerrainFeature.FLAT) return smoothedHeight;
        float p = FEATURE_PROTECTION_FACTOR;
        return originalHeight * p + smoothedHeight * (1.0f - p);
    }

    /**
     * 无开方边缘衰减（余弦窗），基于平方距离映射
     */
    private float calculateFalloffWithoutSqrt(BlockPos columnXZ, BlockPos brushCenterXZ, int brushRadius) {
        int dx = columnXZ.getX() - brushCenterXZ.getX();
        int dz = columnXZ.getZ() - brushCenterXZ.getZ();
        float d2 = dx * dx + dz * dz;

        float r0 = brushRadius * 0.8f;
        float r1sq = (float) brushRadius * (float) brushRadius;
        float r0sq = r0 * r0;

        if (d2 <= r0sq) return 1.0f;
        if (d2 >= r1sq) return 0.0f;

        float t = (d2 - r0sq) / (r1sq - r0sq);
        t = Math.max(0.0f, Math.min(1.0f, t));
        return (float) (Math.cos(t * Math.PI) * 0.5 + 0.5);
    }

    /**
     * 获取核半径内的整数偏移列表，按半径平方缓存
     */
    private java.util.List<BlockPos> getOffsetsForRadius(float radius) {
        int maxR = Math.max(1, Math.round(radius));
        int key = maxR * maxR; // 使用半径平方作为缓存键，避免重复
        java.util.List<BlockPos> cached = OFFSETS_CACHE.get(key);
        if (cached != null) return cached;

        java.util.List<BlockPos> offsets = new java.util.ArrayList<>();
        int r2 = maxR * maxR;
        for (int dz = -maxR; dz <= maxR; dz++) {
            for (int dx = -maxR; dx <= maxR; dx++) {
                int d2 = dx * dx + dz * dz;
                if (d2 <= r2) {
                    offsets.add(new BlockPos(dx, 0, dz));
                }
            }
        }
        OFFSETS_CACHE.put(key, java.util.Collections.unmodifiableList(offsets));
        return OFFSETS_CACHE.get(key);
    }

    private enum TerrainFeature {
        RIDGE,
        VALLEY,
        FLAT
    }
} 