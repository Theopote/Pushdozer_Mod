package com.pushdozer.items.handlers;

import com.pushdozer.PushdozerMod;
import com.pushdozer.config.PushdozerConfig;
import com.pushdozer.shapes.GeometryShape;
import com.pushdozer.util.ShapeUtil;
import com.pushdozer.operations.UndoAction;

import net.minecraft.block.*;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.BoneMealItem;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import java.util.*;

/**
 * 骨粉模式处理器
 * 在指定区域使用骨粉促进植物生长
 */
public class BoneMealHandler {
    private final PushdozerConfig config;
    private static final Set<Block> GROWABLE_BLOCKS = Set.of(
        Blocks.GRASS_BLOCK, Blocks.DIRT, Blocks.FARMLAND, Blocks.SAND
    );

    public BoneMealHandler(PushdozerConfig config) {
        this.config = config;
    }

    /**
     * 处理骨粉操作
     */
    public void handleBoneMeal(PlayerEntity player, World world) {
        if (world.isClient()) return;

        BlockPos basePos = ShapeUtil.getTargetBlockPos(player, config);
        GeometryShape shape = ShapeUtil.createShape(player, config, basePos);

        if (shape == null) {
            return;
        }

        List<BlockPos> affectedPositions = new ArrayList<>();
        List<BlockState> originalStates = new ArrayList<>();
        List<BlockState> newStates = new ArrayList<>();

        // 执行骨粉操作
        applyBoneMeal(world, shape, affectedPositions, originalStates, newStates);

        // 创建撤销操作
        if (!affectedPositions.isEmpty()) {
            UndoAction undoAction = new UndoAction(
                UndoAction.ActionType.BONE_MEAL,
                affectedPositions,
                originalStates,
                newStates
            );
            PushdozerMod.pushUndoAction(player, undoAction);
        }
    }

    /**
     * 应用骨粉操作
     */
    private void applyBoneMeal(World world, GeometryShape shape, 
                              List<BlockPos> affectedPositions,
                              List<BlockState> originalStates, 
                              List<BlockState> newStates) {
        int totalBoneMealUsed = 0;
        int totalChangesDetected = 0;
        
         // 遍历形状内的所有方块
        for (BlockPos pos : shape.getBlockPositions()) {
            BlockState state = world.getBlockState(pos);
            
            // 检查是否为可生长的方块
            if (isGrowableBlock(state)) {
                // 记录骨粉使用前的状态（包括地面和上方可能存在的植物）
                Set<BlockPos> positionsToCheck = getPositionsToCheck(pos);
                Map<BlockPos, BlockState> statesBefore = new HashMap<>();
                
                for (BlockPos checkPos : positionsToCheck) {
                    statesBefore.put(checkPos, world.getBlockState(checkPos));
                }
                
                // 尝试使用骨粉，并重复使用直到有效果或达到最大尝试次数
                ItemStack boneMealStack = new ItemStack(net.minecraft.item.Items.BONE_MEAL);
                boolean boneMealUsed = false;
                int maxAttempts = 3; // 最多尝试3次，平衡效果和性能
                
                for (int attempt = 0; attempt < maxAttempts; attempt++) {
                    if (BoneMealItem.useOnFertilizable(boneMealStack, world, pos)) {
                        boneMealUsed = true;
                        totalBoneMealUsed++;
                        
                        // 立即检查变化，因为骨粉效果是同步的
                        checkForChanges(positionsToCheck, statesBefore, world, 
                            affectedPositions, originalStates, newStates, attempt + 1);
                    }
                }
                
                // 即使骨粉没有效果，也检查一次变化（某些方块可能已经发生变化）
                if (!boneMealUsed) {
                    checkForChanges(positionsToCheck, statesBefore, world, 
                        affectedPositions, originalStates, newStates, 0);
                }
            }
        }
        
        // 记录总体统计
        if (totalBoneMealUsed > 0) {
            PushdozerMod.LOGGER.info("骨粉操作完成: 使用了 {} 次骨粉，检测到 {} 个方块变化", 
                totalBoneMealUsed, totalChangesDetected);
        }
    }

    /**
     * 获取需要检查的位置列表
     * 包括地面方块和上方可能生成植物的位置
     * 改进：支持更全面的植物检测，包括高草丛、花朵等
     */
    private Set<BlockPos> getPositionsToCheck(BlockPos groundPos) {
        Set<BlockPos> positions = new HashSet<>();
        
        // 添加地面位置
        positions.add(groundPos);
        
        // 添加上方可能生成植物的位置（扩展到8格高度以支持所有植物）
        // 包括：高草丛(2格)、甘蔗(3格)、竹子(4-8格)、树木等
        for (int y = 1; y <= 8; y++) {
            positions.add(groundPos.up(y));
        }
        
        // 添加周围位置（某些植物可能会在相邻位置生成）
        // 扩展检查范围以捕获蔓延的植物
        for (int x = -2; x <= 2; x++) {
            for (int z = -2; z <= 2; z++) {
                if (x == 0 && z == 0) continue; // 跳过中心位置（已经添加）
                
                BlockPos adjacentPos = groundPos.add(x, 0, z);
                positions.add(adjacentPos);
                
                // 也检查相邻位置的上方（某些植物可能会蔓延）
                // 对于相邻位置，检查较低的高度以避免过度扩展
                for (int y = 1; y <= 4; y++) {
                    positions.add(adjacentPos.up(y));
                }
            }
        }
        
        return positions;
    }

    /**
     * 检查变化的统一方法
     */
    private void checkForChanges(Set<BlockPos> positionsToCheck, Map<BlockPos, BlockState> statesBefore,
                                World world, List<BlockPos> affectedPositions, 
                                List<BlockState> originalStates, List<BlockState> newStates, int attempt) {
        int changesFound = 0;
        
        for (BlockPos checkPos : positionsToCheck) {
            BlockState stateAfter = world.getBlockState(checkPos);
            BlockState stateBefore = statesBefore.get(checkPos);
            
            if (!stateBefore.equals(stateAfter)) {
                // 检查是否已经记录过这个位置（避免重复记录）
                if (!affectedPositions.contains(checkPos)) {
                    affectedPositions.add(checkPos);
                    originalStates.add(stateBefore);
                    newStates.add(stateAfter);
                    changesFound++;
                    
                    PushdozerMod.LOGGER.debug("骨粉变化检测: 位置 {} 从 {} 变为 {} (尝试 {})", 
                        checkPos, stateBefore.getBlock(), stateAfter.getBlock(), 
                        attempt > 0 ? attempt : "最终检查");
                }
            }
        }
        
        if (changesFound > 0) {
            PushdozerMod.LOGGER.debug("本次检查发现 {} 个新变化", changesFound);
        }
    }

    /**
     * 检查是否为可生长的方块
     * 扩展支持更多可生长的方块类型
     */
    private boolean isGrowableBlock(BlockState state) {
        Block block = state.getBlock();
        
        // 基础可生长方块
        if (GROWABLE_BLOCKS.contains(block)) {
            return true;
        }
        
        // 扩展支持更多可生长的方块
        return block == Blocks.MYCELIUM ||
               block == Blocks.PODZOL ||
               block == Blocks.COARSE_DIRT ||
               block == Blocks.ROOTED_DIRT ||
               block == Blocks.MOSS_BLOCK ||
               block == Blocks.MUD ||
               block == Blocks.MUDDY_MANGROVE_ROOTS;
    }
} 