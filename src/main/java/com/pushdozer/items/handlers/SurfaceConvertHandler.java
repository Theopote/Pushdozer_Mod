package com.pushdozer.items.handlers;

import com.pushdozer.PushdozerMod;
import com.pushdozer.config.PushdozerConfig;
import com.pushdozer.config.domain.SurfaceConfig;
import com.pushdozer.util.RegistryBlocks;
import com.pushdozer.shapes.GeometryShape;
import com.pushdozer.util.ShapeUtil;
import com.pushdozer.operations.BlockOperation;
import com.pushdozer.operations.UndoAction;

import net.minecraft.block.*;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import java.util.*;

/**
 * 表层转换模式处理器
 * 根据配置面板中的方块类型和配置比例，对minecraft地形表面的方块进行替换
 * 如果异常那就默认替换为草方块
 */
public class SurfaceConvertHandler implements TerrainToolHandler {
    private PushdozerConfig config;
    private static final Random RANDOM = new Random(); // 静态Random对象，避免重复创建
    private static final Set<Block> IGNORED_BLOCKS = Set.of(
        // 原木
        Blocks.OAK_LOG, Blocks.SPRUCE_LOG, Blocks.BIRCH_LOG, Blocks.JUNGLE_LOG, 
        Blocks.ACACIA_LOG, Blocks.DARK_OAK_LOG, Blocks.MANGROVE_LOG, Blocks.CHERRY_LOG,
        
        // 草和蕨类
        Blocks.SHORT_GRASS, Blocks.TALL_GRASS, Blocks.FERN, Blocks.LARGE_FERN,
        Blocks.SHORT_DRY_GRASS, Blocks.TALL_DRY_GRASS,
        
        // 花朵
        Blocks.DANDELION, Blocks.POPPY, Blocks.BLUE_ORCHID, Blocks.ALLIUM, 
        Blocks.AZURE_BLUET, Blocks.RED_TULIP, Blocks.ORANGE_TULIP, Blocks.WHITE_TULIP, 
        Blocks.PINK_TULIP, Blocks.OXEYE_DAISY, Blocks.CORNFLOWER, Blocks.LILY_OF_THE_VALLEY, 
        Blocks.SUNFLOWER, Blocks.LILAC, Blocks.ROSE_BUSH, Blocks.PEONY, Blocks.WITHER_ROSE,
        Blocks.TORCHFLOWER,
        
        // 其他植物
        Blocks.DEAD_BUSH, Blocks.CACTUS, Blocks.CACTUS_FLOWER, Blocks.SUGAR_CANE, Blocks.BAMBOO,
        Blocks.CHORUS_PLANT, Blocks.CHORUS_FLOWER, Blocks.NETHER_SPROUTS,
        Blocks.WARPED_ROOTS, Blocks.CRIMSON_ROOTS, Blocks.WARPED_FUNGUS, Blocks.CRIMSON_FUNGUS,
        Blocks.WEEPING_VINES, Blocks.TWISTING_VINES, Blocks.GLOW_LICHEN, Blocks.HANGING_ROOTS,
        Blocks.SPORE_BLOSSOM, Blocks.FLOWERING_AZALEA, Blocks.AZALEA,
        Blocks.MOSS_CARPET, Blocks.SEAGRASS, Blocks.TALL_SEAGRASS, Blocks.KELP,
        Blocks.KELP_PLANT, Blocks.SEA_PICKLE, Blocks.LILY_PAD, Blocks.SMALL_DRIPLEAF,
        Blocks.BIG_DRIPLEAF, Blocks.PITCHER_PLANT,
        
        // 树苗
        Blocks.OAK_SAPLING, Blocks.SPRUCE_SAPLING, Blocks.BIRCH_SAPLING, Blocks.JUNGLE_SAPLING,
        Blocks.ACACIA_SAPLING, Blocks.DARK_OAK_SAPLING, Blocks.MANGROVE_PROPAGULE, Blocks.CHERRY_SAPLING,
        
        // 农作物
        Blocks.WHEAT, Blocks.CARROTS, Blocks.POTATOES, Blocks.BEETROOTS, Blocks.SWEET_BERRY_BUSH,
        Blocks.COCOA, Blocks.MELON_STEM, Blocks.PUMPKIN_STEM,
        
        // 蘑菇
        Blocks.BROWN_MUSHROOM, Blocks.RED_MUSHROOM,
        
        // 藤蔓
        Blocks.VINE, Blocks.CAVE_VINES, Blocks.CAVE_VINES_PLANT
    );

    public SurfaceConvertHandler() {
    }

    /**
     * 处理表层转换操作
     */
    public void handleSurfaceConvert(PlayerEntity player, World world, PushdozerConfig config) {
        this.config = config;
        if (world.isClient()) return;

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

        if (!affectedPositions.isEmpty() && world instanceof ServerWorld serverWorld) {
            BlockOperation.applyTerrainChanges(serverWorld, affectedPositions, newStates, () -> {
                UndoAction undoAction = new UndoAction(
                    UndoAction.ActionType.SURFACE_CONVERT,
                    affectedPositions,
                    originalStates,
                    newStates
                );
                PushdozerMod.pushUndoAction(player, undoAction);
            });
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

        // 对每个柱子，从高到低扫描，找到第一个"非空气、非水、非被忽略的方块"
        for (Map.Entry<BlockPos, List<Integer>> entry : columnYMap.entrySet()) {
            BlockPos columnPos = entry.getKey();
            List<Integer> yList = entry.getValue();
            yList.sort(Collections.reverseOrder()); // 从高到低
            for (int y : yList) {
                BlockPos surfacePos = new BlockPos(columnPos.getX(), y, columnPos.getZ());
                BlockState state = world.getBlockState(surfacePos);
                if (!world.isAir(surfacePos) && !isWater(world, surfacePos) && 
                    !isIgnoredBlock(state)) {
                    // 找到真正的地表
                    Block targetBlock = selectTargetBlock();
                    BlockState targetState = targetBlock.getDefaultState();
                    if (state.getBlock() != targetBlock) {
                        affectedPositions.add(surfacePos);
                        originalStates.add(state);
                        newStates.add(targetState);
                        newlyPlacedSurfacePositions.add(surfacePos);
                    }
                    break; // 只处理第一个地表
                }
                // 如果是空气/水/被忽略的方块，继续向下扫描
            }
        }

        // 处理悬空植物 - 只检查被修改过的地表上方的方块
        removeFloatingVegetation(world, newlyPlacedSurfacePositions, affectedPositions, originalStates, newStates);
    }

    /**
     * 根据配置比例选择目标方块
     */
    private Block selectTargetBlock() {
        List<SurfaceConfig.SurfaceConvertBlock> surfaceBlocks = config.getSurfaceConvertBlocks();
        
        if (surfaceBlocks.isEmpty()) {
            // 如果没有配置方块，默认使用草方块
            return Blocks.GRASS_BLOCK;
        }

        // 计算总百分比
        float totalPercentage = 0f;
        for (SurfaceConfig.SurfaceConvertBlock block : surfaceBlocks) {
            totalPercentage += block.getPercentage();
        }

        if (totalPercentage <= 0f) {
            // 如果总百分比为0，默认使用草方块
            return Blocks.GRASS_BLOCK;
        }

        // 生成随机数来决定使用哪个方块 - 使用静态Random对象
        float random = RANDOM.nextFloat() * totalPercentage;
        float currentSum = 0f;

        for (SurfaceConfig.SurfaceConvertBlock surfaceBlock : surfaceBlocks) {
            currentSum += surfaceBlock.getPercentage();
            if (random <= currentSum) {
                Block block = RegistryBlocks.resolveOrAir(surfaceBlock.getBlockId());
                if (block != Blocks.AIR) {
                    return block;
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
        Block block = state.getBlock();
        return IGNORED_BLOCKS.contains(block) || isPotted(block) || isLiveCoral(block) || isDeadCoral(block);
    }
    
    /**
     * 检查方块是否为盆栽（不包括空花盆）
     */
    private boolean isPotted(Block block) {
        return (block instanceof net.minecraft.block.FlowerPotBlock) && block != Blocks.FLOWER_POT;
    }
    
    /**
     * 检查方块是否为活珊瑚
     */
    private boolean isLiveCoral(Block block) {
        String id = block.toString().toLowerCase();
        return id.contains("coral") && !id.contains("dead");
    }
    
    /**
     * 检查方块是否为死珊瑚
     */
    private boolean isDeadCoral(Block block) {
        String id = block.toString().toLowerCase();
        return id.contains("coral") && id.contains("dead");
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
            Blocks.SUNFLOWER, Blocks.LILAC, Blocks.ROSE_BUSH, Blocks.PEONY,
            Blocks.SHORT_DRY_GRASS, Blocks.TALL_DRY_GRASS
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
            }
        }
    }
} 