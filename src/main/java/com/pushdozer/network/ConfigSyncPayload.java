package com.pushdozer.network;

import com.pushdozer.config.PushdozerConfig;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import net.minecraft.network.codec.PacketCodec;

/**
 * 配置同步网络包
 * 用于在服务器和客户端之间同步配置数据
 */
public record ConfigSyncPayload(
    PushdozerConfig.WorkMode workMode,
    PushdozerConfig.GeometryType geometryType,
    PushdozerConfig.PlaceMode placeMode,
    PushdozerConfig.HeightMode heightMode,
    int radius,
    int lockedHeight,
    String selectedNaturalBlockId
) implements CustomPayload {
    
    public static final CustomPayload.Id<ConfigSyncPayload> ID = 
        new CustomPayload.Id<>(Identifier.of("pushdozer", "config_sync"));

    public static final PacketCodec<PacketByteBuf, ConfigSyncPayload> CODEC = new PacketCodec<>() {
        @Override
        public void encode(PacketByteBuf buf, ConfigSyncPayload payload) {
            buf.writeEnumConstant(payload.workMode());
            buf.writeEnumConstant(payload.geometryType());
            buf.writeEnumConstant(payload.placeMode());
            buf.writeEnumConstant(payload.heightMode());
            buf.writeVarInt(payload.radius());
            buf.writeVarInt(payload.lockedHeight());
            buf.writeString(payload.selectedNaturalBlockId());
        }

        @Override
        public ConfigSyncPayload decode(PacketByteBuf buf) {
            return new ConfigSyncPayload(
                buf.readEnumConstant(PushdozerConfig.WorkMode.class),
                buf.readEnumConstant(PushdozerConfig.GeometryType.class),
                buf.readEnumConstant(PushdozerConfig.PlaceMode.class),
                buf.readEnumConstant(PushdozerConfig.HeightMode.class),
                buf.readVarInt(),
                buf.readVarInt(),
                buf.readString()
            );
        }
    };

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() {
        return ID;
    }
    
    /**
     * 从配置对象创建网络包
     */
    public static ConfigSyncPayload fromConfig(PushdozerConfig config) {
        return new ConfigSyncPayload(
            config.getWorkMode(),
            config.getGeometryType(),
            config.getPlaceMode(),
            config.getHeightMode(),
            config.getRadius(),
            config.getLockedHeight(),
            config.getSelectedNaturalBlockId()
        );
    }
    
    /**
     * 将网络包数据应用到配置对象
     */
    public void applyToConfig(PushdozerConfig config) {
        config.setWorkMode(workMode());
        config.setGeometryType(geometryType());
        config.setPlaceMode(placeMode());
        config.setHeightMode(heightMode());
        config.setRadius(radius());
        config.setLockedHeight(lockedHeight());
        config.setSelectedNaturalBlockId(selectedNaturalBlockId());
    }
}