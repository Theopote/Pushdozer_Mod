package com.pushdozer.items.handlers;

import java.util.List;
import java.util.stream.Collectors;
import java.util.ArrayList;

import com.pushdozer.PushdozerMod;
import com.pushdozer.config.PushdozerConfig;
import com.pushdozer.shapes.GeometryShape;
import com.pushdozer.util.OperationPermissions;
import com.pushdozer.util.ShapeUtil;
import com.pushdozer.util.TerrainOperationFeedback;
import com.pushdozer.operations.AppliedChangeResult;
import com.pushdozer.operations.BlockOperation;
import com.pushdozer.operations.UndoAction;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.registry.Registries;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * 挖掘模式处理器
 * 负责处理挖掘操作，支持分层挖掘功能
 */
public class ExcavationHandler implements TerrainToolHandler {

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
        if (world.isClient()) {
            return List.of();
        }

        if (!OperationPermissions.checkForTerrainOperation(player, world, config)) {
            return List.of();
        }

        BlockPos basePos = ShapeUtil.getTargetBlockPos(player, config);
        GeometryShape shape = ShapeUtil.createShape(player, config, basePos);

        if (shape == null) {
            return List.of();
        }

        List<BlockPos> blocksToBreak = getBlocksToBreak(player, world, shape, config);

        if (!blocksToBreak.isEmpty()) {
            performExcavation(world, blocksToBreak, player);
        }

        return blocksToBreak;
    }

    /**
     * 在指定世界坐标执行挖掘并记录撤销栈（供 Game Test 与确定性调用场景使用）。
     */
    public void excavateBlocksAt(PlayerEntity player, ServerWorld world, PushdozerConfig config, List<BlockPos> worldPositions) {
        if (!OperationPermissions.checkForTerrainOperation(player, world, config)) {
            return;
        }

        List<BlockPos> filtered = worldPositions.stream()
            .filter(pos -> isValidBreakTarget(world.getBlockState(pos), world, pos, config))
            .filter(pos -> isValidHeightForExcavation(pos, player, config))
            .collect(Collectors.toCollection(ArrayList::new));

        if (!filtered.isEmpty()) {
            performExcavation(world, filtered, player);
        }
    }

    /**
     * 获取需要挖掘的方块列表
     */
    private List<BlockPos> getBlocksToBreak(PlayerEntity player, World world, GeometryShape shape, PushdozerConfig config) {
        return shape.getBlockPositions().stream()
                .filter(pos -> isValidBreakTarget(world.getBlockState(pos), world, pos, config))
                .filter(pos -> isValidHeightForExcavation(pos, player, config))
                .collect(Collectors.toList());
    }

    /**
     * 执行挖掘操作
     */
    private void performExcavation(World world, List<BlockPos> positions, PlayerEntity player) {
        if (!(world instanceof ServerWorld serverWorld)) {
            return;
        }

        List<BlockState> newStates = new ArrayList<>(positions.size());
        for (int i = 0; i < positions.size(); i++) {
            newStates.add(Blocks.AIR.getDefaultState());
        }

        if (!BlockOperation.applyTerrainChanges(serverWorld, positions, newStates, applied ->
            pushExcavationUndo(player, world, serverWorld, applied)
        )) {
            TerrainOperationFeedback.notifyRegionBusy(player);
        }
    }

    private static void pushExcavationUndo(PlayerEntity player, World world, ServerWorld serverWorld,
                                             AppliedChangeResult applied) {
        if (applied.isEmpty()) {
            return;
        }

        BlockOperation.BoundaryExtension boundaryExtension =
            BlockOperation.collectBoundaryExtension(applied.positions(), world);

        UndoAction undoAction = new UndoAction(
            UndoAction.ActionType.BREAK,
            serverWorld.getRegistryKey(),
            applied.positions(),
            applied.originalStates(),
            applied.appliedStates(),
            UndoAction.orderedBoundarySet(boundaryExtension.positions()),
            boundaryExtension.originalStates(),
            boundaryExtension.newStates()
        );
        PushdozerMod.pushUndoAction(player, undoAction);
    }

    /**
     * 检查方块是否可以挖掘
     */
    private boolean isValidBreakTarget(BlockState state, World world, BlockPos pos, PushdozerConfig config) {
        if (world.isAir(pos)) {
            return false;
        }

        Block block = state.getBlock();

        if (!config.isBlockBreakable(block)) {
            return false;
        }

        String blockId = Registries.BLOCK.getId(block).toString();
        if (config.getIgnoredBlockIds().contains(blockId)) {
            return false;
        }

        return !(block.getHardness() < 0);
    }

    /**
     * 检查标高是否适合挖掘（挖掘模式：只能在此标高以上工作）
     */
    private boolean isValidHeightForExcavation(BlockPos pos, PlayerEntity player, PushdozerConfig config) {
        PushdozerConfig.HeightMode heightMode = config.getHeightMode();
        if (heightMode == PushdozerConfig.HeightMode.NO_LIMIT) {
            return true;
        } else if (heightMode == PushdozerConfig.HeightMode.FOLLOW_PLAYER) {
            return pos.getY() >= player.getBlockY();
        } else if (heightMode == PushdozerConfig.HeightMode.LOCKED_ONCE || heightMode == PushdozerConfig.HeightMode.CUSTOM) {
            return pos.getY() >= config.getLockedHeight() + 1;
        }
        return true;
    }
}
