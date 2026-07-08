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
    public Set<BlockPos> findEdges(World world, GeometryShape shape) {
        Set<BlockPos> edges = new HashSet<>();
        Set<BlockPos> waterBlocks = new HashSet<>();
        Set<BlockPos> checkedPositions = new HashSet<>();

        // 首先收集笔刷范围内的所有水体
        for (BlockPos pos : shape.getBlockPositions()) {
            if (world.getFluidState(pos).isIn(FluidTags.WATER)) {
                waterBlocks.add(pos);
            }
        }

        // 处理水平和垂直方向的水岸边缘
        for (BlockPos waterPos : waterBlocks) {
            // 检查水平方向（XZ平面）
            for (Direction dir : Direction.Type.HORIZONTAL) {
                BlockPos neighborPos = waterPos.offset(dir);
                
                if (!checkedPositions.contains(neighborPos)) {
                    checkedPositions.add(neighborPos);
                    
                    if (!waterBlocks.contains(neighborPos) && 
                        !world.getFluidState(neighborPos).isIn(FluidTags.WATER) &&
                        isReplaceableLandBlock(world, neighborPos, world.getBlockState(neighborPos))) {
                        edges.add(neighborPos);
                    }
                }
            }
            
            // 检查垂直方向（Y轴）- 处理水岸的垂直边缘
            for (Direction dir : Direction.Type.VERTICAL) {
                BlockPos neighborPos = waterPos.offset(dir);
                
                if (!checkedPositions.contains(neighborPos)) {
                    checkedPositions.add(neighborPos);
                    
                    if (!waterBlocks.contains(neighborPos) && 
                        !world.getFluidState(neighborPos).isIn(FluidTags.WATER) &&
                        isReplaceableLandBlock(world, neighborPos, world.getBlockState(neighborPos))) {
                        edges.add(neighborPos);
                    }
                }
            }
        }

        return edges;
    }

    public boolean isReplaceableLandBlock(World world, BlockPos pos, BlockState state) {
        // 检查是否为水方块或空气方块
        if (state.getFluidState().isIn(FluidTags.WATER) || state.isAir()) {
            return false;
        }
        
        // 修复：忽略植物等装饰物，直接跳过
        if (PlantBlockClassifier.isPlantOrDecoration(state)) {
            return false;
        }
        
        // 优化：只替换表面方块，跳过内部方块
        if (!isSurfaceBlock(world, pos)) {
            return false;
        }
        
        // 修复：检查是否为自定义水岸方块
        List<Block> customBlocks = config.getCustomShorelineBlockList();
        if (!customBlocks.isEmpty() && customBlocks.contains(state.getBlock())) {
            return true; // 自定义水岸方块可以被替换
        }
        
        // 优化：使用isReplaceable()和标签检查，简化逻辑
        return state.isReplaceable() || 
               state.isIn(BlockTags.DIRT) || 
               state.isIn(BlockTags.SAND) || 
               state.isIn(BlockTags.LOGS) ||
               state.getBlock() == Blocks.GRASS_BLOCK ||
               state.getBlock() == Blocks.STONE ||
               state.getBlock() == Blocks.COBBLESTONE ||
               state.getBlock() == Blocks.GRAVEL ||
               state.getBlock() == Blocks.SANDSTONE ||
               state.getBlock() == Blocks.SMOOTH_SANDSTONE ||
               state.getBlock() == Blocks.RED_SANDSTONE ||
               state.getBlock() == Blocks.DIRT_PATH ||
               state.getBlock() == Blocks.COARSE_DIRT ||
               state.getBlock() == Blocks.PODZOL ||
               state.getBlock() == Blocks.MYCELIUM ||
               state.getBlock() == Blocks.SNOW_BLOCK ||
               state.getBlock() == Blocks.ICE ||
               state.getBlock() == Blocks.PACKED_ICE ||
               state.getBlock() == Blocks.ANDESITE ||
               state.getBlock() == Blocks.DIORITE ||
               state.getBlock() == Blocks.GRANITE ||
               state.getBlock() == Blocks.DEEPSLATE ||
               state.getBlock() == Blocks.TUFF ||
               state.getBlock() == Blocks.CALCITE ||
               state.getBlock() == Blocks.SMOOTH_BASALT ||
               state.getBlock() == Blocks.ROOTED_DIRT ||
               state.getBlock() == Blocks.MOSS_BLOCK ||
               state.getBlock() == Blocks.SUSPICIOUS_SAND ||
               state.getBlock() == Blocks.SUSPICIOUS_GRAVEL ||
               state.getBlock() == Blocks.CLAY ||
               state.getBlock() == Blocks.MOSS_CARPET ||
               state.getBlock() == Blocks.MUD; // 修复：添加MUD方块
    }

    public boolean isSurfaceBlock(World world, BlockPos pos) {
        // 检查上方是否为空气、水或植物
        BlockPos above = pos.up();
        BlockState aboveState = world.getBlockState(above);
        if (aboveState.isAir() || aboveState.getFluidState().isIn(FluidTags.WATER) || PlantBlockClassifier.isPlantOrDecoration(aboveState)) {
            return true;
        }
        
        // 检查水平方向是否有空气、水或植物
        for (Direction dir : Direction.Type.HORIZONTAL) {
            BlockPos neighborPos = pos.offset(dir);
            BlockState neighborState = world.getBlockState(neighborPos);
            if (neighborState.isAir() || neighborState.getFluidState().isIn(FluidTags.WATER) || PlantBlockClassifier.isPlantOrDecoration(neighborState)) {
                return true;
            }
        }
        
        // 检查下方是否为空气或水（处理悬空方块）
        BlockPos below = pos.down();
        BlockState belowState = world.getBlockState(below);
        return belowState.isAir() || belowState.getFluidState().isIn(FluidTags.WATER);
        
        // 如果周围都是实体方块（非空气、非水、非植物），则不是表面方块
    }

    /**
     * 检查chunk是否已加载
     * 防止在未加载的chunk中放置方块
     * 
     * @param world 世界实例
     * @param pos 要检查的位置
     * @return 如果chunk已加载返回true，否则返回false
     */
    public boolean isChunkLoaded(World world, BlockPos pos) {
        return world.isChunkLoaded(pos.getX() >> 4, pos.getZ() >> 4);
    }
}

