package com.pushdozer.network;

import com.pushdozer.config.PushdozerConfig;
import com.pushdozer.services.ConfigService;
import com.pushdozer.services.UndoRedoService;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.block.BlockState;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import com.pushdozer.util.ExceptionPolicy;
import com.pushdozer.util.OperationPermissions;
import net.minecraft.util.math.BlockPos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.List;

/**
 * 网络管理器
 * 统一管理所有网络包的注册和处理
 */
public class NetworkManager {
    private static final Logger LOGGER = LoggerFactory.getLogger("pushdozer");
    
    /**
     * 注册服务器端网络包类型和处理器
     */
    public static void registerNetworking() {
        // 注册客户端到服务器的网络包
        PayloadTypeRegistry.playC2S().register(UndoRedoPayload.ID, UndoRedoPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(ConfigSyncPayload.ID, ConfigSyncPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(PermissionCheckPayload.ID, PermissionCheckPayload.CODEC);
        
        // 注册服务器端处理器
        registerServerHandlers();
        
        // 注册玩家连接事件
        registerConnectionEvents();
        
        LOGGER.info("Pushdozer服务器端网络包注册完成");
    }
    
    /**
     * 注册服务器端网络包处理器
     */
    private static void registerServerHandlers() {
        // 撤销/重做处理器
        ServerPlayNetworking.registerGlobalReceiver(
            UndoRedoPayload.ID,
            (payload, context) -> context.server().execute(() -> {
                try {
                    if (payload.isUndo()) {
                        LOGGER.info("服务器收到撤销请求，玩家: {}", context.player().getName().getString());
                        UndoRedoService.getInstance().undoLastAction(context.player(), context.player().getEntityWorld());
                    } else {
                        LOGGER.info("服务器收到重做请求，玩家: {}", context.player().getName().getString());
                        UndoRedoService.getInstance().redoLastAction(context.player(), context.player().getEntityWorld());
                    }
                } catch (RuntimeException e) {
                    ExceptionPolicy.logBenignOrRethrow("处理撤销/重做操作", e, LOGGER);
                }
            })
        );
        
        // 配置同步处理器（客户端发送到服务器）
        // 注意：每个玩家的配置是独立的，不需要同步到其他玩家
        ServerPlayNetworking.registerGlobalReceiver(
            ConfigSyncPayload.ID,
            (payload, context) -> context.server().execute(() -> {
                try {
                    if (hasPermissionToChangeConfig(context.player())) {
                        ConfigService.getInstance().applySync(context.player(), payload);
                        LOGGER.debug("玩家 {} 更新了个人 Pushdozer 配置", context.player().getName().getString());
                    }
                } catch (RuntimeException e) {
                    ExceptionPolicy.logBenignOrRethrow("处理配置同步", e, LOGGER);
                }
            })
        );
        
        // 权限检查处理器
        ServerPlayNetworking.registerGlobalReceiver(
            PermissionCheckPayload.ID,
            (payload, context) -> context.server().execute(() -> {
                try {
                    checkTerrainOperationPermission(
                        context.player(),
                        payload.operationType(),
                        payload.centerPos(),
                        payload.radius()
                    );
                } catch (RuntimeException e) {
                    ExceptionPolicy.logBenignOrRethrow("处理权限检查", e, LOGGER);
                }
            })
        );
    }
    
    /**
     * 注册连接事件
     */
    private static void registerConnectionEvents() {
        // 玩家加入时的处理
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> ConfigService.getInstance().removePlayer(handler.player.getUuid()));
    }
    
    /**
     * 向所有玩家广播地形操作（使用批处理优化）
     */
    public static void broadcastTerrainOperation(ServerWorld world, String operationType, 
                                               List<BlockPos> positions, List<BlockState> states) {
        try {
            // 如果操作很小，直接发送
            if (positions.size() <= 50) {
                TerrainOperationPayload payload = new TerrainOperationPayload(operationType, positions, states);
                
                for (ServerPlayerEntity player : world.getPlayers()) {
                    ServerPlayNetworking.send(player, payload);
                }
                
                LOGGER.debug("直接广播地形操作 {} 到 {} 个玩家，方块数: {}", 
                    operationType, world.getPlayers().size(), positions.size());
            } else {
                // 大操作使用批处理管理器
                BatchedNetworkManager.getInstance().addTerrainOperation(world, operationType, positions, states);
                LOGGER.debug("添加地形操作到批处理队列: {} 个方块", positions.size());
            }
        } catch (RuntimeException e) {
            ExceptionPolicy.logBenignOrRethrow("广播地形操作", e, LOGGER);
        }
    }
    
    /**
     * 向所有玩家同步配置
     */
    public static void syncConfigToAllPlayers(net.minecraft.server.MinecraftServer server, ConfigSyncPayload configPayload) {
        try {
            for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
                ServerPlayNetworking.send(player, configPayload);
            }
            
            LOGGER.info("向 {} 个玩家同步配置", server.getPlayerManager().getPlayerList().size());
        } catch (RuntimeException e) {
            ExceptionPolicy.logBenignOrRethrow("同步配置到所有玩家", e, LOGGER);
        }
    }
    
    /**
     * 检查玩家是否有权限修改配置
     * 每个玩家都可以修改自己的配置
     */
    private static boolean hasPermissionToChangeConfig(ServerPlayerEntity player) {
        // 所有玩家都可以修改自己的配置
        return true;
    }
    
    /**
     * 检查玩家是否有权限执行地形操作
     */
    private static boolean checkTerrainOperationPermission(ServerPlayerEntity player, String operationType,
                                                         BlockPos centerPos, int radius) {
        return OperationPermissions.checkBrushRadius(player, radius);
    }
}