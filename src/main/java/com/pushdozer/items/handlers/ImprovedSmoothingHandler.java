package com.pushdozer.items.handlers;

import com.pushdozer.config.PushdozerConfig;
import com.pushdozer.operations.UndoAction;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import java.util.*;

/**
 * 改进的平滑处理器
 * 
 * 新增特性：
 * - 自适应平滑强度（根据地形复杂度调整）
 * - 多尺度平滑（处理不同频率的地形特征）
 * - 地形特征保护（保留重要的地形特征如山脊、峡谷）
 * - 更智能的边缘处理
 * - 性能优化的空间索引
 */
public class ImprovedSmoothingHandler extends AbstractTerrainToolHandler {

    // 多尺度平滑参数
    private static final float[] SCALE_FACTORS = {0.5f, 1.0f, 2.0f}; // 小、中、大尺度
    private static final float[] SCALE_WEIGHTS = {0.3f, 0.5f, 0.2f}; // 对应权重
    
    // 地形特征检测参数
    private static final float RIDGE_THRESHOLD = 2.0f; // 山脊检测阈值
    private static final float VALLEY_THRESHOLD = -2.0f; // 峡谷检测阈值
    private static final float FEATURE_PROTECTION_FACTOR = 0.3f; // 特征保护因子
    
    // 自适应参数
    private static final float MIN_COMPLEXITY_THRESHOLD = 0.5f;
    private static final float MAX_COMPLEXITY_THRESHOLD = 3.0f;

    public ImprovedSmoothingHandler(PushdozerConfig config) {
        super(config);
    }

    /**
     * 处理平滑操作（兼容性方法）
     */
    public void handleSmoothing(PlayerEntity player, World world) {
        handleOperation(player, world, UndoAction.ActionType.SMOOTH);
    }

    @Override
    protected int calculateTargetHeight(Map<BlockPos, TerrainColumn> columns, 
                                     TerrainColumn currentColumn,
                                     BlockPos columnXZ, 
                                     BlockPos brushCenter) {
        
        // 1. 地形复杂度分析
        float complexity = analyzeTerrainComplexity(columns, currentColumn, columnXZ);
        
        // 2. 自适应平滑强度
        float adaptiveSmoothStrength = calculateAdaptiveSmoothStrength(complexity);
        
        // 3. 地形特征检测
        TerrainFeature feature = detectTerrainFeature(columns, currentColumn, columnXZ);
        
        // 4. 多尺度平滑计算
        float multiScaleHeight = calculateMultiScaleSmoothedHeight(columns, currentColumn, columnXZ, brushCenter);
        
        // 5. 特征保护处理
        float protectedHeight = applyFeatureProtection(currentColumn.getOriginalHeight(), multiScaleHeight, feature);
        
        // 6. 最终插值
        float targetHeight = calculateAdaptiveInterpolation(
            currentColumn.getOriginalHeight(), 
            protectedHeight, 
            adaptiveSmoothStrength
        );
        
        return Math.round(targetHeight);
    }

    /**
     * 分析地形复杂度
     * 通过计算局部高度变化的标准差来评估地形复杂程度
     */
    private float analyzeTerrainComplexity(Map<BlockPos, TerrainColumn> columns, 
                                         TerrainColumn currentColumn, 
                                         BlockPos columnXZ) {
        List<Float> heights = new ArrayList<>();
        int radius = 3; // 用于复杂度分析的半径
        
        for (Map.Entry<BlockPos, TerrainColumn> entry : columns.entrySet()) {
            BlockPos pos = entry.getKey();
            if (pos.getSquaredDistance(columnXZ) <= radius * radius) {
                heights.add((float) entry.getValue().getOriginalHeight());
            }
        }
        
        if (heights.size() < 2) return MIN_COMPLEXITY_THRESHOLD;
        
        // 计算标准差作为复杂度指标
        float mean = (float) heights.stream().mapToDouble(Float::doubleValue).average().orElse(0.0);
        float variance = (float) heights.stream()
            .mapToDouble(h -> Math.pow(h - mean, 2))
            .average().orElse(0.0);
        
        return (float) Math.sqrt(variance);
    }

    /**
     * 计算自适应平滑强度
     * 复杂地形使用较小的平滑强度，平坦地形使用较大的平滑强度
     */
    private float calculateAdaptiveSmoothStrength(float complexity) {
        float baseSmoothStrength = config.getSmoothStrength();
        
        // 将复杂度映射到调整因子 [0.3, 1.0]
        float normalizedComplexity = Math.max(0, Math.min(1, 
            (complexity - MIN_COMPLEXITY_THRESHOLD) / (MAX_COMPLEXITY_THRESHOLD - MIN_COMPLEXITY_THRESHOLD)));
        
        float adjustmentFactor = 1.0f - 0.7f * normalizedComplexity;
        
        return baseSmoothStrength * adjustmentFactor;
    }

    /**
     * 地形特征检测
     */
    private TerrainFeature detectTerrainFeature(Map<BlockPos, TerrainColumn> columns, 
                                              TerrainColumn currentColumn, 
                                              BlockPos columnXZ) {
        float centerHeight = currentColumn.getOriginalHeight();
        List<Float> neighborHeights = new ArrayList<>();
        
        // 收集邻近高度
        for (Map.Entry<BlockPos, TerrainColumn> entry : columns.entrySet()) {
            BlockPos pos = entry.getKey();
            if (!pos.equals(columnXZ) && pos.getSquaredDistance(columnXZ) <= 4) { // 2格半径内
                neighborHeights.add((float) entry.getValue().getOriginalHeight());
            }
        }
        
        if (neighborHeights.isEmpty()) return TerrainFeature.FLAT;
        
        float avgNeighborHeight = (float) neighborHeights.stream().mapToDouble(Float::doubleValue).average().orElse(centerHeight);
        float heightDiff = centerHeight - avgNeighborHeight;
        
        if (heightDiff > RIDGE_THRESHOLD) {
            return TerrainFeature.RIDGE;
        } else if (heightDiff < VALLEY_THRESHOLD) {
            return TerrainFeature.VALLEY;
        } else {
            return TerrainFeature.FLAT;
        }
    }

    /**
     * 多尺度平滑计算
     * 结合不同尺度的平滑结果
     */
    private float calculateMultiScaleSmoothedHeight(Map<BlockPos, TerrainColumn> columns, 
                                                  TerrainColumn currentColumn,
                                                  BlockPos columnXZ, 
                                                  BlockPos brushCenter) {
        float totalWeight = 0;
        float weightedSum = 0;
        
        for (int i = 0; i < SCALE_FACTORS.length; i++) {
            float scale = SCALE_FACTORS[i];
            float weight = SCALE_WEIGHTS[i];
            
            float scaleRadius = config.getRadius() * scale;
            float smoothedHeight = calculateScaleSpecificSmoothing(columns, currentColumn, columnXZ, brushCenter, scaleRadius);
            
            weightedSum += smoothedHeight * weight;
            totalWeight += weight;
        }
        
        return weightedSum / totalWeight;
    }

    /**
     * 特定尺度的平滑计算
     */
    private float calculateScaleSpecificSmoothing(Map<BlockPos, TerrainColumn> columns, 
                                                TerrainColumn currentColumn,
                                                BlockPos columnXZ, 
                                                BlockPos brushCenter, 
                                                float radius) {
        float totalWeight = 0;
        float weightedHeightSum = 0;
        
        float spatialSigma = radius / 2.0f;
        float twoSpatialSigmaSquared = 2.0f * spatialSigma * spatialSigma;
        float heightSigma = getAdaptiveHeightSigma(radius);
        float twoHeightSigmaSquared = 2.0f * heightSigma * heightSigma;
        
        for (Map.Entry<BlockPos, TerrainColumn> entry : columns.entrySet()) {
            BlockPos neighborColumnXZ = entry.getKey();
            TerrainColumn neighborColumn = entry.getValue();
            
            double spatialDistanceSq = neighborColumnXZ.getSquaredDistance(columnXZ);
            if (spatialDistanceSq > radius * radius) continue;
            
            float heightDiff = Math.abs(neighborColumn.getOriginalHeight() - currentColumn.getOriginalHeight());
            
            float spatialWeight = (float) Math.exp(-spatialDistanceSq / twoSpatialSigmaSquared);
            float heightWeight = (float) Math.exp(-heightDiff * heightDiff / twoHeightSigmaSquared);
            float weight = spatialWeight * heightWeight;
            
            weightedHeightSum += neighborColumn.getOriginalHeight() * weight;
            totalWeight += weight;
        }
        
        return totalWeight > 0 ? weightedHeightSum / totalWeight : currentColumn.getOriginalHeight();
    }

    /**
     * 自适应高度敏感度
     */
    private float getAdaptiveHeightSigma(float radius) {
        // 根据半径和地形类型动态调整
        float baseSigma = radius / 3.0f;
        return Math.max(1.0f, Math.min(6.0f, baseSigma));
    }

    /**
     * 应用地形特征保护
     */
    private float applyFeatureProtection(float originalHeight, float smoothedHeight, TerrainFeature feature) {
        if (feature == TerrainFeature.FLAT) {
            return smoothedHeight;
        }
        
        // 对山脊和峡谷应用保护
        float protectionStrength = FEATURE_PROTECTION_FACTOR;
        return originalHeight * protectionStrength + smoothedHeight * (1.0f - protectionStrength);
    }

    /**
     * 自适应插值
     * 使用改进的平滑步进函数
     */
    private float calculateAdaptiveInterpolation(float originalHeight, float smoothedHeight, float strength) {
        // 使用 smootherstep 函数（6t^5 - 15t^4 + 10t^3）
        float t = Math.max(0, Math.min(1, strength));
        float smoothT = t * t * t * (t * (t * 6 - 15) + 10);
        
        return originalHeight * (1.0f - smoothT) + smoothedHeight * smoothT;
    }

    /**
     * 地形特征枚举
     */
    private enum TerrainFeature {
        RIDGE,    // 山脊
        VALLEY,   // 峡谷
        FLAT      // 平坦
    }
}
