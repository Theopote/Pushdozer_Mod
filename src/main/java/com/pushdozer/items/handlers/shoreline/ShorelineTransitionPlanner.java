package com.pushdozer.items.handlers.shoreline;

import com.pushdozer.PushdozerMod;
import com.pushdozer.config.PushdozerConfig;
import com.pushdozer.items.handlers.shoreline.model.ShorelineResult;
import com.pushdozer.items.handlers.shoreline.model.ShorelineTransition;
import com.pushdozer.shapes.GeometryShape;
import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.biome.Biome;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ShorelineTransitionPlanner {
    private static final int VERTICAL_SCAN_PADDING = 2;

    private final PushdozerConfig config;
    private final ShorelineBlockGenerator blockGenerator;
    private final ShorelineEdgeFinder edgeFinder;
    private Integer playerHeightSnapshot;

    public ShorelineTransitionPlanner(PushdozerConfig config, ShorelineBlockGenerator blockGenerator, ShorelineEdgeFinder edgeFinder) {
        this.config = config;
        this.blockGenerator = blockGenerator;
        this.edgeFinder = edgeFinder;
    }

    public void beginOperation(PlayerEntity player) {
        playerHeightSnapshot = config.getHeightMode() == PushdozerConfig.HeightMode.FOLLOW_PLAYER
            ? player.getBlockY()
            : null;
    }

    public Map<BlockPos, ShorelineTransition> computeShorelineTransitions(
            World world,
            GeometryShape shape,
            BlockPos brushCenter,
            Set<BlockPos> waterBlocks) {
        int width = config.getShorelineWidth();
        Set<BlockPos> allowedColumns = ShorelineModifyBounds.allowedModifyColumns(shape, width);
        Map<BlockPos, Integer> columnDistances = ShorelineHorizontalDistance.compute(waterBlocks, width);

        int minY = shape.getMinY(brushCenter) - VERTICAL_SCAN_PADDING;
        int maxY = shape.getMaxY(brushCenter) + VERTICAL_SCAN_PADDING;

        Map<BlockPos, ShorelineTransition> transitions = new HashMap<>();
        Map<BlockPos, Biome> biomeCache = new HashMap<>();

        for (Map.Entry<BlockPos, Integer> entry : columnDistances.entrySet()) {
            BlockPos column = entry.getKey();
            int distance = entry.getValue();
            if (distance < 1 || distance > width) {
                continue;
            }
            if (!allowedColumns.contains(column)) {
                continue;
            }

            for (int y = minY; y <= maxY; y++) {
                BlockPos pos = new BlockPos(column.getX(), y, column.getZ());
                BlockState current = world.getBlockState(pos);
                if (!edgeFinder.isReplaceableLandBlock(world, pos, current)) {
                    continue;
                }

                Biome biome = biomeCache.computeIfAbsent(pos, p -> world.getBiome(p).value());
                BlockState newState = blockGenerator.generate(world, pos, distance, biome);
                if (newState != null) {
                    transitions.put(pos, ShorelineTransition.valid(pos, newState, distance));
                }
            }
        }

        return transitions;
    }

    public ShorelineResult collectApplyableTransitions(World world, PlayerEntity player,
                                                       Map<BlockPos, ShorelineTransition> transitions,
                                                       ShorelineVegetationPlanner vegetationPlanner) {
        List<BlockPos> affectedPositions = new ArrayList<>();
        List<BlockState> originalStates = new ArrayList<>();
        List<BlockState> newStates = new ArrayList<>();
        List<BlockPos> vegetationPositions = new ArrayList<>();
        int processedCount = 0;

        for (ShorelineTransition transition : transitions.values()) {
            if (transition.isValid() && isValidHeightForShorelineProcess(transition.pos, player)) {
                if (!edgeFinder.isChunkLoaded(world, transition.pos)) {
                    continue;
                }

                BlockState originalState = world.getBlockState(transition.pos);
                affectedPositions.add(transition.pos);
                originalStates.add(originalState);
                newStates.add(transition.newState);
                processedCount++;

                if (vegetationPlanner.shouldPlantVegetation(world, transition.pos, transition.distance, player)) {
                    vegetationPositions.add(transition.pos);
                    PushdozerMod.LOGGER.debug("Added vegetation position at {} with distance {}",
                        transition.pos, transition.distance);
                }
            }
        }

        return new ShorelineResult(affectedPositions, originalStates, newStates, vegetationPositions, processedCount, 0);
    }

    public boolean isValidHeightForShorelineProcess(BlockPos pos, PlayerEntity player) {
        if (config.getHeightMode() == PushdozerConfig.HeightMode.NO_LIMIT) {
            return true;
        }

        if (!config.isShorelineHeightAboveEnabled() && !config.isShorelineHeightBelowEnabled()) {
            return true;
        }

        int targetHeight = getTargetHeight(player);
        int posY = pos.getY();

        if (config.isShorelineHeightAboveEnabled()) {
            return posY >= targetHeight;
        }
        if (config.isShorelineHeightBelowEnabled()) {
            return posY <= targetHeight;
        }

        return true;
    }

    public int getTargetHeight(PlayerEntity player) {
        PushdozerConfig.HeightMode heightMode = config.getHeightMode();

        return switch (heightMode) {
            case FOLLOW_PLAYER -> playerHeightSnapshot != null ? playerHeightSnapshot : player.getBlockY();
            case LOCKED_ONCE, CUSTOM -> config.getLockedHeight();
            default -> player.getBlockY();
        };
    }
}
