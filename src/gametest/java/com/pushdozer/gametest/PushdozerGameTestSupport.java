package com.pushdozer.gametest;

import com.mojang.authlib.GameProfile;
import com.pushdozer.config.PushdozerConfig;
import com.pushdozer.config.domain.SurfaceConfig;
import com.pushdozer.items.handlers.SurfaceConvertHandler;
import com.pushdozer.items.handlers.surface.SurfaceConvertMaterialSelector;
import com.pushdozer.shapes.GeometryShape;
import net.fabricmc.fabric.api.entity.FakePlayer;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.test.TestContext;
import net.minecraft.text.Text;

import java.lang.reflect.Method;
import java.util.List;
import java.util.UUID;

final class PushdozerGameTestSupport {
    private PushdozerGameTestSupport() {
    }

    static void assertTrue(TestContext context, boolean condition, String message) {
        context.assertTrue(condition, Text.literal(message));
    }

    static void assertFalse(TestContext context, boolean condition, String message) {
        context.assertFalse(condition, Text.literal(message));
    }

    static PushdozerConfig createExcavationConfig(Block... breakableBlocks) {
        PushdozerConfig config = new PushdozerConfig();
        config.setBreakableBlocks(List.of(breakableBlocks));
        config.setHeightMode(PushdozerConfig.HeightMode.NO_LIMIT);
        return config;
    }

    static PushdozerConfig createPlacementConfig() {
        PushdozerConfig config = new PushdozerConfig();
        config.setHeightMode(PushdozerConfig.HeightMode.NO_LIMIT);
        return config;
    }

    /**
     * Waits for {@link com.pushdozer.operations.UndoRedoManager} cooldown before redo in GameTests.
     */
    static void runAfterUndoCooldown(TestContext context, Runnable action) {
        context.runAtTick(context.getTick() + 1, () -> {
            try {
                Thread.sleep(350);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new RuntimeException(ex);
            }
            action.run();
        });
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

    static void prepareSurfaceConvertColumn(TestContext context, BlockPos groundPos, Block groundBlock) {
        for (int y = groundPos.getY(); y <= groundPos.getY() + 8; y++) {
            context.setBlockState(new BlockPos(groundPos.getX(), y, groundPos.getZ()), Blocks.AIR);
        }
        context.setBlockState(new BlockPos(groundPos.getX(), groundPos.getY() - 1, groundPos.getZ()), Blocks.STONE);
        context.setBlockState(groundPos, groundBlock);
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

    /**
     * Ensures {@link GameTest#setupTicks()} elapses before the test body runs.
     * <p>
     * CustomTestMethodInvoker suites must call this from {@code invokeTestMethod}. Some runtimes
     * already advance the tick counter through setup; scheduling uses an absolute target tick so
     * setup is never applied twice.
     */
    static void invokeAfterSetup(TestContext context, Object target, Method method) throws ReflectiveOperationException {
        GameTest gameTest = method.getAnnotation(GameTest.class);
        int setupTicks = gameTest != null ? gameTest.setupTicks() : 0;
        if (setupTicks > 0 && context.getTick() < setupTicks) {
            context.runAtTick(setupTicks, () -> invokeUnchecked(target, method, context));
            return;
        }
        method.invoke(target, context);
    }

    private static void invokeUnchecked(Object target, Method method, TestContext context) {
        try {
            method.invoke(target, context);
        } catch (ReflectiveOperationException ex) {
            throw new RuntimeException(ex);
        }
    }

    /**
     * Verifies brush footprint, surface resolution, and material selection before apply.
     */
    static void assertSurfaceConvertPreconditions(TestContext context, GeometryShape shape,
                                                  BlockPos relativeCenter, PushdozerConfig config,
                                                  BlockPos relativeSurface, Block expectedTarget) {
        BlockPos absoluteCenter = context.getAbsolutePos(relativeCenter);
        BlockPos absoluteSurface = context.getAbsolutePos(relativeSurface);
        BlockPos columnKey = new BlockPos(absoluteCenter.getX(), 0, absoluteCenter.getZ());

        assertTrue(context,
            SurfaceConvertHandler.collectBrushColumns(shape).contains(columnKey),
            "Surface convert brush must include target XZ column " + columnKey);

        boolean shapeCoversColumn = shape.getBlockPositions().stream()
            .anyMatch(pos -> pos.getX() == absoluteCenter.getX() && pos.getZ() == absoluteCenter.getZ());
        assertTrue(context,shapeCoversColumn,
            "Shape block positions must cover target column at " + absoluteCenter);

        BlockPos resolved = SurfaceConvertHandler.resolveConvertibleSurface(
            context.getWorld(),
            columnKey,
            shape.getMaxY(absoluteCenter),
            config.getSurfaceConvertMaxBelowSurfaceDepth(),
            config.isConvertArtificialSurfaces());
        assertTrue(context,resolved != null && resolved.equals(absoluteSurface),
            "resolveConvertibleSurface should resolve surface at " + absoluteSurface + " but got " + resolved);

        SurfaceConvertMaterialSelector.SelectionContext materials = SurfaceConvertMaterialSelector.prepare(config);
        assertFalse(context,materials.isEmpty(), "SurfaceConvertMaterialSelector must resolve configured materials");
        Block selectedTarget = SurfaceConvertMaterialSelector.selectBlock(materials, columnKey);
        assertTrue(context,selectedTarget == expectedTarget,
            "SurfaceConvertMaterialSelector should return " + expectedTarget + " but got " + selectedTarget);

        assertFalse(context,context.getBlockState(relativeSurface).isOf(expectedTarget),
            "Surface should differ from target block before convert");
    }
}
