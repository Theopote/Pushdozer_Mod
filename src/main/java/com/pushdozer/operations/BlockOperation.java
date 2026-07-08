package com.pushdozer.operations;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.FallingBlock;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;
import net.minecraft.server.world.ServerWorld;
import com.pushdozer.util.ExceptionPolicy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

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
    
    /**
     * 收集边界扩展位置
     * @param positions 原始位置列表
     * @param world 世界实例
     * @return 边界扩展信息
     */
    public static BoundaryExtension collectBoundaryExtension(List<BlockPos> positions, World world) {
        Set<BlockPos> boundaryPositions = new HashSet<>();
        List<BlockState> boundaryOriginalStates = new ArrayList<>();
        List<BlockState> boundaryNewStates = new ArrayList<>();
        
        // 收集所有需要扩展的位置
        Set<BlockPos> allPositions = new HashSet<>(positions);
        
        for (BlockPos pos : positions) {
            // 添加直接邻居（6个方向）
            BlockPos[] neighbors = {
                pos.north(), pos.south(), pos.east(), pos.west(), pos.up(), pos.down()
            };
            
            for (BlockPos neighbor : neighbors) {
                if (!allPositions.contains(neighbor) && isValidBoundaryPosition(neighbor, world)) {
                    if (boundaryPositions.add(neighbor)) { // 仅在成功加入集合时记录状态
                        BlockState s = world.getBlockState(neighbor);
                        boundaryOriginalStates.add(s);
                        boundaryNewStates.add(s); // 边界位置的新状态保持原样
                    }
                }
            }
            
            // 添加对角线邻居（用于更好的边界处理）
            BlockPos[] diagonalNeighbors = {
                pos.north().east(), pos.north().west(), pos.south().east(), pos.south().west(),
                pos.up().north(), pos.up().south(), pos.up().east(), pos.up().west(),
                pos.down().north(), pos.down().south(), pos.down().east(), pos.down().west()
            };
            
            for (BlockPos diagonalNeighbor : diagonalNeighbors) {
                if (!allPositions.contains(diagonalNeighbor) && isValidBoundaryPosition(diagonalNeighbor, world)) {
                    if (boundaryPositions.add(diagonalNeighbor)) { // 仅在成功加入集合时记录状态
                        BlockState s = world.getBlockState(diagonalNeighbor);
                        boundaryOriginalStates.add(s);
                        boundaryNewStates.add(s);
                    }
                }
            }
        }
        
        return new BoundaryExtension(boundaryPositions, boundaryOriginalStates, boundaryNewStates);
    }
    
    /**
     * 验证边界位置是否有效
     */
    private static boolean isValidBoundaryPosition(BlockPos pos, World world) {
        if (pos.getY() < world.getBottomY() || pos.getY() > world.getHeight()) {
            return false;
        }
        if (world instanceof ServerWorld serverWorld) {
            return serverWorld.isChunkLoaded(new ChunkPos(pos).toLong());
        }
        return false;
    }
    
    /**
     * 批量设置方块状态（同步完成）。
     * 超过 {@link #SYNC_BLOCK_LIMIT} 的大操作请使用带 {@code onComplete} 的重载以跨 tick 调度。
     */
    public static void batchSetBlockStates(List<BlockPos> positions, List<BlockState> states, World world, int flags) {
        batchSetBlockStates(positions, states, world, flags, null);
    }

    /**
     * 批量设置方块状态。
     * <p>
     * 小操作在同一 tick 内完成；大操作通过 {@code server.execute()} 拆到后续 tick，
     * 全部完成后调用 {@code onComplete}（仍在服务端主线程）。
     *
     * @return 若工作已同步完成返回 {@code true}，若已排队跨 tick 执行返回 {@code false}
     */
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

    /** 大操作后处理阈值：超过此数量时使用列顶光照而非逐方块更新 */
    public static final int LARGE_POST_PROCESS_THRESHOLD = 4096;

    /**
     * 批量写入 flags：仅同步客户端，跳过逐块邻居更新与光照重算（写入完成后统一 post-process）。
     * 类似 WorldEdit 的 fast mode 写法。
     */
    public static final int BULK_WRITE_FLAGS = Block.NOTIFY_LISTENERS | Block.FORCE_STATE | Block.SKIP_DROPS;

    /**
     * 应用地形工具收集的方块变更，大操作自动跨 tick 调度。
     * 写入阶段使用 {@link #BULK_WRITE_FLAGS}，全部完成后统一 relight / 邻居更新。
     */
    public static void applyTerrainChanges(ServerWorld world, List<BlockPos> positions, List<BlockState> newStates,
                                           Runnable onComplete) {
        batchSetBlockStates(positions, newStates, world, BULK_WRITE_FLAGS, () -> {
            postProcessBlockChanges(world, positions, newStates);
            if (onComplete != null) {
                onComplete.run();
            }
        });
    }

    /**
     * 应用放置模式收集的方块变更，并在全部写入后执行光照/邻居更新。
     */
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

    /**
     * 批量写入后的统一后处理：小操作逐方块 relight + 邻居更新；大操作仅按列顶补光照。
     */
    public static void postProcessBlockChanges(ServerWorld world, List<BlockPos> positions, List<BlockState> newStates) {
        if (positions.isEmpty()) {
            return;
        }

        var lightProvider = world.getLightingProvider();
        boolean isLarge = positions.size() >= LARGE_POST_PROCESS_THRESHOLD;

        if (!isLarge) {
            for (int i = 0; i < positions.size(); i++) {
                BlockPos pos = positions.get(i);
                if (!isChunkLoaded(world, pos)) {
                    continue;
                }
                ExceptionPolicy.runPerItem("方块后处理 " + pos, () -> {
                    lightProvider.checkBlock(pos);
                    BlockState currentState = world.getBlockState(pos);
                    world.updateNeighbors(pos, currentState.getBlock());
                    world.updateNeighbors(pos.down(), currentState.getBlock());
                    world.updateComparators(pos, currentState.getBlock());
                }, LOGGER);
            }
            return;
        }

        Map<Long, Integer> xzToTopY = new HashMap<>();
        for (BlockPos pos : positions) {
            long key = (((long) pos.getX()) << 32) ^ (pos.getZ() & 0xffffffffL);
            xzToTopY.merge(key, pos.getY(), Math::max);
        }
        for (Map.Entry<Long, Integer> entry : xzToTopY.entrySet()) {
            int x = (int) (entry.getKey() >> 32);
            int z = (int) entry.getKey().longValue();
            int topY = entry.getValue();
            BlockPos topPos = new BlockPos(x, topY, z);
            if (!isChunkLoaded(world, topPos)) {
                continue;
            }
            ExceptionPolicy.runPerItem("列顶光照 " + topPos, () -> {
                lightProvider.checkBlock(topPos);
                lightProvider.checkBlock(topPos.up());
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

    /**
     * 边界扩展信息类
     */
    public static class BoundaryExtension {
        private final Set<BlockPos> positions;
        private final List<BlockState> originalStates;
        private final List<BlockState> newStates;
        
        public BoundaryExtension(Set<BlockPos> positions, List<BlockState> originalStates, List<BlockState> newStates) {
            this.positions = positions;
            this.originalStates = originalStates;
            this.newStates = newStates;
        }
        
        public Set<BlockPos> getPositions() {
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