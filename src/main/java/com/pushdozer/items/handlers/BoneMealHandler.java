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
        if (world.isClient) return;

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
        // 遍历形状内的所有方块
        for (BlockPos pos : shape.getBlockPositions()) {
            BlockState state = world.getBlockState(pos);
            
            // 检查是否为可生长的方块
            if (isGrowableBlock(state)) {
                // 尝试使用骨粉
                ItemStack boneMealStack = new ItemStack(net.minecraft.item.Items.BONE_MEAL);
                if (BoneMealItem.useOnFertilizable(boneMealStack, world, pos)) {
                    // 记录变化
                    BlockState newState = world.getBlockState(pos);
                    if (!state.equals(newState)) {
                        affectedPositions.add(pos);
                        originalStates.add(state);
                        newStates.add(newState);
                    }
                }
            }
        }
    }

    /**
     * 检查是否为可生长的方块
     */
    private boolean isGrowableBlock(BlockState state) {
        return GROWABLE_BLOCKS.contains(state.getBlock());
    }
} 