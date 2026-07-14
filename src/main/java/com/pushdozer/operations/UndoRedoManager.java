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
import java.util.concurrent.ConcurrentHashMap;
import java.util.Deque;
import java.util.ArrayDeque;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class UndoRedoManager {
    private static final Logger LOGGER = LoggerFactory.getLogger("pushdozer");
    private static final int MAX_UNDO_REDO_STEPS = 30;
    private static final int LARGE_OPERATION_THRESHOLD = 4096; // Large operation threshold
    private final Map<UUID, PlayerUndoRedoStacks> playerStacks = new ConcurrentHashMap<>();

    // Cooldown and concurrency protection
    private final Map<UUID, Long> lastActionTime = new ConcurrentHashMap<>();
    private static final long ACTION_COOLDOWN_MS = 300; // Unified cooldown for undo/redo
    private final Set<UUID> executingPlayers = ConcurrentHashMap.newKeySet();

    private static class PlayerUndoRedoStacks {
        final Deque<UndoAction> undoStack = new ArrayDeque<>();
        final Deque<UndoAction> redoStack = new ArrayDeque<>();
    }

    public void pushUndoAction(PlayerEntity player, UndoAction action) {
        UUID playerId = player.getUuid();
        PlayerUndoRedoStacks stacks = playerStacks.computeIfAbsent(playerId, k -> new PlayerUndoRedoStacks());
        if (stacks.undoStack.size() >= MAX_UNDO_REDO_STEPS) {
            stacks.undoStack.removeFirst(); // Remove the oldest operation
        }
        stacks.undoStack.push(action);
        stacks.redoStack.clear();
        LOGGER.debug("Player {} added new undo action, type: {}, affected blocks: {}, current undo stack size: {}",
            player.getName().getString(), action.getType(), action.getPositions().size(), stacks.undoStack.size());
    }

    public void undoLastAction(PlayerEntity player, World world) {
        UUID playerId = player.getUuid();
        LOGGER.debug("Player {} attempting to execute undo operation", player.getName().getString());

        if (isCoolingDownOrExecuting(playerId)) return;
        markExecuting(playerId);

        try {
            PlayerUndoRedoStacks stacks = playerStacks.get(playerId);
            LOGGER.debug("Player {} undo stack state: undoStack={}, redoStack={}",
                player.getName().getString(),
                stacks != null ? stacks.undoStack.size() : "null",
                stacks != null ? stacks.redoStack.size() : "null");

            if (stacks != null && !stacks.undoStack.isEmpty()) {
                UndoAction action = stacks.undoStack.pop();
                LOGGER.debug("Starting undo operation, type: {}, block count: {}", action.getType(), action.getPositions().size());
                executeUndoRedoAction(action, player, world, true, success -> {
                    try {
                        if (success) {
                            stacks.redoStack.push(action);
                            LOGGER.debug("Player {} undo successful, type: {}, affected blocks: {}",
                                player.getName().getString(), action.getType(), action.getPositions().size());
                        } else {
                            LOGGER.warn("Player {} undo failed, operation ignored", player.getName().getString());
                        }
                    } finally {
                        updateCooldown(playerId);
                        unmarkExecuting(playerId);
                    }
                });
                return;
            }
            LOGGER.debug("Player {} attempted undo, but no operations available to undo", player.getName().getString());
        } catch (RuntimeException e) {
            LOGGER.error("Player {} encountered exception during undo operation", player.getName().getString(), e);
            throw e;
        } finally {
            // Ensure state cleanup even on exception
            if (executingPlayers.contains(playerId)) {
                updateCooldown(playerId);
                unmarkExecuting(playerId);
            }
        }
    }

    public void redoLastAction(PlayerEntity player, World world) {
        UUID playerId = player.getUuid();
        if (isCoolingDownOrExecuting(playerId)) return;
        markExecuting(playerId);

        try {
            PlayerUndoRedoStacks stacks = playerStacks.get(playerId);
            if (stacks != null && !stacks.redoStack.isEmpty()) {
                UndoAction action = stacks.redoStack.pop();
                executeUndoRedoAction(action, player, world, false, success -> {
                    try {
                        if (success) {
                            stacks.undoStack.push(action);
                            LOGGER.debug("Player {} redo successful, type: {}, affected blocks: {}",
                                player.getName().getString(), action.getType(), action.getPositions().size());
                        } else {
                            LOGGER.warn("Player {} redo failed, operation ignored", player.getName().getString());
                        }
                    } finally {
                        updateCooldown(playerId);
                        unmarkExecuting(playerId);
                    }
                });
                return;
            }
            LOGGER.debug("Player {} attempted redo, but no operations available to redo", player.getName().getString());
        } catch (RuntimeException e) {
            LOGGER.error("Player {} encountered exception during redo operation", player.getName().getString(), e);
            throw e;
        } finally {
            // Ensure state cleanup even on exception
            if (executingPlayers.contains(playerId)) {
                updateCooldown(playerId);
                unmarkExecuting(playerId);
            }
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

        LOGGER.debug("Starting {} operation, player: {}, affected blocks: {}",
            isUndo ? "undo" : "redo", player.getName().getString(), positions.size());

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
            LOGGER.debug("Position validation skipped {} unavailable positions", skipped);
        }

        boolean isLarge = validPositions.size() >= LARGE_OPERATION_THRESHOLD;

        Runnable afterBlocksApplied = () -> {
            BlockOperation.postProcessBlockChanges(serverWorld, validPositions, validNewStates);
            syncUndoChangesToClient(serverWorld, player, validPositions, isLarge, isUndo);
            onFinished.accept(true);
        };

        if (validPositions.size() > BlockOperation.SYNC_BLOCK_LIMIT) {
            LOGGER.debug(“Applying {} blocks in batches across ticks (max {} per tick)”,
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
     * Synchronizes undo/redo block changes to the triggering player.
     * <p>
     * This method is split into two paths: “small operation block-by-block sync / large operation chunk-by-chunk sync”
     * to facilitate threshold branch coverage in unit tests while avoiding fragile tests caused by directly constructing complex chunk data packet objects.
     */
    protected void syncUndoChangesToClient(ServerWorld serverWorld, PlayerEntity player, List<BlockPos> validPositions,
                                          boolean isLarge, boolean isUndo) {
        if (!(player instanceof ServerPlayerEntity serverPlayer)) {
            LOGGER.debug("Completed {} operation, updated valid positions: {} (large operation: {})",
                isUndo ? "undo" : "redo", validPositions.size(), isLarge);
            return;
        }

        LightingProvider lightProvider = serverWorld.getLightingProvider();
        if (!isLarge) {
            syncSmallOperation(serverWorld, serverPlayer, validPositions);
        } else {
            syncLargeOperation(serverWorld, serverPlayer, validPositions, lightProvider);
        }

        LOGGER.debug("Completed {} operation, updated valid positions: {} (large operation: {})",
            isUndo ? "undo" : "redo", validPositions.size(), isLarge);
    }

    protected void syncSmallOperation(ServerWorld serverWorld, ServerPlayerEntity serverPlayer, List<BlockPos> validPositions) {
        for (BlockPos pos : validPositions) {
            if (!serverWorld.isChunkLoaded(new ChunkPos(pos).toLong())) {
                continue;
            }
            ExceptionPolicy.runPerItem("Send block update " + pos, () -> {
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
        sendChunks(serverWorld, serverPlayer, affectedChunks, lightProvider, "Large operation fast sync");
        Objects.requireNonNull(serverWorld.getServer()).execute(() ->
            sendChunks(serverWorld, serverPlayer, affectedChunks, lightProvider, "Delayed lighting sync")
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
            ExceptionPolicy.runPerItem("Send chunk data " + chunkPos, () ->
                sendPacket(serverPlayer, new ChunkDataS2CPacket(chunk, lightProvider, null, null)),
                LOGGER);
            sent++;
        }
        LOGGER.debug("{}: Sent {} chunks to player {}", reason, sent, serverPlayer.getName().getString());
    }

    /**
     * Sends packet to client. Abstracted as an overridable point to facilitate capturing actual sent packet types in GameTest.
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
     * Debug method: Check player's undo stack state
     */
    public void debugPlayerStacks(PlayerEntity player) {
        UUID playerId = player.getUuid();
        PlayerUndoRedoStacks stacks = playerStacks.get(playerId);

        if (stacks == null) {
            LOGGER.debug("Player {} has no undo stack", player.getName().getString());
        } else {
            LOGGER.debug("Player {} undo stack state: undoStack={}, redoStack={}",
                player.getName().getString(), stacks.undoStack.size(), stacks.redoStack.size());

            if (!stacks.undoStack.isEmpty()) {
                UndoAction topAction = stacks.undoStack.peek();
                LOGGER.debug("Top of stack operation: type={}, block count={}", topAction.getType(), topAction.getPositions().size());
            }
        }
    }

    /**
     * Gets the size of player's undo stack
     */
    public int getUndoStackSize(PlayerEntity player) {
        UUID playerId = player.getUuid();
        PlayerUndoRedoStacks stacks = playerStacks.get(playerId);
        return stacks != null ? stacks.undoStack.size() : 0;
    }
}
