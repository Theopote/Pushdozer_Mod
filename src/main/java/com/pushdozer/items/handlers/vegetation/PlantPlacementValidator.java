package com.pushdozer.items.handlers.vegetation;

import com.pushdozer.PushdozerMod;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.CoralWallFanBlock;
import net.minecraft.block.TallPlantBlock;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.state.property.Properties;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;

/**
 * 植物种植位置校验，供海岸线处理与批量种植共享。
 */
public final class PlantPlacementValidator {

    private PlantPlacementValidator() {
    }

    public static boolean isAdjacentToWater(World world, BlockPos pos) {
        for (Direction direction : Direction.values()) {
            BlockPos adjacentPos = pos.offset(direction);
            BlockState adjacentState = world.getBlockState(adjacentPos);
            if (adjacentState.getFluidState().isIn(FluidTags.WATER)) {
                return true;
            }
        }
        return false;
    }

    public static boolean isSolidBlock(World world, BlockPos pos, BlockState state) {
        if (state.getFluidState().isIn(FluidTags.WATER)) {
            return false;
        }
        return !state.getCollisionShape(world, pos).isEmpty();
    }

    public static boolean hasEnoughSpaceForPlant(World world, BlockPos pos, int requiredHeight) {
        for (int i = 1; i < requiredHeight; i++) {
            BlockPos checkPos = pos.up(i);
            BlockState checkState = world.getBlockState(checkPos);
            if (!checkState.isAir()
                    && !checkState.getFluidState().isIn(FluidTags.WATER)
                    && !PlantBlockClassifier.isPlantOrDecoration(checkState)) {
                return false;
            }
        }
        return true;
    }

    /**
     * 自定义种植时的动态可种规则。
     */
    public static boolean canPlantCustomBlockAt(World world, BlockPos pos, Block block) {
        BlockState targetState = world.getBlockState(pos);
        BlockState below = world.getBlockState(pos.down());
        BlockState state = block.getDefaultState();

        if (block == Blocks.LILY_PAD) {
            boolean airHere = targetState.isAir();
            boolean waterBelow = below.getFluidState().isIn(FluidTags.WATER);
            return airHere && waterBelow && state.canPlaceAt(world, pos);
        }

        if (block instanceof TallPlantBlock) {
            if (!state.contains(Properties.DOUBLE_BLOCK_HALF)) {
                return false;
            }
            BlockPos upperPos = pos.up();
            BlockState upperCurrent = world.getBlockState(upperPos);
            if (!(upperCurrent.isAir() || upperCurrent.isReplaceable())) {
                return false;
            }
        }

        if (PlantBlockClassifier.isPotted(block)) {
            boolean spotFree = targetState.isAir() || targetState.isReplaceable();
            boolean notInWater = !targetState.getFluidState().isIn(FluidTags.WATER);
            boolean solidBelow = below.isSolidBlock(world, pos.down());
            boolean notPlantBelow = !PlantBlockClassifier.isPlantBlock(below.getBlock())
                    && !PlantBlockClassifier.isPotted(below.getBlock());
            boolean potCanPlace = Blocks.FLOWER_POT.getDefaultState().canPlaceAt(world, pos);
            return spotFree && notInWater && solidBelow && notPlantBelow && potCanPlace && state.canPlaceAt(world, pos);
        }

        boolean isCrop = PlantBlockClassifier.isCropBlock(block) || state.contains(Properties.AGE_7);
        if (isCrop) {
            return below.isOf(Blocks.FARMLAND) && (targetState.isAir() || targetState.isReplaceable());
        }

        boolean inWater = targetState.getFluidState().isIn(FluidTags.WATER);
        boolean adjacentToWater = isAdjacentToWater(world, pos);

        if (PlantBlockClassifier.isLiveCoral(block)) {
            BlockPos upperPos = pos.up();
            BlockState upperState = world.getBlockState(upperPos);
            boolean upperInWater = upperState.getFluidState().isIn(FluidTags.WATER);
            boolean validBase = below.isIn(BlockTags.SAND)
                    || below.isIn(BlockTags.CORAL_BLOCKS)
                    || below.isIn(BlockTags.BASE_STONE_OVERWORLD)
                    || below.isIn(BlockTags.BASE_STONE_NETHER)
                    || below.isOf(Blocks.END_STONE)
                    || below.isOf(Blocks.GRAVEL)
                    || below.isOf(Blocks.CLAY);

            if (block instanceof CoralWallFanBlock) {
                boolean hasSolidSide = false;
                for (Direction direction : Direction.Type.HORIZONTAL) {
                    BlockPos sidePos = pos.offset(direction);
                    BlockState sideState = world.getBlockState(sidePos);
                    if (sideState.isSolidBlock(world, sidePos)) {
                        hasSolidSide = true;
                        break;
                    }
                }
                return (inWater || adjacentToWater) && upperInWater && hasSolidSide;
            }
            return (inWater || adjacentToWater) && upperInWater && validBase;
        }

        if (block == Blocks.SMALL_DRIPLEAF) {
            boolean validBase = below.isIn(BlockTags.SMALL_DRIPLEAF_PLACEABLE)
                    || below.isOf(Blocks.CLAY)
                    || below.isOf(Blocks.MOSS_BLOCK)
                    || below.isOf(Blocks.ROOTED_DIRT)
                    || below.isOf(Blocks.MYCELIUM)
                    || below.isOf(Blocks.PODZOL)
                    || (inWater && (below.isOf(Blocks.DIRT)
                    || below.isOf(Blocks.COARSE_DIRT)
                    || below.isOf(Blocks.GRASS_BLOCK)
                    || below.isOf(Blocks.FARMLAND)));
            BlockPos upperPos = pos.up();
            BlockState upperState = world.getBlockState(upperPos);
            boolean upperReplaceable = upperState.isAir() || upperState.isReplaceable();
            return validBase && upperReplaceable;
        }

        if (block == Blocks.BIG_DRIPLEAF) {
            boolean validBase = below.isOf(Blocks.CLAY)
                    || below.isOf(Blocks.MOSS_BLOCK)
                    || below.isOf(Blocks.GRASS_BLOCK)
                    || below.isOf(Blocks.MYCELIUM)
                    || below.isOf(Blocks.PODZOL)
                    || below.isOf(Blocks.DIRT)
                    || below.isOf(Blocks.ROOTED_DIRT)
                    || below.isOf(Blocks.COARSE_DIRT)
                    || below.isOf(Blocks.FARMLAND)
                    || below.isOf(Blocks.MUD)
                    || below.isOf(Blocks.MUDDY_MANGROVE_ROOTS);
            BlockPos upperPos = pos.up();
            BlockState upperState = world.getBlockState(upperPos);
            boolean upperReplaceable = upperState.isAir() || upperState.isReplaceable();
            return validBase && upperReplaceable;
        }

        if (PlantBlockClassifier.isAquatic(block)) {
            return inWater && state.canPlaceAt(world, pos);
        }

        if (PlantBlockClassifier.isDeadCoral(block)) {
            boolean spotFree = targetState.isAir() || targetState.isReplaceable();
            boolean solidBelow = below.isSolidBlock(world, pos.down());
            return spotFree && solidBelow;
        }

        if (block.toString().toLowerCase().contains("leaf_litter")) {
            boolean spotFree = targetState.isAir() || targetState.isReplaceable();
            boolean validBase = below.isSolidBlock(world, pos.down());
            return spotFree && validBase;
        }

        boolean validBase = below.isIn(BlockTags.DIRT)
                || below.isOf(Blocks.GRASS_BLOCK)
                || below.isIn(BlockTags.SAND)
                || below.isIn(BlockTags.SNOW)
                || below.isOf(Blocks.DIRT_PATH)
                || below.isOf(Blocks.MOSS_BLOCK)
                || below.isOf(Blocks.CLAY);
        if (!validBase) {
            PushdozerMod.LOGGER.debug("Custom plant {} cannot grow on invalid base block {} at pos {}",
                    block, below.getBlock().toString(), pos);
            return false;
        }

        boolean spotFree = targetState.isAir() || targetState.isReplaceable();
        boolean canPlace = state.canPlaceAt(world, pos);
        if (!spotFree) {
            PushdozerMod.LOGGER.debug("Custom plant {} cannot grow at pos {}: spot not free (current state: {})",
                    block, pos, targetState.getBlock().toString());
            return false;
        }
        if (!canPlace) {
            PushdozerMod.LOGGER.debug("Custom plant {} cannot be placed at pos {}: canPlaceAt returned false",
                    block, pos);
            return false;
        }

        PushdozerMod.LOGGER.debug("Custom plant {} can grow at pos {} on base block {}",
                block, pos, below.getBlock().toString());
        return true;
    }
}
