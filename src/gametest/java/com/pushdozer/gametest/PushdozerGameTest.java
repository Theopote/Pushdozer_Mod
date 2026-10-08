package com.pushdozer.gametest;

import com.pushdozer.PushdozerMod;
import com.pushdozer.config.PushdozerConfig;
import com.pushdozer.items.handlers.ExcavationHandler;
import com.pushdozer.operations.BlockOperation;
import com.pushdozer.operations.UndoAction;
import com.pushdozer.operations.UndoRedoManager;
import com.pushdozer.services.UndoRedoService;
import net.fabricmc.fabric.api.gametest.v1.CustomTestMethodInvoker;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.block.Blocks;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.BlockUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.ChunkDataS2CPacket;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 服务端 Game Test：在真实 ServerWorld 中验证 Pushdozer 核心路径。
 * <p>
 * 本地运行：{@code ./gradlew runGametest} 或在 IDE 中选择 "Game Test" 运行配置。
 */
public class PushdozerGameTest implements CustomTestMethodInvoker {

    private static final BlockPos[] EXCAVATION_TARGETS = {
        new BlockPos(1, 1, 1),
        new BlockPos(2, 1, 1),
        new BlockPos(3, 1, 1),
    };

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

    @GameTest(maxTicks = 40)
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

    @GameTest
    public void undoSync_smallOperation_sendsBlockUpdates(TestContext context) {
        ServerWorld world = context.getWorld();
        ServerPlayerEntity player = PushdozerGameTestSupport.createMockServerPlayer(context);

        PacketRecordingUndoRedoManager manager = new PacketRecordingUndoRedoManager();

        // 10 blocks inside same chunk.
        BlockPos base = context.getAbsolutePos(new BlockPos(0, 1, 0));
        List<BlockPos> positions = new ArrayList<>();
        List<net.minecraft.block.BlockState> original = new ArrayList<>();
        List<net.minecraft.block.BlockState> updated = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            positions.add(base.add(i, 0, 0));
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

    @GameTest(maxTicks = 40)
    public void undoSync_largeOperation_sendsChunkDataTwice(TestContext context) {
        ServerWorld world = context.getWorld();
        ServerPlayerEntity player = PushdozerGameTestSupport.createMockServerPlayer(context);

        PacketRecordingUndoRedoManager manager = new PacketRecordingUndoRedoManager();

        // 4096 blocks: 16x16x16 cube within a single chunk (x,z 0-15).
        BlockPos base = context.getAbsolutePos(new BlockPos(0, 1, 0));
        List<BlockPos> positions = new ArrayList<>(4096);
        List<net.minecraft.block.BlockState> original = new ArrayList<>(4096);
        List<net.minecraft.block.BlockState> updated = new ArrayList<>(4096);
        for (int y = 0; y < 16; y++) {
            for (int z = 0; z < 16; z++) {
                for (int x = 0; x < 16; x++) {
                    positions.add(base.add(x, y, z));
                    original.add(Blocks.STONE.getDefaultState());
                    updated.add(Blocks.AIR.getDefaultState());
                }
            }
        }

        UndoAction action = new UndoAction(UndoAction.ActionType.BREAK, world.getRegistryKey(), positions, original, updated);
        manager.runUndoRedoAction(action, player, world);

        // Needs several ticks: 4096 blocks apply across ticks, post-process is scheduled, then chunk sync passes.
        context.runAtTick(context.getTick() + 25, () -> {
            context.assertTrue(manager.chunkDataPackets.get() >= 2, "Expected ChunkData packets (fast + delayed) for large undo");
            // Large sync path should avoid per-block updates.
            context.assertTrue(manager.blockUpdatePackets.get() == 0, "Expected no BlockUpdate packets for large undo");
            context.complete();
        });
    }

    @GameTest
    public void undoWrongDimension_doesNotModifyWorld(TestContext context) {
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
    }

    @GameTest(maxTicks = 40)
    public void undoReentry_blockedWhileLargeUndoPending(TestContext context) {
        ServerWorld world = context.getWorld();
        ServerPlayerEntity player = PushdozerGameTestSupport.createMockServerPlayer(context);
        PacketRecordingUndoRedoManager manager = new PacketRecordingUndoRedoManager();

        BlockPos base = context.getAbsolutePos(new BlockPos(0, 1, 0));
        List<BlockPos> positions = new ArrayList<>(600);
        List<net.minecraft.block.BlockState> original = new ArrayList<>(600);
        List<net.minecraft.block.BlockState> updated = new ArrayList<>(600);
        for (int i = 0; i < 600; i++) {
            positions.add(base.add(i % 16, 0, i / 16));
            original.add(Blocks.STONE.getDefaultState());
            updated.add(Blocks.AIR.getDefaultState());
        }

        UndoAction action = new UndoAction(UndoAction.ActionType.BREAK, world.getRegistryKey(), positions, original, updated);
        manager.pushUndoAction(player, action);
        manager.undoLastAction(player, world);
        manager.undoLastAction(player, world);

        context.runAtTick(context.getTick() + 10, () -> {
            context.assertTrue(manager.getUndoStackSize(player) == 0,
                "Large undo should consume the only stack entry once");
            context.complete();
        });
    }

    @GameTest
    public void postProcessThreshold_4095_updatesNeighbors(TestContext context) {
        ServerWorld world = context.getWorld();
        BlockPos base = context.getAbsolutePos(new BlockPos(0, 1, 0));
        List<BlockPos> positions = new ArrayList<>(4095);
        List<net.minecraft.block.BlockState> states = new ArrayList<>(4095);
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

    @GameTest(maxTicks = 40)
    public void postProcessThreshold_4096_completesWithoutError(TestContext context) {
        ServerWorld world = context.getWorld();
        BlockPos base = context.getAbsolutePos(new BlockPos(0, 1, 0));
        List<BlockPos> positions = new ArrayList<>(4096);
        List<net.minecraft.block.BlockState> states = new ArrayList<>(4096);
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
    public void invokeTestMethod(TestContext context, Method method) throws ReflectiveOperationException {
        context.setBlockState(new BlockPos(0, 0, 0), Blocks.STONE.getDefaultState());

        if ("batchTerrainWriteAppliesBlockStates".equals(method.getName())) {
            context.setBlockState(new BlockPos(1, 1, 1), Blocks.AIR.getDefaultState());
        } else if ("excavationUndoRestoresBrokenBlocks".equals(method.getName())) {
            for (BlockPos target : EXCAVATION_TARGETS) {
                context.setBlockState(target, Blocks.STONE.getDefaultState());
            }
        }

        method.invoke(this, context);
    }
}
