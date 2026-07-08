package com.pushdozer.items.handlers.vegetation;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.FlowerBlock;
import net.minecraft.block.FlowerPotBlock;
import net.minecraft.block.MushroomBlock;
import net.minecraft.block.PlantBlock;
import net.minecraft.block.SaplingBlock;
import net.minecraft.block.TallPlantBlock;

/**
 * 植物与装饰方块的分类判断，供海岸线处理与批量种植共享。
 */
public final class PlantBlockClassifier {

    private PlantBlockClassifier() {
    }

    public static boolean isAquatic(Block block) {
        return block == Blocks.SEAGRASS
                || block == Blocks.TALL_SEAGRASS
                || block == Blocks.KELP
                || block == Blocks.KELP_PLANT
                || block == Blocks.SEA_PICKLE;
    }

    public static boolean isLiveCoral(Block block) {
        String id = block.toString().toLowerCase();
        return id.contains("coral") && !id.contains("dead");
    }

    public static boolean isLiveCoralFan(Block block) {
        String id = block.toString().toLowerCase();
        return id.contains("coral") && id.contains("fan") && !id.contains("dead");
    }

    public static boolean isDeadCoral(Block block) {
        String id = block.toString().toLowerCase();
        return id.contains("coral") && id.contains("dead");
    }

    public static boolean isCropBlock(Block block) {
        return block == Blocks.WHEAT
                || block == Blocks.CARROTS
                || block == Blocks.POTATOES
                || block == Blocks.BEETROOTS
                || block == Blocks.PUMPKIN_STEM
                || block == Blocks.MELON_STEM;
    }

    public static boolean isPotted(Block block) {
        return block instanceof FlowerPotBlock && block != Blocks.FLOWER_POT;
    }

    public static boolean isPlantBlock(Block block) {
        return block == Blocks.DANDELION || block == Blocks.POPPY || block == Blocks.BLUE_ORCHID
                || block == Blocks.ALLIUM || block == Blocks.AZURE_BLUET || block == Blocks.RED_TULIP
                || block == Blocks.ORANGE_TULIP || block == Blocks.WHITE_TULIP || block == Blocks.PINK_TULIP
                || block == Blocks.OXEYE_DAISY || block == Blocks.CORNFLOWER || block == Blocks.LILY_OF_THE_VALLEY
                || block == Blocks.SUNFLOWER || block == Blocks.LILAC || block == Blocks.ROSE_BUSH
                || block == Blocks.PEONY || block == Blocks.SHORT_GRASS || block == Blocks.TALL_GRASS
                || block == Blocks.FERN || block == Blocks.LARGE_FERN || block == Blocks.DEAD_BUSH
                || block == Blocks.CACTUS || block == Blocks.CACTUS_FLOWER || block == Blocks.SUGAR_CANE
                || block == Blocks.BAMBOO || block == Blocks.CHORUS_PLANT || block == Blocks.CHORUS_FLOWER
                || block == Blocks.NETHER_SPROUTS || block == Blocks.WARPED_ROOTS || block == Blocks.CRIMSON_ROOTS
                || block == Blocks.WARPED_FUNGUS || block == Blocks.CRIMSON_FUNGUS
                || block == Blocks.WEEPING_VINES || block == Blocks.TWISTING_VINES
                || block == Blocks.GLOW_LICHEN || block == Blocks.HANGING_ROOTS
                || block == Blocks.SPORE_BLOSSOM || block == Blocks.FLOWERING_AZALEA
                || block == Blocks.AZALEA || block == Blocks.MOSS_CARPET
                || block == Blocks.SEAGRASS || block == Blocks.TALL_SEAGRASS
                || block == Blocks.KELP || block == Blocks.KELP_PLANT || block == Blocks.SEA_PICKLE
                || block == Blocks.LILY_PAD || block == Blocks.SMALL_DRIPLEAF || block == Blocks.BIG_DRIPLEAF
                || isLiveCoral(block) || isDeadCoral(block)
                || isCropBlock(block)
                || block instanceof TallPlantBlock
                || block.toString().toLowerCase().contains("leaf_litter");
    }

    public static boolean hasExistingPlantOrDecoration(Block block) {
        if (block == Blocks.CACTUS || block == Blocks.CACTUS_FLOWER) {
            return true;
        }
        if (isPlantBlock(block)) {
            return true;
        }
        if (isPotted(block)) {
            return true;
        }
        if (isLiveCoral(block) || isDeadCoral(block)) {
            return true;
        }
        return block == Blocks.DEAD_BUSH
                || block == Blocks.SUGAR_CANE
                || block == Blocks.BAMBOO
                || block == Blocks.CHORUS_PLANT
                || block == Blocks.CHORUS_FLOWER
                || block == Blocks.NETHER_SPROUTS
                || block == Blocks.WARPED_ROOTS
                || block == Blocks.CRIMSON_ROOTS
                || block == Blocks.WARPED_FUNGUS
                || block == Blocks.CRIMSON_FUNGUS
                || block == Blocks.WEEPING_VINES
                || block == Blocks.TWISTING_VINES
                || block == Blocks.GLOW_LICHEN
                || block == Blocks.HANGING_ROOTS
                || block == Blocks.SPORE_BLOSSOM
                || block == Blocks.FLOWERING_AZALEA
                || block == Blocks.AZALEA
                || block == Blocks.MOSS_CARPET
                || block == Blocks.SEAGRASS
                || block == Blocks.TALL_SEAGRASS
                || block == Blocks.KELP
                || block == Blocks.KELP_PLANT
                || block == Blocks.SEA_PICKLE
                || block == Blocks.LILY_PAD
                || block == Blocks.SMALL_DRIPLEAF
                || block == Blocks.BIG_DRIPLEAF
                || block.toString().toLowerCase().contains("leaf_litter");
    }

    /**
     * 检查方块状态是否为植物或装饰物（用于水岸处理时跳过替换）。
     */
    public static boolean isPlantOrDecoration(BlockState state) {
        if (state.getBlock() instanceof PlantBlock
                || state.getBlock() instanceof TallPlantBlock
                || state.getBlock() instanceof FlowerBlock
                || state.getBlock() instanceof SaplingBlock
                || state.getBlock() instanceof MushroomBlock) {
            return true;
        }

        return state.getBlock() == Blocks.SHORT_GRASS
                || state.getBlock() == Blocks.TALL_GRASS
                || state.getBlock() == Blocks.FERN
                || state.getBlock() == Blocks.LARGE_FERN
                || state.getBlock() == Blocks.DEAD_BUSH
                || state.getBlock() == Blocks.SUGAR_CANE
                || state.getBlock() == Blocks.CACTUS
                || state.getBlock() == Blocks.CACTUS_FLOWER
                || state.getBlock() == Blocks.SEAGRASS
                || state.getBlock() == Blocks.KELP
                || state.getBlock() == Blocks.DANDELION
                || state.getBlock() == Blocks.POPPY
                || state.getBlock() == Blocks.BLUE_ORCHID
                || state.getBlock() == Blocks.ALLIUM
                || state.getBlock() == Blocks.AZURE_BLUET
                || state.getBlock() == Blocks.RED_TULIP
                || state.getBlock() == Blocks.ORANGE_TULIP
                || state.getBlock() == Blocks.WHITE_TULIP
                || state.getBlock() == Blocks.PINK_TULIP
                || state.getBlock() == Blocks.OXEYE_DAISY
                || state.getBlock() == Blocks.CORNFLOWER
                || state.getBlock() == Blocks.LILY_OF_THE_VALLEY
                || state.getBlock() == Blocks.SUNFLOWER
                || state.getBlock() == Blocks.LILAC
                || state.getBlock() == Blocks.ROSE_BUSH
                || state.getBlock() == Blocks.PEONY
                || state.getBlock() == Blocks.OAK_SAPLING
                || state.getBlock() == Blocks.SPRUCE_SAPLING
                || state.getBlock() == Blocks.BIRCH_SAPLING
                || state.getBlock() == Blocks.JUNGLE_SAPLING
                || state.getBlock() == Blocks.ACACIA_SAPLING
                || state.getBlock() == Blocks.DARK_OAK_SAPLING
                || state.getBlock() == Blocks.BROWN_MUSHROOM
                || state.getBlock() == Blocks.RED_MUSHROOM
                || state.getBlock() == Blocks.CRIMSON_FUNGUS
                || state.getBlock() == Blocks.WARPED_FUNGUS
                || state.getBlock() == Blocks.CRIMSON_ROOTS
                || state.getBlock() == Blocks.WARPED_ROOTS
                || state.getBlock() == Blocks.NETHER_SPROUTS
                || state.getBlock() == Blocks.WEEPING_VINES
                || state.getBlock() == Blocks.TWISTING_VINES
                || state.getBlock() == Blocks.GLOW_LICHEN
                || state.getBlock() == Blocks.MOSS_CARPET
                || state.getBlock() == Blocks.SMALL_DRIPLEAF
                || state.getBlock() == Blocks.BIG_DRIPLEAF
                || state.getBlock() == Blocks.SPORE_BLOSSOM
                || state.getBlock() == Blocks.AZALEA
                || state.getBlock() == Blocks.FLOWERING_AZALEA
                || state.getBlock() == Blocks.PINK_PETALS
                || state.getBlock() == Blocks.TORCHFLOWER
                || state.getBlock() == Blocks.PITCHER_PLANT
                || state.getBlock() == Blocks.CHERRY_SAPLING
                || state.getBlock() == Blocks.BAMBOO
                || state.getBlock() == Blocks.BAMBOO_SAPLING
                || state.getBlock() == Blocks.CHORUS_PLANT
                || state.getBlock() == Blocks.CHORUS_FLOWER
                || state.getBlock() == Blocks.NETHER_WART
                || state.getBlock() == Blocks.SHROOMLIGHT
                || state.getBlock() == Blocks.SOUL_FIRE
                || state.getBlock() == Blocks.SOUL_TORCH
                || state.getBlock() == Blocks.SOUL_WALL_TORCH
                || state.getBlock() == Blocks.SOUL_LANTERN
                || state.getBlock() == Blocks.SOUL_CAMPFIRE
                || state.getBlock() == Blocks.TORCH
                || state.getBlock() == Blocks.WALL_TORCH
                || state.getBlock() == Blocks.LANTERN
                || state.getBlock() == Blocks.CAMPFIRE
                || state.getBlock() == Blocks.FIRE
                || state.getBlock() == Blocks.LAVA
                || state.getBlock() == Blocks.WATER
                || state.getBlock() == Blocks.BUBBLE_COLUMN
                || state.getBlock() == Blocks.SEA_PICKLE
                || state.getBlock() == Blocks.TUBE_CORAL
                || state.getBlock() == Blocks.BRAIN_CORAL
                || state.getBlock() == Blocks.BUBBLE_CORAL
                || state.getBlock() == Blocks.FIRE_CORAL
                || state.getBlock() == Blocks.HORN_CORAL
                || state.getBlock() == Blocks.TUBE_CORAL_FAN
                || state.getBlock() == Blocks.BRAIN_CORAL_FAN
                || state.getBlock() == Blocks.BUBBLE_CORAL_FAN
                || state.getBlock() == Blocks.FIRE_CORAL_FAN
                || state.getBlock() == Blocks.HORN_CORAL_FAN
                || state.getBlock() == Blocks.TUBE_CORAL_WALL_FAN
                || state.getBlock() == Blocks.BRAIN_CORAL_WALL_FAN
                || state.getBlock() == Blocks.BUBBLE_CORAL_WALL_FAN
                || state.getBlock() == Blocks.FIRE_CORAL_WALL_FAN
                || state.getBlock() == Blocks.HORN_CORAL_WALL_FAN;
    }
}
