package com.pushdozer.network;

import net.minecraft.block.BlockState;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 批处理网络管理器
 * 优化网络传输，避免大量数据包造成延迟
 */
public class BatchedNetworkManager {
    private static final Logger LOGGER = LoggerFactory.getLogger("pushdozer");
    private static final int MAX_BATCH_SIZE = 500; // 每批最大方块数量
    private static final int BATCH_DELAY_MS = 100; // 批处理延迟毫秒数
    
    private static BatchedNetworkManager instance;
    private final ScheduledExecutorService scheduler;
    private final Map<ServerWorld, BatchedOperation> pendingOperations;
    
    private BatchedNetworkManager() {
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "Pushdozer-Network-Batch");
            t.setDaemon(true);
            return t;
        });
        this.pendingOperations = new ConcurrentHashMap<>();
    }
    
    public static BatchedNetworkManager getInstance() {
        if (instance == null) {
            instance = new BatchedNetworkManager();
        }
        return instance;
    }
    
    /**
     * 添加地形操作到批处理队列
     */
    public void addTerrainOperation(ServerWorld world, String operationType, 
                                  List<BlockPos> positions, List<BlockState> states) {
        if (positions.isEmpty() || states.isEmpty()) {
            return;
        }
        
        BatchedOperation operation = pendingOperations.computeIfAbsent(world, 
            k -> new BatchedOperation(world));
        
        synchronized (operation) {
            operation.addOperation(operationType, positions, states);
            
            // 如果达到批大小限制，立即发送
            if (operation.getTotalSize() >= MAX_BATCH_SIZE) {
                pendingOperations.remove(world);
                dispatchSendBatch(operation, false);
            } else if (!operation.isScheduled) {
                // 安排延迟发送（调度线程仅触发，实际发包须回到服务器主线程）
                operation.isScheduled = true;
                scheduler.schedule(() -> {
                    BatchedOperation toSend;
                    synchronized (operation) {
                        toSend = pendingOperations.remove(world);
                    }
                    if (toSend != null) {
                        dispatchSendBatch(toSend, false);
                    }
                }, BATCH_DELAY_MS, TimeUnit.MILLISECONDS);
            }
        }
    }

    /**
     * 将批处理发送调度到服务器主线程；必要时阻塞等待完成。
     */
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
        } else {
            if (server != null) {
                server.execute(sendTask);
            }
        }
    }

    /**
     * 发送批处理操作（必须在服务器主线程调用）
     */
    private void sendBatch(BatchedOperation operation) {
        try {
            List<ServerPlayerEntity> players = operation.world.getPlayers();
            if (players.isEmpty()) {
                return;
            }
            
            // 分块发送以避免包过大
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
            
        } catch (Exception e) {
            LOGGER.error("发送批处理操作失败", e);
        }
    }

    /**
     * 批处理操作数据类
     */
    private static class BatchedOperation {
        final ServerWorld world;
        final List<BlockPos> positions = new ArrayList<>();
        final List<BlockState> states = new ArrayList<>();
        String operationType = "";
        boolean isScheduled = false;
        
        BatchedOperation(ServerWorld world) {
            this.world = world;
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