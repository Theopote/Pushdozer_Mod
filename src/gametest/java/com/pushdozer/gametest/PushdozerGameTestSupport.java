package com.pushdozer.gametest;

import com.mojang.authlib.GameProfile;
import com.pushdozer.config.PushdozerConfig;
import com.pushdozer.config.domain.SurfaceConfig;
import net.fabricmc.fabric.api.entity.FakePlayer;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
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

    static PushdozerConfig createShorelineConfig() {
        return createShorelineConfig(1, 1);
    }

    static PushdozerConfig createShorelineConfig(int length, int width) {
        PushdozerConfig config = new PushdozerConfig();
        config.setGeometryType(PushdozerConfig.GeometryType.BOX);
        config.setLength(length);
        config.setWidth(width);
        config.setHeight(5);
        config.setBoxHeight(5);
        config.setHeightMode(PushdozerConfig.HeightMode.NO_LIMIT);
        config.setShorelineType(PushdozerConfig.ShorelineType.BEACH);
        config.setShorelineWidth(3);
        config.setPlantVegetationEnabled(false);
        config.setNoiseSeed(42L);
        return config;
    }

    static PushdozerConfig createBatchPlantFlowerConfig() {
        PushdozerConfig config = new PushdozerConfig();
        config.setGeometryType(PushdozerConfig.GeometryType.BOX);
        config.setLength(1);
        config.setWidth(1);
        config.setBoxHeight(5);
        config.setHeightMode(PushdozerConfig.HeightMode.NO_LIMIT);
        config.setPlantType(PushdozerConfig.PlantType.CUSTOM);
        config.setCustomPlantBlocks(List.of(Blocks.SUNFLOWER));
        config.setPlantDensity(1.0f);
        config.setNoiseSeed(42L);
        return config;
    }

    static PushdozerConfig createSurfaceConvertConfig(String targetBlockId) {
        PushdozerConfig config = new PushdozerConfig();
        config.setGeometryType(PushdozerConfig.GeometryType.BOX);
        config.setLength(1);
        config.setWidth(1);
        config.setBoxHeight(3);
        config.setHeightMode(PushdozerConfig.HeightMode.NO_LIMIT);
        config.setNoiseSeed(42L);
        config.setSurfaceConvertDistribution(PushdozerConfig.SurfaceConvertDistribution.SCATTER);
        // Explicit unlimited depth for void GameTest columns; production default remains 3.
        config.setSurfaceConvertMaxBelowSurfaceDepth(-1);
        config.getSurfaceConvertBlocks().clear();
        config.getSurfaceConvertBlocks().add(new SurfaceConfig.SurfaceConvertBlock(targetBlockId, 100f));
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
