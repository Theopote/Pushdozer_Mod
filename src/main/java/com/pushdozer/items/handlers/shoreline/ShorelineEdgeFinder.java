package com.pushdozer.items.handlers.shoreline;

import com.pushdozer.config.PushdozerConfig;
import com.pushdozer.items.handlers.vegetation.PlantBlockClassifier;
import com.pushdozer.shapes.GeometryShape;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ShorelineEdgeFinder {
    private final PushdozerConfig config;

    public ShorelineEdgeFinder(PushdozerConfig config) {
        this.config = config;
    }

    public Set<BlockPos> collectWaterBlocks(World world, GeometryShape shape) {
        Set<BlockPos> waterBlocks = new HashSet<>();
        for (BlockPos pos : shape.getBlockPositions()) {
            if (world.getFluidState(pos).isIn(FluidTags.WATER)) {
                waterBlocks.add(pos);
            }
        }
        return waterBlocks;
    }

    public Set<BlockPos> findEdges(World world, GeometryShape shape) {
        Set<BlockPos> edges = new HashSet<>();
        Set<BlockPos> waterBlocks = collectWaterBlocks(world, shape);
        Set<BlockPos> checkedPositions = new HashSet<>();

        for (BlockPos waterPos : waterBlocks) {
            for (Direction dir : Direction.Type.HORIZONTAL) {
                BlockPos neighborPos = waterPos.offset(dir);

                if (!checkedPositions.contains(neighborPos)) {
                    checkedPositions.add(neighborPos);

                    if (!waterBlocks.contains(neighborPos)
                        && !world.getFluidState(neighborPos).isIn(FluidTags.WATER)
                        && isReplaceableLandBlock(world, neighborPos, world.getBlockState(neighborPos))) {
                        edges.add(neighborPos);
                    }
                }
            }

            for (Direction dir : Direction.Type.VERTICAL) {
                BlockPos neighborPos = waterPos.offset(dir);

                if (!checkedPositions.contains(neighborPos)) {
                    checkedPositions.add(neighborPos);

                    if (!waterBlocks.contains(neighborPos)
                        && !world.getFluidState(neighborPos).isIn(FluidTags.WATER)
                        && isReplaceableLandBlock(world, neighborPos, world.getBlockState(neighborPos))) {
                        edges.add(neighborPos);
                    }
                }
            }
        }

        return edges;
    }

    public boolean isReplaceableLandBlock(World world, BlockPos pos, BlockState state) {
        if (state.getFluidState().isIn(FluidTags.WATER) || state.isAir()) {
            return false;
        }

        if (state.hasBlockEntity()) {
            return false;
        }

        if (PlantBlockClassifier.isPlantOrDecoration(state)) {
            return false;
        }

        if (!isSurfaceBlock(world, pos)) {
            return false;
        }

        List<Block> customBlocks = config.getCustomShorelineBlockList();
        if (!customBlocks.isEmpty() && customBlocks.contains(state.getBlock())) {
            return true;
        }

        return isNaturalShorelineBlock(state);
    }

    static boolean isNaturalShorelineBlock(BlockState state) {
        Block block = state.getBlock();
        if (block == Blocks.SUSPICIOUS_SAND || block == Blocks.SUSPICIOUS_GRAVEL) {
            return false;
        }
        if (state.isIn(BlockTags.LOGS)) {
            return false;
        }

        return state.isIn(BlockTags.DIRT)
            || state.isIn(BlockTags.SAND)
            || block == Blocks.GRASS_BLOCK
            || block == Blocks.STONE
            || block == Blocks.COBBLESTONE
            || block == Blocks.GRAVEL
            || block == Blocks.SANDSTONE
            || block == Blocks.SMOOTH_SANDSTONE
            || block == Blocks.RED_SANDSTONE
            || block == Blocks.DIRT_PATH
            || block == Blocks.COARSE_DIRT
            || block == Blocks.PODZOL
            || block == Blocks.MYCELIUM
            || block == Blocks.SNOW_BLOCK
            || block == Blocks.ICE
            || block == Blocks.PACKED_ICE
            || block == Blocks.ANDESITE
            || block == Blocks.DIORITE
            || block == Blocks.GRANITE
            || block == Blocks.DEEPSLATE
            || block == Blocks.TUFF
            || block == Blocks.CALCITE
            || block == Blocks.SMOOTH_BASALT
            || block == Blocks.ROOTED_DIRT
            || block == Blocks.MOSS_BLOCK
            || block == Blocks.CLAY
            || block == Blocks.MOSS_CARPET
            || block == Blocks.MUD;
    }

    public boolean isSurfaceBlock(World world, BlockPos pos) {
        BlockPos above = pos.up();
        BlockState aboveState = world.getBlockState(above);
        if (aboveState.isAir() || aboveState.getFluidState().isIn(FluidTags.WATER)
            || PlantBlockClassifier.isPlantOrDecoration(aboveState)) {
            return true;
        }

        for (Direction dir : Direction.Type.HORIZONTAL) {
            BlockPos neighborPos = pos.offset(dir);
            BlockState neighborState = world.getBlockState(neighborPos);
            if (neighborState.isAir() || neighborState.getFluidState().isIn(FluidTags.WATER)
                || PlantBlockClassifier.isPlantOrDecoration(neighborState)) {
                return true;
            }
        }

        BlockPos below = pos.down();
        BlockState belowState = world.getBlockState(below);
        return belowState.isAir() || belowState.getFluidState().isIn(FluidTags.WATER);
    }

    public boolean isChunkLoaded(World world, BlockPos pos) {
        return world.isChunkLoaded(pos.getX() >> 4, pos.getZ() >> 4);
    }
}
