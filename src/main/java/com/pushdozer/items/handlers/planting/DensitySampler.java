package com.pushdozer.items.handlers.planting;

import com.pushdozer.config.PushdozerConfig;
import com.pushdozer.items.handlers.vegetation.PlantBlockClassifier;
import net.minecraft.block.Block;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.noise.SimplexNoiseSampler;
import net.minecraft.util.math.random.Random;

import java.util.List;

public class DensitySampler {
    private static final double MIN_TREE_PROB = 0.02; // 树的最小概率
    private static final double MIN_PLANT_PROB = 0.06; // 植物的最小概率
    private static final double NOISE_SCALE = 0.05; // 团簇噪声缩放

    private final PushdozerConfig config;
    private final Random random;
    private final SimplexNoiseSampler noiseSampler;

    public DensitySampler(PushdozerConfig config, Random random, SimplexNoiseSampler noiseSampler) {
        this.config = config;
        this.random = random;
        this.noiseSampler = noiseSampler;
    }

    /**
     * 根据噪声和密度决定是否在此处种植，以形成团簇效果
     * 改进版本：使用动态团簇规模和修正的密度逻辑
     * 农作物优化：如果自定义植物包含农作物，使用更均匀的分布
     */
    public boolean shouldPlantHere(BlockPos pos) {
        // 统一密度控制：所有植物类型都应用密度设置
        double density = Math.max(0.0, Math.min(1.0, config.getPlantDensity()));

        // 农作物使用更均匀的分布，但仍受密度控制
        if (config.getPlantType() == PushdozerConfig.PlantType.CUSTOM) {
            List<Block> customBlocks = config.getCustomPlantBlocks();
            boolean containsCrops = customBlocks.stream().anyMatch(PlantBlockClassifier::isCropBlock);
            if (containsCrops) {
                // 农作物使用简单的随机分布，但仍受密度控制
                return this.random.nextFloat() < density;
            }
        }

        // 树木沿用上一版语义：scale = clusterScale * NOISE_SCALE；
        // 其他类型保持“规模越大→频率越低”的语义
        float clusterScaleCfg = config.getClusterScale();
        double scale = (config.getPlantType() == PushdozerConfig.PlantType.TREES)
                ? (clusterScaleCfg * NOISE_SCALE)
                : (NOISE_SCALE / Math.max(0.1, clusterScaleCfg));

        int x = pos.getX();
        int z = pos.getZ();

        // fBm：多倍频叠加，柔化边界
        double n1 = noiseSampler.sample(x * scale, z * scale);
        double n2 = noiseSampler.sample(x * scale * 2.0, z * scale * 2.0);
        double n3 = noiseSampler.sample(x * scale * 4.0, z * scale * 4.0);
        double n = 0.6 * n1 + 0.3 * n2 + 0.1 * n3;

        // 归一化并加一点确定性抖动（简易蓝噪声）
        double finalProb = getFinalProb(n, x, z, density);
        return this.random.nextFloat() < (float) finalProb;
    }

    private double getFinalProb(double n, int x, int z, double density) {
        double p = (n + 1.0) * 0.5; // 0..1
        int h = (x * 73856093) ^ (z * 19349663);
        double jitter = ((h & 1023) / 1023.0) * 0.1 - 0.05; // ±0.05
        p = Math.max(0.0, Math.min(1.0, p + jitter));

        // 概率采样（带下限），避免小样本“全空”
        double baseProb = Math.max(0.0, Math.min(1.0, p * density));
        double minProb = (config.getPlantType() == PushdozerConfig.PlantType.TREES) ? MIN_TREE_PROB : MIN_PLANT_PROB;
        return Math.max(minProb, baseProb);
    }
}
