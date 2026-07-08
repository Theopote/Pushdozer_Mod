package com.pushdozer.operations;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.block.BlockState;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.ChunkDataS2CPacket;
import net.minecraft.network.packet.s2c.play.BlockUpdateS2CPacket;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.chunk.WorldChunk;
import net.minecraft.world.chunk.light.LightingProvider;

import com.pushdozer.util.ExceptionPolicy;

import java.util.*;
import java.util.Deque;
import java.util.ArrayDeque;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class UndoRedoManager {
    private static final Logger LOGGER = LoggerFactory.getLogger("pushdozer");
    private static final int MAX_UNDO_REDO_STEPS = 30;
    private static final int LARGE_OPERATION_THRESHOLD = 4096; // 大操作阈值
    private final Map<UUID, PlayerUndoRedoStacks> playerStacks = new HashMap<>();

    // 冷却与并发保护
    private final Map<UUID, Long> lastActionTime = new HashMap<>();
    private static final long ACTION_COOLDOWN_MS = 300; // 撤销/重做统一冷却
    private final Set<UUID> executingPlayers = new HashSet<>();

    private static class PlayerUndoRedoStacks {
        final Deque<UndoAction> undoStack = new ArrayDeque<>();
        final Deque<UndoAction> redoStack = new ArrayDeque<>();
    }

    public void pushUndoAction(PlayerEntity player, UndoAction action) {
        UUID playerId = player.getUuid();
        PlayerUndoRedoStacks stacks = playerStacks.computeIfAbsent(playerId, k -> new PlayerUndoRedoStacks());
        if (stacks.undoStack.size() >= MAX_UNDO_REDO_STEPS) {
            stacks.undoStack.removeFirst(); // 移除最旧的操作
        }
        stacks.undoStack.push(action);
        stacks.redoStack.clear();
        LOGGER.debug("玩家 {} 添加了新的撤销操作，类型：{}，涉及 {} 个方块，当前撤销栈大小: {}", 
            player.getName().getString(), action.getType(), action.getPositions().size(), stacks.undoStack.size());
    }

    public void undoLastAction(PlayerEntity player, World world) {
        UUID playerId = player.getUuid();
        LOGGER.debug("玩家 {} 尝试执行撤销操作", player.getName().getString());

        if (isCoolingDownOrExecuting(playerId)) return;
        markExecuting(playerId);
        Runnable finish = () -> {
            updateCooldown(playerId);
            unmarkExecuting(playerId);
        };
        try {
            PlayerUndoRedoStacks stacks = playerStacks.get(playerId);
            LOGGER.debug("玩家 {} 的撤销栈状态: undoStack={}, redoStack={}", 
                player.getName().getString(), 
                stacks != null ? stacks.undoStack.size() : "null", 
                stacks != null ? stacks.redoStack.size() : "null");
            
            if (stacks != null && !stacks.undoStack.isEmpty()) {
                UndoAction action = stacks.undoStack.pop();
                LOGGER.debug("开始执行撤销操作，类型: {}, 方块数: {}", action.getType(), action.getPositions().size());
                executeUndoRedoAction(action, player, world, true, success -> {
                    if (success) {
                        stacks.redoStack.push(action);
                        LOGGER.debug("玩家 {} 撤销成功，类型：{}，涉及 {} 个方块",
                            player.getName().getString(), action.getType(), action.getPositions().size());
                    } else {
                        LOGGER.warn("玩家 {} 撤销失败，操作被忽略", player.getName().getString());
                    }
                    finish.run();
                });
                return;
            }
            LOGGER.debug("玩家 {} 尝试撤销，但没有可撤销的操作", player.getName().getString());
            finish.run();
        } catch (RuntimeException e) {
            finish.run();
            throw e;
        }
    }

    public void redoLastAction(PlayerEntity player, World world) {
        UUID playerId = player.getUuid();
        if (isCoolingDownOrExecuting(playerId)) return;
        markExecuting(playerId);
        Runnable finish = () -> {
            updateCooldown(playerId);
            unmarkExecuting(playerId);
        };
        try {
            PlayerUndoRedoStacks stacks = playerStacks.get(playerId);
            if (stacks != null && !stacks.redoStack.isEmpty()) {
                UndoAction action = stacks.redoStack.pop();
                executeUndoRedoAction(action, player, world, false, success -> {
                    if (success) {
                        stacks.undoStack.push(action);
                        LOGGER.debug("玩家 {} 重做成功，类型：{}，涉及 {} 个方块",
                            player.getName().getString(), action.getType(), action.getPositions().size());
                    } else {
                        LOGGER.warn("玩家 {} 重做失败，操作被忽略", player.getName().getString());
                    }
                    finish.run();
                });
                return;
            }
            LOGGER.debug("玩家 {} 尝试重做，但没有可重做的操作", player.getName().getString());
            finish.run();
        } catch (RuntimeException e) {
            finish.run();
            throw e;
        }
    }

    protected void executeUndoRedoAction(UndoAction action, PlayerEntity player, World world, boolean isUndo,
                                         java.util.function.Consumer<Boolean> onFinished) {
        if (!(world instanceof ServerWorld serverWorld)) {
            LOGGER.error("Action must be performed on the server side.");
            onFinished.accept(false);
            return;
        }
        if (action == null || player == null) {
            LOGGER.error("Invalid action or player.");
            onFinished.accept(false);
            return;
        }
        
        if (!action.isValid()) {
            LOGGER.error("Invalid action data.");
            onFinished.accept(false);
            return;
        }
        
        List<BlockPos> positions = action.getPositions();
        List<BlockState> originalStates = action.getOriginalStates();
        List<BlockState> newStates = action.getNewStates();

        LOGGER.debug("开始执行{}操作，玩家: {}，涉及 {} 个方块", 
            isUndo ? "撤销" : "重做", player.getName().getString(), positions.size());

        List<BlockPos> validPositions = new ArrayList<>(positions.size());
        List<BlockState> validNewStates = new ArrayList<>(positions.size());
        int skipped = 0;
        for (int i = 0; i < positions.size(); i++) {
            BlockPos pos = positions.get(i);
            if (isValidPosition(pos, serverWorld)) {
                validPositions.add(pos);
                validNewStates.add(isUndo ? originalStates.get(i) : newStates.get(i));
            } else {
                skipped++;
            }
        }
        if (skipped > 0) {
            LOGGER.debug("位置验证跳过 {} 个不可用位置", skipped);
        }

        boolean isLarge = validPositions.size() >= LARGE_OPERATION_THRESHOLD;

        Runnable afterBlocksApplied = () -> {
            BlockOperation.postProcessBlockChanges(serverWorld, validPositions, validNewStates);
            syncUndoChangesToClient(serverWorld, player, validPositions, isLarge, isUndo);
            onFinished.accept(true);
        };

        if (validPositions.size() > BlockOperation.SYNC_BLOCK_LIMIT) {
            LOGGER.debug("跨 tick 分批应用 {} 个方块（每 tick 最多 {} 个）",
                validPositions.size(), BlockOperation.BLOCKS_PER_TICK);
            BlockOperation.batchSetBlockStates(validPositions, validNewStates, serverWorld,
                BlockOperation.BULK_WRITE_FLAGS, afterBlocksApplied);
        } else {
            BlockOperation.batchSetBlockStates(validPositions, validNewStates, serverWorld,
                BlockOperation.BULK_WRITE_FLAGS);
            afterBlocksApplied.run();
        }
    }

    /**
     * 将撤销/重做的方块变更同步给触发玩家。
     * <p>
     * 该方法被拆分为“小操作逐块同步 / 大操作按区块同步”两条路径，便于在单测中覆盖阈值分支，
     * 同时避免直接构造复杂的区块数据包对象导致测试脆弱。
     */
    protected void syncUndoChangesToClient(ServerWorld serverWorld, PlayerEntity player, List<BlockPos> validPositions,
                                          boolean isLarge, boolean isUndo) {
        if (!(player instanceof ServerPlayerEntity serverPlayer)) {
            LOGGER.debug("完成{}操作，已更新有效位置: {} (大操作: {})",
                isUndo ? "撤销" : "重做", validPositions.size(), isLarge);
            return;
        }

        LightingProvider lightProvider = serverWorld.getLightingProvider();
        if (!isLarge) {
            syncSmallOperation(serverWorld, serverPlayer, validPositions);
        } else {
            syncLargeOperation(serverWorld, serverPlayer, validPositions, lightProvider);
        }

        LOGGER.debug("完成{}操作，已更新有效位置: {} (大操作: {})",
            isUndo ? "撤销" : "重做", validPositions.size(), isLarge);
    }

    protected void syncSmallOperation(ServerWorld serverWorld, ServerPlayerEntity serverPlayer, List<BlockPos> validPositions) {
        for (BlockPos pos : validPositions) {
            if (!serverWorld.isChunkLoaded(new ChunkPos(pos).toLong())) {
                continue;
            }
            ExceptionPolicy.runPerItem("发送方块更新 " + pos, () -> {
                BlockState currentState = serverWorld.getBlockState(pos);
                sendPacket(serverPlayer, new BlockUpdateS2CPacket(pos, currentState));
            }, LOGGER);
        }
    }

    protected void syncLargeOperation(ServerWorld serverWorld, ServerPlayerEntity serverPlayer, List<BlockPos> validPositions,
                                      LightingProvider lightProvider) {
        Set<ChunkPos> affectedChunks = new HashSet<>();
        for (BlockPos pos : validPositions) {
            affectedChunks.add(new ChunkPos(pos));
        }
        sendChunks(serverWorld, serverPlayer, affectedChunks, lightProvider, "大操作快速同步");
        Objects.requireNonNull(serverWorld.getServer()).execute(() ->
            sendChunks(serverWorld, serverPlayer, affectedChunks, lightProvider, "延迟光照同步")
        );
    }

    protected void sendChunks(ServerWorld serverWorld, ServerPlayerEntity serverPlayer, Set<ChunkPos> chunks,
                              LightingProvider lightProvider, String reason) {
        int sent = 0;
        for (ChunkPos chunkPos : chunks) {
            if (!serverWorld.isChunkLoaded(chunkPos.toLong())) {
                continue;
            }
            WorldChunk chunk = serverWorld.getChunk(chunkPos.x, chunkPos.z);
            if (chunk == null) {
                continue;
            }
            ExceptionPolicy.runPerItem("发送区块数据 " + chunkPos, () ->
                sendPacket(serverPlayer, new ChunkDataS2CPacket(chunk, lightProvider, null, null)),
                LOGGER);
            sent++;
        }
        LOGGER.debug("{}：发送 {} 个区块到玩家 {}", reason, sent, serverPlayer.getName().getString());
    }

    /**
     * 发送数据包到客户端。抽为可覆盖点，便于在 GameTest 中捕获实际发送的包类型。
     */
    protected void sendPacket(ServerPlayerEntity player, Packet<?> packet) {
        player.networkHandler.sendPacket(packet);
    }
    
    private boolean isValidPosition(BlockPos pos, ServerWorld world) {
        if (pos.getY() < world.getBottomY() || pos.getY() > world.getHeight()) {
            return false;
        }
        return world.isChunkLoaded(new ChunkPos(pos).toLong());
    }

    private boolean isCoolingDownOrExecuting(UUID playerId) {
        long now = System.currentTimeMillis();
        Long last = lastActionTime.get(playerId);
        if (executingPlayers.contains(playerId)) return true;
        return last != null && now - last < ACTION_COOLDOWN_MS;
    }

    private void updateCooldown(UUID playerId) {
        lastActionTime.put(playerId, System.currentTimeMillis());
    }

    private void markExecuting(UUID playerId) { executingPlayers.add(playerId); }
    private void unmarkExecuting(UUID playerId) { executingPlayers.remove(playerId); }

    /**
     * 调试方法：检查玩家的撤销栈状态
     */
    public void debugPlayerStacks(PlayerEntity player) {
        UUID playerId = player.getUuid();
        PlayerUndoRedoStacks stacks = playerStacks.get(playerId);
        
        if (stacks == null) {
            LOGGER.debug("玩家 {} 没有撤销栈", player.getName().getString());
        } else {
            LOGGER.debug("玩家 {} 的撤销栈状态: undoStack={}, redoStack={}", 
                player.getName().getString(), stacks.undoStack.size(), stacks.redoStack.size());
            
            if (!stacks.undoStack.isEmpty()) {
                UndoAction topAction = stacks.undoStack.peek();
                LOGGER.debug("栈顶操作: 类型={}, 方块数={}", topAction.getType(), topAction.getPositions().size());
            }
        }
    }

    /**
     * 获取玩家的撤销栈大小
     */
    public int getUndoStackSize(PlayerEntity player) {
        UUID playerId = player.getUuid();
        PlayerUndoRedoStacks stacks = playerStacks.get(playerId);
        return stacks != null ? stacks.undoStack.size() : 0;
    }
}
