package com.pushdozer.network;

import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.network.codec.PacketCodec;

/**
 * 权限检查网络包
 * 用于在执行地形操作前检查玩家权限
 */
public record PermissionCheckPayload(
    String operationType,
    BlockPos centerPos,
    int radius
) implements CustomPayload {
    
    public static final CustomPayload.Id<PermissionCheckPayload> ID = 
        new CustomPayload.Id<>(Identifier.of("pushdozer", "permission_check"));

    public static final PacketCodec<PacketByteBuf, PermissionCheckPayload> CODEC = new PacketCodec<>() {
        @Override
        public void encode(PacketByteBuf buf, PermissionCheckPayload payload) {
            buf.writeString(payload.operationType());
            buf.writeBlockPos(payload.centerPos());
            buf.writeVarInt(payload.radius());
        }

        @Override
        public PermissionCheckPayload decode(PacketByteBuf buf) {
            return new PermissionCheckPayload(
                buf.readString(),
                buf.readBlockPos(),
                buf.readVarInt()
            );
        }
    };

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() {
        return ID;
    }
}