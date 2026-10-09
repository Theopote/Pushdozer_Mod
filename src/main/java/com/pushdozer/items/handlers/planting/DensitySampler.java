package com.pushdozer.items.handlers.planting;

import com.pushdozer.config.PushdozerConfig;
import com.pushdozer.items.handlers.vegetation.PlantBlockClassifier;
import com.pushdozer.util.PositionRandom;
import net.minecraft.block.Block;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.noise.SimplexNoiseSampler;
import net.minecraft.util.math.random.Random;

import java.util.List;

public class DensitySampler {
    private static final double MIN_TREE_PROB = 0.02;
    private static final double MIN_PLANT_PROB = 0.06;
    private static final double NOISE_SCALE = 0.05;
    private static final long DENSITY_RANDOM_SALT = 0x44656E73L;

    private final PushdozerConfig config;
    private final long worldSeed;
    private final SimplexNoiseSampler noiseSampler;

    public DensitySampler(PushdozerConfig config, long worldSeed, SimplexNoiseSampler noiseSampler) {
        this.config = config;
        this.worldSeed = worldSeed;
        this.noiseSampler = noiseSampler;
    }

    /** @deprecated 使用 {@link #DensitySampler(PushdozerConfig, long, SimplexNoiseSampler)} */
    @Deprecated
    public DensitySampler(PushdozerConfig config, Random ignored, SimplexNoiseSampler noiseSampler) {
        this(config, 0L, noiseSampler);
    }

    public boolean shouldPlantHere(BlockPos pos) {
        double density = Math.clamp(config.getPlantDensity(), 0.0, 1.0);
        if (density >= 1.0) {
            return true;
        }
        double finalProb = computeFinalProbability(pos, density);
        Random positionRandom = PositionRandom.forOperation(pos, worldSeed, DENSITY_RANDOM_SALT);
        return positionRandom.nextFloat() < (float) finalProb;
    }

    double computeFinalProbability(BlockPos pos, double density) {
        if (config.getPlantType() == PushdozerConfig.PlantType.CUSTOM) {
            List<Block> customBlocks = config.getCustomPlantBlocks();
            boolean containsCrops = customBlocks.stream().anyMatch(PlantBlockClassifier::isCropBlock);
            if (containsCrops) {
                return density;
            }
        }

        float clusterScaleCfg = config.getClusterScale();
        double scale = (config.getPlantType() == PushdozerConfig.PlantType.TREES)
                ? (clusterScaleCfg * NOISE_SCALE)
                : (NOISE_SCALE / Math.max(0.1, clusterScaleCfg));

        int x = pos.getX();
        int z = pos.getZ();

        double n1 = noiseSampler.sample(x * scale, z * scale);
        double n2 = noiseSampler.sample(x * scale * 2.0, z * scale * 2.0);
        double n3 = noiseSampler.sample(x * scale * 4.0, z * scale * 4.0);
        double n = 0.6 * n1 + 0.3 * n2 + 0.1 * n3;

        return getFinalProb(n, x, z, density);
    }

    private double getFinalProb(double n, int x, int z, double density) {
        double p = (n + 1.0) * 0.5;
        int h = (x * 73856093) ^ (z * 19349663);
        double jitter = ((h & 1023) / 1023.0) * 0.1 - 0.05;
        p = Math.clamp(p + jitter, 0.0, 1.0);

        double baseProb = Math.clamp(p * density, 0.0, 1.0);
        double minProb = (config.getPlantType() == PushdozerConfig.PlantType.TREES) ? MIN_TREE_PROB : MIN_PLANT_PROB;
        return Math.max(minProb, baseProb);
    }
}
