package com.pushdozer.operations;

import com.pushdozer.PushdozerTestBase;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.chunk.light.LightingProvider;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class UndoRedoManagerLifecycleTest extends PushdozerTestBase {

    @Test
    void undoLastAction_restoresStackOnFailure() {
        UndoRedoManager manager = new UndoRedoManager();
        ServerWorld world = mockServerWorld(mock(MinecraftServer.class));
        UUID playerId = UUID.randomUUID();
        ServerPlayerEntity player = mock(ServerPlayerEntity.class);
        when(player.getUuid()).thenReturn(playerId);
        when(player.getName()).thenReturn(net.minecraft.text.Text.literal("test"));
        when(player.getEntityWorld()).thenReturn(world);

        UndoAction action = new UndoAction(
            UndoAction.ActionType.BREAK,
            World.OVERWORLD,
            List.of(new BlockPos(0, 10_000, 0)),
            List.of(mock(BlockState.class)),
            List.of(mock(BlockState.class))
        );
        manager.pushUndoAction(player, action);
        manager.undoLastAction(player, world);

        assertEquals(1, manager.getUndoStackSize(player), "Failed undo should restore action to undo stack");
    }

    @Test
    void pushUndoAction_isolatedByDimension() {
        UndoRedoManager manager = new UndoRedoManager();
        UUID playerId = UUID.randomUUID();
        ServerPlayerEntity player = mock(ServerPlayerEntity.class);
        when(player.getUuid()).thenReturn(playerId);
        when(player.getName()).thenReturn(net.minecraft.text.Text.literal("test"));

        ServerWorld overworld = mock(ServerWorld.class);
        when(overworld.getRegistryKey()).thenReturn(World.OVERWORLD);
        when(player.getEntityWorld()).thenReturn(overworld);

        manager.pushUndoAction(player, sampleAction(World.OVERWORLD, 1));
        manager.pushUndoAction(player, sampleAction(World.NETHER, 2));

        assertEquals(1, manager.getUndoStackSize(player));
    }

    private static UndoAction sampleAction(net.minecraft.registry.RegistryKey<World> worldKey, int seed) {
        BlockState state = mock(BlockState.class);
        return new UndoAction(
            UndoAction.ActionType.BREAK,
            worldKey,
            List.of(new BlockPos(seed, 64, seed)),
            List.of(state),
            List.of(state)
        );
    }

    private static UndoAction largeAction(int count) {
        List<BlockPos> positions = new ArrayList<>(count);
        List<BlockState> original = new ArrayList<>(count);
        List<BlockState> updated = new ArrayList<>(count);
        BlockState state = mock(BlockState.class);
        for (int i = 0; i < count; i++) {
            positions.add(new BlockPos(i, 64, 0));
            original.add(state);
            updated.add(state);
        }
        return new UndoAction(UndoAction.ActionType.BREAK, World.OVERWORLD, positions, original, updated);
    }

    private static ServerWorld mockServerWorld(MinecraftServer server) {
        ServerWorld world = mock(ServerWorld.class);
        when(world.getServer()).thenReturn(server);
        when(world.isChunkLoaded(anyLong())).thenReturn(true);
        when(world.setBlockState(any(), any(), anyInt())).thenReturn(true);
        when(world.getBottomY()).thenReturn(-64);
        when(world.getHeight()).thenReturn(384);
        when(world.getRegistryKey()).thenReturn(World.OVERWORLD);
        when(world.getLightingProvider()).thenReturn(mock(LightingProvider.class));
        when(world.getBlockState(any())).thenReturn(Blocks.STONE.getDefaultState());
        return world;
    }
}
