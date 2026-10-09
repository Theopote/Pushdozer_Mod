package com.pushdozer.gametest;

import com.pushdozer.PushdozerMod;
import com.pushdozer.config.PushdozerConfig;
import com.pushdozer.items.handlers.BatchPlantHandler;
import com.pushdozer.items.handlers.BoneMealHandler;
import com.pushdozer.items.handlers.planting.SimplePlantProcessor;
import com.pushdozer.items.handlers.planting.model.BatchPlantingResult;
import com.pushdozer.items.handlers.planting.model.PlantingPosition;
import com.pushdozer.operations.BlockOperation;
import com.pushdozer.operations.TerrainOperationScheduler;
import com.pushdozer.operations.UndoAction;
import com.pushdozer.operations.VegetationOperation;
import com.pushdozer.services.UndoRedoService;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.item.BoneMealItem;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.state.property.Properties;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;

public class PushdozerVegetationGameTest {

    private static final int VEGETATION_WAIT_LIMIT = 120;

    @GameTest(maxTicks = 200, setupTicks = 600)
    public void boneMealUndoRestoresInitialWheatAge(TestContext context) {
        runWhenSchedulerIdle(context, () -> {
            ServerWorld world = context.getWorld();
            ServerPlayerEntity player = PushdozerGameTestSupport.createMockServerPlayer(context);

            BlockPos ground = new BlockPos(2, 0, 2);
            BlockPos wheat = new BlockPos(2, 1, 2);
            context.setBlockState(ground, Blocks.FARMLAND);
            BlockState initial = Blocks.WHEAT.getDefaultState().with(Properties.AGE_7, 0);
            context.setBlockState(wheat, initial);

            BlockPos absoluteWheat = context.getAbsolutePos(wheat);
            Map<BlockPos, BlockOperation.BlockChange> changes = applyBoneMealGrowth(world, absoluteWheat, 2);

            context.assertTrue(!changes.isEmpty(), "Bone meal should change at least one block");

            UndoAction undoAction = new UndoAction(
                UndoAction.ActionType.BONE_MEAL,
                world.getRegistryKey(),
                changes.values().stream().map(BlockOperation.BlockChange::pos).toList(),
                changes.values().stream().map(BlockOperation.BlockChange::before).toList(),
                changes.values().stream().map(BlockOperation.BlockChange::after).toList()
            );
            PushdozerMod.pushUndoAction(player, undoAction);

            UndoRedoService.getInstance().undoLastAction(player, world);

            waitUntilBlockState(context, wheat,
                state -> state.isOf(Blocks.WHEAT) && state.get(Properties.AGE_7) == 0,
                "Undo should restore initial wheat age", VEGETATION_WAIT_LIMIT, context::complete);
        });
    }

    @GameTest(maxTicks = 200, setupTicks = 650)
    public void boneMealSaplingUndoRestoresAllBlocks(TestContext context) {
        runWhenSchedulerIdle(context, () -> {
            ServerWorld world = context.getWorld();
            ServerPlayerEntity player = PushdozerGameTestSupport.createMockServerPlayer(context);

            BlockPos ground = new BlockPos(2, 0, 2);
            BlockPos sapling = new BlockPos(2, 1, 2);
            context.setBlockState(ground, Blocks.GRASS_BLOCK);
            context.setBlockState(sapling, Blocks.OAK_SAPLING.getDefaultState());

            BlockPos absoluteSapling = context.getAbsolutePos(sapling);
            Map<BlockPos, BlockState> before = snapshotLockArea(world, absoluteSapling);
            Map<BlockPos, BlockOperation.BlockChange> changes = new LinkedHashMap<>();
            ItemStack boneMeal = new ItemStack(net.minecraft.item.Items.BONE_MEAL);

            for (int attempt = 0; attempt < 12; attempt++) {
                BoneMealItem.useOnFertilizable(boneMeal, world, absoluteSapling);
                BoneMealHandler.recordChanges(before.keySet(), before, world, changes);
                if (hasTreeBlocks(world, before.keySet(), before)) {
                    break;
                }
            }

            context.assertTrue(hasTreeBlocks(world, before.keySet(), before),
                "Bone meal should grow sapling into a tree");

            UndoAction undoAction = new UndoAction(
                UndoAction.ActionType.BONE_MEAL,
                world.getRegistryKey(),
                changes.values().stream().map(BlockOperation.BlockChange::pos).toList(),
                changes.values().stream().map(BlockOperation.BlockChange::before).toList(),
                changes.values().stream().map(BlockOperation.BlockChange::after).toList()
            );
            PushdozerMod.pushUndoAction(player, undoAction);
            UndoRedoService.getInstance().undoLastAction(player, world);

            waitUntil(context,
                () -> lockAreaMatchesSnapshot(world, before),
                "Undo should restore entire bone meal lock area including canopy blocks",
                VEGETATION_WAIT_LIMIT,
                context::complete);
        });
    }

    @GameTest(maxTicks = 240, setupTicks = 820)
    public void batchPlantHandlerUndoRestoresTallFlower(TestContext context) {
        runWhenSchedulerIdle(context, () -> {
            ServerWorld world = context.getWorld();
            ServerPlayerEntity player = PushdozerGameTestSupport.createMockServerPlayer(context);

            BlockPos ground = new BlockPos(2, 0, 2);
            BlockPos lower = new BlockPos(2, 1, 2);
            BlockPos upper = lower.up();
            context.setBlockState(ground, Blocks.GRASS_BLOCK);
            context.setBlockState(lower, Blocks.AIR);
            context.setBlockState(upper, Blocks.AIR);

            BlockPos absoluteGround = context.getAbsolutePos(ground);
            BlockPos absoluteLower = context.getAbsolutePos(lower);
            Map<BlockPos, BlockState> before = Map.of(
                absoluteGround, Blocks.GRASS_BLOCK.getDefaultState(),
                absoluteLower, Blocks.AIR.getDefaultState(),
                context.getAbsolutePos(upper), Blocks.AIR.getDefaultState()
            );

            PushdozerConfig config = PushdozerGameTestSupport.createBatchPlantFlowerConfig();
            world.getChunk(absoluteLower);
            new BatchPlantHandler().applyBatchPlantPositions(world, player, config,
                List.of(new PlantingPosition(absoluteLower, PushdozerConfig.PlantType.CUSTOM)));

            waitUntil(context,
                () -> TerrainOperationScheduler.getInstance().isIdle()
                    && isTallSunflowerPlanted(context, lower, upper),
                "Batch plant handler should place a tall sunflower through the full entry path",
                VEGETATION_WAIT_LIMIT,
                () -> {
                    UndoRedoService.getInstance().undoLastAction(player, world);
                    waitUntil(context,
                        () -> lockAreaMatchesSnapshot(world, before),
                        "Batch plant handler undo should restore all recorded blocks",
                        VEGETATION_WAIT_LIMIT,
                        context::complete);
                });
        });
    }

    @GameTest(maxTicks = 120, setupTicks = 700)
    public void batchPlantUndoRestoresAllRecordedBlocks(TestContext context) {
        runWhenSchedulerIdle(context, () -> {
            ServerWorld world = context.getWorld();
            ServerPlayerEntity player = PushdozerGameTestSupport.createMockServerPlayer(context);

            BlockPos ground = new BlockPos(2, 0, 2);
            BlockPos lower = new BlockPos(2, 1, 2);
            context.setBlockState(ground, Blocks.GRASS_BLOCK);
            context.setBlockState(lower, Blocks.AIR);
            context.setBlockState(lower.up(), Blocks.AIR);

            PushdozerConfig config = PushdozerGameTestSupport.createBatchPlantFlowerConfig();
            SimplePlantProcessor processor = new SimplePlantProcessor(config);
            BatchPlantingResult result = new BatchPlantingResult();
            processor.process(world,
                List.of(new PlantingPosition(
                    context.getAbsolutePos(lower), PushdozerConfig.PlantType.CUSTOM)),
                result);

            context.assertFalse(result.getTallPlantPairs().isEmpty(),
                "Batch plant should place a tall flower with both halves recorded");
            context.assertTrue(result.getTotalCount() >= 2,
                "Batch plant should record both tall flower halves");

            var operation = VegetationOperation.tryBegin(world, result.getAllPositions());
            context.assertTrue(operation.isPresent(), "Batch plant apply should acquire vegetation lock");
            try {
                BlockOperation.applyTerrainPhaseWithToken(
                    operation.get().token(),
                    result.getSimplePlantPositions(),
                    result.getSimplePlantNewStates(),
                    result.getTallPlantPairs(),
                    result::reconcileSimplePlants
                );
            } finally {
                operation.get().release();
            }

            UndoAction undoAction = new UndoAction(
                UndoAction.ActionType.BATCH_PLANT,
                world.getRegistryKey(),
                result.getAllPositions(),
                result.getAllOriginalStates(),
                result.getAllNewStates()
            );
            PushdozerMod.pushUndoAction(player, undoAction);
            UndoRedoService.getInstance().undoLastAction(player, world);

            BlockPos chosenUpper = lower.up();
            context.assertTrue(context.getBlockState(lower).isAir(), "Undo should restore lower air");
            context.assertTrue(context.getBlockState(chosenUpper).isAir(), "Undo should restore upper air");
            context.assertTrue(context.getBlockState(ground).isOf(Blocks.GRASS_BLOCK),
                "Undo should restore grass ground");
            context.complete();
        });
    }

    @GameTest(maxTicks = 80, setupTicks = 750)
    public void vegetationOperation_rejectsOverlappingLock(TestContext context) {
        runWhenSchedulerIdle(context, () -> {
            ServerWorld world = context.getWorld();
            BlockPos anchor = context.getAbsolutePos(new BlockPos(2, 1, 2));
            Set<BlockPos> lockArea = BoneMealHandler.collectLockPositions(List.of(anchor));

            var first = VegetationOperation.tryBegin(world, lockArea);
            context.assertTrue(first.isPresent(), "First vegetation operation should acquire lock");

            var second = VegetationOperation.tryBegin(world, lockArea);
            context.assertTrue(second.isEmpty(), "Overlapping vegetation operation must be rejected");

            first.get().release();
            context.complete();
        });
    }

    private static Map<BlockPos, BlockOperation.BlockChange> applyBoneMealGrowth(
            ServerWorld world, BlockPos target, int rounds) {
        Map<BlockPos, BlockState> before = snapshotLockArea(world, target);
        Map<BlockPos, BlockOperation.BlockChange> changes = new LinkedHashMap<>();
        ItemStack boneMeal = new ItemStack(net.minecraft.item.Items.BONE_MEAL);

        for (int round = 0; round < rounds; round++) {
            for (int attempt = 0; attempt < 3; attempt++) {
                BoneMealItem.useOnFertilizable(boneMeal, world, target);
            }
            BoneMealHandler.recordChanges(before.keySet(), before, world, changes);
        }
        return changes;
    }

    private static Map<BlockPos, BlockState> snapshotLockArea(ServerWorld world, BlockPos anchor) {
        Map<BlockPos, BlockState> snapshot = new HashMap<>();
        for (BlockPos pos : BoneMealHandler.collectLockPositions(List.of(anchor))) {
            snapshot.put(pos, world.getBlockState(pos));
        }
        return snapshot;
    }

    private static boolean isTallSunflowerPlanted(TestContext context, BlockPos lower, BlockPos upper) {
        return context.getBlockState(lower).isOf(Blocks.SUNFLOWER)
            && context.getBlockState(upper).isOf(Blocks.SUNFLOWER);
    }

    private static boolean lockAreaMatchesSnapshot(ServerWorld world, Map<BlockPos, BlockState> expected) {
        for (Map.Entry<BlockPos, BlockState> entry : expected.entrySet()) {
            if (!world.getBlockState(entry.getKey()).equals(entry.getValue())) {
                return false;
            }
        }
        return true;
    }

    private static boolean hasTreeBlocks(ServerWorld world, Set<BlockPos> area, Map<BlockPos, BlockState> before) {
        for (BlockPos pos : area) {
            BlockState current = world.getBlockState(pos);
            BlockState original = before.get(pos);
            if (original != null && !original.equals(current)
                && (current.isIn(net.minecraft.registry.tag.BlockTags.LOGS)
                || current.isIn(net.minecraft.registry.tag.BlockTags.LEAVES))) {
                return true;
            }
        }
        return false;
    }

    private static void runWhenSchedulerIdle(TestContext context, Runnable testBody) {
        runWhenSchedulerIdle(context, 0, testBody);
    }

    private static void runWhenSchedulerIdle(TestContext context, int waitedTicks, Runnable testBody) {
        int delay = waitedTicks == 0 ? 2 : 1;
        context.runAtTick(context.getTick() + delay, () -> {
            if (!TerrainOperationScheduler.getInstance().isIdle() && waitedTicks < VEGETATION_WAIT_LIMIT) {
                runWhenSchedulerIdle(context, waitedTicks + 1, testBody);
                return;
            }
            testBody.run();
        });
    }

    private static void waitUntilBlockState(TestContext context, BlockPos relativePos, Predicate<BlockState> expected,
                                              String message, int ticksRemaining, Runnable onSuccess) {
        if (expected.test(context.getBlockState(relativePos))) {
            onSuccess.run();
            return;
        }
        if (ticksRemaining <= 0) {
            context.assertTrue(expected.test(context.getBlockState(relativePos)), message);
            return;
        }
        context.runAtTick(context.getTick() + 1, () ->
            waitUntilBlockState(context, relativePos, expected, message, ticksRemaining - 1, onSuccess));
    }

    private static void waitUntil(TestContext context, BooleanSupplier condition, String message,
                                  int ticksRemaining, Runnable onSuccess) {
        if (condition.getAsBoolean()) {
            onSuccess.run();
            return;
        }
        if (ticksRemaining <= 0) {
            context.assertTrue(condition.getAsBoolean(), message);
            return;
        }
        context.runAtTick(context.getTick() + 1, () ->
            waitUntil(context, condition, message, ticksRemaining - 1, onSuccess));
    }
}
