package com.pushdozer.network;

import com.pushdozer.PushdozerTestBase;
import net.minecraft.block.Blocks;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TerrainOperationPayloadTest extends PushdozerTestBase {

    @Test
    void codec_roundTripsValidPayload() {
        TerrainOperationPayload payload = new TerrainOperationPayload(
            "BREAK",
            List.of(new BlockPos(1, 2, 3)),
            List.of(Blocks.STONE.getDefaultState())
        );

        PacketByteBuf buf = new PacketByteBuf(io.netty.buffer.Unpooled.buffer());
        TerrainOperationPayload.CODEC.encode(buf, payload);
        TerrainOperationPayload decoded = TerrainOperationPayload.CODEC.decode(buf);

        assertEquals(payload.operationType(), decoded.operationType());
        assertEquals(payload.positions(), decoded.positions());
        assertEquals(payload.states(), decoded.states());
    }

    @Test
    void codec_rejectsMismatchedCounts() {
        PacketByteBuf buf = new PacketByteBuf(io.netty.buffer.Unpooled.buffer());
        buf.writeString("BREAK");
        buf.writeVarInt(1);
        buf.writeBlockPos(new BlockPos(0, 0, 0));
        buf.writeVarInt(2);
        buf.writeVarInt(1);
        buf.writeVarInt(1);

        assertThrows(IllegalArgumentException.class, () -> TerrainOperationPayload.CODEC.decode(buf));
    }

    @Test
    void codec_rejectsExcessiveCounts() {
        PacketByteBuf buf = new PacketByteBuf(io.netty.buffer.Unpooled.buffer());
        buf.writeString("BREAK");
        buf.writeVarInt(TerrainOperationPayload.MAX_BLOCKS_PER_PACKET + 1);

        assertThrows(IllegalArgumentException.class, () -> TerrainOperationPayload.CODEC.decode(buf));
    }
}
