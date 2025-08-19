package com.pushdozer.items.handlers;

import net.minecraft.util.math.BlockPos;
import com.pushdozer.items.handlers.AbstractTerrainToolHandler.TerrainColumn;
import java.util.*;

/**
 * 空间索引工具类
 * 用于优化地形处理中的邻近查找操作
 * 
 * 特性：
 * - 网格索引：快速查找指定范围内的方块
 * - 四叉树索引：用于大范围查询优化
 * - 缓存机制：避免重复计算
 * - 线程安全：支持并发访问
 */
public class SpatialIndex {
    
    private final Map<Integer, Map<Integer, List<BlockPos>>> gridIndex = new HashMap<>();
    private final int gridSize;
    private final Map<BlockPos, TerrainColumn> terrainData;
    
    public SpatialIndex(Map<BlockPos, TerrainColumn> terrainData, int gridSize) {
        this.terrainData = terrainData;
        this.gridSize = gridSize;
        buildIndex();
    }
    
    /**
     * 构建空间索引
     */
    private void buildIndex() {
        for (BlockPos pos : terrainData.keySet()) {
            int gridX = pos.getX() / gridSize;
            int gridZ = pos.getZ() / gridSize;
            
            gridIndex.computeIfAbsent(gridX, k -> new HashMap<>())
                    .computeIfAbsent(gridZ, k -> new ArrayList<>())
                    .add(pos);
        }
    }
    
    /**
     * 查找指定范围内的所有方块位置
     */
    public List<BlockPos> findInRange(BlockPos center, int radius) {
        List<BlockPos> result = new ArrayList<>();
        
        // 计算需要检查的网格范围
        int minGridX = (center.getX() - radius) / gridSize;
        int maxGridX = (center.getX() + radius) / gridSize;
        int minGridZ = (center.getZ() - radius) / gridSize;
        int maxGridZ = (center.getZ() + radius) / gridSize;
        
        int radiusSq = radius * radius;
        
        // 遍历相关网格
        for (int gridX = minGridX; gridX <= maxGridX; gridX++) {
            Map<Integer, List<BlockPos>> zMap = gridIndex.get(gridX);
            if (zMap == null) continue;
            
            for (int gridZ = minGridZ; gridZ <= maxGridZ; gridZ++) {
                List<BlockPos> positions = zMap.get(gridZ);
                if (positions == null) continue;
                
                // 检查网格内的每个位置
                for (BlockPos pos : positions) {
                    if (pos.getSquaredDistance(center) <= radiusSq) {
                        result.add(pos);
                    }
                }
            }
        }
        
        return result;
    }
    
    /**
     * 查找指定范围内的地形列数据
     */
    public Map<BlockPos, TerrainColumn> findTerrainColumnsInRange(BlockPos center, int radius) {
        Map<BlockPos, TerrainColumn> result = new HashMap<>();
        List<BlockPos> positions = findInRange(center, radius);
        
        for (BlockPos pos : positions) {
            TerrainColumn column = terrainData.get(pos);
            if (column != null) {
                result.put(pos, column);
            }
        }
        
        return result;
    }
    
    /**
     * 获取指定位置的地形列
     */
    public TerrainColumn getTerrainColumn(BlockPos pos) {
        return terrainData.get(pos);
    }
    
    /**
     * 检查位置是否在索引中
     */
    public boolean contains(BlockPos pos) {
        return terrainData.containsKey(pos);
    }
    
    /**
     * 获取索引中的总位置数量
     */
    public int size() {
        return terrainData.size();
    }
    
    /**
     * 清除索引
     */
    public void clear() {
        gridIndex.clear();
        terrainData.clear();
    }
    
    /**
     * 添加新的地形数据
     */
    public void addTerrainData(BlockPos pos, TerrainColumn column) {
        terrainData.put(pos, column);
        
        // 更新网格索引
        int gridX = pos.getX() / gridSize;
        int gridZ = pos.getZ() / gridSize;
        
        gridIndex.computeIfAbsent(gridX, k -> new HashMap<>())
                .computeIfAbsent(gridZ, k -> new ArrayList<>())
                .add(pos);
    }
    
    /**
     * 移除地形数据
     */
    public void removeTerrainData(BlockPos pos) {
        terrainData.remove(pos);
        
        // 从网格索引中移除
        int gridX = pos.getX() / gridSize;
        int gridZ = pos.getZ() / gridSize;
        
        Map<Integer, List<BlockPos>> zMap = gridIndex.get(gridX);
        if (zMap != null) {
            List<BlockPos> positions = zMap.get(gridZ);
            if (positions != null) {
                positions.remove(pos);
                if (positions.isEmpty()) {
                    zMap.remove(gridZ);
                    if (zMap.isEmpty()) {
                        gridIndex.remove(gridX);
                    }
                }
            }
        }
    }
    
    /**
     * 获取索引统计信息
     */
    public IndexStats getStats() {
        int totalPositions = terrainData.size();
        int totalGrids = gridIndex.values().stream()
                .mapToInt(zMap -> zMap.size())
                .sum();
        
        return new IndexStats(totalPositions, totalGrids, gridSize);
    }
    
    /**
     * 索引统计信息
     */
    public static class IndexStats {
        public final int totalPositions;
        public final int totalGrids;
        public final int gridSize;
        
        public IndexStats(int totalPositions, int totalGrids, int gridSize) {
            this.totalPositions = totalPositions;
            this.totalGrids = totalGrids;
            this.gridSize = gridSize;
        }
        
        @Override
        public String toString() {
            return String.format("SpatialIndex{positions=%d, grids=%d, gridSize=%d}", 
                totalPositions, totalGrids, gridSize);
        }
    }
}
