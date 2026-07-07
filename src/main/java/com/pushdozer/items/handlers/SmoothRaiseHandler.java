package com.pushdozer.items.handlers;

import com.pushdozer.config.PushdozerConfig;
import com.pushdozer.operations.UndoAction;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import java.util.*;

/**
 * 平滑提升模式处理器
 * 将地形平滑地提升到指定高度
 * 核心算法：高斯提升 + 区域平滑
 * 
 * 改进版本：
 * - 可配置的高斯参数
 * - 性能优化（限制计算范围）
 * - 更好的高度插值
 * - 最大提升深度限制
 */
public class SmoothRaiseHandler extends AbstractTerrainToolHandler {

    // 高斯参数配置
    // 保留但当前未使用的默认因子改为与 getSigmaFactor 对齐或移除
    // 移除未使用常量以避免警告
    private static final float MIN_SIGMA = 1.0f;
    private static final float MAX_RAISE_DEPTH = 10.0f; // 最大提升深度
    
    // 性能优化参数
    private static final float GAUSSIAN_KERNEL_RADIUS_FACTOR = 2.5f; // 高斯核半径因子

    // 邻域偏移缓存，按半径平方缓存离散偏移
    private static final java.util.Map<Integer, java.util.List<BlockPos>> OFFSETS_CACHE = new java.util.HashMap<>();

    public SmoothRaiseHandler() {
    }

    /**
     * 处理平滑提升操作
     */
    public void handleSmoothRaise(PlayerEntity player, World world, PushdozerConfig config) {
        handleOperation(player, world, UndoAction.ActionType.SMOOTH_RAISE, config);
    }

    /**
     * 计算平滑提升的目标高度
     * 核心算法：高斯提升 + 区域平滑
     */
    @Override
    protected int calculateTargetHeight(Map<BlockPos, TerrainColumn> columns, 
                                     TerrainColumn currentColumn,
                                     BlockPos columnXZ, 
                                     BlockPos brushCenter) {
        float smoothStrength = config.getSmoothStrength();
        int brushRadius = config.getRadius();

        // 1. 区域平滑（以当前列为核中心，使用偏移缓存与核半径限制）
        float smoothedHeight = calculateColumnCenteredSmoothedHeight(columns, columnXZ, brushCenter, brushRadius);

        // 2. 高斯提升：计算提升量（去除不必要的开方）
        float raiseAmount = calculateRaiseAmount(columnXZ, brushCenter, brushRadius, smoothStrength);

        // 3. 更平滑的插值（smootherstep）
        float t = Math.max(0f, Math.min(1f, smoothStrength));
        float s = t * t * t * (t * (t * 6f - 15f) + 10f);
        float targetHeight = currentColumn.getOriginalHeight() + 
                           (smoothedHeight - currentColumn.getOriginalHeight()) * s + 
                           raiseAmount;
        
        // 4. 应用最大提升深度限制
        float maxRaise = currentColumn.getOriginalHeight() + MAX_RAISE_DEPTH;
        targetHeight = Math.min(targetHeight, maxRaise);
        
        // 5. 确保最终高度不低于原始高度（这是一个提升操作）
        return Math.max(Math.round(targetHeight), currentColumn.getOriginalHeight());
    }

    /**
     * 计算高斯提升量（改进版本）
     */
    private float calculateRaiseAmount(BlockPos columnXZ, 
                                     BlockPos brushCenter, 
                                     int brushRadius, 
                                     float strength) {
        // 计算到笔刷中心的2D距离（使用平方距离，避免开方）
        BlockPos brushCenterXZ = new BlockPos(brushCenter.getX(), 0, brushCenter.getZ());
        double distanceSq = columnXZ.getSquaredDistance(brushCenterXZ);
        
        // 使用基类的高斯参数
        float sigmaFactor = getSigmaFactor(brushRadius);
        float sigma = brushRadius * sigmaFactor;
        if (sigma < MIN_SIGMA) sigma = MIN_SIGMA;
        
        double twoSigmaSquared = 2.0 * sigma * sigma;
        float falloff = (float) Math.exp(-distanceSq / twoSigmaSquared);
        
        // 限制提升量，避免过度提升
        float maxRaiseAmount = strength * 2.0f; // 最大提升量为强度的2倍
        return Math.min(strength * falloff, maxRaiseAmount);
    }

    /**
     * 以当前列为核中心的区域平滑（仅空间高斯），使用核半径限制与偏移缓存
     */
    private float calculateColumnCenteredSmoothedHeight(Map<BlockPos, TerrainColumn> columns,
                                                       BlockPos columnXZ,
                                                       BlockPos brushCenter,
                                                       int brushRadius) {
        float sigmaFactor = getSigmaFactor(brushRadius);
        float sigma = brushRadius * sigmaFactor;
        if (sigma < MIN_SIGMA) sigma = MIN_SIGMA;
        double twoSigmaSquared = 2.0 * sigma * sigma;

        // 统一核半径：与 Abstract 逻辑一致（sigma * 2.5）
        float kernelRadius = sigma * GAUSSIAN_KERNEL_RADIUS_FACTOR;
        java.util.List<BlockPos> offsets = getOffsetsForRadius(kernelRadius);

        float totalWeight = 0f;
        float weightedSum = 0f;
        for (BlockPos offset : offsets) {
            BlockPos neighborXZ = new BlockPos(columnXZ.getX() + offset.getX(), 0, columnXZ.getZ() + offset.getZ());
            TerrainColumn neighbor = columns.get(neighborXZ);
            if (neighbor == null) continue;

            int dx = offset.getX();
            int dz = offset.getZ();
            int d2 = dx * dx + dz * dz;
            float weight = (float) Math.exp(-(d2) / twoSigmaSquared);
            weightedSum += neighbor.getOriginalHeight() * weight;
            totalWeight += weight;
        }

        if (totalWeight <= 0f) return columns.get(columnXZ) != null ? columns.get(columnXZ).getOriginalHeight() : 0f;
        return weightedSum / totalWeight;
    }

    /**
     * 获取核半径内的整数偏移列表，按半径平方缓存
     */
    private java.util.List<BlockPos> getOffsetsForRadius(float radius) {
        int maxR = Math.max(1, Math.round(radius));
        int key = maxR * maxR;
        java.util.List<BlockPos> cached = OFFSETS_CACHE.get(key);
        if (cached != null) return cached;

        java.util.List<BlockPos> offsets = new java.util.ArrayList<>();
        int r2 = maxR * maxR;
        for (int dz = -maxR; dz <= maxR; dz++) {
            for (int dx = -maxR; dx <= maxR; dx++) {
                int d2 = dx * dx + dz * dz;
                if (d2 <= r2) offsets.add(new BlockPos(dx, 0, dz));
            }
        }
        OFFSETS_CACHE.put(key, java.util.Collections.unmodifiableList(offsets));
        return OFFSETS_CACHE.get(key);
    }
} 