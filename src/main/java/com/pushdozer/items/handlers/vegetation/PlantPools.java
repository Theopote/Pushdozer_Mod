package com.pushdozer.items.handlers.vegetation;

import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.KelpBlock;
import net.minecraft.block.TallPlantBlock;
import net.minecraft.block.enums.DoubleBlockHalf;
import net.minecraft.state.property.Properties;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.World;

/**
 * 共享植物池：供岸线植被与批量种植复用。
 */
public final class PlantPools {

    private static final Block[] FLOWERS = {
        Blocks.DANDELION, Blocks.POPPY, Blocks.BLUE_ORCHID, Blocks.ALLIUM, Blocks.AZURE_BLUET,
        Blocks.RED_TULIP, Blocks.ORANGE_TULIP, Blocks.WHITE_TULIP, Blocks.PINK_TULIP,
        Blocks.OXEYE_DAISY, Blocks.CORNFLOWER, Blocks.LILY_OF_THE_VALLEY
    };

    private static final Block[] AQUATIC = {
        Blocks.SEAGRASS, Blocks.KELP, Blocks.SEA_PICKLE,
        Blocks.TUBE_CORAL, Blocks.BRAIN_CORAL, Blocks.BUBBLE_CORAL, Blocks.FIRE_CORAL, Blocks.HORN_CORAL
    };

    private static final Block[] FERNS = {Blocks.FERN, Blocks.LARGE_FERN};

    private static final Block[] MOSS = {
        Blocks.MOSS_CARPET, Blocks.GLOW_LICHEN, Blocks.SMALL_DRIPLEAF, Blocks.BIG_DRIPLEAF
    };

    private PlantPools() {
    }

    public enum Pool {
        FLOWER, AQUATIC, FERN, MOSS
    }

    public static net.minecraft.block.BlockState pick(Pool pool, Random random, World world) {
        return switch (pool) {
            case FLOWER -> randomFlower(random);
            case AQUATIC -> randomAquatic(random);
            case FERN -> randomFern(random);
            case MOSS -> randomMoss(random);
        };
    }

    public static net.minecraft.block.BlockState randomFlower(Random random) {
        return FLOWERS[random.nextInt(FLOWERS.length)].getDefaultState();
    }

    public static net.minecraft.block.BlockState randomAquatic(Random random) {
        Block selected = AQUATIC[random.nextInt(AQUATIC.length)];
        if (selected == Blocks.KELP) {
            return selected.getDefaultState().with(KelpBlock.AGE, random.nextInt(4));
        }
        return selected.getDefaultState();
    }

    public static net.minecraft.block.BlockState randomFern(Random random) {
        Block selected = FERNS[random.nextInt(FERNS.length)];
        if (selected == Blocks.LARGE_FERN) {
            return selected.getDefaultState().with(TallPlantBlock.HALF,
                random.nextBoolean() ? DoubleBlockHalf.LOWER : DoubleBlockHalf.UPPER);
        }
        return selected.getDefaultState();
    }

    public static net.minecraft.block.BlockState randomMoss(Random random) {
        return MOSS[random.nextInt(MOSS.length)].getDefaultState();
    }

    public static net.minecraft.block.BlockState deadCoralWithoutWater(net.minecraft.block.BlockState state) {
        if (state.contains(Properties.WATERLOGGED)) {
            return state.with(Properties.WATERLOGGED, false);
        }
        return state;
    }
}
