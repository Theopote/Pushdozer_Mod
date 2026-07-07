package com.pushdozer.network;

import com.pushdozer.config.PushdozerConfig;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import net.minecraft.network.codec.PacketCodec;

/**
 * 配置同步网络包：客户端将完整配置 JSON 同步到服务端，按玩家 UUID 独立存储。
 */
public record ConfigSyncPayload(String configJson) implements CustomPayload {

    public static final CustomPayload.Id<ConfigSyncPayload> ID =
        new CustomPayload.Id<>(Identifier.of("pushdozer", "config_sync"));

    public static final PacketCodec<PacketByteBuf, ConfigSyncPayload> CODEC = new PacketCodec<>() {
        @Override
        public void encode(PacketByteBuf buf, ConfigSyncPayload payload) {
            buf.writeString(payload.configJson());
        }

        @Override
        public ConfigSyncPayload decode(PacketByteBuf buf) {
            return new ConfigSyncPayload(buf.readString());
        }
    };

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() {
        return ID;
    }

    public static ConfigSyncPayload fromConfig(PushdozerConfig config) {
        return new ConfigSyncPayload(config.toJson());
    }

    public PushdozerConfig toConfig() {
        return PushdozerConfig.fromJson(configJson());
    }
}
