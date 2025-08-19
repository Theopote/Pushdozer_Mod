package com.pushdozer.operations;

import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;
import net.minecraft.server.world.ServerWorld;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * BlockOperation 工具类
 * 用于处理批量方块操作和边界扩展
 */
public class BlockOperation {
    private static final Logger LOGGER = LoggerFactory.getLogger("pushdozer");
    
    // 边界扩展配置
    private static final int MAX_BATCH_SIZE = 4096; // 提升批量大小减少循环次数
    
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
     * 批量设置方块状态
     * @param positions 位置列表
     * @param states 状态列表
     * @param world 世界实例
     * @param flags 设置标志
     */
    public static void batchSetBlockStates(List<BlockPos> positions, List<BlockState> states, World world, int flags) {
        if (positions.size() != states.size()) {
            LOGGER.error("位置和状态列表大小不匹配: {} vs {}", positions.size(), states.size());
            return;
        }
        
        // 分批处理以避免一次性占用过长时间
        for (int i = 0; i < positions.size(); i += MAX_BATCH_SIZE) {
            int endIndex = Math.min(i + MAX_BATCH_SIZE, positions.size());
            List<BlockPos> batchPositions = positions.subList(i, endIndex);
            List<BlockState> batchStates = states.subList(i, endIndex);
            
            // 批量设置方块
            for (int j = 0; j < batchPositions.size(); j++) {
                BlockPos pos = batchPositions.get(j);
                BlockState newState = batchStates.get(j);
                
                try {
                    world.setBlockState(pos, newState, flags);
                } catch (Exception e) {
                    LOGGER.error("设置方块状态失败: {} -> {}", pos, newState, e);
                }
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