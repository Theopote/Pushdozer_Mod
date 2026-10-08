package com.pushdozer.items.handlers.surface;

import com.pushdozer.config.PushdozerConfig;
import com.pushdozer.config.domain.SurfaceConfig;
import com.pushdozer.items.handlers.SeededPerlinNoise;
import com.pushdozer.util.PositionRandom;
import com.pushdozer.util.RegistryBlocks;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.List;

/**
 * Validates surface-convert target materials and selects blocks deterministically per column.
 */
public final class SurfaceConvertMaterialSelector {

    public record ResolvedMaterial(Block block, float weight) {
    }

    public record SelectionContext(List<ResolvedMaterial> materials, float totalWeight,
                                   SeededPerlinNoise patchNoise, long scatterSeed,
                                   PushdozerConfig.SurfaceConvertDistribution distribution,
                                   float noiseFrequency) {
        public boolean isEmpty() {
            return materials.isEmpty() || totalWeight <= 0f;
        }
    }

    private SurfaceConvertMaterialSelector() {
    }

    public static SelectionContext prepare(PushdozerConfig config) {
        List<ResolvedMaterial> resolved = new ArrayList<>();
        float totalWeight = 0f;

        for (SurfaceConfig.SurfaceConvertBlock entry : config.getSurfaceConvertBlocks()) {
            Block block = RegistryBlocks.resolveOrAir(entry.getBlockId());
            if (block == Blocks.AIR || !NaturalTerrainClassifier.isValidTargetBlock(block)) {
                continue;
            }
            float weight = Math.max(0f, entry.getPercentage());
            if (weight <= 0f) {
                continue;
            }
            resolved.add(new ResolvedMaterial(block, weight));
            totalWeight += weight;
        }

        if (resolved.isEmpty()) {
            return new SelectionContext(List.of(), 0f, null, config.getNoiseSeed(),
                config.getSurfaceConvertDistribution(), config.getNoiseFrequency());
        }

        float scale = 100f / totalWeight;
        List<ResolvedMaterial> normalized = new ArrayList<>(resolved.size());
        float normalizedTotal = 0f;
        for (ResolvedMaterial material : resolved) {
            float weight = material.weight() * scale;
            normalized.add(new ResolvedMaterial(material.block(), weight));
            normalizedTotal += weight;
        }

        SeededPerlinNoise patchNoise = null;
        if (config.getSurfaceConvertDistribution() == PushdozerConfig.SurfaceConvertDistribution.PATCHY) {
            patchNoise = new SeededPerlinNoise(config.getNoiseSeed());
        }

        return new SelectionContext(normalized, normalizedTotal, patchNoise, config.getNoiseSeed(),
            config.getSurfaceConvertDistribution(), config.getNoiseFrequency());
    }

    public static Block selectBlock(SelectionContext context, BlockPos columnXZ) {
        if (context.isEmpty()) {
            return null;
        }

        float pick = switch (context.distribution()) {
            case PATCHY -> {
                float noise = context.patchNoise().sample(
                    columnXZ.getX(), columnXZ.getZ(),
                    context.noiseFrequency(), 0.5f, 3);
                yield (noise + 1f) * 0.5f * context.totalWeight();
            }
            case SCATTER -> PositionRandom.at(columnXZ, context.scatterSeed()).nextFloat() * context.totalWeight();
        };

        float cumulative = 0f;
        for (ResolvedMaterial material : context.materials()) {
            cumulative += material.weight();
            if (pick <= cumulative) {
                return material.block();
            }
        }
        return context.materials().getLast().block();
    }
}
