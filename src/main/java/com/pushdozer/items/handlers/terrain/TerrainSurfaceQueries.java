package com.pushdozer.items.handlers.terrain;

import net.minecraft.block.BambooBlock;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.PlantBlock;
import net.minecraft.block.TallPlantBlock;
import net.minecraft.fluid.FluidState;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.Heightmap;
import net.minecraft.world.World;

import java.util.Set;

/**
 * Shared surface column queries used by terrain tools and surface convert.
 */
public final class TerrainSurfaceQueries {

    private static final Set<net.minecraft.block.Block> IGNORED_BLOCKS = Set.of(
        Blocks.VINE, Blocks.SNOW, Blocks.BROWN_MUSHROOM, Blocks.RED_MUSHROOM
    );

    private TerrainSurfaceQueries() {
    }

    public static boolean isWater(World world, BlockPos pos) {
        FluidState fluidState = world.getFluidState(pos);
        return !fluidState.isEmpty() && fluidState.isStill();
    }

    public static boolean isIgnoredBlock(BlockState state) {
        if (state.isIn(BlockTags.LOGS)
            || state.isIn(BlockTags.LEAVES)
            || state.isIn(BlockTags.FLOWERS)
            || state.isIn(BlockTags.SAPLINGS)
            || state.isIn(BlockTags.CROPS)
            || state.isIn(BlockTags.SMALL_FLOWERS)) {
            return true;
        }
        if (state.getBlock() instanceof BambooBlock
            || state.getBlock() instanceof PlantBlock
            || state.getBlock() instanceof TallPlantBlock) {
            return true;
        }
        return IGNORED_BLOCKS.contains(state.getBlock())
            || state.isOf(Blocks.SHORT_GRASS)
            || state.isOf(Blocks.TALL_GRASS)
            || state.isOf(Blocks.FERN)
            || state.isOf(Blocks.LARGE_FERN);
    }

    public static BlockPos findGroundBlock(World world, BlockPos initialPos) {
        BlockPos.Mutable currentPos = new BlockPos.Mutable(initialPos.getX(), initialPos.getY(), initialPos.getZ());

        if (world.isAir(currentPos) || isWater(world, currentPos) || isIgnoredBlock(world.getBlockState(currentPos))) {
            // Starting from air, search downward
        } else {
            while (currentPos.getY() < world.getHeight()
                && !world.isAir(currentPos) && !isWater(world, currentPos)
                && !isIgnoredBlock(world.getBlockState(currentPos))) {
                currentPos.move(0, 1, 0);
            }
        }

        while (currentPos.getY() >= world.getBottomY()
            && (world.isAir(currentPos) || isWater(world, currentPos)
                || isIgnoredBlock(world.getBlockState(currentPos)))) {
            currentPos.move(0, -1, 0);
        }

        if (currentPos.getY() < world.getBottomY()) {
            return null;
        }
        return currentPos.toImmutable();
    }

    /**
     * Reference surface Y for heightmap guard (world surface or ocean floor when flooded).
     */
    public static int resolveReferenceSurfaceY(World world, int x, int z) {
        int surfaceY = world.getTopY(Heightmap.Type.WORLD_SURFACE, x, z);
        BlockState topState = world.getBlockState(new BlockPos(x, surfaceY, z));
        if (!topState.getFluidState().isEmpty()) {
            return world.getTopY(Heightmap.Type.OCEAN_FLOOR, x, z);
        }
        return surfaceY;
    }

    public static boolean isWithinSurfaceDepth(World world, int x, int z, int groundY, int maxBelowSurfaceDepth) {
        if (maxBelowSurfaceDepth <= 0) {
            return true;
        }
        int referenceY = resolveReferenceSurfaceY(world, x, z);
        BlockState referenceState = world.getBlockState(new BlockPos(x, referenceY, z));
        if (referenceState.isAir() || !referenceState.getFluidState().isEmpty()) {
            return true;
        }
        if (Math.abs(referenceY - groundY) > 64) {
            return true;
        }
        return groundY >= referenceY - maxBelowSurfaceDepth;
    }

    /**
     * Walk downward from a position without the upward climb used by {@link #findGroundBlock}.
     */
    public static BlockPos findSolidBelow(World world, BlockPos start) {
        BlockPos.Mutable cursor = start.mutableCopy();
        while (cursor.getY() >= world.getBottomY()) {
            BlockState state = world.getBlockState(cursor);
            if (!state.isAir() && !isWater(world, cursor) && !isIgnoredBlock(state)) {
                return cursor.toImmutable();
            }
            cursor.move(0, -1, 0);
        }
        return null;
    }
}
