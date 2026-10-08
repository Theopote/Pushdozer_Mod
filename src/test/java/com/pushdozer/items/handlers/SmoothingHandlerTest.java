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

class SmoothingHandlerTest extends PushdozerTestBase {

    private final TestableSmoothingHandler handler = new TestableSmoothingHandler();
    private PushdozerConfig config;

    @BeforeEach
    void setUp() {
        config = new PushdozerConfig();
        config.setRadius(10);
        config.setSmoothStrength(1.0f);
        handler.setOperationConfig(config);
    }

    @Test
    void flatTerrain_unchanged() {
        Map<BlockPos, AbstractTerrainToolHandler.TerrainColumn> columns = flatColumns(64, 5);
        BlockPos center = new BlockPos(0, 0, 0);
        BlockPos brushCenter = new BlockPos(0, 64, 0);

        int target = handler.computeTargetHeight(columns, columns.get(center), center, brushCenter);

        assertEquals(64, target);
    }

    @Test
    void singleBump_reducesPeak() {
        Map<BlockPos, AbstractTerrainToolHandler.TerrainColumn> columns = flatColumns(64, 5);
        BlockPos peak = new BlockPos(0, 0, 0);
        columns.put(peak, column(Blocks.STONE.getDefaultState(), 70));
        BlockPos brushCenter = new BlockPos(0, 64, 0);

        int target = handler.computeTargetHeight(columns, columns.get(peak), peak, brushCenter);

        assertTrue(target < 70, "Standard smooth should lower a single peak");
        assertTrue(target > 64, "Standard smooth should not over-flatten in one pass");
    }

    @Test
    void singleDepression_raisesFloor() {
        Map<BlockPos, AbstractTerrainToolHandler.TerrainColumn> columns = flatColumns(64, 5);
        BlockPos pit = new BlockPos(0, 0, 0);
        columns.put(pit, column(Blocks.STONE.getDefaultState(), 58));
        BlockPos brushCenter = new BlockPos(0, 64, 0);

        int target = handler.computeTargetHeight(columns, columns.get(pit), pit, brushCenter);

        assertTrue(target > 58, "Standard smooth should raise a single depression");
        assertTrue(target < 64, "Standard smooth should not over-fill in one pass");
    }

    @Test
    void edgeFalloff_zeroAtBrushEdge() {
        Map<BlockPos, AbstractTerrainToolHandler.TerrainColumn> columns = flatColumns(64, 21);
        BlockPos edge = new BlockPos(10, 0, 0);
        columns.put(edge, column(Blocks.STONE.getDefaultState(), 70));
        BlockPos brushCenter = new BlockPos(0, 64, 0);

        int target = handler.computeTargetHeight(columns, columns.get(edge), edge, brushCenter);

        assertEquals(70, target, "At brush edge falloff should preserve original height");
    }

    @Test
    void samplePadding_includesOutsideNeighborsForEdgeColumn() {
        config.setRadius(10);
        BlockPos brushEdge = new BlockPos(3, 0, 0);
        Map<BlockPos, AbstractTerrainToolHandler.TerrainColumn> modifyOnly = new HashMap<>();
        modifyOnly.put(brushEdge, column(Blocks.STONE.getDefaultState(), 70));

        int padding = handler.getSamplePaddingBlocks(config);
        BlockPos outsideNeighbor = new BlockPos(5, 0, 0);
        var expanded = AbstractTerrainToolHandler.expandColumnPositions(modifyOnly.keySet(), padding);

        Map<BlockPos, AbstractTerrainToolHandler.TerrainColumn> paddedSample = new HashMap<>(modifyOnly);
        paddedSample.put(outsideNeighbor, column(Blocks.STONE.getDefaultState(), 64));

        int withPadding = handler.computeTargetHeight(
            paddedSample, paddedSample.get(brushEdge), brushEdge, new BlockPos(0, 64, 0));
        int withoutPadding = handler.computeTargetHeight(
            modifyOnly, modifyOnly.get(brushEdge), brushEdge, new BlockPos(0, 64, 0));

        assertEquals(70, withoutPadding, "Isolated column should stay unchanged without padding neighbors");
        assertTrue(withPadding < withoutPadding,
            "Sample padding should include outside neighbors that influence edge columns");
        assertTrue(padding > 0);
        assertTrue(expanded.contains(outsideNeighbor));
        assertTrue(expanded.size() > modifyOnly.size());
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

    private static final class TestableSmoothingHandler extends SmoothingHandler {
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
