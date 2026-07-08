package com.pushdozer.services;

import com.pushdozer.operations.UndoAction;
import com.pushdozer.operations.UndoRedoManager;
import net.minecraft.entity.player.PlayerEntity;

/**
 * UndoRedoService 类负责管理所有撤销/重做相关的操作
 */
public class UndoRedoService {
    private static final Logger LOGGER = LoggerFactory.getLogger("pushdozer");
    private static UndoRedoService instance;
    private final UndoRedoManager undoRedoManager;

    private UndoRedoService() {
        this.undoRedoManager = new UndoRedoManager();
    }

    /**
     * 获取 UndoRedoService 的单例实例
     */
    public static UndoRedoService getInstance() {
        if (instance == null) {
            instance = new UndoRedoService();
        }
        return instance;
    }

    /**
     * 添加撤销操作
     * @param player 执行操作的玩家
     * @param action 撤销操作
     */
    public void pushUndoAction(PlayerEntity player, UndoAction action) {
        undoRedoManager.pushUndoAction(player, action);
    }

    /**
     * 执行撤销操作
     * @param player 执行操作的玩家
     * @param world 世界实例
     */
    public void undoLastAction(PlayerEntity player, World world) {
        undoRedoManager.undoLastAction(player, world);
        updatePlayerPosition(player);
    }

    /**
     * 执行重做操作
     * @param player 执行操作的玩家
     * @param world 世界实例
     */
    public void redoLastAction(PlayerEntity player, World world) {
        undoRedoManager.redoLastAction(player, world);
        updatePlayerPosition(player);
    }

    /**
     * 调试方法：检查玩家的撤销栈状态
     */
    public void debugPlayerStacks(PlayerEntity player) {
        undoRedoManager.debugPlayerStacks(player);
    }

    /**
     * 获取玩家的撤销栈大小
     */
    public int getUndoStackSize(PlayerEntity player) {
        return undoRedoManager.getUndoStackSize(player);
    }

    /**
     * 更新玩家位置
     */
    private void updatePlayerPosition(PlayerEntity player) {
        if (player instanceof ServerPlayerEntity serverPlayer) {
            ServerWorld serverWorld = serverPlayer.getEntityWorld();
            serverWorld.getChunkManager().updatePosition(serverPlayer);
        }
    }
} 