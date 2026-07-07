package com.pushdozer.operations;

import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;
import net.minecraft.server.world.ServerWorld;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
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
        try {
            // 检查位置是否在世界边界内
            if (pos.getY() < world.getBottomY() || pos.getY() > world.getHeight()) {
                return false;
            }
            
            // 检查区块是否已加载：仅在服务端使用非弃用 long 重载
            if (world instanceof ServerWorld serverWorld) {
                return serverWorld.isChunkLoaded(new ChunkPos(pos).toLong());
            }
            // 非服务端上下文不应执行边界扩展校验，安全返回 false 以避免不必要处理
            return false;
        } catch (Exception e) {
            LOGGER.warn("边界位置验证失败: {}", pos, e);
            return false;
        }
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
            BlockState newState = states.get(i);
            try {
                world.setBlockState(pos, newState, flags);
            } catch (Exception e) {
                LOGGER.error("设置方块状态失败: {} -> {}", pos, newState, e);
            }
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

        world.getServer().execute(() ->
            scheduleBlockStatesAcrossTicks(world, positions, states, flags, endIndex, onComplete)
        );
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