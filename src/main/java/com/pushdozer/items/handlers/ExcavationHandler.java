package com.pushdozer.items.handlers;

import java.util.List;
import java.util.stream.Collectors;
import java.util.ArrayList;

import com.pushdozer.PushdozerMod;
import com.pushdozer.config.PushdozerConfig;
import com.pushdozer.shapes.GeometryShape;
import com.pushdozer.util.ShapeUtil;
import com.pushdozer.operations.BlockOperation;
import com.pushdozer.operations.UndoAction;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.block.Blocks;
import net.minecraft.registry.Registries;

/**
 * 挖掘模式处理器
 * 负责处理挖掘操作，支持分层挖掘功能
 */
public class ExcavationHandler {
    private PushdozerConfig config;

    public ExcavationHandler() {
    }

    /**
     * 处理挖掘操作
     * 
     * @param player 执行操作的玩家
     * @param world 世界对象
     * @param config 玩家个人配置
     * @return 被挖掘的方块位置列表
     */
    public List<BlockPos> handleExcavation(PlayerEntity player, World world, PushdozerConfig config) {
        this.config = config;
        if (world.isClient()) {
            return List.of();
        }

        BlockPos basePos = ShapeUtil.getTargetBlockPos(player, config);
        GeometryShape shape = ShapeUtil.createShape(player, config, basePos);
        
        if (shape == null) {
            return List.of();
        }

        List<BlockPos> blocksToBreak = getBlocksToBreak(player, world, shape);
        
        if (!blocksToBreak.isEmpty()) {
            // 执行挖掘操作
            performExcavation(world, blocksToBreak, player);
        }

        return blocksToBreak;
    }

    /**
     * 获取需要挖掘的方块列表
     */
    private List<BlockPos> getBlocksToBreak(PlayerEntity player, World world, GeometryShape shape) {
        return shape.getBlockPositions().stream()
                .filter(pos -> isValidBreakTarget(world.getBlockState(pos), world, pos))
                .filter(pos -> isValidHeightForExcavation(pos, player))
                .collect(Collectors.toList());
    }

    /**
     * 执行挖掘操作
     */
    private void performExcavation(World world, List<BlockPos> positions, PlayerEntity player) {
        List<BlockState> originalStates = new ArrayList<>();
        List<BlockState> newStates = new ArrayList<>();

        for (BlockPos pos : positions) {
            BlockState originalState = world.getBlockState(pos);
            originalStates.add(originalState);
            newStates.add(Blocks.AIR.getDefaultState());
            
            // 挖掘方块
            world.setBlockState(pos, Blocks.AIR.getDefaultState(), Block.NOTIFY_ALL);
        }

        // 收集边界扩展信息
        BlockOperation.BoundaryExtension boundaryExtension = BlockOperation.collectBoundaryExtension(positions, world);
        
        // 创建撤销操作
        UndoAction undoAction = new UndoAction(
            UndoAction.ActionType.BREAK,
            positions,
            originalStates,
            newStates,
            boundaryExtension.getPositions(),
            boundaryExtension.getOriginalStates(),
            boundaryExtension.getNewStates()
        );
        PushdozerMod.pushUndoAction(player, undoAction);
    }

    /**
     * 检查方块是否可以挖掘
     */
    private boolean isValidBreakTarget(BlockState state, World world, BlockPos pos) {
        if (world.isAir(pos)) {
            return false;
        }

        Block block = state.getBlock();
        
        // 检查是否在可破坏方块列表中
        if (!config.isBlockBreakable(block)) {
            return false;
        }

        // 检查是否在忽略列表中
        String blockId = Registries.BLOCK.getId(block).toString();
        if (config.getIgnoredBlockIds().contains(blockId)) {
            return false;
        }

        // 检查是否为不可破坏的方块
        return !(block.getHardness() < 0);
    }

    /**
     * 检查标高是否适合挖掘（挖掘模式：只能在此标高以上工作）
     */
    private boolean isValidHeightForExcavation(BlockPos pos, PlayerEntity player) {
        PushdozerConfig.HeightMode heightMode = config.getHeightMode();
        if (heightMode == PushdozerConfig.HeightMode.NO_LIMIT) {
            // 标高不限：不限制标高
            return true;
        } else if (heightMode == PushdozerConfig.HeightMode.FOLLOW_PLAYER) {
            // 跟随玩家标高：只能在玩家当前高度及以上挖掘
            return pos.getY() >= player.getBlockY();
        } else if (heightMode == PushdozerConfig.HeightMode.LOCKED_ONCE || heightMode == PushdozerConfig.HeightMode.CUSTOM) {
            // 锁定到玩家标高/自定义标高：只能在锁定高度+1及以上挖掘
            return pos.getY() >= config.getLockedHeight() + 1;
        }
        return true; // 默认允许
    }
}