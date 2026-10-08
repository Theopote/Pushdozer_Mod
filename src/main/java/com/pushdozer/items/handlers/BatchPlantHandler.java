package com.pushdozer.items.handlers;

import com.pushdozer.PushdozerMod;
import com.pushdozer.config.PushdozerConfig;
import com.pushdozer.items.handlers.planting.DensitySampler;
import com.pushdozer.items.handlers.planting.PlantingPositionCollector;
import com.pushdozer.items.handlers.planting.SimplePlantProcessor;
import com.pushdozer.items.handlers.planting.TreeGenerator;
import com.pushdozer.items.handlers.planting.model.BatchPlantingResult;
import com.pushdozer.items.handlers.planting.model.PlantingPosition;
import com.pushdozer.operations.BlockOperation;
import com.pushdozer.operations.UndoAction;
import com.pushdozer.operations.VegetationOperation;
import com.pushdozer.shapes.GeometryShape;
import com.pushdozer.util.ShapeUtil;
import com.pushdozer.util.TerrainOperationFeedback;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.noise.SimplexNoiseSampler;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

/**
 * 批量种植处理器
 * 根据生物群系自动生成合理的植被（树木、花草）
 */
public class BatchPlantHandler implements TerrainToolHandler {

    private final SimplexNoiseSampler noiseSampler;

    public BatchPlantHandler() {
        this.noiseSampler = new SimplexNoiseSampler(net.minecraft.util.math.random.Random.create(1234L));
    }

    public void handleBatchPlant(PlayerEntity player, World world, PushdozerConfig config) {
        if (world.isClient() || !(world instanceof ServerWorld serverWorld)) return;

        BlockPos basePos = ShapeUtil.getTargetBlockPos(player, config);
        GeometryShape shape = ShapeUtil.createShape(player, config, basePos);
        if (shape == null) return;

        long worldSeed = serverWorld.getSeed();
        DensitySampler densitySampler = new DensitySampler(config, worldSeed, noiseSampler);
        PlantingPositionCollector positionCollector = new PlantingPositionCollector(config, densitySampler);
        SimplePlantProcessor simplePlantProcessor = new SimplePlantProcessor(config);
        TreeGenerator treeGenerator = new TreeGenerator(config, worldSeed);

        PushdozerMod.LOGGER.info("Batch planting started at position: {}, plant type: {}", basePos, config.getPlantType());

        List<PlantingPosition> plantingPositions = positionCollector.collect(world, shape);
        if (plantingPositions.isEmpty()) {
            PushdozerMod.LOGGER.info("No planting positions found");
            return;
        }

        PushdozerMod.LOGGER.info("Found {} planting positions", plantingPositions.size());

        List<PlantingPosition> treePositions = new ArrayList<>();
        List<PlantingPosition> simplePlantPositions = new ArrayList<>();
        for (PlantingPosition pos : plantingPositions) {
            if (pos.plantType() == PushdozerConfig.PlantType.TREES) {
                treePositions.add(pos);
            } else {
                simplePlantPositions.add(pos);
            }
        }

        List<BlockPos> lockPositions = new ArrayList<>();
        lockPositions.addAll(collectSimplePlantLockPositions(simplePlantPositions));
        lockPositions.addAll(TreeGenerator.collectLockPositions(treePositions, config));

        var operation = VegetationOperation.tryBegin(serverWorld, lockPositions);
        if (operation.isEmpty()) {
            TerrainOperationFeedback.notifyRegionBusy(player);
            return;
        }

        BatchPlantingResult result = new BatchPlantingResult();
        simplePlantProcessor.process(serverWorld, simplePlantPositions, result);

        Runnable pushUndo = () -> {
            if (!result.isEmpty()) {
                UndoAction undoAction = new UndoAction(
                    UndoAction.ActionType.BATCH_PLANT,
                    serverWorld.getRegistryKey(),
                    result.getAllPositions(),
                    result.getAllOriginalStates(),
                    result.getAllNewStates()
                );
                PushdozerMod.pushUndoAction(player, undoAction);
            }

            if (result.getTotalCount() > 0) {
                PushdozerMod.LOGGER.info("Batch planting completed: {} plants, {} trees, {} total blocks changed",
                    result.getSimplePlantCount(), result.getTreeCount(), result.getTotalCount());
            }
        };

        Runnable afterSimplePlants = () -> {
            if (treePositions.isEmpty()) {
                try {
                    pushUndo.run();
                } finally {
                    operation.get().release();
                }
            } else {
                treeGenerator.scheduleTreesAcrossTicks(
                    serverWorld, treePositions, 0, new HashSet<>(), result, operation.get(), pushUndo
                );
            }
        };

        if (result.hasSimplePlants()) {
            BlockOperation.applyTerrainPhaseWithToken(
                operation.get().token(),
                result.getSimplePlantPositions(),
                result.getSimplePlantNewStates(),
                result.getTallPlantPairs(),
                applied -> {
                    result.reconcileSimplePlants(applied);
                    afterSimplePlants.run();
                }
            );
        } else {
            afterSimplePlants.run();
        }
    }

    private static List<BlockPos> collectSimplePlantLockPositions(List<PlantingPosition> simplePlantPositions) {
        List<BlockPos> lockPositions = new ArrayList<>(simplePlantPositions.size() * 2);
        for (PlantingPosition plantingPosition : simplePlantPositions) {
            BlockPos pos = plantingPosition.position();
            lockPositions.add(pos);
            lockPositions.add(pos.up());
        }
        return lockPositions;
    }
}
