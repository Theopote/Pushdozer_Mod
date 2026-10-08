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

class AdaptiveSmoothHandlerTest extends PushdozerTestBase {

    private final TestableAdaptiveSmoothHandler adaptiveHandler = new TestableAdaptiveSmoothHandler();
    private final TestableSmoothingHandler standardHandler = new TestableSmoothingHandler();
    private PushdozerConfig config;

    @BeforeEach
    void setUp() {
        config = new PushdozerConfig();
        config.setRadius(10);
        config.setSmoothStrength(1.0f);
        adaptiveHandler.setOperationConfig(config);
        standardHandler.setOperationConfig(config);
    }

    @Test
    void flatTerrain_unchanged() {
        Map<BlockPos, AbstractTerrainToolHandler.TerrainColumn> columns = flatColumns(64, 5);
        BlockPos center = new BlockPos(0, 0, 0);
        BlockPos brushCenter = new BlockPos(0, 64, 0);

        assertEquals(64, adaptiveHandler.computeTargetHeight(columns, columns.get(center), center, brushCenter));
    }

    @Test
    void ridgeStructure_changesLessThanStandard() {
        Map<BlockPos, AbstractTerrainToolHandler.TerrainColumn> columns = ridgeColumns();
        BlockPos ridge = new BlockPos(0, 0, 0);
        BlockPos brushCenter = new BlockPos(0, 64, 0);

        int adaptiveTarget = adaptiveHandler.computeTargetHeight(columns, columns.get(ridge), ridge, brushCenter);
        int standardTarget = standardHandler.computeTargetHeight(columns, columns.get(ridge), ridge, brushCenter);

        assertTrue(Math.abs(adaptiveTarget - 68) < Math.abs(standardTarget - 68),
            "Adaptive mode should preserve ridge height more than standard smooth");
    }

    @Test
    void edgeFalloff_zeroAtBrushEdge() {
        Map<BlockPos, AbstractTerrainToolHandler.TerrainColumn> columns = flatColumns(64, 21);
        BlockPos edge = new BlockPos(10, 0, 0);
        columns.put(edge, column(Blocks.STONE.getDefaultState(), 70));
        BlockPos brushCenter = new BlockPos(0, 64, 0);

        int target = adaptiveHandler.computeTargetHeight(columns, columns.get(edge), edge, brushCenter);

        assertEquals(70, target);
    }

    @Test
    void brushEdgeFalloff_monotonicBetweenInnerAndOuterRadius() {
        BlockPos brushCenter = new BlockPos(0, 64, 0);
        int brushRadius = config.getLargestBrushDimension();

        float inner = AbstractTerrainToolHandler.calculateBrushEdgeFalloff(
            new BlockPos((int) (brushRadius * 0.85f), 0, 0), brushCenter, brushRadius);
        float mid = AbstractTerrainToolHandler.calculateBrushEdgeFalloff(
            new BlockPos((int) (brushRadius * 0.95f), 0, 0), brushCenter, brushRadius);
        float outer = AbstractTerrainToolHandler.calculateBrushEdgeFalloff(
            new BlockPos(brushRadius, 0, 0), brushCenter, brushRadius);

        assertTrue(inner > mid && mid > outer);
        assertEquals(0.0f, outer, 0.001f);
    }

    private static Map<BlockPos, AbstractTerrainToolHandler.TerrainColumn> ridgeColumns() {
        Map<BlockPos, AbstractTerrainToolHandler.TerrainColumn> columns = flatColumns(64, 5);
        columns.put(new BlockPos(0, 0, 0), column(Blocks.STONE.getDefaultState(), 68));
        columns.put(new BlockPos(1, 0, 0), column(Blocks.STONE.getDefaultState(), 66));
        columns.put(new BlockPos(-1, 0, 0), column(Blocks.STONE.getDefaultState(), 66));
        columns.put(new BlockPos(0, 0, 1), column(Blocks.STONE.getDefaultState(), 64));
        columns.put(new BlockPos(0, 0, -1), column(Blocks.STONE.getDefaultState(), 64));
        return columns;
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

    private static final class TestableAdaptiveSmoothHandler extends AdaptiveSmoothHandler {
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
