package com.pushdozer.items.handlers;

import com.pushdozer.config.PushdozerConfig;
import com.pushdozer.operations.UndoAction;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import java.util.*;

/**
 * 平滑处理器（兼容性类）
 * 现在使用自适应平滑算法，保持向后兼容
 * 
 * 改进版本：
 * - 可配置的高度敏感度参数
 * - 性能优化（限制计算范围）
 * - 非线性插值增强平滑效果
 * - 更好的边缘处理
 */
public class SmoothingHandler extends AbstractTerrainToolHandler {

    // 双边滤波参数配置
    private static final float DEFAULT_HEIGHT_SIGMA = 3.0f; // 默认高度敏感度
    private static final float MIN_HEIGHT_SIGMA = 1.0f;
    private static final float MAX_HEIGHT_SIGMA = 10.0f;
    
    // 性能优化参数
    private static final float BILATERAL_KERNEL_RADIUS_FACTOR = 2.0f; // 双边核半径因子

    public SmoothingHandler(PushdozerConfig config) {
        super(config);
    }

    /**
     * 处理平滑操作（兼容性方法）
     */
    public void handleSmoothing(PlayerEntity player, World world) {
        handleOperation(player, world, UndoAction.ActionType.SMOOTH);
    }

    /**
     * 计算平滑目标高度
     * 使用双边高斯模糊算法（改进版本）
     */
    @Override
    protected int calculateTargetHeight(Map<BlockPos, TerrainColumn> columns, 
                                     TerrainColumn currentColumn,
                                     BlockPos columnXZ, 
                                     BlockPos brushCenter) {
        float smoothStrength = config.getSmoothStrength();
        int brushRadius = config.getRadius();
        
        // 使用改进的双边高斯模糊计算平滑高度
        float smoothedHeight = calculateOptimizedBilateralSmoothedHeight(columns, currentColumn, columnXZ, brushCenter, brushRadius);
        
        // 使用非线性插值增强平滑效果
        float targetHeight = calculateNonLinearInterpolation(currentColumn.getOriginalHeight(), smoothedHeight, smoothStrength);
        
        return Math.round(targetHeight);
    }

    /**
     * 计算非线性插值
     * 使用平滑步进函数增强平滑效果
     */
    private float calculateNonLinearInterpolation(float originalHeight, float smoothedHeight, float smoothStrength) {
        // 使用平滑步进函数替代线性插值
        float smoothT = smoothStrength * smoothStrength * (3.0f - 2.0f * smoothStrength); // 平滑步进函数
        
        return originalHeight * (1.0f - smoothT) + smoothedHeight * smoothT;
    }

    /**
     * 计算优化的双边高斯模糊平滑高度
     * 权重不仅取决于空间距离，还取决于高度差
     */
    private float calculateOptimizedBilateralSmoothedHeight(Map<BlockPos, TerrainColumn> columns, 
                                                          TerrainColumn currentColumn,
                                                          BlockPos columnXZ, 
                                                          BlockPos brushCenter, 
                                                          int brushRadius) {
        float totalWeight = 0;
        float weightedHeightSum = 0;

        // 空间高斯核参数
        float spatialSigma = brushRadius / 2.0f;
        float twoSpatialSigmaSquared = 2.0f * spatialSigma * spatialSigma;
        
        // 可配置的高度差高斯核参数
        float heightSigma = getHeightSigma(brushRadius);
        float twoHeightSigmaSquared = 2.0f * heightSigma * heightSigma;
        
        // 性能优化：限制双边核范围
        float kernelRadius = spatialSigma * BILATERAL_KERNEL_RADIUS_FACTOR;
        float maxDistanceSq = kernelRadius * kernelRadius;
        
        // 创建2D平面上的笔刷中心点
        BlockPos brushCenterXZ = new BlockPos(brushCenter.getX(), 0, brushCenter.getZ());
        
        // 计算当前处理点到笔刷中心的距离（用于边缘衰减）
        float falloff = getFalloff(columnXZ, brushRadius, brushCenterXZ);

        // 遍历所有邻近的柱子计算加权平均高度
        for (Map.Entry<BlockPos, TerrainColumn> entry : columns.entrySet()) {
            BlockPos neighborColumnXZ = entry.getKey();
            TerrainColumn neighborColumn = entry.getValue();

            // 计算邻居到当前处理中心的空间距离
            double spatialDistanceSq = neighborColumnXZ.getSquaredDistance(columnXZ);
            
            // 性能优化：跳过超出双边核范围的方块
            if (spatialDistanceSq > maxDistanceSq) continue;
            
            // 计算高度差
            float heightDiff = Math.abs(neighborColumn.getOriginalHeight() - currentColumn.getOriginalHeight());
            
            // 空间高斯权重
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
     * 获取高度敏感度参数
     * 根据笔刷半径动态调整，适应不同地形
     */
    private float getHeightSigma(int brushRadius) {
        // 小半径使用较小的敏感度以保留细节
        if (brushRadius <= 5) {
            return 2.0f;
        } else if (brushRadius <= 10) {
            return 3.0f;
        } else {
            return 4.0f; // 大半径使用较大的敏感度以平滑陡峭地形
        }
    }

    private static float getFalloff(BlockPos columnXZ, int brushRadius, BlockPos brushCenterXZ) {
        double distanceToCenter = Math.sqrt(
            Math.pow(columnXZ.getX() - brushCenterXZ.getX(), 2) +
            Math.pow(columnXZ.getZ() - brushCenterXZ.getZ(), 2)
        );

        // 改进的边缘衰减计算
        float falloff = 1.0f;
        if (distanceToCenter > brushRadius * 0.8f) {
            float t = (float)((distanceToCenter - brushRadius * 0.8f) / (brushRadius * 0.2f));
            t = Math.min(1.0f, Math.max(0.0f, t));
            // 使用更平滑的衰减函数
            falloff = (float)(Math.cos(t * Math.PI) * 0.5f + 0.5f);
        }
        return falloff;
    }
}