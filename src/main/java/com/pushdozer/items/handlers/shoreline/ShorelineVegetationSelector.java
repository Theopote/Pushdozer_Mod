package com.pushdozer.items.handlers.shoreline;

import com.pushdozer.PushdozerMod;
import com.pushdozer.config.PushdozerConfig;
import com.pushdozer.items.handlers.shoreline.profile.ShorelineVegetationProfileRegistry;
import com.pushdozer.items.handlers.vegetation.PlantBlockClassifier;
import com.pushdozer.items.handlers.vegetation.PlantPools;
import com.pushdozer.util.PositionRandom;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.World;
import net.minecraft.world.biome.Biome;

import java.util.ArrayList;
import java.util.List;

/**
 * 岸线植被选择器：自定义植物 + profile 注册表驱动。
 */
public class ShorelineVegetationSelector {
    private final PushdozerConfig config;
    private final ShorelinePlantCompatibility compatibility;

    public ShorelineVegetationSelector(PushdozerConfig config, ShorelinePlantCompatibility compatibility) {
        this.config = config;
        this.compatibility = compatibility;
    }

    public BlockState getVegetationForBiome(World world, BlockPos pos, Biome biome, BlockState groundBlock) {
        PushdozerConfig.ShorelineType shorelineType = config.getShorelineType();
        PushdozerMod.LOGGER.debug("getVegetationForBiome: shorelineType = {}, pos = {}", shorelineType, pos);

        if (shorelineType == PushdozerConfig.ShorelineType.CUSTOM) {
            return resolveCustomPlant(world, pos, groundBlock);
        }

        RegistryEntry<Biome> biomeEntry = world.getBiome(pos);
        Random random = PositionRandom.at(pos, shorelineType.ordinal());
        return ShorelineVegetationProfileRegistry.resolve(
            shorelineType, world, pos, biomeEntry, groundBlock, random);
    }

    private BlockState resolveCustomPlant(World world, BlockPos pos, BlockState groundBlock) {
        List<Block> customPlants = config.getCustomShorelinePlantList();
        if (customPlants.isEmpty()) {
            PushdozerMod.LOGGER.debug("Custom shoreline type but no custom plants configured!");
            return null;
        }

        Block selectedPlant = pickSuitableCustomPlant(world, pos, customPlants, groundBlock);
        if (selectedPlant == null) {
            return null;
        }

        BlockState plantState = selectedPlant.getDefaultState();
        if (PlantBlockClassifier.isDeadCoral(selectedPlant)) {
            plantState = PlantPools.deadCoralWithoutWater(plantState);
        }
        return plantState;
    }

    private Block pickSuitableCustomPlant(World world, BlockPos pos, List<Block> customPlants, BlockState groundBlock) {
        List<Block> suitablePlants = new ArrayList<>();
        for (Block plant : customPlants) {
            if (plant != null && compatibility.canPlantGrowOnBlock(world, pos, plant, groundBlock)) {
                suitablePlants.add(plant);
            }
        }
        if (suitablePlants.isEmpty()) {
            return null;
        }
        Random random = PositionRandom.at(pos, PushdozerConfig.ShorelineType.CUSTOM.ordinal());
        return suitablePlants.get(random.nextInt(suitablePlants.size()));
    }
}
