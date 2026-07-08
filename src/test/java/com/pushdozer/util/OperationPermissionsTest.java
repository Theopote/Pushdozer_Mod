package com.pushdozer.util;

import com.pushdozer.PushdozerTestBase;
import com.pushdozer.config.PushdozerConfig;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OperationPermissionsTest extends PushdozerTestBase {

    @Test
    void allowsValidBrushSizeInMultiplayer() {
        ServerPlayerEntity player = Mockito.mock(ServerPlayerEntity.class);
        ServerWorld world = Mockito.mock(ServerWorld.class);
        MinecraftServer server = Mockito.mock(MinecraftServer.class);
        PushdozerConfig config = new PushdozerConfig();
        config.setRadius(PushdozerConfig.MAX_BRUSH_RADIUS);

        Mockito.when(world.getServer()).thenReturn(server);
        Mockito.when(server.isSingleplayer()).thenReturn(false);

        assertTrue(OperationPermissions.checkForTerrainOperation(player, world, config));
    }

    @Test
    void deniesOversizedBrushAndNotifiesPlayer() {
        ServerPlayerEntity player = Mockito.mock(ServerPlayerEntity.class);
        Mockito.when(player.getName()).thenReturn(Text.literal("test"));

        assertFalse(OperationPermissions.checkBrushRadius(player, PushdozerConfig.MAX_BRUSH_RADIUS + 1));
        Mockito.verify(player).sendMessage(Mockito.any(), Mockito.eq(true));
    }
}
