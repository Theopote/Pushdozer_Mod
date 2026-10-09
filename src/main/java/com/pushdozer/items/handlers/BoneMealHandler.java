package com.pushdozer.items.handlers;

import com.pushdozer.PushdozerMod;
import com.pushdozer.config.PushdozerConfig;
import com.pushdozer.operations.BlockOperation;
import com.pushdozer.operations.UndoAction;
import com.pushdozer.operations.VegetationOperation;
import com.pushdozer.shapes.GeometryShape;
import com.pushdozer.util.ShapeUtil;
import com.pushdozer.util.TerrainOperationFeedback;

import net.minecraft.block.*;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.BoneMealItem;
import net.minecraft.item.ItemStack;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 骨粉模式处理器
 * 在指定区域使用骨粉促进植物生长
 */
public class BoneMealHandler implements TerrainToolHandler {
    private static final int POSITIONS_PER_TICK = 48;
    /** 变更检测：水平半径（覆盖骨粉触发的树木/大植被） */
    private static final int CHANGE_SCAN_HORIZONTAL = 8;
    /** 变更检测：向上高度 */
    private static final int CHANGE_SCAN_UP = 32;
    /** 变更检测：向下深度 */
    private static final int CHANGE_SCAN_DOWN = 4;

    private static final Set<Block> SURFACE_VEGETATION_BLOCKS = Set.of(
        Blocks.GRASS_BLOCK, Blocks.DIRT, Blocks.FARMLAND, Blocks.SAND
    );

    public BoneMealHandler() {
    }

    /**
     * 处理骨粉操作
     */
    public void handleBoneMeal(PlayerEntity player, World world, PushdozerConfig config) {
        if (world.isClient() || !(world instanceof ServerWorld serverWorld)) {
            return;
        }

        BlockPos basePos = ShapeUtil.getTargetBlockPos(player, config);
        GeometryShape shape = ShapeUtil.createShape(player, config, basePos);

        if (shape == null) {
            return;
        }

        List<BlockPos> targetPositions = new ArrayList<>();
        for (BlockPos pos : shape.getBlockPositions()) {
            if (isBoneMealTarget(serverWorld, pos)) {
                targetPositions.add(pos);
            }
        }

        if (targetPositions.isEmpty()) {
            return;
        }

        Set<BlockPos> lockPositions = collectLockPositions(targetPositions);
        var operation = VegetationOperation.tryBegin(serverWorld, lockPositions);
        if (operation.isEmpty()) {
            TerrainOperationFeedback.notifyRegionBusy(player);
            return;
        }

        Map<BlockPos, BlockOperation.BlockChange> changes = new LinkedHashMap<>();
        scheduleBoneMeal(serverWorld, player, operation.get(), targetPositions, 0, changes);
    }

    private void scheduleBoneMeal(ServerWorld world, PlayerEntity player, VegetationOperation operation,
                                  List<BlockPos> targetPositions, int startIndex,
                                  Map<BlockPos, BlockOperation.BlockChange> changes) {
        int endIndex = Math.min(startIndex + POSITIONS_PER_TICK, targetPositions.size());
        int totalBoneMealUsed = 0;

        for (int i = startIndex; i < endIndex; i++) {
            totalBoneMealUsed += applyBoneMealAt(world, targetPositions.get(i), changes);
        }

        if (totalBoneMealUsed > 0) {
            PushdozerMod.LOGGER.debug("本 tick 骨粉处理 {} 个位置，累计变化 {} 个方块",
                endIndex - startIndex, changes.size());
        }

        if (endIndex >= targetPositions.size()) {
            finishBoneMeal(world, player, operation, changes);
            return;
        }

        operation.scheduleNextBatch(() ->
            scheduleBoneMeal(world, player, operation, targetPositions, endIndex, changes)
        );
    }

    private void finishBoneMeal(ServerWorld world, PlayerEntity player, VegetationOperation operation,
                                Map<BlockPos, BlockOperation.BlockChange> changes) {
        try {
            if (!changes.isEmpty()) {
                List<BlockPos> affectedPositions = new ArrayList<>(changes.size());
                List<BlockState> originalStates = new ArrayList<>(changes.size());
                List<BlockState> newStates = new ArrayList<>(changes.size());

                for (BlockOperation.BlockChange change : changes.values()) {
                    affectedPositions.add(change.pos());
                    originalStates.add(change.before());
                    newStates.add(change.after());
                }

                UndoAction undoAction = new UndoAction(
                    UndoAction.ActionType.BONE_MEAL,
                    world.getRegistryKey(),
                    affectedPositions,
                    originalStates,
                    newStates
                );
                PushdozerMod.pushUndoAction(player, undoAction);
                PushdozerMod.LOGGER.info("骨粉操作完成，检测到 {} 个方块变化", affectedPositions.size());
            }
        } finally {
            operation.release();
        }
    }

    private int applyBoneMealAt(World world, BlockPos pos, Map<BlockPos, BlockOperation.BlockChange> changes) {
        Set<BlockPos> positionsToCheck = getPositionsToCheck(pos);
        Map<BlockPos, BlockState> statesBefore = new HashMap<>();

        for (BlockPos checkPos : positionsToCheck) {
            statesBefore.put(checkPos, world.getBlockState(checkPos));
        }

        ItemStack boneMealStack = new ItemStack(net.minecraft.item.Items.BONE_MEAL);
        boolean boneMealUsed = false;
        int maxAttempts = 3;
        int uses = 0;

        for (int attempt = 0; attempt < maxAttempts; attempt++) {
            if (BoneMealItem.useOnFertilizable(boneMealStack, world, pos)) {
                boneMealUsed = true;
                uses++;
                recordChanges(positionsToCheck, statesBefore, world, changes);
            }
        }

        if (!boneMealUsed) {
            recordChanges(positionsToCheck, statesBefore, world, changes);
        }

        return uses;
    }

    public static void recordChanges(Set<BlockPos> positionsToCheck, Map<BlockPos, BlockState> statesBefore,
                                     World world, Map<BlockPos, BlockOperation.BlockChange> changes) {
        for (BlockPos checkPos : positionsToCheck) {
            BlockState stateAfter = world.getBlockState(checkPos);
            BlockState stateBefore = statesBefore.get(checkPos);

            if (stateBefore == null || stateBefore.equals(stateAfter)) {
                continue;
            }

            BlockOperation.BlockChange existing = changes.get(checkPos);
            if (existing == null) {
                changes.put(checkPos, new BlockOperation.BlockChange(checkPos, stateBefore, stateAfter));
            } else {
                changes.put(checkPos, new BlockOperation.BlockChange(checkPos, existing.before(), stateAfter));
            }
        }
    }

    public static Set<BlockPos> collectLockPositions(List<BlockPos> targetPositions) {
        Set<BlockPos> lockPositions = new HashSet<>();
        for (BlockPos pos : targetPositions) {
            lockPositions.addAll(getPositionsToCheck(pos));
        }
        return lockPositions;
    }

    /**
     * 获取需要检查/锁定的位置：地面、上方植被、以及可能由骨粉触发的树木范围
     */
    static Set<BlockPos> getPositionsToCheck(BlockPos groundPos) {
        Set<BlockPos> positions = new HashSet<>();

        BlockPos min = groundPos.add(-CHANGE_SCAN_HORIZONTAL, -CHANGE_SCAN_DOWN, -CHANGE_SCAN_HORIZONTAL);
        BlockPos max = groundPos.add(CHANGE_SCAN_HORIZONTAL, CHANGE_SCAN_UP, CHANGE_SCAN_HORIZONTAL);
        for (BlockPos pos : BlockPos.iterate(min, max)) {
            positions.add(pos.toImmutable());
        }

        return positions;
    }

    /**
     * 判定笔刷内位置是否应尝试骨粉：
     * - 地表植被生成模式：草地/泥土等可长草的地表
     * - 原版骨粉模式：实现 {@link Fertilizable} 且当前可施肥的方块（树苗、作物等）
     */
    private boolean isBoneMealTarget(World world, BlockPos pos) {
        BlockState state = world.getBlockState(pos);
        if (isSurfaceVegetationBlock(state)) {
            return true;
        }
        Block block = state.getBlock();
        return block instanceof Fertilizable fertilizable && fertilizable.isFertilizable(world, pos, state);
    }

    private boolean isSurfaceVegetationBlock(BlockState state) {
        Block block = state.getBlock();

        if (SURFACE_VEGETATION_BLOCKS.contains(block)) {
            return true;
        }

        return block == Blocks.MYCELIUM ||
               block == Blocks.PODZOL ||
               block == Blocks.COARSE_DIRT ||
               block == Blocks.ROOTED_DIRT ||
               block == Blocks.MOSS_BLOCK ||
               block == Blocks.MUD ||
               block == Blocks.MUDDY_MANGROVE_ROOTS;
    }
}
