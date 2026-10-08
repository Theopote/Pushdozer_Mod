package com.pushdozer.operations;

import com.pushdozer.util.ExceptionPolicy;
import com.pushdozer.util.WorldBounds;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.FallingBlock;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * BlockOperation 工具类
 * 用于处理批量方块操作和边界扩展
 */
public class BlockOperation {
    private static final Logger LOGGER = LoggerFactory.getLogger("pushdozer");

    /** 低于此数量时在同一 tick 内同步完成 */
    public static final int SYNC_BLOCK_LIMIT = 512;
    /** 跨 tick 调度时，每个 tick 最多应用的方块数 */
    public static final int BLOCKS_PER_TICK = 1024;
    /** 大操作后处理阈值 */
    public static final int LARGE_POST_PROCESS_THRESHOLD = 4096;
    /** 大操作后处理每 tick 处理的光照/邻居更新数量 */
    public static final int POST_PROCESS_PER_TICK = 2048;

    public static final int BULK_WRITE_FLAGS = Block.NOTIFY_LISTENERS | Block.FORCE_STATE | Block.SKIP_DROPS;

    public record TerrainOperationToken(UUID operationId, ServerWorld world) {
    }

    public record BlockChange(BlockPos pos, BlockState before, BlockState after) {
    }

    public static BoundaryExtension collectBoundaryExtension(List<BlockPos> positions, World world) {
        LinkedHashSet<BlockPos> boundaryPositions = new LinkedHashSet<>();
        List<BlockState> boundaryOriginalStates = new ArrayList<>();
        List<BlockState> boundaryNewStates = new ArrayList<>();

        Set<BlockPos> allPositions = new HashSet<>(positions);

        for (BlockPos pos : positions) {
            collectBoundaryNeighbors(pos, allPositions, world, boundaryPositions, boundaryOriginalStates, boundaryNewStates,
                pos.north(), pos.south(), pos.east(), pos.west(), pos.up(), pos.down());

            collectBoundaryNeighbors(pos, allPositions, world, boundaryPositions, boundaryOriginalStates, boundaryNewStates,
                pos.north().east(), pos.north().west(), pos.south().east(), pos.south().west(),
                pos.up().north(), pos.up().south(), pos.up().east(), pos.up().west(),
                pos.down().north(), pos.down().south(), pos.down().east(), pos.down().west());
        }

        return new BoundaryExtension(boundaryPositions, boundaryOriginalStates, boundaryNewStates);
    }

    private static void collectBoundaryNeighbors(BlockPos source, Set<BlockPos> allPositions, World world,
                                                 LinkedHashSet<BlockPos> boundaryPositions,
                                                 List<BlockState> boundaryOriginalStates,
                                                 List<BlockState> boundaryNewStates,
                                                 BlockPos... neighbors) {
        for (BlockPos neighbor : neighbors) {
            if (!allPositions.contains(neighbor) && isValidBoundaryPosition(neighbor, world)
                && boundaryPositions.add(neighbor)) {
                BlockState state = world.getBlockState(neighbor);
                boundaryOriginalStates.add(state);
                boundaryNewStates.add(state);
            }
        }
    }

    private static boolean isValidBoundaryPosition(BlockPos pos, World world) {
        if (!WorldBounds.isBuildablePos(world, pos)) {
            return false;
        }
        if (world instanceof ServerWorld serverWorld) {
            return serverWorld.isChunkLoaded(new ChunkPos(pos).toLong());
        }
        return false;
    }

    public static void batchSetBlockStates(List<BlockPos> positions, List<BlockState> states, World world, int flags) {
        batchSetBlockStates(positions, states, world, flags, (Consumer<AppliedChangeResult>) null);
    }

    public static boolean batchSetBlockStates(List<BlockPos> positions, List<BlockState> states, World world,
                                              int flags, Runnable onComplete) {
        if (onComplete == null) {
            return batchSetBlockStates(positions, states, world, flags, (Consumer<AppliedChangeResult>) null);
        }
        return batchSetBlockStates(positions, states, world, flags, result -> onComplete.run());
    }

    public static boolean batchSetBlockStates(List<BlockPos> positions, List<BlockState> states, World world,
                                              int flags, Consumer<AppliedChangeResult> onComplete) {
        return batchSetBlockStates(positions, states, world, flags, null, onComplete);
    }

    public static boolean batchSetBlockStates(List<BlockPos> positions, List<BlockState> states, World world,
                                              int flags, List<int[]> tallPlantPairs,
                                              Consumer<AppliedChangeResult> onComplete) {
        if (positions.size() != states.size()) {
            LOGGER.error("位置和状态列表大小不匹配: {} vs {}", positions.size(), states.size());
            completeWith(onComplete, AppliedChangeResult.empty());
            return true;
        }

        if (positions.isEmpty()) {
            completeWith(onComplete, AppliedChangeResult.empty());
            return true;
        }

        AppliedChangeResult.Builder accumulator = new AppliedChangeResult.Builder();

        if (!(world instanceof ServerWorld serverWorld) || positions.size() <= SYNC_BLOCK_LIMIT) {
            applyBlockStates(positions, states, world, flags, 0, positions.size(), accumulator, tallPlantPairs);
            completeWith(onComplete, accumulator.build());
            return true;
        }

        scheduleBlockStatesAcrossTicks(serverWorld, positions, states, flags, 0, accumulator, onComplete, tallPlantPairs);
        return false;
    }

    private static void applyBlockStates(List<BlockPos> positions, List<BlockState> states, World world,
                                         int flags, int startIndex, int endIndex,
                                         AppliedChangeResult.Builder accumulator) {
        applyBlockStates(positions, states, world, flags, startIndex, endIndex, accumulator, null);
    }

    private static void applyBlockStates(List<BlockPos> positions, List<BlockState> states, World world,
                                         int flags, int startIndex, int endIndex,
                                         AppliedChangeResult.Builder accumulator, List<int[]> tallPlantPairs) {
        if (tallPlantPairs == null || tallPlantPairs.isEmpty()) {
            for (int i = startIndex; i < endIndex; i++) {
                applySingleBlock(positions, states, world, flags, i, accumulator);
            }
            return;
        }

        java.util.Set<Integer> paired = new java.util.HashSet<>();
        for (int[] pair : tallPlantPairs) {
            if (pair[0] < startIndex || pair[0] >= endIndex || pair[1] < startIndex || pair[1] >= endIndex) {
                continue;
            }
            applyTallPlantPair(positions, states, world, flags, pair[0], pair[1], accumulator);
            paired.add(pair[0]);
            paired.add(pair[1]);
        }

        for (int i = startIndex; i < endIndex; i++) {
            if (!paired.contains(i)) {
                applySingleBlock(positions, states, world, flags, i, accumulator);
            }
        }
    }

    private static void applySingleBlock(List<BlockPos> positions, List<BlockState> states, World world,
                                         int flags, int index, AppliedChangeResult.Builder accumulator) {
        BlockPos pos = positions.get(index);
        if (!isChunkLoaded(world, pos)) {
            LOGGER.debug("跳过未加载区块: {}", pos);
            return;
        }
        BlockState newState = states.get(index);
        BlockState originalState = world.getBlockState(pos);
        if (trySetBlockState(world, pos, newState, flags)) {
            accumulator.addSuccess(pos, originalState, newState);
        }
    }

    private static void applyTallPlantPair(List<BlockPos> positions, List<BlockState> states, World world,
                                           int flags, int lowerIndex, int upperIndex,
                                           AppliedChangeResult.Builder accumulator) {
        BlockPos lowerPos = positions.get(lowerIndex);
        BlockPos upperPos = positions.get(upperIndex);
        if (!isChunkLoaded(world, lowerPos) || !isChunkLoaded(world, upperPos)) {
            LOGGER.debug("跳过未加载区块的双高植物: {} / {}", lowerPos, upperPos);
            return;
        }

        BlockState lowerNew = states.get(lowerIndex);
        BlockState upperNew = states.get(upperIndex);
        BlockState lowerOriginal = world.getBlockState(lowerPos);
        BlockState upperOriginal = world.getBlockState(upperPos);

        if (!canApplyTallPlantUpperHalf(world, upperPos, upperOriginal, upperNew)
            || !canApplyTallPlantHalf(world, lowerPos, lowerOriginal, lowerNew)) {
            return;
        }

        boolean lowerApplied = trySetBlockState(world, lowerPos, lowerNew, flags);
        boolean upperApplied = trySetBlockState(world, upperPos, upperNew, flags);
        if (lowerApplied && upperApplied) {
            accumulator.addSuccess(lowerPos, lowerOriginal, lowerNew);
            accumulator.addSuccess(upperPos, upperOriginal, upperNew);
            return;
        }

        if (lowerApplied) {
            trySetBlockState(world, lowerPos, lowerOriginal, flags);
        }
        if (upperApplied) {
            trySetBlockState(world, upperPos, upperOriginal, flags);
        }
    }

    private static boolean canApplyTallPlantHalf(World world, BlockPos pos, BlockState original, BlockState target) {
        if (original.equals(target)) {
            return true;
        }
        if (original.isAir() || original.isReplaceable()) {
            return target.canPlaceAt(world, pos);
        }
        return false;
    }

    /** Upper half is validated before lower exists; only require a free upper cell. */
    private static boolean canApplyTallPlantUpperHalf(World world, BlockPos pos, BlockState original, BlockState target) {
        if (original.equals(target)) {
            return true;
        }
        return original.isAir() || original.isReplaceable();
    }

    private static boolean trySetBlockState(World world, BlockPos pos, BlockState newState, int flags) {
        final boolean[] applied = {false};
        ExceptionPolicy.runPerItem("设置方块状态 " + pos, () -> {
            if (world.setBlockState(pos, newState, flags)) {
                applied[0] = true;
            }
        }, LOGGER);
        return applied[0];
    }

    private static void scheduleBlockStatesAcrossTicks(ServerWorld world, List<BlockPos> positions,
                                                       List<BlockState> states, int flags, int startIndex,
                                                       AppliedChangeResult.Builder accumulator,
                                                       Consumer<AppliedChangeResult> onComplete) {
        scheduleBlockStatesAcrossTicks(world, positions, states, flags, startIndex, accumulator, onComplete, null);
    }

    private static void scheduleBlockStatesAcrossTicks(ServerWorld world, List<BlockPos> positions,
                                                       List<BlockState> states, int flags, int startIndex,
                                                       AppliedChangeResult.Builder accumulator,
                                                       Consumer<AppliedChangeResult> onComplete,
                                                       List<int[]> tallPlantPairs) {
        int endIndex = Math.min(startIndex + BLOCKS_PER_TICK, positions.size());
        applyBlockStates(positions, states, world, flags, startIndex, endIndex, accumulator, tallPlantPairs);

        if (endIndex >= positions.size()) {
            completeWith(onComplete, accumulator.build());
            return;
        }

        Objects.requireNonNull(world.getServer()).execute(() ->
            scheduleBlockStatesAcrossTicks(world, positions, states, flags, endIndex, accumulator, onComplete, tallPlantPairs)
        );
    }

    public static Optional<TerrainOperationToken> beginTerrainOperation(ServerWorld world, Collection<BlockPos> positions) {
        UUID operationId = UUID.randomUUID();
        if (!positions.isEmpty()
            && !TerrainOperationScheduler.getInstance().tryAcquire(world, operationId, positions)) {
            return Optional.empty();
        }
        return Optional.of(new TerrainOperationToken(operationId, world));
    }

    public static boolean extendTerrainOperation(TerrainOperationToken token, Collection<BlockPos> additionalPositions) {
        return TerrainOperationScheduler.getInstance().tryExtend(token.world(), token.operationId(), additionalPositions);
    }

    public static void releaseTerrainOperation(TerrainOperationToken token) {
        TerrainOperationScheduler.getInstance().release(token.world(), token.operationId());
    }

    public static void applyTerrainPhase(TerrainOperationToken token, List<BlockPos> positions, List<BlockState> newStates,
                                         Consumer<AppliedChangeResult> onComplete) {
        applyTerrainPhase(token, positions, newStates, false, onComplete);
    }

    public static void applyTerrainPhase(TerrainOperationToken token, List<BlockPos> positions, List<BlockState> newStates,
                                         Runnable onComplete) {
        applyTerrainPhase(token, positions, newStates, onComplete == null ? null : result -> onComplete.run());
    }

    public static void applyPlacementPhase(TerrainOperationToken token, List<BlockPos> positions, List<BlockState> newStates,
                                           Consumer<AppliedChangeResult> onComplete) {
        applyTerrainPhase(token, positions, newStates, true, onComplete);
    }

    private static void applyTerrainPhase(TerrainOperationToken token, List<BlockPos> positions, List<BlockState> newStates,
                                          boolean placement, Consumer<AppliedChangeResult> onComplete) {
        applyTerrainPhase(token, positions, newStates, placement, null, onComplete);
    }

    private static void applyTerrainPhase(TerrainOperationToken token, List<BlockPos> positions, List<BlockState> newStates,
                                          boolean placement, List<int[]> tallPlantPairs,
                                          Consumer<AppliedChangeResult> onComplete) {
        if (positions.isEmpty()) {
            completeWith(onComplete, AppliedChangeResult.empty());
            return;
        }

        batchSetBlockStates(positions, newStates, token.world(), BULK_WRITE_FLAGS, tallPlantPairs, applied -> {
            if (placement) {
                scheduleFallingBlockTicks(token.world(), applied);
            }
            postProcessBlockChanges(token.world(), applied.positions(), applied.appliedStates(),
                () -> completeWith(onComplete, applied));
        });
    }

    /**
     * Applies terrain changes under an already-acquired operation token (caller releases the lock).
     */
    public static void applyTerrainPhaseWithToken(TerrainOperationToken token, List<BlockPos> positions,
                                                  List<BlockState> newStates, List<int[]> tallPlantPairs,
                                                  Consumer<AppliedChangeResult> onComplete) {
        applyTerrainPhase(token, positions, newStates, false, tallPlantPairs, onComplete);
    }

    /**
     * @return false when target chunks are locked by another in-flight operation
     */
    public static boolean applyTerrainChanges(ServerWorld world, List<BlockPos> positions, List<BlockState> newStates,
                                              Consumer<AppliedChangeResult> onComplete) {
        return applyScheduledChanges(world, positions, newStates, onComplete, false);
    }

    public static boolean applyTerrainChanges(ServerWorld world, List<BlockPos> positions, List<BlockState> newStates,
                                              Runnable onComplete) {
        if (onComplete == null) {
            return applyTerrainChanges(world, positions, newStates, (Consumer<AppliedChangeResult>) null);
        }
        return applyTerrainChanges(world, positions, newStates, result -> onComplete.run());
    }

    /**
     * @return false when target chunks are locked by another in-flight operation
     */
    public static boolean applyPlacementChanges(ServerWorld world, List<BlockPos> positions, List<BlockState> newStates,
                                                Consumer<AppliedChangeResult> onComplete) {
        return applyScheduledChanges(world, positions, newStates, onComplete, true);
    }

    public static boolean applyPlacementChanges(ServerWorld world, List<BlockPos> positions, List<BlockState> newStates,
                                                Runnable onComplete) {
        if (onComplete == null) {
            return applyPlacementChanges(world, positions, newStates, (Consumer<AppliedChangeResult>) null);
        }
        return applyPlacementChanges(world, positions, newStates, result -> onComplete.run());
    }

    private static boolean applyScheduledChanges(ServerWorld world, List<BlockPos> positions, List<BlockState> newStates,
                                                 Consumer<AppliedChangeResult> onComplete, boolean placement) {
        if (positions.isEmpty()) {
            completeWith(onComplete, AppliedChangeResult.empty());
            return true;
        }

        Optional<TerrainOperationToken> token = beginTerrainOperation(world, positions);
        if (token.isEmpty()) {
            LOGGER.warn("Skipped terrain apply due to chunk conflict ({} positions)", positions.size());
            return false;
        }

        Consumer<AppliedChangeResult> finishOperation = applied -> {
            try {
                completeWith(onComplete, applied);
            } finally {
                releaseTerrainOperation(token.get());
            }
        };

        if (placement) {
            applyPlacementPhase(token.get(), positions, newStates, finishOperation);
        } else {
            applyTerrainPhase(token.get(), positions, newStates, finishOperation);
        }
        return true;
    }

    public static void postProcessBlockChanges(ServerWorld world, List<BlockPos> positions, List<BlockState> newStates) {
        postProcessBlockChanges(world, positions, newStates, null);
    }

    public static void postProcessBlockChanges(ServerWorld world, List<BlockPos> positions, List<BlockState> newStates,
                                                Runnable onComplete) {
        if (positions.isEmpty()) {
            if (onComplete != null) {
                onComplete.run();
            }
            return;
        }

        Set<BlockPos> changed = new HashSet<>(positions);
        List<BlockPos> relightTargets = new ArrayList<>(positions);
        Set<BlockPos> neighborTargets = collectNeighborUpdateTargets(world, changed);

        if (positions.size() >= LARGE_POST_PROCESS_THRESHOLD) {
            schedulePostProcessAcrossTicks(world, relightTargets, new ArrayList<>(neighborTargets), 0, 0, onComplete);
            return;
        }

        applyRelightBatch(world, relightTargets, 0, relightTargets.size());
        applyNeighborUpdates(world, neighborTargets);
        if (onComplete != null) {
            onComplete.run();
        }
    }

    private static Set<BlockPos> collectNeighborUpdateTargets(ServerWorld world, Set<BlockPos> changed) {
        Set<BlockPos> neighborTargets = new LinkedHashSet<>();
        for (BlockPos pos : changed) {
            if (!isChunkLoaded(world, pos)) {
                continue;
            }
            neighborTargets.add(pos);
            for (Direction direction : Direction.values()) {
                BlockPos neighbor = pos.offset(direction);
                if (!changed.contains(neighbor) && isValidBoundaryPosition(neighbor, world)) {
                    neighborTargets.add(neighbor);
                }
            }
            BlockPos below = pos.down();
            if (WorldBounds.isBuildablePos(world, below) && isChunkLoaded(world, below)) {
                neighborTargets.add(below);
            }
        }
        return neighborTargets;
    }

    private static void schedulePostProcessAcrossTicks(ServerWorld world, List<BlockPos> relightTargets,
                                                       List<BlockPos> neighborTargets, int relightIndex,
                                                       int neighborIndex, Runnable onComplete) {
        relightIndex = applyRelightBatch(world, relightTargets, relightIndex,
            Math.min(relightIndex + POST_PROCESS_PER_TICK, relightTargets.size()));

        if (relightIndex < relightTargets.size()) {
            int nextRelightIndex = relightIndex;
            Objects.requireNonNull(world.getServer()).execute(() ->
                schedulePostProcessAcrossTicks(world, relightTargets, neighborTargets, nextRelightIndex, neighborIndex, onComplete)
            );
            return;
        }

        int endNeighborIndex = Math.min(neighborIndex + POST_PROCESS_PER_TICK, neighborTargets.size());
        applyNeighborUpdates(world, new LinkedHashSet<>(neighborTargets.subList(neighborIndex, endNeighborIndex)));

        if (endNeighborIndex < neighborTargets.size()) {
            int nextNeighborIndex = endNeighborIndex;
            Objects.requireNonNull(world.getServer()).execute(() ->
                schedulePostProcessAcrossTicks(world, relightTargets, neighborTargets, relightTargets.size(), nextNeighborIndex, onComplete)
            );
            return;
        }

        if (onComplete != null) {
            onComplete.run();
        }
    }

    private static int applyRelightBatch(ServerWorld world, List<BlockPos> relightTargets, int start, int end) {
        var lightProvider = world.getLightingProvider();
        for (int i = start; i < end; i++) {
            BlockPos pos = relightTargets.get(i);
            if (!isChunkLoaded(world, pos)) {
                continue;
            }
            ExceptionPolicy.runPerItem("relight " + pos, () -> lightProvider.checkBlock(pos), LOGGER);
        }
        return end;
    }

    private static void applyNeighborUpdates(ServerWorld world, Set<BlockPos> neighborTargets) {
        for (BlockPos pos : neighborTargets) {
            if (!isChunkLoaded(world, pos)) {
                continue;
            }
            ExceptionPolicy.runPerItem("neighbor update " + pos, () -> {
                BlockState currentState = world.getBlockState(pos);
                world.updateNeighbors(pos, currentState.getBlock());
                world.updateComparators(pos, currentState.getBlock());
            }, LOGGER);
        }
    }

    private static boolean isChunkLoaded(World world, BlockPos pos) {
        if (!(world instanceof ServerWorld serverWorld)) {
            return true;
        }
        return serverWorld.isChunkLoaded(new ChunkPos(pos).toLong());
    }

    private static void scheduleFallingBlockTicks(ServerWorld world, AppliedChangeResult applied) {
        for (int i = 0; i < applied.positions().size(); i++) {
            BlockState newState = applied.appliedStates().get(i);
            if (newState.getBlock() instanceof FallingBlock) {
                world.scheduleBlockTick(applied.positions().get(i), newState.getBlock(), 2);
            }
        }
    }

    private static void completeWith(Consumer<AppliedChangeResult> onComplete, AppliedChangeResult result) {
        if (onComplete != null) {
            onComplete.accept(result);
        }
    }

    public record BoundaryExtension(LinkedHashSet<BlockPos> positions, List<BlockState> originalStates,
                                    List<BlockState> newStates) {

        public int getSize() {
            return positions.size();
        }
    }
}
