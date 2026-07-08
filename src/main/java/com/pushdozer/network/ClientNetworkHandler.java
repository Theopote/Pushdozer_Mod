package com.pushdozer.network;

import com.pushdozer.config.PushdozerConfig;
import com.pushdozer.util.ExceptionPolicy;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.block.BlockState;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.BlockPos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 客户端网络处理器
 * 处理从服务器发送到客户端的网络包
 */
public class ClientNetworkHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger("pushdozer");
    
    /**
     * 注册客户端网络处理器
     */
    public static void registerClientNetworking() {
        // 注册从服务器到客户端的网络包类型
        PayloadTypeRegistry.playS2C().register(TerrainOperationPayload.ID, TerrainOperationPayload.CODEC);
        
        // 注册客户端处理器
        registerClientHandlers();
        
        LOGGER.info("Pushdozer客户端网络处理器注册完成");
    }
    
    /**
     * 注册客户端网络包处理器
     */
    private static void registerClientHandlers() {
        // 地形操作同步处理器
        ClientPlayNetworking.registerGlobalReceiver(
            TerrainOperationPayload.ID,
            (payload, context) -> context.client().execute(() -> {
                try {
                    ClientWorld world = context.client().world;
                    if (world != null) {
                        for (int i = 0; i < payload.positions().size() && i < payload.states().size(); i++) {
                            BlockPos pos = payload.positions().get(i);
                            BlockState state = payload.states().get(i);
                            ExceptionPolicy.runPerItem("客户端应用方块 " + pos,
                                () -> world.setBlockState(pos, state), LOGGER);
                        }
                        LOGGER.debug("客户端应用地形操作: {} 个方块", payload.positions().size());
                    }
                } catch (RuntimeException e) {
                    ExceptionPolicy.logBenignOrRethrow("客户端处理地形操作", e, LOGGER);
                }
            })
        );
        
        // 配置同步处理器已移除，因为每个玩家的配置是独立的
    }
    
    /**
     * 发送撤销/重做请求到服务器
     */
    public static void sendUndoRedoRequest(boolean isUndo) {
        UndoRedoPayload payload = new UndoRedoPayload(isUndo);
        ClientPlayNetworking.send(payload);
        LOGGER.debug("发送{}请求到服务器", isUndo ? "撤销" : "重做");
    }
    
    /**
     * 发送配置同步到服务器
     */
    public static void sendConfigSync(PushdozerConfig config) {
        if (!ClientPlayNetworking.canSend(ConfigSyncPayload.ID)) {
            return;
        }
        try {
            ConfigSyncPayload payload = ConfigSyncPayload.fromConfig(config);
            ClientPlayNetworking.send(payload);
            LOGGER.debug("已将 Pushdozer 配置同步到服务器 ({} bytes)", payload.configJsonUtf8().length);
        } catch (IllegalArgumentException e) {
            LOGGER.error("Pushdozer 配置过大，无法同步到服务器: {}", e.getMessage());
        }
    }
    
    /**
     * 发送权限检查请求到服务器
     */
    public static void sendPermissionCheck(String operationType, 
                                         net.minecraft.util.math.BlockPos centerPos, 
                                         int radius) {
        PermissionCheckPayload payload = new PermissionCheckPayload(operationType, centerPos, radius);
        ClientPlayNetworking.send(payload);
        LOGGER.debug("发送权限检查请求: {}", operationType);
    }
}