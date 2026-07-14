package com.pushdozer.network;

import com.pushdozer.config.PushdozerConfig;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.util.Identifier;

import java.nio.charset.StandardCharsets;

/**
 * Config sync network payload: client syncs complete config JSON to server, stored independently per player UUID.
 * Uses UTF-8 byte array instead of writeString, avoiding the 32767 character network string limit.
 */
public record ConfigSyncPayload(byte[] configJsonUtf8) implements CustomPayload {

    /** Safe threshold aligned with Minecraft PacketByteBuf byte array limit */
    public static final int MAX_JSON_BYTES = 1_048_576;

    public static final CustomPayload.Id<ConfigSyncPayload> ID =
        new CustomPayload.Id<>(Identifier.of("pushdozer", "config_sync"));

    public static final PacketCodec<PacketByteBuf, ConfigSyncPayload> CODEC = new PacketCodec<>() {
        @Override
        public void encode(PacketByteBuf buf, ConfigSyncPayload payload) {
            buf.writeByteArray(payload.configJsonUtf8());
        }

        @Override
        public ConfigSyncPayload decode(PacketByteBuf buf) {
            return new ConfigSyncPayload(buf.readByteArray());
        }
    };

    public ConfigSyncPayload(String configJson) {
        this(encodeJson(configJson));
    }

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() {
        return ID;
    }

    public String configJson() {
        return new String(configJsonUtf8, StandardCharsets.UTF_8);
    }

    public static ConfigSyncPayload fromConfig(PushdozerConfig config) {
        return new ConfigSyncPayload(encodeJson(config.toJson()));
    }

    public PushdozerConfig toConfig() {
        return PushdozerConfig.fromJson(configJson());
    }

    private static byte[] encodeJson(String json) {
        if (json == null) {
            json = "";
        }
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        if (bytes.length > MAX_JSON_BYTES) {
            throw new IllegalArgumentException(
                "Pushdozer config JSON exceeds network limit: " + bytes.length + " bytes (max " + MAX_JSON_BYTES + ")");
        }
        return bytes;
    }
}
