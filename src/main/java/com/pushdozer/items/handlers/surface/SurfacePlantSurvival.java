package com.pushdozer.items.handlers.surface;

import com.pushdozer.items.handlers.vegetation.PlantBlockClassifier;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.TallPlantBlock;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.state.property.Properties;
import net.minecraft.util.math.BlockPos;

/**
 * Determines whether vegetation above a converted surface can remain.
 */
public final class SurfacePlantSurvival {

    private SurfacePlantSurvival() {
    }

    public static boolean canSurviveOn(BlockState plantState, BlockState newSurfaceState) {
        Block plantBlock = plantState.getBlock();
        if (!PlantBlockClassifier.isPlantOrDecoration(plantState)
            && !PlantBlockClassifier.hasExistingPlantOrDecoration(plantBlock)) {
            return true;
        }

        if (plantBlock instanceof TallPlantBlock) {
            if (plantState.contains(Properties.DOUBLE_BLOCK_HALF)
                && plantState.get(Properties.DOUBLE_BLOCK_HALF) == net.minecraft.block.enums.DoubleBlockHalf.UPPER) {
                return true;
            }
        }

        if (plantBlock == Blocks.LILY_PAD) {
            return false;
        }

        if (PlantBlockClassifier.isAquatic(plantBlock) || PlantBlockClassifier.isLiveCoral(plantBlock)) {
            return false;
        }

        if (PlantBlockClassifier.isCropBlock(plantBlock) || plantState.contains(Properties.AGE_7)) {
            return newSurfaceState.isOf(Blocks.FARMLAND);
        }

        if (plantBlock == Blocks.SUGAR_CANE) {
            return newSurfaceState.isOf(Blocks.GRASS_BLOCK)
                || newSurfaceState.isIn(BlockTags.SAND)
                || newSurfaceState.isOf(Blocks.DIRT)
                || newSurfaceState.isOf(Blocks.RED_SAND);
        }

        if (plantBlock == Blocks.CACTUS || plantBlock == Blocks.CACTUS_FLOWER) {
            return newSurfaceState.isIn(BlockTags.SAND) || newSurfaceState.isOf(Blocks.RED_SAND);
        }

        if (plantBlock == Blocks.DEAD_BUSH) {
            return newSurfaceState.isIn(BlockTags.SAND) || newSurfaceState.isOf(Blocks.RED_SAND);
        }

        if (plantBlock == Blocks.BAMBOO || plantBlock == Blocks.BAMBOO_SAPLING) {
            return newSurfaceState.isOf(Blocks.GRASS_BLOCK)
                || newSurfaceState.isOf(Blocks.DIRT)
                || newSurfaceState.isOf(Blocks.COARSE_DIRT)
                || newSurfaceState.isOf(Blocks.PODZOL)
                || newSurfaceState.isOf(Blocks.MYCELIUM)
                || newSurfaceState.isOf(Blocks.MUD)
                || newSurfaceState.isOf(Blocks.MUDDY_MANGROVE_ROOTS);
        }

        return newSurfaceState.isIn(BlockTags.DIRT)
            || newSurfaceState.isOf(Blocks.GRASS_BLOCK)
            || newSurfaceState.isIn(BlockTags.SAND)
            || newSurfaceState.isIn(BlockTags.SNOW)
            || newSurfaceState.isOf(Blocks.DIRT_PATH)
            || newSurfaceState.isOf(Blocks.MOSS_BLOCK)
            || newSurfaceState.isOf(Blocks.CLAY)
            || newSurfaceState.isOf(Blocks.MYCELIUM)
            || newSurfaceState.isOf(Blocks.PODZOL);
    }

    public static BlockPos upperPartPos(BlockState plantState, BlockPos plantPos) {
        if (plantState.getBlock() instanceof TallPlantBlock
            && plantState.contains(Properties.DOUBLE_BLOCK_HALF)
            && plantState.get(Properties.DOUBLE_BLOCK_HALF) == net.minecraft.block.enums.DoubleBlockHalf.LOWER) {
            return plantPos.up();
        }
        return null;
    }
}
