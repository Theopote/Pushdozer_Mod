package com.pushdozer.gametest;



import com.pushdozer.PushdozerMod;

import com.pushdozer.config.PushdozerConfig;

import com.pushdozer.items.handlers.ExcavationHandler;

import com.pushdozer.items.handlers.PlacementHandler;

import com.pushdozer.items.handlers.SurfaceConvertHandler;

import com.pushdozer.shapes.GeometryShape;

import com.pushdozer.shapes.GeometryShapeFactory;

import com.pushdozer.operations.BlockOperation;

import com.pushdozer.operations.TerrainOperationScheduler;

import com.pushdozer.operations.UndoAction;

import com.pushdozer.operations.UndoRedoManager;

import com.pushdozer.services.UndoRedoService;

import net.fabricmc.fabric.api.gametest.v1.CustomTestMethodInvoker;

import net.fabricmc.fabric.api.gametest.v1.GameTest;

import net.minecraft.block.BlockState;

import net.minecraft.block.Blocks;

import net.minecraft.block.enums.DoubleBlockHalf;

import net.minecraft.state.property.Properties;

import net.minecraft.network.packet.Packet;

import net.minecraft.network.packet.s2c.play.BlockUpdateS2CPacket;

import net.minecraft.network.packet.s2c.play.ChunkDataS2CPacket;

import net.minecraft.server.network.ServerPlayerEntity;

import net.minecraft.server.world.ServerWorld;

import net.minecraft.test.TestContext;

import net.minecraft.util.math.BlockPos;

import net.minecraft.world.World;
import org.jspecify.annotations.NonNull;


import java.lang.reflect.Method;

import java.util.ArrayList;

import java.util.List;

import java.util.UUID;

import java.util.concurrent.atomic.AtomicInteger;

import java.util.function.Predicate;




/**

 * 服务端 Game Test：在真实 ServerWorld 中验证 Pushdozer 核心路径。

 * <p>

 * 本地运行：{@code ./gradlew runGametest} 或在 IDE 中选择 "Game Test" 运行配置。

 * <p>

 * <b>并行隔离：</b>Fabric {@link GameTest} 不支持 vanilla 的 batch 分组；同 batch 内所有测试并行执行，

 * 且 {@link CustomTestMethodInvoker} 默认共用同一 8×8 结构区域与全局 {@link TerrainOperationScheduler}。

 * 并行测试各自拥有独立 structure，但共享全局 {@link TerrainOperationScheduler}。不可在

 * {@link #invokeTestMethod} 中调用 {@link TerrainOperationScheduler#resetForTests()}（会清掉其他测试的锁）。

 * 会占用调度器的测试通过 {@link GameTest#setupTicks()} 错峰启动；调度器/undo 测试额外延迟 2 tick 再操作。

 */

public class PushdozerGameTest implements CustomTestMethodInvoker {



    private static final BlockPos[] EXCAVATION_TARGETS = {

        new BlockPos(1, 1, 1),

        new BlockPos(2, 1, 1),

        new BlockPos(3, 1, 1),

    };



    @GameTest

    public void brushRadiusClampMatchesSharedLimit(TestContext context) {

        PushdozerConfig config = new PushdozerConfig();

        config.setRadius(PushdozerConfig.MAX_BRUSH_RADIUS + 50);

        config.setSphereRadius(0);



        context.assertTrue(

            config.getRadius() == PushdozerConfig.MAX_BRUSH_RADIUS,

            "Expected radius clamp to MAX_BRUSH_RADIUS"

        );

        context.assertTrue(

            config.getSphereRadius() == PushdozerConfig.MIN_BRUSH_RADIUS,

            "Expected sphere radius clamp to MIN_BRUSH_RADIUS"

        );

        context.complete();

    }



    @GameTest

    public void placementDoesNotTreatSolidSandAsReplaceable(TestContext context) {

        BlockPos sandPos = new BlockPos(1, 1, 1);

        context.setBlockState(sandPos, Blocks.SAND);



        PlacementHandler handler = new PlacementHandler();

        BlockPos absolute = context.getAbsolutePos(sandPos);

        context.assertFalse(

            handler.isAllowedBlock(context.getBlockState(sandPos), context.getWorld(), absolute),

            "Solid sand must not be treated as replaceable for placement"

        );

        context.complete();

    }



    @GameTest

    public void batchTerrainWriteAppliesBlockStates(TestContext context) {

        BlockPos target = new BlockPos(1, 1, 1);

        context.assertTrue(context.getBlockState(target).isOf(Blocks.AIR), "Expected air before write");



        ServerWorld world = context.getWorld();

        BlockPos absoluteTarget = context.getAbsolutePos(target);

        BlockOperation.batchSetBlockStates(

            List.of(absoluteTarget),

            List.of(Blocks.STONE.getDefaultState()),

            world,

            BlockOperation.BULK_WRITE_FLAGS

        );

        BlockOperation.postProcessBlockChanges(

            world,

            List.of(absoluteTarget),

            List.of(Blocks.STONE.getDefaultState())

        );



        context.assertTrue(context.getBlockState(target).isOf(Blocks.STONE), "Expected stone after write");

        context.complete();

    }



    @GameTest(maxTicks = 40, setupTicks = 20)

    public void excavationUndoRestoresBrokenBlocks(TestContext context) {

        ServerWorld world = context.getWorld();

        ServerPlayerEntity player = PushdozerGameTestSupport.createMockServerPlayer(context);

        PushdozerConfig config = PushdozerGameTestSupport.createExcavationConfig(Blocks.STONE);



        List<BlockPos> absoluteTargets = new ArrayList<>(EXCAVATION_TARGETS.length);

        for (BlockPos relative : EXCAVATION_TARGETS) {

            context.assertTrue(context.getBlockState(relative).isOf(Blocks.STONE), "Expected stone before excavation");

            absoluteTargets.add(context.getAbsolutePos(relative));

        }



        new ExcavationHandler().excavateBlocksAt(player, world, config, absoluteTargets);



        context.runAtTick(context.getTick() + 1, () -> {

            for (BlockPos relative : EXCAVATION_TARGETS) {

                context.assertTrue(context.getBlockState(relative).isOf(Blocks.AIR), "Expected air after excavation");

            }

            context.assertTrue(PushdozerMod.getUndoStackSize(player) >= 1, "Expected undo entry after excavation");



            UndoRedoService.getInstance().undoLastAction(player, world);



            context.runAtTick(context.getTick() + 1, () -> {

                for (BlockPos relative : EXCAVATION_TARGETS) {

                    context.assertTrue(

                        context.getBlockState(relative).isOf(Blocks.STONE),

                        "Expected stone restored after undo at " + relative

                    );

                }

                context.complete();

            });

        });

    }



    private static class PacketRecordingUndoRedoManager extends UndoRedoManager {

        final AtomicInteger blockUpdatePackets = new AtomicInteger();

        final AtomicInteger chunkDataPackets = new AtomicInteger();



        void runUndoRedoAction(UndoAction action, ServerPlayerEntity player, ServerWorld world) {

            executeUndoRedoAction(action, player, world, true, ok -> {});

        }



        @Override

        protected void sendPacket(ServerPlayerEntity player, Packet<?> packet) {

            if (packet instanceof BlockUpdateS2CPacket) {

                blockUpdatePackets.incrementAndGet();

            } else if (packet instanceof ChunkDataS2CPacket) {

                chunkDataPackets.incrementAndGet();

            }

            super.sendPacket(player, packet);

        }

    }



    @GameTest(setupTicks = 70)

    public void undoSync_smallOperation_sendsBlockUpdates(TestContext context) {

        ServerWorld world = context.getWorld();

        ServerPlayerEntity player = PushdozerGameTestSupport.createMockServerPlayer(context);



        PacketRecordingUndoRedoManager manager = new PacketRecordingUndoRedoManager();



        BlockPos relativeBase = new BlockPos(0, 1, 0);

        List<BlockPos> positions = new ArrayList<>();

        List<BlockState> original = new ArrayList<>();

        List<BlockState> updated = new ArrayList<>();

        for (int i = 0; i < 10; i++) {

            BlockPos relative = relativeBase.add(i, 0, 0);

            context.setBlockState(relative, Blocks.AIR);

            positions.add(context.getAbsolutePos(relative));

            original.add(Blocks.STONE.getDefaultState());

            updated.add(Blocks.AIR.getDefaultState());

        }



        UndoAction action = new UndoAction(UndoAction.ActionType.BREAK, world.getRegistryKey(), positions, original, updated);

        manager.runUndoRedoAction(action, player, world);



        context.runAtTick(context.getTick() + 2, () -> {

            context.assertTrue(manager.blockUpdatePackets.get() > 0, "Expected BlockUpdate packets for small undo");

            context.assertTrue(manager.chunkDataPackets.get() == 0, "Expected no ChunkData packets for small undo");

            context.complete();

        });

    }



    @GameTest(maxTicks = 40, setupTicks = 90)

    public void undoSync_largeOperation_sendsBlockUpdates(TestContext context) {

        ServerWorld world = context.getWorld();

        ServerPlayerEntity player = PushdozerGameTestSupport.createMockServerPlayer(context);



        PacketRecordingUndoRedoManager manager = new PacketRecordingUndoRedoManager();



        BlockPos relativeBase = new BlockPos(0, 1, 0);

        List<BlockPos> positions = new ArrayList<>(4096);

        List<BlockState> original = new ArrayList<>(4096);

        List<BlockState> updated = new ArrayList<>(4096);

        for (int y = 0; y < 16; y++) {

            for (int z = 0; z < 16; z++) {

                for (int x = 0; x < 16; x++) {

                    BlockPos relative = relativeBase.add(x, y, z);

                    context.setBlockState(relative, Blocks.AIR);

                    positions.add(context.getAbsolutePos(relative));

                    original.add(Blocks.STONE.getDefaultState());

                    updated.add(Blocks.AIR.getDefaultState());

                }

            }

        }



        UndoAction action = new UndoAction(UndoAction.ActionType.BREAK, world.getRegistryKey(), positions, original, updated);

        manager.runUndoRedoAction(action, player, world);



        context.runAtTick(context.getTick() + 25, () -> {

            context.assertTrue(manager.blockUpdatePackets.get() > 0,

                "Expected BlockUpdate packets for large undo");

            context.assertTrue(manager.chunkDataPackets.get() == 0,

                "Expected no ChunkData packets for unified undo sync");

            context.complete();

        });

    }



    @GameTest(maxTicks = 20, setupTicks = 240)

    public void terrainScheduler_rejectsOverlappingChunkWrites(TestContext context) {

        context.runAtTick(context.getTick() + 2, () -> {

            ServerWorld world = context.getWorld();

            TerrainOperationScheduler scheduler = TerrainOperationScheduler.getInstance();



            UUID firstOperation = UUID.randomUUID();

            UUID secondOperation = UUID.randomUUID();

            BlockPos anchor = context.getAbsolutePos(new BlockPos(1, 1, 1));



            List<BlockPos> firstChunks = List.of(anchor, anchor.add(1, 0, 0));

            context.assertTrue(scheduler.tryAcquire(world, firstOperation, firstChunks),

                "First operation should acquire chunk lock");

            context.assertFalse(scheduler.tryAcquire(world, secondOperation, List.of(anchor.add(2, 0, 0))),

                "Overlapping chunk write should be rejected while lock is held");



            scheduler.release(world, firstOperation);

            context.assertTrue(scheduler.tryAcquire(world, secondOperation, List.of(anchor.add(1, 0, 0))),

                "Chunk lock should be available after release");

            scheduler.release(world, secondOperation);

            context.complete();

        });

    }



    @GameTest(setupTicks = 300)

    public void undoWrongDimension_doesNotModifyWorld(TestContext context) {

        context.runAtTick(context.getTick() + 2, () -> {

            ServerWorld world = context.getWorld();

            ServerPlayerEntity player = PushdozerGameTestSupport.createMockServerPlayer(context);

            BlockPos relative = new BlockPos(1, 1, 1);

            context.setBlockState(relative, Blocks.AIR);



            BlockPos absolute = context.getAbsolutePos(relative);

            UndoAction action = new UndoAction(

                UndoAction.ActionType.BREAK,

                World.NETHER,

                List.of(absolute),

                List.of(Blocks.STONE.getDefaultState()),

                List.of(Blocks.AIR.getDefaultState())

            );

            PushdozerMod.pushUndoAction(player, action);

            UndoRedoService.getInstance().undoLastAction(player, world);



            context.runAtTick(context.getTick() + 1, () -> {

                context.assertTrue(context.getBlockState(relative).isOf(Blocks.AIR),

                    "Undo from wrong dimension must not apply overworld changes");

                context.assertTrue(PushdozerMod.getUndoStackSize(player) == 0,

                    "Overworld undo stack must stay empty when history belongs to another dimension");

                context.complete();

            });

        });

    }



    @GameTest(maxTicks = 60, setupTicks = 360)

    public void undoReentry_blockedWhileLargeUndoPending(TestContext context) {

        ServerWorld world = context.getWorld();

        ServerPlayerEntity player = PushdozerGameTestSupport.createMockServerPlayer(context);

        PacketRecordingUndoRedoManager manager = new PacketRecordingUndoRedoManager();



        BlockPos relativeBase = new BlockPos(0, 1, 0);

        List<BlockPos> positions = new ArrayList<>(600);

        List<BlockState> original = new ArrayList<>(600);

        List<BlockState> updated = new ArrayList<>(600);

        int index = 0;

        outer:

        for (int y = 0; y < 16; y++) {

            for (int z = 0; z < 16; z++) {

                for (int x = 0; x < 16; x++) {

                    if (index >= 600) {

                        break outer;

                    }

                    BlockPos relative = relativeBase.add(x, y, z);

                    context.setBlockState(relative, Blocks.AIR);

                    positions.add(context.getAbsolutePos(relative));

                    original.add(Blocks.STONE.getDefaultState());

                    updated.add(Blocks.AIR.getDefaultState());

                    index++;

                }

            }

        }



        UndoAction action = new UndoAction(UndoAction.ActionType.BREAK, world.getRegistryKey(), positions, original, updated);

        manager.pushUndoAction(player, action);



        context.runAtTick(context.getTick() + 2, () -> {

            manager.undoLastAction(player, world);

            manager.undoLastAction(player, world);

            context.runAtTick(context.getTick() + 20, () -> {

                context.assertTrue(manager.getUndoStackSize(player) == 0,

                    "Large undo should consume the only stack entry once");

                context.complete();

            });

        });

    }



    @GameTest(maxTicks = 200, setupTicks = 450)

    public void surfaceConvertGrassToDirt(TestContext context) {

        runSurfaceConvertTest(context, () -> {

            ServerWorld world = context.getWorld();

            ServerPlayerEntity player = PushdozerGameTestSupport.createMockServerPlayer(context);

            BlockPos center = new BlockPos(2, 1, 2);

            context.setBlockState(new BlockPos(2, 0, 2), Blocks.STONE);

            context.setBlockState(center, Blocks.GRASS_BLOCK);

            PushdozerConfig config = PushdozerGameTestSupport.createSurfaceConvertConfig("minecraft:dirt");

            applySurfaceConvert(context, world, player, center, config, () -> assertEventually(context, center,

                state -> state.isOf(Blocks.DIRT), "Grass surface should become dirt", 30, () -> context.complete()));

        });

    }



    @GameTest(maxTicks = 200, setupTicks = 700)

    public void surfaceConvertProtectsRoof(TestContext context) {

        runSurfaceConvertTest(context, () -> {

            ServerWorld world = context.getWorld();

            ServerPlayerEntity player = PushdozerGameTestSupport.createMockServerPlayer(context);

            BlockPos ground = new BlockPos(2, 1, 2);

            BlockPos roof = new BlockPos(2, 2, 2);

            context.setBlockState(new BlockPos(2, 0, 2), Blocks.STONE);

            context.setBlockState(ground, Blocks.GRASS_BLOCK);

            context.setBlockState(roof, Blocks.OAK_PLANKS);

            PushdozerConfig config = PushdozerGameTestSupport.createSurfaceConvertConfig("minecraft:sand");

            applySurfaceConvert(context, world, player, ground, config, () -> assertEventually(context, ground,

                state -> state.isOf(Blocks.SAND), "Natural ground below roof should convert", 30, () -> {

                    context.assertTrue(context.getBlockState(roof).isOf(Blocks.OAK_PLANKS),

                        "Roof planks must stay intact");

                    context.complete();

                }));

        });

    }



    @GameTest(maxTicks = 200, setupTicks = 950)

    public void surfaceConvertRetainsShortGrassOnDirt(TestContext context) {

        runSurfaceConvertTest(context, () -> {

        ServerWorld world = context.getWorld();

        ServerPlayerEntity player = PushdozerGameTestSupport.createMockServerPlayer(context);

        BlockPos ground = new BlockPos(2, 1, 2);

        BlockPos plant = new BlockPos(2, 2, 2);

        context.setBlockState(new BlockPos(2, 0, 2), Blocks.STONE);

        context.setBlockState(ground, Blocks.GRASS_BLOCK);

        context.setBlockState(plant, Blocks.SHORT_GRASS);

        PushdozerConfig config = PushdozerGameTestSupport.createSurfaceConvertConfig("minecraft:dirt");

        applySurfaceConvert(context, world, player, ground, config, () -> assertEventually(context, ground,

            state -> state.isOf(Blocks.DIRT), "Surface should convert to dirt", 30, () -> {

                context.assertTrue(context.getBlockState(plant).isOf(Blocks.SHORT_GRASS),

                    "Short grass should survive on dirt");

                context.complete();

            }));

        });

    }



    @GameTest(maxTicks = 200, setupTicks = 1200)

    public void surfaceConvertRemovesShortGrassOnStone(TestContext context) {

        runSurfaceConvertTest(context, () -> {

        ServerWorld world = context.getWorld();

        ServerPlayerEntity player = PushdozerGameTestSupport.createMockServerPlayer(context);

        BlockPos ground = new BlockPos(2, 1, 2);

        BlockPos plant = new BlockPos(2, 2, 2);

        context.setBlockState(new BlockPos(2, 0, 2), Blocks.STONE);

        context.setBlockState(ground, Blocks.GRASS_BLOCK);

        context.setBlockState(plant, Blocks.SHORT_GRASS);

        PushdozerConfig config = PushdozerGameTestSupport.createSurfaceConvertConfig("minecraft:stone");

        applySurfaceConvert(context, world, player, ground, config, () -> assertEventually(context, ground,

            state -> state.isOf(Blocks.STONE), "Surface should convert to stone", 30, () -> {

                context.assertFalse(context.getBlockState(ground).isOf(Blocks.GRASS_BLOCK),

                    "Surface should no longer be grass after convert");

                context.assertTrue(context.getBlockState(plant).isAir(),

                    "Short grass should be removed when base becomes stone");

                context.complete();

            }));

        });

    }



    @GameTest(maxTicks = 200, setupTicks = 1450)

    public void surfaceConvertUndoRestoresBlocks(TestContext context) {

        runSurfaceConvertTest(context, () -> {

        ServerWorld world = context.getWorld();

        ServerPlayerEntity player = PushdozerGameTestSupport.createMockServerPlayer(context);

        BlockPos ground = new BlockPos(2, 1, 2);

        BlockPos plant = new BlockPos(2, 2, 2);

        context.setBlockState(new BlockPos(2, 0, 2), Blocks.STONE);

        context.setBlockState(ground, Blocks.GRASS_BLOCK);

        context.setBlockState(plant, Blocks.SHORT_GRASS);

        PushdozerConfig config = PushdozerGameTestSupport.createSurfaceConvertConfig("minecraft:stone");

        applySurfaceConvert(context, world, player, ground, config, () -> assertEventually(context, plant,

            BlockState::isAir, "Plant should be removed after convert", 30, () -> {

                UndoRedoService.getInstance().undoLastAction(player, world);

                assertEventually(context, ground, state -> state.isOf(Blocks.GRASS_BLOCK),

                    "Undo should restore grass block", 30, () -> {

                        context.assertTrue(context.getBlockState(plant).isOf(Blocks.SHORT_GRASS),

                            "Undo should restore removed plant");

                        context.complete();

                    });

            }));

        });

    }



    @GameTest(maxTicks = 200, setupTicks = 1700)

    public void surfaceConvertSkipsInvalidTargetConfig(TestContext context) {

        runSurfaceConvertTest(context, () -> {

        ServerWorld world = context.getWorld();

        ServerPlayerEntity player = PushdozerGameTestSupport.createMockServerPlayer(context);

        BlockPos ground = new BlockPos(2, 1, 2);

        context.setBlockState(ground, Blocks.GRASS_BLOCK);

        PushdozerConfig config = PushdozerGameTestSupport.createSurfaceConvertConfig("minecraft:oak_door");

        BlockPos absoluteGround = context.getAbsolutePos(ground);

        GeometryShape shape = GeometryShapeFactory.createShape(config.getGeometryType(), config, absoluteGround);

        context.assertFalse(

            new SurfaceConvertHandler().applySurfaceConvert(world, player, shape, absoluteGround, config),

            "Invalid target config must be rejected before terrain apply");

        context.assertTrue(context.getBlockState(ground).isOf(Blocks.GRASS_BLOCK),

            "Invalid target config must not modify terrain");

        context.complete();

        });

    }



    private static final int SURFACE_CONVERT_IDLE_WAIT_LIMIT = 40;



    private static void runSurfaceConvertTest(TestContext context, Runnable testBody) {

        runSurfaceConvertTest(context, testBody, 0);

    }



    private static void runSurfaceConvertTest(TestContext context, Runnable testBody, int waitedTicks) {

        int delay = waitedTicks == 0 ? 2 : 1;

        context.runAtTick(context.getTick() + delay, () -> {

            if (!TerrainOperationScheduler.getInstance().isIdle() && waitedTicks < SURFACE_CONVERT_IDLE_WAIT_LIMIT) {

                runSurfaceConvertTest(context, testBody, waitedTicks + 1);

                return;

            }

            testBody.run();

        });

    }



    private static void applySurfaceConvert(TestContext context, ServerWorld world, ServerPlayerEntity player,

                                            BlockPos relativeCenter, PushdozerConfig config, Runnable onApplied) {

        applySurfaceConvertWithRetry(context, world, player, relativeCenter, config, 0, onApplied);

    }



    private static void applySurfaceConvertWithRetry(TestContext context, ServerWorld world,

                                                     ServerPlayerEntity player, BlockPos relativeCenter,

                                                     PushdozerConfig config, int attempts, Runnable onApplied) {

        BlockPos absoluteCenter = context.getAbsolutePos(relativeCenter);

        world.getChunk(absoluteCenter);

        GeometryShape shape = GeometryShapeFactory.createShape(config.getGeometryType(), config, absoluteCenter);

        boolean scheduled = new SurfaceConvertHandler().applySurfaceConvert(world, player, shape, absoluteCenter, config);

        if (scheduled) {

            onApplied.run();

            return;

        }

        if (attempts >= 40) {

            context.assertTrue(false, "Surface convert could not apply terrain changes due to chunk lock contention");

            return;

        }

        retrySurfaceConvertApply(context, world, player, relativeCenter, config, attempts, onApplied, 0);

    }



    private static void retrySurfaceConvertApply(TestContext context, ServerWorld world,

                                                 ServerPlayerEntity player, BlockPos relativeCenter,

                                                 PushdozerConfig config, int attempts, Runnable onApplied,

                                                 int idleWaitTicks) {

        context.runAtTick(context.getTick() + 1, () -> {

            if (!TerrainOperationScheduler.getInstance().isIdle()

                && idleWaitTicks < SURFACE_CONVERT_IDLE_WAIT_LIMIT) {

                retrySurfaceConvertApply(context, world, player, relativeCenter, config, attempts, onApplied,

                    idleWaitTicks + 1);

                return;

            }

            applySurfaceConvertWithRetry(context, world, player, relativeCenter, config, attempts + 1, onApplied);

        });

    }



    private static void assertEventually(TestContext context, BlockPos pos, Predicate<BlockState> predicate,

                                         String message, int ticksRemaining, Runnable onSuccess) {

        context.runAtTick(context.getTick() + 1, () -> {

            if (predicate.test(context.getBlockState(pos))) {

                onSuccess.run();

                return;

            }

            if (ticksRemaining <= 0) {

                context.assertTrue(predicate.test(context.getBlockState(pos)), message);

                return;

            }

            assertEventually(context, pos, predicate, message, ticksRemaining - 1, onSuccess);

        });

    }



    @GameTest(setupTicks = 1950)

    public void postProcessThreshold_4095_updatesNeighbors(TestContext context) {

        ServerWorld world = context.getWorld();

        BlockPos base = context.getAbsolutePos(new BlockPos(0, 1, 0));

        List<BlockPos> positions = new ArrayList<>(4095);

        List<BlockState> states = new ArrayList<>(4095);

        for (int i = 0; i < 4095; i++) {

            positions.add(base.add(i % 16, 0, i / 16));

            states.add(Blocks.STONE.getDefaultState());

        }



        BlockOperation.batchSetBlockStates(positions, states, world, BlockOperation.BULK_WRITE_FLAGS);

        BlockOperation.postProcessBlockChanges(world, positions, states);



        context.assertTrue(context.getBlockState(new BlockPos(0, 1, 0)).isOf(Blocks.STONE),

            "4095-block post-process should complete without error");

        context.complete();

    }



    @GameTest(setupTicks = 1990)

    public void tallPlantApplyIsAtomicWhenUpperBlocked(TestContext context) {

        BlockPos lower = new BlockPos(1, 1, 1);

        BlockPos upper = lower.up();

        context.setBlockState(lower, Blocks.AIR);

        context.setBlockState(upper, Blocks.STONE);



        ServerWorld world = context.getWorld();

        BlockPos absoluteLower = context.getAbsolutePos(lower);

        BlockPos absoluteUpper = context.getAbsolutePos(upper);

        BlockState lowerPlant = Blocks.SUNFLOWER.getDefaultState().with(Properties.DOUBLE_BLOCK_HALF, DoubleBlockHalf.LOWER);

        BlockState upperPlant = Blocks.SUNFLOWER.getDefaultState().with(Properties.DOUBLE_BLOCK_HALF, DoubleBlockHalf.UPPER);



        BlockOperation.batchSetBlockStates(

            List.of(absoluteLower, absoluteUpper),

            List.of(lowerPlant, upperPlant),

            world,

            BlockOperation.BULK_WRITE_FLAGS,

            List.of(new int[]{0, 1}),

            applied -> {}

        );



        context.assertTrue(context.getBlockState(lower).isAir(), "Lower half must roll back when upper is blocked");

        context.assertTrue(context.getBlockState(upper).isOf(Blocks.STONE), "Blocking stone must remain");

        context.complete();

    }



    @GameTest(setupTicks = 2010)

    public void tallPlantApplyPlacesBothHalvesWhenSpaceIsClear(TestContext context) {

        BlockPos ground = new BlockPos(2, 0, 1);

        BlockPos lower = new BlockPos(2, 1, 1);

        BlockPos upper = lower.up();

        context.setBlockState(ground, Blocks.GRASS_BLOCK);

        context.setBlockState(lower, Blocks.AIR);

        context.setBlockState(upper, Blocks.AIR);



        ServerWorld world = context.getWorld();

        BlockPos absoluteLower = context.getAbsolutePos(lower);

        BlockPos absoluteUpper = context.getAbsolutePos(upper);

        BlockState lowerPlant = Blocks.SUNFLOWER.getDefaultState().with(Properties.DOUBLE_BLOCK_HALF, DoubleBlockHalf.LOWER);

        BlockState upperPlant = Blocks.SUNFLOWER.getDefaultState().with(Properties.DOUBLE_BLOCK_HALF, DoubleBlockHalf.UPPER);



        BlockOperation.batchSetBlockStates(

            List.of(absoluteLower, absoluteUpper),

            List.of(lowerPlant, upperPlant),

            world,

            BlockOperation.BULK_WRITE_FLAGS,

            List.of(new int[]{0, 1}),

            applied -> {}

        );



        context.assertTrue(context.getBlockState(lower).isOf(Blocks.SUNFLOWER), "Lower sunflower half expected");

        context.assertTrue(context.getBlockState(upper).isOf(Blocks.SUNFLOWER), "Upper sunflower half expected");

        context.complete();

    }



    @GameTest(maxTicks = 40, setupTicks = 2050)

    public void postProcessThreshold_4096_completesWithoutError(TestContext context) {

        ServerWorld world = context.getWorld();

        BlockPos base = context.getAbsolutePos(new BlockPos(0, 1, 0));

        List<BlockPos> positions = new ArrayList<>(4096);

        List<BlockState> states = new ArrayList<>(4096);

        for (int i = 0; i < 4096; i++) {

            positions.add(base.add(i % 16, i / 16 % 16, i / 256));

            states.add(Blocks.STONE.getDefaultState());

        }



        BlockOperation.batchSetBlockStates(positions, states, world, BlockOperation.BULK_WRITE_FLAGS);

        BlockOperation.postProcessBlockChanges(world, positions, states);



        context.runAtTick(context.getTick() + 5, () -> {

            context.assertTrue(context.getBlockState(new BlockPos(0, 1, 0)).isOf(Blocks.STONE),

                "4096-block post-process should complete across scheduled ticks");

            context.complete();

        });

    }



    @Override

    public void invokeTestMethod(@NonNull TestContext context, @NonNull Method method) throws ReflectiveOperationException {

        prepareSharedTestArea(context, method);

        method.invoke(this, context);

    }



    private static void prepareSharedTestArea(TestContext context, Method method) {

        context.setBlockState(new BlockPos(0, 0, 0), Blocks.STONE.getDefaultState());


        switch (method.getName()) {
            case "batchTerrainWriteAppliesBlockStates" ->
                    context.setBlockState(new BlockPos(1, 1, 1), Blocks.AIR.getDefaultState());
            case "placementDoesNotTreatSolidSandAsReplaceable" ->
                    context.setBlockState(new BlockPos(1, 1, 1), Blocks.SAND.getDefaultState());
            case "excavationUndoRestoresBrokenBlocks" -> {

                for (BlockPos target : EXCAVATION_TARGETS) {

                    context.setBlockState(target, Blocks.STONE.getDefaultState());

                }
            }
            case "tallPlantApplyIsAtomicWhenUpperBlocked" -> {

                context.setBlockState(new BlockPos(1, 1, 1), Blocks.AIR);

                context.setBlockState(new BlockPos(1, 2, 1), Blocks.STONE);
            }
            case "tallPlantApplyPlacesBothHalvesWhenSpaceIsClear" -> {

                context.setBlockState(new BlockPos(2, 0, 1), Blocks.GRASS_BLOCK);

                context.setBlockState(new BlockPos(2, 1, 1), Blocks.AIR);

                context.setBlockState(new BlockPos(2, 2, 1), Blocks.AIR);
            }
        }

    }

}


