package com.pushdozer.items.handlers;

import com.pushdozer.PushdozerMod;
import com.pushdozer.config.PushdozerConfig;
import com.pushdozer.shapes.GeometryShape;
import com.pushdozer.util.ShapeUtil;
import com.pushdozer.operations.UndoAction;

import net.minecraft.block.*;
import net.minecraft.block.LeavesBlock;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.registry.Registries;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import java.util.*;

/**
 * 表层转换模式处理器
 * 根据配置面板中的方块类型和配置比例，对minecraft地形表面的方块进行替换
 * 如果异常那就默认替换为草方块
 */
public class SurfaceConvertHandler {
    private final PushdozerConfig config;
    private static final Random RANDOM = new Random(); // 静态Random对象，避免重复创建
    private static final Set<Block> IGNORED_BLOCKS = Set.of(
        Blocks.OAK_LOG, Blocks.SPRUCE_LOG, Blocks.BIRCH_LOG, Blocks.JUNGLE_LOG, 
        Blocks.ACACIA_LOG, Blocks.DARK_OAK_LOG, Blocks.TALL_GRASS, Blocks.FERN, 
        Blocks.LARGE_FERN, Blocks.DANDELION, Blocks.POPPY, Blocks.BLUE_ORCHID, 
        Blocks.ALLIUM, Blocks.AZURE_BLUET, Blocks.RED_TULIP, Blocks.ORANGE_TULIP, 
        Blocks.WHITE_TULIP, Blocks.PINK_TULIP, Blocks.OXEYE_DAISY, Blocks.CORNFLOWER, 
        Blocks.LILY_OF_THE_VALLEY, Blocks.SUNFLOWER, Blocks.LILAC, Blocks.ROSE_BUSH, 
        Blocks.PEONY
    );

    public SurfaceConvertHandler(PushdozerConfig config) {
        this.config = config;
    }

    /**
     * 处理表层转换操作
     */
    public void handleSurfaceConvert(PlayerEntity player, World world) {
        if (world.isClient) return;

        BlockPos basePos = ShapeUtil.getTargetBlockPos(player, config);
        GeometryShape shape = ShapeUtil.createShape(player, config, basePos);

        if (shape == null) {
            return;
        }

        List<BlockPos> affectedPositions = new ArrayList<>();
        List<BlockState> originalStates = new ArrayList<>();
        List<BlockState> newStates = new ArrayList<>();

        // 执行表层转换
        convertSurface(world, shape, affectedPositions, originalStates, newStates);

        // 创建撤销操作
        if (!affectedPositions.isEmpty()) {
            UndoAction undoAction = new UndoAction(
                UndoAction.ActionType.SURFACE_CONVERT,
                affectedPositions,
                originalStates,
                newStates
            );
            PushdozerMod.pushUndoAction(player, undoAction);
        }
    }

    /**
     * 表层转换处理
     */
    private void convertSurface(World world, GeometryShape shape, 
                              List<BlockPos> affectedPositions,
                              List<BlockState> originalStates, 
                              List<BlockState> newStates) {
        // 按XZ分组，收集每个柱子的所有Y
        Map<BlockPos, List<Integer>> columnYMap = new HashMap<>();
        for (BlockPos pos : shape.getBlockPositions()) {
            BlockPos columnPos = new BlockPos(pos.getX(), 0, pos.getZ());
            columnYMap.computeIfAbsent(columnPos, k -> new ArrayList<>()).add(pos.getY());
        }

        // 记录被修改的地表位置，用于后续移除悬空植物
        List<BlockPos> newlyPlacedSurfacePositions = new ArrayList<>();

        // 对每个柱子，从高到低扫描，找到第一个"非空气、非水、非被忽略的方块、非树叶"
        for (Map.Entry<BlockPos, List<Integer>> entry : columnYMap.entrySet()) {
            BlockPos columnPos = entry.getKey();
            List<Integer> yList = entry.getValue();
            yList.sort(Collections.reverseOrder()); // 从高到低
            for (int y : yList) {
                BlockPos surfacePos = new BlockPos(columnPos.getX(), y, columnPos.getZ());
                BlockState state = world.getBlockState(surfacePos);
                if (!world.isAir(surfacePos) && !isWater(world, surfacePos) && 
                    !isIgnoredBlock(state) && !isLeafBlock(state)) {
                    // 找到真正的地表
                    Block targetBlock = selectTargetBlock();
                    BlockState targetState = targetBlock.getDefaultState();
                    if (state.getBlock() != targetBlock) {
                        affectedPositions.add(surfacePos);
                        originalStates.add(state);
                        newStates.add(targetState);
                        world.setBlockState(surfacePos, targetState, Block.NOTIFY_ALL);
                        newlyPlacedSurfacePositions.add(surfacePos); // 记录被修改的地表位置
                    }
                    break; // 只处理第一个地表
                }
                // 如果是空气/水/被忽略的方块/树叶，继续向下扫描
            }
        }

        // 处理悬空植物 - 只检查被修改过的地表上方的方块
        removeFloatingVegetation(world, newlyPlacedSurfacePositions, affectedPositions, originalStates, newStates);
    }

    /**
     * 根据配置比例选择目标方块
     */
    private Block selectTargetBlock() {
        List<PushdozerConfig.SurfaceConvertBlock> surfaceBlocks = config.getSurfaceConvertBlocks();
        
        if (surfaceBlocks.isEmpty()) {
            // 如果没有配置方块，默认使用草方块
            return Blocks.GRASS_BLOCK;
        }

        // 计算总百分比
        float totalPercentage = 0f;
        for (PushdozerConfig.SurfaceConvertBlock block : surfaceBlocks) {
            totalPercentage += block.getPercentage();
        }

        if (totalPercentage <= 0f) {
            // 如果总百分比为0，默认使用草方块
            return Blocks.GRASS_BLOCK;
        }

        // 生成随机数来决定使用哪个方块 - 使用静态Random对象
        float random = RANDOM.nextFloat() * totalPercentage;
        float currentSum = 0f;

        for (PushdozerConfig.SurfaceConvertBlock surfaceBlock : surfaceBlocks) {
            currentSum += surfaceBlock.getPercentage();
            if (random <= currentSum) {
                try {
                    // 尝试根据配置的方块ID获取方块
                    String blockId = surfaceBlock.getBlockId();
                    Block block = Registries.BLOCK.get(net.minecraft.util.Identifier.of(blockId));
                    if (block != Blocks.AIR) {
                        return block;
                    }
                } catch (Exception e) {
                    // 如果出现异常，继续尝试下一个方块
                }
            }
        }

        // 如果所有方块都无效，默认使用草方块
        return Blocks.GRASS_BLOCK;
    }

    /**
     * 检查是否为水 - 改进判断逻辑，包括流动的水
     */
    private boolean isWater(World world, BlockPos pos) {
        return !world.getFluidState(pos).isEmpty() && 
               world.getFluidState(pos).getFluid().matchesType(net.minecraft.fluid.Fluids.WATER);
    }

    /**
     * 检查是否为忽略的方块
     */
    private boolean isIgnoredBlock(BlockState state) {
        return IGNORED_BLOCKS.contains(state.getBlock());
    }

    /**
     * 检查是否为树叶
     */
    private boolean isLeafBlock(BlockState state) {
        return state.getBlock() instanceof LeavesBlock;
    }

    /**
     * 移除悬空植物 - 优化版本，只检查被修改过的地表上方的方块
     */
    private void removeFloatingVegetation(World world, List<BlockPos> surfacePositions,
                                        List<BlockPos> affectedPositions,
                                        List<BlockState> originalStates,
                                        List<BlockState> newStates) {
        // 定义真正的悬空植物（不包括树叶）
        Set<Block> floatingPlants = Set.of(
            Blocks.TALL_GRASS, Blocks.FERN, Blocks.LARGE_FERN, Blocks.DANDELION, Blocks.POPPY,
            Blocks.BLUE_ORCHID, Blocks.ALLIUM, Blocks.AZURE_BLUET, Blocks.RED_TULIP, Blocks.ORANGE_TULIP,
            Blocks.WHITE_TULIP, Blocks.PINK_TULIP, Blocks.OXEYE_DAISY, Blocks.CORNFLOWER, Blocks.LILY_OF_THE_VALLEY,
            Blocks.SUNFLOWER, Blocks.LILAC, Blocks.ROSE_BUSH, Blocks.PEONY
        );
        
        // 只检查被修改过的地表上方的方块
        for (BlockPos surfacePos : surfacePositions) {
            BlockPos pos = surfacePos.up(); // 检查上方
            BlockState state = world.getBlockState(pos);
            Block block = state.getBlock();
            
            // 只删除真正的悬空植物，不包括树叶
            if (floatingPlants.contains(block)) {
                // 此时下方的方块已经被我们替换了，所以不需要再检查 isAir 或 isWater
                // 直接可以判定为悬空植物
                affectedPositions.add(pos);
                originalStates.add(state);
                newStates.add(Blocks.AIR.getDefaultState());
                
                world.setBlockState(pos, Blocks.AIR.getDefaultState(), Block.NOTIFY_ALL);
            }
        }
    }
} 