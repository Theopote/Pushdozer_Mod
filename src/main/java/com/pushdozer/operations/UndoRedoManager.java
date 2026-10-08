package com.pushdozer.operations;

import com.pushdozer.util.WorldBounds;
import com.pushdozer.util.ExceptionPolicy;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;
import net.minecraft.block.BlockState;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.ChunkDataS2CPacket;
import net.minecraft.network.packet.s2c.play.BlockUpdateS2CPacket;
import net.minecraft.world.chunk.WorldChunk;
import net.minecraft.world.chunk.light.LightingProvider;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Deque;
import java.util.ArrayDeque;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class UndoRedoManager {
    private static final Logger LOGGER = LoggerFactory.getLogger("pushdozer");
    private static final int MAX_UNDO_REDO_STEPS = 30;
    private static final int LARGE_OPERATION_THRESHOLD = 4096;
    private final Map<StackKey, PlayerUndoRedoStacks> playerStacks = new ConcurrentHashMap<>();

    private final Map<UUID, Long> lastActionTime = new ConcurrentHashMap<>();
    private static final long ACTION_COOLDOWN_MS = 300;
    private final Set<UUID> executingPlayers = ConcurrentHashMap.newKeySet();

    private record StackKey(UUID playerId, RegistryKey<World> worldKey) {
    }

    private static class PlayerUndoRedoStacks {
        final Deque<UndoAction> undoStack = new ArrayDeque<>();
        final Deque<UndoAction> redoStack = new ArrayDeque<>();
    }

    public void pushUndoAction(PlayerEntity player, UndoAction action) {
        if (action == null || !action.isValid()) {
            LOGGER.warn("Rejected invalid undo action for player {}", player.getName().getString());
            return;
        }

        StackKey key = stackKey(player, action.getWorldKey());
        PlayerUndoRedoStacks stacks = playerStacks.computeIfAbsent(key, k -> new PlayerUndoRedoStacks());
        if (stacks.undoStack.size() >= MAX_UNDO_REDO_STEPS) {
            stacks.undoStack.removeFirst();
        }
        stacks.undoStack.push(action);
        stacks.redoStack.clear();
        LOGGER.debug("Player {} added undo action in {}, type: {}, blocks: {}, stack size: {}",
            player.getName().getString(), action.getWorldKey().getValue(), action.getType(),
            action.getPositions().size(), stacks.undoStack.size());
    }

    public void undoLastAction(PlayerEntity player, World world) {
        UUID playerId = player.getUuid();
        LOGGER.debug("Player {} attempting undo in {}", player.getName().getString(), world.getRegistryKey().getValue());

        if (isCoolingDownOrExecuting(playerId)) {
            return;
        }

        StackKey key = stackKey(player, world);
        PlayerUndoRedoStacks stacks = playerStacks.get(key);
        if (stacks == null || stacks.undoStack.isEmpty()) {
            LOGGER.debug("Player {} has no undo entries for {}", player.getName().getString(), world.getRegistryKey().getValue());
            return;
        }

        markExecuting(playerId);
        UndoAction action = stacks.undoStack.pop();
        try {
            executeUndoRedoAction(action, player, world, true, success ->
                finishUndoRedo(playerId, stacks, action, true, success));
        } catch (RuntimeException e) {
            stacks.undoStack.push(action);
            releaseExecution(playerId);
            LOGGER.error("Player {} undo failed with exception", player.getName().getString(), e);
            throw e;
        }
    }

    public void redoLastAction(PlayerEntity player, World world) {
        UUID playerId = player.getUuid();
        if (isCoolingDownOrExecuting(playerId)) {
            return;
        }

        StackKey key = stackKey(player, world);
        PlayerUndoRedoStacks stacks = playerStacks.get(key);
        if (stacks == null || stacks.redoStack.isEmpty()) {
            LOGGER.debug("Player {} has no redo entries for {}", player.getName().getString(), world.getRegistryKey().getValue());
            return;
        }

        markExecuting(playerId);
        UndoAction action = stacks.redoStack.pop();
        try {
            executeUndoRedoAction(action, player, world, false, success ->
                finishUndoRedo(playerId, stacks, action, false, success));
        } catch (RuntimeException e) {
            stacks.redoStack.push(action);
            releaseExecution(playerId);
            LOGGER.error("Player {} redo failed with exception", player.getName().getString(), e);
            throw e;
        }
    }

    private void finishUndoRedo(UUID playerId, PlayerUndoRedoStacks stacks, UndoAction action,
                                boolean wasUndo, boolean success) {
        try {
            if (success) {
                if (wasUndo) {
                    stacks.redoStack.push(action);
                } else {
                    stacks.undoStack.push(action);
                }
            } else {
                if (wasUndo) {
                    stacks.undoStack.push(action);
                    LOGGER.warn("Undo failed; restored action to undo stack");
                } else {
                    stacks.redoStack.push(action);
                    LOGGER.warn("Redo failed; restored action to redo stack");
                }
            }
        } finally {
            releaseExecution(playerId);
        }
    }

    private void releaseExecution(UUID playerId) {
        updateCooldown(playerId);
        unmarkExecuting(playerId);
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

        if (!action.matchesWorld(serverWorld)) {
            LOGGER.warn("Undo/redo action targets {} but player is in {}",
                action.getWorldKey().getValue(), serverWorld.getRegistryKey().getValue());
            onFinished.accept(false);
            return;
        }

        List<BlockPos> positions = action.getExecutionPositions();
        List<BlockState> originalStates = action.getExecutionOriginalStates();
        List<BlockState> newStates = action.getExecutionNewStates();

        LOGGER.debug("Starting {} operation, player: {}, affected blocks: {}",
            isUndo ? "undo" : "redo", player.getName().getString(), positions.size());

        List<BlockPos> validPositions = new ArrayList<>(positions.size());
        List<BlockState> validNewStates = new ArrayList<>(positions.size());
        int skipped = 0;
        for (int i = 0; i < positions.size(); i++) {
            BlockPos pos = positions.get(i);
            if (WorldBounds.isLoadedBuildablePos(serverWorld, pos)) {
                validPositions.add(pos);
                validNewStates.add(isUndo ? originalStates.get(i) : newStates.get(i));
            } else {
                skipped++;
            }
        }
        if (skipped > 0) {
            LOGGER.debug("Position validation skipped {} unavailable positions", skipped);
        }

        if (validPositions.isEmpty()) {
            onFinished.accept(false);
            return;
        }

        boolean isLarge = validPositions.size() >= LARGE_OPERATION_THRESHOLD;

        Runnable afterBlocksApplied = () -> {
            BlockOperation.postProcessBlockChanges(serverWorld, validPositions, validNewStates);
            syncUndoChangesToClient(serverWorld, player, validPositions, isLarge, isUndo);
            onFinished.accept(true);
        };

        if (validPositions.size() > BlockOperation.SYNC_BLOCK_LIMIT) {
            LOGGER.debug("Applying {} blocks in batches across ticks (max {} per tick)",
                validPositions.size(), BlockOperation.BLOCKS_PER_TICK);
            BlockOperation.batchSetBlockStates(validPositions, validNewStates, serverWorld,
                BlockOperation.BULK_WRITE_FLAGS, afterBlocksApplied);
        } else {
            BlockOperation.batchSetBlockStates(validPositions, validNewStates, serverWorld,
                BlockOperation.BULK_WRITE_FLAGS);
            afterBlocksApplied.run();
        }
    }

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

    protected void sendPacket(ServerPlayerEntity player, Packet<?> packet) {
        player.networkHandler.sendPacket(packet);
    }

    private StackKey stackKey(PlayerEntity player, World world) {
        return new StackKey(player.getUuid(), world.getRegistryKey());
    }

    private StackKey stackKey(PlayerEntity player, RegistryKey<World> worldKey) {
        return new StackKey(player.getUuid(), worldKey);
    }

    private boolean isCoolingDownOrExecuting(UUID playerId) {
        long now = System.currentTimeMillis();
        Long last = lastActionTime.get(playerId);
        if (executingPlayers.contains(playerId)) {
            return true;
        }
        return last != null && now - last < ACTION_COOLDOWN_MS;
    }

    private void updateCooldown(UUID playerId) {
        lastActionTime.put(playerId, System.currentTimeMillis());
    }

    private void markExecuting(UUID playerId) {
        executingPlayers.add(playerId);
    }

    private void unmarkExecuting(UUID playerId) {
        executingPlayers.remove(playerId);
    }

    public void debugPlayerStacks(PlayerEntity player) {
        World world = player.getEntityWorld();
        StackKey key = stackKey(player, world);
        PlayerUndoRedoStacks stacks = playerStacks.get(key);

        if (stacks == null) {
            LOGGER.debug("Player {} has no undo stack in {}", player.getName().getString(), world.getRegistryKey().getValue());
        } else {
            LOGGER.debug("Player {} undo stack in {}: undo={}, redo={}",
                player.getName().getString(), world.getRegistryKey().getValue(),
                stacks.undoStack.size(), stacks.redoStack.size());

            if (!stacks.undoStack.isEmpty()) {
                UndoAction topAction = stacks.undoStack.peek();
                LOGGER.debug("Top undo: type={}, blocks={}", topAction.getType(), topAction.getPositions().size());
            }
        }
    }

    public int getUndoStackSize(PlayerEntity player) {
        StackKey key = stackKey(player, player.getEntityWorld());
        PlayerUndoRedoStacks stacks = playerStacks.get(key);
        return stacks != null ? stacks.undoStack.size() : 0;
    }
}
