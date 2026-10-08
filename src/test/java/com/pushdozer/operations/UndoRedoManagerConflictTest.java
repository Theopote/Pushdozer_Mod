package com.pushdozer.operations;

import com.pushdozer.PushdozerTestBase;
import net.minecraft.block.Blocks;
import net.minecraft.server.MinecraftServer;
import net.minecraft.network.packet.Packet;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.chunk.light.LightingProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class UndoRedoManagerConflictTest extends PushdozerTestBase {

    private static class NoSyncUndoRedoManager extends UndoRedoManager {
        @Override
        protected void sendPacket(ServerPlayerEntity player, Packet<?> packet) {
        }
    }

    private final TerrainOperationScheduler scheduler = TerrainOperationScheduler.getInstance();

    @AfterEach
    void resetScheduler() {
        scheduler.resetForTests();
    }

    @Test
    void executeUndoRedoAction_skipsPositionsWithUnexpectedBlockState() {
        UndoRedoManager manager = new NoSyncUndoRedoManager();
        MinecraftServer server = mock(MinecraftServer.class);
        ServerWorld world = mock(ServerWorld.class);
        when(world.getServer()).thenReturn(server);
        when(world.isChunkLoaded(anyLong())).thenReturn(true);
        when(world.setBlockState(any(), any(), anyInt())).thenReturn(true);
        when(world.getBottomY()).thenReturn(-64);
        when(world.getHeight()).thenReturn(384);
        when(world.getRegistryKey()).thenReturn(World.OVERWORLD);
        when(world.getLightingProvider()).thenReturn(mock(LightingProvider.class));

        BlockPos conflictPos = new BlockPos(1, 64, 0);
        BlockPos validPos = new BlockPos(2, 64, 0);
        when(world.getBlockState(any())).thenAnswer(invocation -> {
            BlockPos pos = invocation.getArgument(0);
            if (pos.equals(conflictPos)) {
                return Blocks.DIRT.getDefaultState();
            }
            if (pos.equals(validPos)) {
                return Blocks.AIR.getDefaultState();
            }
            return Blocks.STONE.getDefaultState();
        });

        UndoAction action = new UndoAction(
            UndoAction.ActionType.BREAK,
            World.OVERWORLD,
            List.of(conflictPos, validPos),
            List.of(Blocks.STONE.getDefaultState(), Blocks.STONE.getDefaultState()),
            List.of(Blocks.AIR.getDefaultState(), Blocks.AIR.getDefaultState())
        );

        ServerPlayerEntity player = mock(ServerPlayerEntity.class);
        when(player.getUuid()).thenReturn(UUID.randomUUID());
        when(player.getName()).thenReturn(net.minecraft.text.Text.literal("test"));
        AtomicBoolean finished = new AtomicBoolean(false);
        manager.executeUndoRedoAction(action, player, world, true, ok -> finished.set(true));

        assertTrue(finished.get());
    }

    @Test
    void executeUndoRedoAction_failsWhenAllPositionsConflict() {
        UndoRedoManager manager = new UndoRedoManager();
        ServerWorld world = mock(ServerWorld.class);
        when(world.isChunkLoaded(anyLong())).thenReturn(true);
        when(world.getBottomY()).thenReturn(-64);
        when(world.getHeight()).thenReturn(384);
        when(world.getRegistryKey()).thenReturn(World.OVERWORLD);
        when(world.getBlockState(any())).thenReturn(Blocks.DIRT.getDefaultState());

        UndoAction action = new UndoAction(
            UndoAction.ActionType.BREAK,
            World.OVERWORLD,
            List.of(new BlockPos(0, 64, 0)),
            List.of(Blocks.STONE.getDefaultState()),
            List.of(Blocks.AIR.getDefaultState())
        );

        ServerPlayerEntity player = mock(ServerPlayerEntity.class);
        when(player.getUuid()).thenReturn(UUID.randomUUID());
        when(player.getName()).thenReturn(net.minecraft.text.Text.literal("test"));

        AtomicBoolean success = new AtomicBoolean(true);
        manager.executeUndoRedoAction(action, player, world, true, success::set);

        assertEquals(false, success.get());
    }
}
