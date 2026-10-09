package com.pushdozer.gametest;

import com.pushdozer.config.PushdozerConfig;
import com.pushdozer.items.handlers.shoreline.ShorelineBlockGenerator;
import com.pushdozer.items.handlers.shoreline.ShorelineEdgeFinder;
import com.pushdozer.items.handlers.shoreline.ShorelineTransitionPlanner;
import com.pushdozer.items.handlers.shoreline.model.ShorelineTransition;
import com.pushdozer.operations.BlockOperation;
import com.pushdozer.shapes.GeometryShape;
import com.pushdozer.shapes.GeometryShapeFactory;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.block.Blocks;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;

import java.util.Map;
import java.util.Set;

public class PushdozerShorelineGameTest {

    @GameTest(maxTicks = 40)
    public void shorelinePreservesSuspiciousSand(TestContext context) {
        ServerWorld world = context.getWorld();
        BlockPos water = new BlockPos(2, 1, 2);
        BlockPos archaeology = new BlockPos(3, 1, 2);
        context.setBlockState(water, Blocks.WATER.getDefaultState());
        context.setBlockState(archaeology, Blocks.SUSPICIOUS_SAND.getDefaultState());

        PushdozerConfig config = PushdozerGameTestSupport.createShorelineConfig();
        BlockPos center = context.getAbsolutePos(water);
        GeometryShape shape = GeometryShapeFactory.createShape(config.getGeometryType(), config, center);

        runShoreline(world, config, shape, center);

        context.assertTrue(context.getBlockState(archaeology).isOf(Blocks.SUSPICIOUS_SAND),
            "Archaeology blocks must not be replaced by shoreline material pass");
        context.complete();
    }

    @GameTest(maxTicks = 40)
    public void shorelineDoesNotModifyOutsideBrushBounds(TestContext context) {
        ServerWorld world = context.getWorld();
        BlockPos water = new BlockPos(2, 1, 2);
        BlockPos inside = new BlockPos(3, 1, 2);
        BlockPos outside = new BlockPos(8, 1, 2);
        context.setBlockState(water, Blocks.WATER.getDefaultState());
        context.setBlockState(inside, Blocks.GRASS_BLOCK);
        context.setBlockState(outside, Blocks.GRASS_BLOCK);

        PushdozerConfig config = PushdozerGameTestSupport.createShorelineConfig();
        BlockPos center = context.getAbsolutePos(water);
        GeometryShape shape = GeometryShapeFactory.createShape(config.getGeometryType(), config, center);

        runShoreline(world, config, shape, center);

        context.assertTrue(context.getBlockState(outside).isOf(Blocks.GRASS_BLOCK),
            "Land outside brush modify bounds must stay unchanged");
        context.complete();
    }

    private static void runShoreline(ServerWorld world, PushdozerConfig config, GeometryShape shape, BlockPos center) {
        ShorelineBlockGenerator blockGenerator = new ShorelineBlockGenerator(config);
        ShorelineEdgeFinder edgeFinder = new ShorelineEdgeFinder(config);
        ShorelineTransitionPlanner planner = new ShorelineTransitionPlanner(config, blockGenerator, edgeFinder);

        Set<BlockPos> waterBlocks = edgeFinder.collectWaterBlocks(world, shape);
        Map<BlockPos, ShorelineTransition> transitions = planner.computeShorelineTransitions(world, shape, center, waterBlocks);

        if (transitions.isEmpty()) {
            return;
        }

        java.util.List<BlockPos> positions = new java.util.ArrayList<>();
        java.util.List<net.minecraft.block.BlockState> states = new java.util.ArrayList<>();
        for (ShorelineTransition transition : transitions.values()) {
            if (!transition.isValid()) {
                continue;
            }
            positions.add(transition.pos);
            states.add(transition.newState);
        }

        if (positions.isEmpty()) {
            return;
        }

        BlockOperation.batchSetBlockStates(positions, states, world, BlockOperation.BULK_WRITE_FLAGS);
        BlockOperation.postProcessBlockChanges(world, positions, states);
    }
}
