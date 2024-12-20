package com.pushdozer.operations;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import net.minecraft.block.BlockState;
import net.minecraft.block.Block;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.network.packet.s2c.play.ChunkDataS2CPacket;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.chunk.WorldChunk;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

public class UndoRedoManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(UndoRedoManager.class);
    private static final int MAX_UNDO_REDO_STEPS = 30;
    private final Map<UUID, PlayerUndoRedoStacks> playerStacks = new HashMap<>();

    private static class PlayerUndoRedoStacks {
        final Stack<UndoAction> undoStack = new Stack<>();
        final Stack<UndoAction> redoStack = new Stack<>();
    }

    public void pushUndoAction(PlayerEntity player, UndoAction action) {
        UUID playerId = player.getUuid();
        PlayerUndoRedoStacks stacks = playerStacks.computeIfAbsent(playerId, k -> new PlayerUndoRedoStacks());
        if (stacks.undoStack.size() >= MAX_UNDO_REDO_STEPS) {
            stacks.undoStack.remove(0); // 移除最旧的操作
        }
        stacks.undoStack.push(action);
        stacks.redoStack.clear();
        // LOGGER.info("玩家 {} 添加了新的撤销操作，类型：{}，涉及 {} 个方块", player.getName().getString(), action.getType(), action.getPositions().size());
    }

    public void undoLastAction(PlayerEntity player, World world) {
        UUID playerId = player.getUuid();
        PlayerUndoRedoStacks stacks = playerStacks.get(playerId);
        if (stacks != null && !stacks.undoStack.isEmpty()) {
            UndoAction action = stacks.undoStack.pop();
            executeUndoRedoAction(action, player, world, true);
            stacks.redoStack.push(action);
            // LOGGER.info("玩家 {} 执行了撤销操作，类型：{}，涉及 {} 个方块", player.getName().getString(), action.getType(), action.getPositions().size());
        } else {
            // LOGGER.info("玩家 {} 尝试撤销，但没有可撤销的操作", player.getName().getString());
        }
    }

    public void redoLastAction(PlayerEntity player, World world) {
        UUID playerId = player.getUuid();
        PlayerUndoRedoStacks stacks = playerStacks.get(playerId);
        if (stacks != null && !stacks.redoStack.isEmpty()) {
            UndoAction action = stacks.redoStack.pop();
            executeUndoRedoAction(action, player, world, false);
            stacks.undoStack.push(action);
            // LOGGER.info("玩家 {} 执行了重做操作，类型：{}，涉及 {} 个方块", player.getName().getString(), action.getType(), action.getPositions().size());
        } else {
            // LOGGER.info("玩家 {} 尝试重做，但没有可重做的操作", player.getName().getString());
        }
    }

    private void executeUndoRedoAction(UndoAction action, PlayerEntity player, World world, boolean isUndo) {
        if (world == null || action == null) {
            // LOGGER.error("无效的撤销/重做操作：world 或 action 为空");
            return;
        }
        
        if (!(world instanceof ServerWorld serverWorld)) {
            // LOGGER.error("撤销/重做操作必须在服务器端执行");
            return;
        }
        
        List<BlockPos> positions = action.getPositions();
        List<BlockState> originalStates = action.getOriginalStates();
        List<BlockState> newStates = action.getNewStates();
        
        if (positions.size() != originalStates.size() || positions.size() != newStates.size()) {
            // LOGGER.error("状态列表长度不匹配：positions={}, originalStates={}, newStates={}", 
            //     positions.size(), originalStates.size(), newStates.size());
            return;
        }
        
        // 创建批量更新追踪器
        Set<ChunkPos> affectedChunks = new HashSet<>();
        
        for (int i = 0; i < positions.size(); i++) {
            BlockPos pos = positions.get(i);
            BlockState stateToSet = isUndo ? originalStates.get(i) : newStates.get(i);

            try {
                // 设置方块状态
                serverWorld.setBlockState(pos, stateToSet, Block.NOTIFY_ALL | Block.FORCE_STATE);

                // 更新相邻方块
                for (Direction direction : Direction.values()) {
                    BlockPos neighborPos = pos.offset(direction);
                    serverWorld.updateNeighborsAlways(neighborPos, stateToSet.getBlock());

                    // 更新相邻方块的渲染状态
                    serverWorld.updateListeners(neighborPos, serverWorld.getBlockState(neighborPos),
                        serverWorld.getBlockState(neighborPos), Block.NOTIFY_ALL | Block.FORCE_STATE);

                    // 更新相邻方块的光照
                    serverWorld.getLightingProvider().checkBlock(neighborPos);

                    // 标记相邻区块更新
                    serverWorld.getChunkManager().markForUpdate(neighborPos);
                }

                // 强制更新方块实体和渲染状态
                serverWorld.updateListeners(pos, stateToSet, stateToSet, Block.NOTIFY_ALL | Block.FORCE_STATE);

                // 更新光照
                serverWorld.getLightingProvider().checkBlock(pos);

                // 标记区块更新
                serverWorld.getChunkManager().markForUpdate(pos);

                // 记录受影响的区块（包括相邻区块）
                affectedChunks.add(new ChunkPos(pos));
                for (Direction direction : Direction.values()) {
                    affectedChunks.add(new ChunkPos(pos.offset(direction)));
                }

            } catch (Exception e) {
                // LOGGER.error("在位置 {} 设置方块状态时发生错误", pos, e);
            }
        }

        // 批量更新客户端
        if (player instanceof ServerPlayerEntity serverPlayer) {
            for (ChunkPos chunkPos : affectedChunks) {
                WorldChunk chunk = serverWorld.getChunk(chunkPos.x, chunkPos.z);
                // 发送完整的区块数据更新
                serverPlayer.networkHandler.sendPacket(new ChunkDataS2CPacket(chunk, serverWorld.getLightingProvider(), null, null));

                // 确保区块边界的渲染更新
                for (int x = -1; x <= 1; x++) {
                    for (int z = -1; z <= 1; z++) {
                        if (x != 0 || z != 0) {
                            ChunkPos neighborChunkPos = new ChunkPos(chunkPos.x + x, chunkPos.z + z);
                            WorldChunk neighborChunk = serverWorld.getChunk(neighborChunkPos.x, neighborChunkPos.z);
                            serverPlayer.networkHandler.sendPacket(new ChunkDataS2CPacket(neighborChunk, serverWorld.getLightingProvider(), null, null));
                        }
                    }
                }
            }
        }
    }

    private Set<ChunkPos> getAffectedChunks(List<BlockPos> positions) {
        Set<ChunkPos> chunks = new HashSet<>();
        for (BlockPos pos : positions) {
            chunks.add(new ChunkPos(pos));
        }
        return chunks;
    }

    public void clear(PlayerEntity player) {
        UUID playerId = player.getUuid();
        playerStacks.remove(playerId);
        // LOGGER.info("玩家 {} 的 UndoRedoManager 已清空", player.getName().getString());
    }

    public void clearAll() {
        playerStacks.clear();
        // LOGGER.info("所有玩家的 UndoRedoManager 已清空");
    }
}
