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
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import java.util.*;

/**
 * 骨粉模式处理器
 * 在指定区域使用骨粉促进植物生长
 */
public class BoneMealHandler {
    private static final int POSITIONS_PER_TICK = 48;

    private PushdozerConfig config;
    private static final Set<Block> GROWABLE_BLOCKS = Set.of(
        Blocks.GRASS_BLOCK, Blocks.DIRT, Blocks.FARMLAND, Blocks.SAND
    );

    public BoneMealHandler() {
    }

    /**
     * 处理骨粉操作
     */
    public void handleBoneMeal(PlayerEntity player, World world, PushdozerConfig config) {
        this.config = config;
        if (world.isClient() || !(world instanceof ServerWorld serverWorld)) {
            return;
        }

        BlockPos basePos = ShapeUtil.getTargetBlockPos(player, config);
        GeometryShape shape = ShapeUtil.createShape(player, config, basePos);

        if (shape == null) {
            return;
        }

        List<BlockPos> growablePositions = new ArrayList<>();
        for (BlockPos pos : shape.getBlockPositions()) {
            if (isGrowableBlock(world.getBlockState(pos))) {
                growablePositions.add(pos);
            }
        }

        if (growablePositions.isEmpty()) {
            return;
        }

        List<BlockPos> affectedPositions = new ArrayList<>();
        List<BlockState> originalStates = new ArrayList<>();
        List<BlockState> newStates = new ArrayList<>();

        scheduleBoneMeal(serverWorld, player, growablePositions, 0, affectedPositions, originalStates, newStates);
    }

    private void scheduleBoneMeal(ServerWorld world, PlayerEntity player, List<BlockPos> growablePositions,
                                  int startIndex, List<BlockPos> affectedPositions,
                                  List<BlockState> originalStates, List<BlockState> newStates) {
        int endIndex = Math.min(startIndex + POSITIONS_PER_TICK, growablePositions.size());
        int totalBoneMealUsed = 0;

        for (int i = startIndex; i < endIndex; i++) {
            totalBoneMealUsed += applyBoneMealAt(world, growablePositions.get(i),
                affectedPositions, originalStates, newStates);
        }

        if (totalBoneMealUsed > 0) {
            PushdozerMod.LOGGER.debug("本 tick 骨粉处理 {} 个位置，累计变化 {} 个方块",
                endIndex - startIndex, affectedPositions.size());
        }

        if (endIndex >= growablePositions.size()) {
            if (!affectedPositions.isEmpty()) {
                UndoAction undoAction = new UndoAction(
                    UndoAction.ActionType.BONE_MEAL,
                    affectedPositions,
                    originalStates,
                    newStates
                );
                PushdozerMod.pushUndoAction(player, undoAction);
                PushdozerMod.LOGGER.info("骨粉操作完成，检测到 {} 个方块变化", affectedPositions.size());
            }
            return;
        }

        world.getServer().execute(() ->
            scheduleBoneMeal(world, player, growablePositions, endIndex,
                affectedPositions, originalStates, newStates)
        );
    }

    private int applyBoneMealAt(World world, BlockPos pos,
                                List<BlockPos> affectedPositions,
                                List<BlockState> originalStates,
                                List<BlockState> newStates) {
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
                checkForChanges(positionsToCheck, statesBefore, world,
                    affectedPositions, originalStates, newStates, attempt + 1);
            }
        }

        if (!boneMealUsed) {
            checkForChanges(positionsToCheck, statesBefore, world,
                affectedPositions, originalStates, newStates, 0);
        }

        return uses;
    }

    /**
     * 获取需要检查的位置列表
     * 包括地面方块和上方可能生成植物的位置
     */
    private Set<BlockPos> getPositionsToCheck(BlockPos groundPos) {
        Set<BlockPos> positions = new HashSet<>();

        positions.add(groundPos);

        for (int y = 1; y <= 8; y++) {
            positions.add(groundPos.up(y));
        }

        for (int x = -2; x <= 2; x++) {
            for (int z = -2; z <= 2; z++) {
                if (x == 0 && z == 0) {
                    continue;
                }

                BlockPos adjacentPos = groundPos.add(x, 0, z);
                positions.add(adjacentPos);

                for (int y = 1; y <= 4; y++) {
                    positions.add(adjacentPos.up(y));
                }
            }
        }

        return positions;
    }

    private void checkForChanges(Set<BlockPos> positionsToCheck, Map<BlockPos, BlockState> statesBefore,
                                World world, List<BlockPos> affectedPositions,
                                List<BlockState> originalStates, List<BlockState> newStates, int attempt) {
        for (BlockPos checkPos : positionsToCheck) {
            BlockState stateAfter = world.getBlockState(checkPos);
            BlockState stateBefore = statesBefore.get(checkPos);

            if (stateBefore != null && !stateBefore.equals(stateAfter) && !affectedPositions.contains(checkPos)) {
                affectedPositions.add(checkPos);
                originalStates.add(stateBefore);
                newStates.add(stateAfter);

                PushdozerMod.LOGGER.debug("骨粉变化检测: 位置 {} 从 {} 变为 {} (尝试 {})",
                    checkPos, stateBefore.getBlock(), stateAfter.getBlock(),
                    attempt > 0 ? attempt : "最终检查");
            }
        }
    }

    private boolean isGrowableBlock(BlockState state) {
        Block block = state.getBlock();

        if (GROWABLE_BLOCKS.contains(block)) {
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
