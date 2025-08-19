package com.pushdozer.network;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.network.codec.PacketCodec;
import java.util.ArrayList;
import java.util.List;

/**
 * 地形操作网络包
 * 用于同步地形修改操作到所有客户端
 */
public record TerrainOperationPayload(
    String operationType,
    List<BlockPos> positions,
    List<BlockState> states
) implements CustomPayload {
    
    public static final CustomPayload.Id<TerrainOperationPayload> ID = 
        new CustomPayload.Id<>(Identifier.of("pushdozer", "terrain_operation"));

    public static final PacketCodec<PacketByteBuf, TerrainOperationPayload> CODEC = new PacketCodec<>() {
        @Override
        public void encode(PacketByteBuf buf, TerrainOperationPayload payload) {
            buf.writeString(payload.operationType());
            
            // 写入位置列表
            buf.writeVarInt(payload.positions().size());
            for (BlockPos pos : payload.positions()) {
                buf.writeBlockPos(pos);
            }
            
            // 写入方块状态列表
            buf.writeVarInt(payload.states().size());
            for (BlockState state : payload.states()) {
                buf.writeVarInt(Block.getRawIdFromState(state));
            }
        }

        @Override
        public TerrainOperationPayload decode(PacketByteBuf buf) {
            String operationType = buf.readString();
            
            // 读取位置列表
            int posCount = buf.readVarInt();
            List<BlockPos> positions = new ArrayList<>(posCount);
            for (int i = 0; i < posCount; i++) {
                positions.add(buf.readBlockPos());
            }
            
            // 读取方块状态列表
            int stateCount = buf.readVarInt();
            List<BlockState> states = new ArrayList<>(stateCount);
            for (int i = 0; i < stateCount; i++) {
                states.add(Block.getStateFromRawId(buf.readVarInt()));
            }
            
            return new TerrainOperationPayload(operationType, positions, states);
        }
    };

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() {
        return ID;
    }
}