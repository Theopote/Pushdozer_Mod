package com.pushdozer.network;

import com.pushdozer.config.PushdozerConfig;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.client.world.ClientWorld;
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
                        // 应用地形操作到客户端世界
                        for (int i = 0; i < payload.positions().size() && i < payload.states().size(); i++) {
                            world.setBlockState(payload.positions().get(i), payload.states().get(i));
                        }
                        
                        LOGGER.debug("客户端应用地形操作: {} 个方块", payload.positions().size());
                    }
                } catch (Exception e) {
                    LOGGER.error("客户端处理地形操作失败", e);
                }
            })
        );
        
        // 配置同步处理器已移除，因为每个玩家的配置是独立的
    }
    
    /**
     * 发送撤销/重做请求到服务器
     */
    public static void sendUndoRedoRequest(boolean isUndo) {
        try {
            UndoRedoPayload payload = new UndoRedoPayload(isUndo);
            ClientPlayNetworking.send(payload);
            
            LOGGER.debug("发送{}请求到服务器", isUndo ? "撤销" : "重做");
        } catch (Exception e) {
            LOGGER.error("发送撤销/重做请求失败", e);
        }
    }
    
    /**
     * 发送配置同步到服务器
     */
    public static void sendConfigSync(PushdozerConfig config) {
        try {
            if (!ClientPlayNetworking.canSend(ConfigSyncPayload.ID)) {
                return;
            }
            ClientPlayNetworking.send(ConfigSyncPayload.fromConfig(config));
            LOGGER.debug("已将 Pushdozer 配置同步到服务器");
        } catch (Exception e) {
            LOGGER.error("发送配置同步失败", e);
        }
    }
    
    /**
     * 发送权限检查请求到服务器
     */
    public static void sendPermissionCheck(String operationType, 
                                         net.minecraft.util.math.BlockPos centerPos, 
                                         int radius) {
        try {
            PermissionCheckPayload payload = new PermissionCheckPayload(operationType, centerPos, radius);
            ClientPlayNetworking.send(payload);
            
            LOGGER.debug("发送权限检查请求: {}", operationType);
        } catch (Exception e) {
            LOGGER.error("发送权限检查请求失败", e);
        }
    }
}