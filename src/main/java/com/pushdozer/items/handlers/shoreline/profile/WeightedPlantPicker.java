package com.pushdozer.items.handlers.shoreline.profile;

import com.pushdozer.items.handlers.vegetation.PlantPools;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.World;

import java.util.List;

public final class WeightedPlantPicker {

    private WeightedPlantPicker() {
    }

    public static BlockState pick(Random random, World world, List<WeightedPlantChoice> choices) {
        if (choices == null || choices.isEmpty()) {
            return null;
        }
        float total = 0f;
        for (WeightedPlantChoice choice : choices) {
            total += choice.weight();
        }
        if (total <= 0f) {
            return null;
        }
        float roll = random.nextFloat() * total;
        float cumulative = 0f;
        for (WeightedPlantChoice choice : choices) {
            cumulative += choice.weight();
            if (roll < cumulative) {
                return choice.resolve(random, world);
            }
        }
        return choices.getLast().resolve(random, world);
    }

    public record WeightedPlantChoice(ChoiceKind kind, Block block, PlantPools.Pool pool, float weight) {

        public static WeightedPlantChoice block(Block block, float weight) {
            return new WeightedPlantChoice(ChoiceKind.BLOCK, block, null, weight);
        }

        public static WeightedPlantChoice pool(PlantPools.Pool pool, float weight) {
            return new WeightedPlantChoice(ChoiceKind.POOL, null, pool, weight);
        }

        public static WeightedPlantChoice none(float weight) {
            return new WeightedPlantChoice(ChoiceKind.NONE, null, null, weight);
        }

        public BlockState resolve(Random random, World world) {
            return switch (kind) {
                case BLOCK -> block.getDefaultState();
                case POOL -> PlantPools.pick(pool, random, world);
                case NONE -> null;
            };
        }
    }

    public enum ChoiceKind {
        BLOCK, POOL, NONE
    }
}
