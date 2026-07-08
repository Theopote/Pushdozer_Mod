package com.pushdozer.items.handlers.shoreline.profile;

import net.minecraft.block.BlockState;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.World;
import net.minecraft.world.biome.Biome;

import java.util.List;

public record ShorelineVegetationRule(
    ShorelineBiomeGroup biomeGroup,
    ShorelineGroundType groundType,
    List<WeightedPlantPicker.WeightedPlantChoice> choices
) {
    public boolean matches(RegistryEntry<Biome> biome, BlockState groundBlock) {
        return biomeGroup.matches(biome) && groundType.matches(groundBlock);
    }

    public BlockState pick(Random random, World world) {
        return WeightedPlantPicker.pick(random, world, choices);
    }
}
