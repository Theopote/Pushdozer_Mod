package com.pushdozer.network;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.block.BlockState;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import com.pushdozer.util.ExceptionPolicy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 批处理网络管理器
 * 优化网络传输，避免大量数据包造成延迟
 */
public class BatchedNetworkManager {
    private static final Logger LOGGER = LoggerFactory.getLogger("pushdozer");
    private static final int MAX_BATCH_SIZE = 500;
    private static final int BATCH_DELAY_MS = 100;

    private static BatchedNetworkManager instance;
    private final ScheduledExecutorService scheduler;
    private final Map<ServerWorld, BatchedOperation> pendingOperations;
    private final AtomicLong batchSequence = new AtomicLong();

    private BatchedNetworkManager() {
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "Pushdozer-Network-Batch");
            t.setDaemon(true);
            return t;
        });
        this.pendingOperations = new ConcurrentHashMap<>();
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> shutdown());
    }

    public static BatchedNetworkManager getInstance() {
        if (instance == null) {
            instance = new BatchedNetworkManager();
        }
        return instance;
    }

    public void addTerrainOperation(ServerWorld world, String operationType,
                                  List<BlockPos> positions, List<BlockState> states) {
        if (positions.isEmpty() || states.isEmpty()) {
            return;
        }

        BatchedOperation operation = pendingOperations.computeIfAbsent(world,
            w -> new BatchedOperation(w, batchSequence.incrementAndGet()));

        synchronized (operation) {
            if (!operation.operationType.isEmpty() && !operation.operationType.equals(operationType)) {
                flushOperation(world, operation);
                operation = pendingOperations.computeIfAbsent(world,
                    w -> new BatchedOperation(w, batchSequence.incrementAndGet()));
            }

            operation.addOperation(operationType, positions, states);

            if (operation.getTotalSize() >= MAX_BATCH_SIZE) {
                flushOperation(world, operation);
            } else if (!operation.isScheduled) {
                operation.isScheduled = true;
                BatchedOperation scheduledOperation = operation;
                scheduler.schedule(() -> flushIfCurrent(world, scheduledOperation), BATCH_DELAY_MS, TimeUnit.MILLISECONDS);
            }
        }
    }

    private void flushIfCurrent(ServerWorld world, BatchedOperation expectedOperation) {
        BatchedOperation toSend;
        synchronized (expectedOperation) {
            expectedOperation.isScheduled = false;
            if (pendingOperations.get(world) != expectedOperation || expectedOperation.getTotalSize() == 0) {
                return;
            }
            toSend = pendingOperations.remove(world);
        }
        dispatchSendBatch(toSend, false);
    }

    private void flushOperation(ServerWorld world, BatchedOperation operation) {
        BatchedOperation toSend;
        synchronized (operation) {
            if (operation.getTotalSize() == 0) {
                return;
            }
            if (pendingOperations.get(world) == operation) {
                toSend = pendingOperations.remove(world);
            } else {
                toSend = operation;
            }
            operation.isScheduled = false;
        }
        dispatchSendBatch(toSend, false);
    }

    private void dispatchSendBatch(BatchedOperation operation, boolean waitForCompletion) {
        MinecraftServer server = operation.world.getServer();
        Runnable sendTask = () -> sendBatch(operation);

        if (server != null && server.isOnThread()) {
            sendTask.run();
            return;
        }

        if (waitForCompletion) {
            CountDownLatch latch = new CountDownLatch(1);
            if (server != null) {
                server.execute(() -> {
                    try {
                        sendTask.run();
                    } finally {
                        latch.countDown();
                    }
                });
            }
            try {
                if (!latch.await(5, TimeUnit.SECONDS)) {
                    LOGGER.warn("等待批处理发包完成超时");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        } else if (server != null) {
            server.execute(sendTask);
        }
    }

    private void sendBatch(BatchedOperation operation) {
        try {
            List<ServerPlayerEntity> players = operation.world.getPlayers();
            if (players.isEmpty()) {
                return;
            }

            List<BlockPos> allPositions = operation.getAllPositions();
            List<BlockState> allStates = operation.getAllStates();
            String operationType = operation.getOperationType();

            int chunks = (int) Math.ceil((double) allPositions.size() / MAX_BATCH_SIZE);

            for (int i = 0; i < chunks; i++) {
                int start = i * MAX_BATCH_SIZE;
                int end = Math.min(start + MAX_BATCH_SIZE, allPositions.size());

                List<BlockPos> chunkPositions = allPositions.subList(start, end);
                List<BlockState> chunkStates = allStates.subList(start, end);

                TerrainOperationPayload payload = new TerrainOperationPayload(
                    operationType, chunkPositions, chunkStates);

                for (ServerPlayerEntity player : players) {
                    ServerPlayNetworking.send(player, payload);
                }

                LOGGER.debug("发送地形操作批次 {}/{} 到 {} 个玩家，方块数: {}",
                    i + 1, chunks, players.size(), chunkPositions.size());
            }

        } catch (RuntimeException e) {
            ExceptionPolicy.logBenignOrRethrow("发送批处理操作", e, LOGGER);
        }
    }

    public void shutdown() {
        pendingOperations.forEach((world, operation) -> {
            synchronized (operation) {
                if (operation.getTotalSize() > 0) {
                    dispatchSendBatch(operation, true);
                }
            }
        });
        pendingOperations.clear();
        scheduler.shutdownNow();
    }

    private static class BatchedOperation {
        final ServerWorld world;
        final long id;
        final List<BlockPos> positions = new ArrayList<>();
        final List<BlockState> states = new ArrayList<>();
        String operationType = "";
        boolean isScheduled = false;

        BatchedOperation(ServerWorld world, long id) {
            this.world = world;
            this.id = id;
        }

        void addOperation(String opType, List<BlockPos> newPositions, List<BlockState> newStates) {
            if (this.operationType.isEmpty()) {
                this.operationType = opType;
            }
            this.positions.addAll(newPositions);
            this.states.addAll(newStates);
        }

        int getTotalSize() {
            return positions.size();
        }

        List<BlockPos> getAllPositions() {
            return new ArrayList<>(positions);
        }

        List<BlockState> getAllStates() {
            return new ArrayList<>(states);
        }

        String getOperationType() {
            return operationType;
        }
    }
}
