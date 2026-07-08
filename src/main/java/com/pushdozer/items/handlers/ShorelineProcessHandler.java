package com.pushdozer.items.handlers;

import com.pushdozer.PushdozerMod;
import com.pushdozer.config.PushdozerConfig;
import com.pushdozer.items.handlers.shoreline.ShorelineBlockGenerator;
import com.pushdozer.items.handlers.shoreline.ShorelineEdgeFinder;
import com.pushdozer.items.handlers.shoreline.ShorelineTransitionPlanner;
import com.pushdozer.items.handlers.shoreline.ShorelineVegetationPlanner;
import com.pushdozer.items.handlers.shoreline.model.ShorelineResult;
import com.pushdozer.items.handlers.shoreline.model.ShorelineTransition;
import com.pushdozer.items.handlers.shoreline.model.VegetationPlacement;
import com.pushdozer.operations.BlockOperation;
import com.pushdozer.operations.UndoAction;
import com.pushdozer.shapes.GeometryShape;
import com.pushdozer.util.ShapeUtil;
import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 水岸处理处理器：根据生物群系自动生成沙滩或堤岸过渡。
 * 支持距离渐变、材质混合和植物种植，创造更自然的水岸效果。
 * <p>
 * 分层架构：边缘检测 → 过渡计算 → 应用变化 → 植物装饰。
 * 使用 BFS 从水体边缘向内陆扩展，按距离混合方块材质。
 * 植物种植与地形变更分离，支持撤销。
 */
public class ShorelineProcessHandler {
    private static final int MAX_SHORELINE_WIDTH = 20;
    private static final int DEFAULT_SHORELINE_WIDTH = 5;
    private static final float DEFAULT_VEGETATION_DENSITY = 0.3f;

    private PushdozerConfig config;

    public ShorelineProcessHandler() {
    }

    private void validateConfig(PushdozerConfig config) {
        if (config.getShorelineWidth() < 1) {
            PushdozerMod.LOGGER.warn("Invalid shoreline width {}, resetting to default ({})", config.getShorelineWidth(), DEFAULT_SHORELINE_WIDTH);
            config.setShorelineWidth(DEFAULT_SHORELINE_WIDTH);
        }

        if (config.getVegetationDensity() < 0.0f || config.getVegetationDensity() > 1.0f) {
            PushdozerMod.LOGGER.warn("Invalid vegetation density {}, resetting to default ({})", config.getVegetationDensity(), DEFAULT_VEGETATION_DENSITY);
            config.setVegetationDensity(DEFAULT_VEGETATION_DENSITY);
        }

        if (config.getShorelineWidth() > MAX_SHORELINE_WIDTH) {
            PushdozerMod.LOGGER.warn("Shoreline width {} is too large, capping at {}", config.getShorelineWidth(), MAX_SHORELINE_WIDTH);
            config.setShorelineWidth(MAX_SHORELINE_WIDTH);
        }
    }

    private boolean validateParameters(PlayerEntity player, World world) {
        if (player == null) {
            PushdozerMod.LOGGER.warn("Invalid parameters for shoreline processing: player is null");
            return false;
        }
        return true;
    }

    private GeometryShape getProcessingShape(PlayerEntity player) {
        BlockPos basePos = ShapeUtil.getTargetBlockPos(player, config);
        GeometryShape shape = ShapeUtil.createShape(player, config, basePos);

        if (shape == null) {
            PushdozerMod.LOGGER.debug("No valid shape created for shoreline processing");
            return null;
        }
        return shape;
    }

    public void handleShorelineProcess(PlayerEntity player, World world, PushdozerConfig config) {
        this.config = config;
        validateConfig(config);

        if (world.isClient()) {
            return;
        }

        if (!validateParameters(player, world)) {
            return;
        }

        GeometryShape shape = getProcessingShape(player);
        if (shape == null) {
            return;
        }

        ShorelineBlockGenerator blockGenerator = new ShorelineBlockGenerator(config);
        ShorelineEdgeFinder edgeFinder = new ShorelineEdgeFinder(config);
        ShorelineTransitionPlanner transitionPlanner = new ShorelineTransitionPlanner(config, blockGenerator, edgeFinder);
        ShorelineVegetationPlanner vegetationPlanner = new ShorelineVegetationPlanner(config, edgeFinder, transitionPlanner);

        Set<BlockPos> shorelineEdges = edgeFinder.findEdges(world, shape);
        if (shorelineEdges.isEmpty()) {
            return;
        }

        Map<BlockPos, ShorelineTransition> transitions = transitionPlanner.computeShorelineTransitions(world, shorelineEdges);
        ShorelineResult result = transitionPlanner.collectApplyableTransitions(world, player, transitions, vegetationPlanner);
        if (result.affectedPositions.isEmpty()) {
            return;
        }

        if (!(world instanceof ServerWorld serverWorld)) {
            return;
        }

        BlockOperation.applyTerrainChanges(serverWorld, result.affectedPositions, result.newStates, () -> {
            List<VegetationPlacement> vegetationPlacements =
                vegetationPlanner.collectVegetationPositions(world, result.vegetationPositions);
            List<BlockPos> vegetationPositions = new ArrayList<>();
            List<BlockState> vegetationOriginal = new ArrayList<>();
            List<BlockState> vegetationNew = new ArrayList<>();
            int vegetationCount = vegetationPlanner.planVegetation(
                world, vegetationPlacements, vegetationPositions, vegetationOriginal, vegetationNew, player
            );

            Runnable finish = () -> {
                result.affectedPositions.addAll(vegetationPositions);
                result.originalStates.addAll(vegetationOriginal);
                result.newStates.addAll(vegetationNew);
                createUndoActionAndNotifyPlayer(player, result, vegetationCount);
            };

            if (vegetationPositions.isEmpty()) {
                finish.run();
            } else {
                BlockOperation.applyTerrainChanges(serverWorld, vegetationPositions, vegetationNew, finish);
            }
        });
    }

    private void createUndoActionAndNotifyPlayer(PlayerEntity player, ShorelineResult result, int vegetationCount) {
        if (!result.affectedPositions.isEmpty()) {
            UndoAction undoAction = new UndoAction(
                UndoAction.ActionType.PLACE,
                result.affectedPositions,
                result.originalStates,
                result.newStates
            );
            PushdozerMod.pushUndoAction(player, undoAction);
        }

        if (result.processedCount > 0) {
            PushdozerMod.LOGGER.debug("Shoreline processing completed: {} blocks processed, {} plants planted",
                result.processedCount, vegetationCount);
        } else {
            PushdozerMod.LOGGER.debug("Shoreline processing completed with no changes");
        }
    }
}
