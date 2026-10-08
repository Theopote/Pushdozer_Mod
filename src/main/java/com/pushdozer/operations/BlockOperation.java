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
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

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
        batchSetBlockStates(positions, states, world, flags, null);
    }

    public static boolean batchSetBlockStates(List<BlockPos> positions, List<BlockState> states, World world,
                                              int flags, Runnable onComplete) {
        if (positions.size() != states.size()) {
            LOGGER.error("位置和状态列表大小不匹配: {} vs {}", positions.size(), states.size());
            if (onComplete != null) {
                onComplete.run();
            }
            return true;
        }

        if (positions.isEmpty()) {
            if (onComplete != null) {
                onComplete.run();
            }
            return true;
        }

        if (!(world instanceof ServerWorld serverWorld) || positions.size() <= SYNC_BLOCK_LIMIT) {
            applyBlockStates(positions, states, world, flags, 0, positions.size());
            if (onComplete != null) {
                onComplete.run();
            }
            return true;
        }

        scheduleBlockStatesAcrossTicks(serverWorld, positions, states, flags, 0, onComplete);
        return false;
    }

    private static void applyBlockStates(List<BlockPos> positions, List<BlockState> states, World world,
                                         int flags, int startIndex, int endIndex) {
        for (int i = startIndex; i < endIndex; i++) {
            BlockPos pos = positions.get(i);
            if (!isChunkLoaded(world, pos)) {
                LOGGER.debug("跳过未加载区块: {}", pos);
                continue;
            }
            BlockState newState = states.get(i);
            ExceptionPolicy.runPerItem("设置方块状态 " + pos, () -> world.setBlockState(pos, newState, flags), LOGGER);
        }
    }

    private static void scheduleBlockStatesAcrossTicks(ServerWorld world, List<BlockPos> positions,
                                                       List<BlockState> states, int flags, int startIndex,
                                                       Runnable onComplete) {
        int endIndex = Math.min(startIndex + BLOCKS_PER_TICK, positions.size());
        applyBlockStates(positions, states, world, flags, startIndex, endIndex);

        if (endIndex >= positions.size()) {
            if (onComplete != null) {
                onComplete.run();
            }
            return;
        }

        Objects.requireNonNull(world.getServer()).execute(() ->
            scheduleBlockStatesAcrossTicks(world, positions, states, flags, endIndex, onComplete)
        );
    }

    public static void applyTerrainChanges(ServerWorld world, List<BlockPos> positions, List<BlockState> newStates,
                                           Runnable onComplete) {
        batchSetBlockStates(positions, newStates, world, BULK_WRITE_FLAGS, () -> {
            postProcessBlockChanges(world, positions, newStates);
            if (onComplete != null) {
                onComplete.run();
            }
        });
    }

    public static void applyPlacementChanges(ServerWorld world, List<BlockPos> positions, List<BlockState> newStates,
                                             Runnable onComplete) {
        batchSetBlockStates(positions, newStates, world, BULK_WRITE_FLAGS, () -> {
            scheduleFallingBlockTicks(world, positions, newStates);
            postProcessBlockChanges(world, positions, newStates);
            if (onComplete != null) {
                onComplete.run();
            }
        });
    }

    public static void postProcessBlockChanges(ServerWorld world, List<BlockPos> positions, List<BlockState> newStates) {
        if (positions.isEmpty()) {
            return;
        }

        Set<BlockPos> changed = new HashSet<>(positions);
        List<BlockPos> relightTargets = new ArrayList<>(positions);
        Set<BlockPos> neighborTargets = collectNeighborUpdateTargets(world, changed);

        if (positions.size() >= LARGE_POST_PROCESS_THRESHOLD) {
            schedulePostProcessAcrossTicks(world, relightTargets, new ArrayList<>(neighborTargets), 0, 0);
            return;
        }

        applyRelightBatch(world, relightTargets, 0, relightTargets.size());
        applyNeighborUpdates(world, neighborTargets);
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
                                                       int neighborIndex) {
        relightIndex = applyRelightBatch(world, relightTargets, relightIndex,
            Math.min(relightIndex + POST_PROCESS_PER_TICK, relightTargets.size()));

        if (relightIndex < relightTargets.size()) {
            int nextRelightIndex = relightIndex;
            Objects.requireNonNull(world.getServer()).execute(() ->
                schedulePostProcessAcrossTicks(world, relightTargets, neighborTargets, nextRelightIndex, neighborIndex)
            );
            return;
        }

        int endNeighborIndex = Math.min(neighborIndex + POST_PROCESS_PER_TICK, neighborTargets.size());
        applyNeighborUpdates(world, new LinkedHashSet<>(neighborTargets.subList(neighborIndex, endNeighborIndex)));

        if (endNeighborIndex < neighborTargets.size()) {
            int nextNeighborIndex = endNeighborIndex;
            Objects.requireNonNull(world.getServer()).execute(() ->
                schedulePostProcessAcrossTicks(world, relightTargets, neighborTargets, relightTargets.size(), nextNeighborIndex)
            );
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

    private static void scheduleFallingBlockTicks(ServerWorld world, List<BlockPos> positions, List<BlockState> newStates) {
        for (int i = 0; i < positions.size(); i++) {
            BlockState newState = newStates.get(i);
            if (newState.getBlock() instanceof FallingBlock) {
                world.scheduleBlockTick(positions.get(i), newState.getBlock(), 2);
            }
        }
    }

    public static class BoundaryExtension {
        private final LinkedHashSet<BlockPos> positions;
        private final List<BlockState> originalStates;
        private final List<BlockState> newStates;

        public BoundaryExtension(LinkedHashSet<BlockPos> positions, List<BlockState> originalStates, List<BlockState> newStates) {
            this.positions = positions;
            this.originalStates = originalStates;
            this.newStates = newStates;
        }

        public LinkedHashSet<BlockPos> getPositions() {
            return positions;
        }

        public List<BlockState> getOriginalStates() {
            return originalStates;
        }

        public List<BlockState> getNewStates() {
            return newStates;
        }

        public int getSize() {
            return positions.size();
        }
    }
}
