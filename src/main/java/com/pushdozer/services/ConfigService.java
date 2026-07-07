package com.pushdozer.services;

import com.pushdozer.config.PushdozerConfig;
import com.pushdozer.network.ConfigSyncPayload;
import net.minecraft.entity.player.PlayerEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 按玩家 UUID 管理 Pushdozer 配置（服务端专用）。
 * 客户端 UI/预览仍使用 {@link PushdozerConfig#getInstance()} 本地单例。
 */
public class ConfigService {
    private static final Logger LOGGER = LoggerFactory.getLogger("pushdozer");
    private static ConfigService instance;

    private final Map<UUID, PushdozerConfig> playerConfigs = new ConcurrentHashMap<>();

    private ConfigService() {
    }

    public static ConfigService getInstance() {
        if (instance == null) {
            instance = new ConfigService();
        }
        return instance;
    }

    /**
     * 获取玩家当前配置；若尚未同步则返回带默认值的新实例。
     */
    public PushdozerConfig getConfig(PlayerEntity player) {
        return playerConfigs.computeIfAbsent(player.getUuid(), uuid -> {
            PushdozerConfig config = new PushdozerConfig();
            PushdozerConfig.ensureDefaults(config);
            LOGGER.debug("为玩家 {} 创建默认 Pushdozer 配置", player.getName().getString());
            return config;
        });
    }

    /**
     * 将客户端同步过来的完整配置写入该玩家的服务端副本。
     */
    public void applySync(PlayerEntity player, ConfigSyncPayload payload) {
        PushdozerConfig config = payload.toConfig();
        playerConfigs.put(player.getUuid(), config);
        LOGGER.debug("已更新玩家 {} 的 Pushdozer 配置", player.getName().getString());
    }

    public void removePlayer(UUID playerId) {
        playerConfigs.remove(playerId);
    }
}
