package com.pushdozer.items.handlers;

import com.pushdozer.PushdozerTestBase;
import com.pushdozer.config.PushdozerConfig;
import net.minecraft.block.Blocks;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SurfaceRoughenHandlerTest extends PushdozerTestBase {

    private final TestableSurfaceRoughenHandler handler = new TestableSurfaceRoughenHandler();
    private PushdozerConfig config;

    @BeforeEach
    void setUp() {
        config = new PushdozerConfig();
        config.setRadius(10);
        config.setRoughnessStrength(1.0f);
        config.setSmoothingIntensity(0.0f);
        config.setNoiseSeed(12345L);
        config.setNoiseAutoScale(false);
        config.setNoiseFrequency(0.05f);
        config.setNoisePersistence(0.5f);
        config.setNoiseOctaves(3);
        handler.setOperationConfig(config);
        handler.prepareNoise();
    }

    @Test
    void edgeFalloff_zeroAtBrushEdge() {
        Map<BlockPos, AbstractTerrainToolHandler.TerrainColumn> columns = flatColumns(64, 21);
        BlockPos edge = new BlockPos(10, 0, 0);
        BlockPos brushCenter = new BlockPos(0, 64, 0);

        int target = handler.computeTargetHeight(columns, columns.get(edge), edge, brushCenter);

        assertEquals(64, target, "Combined delta should fall off to zero at brush edge");
    }

    @Test
    void edgeFalloff_monotonicTowardEdge() {
        config.setSmoothingIntensity(1.0f);
        config.setRoughnessStrength(0.0f);
        Map<BlockPos, AbstractTerrainToolHandler.TerrainColumn> columns = flatColumns(64, 21);
        columns.put(new BlockPos(0, 0, 0), column(Blocks.STONE.getDefaultState(), 70));
        BlockPos brushCenter = new BlockPos(0, 64, 0);

        int inner = Math.abs(handler.computeTargetHeight(
            columns, columns.get(new BlockPos(8, 0, 0)), new BlockPos(8, 0, 0), brushCenter) - 64);
        int mid = Math.abs(handler.computeTargetHeight(
            columns, columns.get(new BlockPos(9, 0, 0)), new BlockPos(9, 0, 0), brushCenter) - 64);
        int edge = Math.abs(handler.computeTargetHeight(
            columns, columns.get(new BlockPos(10, 0, 0)), new BlockPos(10, 0, 0), brushCenter) - 64);

        assertTrue(inner >= mid, "Smoothing delta should decrease toward brush edge");
        assertTrue(mid >= edge, "Smoothing delta should decrease toward brush edge");
        assertEquals(0, edge);
    }

    @Test
    void negativeHeight_supportsUndergroundTerrain() {
        config.setSmoothingIntensity(0.8f);
        Map<BlockPos, AbstractTerrainToolHandler.TerrainColumn> columns = flatColumns(-30, 5);
        columns.put(new BlockPos(0, 0, 0), column(Blocks.STONE.getDefaultState(), -25));
        BlockPos center = new BlockPos(0, 0, 0);
        BlockPos brushCenter = new BlockPos(0, -20, 0);

        int target = handler.computeTargetHeight(columns, columns.get(center), center, brushCenter);

        assertTrue(target < -25 && target > -30,
            "Underground bump should smooth downward toward lower neighbors");
    }

    @Test
    void isolatedColumn_withoutNeighbors_keepsOriginalWhenOnlySmoothing() {
        config.setSmoothingIntensity(1.0f);
        config.setRoughnessStrength(0.0f);
        Map<BlockPos, AbstractTerrainToolHandler.TerrainColumn> columns = new HashMap<>();
        BlockPos only = new BlockPos(0, 0, 0);
        columns.put(only, column(Blocks.STONE.getDefaultState(), 64));

        int target = handler.computeTargetHeight(columns, columns.get(only), only, new BlockPos(0, 64, 0));

        assertEquals(64, target);
    }

    @Test
    void differentSeeds_produceDifferentNoise() {
        Map<BlockPos, AbstractTerrainToolHandler.TerrainColumn> columns = flatColumns(64, 5);
        // Avoid world origin where Perlin noise can be exactly zero for many seeds.
        BlockPos sample = new BlockPos(4, 0, 3);
        BlockPos brushCenter = new BlockPos(0, 64, 0);

        config.setNoiseSeed(111L);
        handler.prepareNoise();
        int first = handler.computeTargetHeight(columns, columns.get(sample), sample, brushCenter);

        config.setNoiseSeed(222L);
        handler.prepareNoise();
        int second = handler.computeTargetHeight(columns, columns.get(sample), sample, brushCenter);

        assertNotEquals(first, second, "Different seeds should change roughened height");
    }

    @Test
    void sameSeed_isRepeatable() {
        Map<BlockPos, AbstractTerrainToolHandler.TerrainColumn> columns = flatColumns(64, 5);
        BlockPos center = new BlockPos(0, 0, 0);
        BlockPos brushCenter = new BlockPos(0, 64, 0);

        int first = handler.computeTargetHeight(columns, columns.get(center), center, brushCenter);
        int second = handler.computeTargetHeight(columns, columns.get(center), center, brushCenter);

        assertEquals(first, second);
    }

    @Test
    void samplePadding_isPositive() {
        assertTrue(handler.getSamplePaddingBlocks(config) > 0);
    }

    @Test
    void samplePadding_influencesSmoothedBaselineAtEdge() {
        config.setSmoothingIntensity(1.0f);
        config.setRoughnessStrength(0.0f);
        BlockPos brushEdge = new BlockPos(3, 0, 0);
        Map<BlockPos, AbstractTerrainToolHandler.TerrainColumn> modifyOnly = new HashMap<>();
        modifyOnly.put(brushEdge, column(Blocks.STONE.getDefaultState(), 70));
        BlockPos outsideNeighbor = new BlockPos(5, 0, 0);

        int padding = handler.getSamplePaddingBlocks(config);
        Map<BlockPos, AbstractTerrainToolHandler.TerrainColumn> paddedSample = new HashMap<>(modifyOnly);
        paddedSample.put(outsideNeighbor, column(Blocks.STONE.getDefaultState(), 64));

        int withPadding = handler.computeTargetHeight(
            paddedSample, paddedSample.get(brushEdge), brushEdge, new BlockPos(0, 64, 0));
        int withoutPadding = handler.computeTargetHeight(
            modifyOnly, modifyOnly.get(brushEdge), brushEdge, new BlockPos(0, 64, 0));

        assertTrue(withPadding != withoutPadding || withPadding < 70);
        assertTrue(padding > 0);
        assertTrue(AbstractTerrainToolHandler.expandColumnPositions(modifyOnly.keySet(), padding).contains(outsideNeighbor));
    }

    private static Map<BlockPos, AbstractTerrainToolHandler.TerrainColumn> flatColumns(int height, int radius) {
        Map<BlockPos, AbstractTerrainToolHandler.TerrainColumn> columns = new HashMap<>();
        for (int z = -radius; z <= radius; z++) {
            for (int x = -radius; x <= radius; x++) {
                if (x * x + z * z <= radius * radius) {
                    columns.put(new BlockPos(x, 0, z), column(Blocks.STONE.getDefaultState(), height));
                }
            }
        }
        return columns;
    }

    private static AbstractTerrainToolHandler.TerrainColumn column(net.minecraft.block.BlockState state, int y) {
        return new AbstractTerrainToolHandler.TerrainColumn(state, y);
    }

    private static final class TestableSurfaceRoughenHandler extends SurfaceRoughenHandler {
        void setOperationConfig(PushdozerConfig config) {
            this.config = config;
        }

        void prepareNoise() {
            beginOperationNoise(config);
        }

        int computeTargetHeight(Map<BlockPos, AbstractTerrainToolHandler.TerrainColumn> columns,
                                AbstractTerrainToolHandler.TerrainColumn current,
                                BlockPos columnXZ,
                                BlockPos brushCenter) {
            return calculateTargetHeight(columns, current, columnXZ, brushCenter);
        }
    }
}
