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

    public static final int MAX_BLOCKS_PER_PACKET = 500;

    public static final CustomPayload.Id<TerrainOperationPayload> ID =
        new CustomPayload.Id<>(Identifier.of("pushdozer", "terrain_operation"));

    public static final PacketCodec<PacketByteBuf, TerrainOperationPayload> CODEC = new PacketCodec<>() {
        @Override
        public void encode(PacketByteBuf buf, TerrainOperationPayload payload) {
            validateLists(payload.positions(), payload.states());
            buf.writeString(payload.operationType());

            buf.writeVarInt(payload.positions().size());
            for (BlockPos pos : payload.positions()) {
                buf.writeBlockPos(pos);
            }

            buf.writeVarInt(payload.states().size());
            for (BlockState state : payload.states()) {
                buf.writeVarInt(Block.getRawIdFromState(state));
            }
        }

        @Override
        public TerrainOperationPayload decode(PacketByteBuf buf) {
            String operationType = buf.readString();

            int posCount = readBoundedCount(buf);
            List<BlockPos> positions = new ArrayList<>(posCount);
            for (int i = 0; i < posCount; i++) {
                positions.add(buf.readBlockPos());
            }

            int stateCount = readBoundedCount(buf);
            if (stateCount != posCount) {
                throw new IllegalArgumentException("Terrain operation position/state count mismatch: "
                    + posCount + " vs " + stateCount);
            }

            List<BlockState> states = new ArrayList<>(stateCount);
            for (int i = 0; i < stateCount; i++) {
                states.add(Block.getStateFromRawId(buf.readVarInt()));
            }

            return new TerrainOperationPayload(operationType, positions, states);
        }
    };

    private static int readBoundedCount(PacketByteBuf buf) {
        int count = buf.readVarInt();
        if (count < 0 || count > MAX_BLOCKS_PER_PACKET) {
            throw new IllegalArgumentException("Invalid terrain operation block count: " + count);
        }
        return count;
    }

    private static void validateLists(List<BlockPos> positions, List<BlockState> states) {
        if (positions.size() != states.size()) {
            throw new IllegalArgumentException("Terrain operation position/state count mismatch");
        }
        if (positions.size() > MAX_BLOCKS_PER_PACKET) {
            throw new IllegalArgumentException("Terrain operation exceeds max blocks: " + positions.size());
        }
    }

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() {
        return ID;
    }
}
