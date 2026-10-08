package com.pushdozer.gametest;

import com.mojang.authlib.GameProfile;
import com.pushdozer.config.PushdozerConfig;
import net.fabricmc.fabric.api.entity.FakePlayer;
import net.minecraft.block.Block;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.test.TestContext;

import java.util.List;
import java.util.UUID;

final class PushdozerGameTestSupport {
    private PushdozerGameTestSupport() {
    }

    static PushdozerConfig createExcavationConfig(Block... breakableBlocks) {
        PushdozerConfig config = new PushdozerConfig();
        config.setBreakableBlocks(List.of(breakableBlocks));
        config.setHeightMode(PushdozerConfig.HeightMode.NO_LIMIT);
        return config;
    }

    /**
     * Creates a unique mock {@link ServerPlayerEntity} for GameTest.
     * <p>
     * Replaces the deprecated {@code TestContext#createMockCreativeServerPlayerInWorld()},
     * which is marked for removal. Uses a fresh UUID so undo stacks stay isolated across tests.
     */
    static ServerPlayerEntity createMockServerPlayer(TestContext context) {
        return FakePlayer.get(
            context.getWorld(),
            new GameProfile(UUID.randomUUID(), "test-mock-player")
        );
    }
}
