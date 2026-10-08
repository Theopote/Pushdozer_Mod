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

class SmoothRaiseHandlerTest extends PushdozerTestBase {

    private final TestableSmoothRaiseHandler raiseHandler = new TestableSmoothRaiseHandler();
    private final TestableSmoothLowerHandler lowerHandler = new TestableSmoothLowerHandler();
    private PushdozerConfig config;

    @BeforeEach
    void setUp() {
        config = new PushdozerConfig();
        config.setRadius(10);
        config.setSmoothStrength(1.0f);
        raiseHandler.setOperationConfig(config);
        lowerHandler.setOperationConfig(config);
    }

    @Test
    void flatTerrain_centerRaises() {
        Map<BlockPos, AbstractTerrainToolHandler.TerrainColumn> columns = flatColumns(64, 5);
        BlockPos center = new BlockPos(0, 0, 0);
        BlockPos brushCenter = new BlockPos(0, 64, 0);

        int target = raiseHandler.computeTargetHeight(columns, columns.get(center), center, brushCenter);

        assertTrue(target > 64, "Center of flat terrain should raise");
    }

    @Test
    void singleBump_stillRaises() {
        Map<BlockPos, AbstractTerrainToolHandler.TerrainColumn> columns = flatColumns(64, 5);
        BlockPos peak = new BlockPos(0, 0, 0);
        columns.put(peak, column(Blocks.STONE.getDefaultState(), 70));
        BlockPos brushCenter = new BlockPos(0, 64, 0);

        int target = raiseHandler.computeTargetHeight(columns, columns.get(peak), peak, brushCenter);

        assertTrue(target > 70, "Raise should still lift a local peak");
    }

    @Test
    void singleDepression_fillsPit() {
        Map<BlockPos, AbstractTerrainToolHandler.TerrainColumn> columns = flatColumns(64, 5);
        BlockPos pit = new BlockPos(0, 0, 0);
        columns.put(pit, column(Blocks.STONE.getDefaultState(), 58));
        BlockPos brushCenter = new BlockPos(0, 64, 0);

        int target = raiseHandler.computeTargetHeight(columns, columns.get(pit), pit, brushCenter);

        assertTrue(target > 58, "Raise should fill a depression");
    }

    @Test
    void edgeFalloff_zeroAtBrushEdge() {
        Map<BlockPos, AbstractTerrainToolHandler.TerrainColumn> columns = flatColumns(64, 21);
        BlockPos edge = new BlockPos(10, 0, 0);
        BlockPos brushCenter = new BlockPos(0, 64, 0);

        int target = raiseHandler.computeTargetHeight(columns, columns.get(edge), edge, brushCenter);

        assertEquals(64, target, "At brush edge falloff should preserve original height");
    }

    @Test
    void edgeFalloff_monotonicTowardEdge() {
        Map<BlockPos, AbstractTerrainToolHandler.TerrainColumn> columns = flatColumns(64, 21);
        BlockPos brushCenter = new BlockPos(0, 64, 0);
        int inner = raiseHandler.computeTargetHeight(
            columns, columns.get(new BlockPos(8, 0, 0)), new BlockPos(8, 0, 0), brushCenter);
        int mid = raiseHandler.computeTargetHeight(
            columns, columns.get(new BlockPos(9, 0, 0)), new BlockPos(9, 0, 0), brushCenter);
        int edge = raiseHandler.computeTargetHeight(
            columns, columns.get(new BlockPos(10, 0, 0)), new BlockPos(10, 0, 0), brushCenter);

        assertTrue(inner >= mid, "Raise delta should decrease toward brush edge");
        assertTrue(mid >= edge, "Raise delta should decrease toward brush edge");
        assertEquals(64, edge);
    }

    @Test
    void maxStrength_respectsMaxDelta() {
        Map<BlockPos, AbstractTerrainToolHandler.TerrainColumn> columns = flatColumns(64, 5);
        BlockPos center = new BlockPos(0, 0, 0);
        BlockPos brushCenter = new BlockPos(0, 64, 0);

        int target = raiseHandler.computeTargetHeight(columns, columns.get(center), center, brushCenter);

        assertTrue(target - 64 <= 4, "Raise delta should not exceed max per stroke (4)");
        assertTrue(target - 64 >= 1, "At full strength center should produce a modest raise");
    }

    @Test
    void symmetry_withLowerOnFlatTerrain() {
        Map<BlockPos, AbstractTerrainToolHandler.TerrainColumn> columns = flatColumns(64, 11);
        BlockPos brushCenter = new BlockPos(0, 64, 0);

        for (int z = -8; z <= 8; z++) {
            for (int x = -8; x <= 8; x++) {
                if (x * x + z * z > 100) {
                    continue;
                }
                BlockPos pos = new BlockPos(x, 0, z);
                int raised = raiseHandler.computeTargetHeight(columns, columns.get(pos), pos, brushCenter);
                int lowered = lowerHandler.computeTargetHeight(columns, columns.get(pos), pos, brushCenter);
                int raiseDelta = raised - 64;
                int lowerDelta = 64 - lowered;
                assertTrue(Math.abs(raiseDelta - lowerDelta) <= 2,
                    "Raise/lower deltas should mirror at " + pos
                        + " within max-delta difference (raise=" + raiseDelta + ", lower=" + lowerDelta + ")");
            }
        }
    }

    @Test
    void samplePadding_includesOutsideNeighborsForEdgeColumn() {
        BlockPos brushEdge = new BlockPos(3, 0, 0);
        Map<BlockPos, AbstractTerrainToolHandler.TerrainColumn> modifyOnly = new HashMap<>();
        modifyOnly.put(brushEdge, column(Blocks.STONE.getDefaultState(), 70));

        int padding = raiseHandler.getSamplePaddingBlocks(config);
        BlockPos outsideNeighbor = new BlockPos(5, 0, 0);
        var expanded = AbstractTerrainToolHandler.expandColumnPositions(modifyOnly.keySet(), padding);

        Map<BlockPos, AbstractTerrainToolHandler.TerrainColumn> paddedSample = new HashMap<>(modifyOnly);
        paddedSample.put(outsideNeighbor, column(Blocks.STONE.getDefaultState(), 64));

        int withPadding = raiseHandler.computeTargetHeight(
            paddedSample, paddedSample.get(brushEdge), brushEdge, new BlockPos(0, 64, 0));
        int withoutPadding = raiseHandler.computeTargetHeight(
            modifyOnly, modifyOnly.get(brushEdge), brushEdge, new BlockPos(0, 64, 0));

        assertTrue(withPadding != withoutPadding || withPadding > 70,
            "Sample padding should include outside neighbors that influence H1 smoothing");
        assertTrue(padding > 0);
        assertTrue(expanded.contains(outsideNeighbor));
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

    private static final class TestableSmoothRaiseHandler extends SmoothRaiseHandler {
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
