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
import com.pushdozer.shapes.GeometryShape;
import com.pushdozer.util.ShapeUtil;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.noise.SimplexNoiseSampler;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

/**
 * 批量种植处理器
 * 根据生物群系自动生成合理的植被（树木、花草）
 *
 * 优化版本：
 * - 性能优化：减少getBlockState调用，使用批量操作
 * - 撤销逻辑修复：正确处理树生成边界，避免操作冲突
 * - 用户体验改进：单个撤销操作包含所有变更
 * - 数据驱动：使用植被注册表进行可扩展的植被选择
 * - API使用优化：使用Minecraft常量替代硬编码字符串
 */
public class BatchPlantHandler {

    private final Random random;
    private final SimplexNoiseSampler noiseSampler;

    public BatchPlantHandler() {
        this.random = Random.create();
        this.noiseSampler = new SimplexNoiseSampler(Random.create(1234L));
    }

    public void handleBatchPlant(PlayerEntity player, World world, PushdozerConfig config) {
        if (world.isClient() || !(world instanceof ServerWorld serverWorld)) return;

        BlockPos basePos = ShapeUtil.getTargetBlockPos(player, config);
        GeometryShape shape = ShapeUtil.createShape(player, config, basePos);
        if (shape == null) return;

        DensitySampler densitySampler = new DensitySampler(config, random, noiseSampler);
        PlantingPositionCollector positionCollector = new PlantingPositionCollector(config, densitySampler);
        SimplePlantProcessor simplePlantProcessor = new SimplePlantProcessor(config, random);
        TreeGenerator treeGenerator = new TreeGenerator(config, random);

        // 添加调试日志
        PushdozerMod.LOGGER.info("Batch planting started at position: {}, plant type: {}", basePos, config.getPlantType());

        // 收集所有需要种植的位置
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

        BatchPlantingResult result = new BatchPlantingResult();
        simplePlantProcessor.process(serverWorld, simplePlantPositions, result);

        Runnable pushUndo = () -> {
            if (!result.isEmpty()) {
                UndoAction undoAction = new UndoAction(
                    UndoAction.ActionType.BATCH_PLANT,
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
                pushUndo.run();
            } else {
                treeGenerator.scheduleTreesAcrossTicks(serverWorld, treePositions, 0, new HashSet<>(), result, pushUndo);
            }
        };

        if (result.hasSimplePlants()) {
            BlockOperation.applyTerrainChanges(
                serverWorld,
                result.getSimplePlantPositions(),
                result.getSimplePlantNewStates(),
                afterSimplePlants
            );
        } else {
            afterSimplePlants.run();
        }
    }
}
