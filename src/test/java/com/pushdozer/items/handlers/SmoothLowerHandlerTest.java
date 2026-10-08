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
import static org.junit.jupiter.api.Assertions.assertTrue;

class SmoothLowerHandlerTest extends PushdozerTestBase {

    private final TestableSmoothLowerHandler handler = new TestableSmoothLowerHandler();
    private PushdozerConfig config;

    @BeforeEach
    void setUp() {
        config = new PushdozerConfig();
        config.setRadius(10);
        config.setSmoothStrength(1.0f);
        handler.setOperationConfig(config);
    }

    @Test
    void flatTerrain_centerLowers() {
        Map<BlockPos, AbstractTerrainToolHandler.TerrainColumn> columns = flatColumns(64, 5);
        BlockPos center = new BlockPos(0, 0, 0);
        BlockPos brushCenter = new BlockPos(0, 64, 0);

        int target = handler.computeTargetHeight(columns, columns.get(center), center, brushCenter);

        assertTrue(target < 64, "Center of flat terrain should lower");
    }

    @Test
    void singleBump_lowersPeak() {
        Map<BlockPos, AbstractTerrainToolHandler.TerrainColumn> columns = flatColumns(64, 5);
        BlockPos peak = new BlockPos(0, 0, 0);
        columns.put(peak, column(Blocks.STONE.getDefaultState(), 70));
        BlockPos brushCenter = new BlockPos(0, 64, 0);

        int target = handler.computeTargetHeight(columns, columns.get(peak), peak, brushCenter);

        assertTrue(target < 70, "Lower should reduce a local peak");
    }

    @Test
    void singleDepression_stillLowers() {
        Map<BlockPos, AbstractTerrainToolHandler.TerrainColumn> columns = flatColumns(64, 5);
        BlockPos pit = new BlockPos(0, 0, 0);
        columns.put(pit, column(Blocks.STONE.getDefaultState(), 58));
        BlockPos brushCenter = new BlockPos(0, 64, 0);

        int target = handler.computeTargetHeight(columns, columns.get(pit), pit, brushCenter);

        assertTrue(target < 58, "Lower should still cut a local depression");
    }

    @Test
    void edgeFalloff_zeroAtBrushEdge() {
        Map<BlockPos, AbstractTerrainToolHandler.TerrainColumn> columns = flatColumns(64, 21);
        BlockPos edge = new BlockPos(10, 0, 0);
        BlockPos brushCenter = new BlockPos(0, 64, 0);

        int target = handler.computeTargetHeight(columns, columns.get(edge), edge, brushCenter);

        assertEquals(64, target, "At brush edge falloff should preserve original height");
    }

    @Test
    void edgeFalloff_monotonicTowardEdge() {
        Map<BlockPos, AbstractTerrainToolHandler.TerrainColumn> columns = flatColumns(64, 21);
        BlockPos brushCenter = new BlockPos(0, 64, 0);
        int inner = 64 - handler.computeTargetHeight(
            columns, columns.get(new BlockPos(8, 0, 0)), new BlockPos(8, 0, 0), brushCenter);
        int mid = 64 - handler.computeTargetHeight(
            columns, columns.get(new BlockPos(9, 0, 0)), new BlockPos(9, 0, 0), brushCenter);
        int edge = 64 - handler.computeTargetHeight(
            columns, columns.get(new BlockPos(10, 0, 0)), new BlockPos(10, 0, 0), brushCenter);

        assertTrue(inner >= mid, "Lower delta should decrease toward brush edge");
        assertTrue(mid >= edge, "Lower delta should decrease toward brush edge");
        assertEquals(0, edge);
    }

    @Test
    void maxStrength_respectsMaxDelta() {
        Map<BlockPos, AbstractTerrainToolHandler.TerrainColumn> columns = flatColumns(64, 5);
        BlockPos center = new BlockPos(0, 0, 0);
        BlockPos brushCenter = new BlockPos(0, 64, 0);

        int target = handler.computeTargetHeight(columns, columns.get(center), center, brushCenter);

        assertTrue(64 - target <= 3, "Lower delta should not exceed max per stroke (3)");
        assertTrue(64 - target >= 1, "At full strength center should produce a modest lower");
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

    private static final class TestableSmoothLowerHandler extends SmoothLowerHandler {
        void setOperationConfig(PushdozerConfig config) {
            this.config = config;
        }

        int computeTargetHeight(Map<BlockPos, AbstractTerrainToolHandler.TerrainColumn> columns,
                                AbstractTerrainToolHandler.TerrainColumn current,
                                BlockPos columnXZ,
                                BlockPos brushCenter) {
            return calculateTargetHeight(columns, current, columnXZ, brushCenter);
        }
    }
}
