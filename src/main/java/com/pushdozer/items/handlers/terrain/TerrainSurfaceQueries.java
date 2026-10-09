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
     * Falls back to a full-column scan when heightmap data does not match placed blocks.
     */
    public static int resolveReferenceSurfaceY(World world, int x, int z) {
        int surfaceY = world.getTopY(Heightmap.Type.WORLD_SURFACE, x, z);
        BlockPos surfacePos = new BlockPos(x, surfaceY, z);
        BlockState topState = world.getBlockState(surfacePos);
        if (!topState.getFluidState().isEmpty()) {
            int oceanFloorY = world.getTopY(Heightmap.Type.OCEAN_FLOOR, x, z);
            BlockPos solid = findColumnSurfaceBelow(world, x, oceanFloorY, z);
            return solid != null ? solid.getY() : oceanFloorY;
        }
        BlockPos solid = findColumnSurfaceBelow(world, x, surfaceY, z);
        if (solid != null) {
            return solid.getY();
        }
        BlockPos scanned = findColumnSurface(world, x, z);
        return scanned != null ? scanned.getY() : surfaceY;
    }

    private static BlockPos findColumnSurfaceBelow(World world, int x, int startY, int z) {
        BlockPos solid = findSolidBelow(world, new BlockPos(x, startY, z));
        if (solid != null) {
            return solid;
        }
        return findColumnSurface(world, x, z);
    }

    private static BlockPos findColumnSurface(World world, int x, int z) {
        BlockPos.Mutable cursor = new BlockPos.Mutable(x, world.getTopY(Heightmap.Type.MOTION_BLOCKING, x, z), z);
        while (cursor.getY() >= world.getBottomY()) {
            BlockState state = world.getBlockState(cursor);
            if (!state.isAir() && !isWater(world, cursor) && !isIgnoredBlock(state)) {
                return cursor.toImmutable();
            }
            cursor.move(0, -1, 0);
        }
        return null;
    }

    /**
     * Returns whether {@code groundY} is within {@code maxBelowSurfaceDepth} blocks below the
     * column reference surface. Depth {@code 0} allows only the top surface block itself.
     * When the reference surface cannot be determined, returns {@code false} (fail closed).
     */
    public static boolean isWithinSurfaceDepth(World world, int x, int z, int groundY, int maxBelowSurfaceDepth) {
        if (world == null) {
            return false;
        }
        if (maxBelowSurfaceDepth < 0) {
            return true;
        }
        int referenceY = resolveReferenceSurfaceY(world, x, z);
        BlockState referenceState = world.getBlockState(new BlockPos(x, referenceY, z));
        if (referenceState.isAir() || !referenceState.getFluidState().isEmpty()) {
            return false;
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
