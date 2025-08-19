package com.pushdozer.operations;

import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.List;
import java.util.Set;
import java.util.HashSet;
import java.util.ArrayList;

public class UndoAction {
    public enum ActionType {
        PLACE,           // 铺设操作
        BREAK,           // 挖掘操作
        SMOOTH,          // 自适应平滑操作
        SMOOTH_RAISE,    // 平滑提升操作
        SMOOTH_LOWER,    // 平滑降低操作
        SURFACE_ROUGHEN, // 表面粗糙操作
        SURFACE_CONVERT, // 表层转换操作
        BONE_MEAL,       // 骨粉操作
        BATCH_PLANT,     // 批量种植操作
        SHORELINE_PROCESS // 水岸处理操作
    }

    private final ActionType type;
    private final List<BlockPos> positions;
    private final List<BlockState> originalStates;
    private final List<BlockState> newStates;
    
    // 新增：边界扩展信息
    private final Set<BlockPos> boundaryPositions;
    private final List<BlockState> boundaryOriginalStates;
    private final List<BlockState> boundaryNewStates;

    public UndoAction(ActionType type, List<BlockPos> positions, List<BlockState> originalStates, List<BlockState> newStates) {
        this.type = type;
        this.positions = positions;
        this.originalStates = originalStates;
        this.newStates = newStates;
        
        // 初始化边界扩展信息
        this.boundaryPositions = new HashSet<>();
        this.boundaryOriginalStates = new ArrayList<>();
        this.boundaryNewStates = new ArrayList<>();
    }
    
    /**
     * 带边界扩展的构造函数
     */
    public UndoAction(ActionType type, List<BlockPos> positions, List<BlockState> originalStates, List<BlockState> newStates,
                     Set<BlockPos> boundaryPositions, List<BlockState> boundaryOriginalStates, List<BlockState> boundaryNewStates) {
        this.type = type;
        this.positions = positions;
        this.originalStates = originalStates;
        this.newStates = newStates;
        this.boundaryPositions = boundaryPositions != null ? boundaryPositions : new HashSet<>();
        this.boundaryOriginalStates = boundaryOriginalStates != null ? boundaryOriginalStates : new ArrayList<>();
        this.boundaryNewStates = boundaryNewStates != null ? boundaryNewStates : new ArrayList<>();
    }

    public List<BlockPos> getPositions() {
        return positions;
    }

    public List<BlockState> getOriginalStates() {
        return originalStates;
    }

    public List<BlockState> getNewStates() {
        return newStates;
    }
    
    public ActionType getType() {
        return type;
    }
    
    /**
     * 获取所有位置（包括边界位置）
     */
    public List<BlockPos> getAllPositions() {
        List<BlockPos> allPositions = new ArrayList<>(positions);
        allPositions.addAll(boundaryPositions);
        return allPositions;
    }
    
    /**
     * 获取所有原始状态（包括边界状态）
     */
    public List<BlockState> getAllOriginalStates() {
        List<BlockState> allOriginalStates = new ArrayList<>(originalStates);
        allOriginalStates.addAll(boundaryOriginalStates);
        return allOriginalStates;
    }
    
    /**
     * 获取所有新状态（包括边界状态）
     */
    public List<BlockState> getAllNewStates() {
        List<BlockState> allNewStates = new ArrayList<>(newStates);
        allNewStates.addAll(boundaryNewStates);
        return allNewStates;
    }
    
    /**
     * 获取边界位置
     */
    public Set<BlockPos> getBoundaryPositions() {
        return boundaryPositions;
    }
    
    /**
     * 获取边界原始状态
     */
    public List<BlockState> getBoundaryOriginalStates() {
        return boundaryOriginalStates;
    }
    
    /**
     * 获取边界新状态
     */
    public List<BlockState> getBoundaryNewStates() {
        return boundaryNewStates;
    }
    
    /**
     * 验证操作数据的完整性（仅核心数据，不强制边界数据）
     */
    public boolean isValid() {
        if (positions == null || originalStates == null || newStates == null) {
            return false;
        }
        return positions.size() == originalStates.size() && positions.size() == newStates.size();
    }
    
    /**
     * 获取操作的总方块数
     */
    public int getTotalBlockCount() {
        return positions.size() + boundaryPositions.size();
    }
}
