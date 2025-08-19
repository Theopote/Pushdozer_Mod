package com.pushdozer.network;

import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import net.minecraft.network.codec.PacketCodec;

/**
 * 撤销/重做网络包
 * 用于客户端向服务器发送撤销或重做请求
 */
public record UndoRedoPayload(boolean isUndo) implements CustomPayload {
    public static final CustomPayload.Id<UndoRedoPayload> ID = 
        new CustomPayload.Id<>(Identifier.of("pushdozer", "undo_redo"));

    public static final PacketCodec<PacketByteBuf, UndoRedoPayload> CODEC = new PacketCodec<>() {
        @Override
        public void encode(PacketByteBuf buf, UndoRedoPayload payload) {
            buf.writeBoolean(payload.isUndo());
        }

        @Override
        public UndoRedoPayload decode(PacketByteBuf buf) {
            return new UndoRedoPayload(buf.readBoolean());
        }
    };

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() {
        return ID;
    }
} 