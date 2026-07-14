package com.pushdozer.items.handlers;

import com.pushdozer.PushdozerMod;
import com.pushdozer.config.PushdozerConfig;
import com.pushdozer.shapes.GeometryShape;
import com.pushdozer.util.PositionRandom;
import com.pushdozer.util.OperationPermissions;
import com.pushdozer.util.ShapeUtil;
import com.pushdozer.operations.UndoAction;
import com.pushdozer.operations.BlockOperation;
import com.pushdozer.network.NetworkManager;
import net.minecraft.block.*;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.fluid.FluidState;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.World;
import java.util.*;

/**
 * Abstract terrain tool handler base class
 * Provides shared base functionality for all terrain tools, subclasses only need to implement specific height calculation algorithms
 */
public abstract class AbstractTerrainToolHandler implements TerrainToolHandler {

    protected PushdozerConfig config;

    // REFINED: 简化忽略方块列表，使用BlockTags替代大部分硬编码
    protected static final Set<Block> IGNORED_BLOCKS = Set.of(
        Blocks.VINE, Blocks.SNOW, Blocks.BROWN_MUSHROOM, Blocks.RED_MUSHROOM
    );

    public AbstractTerrainToolHandler() {
    }

    /**
     * Main entry point for handling terrain operations
     * Enhanced with multiplayer support and permission verification
     */
    @Override
    public void handleOperation(PlayerEntity player, World world, UndoAction.ActionType actionType, PushdozerConfig config) {
        this.config = config;
        if (world.isClient()) return;

        // Multiplayer permission check
        if (!OperationPermissions.checkForTerrainOperation(player, world, config)) {
            return;
        }

        BlockPos basePos = ShapeUtil.getTargetBlockPos(player, config);
        GeometryShape shape = ShapeUtil.createShape(player, config, basePos);

        if (shape == null) {
            return;
        }

        List<BlockPos> affectedPositions = new ArrayList<>();
        List<BlockState> originalStates = new ArrayList<>();
        List<BlockState> newStates = new ArrayList<>();

        // Execute terrain operation (plan changes first, then apply across ticks)
        processTerrain(world, shape, basePos, affectedPositions, originalStates, newStates);

        if (affectedPositions.isEmpty() || !(world instanceof ServerWorld serverWorld)) {
            return;
        }

        Runnable finalizeOperation = () -> {
            UndoAction undoAction = new UndoAction(
                actionType,
                affectedPositions,
                originalStates,
                newStates
            );
            PushdozerMod.pushUndoAction(player, undoAction);

            if (!Objects.requireNonNull(serverWorld.getServer()).isSingleplayer()) {
                NetworkManager.broadcastTerrainOperation(
                    serverWorld,
                    actionType.name(),
                    affectedPositions,
                    newStates
                );
            }
        };

        BlockOperation.applyTerrainChanges(serverWorld, affectedPositions, newStates, () -> {
            List<BlockPos> vegetationPositions = new ArrayList<>();
            List<BlockState> vegetationOriginal = new ArrayList<>();
            List<BlockState> vegetationNew = new ArrayList<>();
            collectFloatingVegetation(world, shape, vegetationPositions, vegetationOriginal, vegetationNew);

            if (vegetationPositions.isEmpty()) {
                finalizeOperation.run();
                return;
            }

            affectedPositions.addAll(vegetationPositions);
            originalStates.addAll(vegetationOriginal);
            newStates.addAll(vegetationNew);
            BlockOperation.applyTerrainChanges(serverWorld, vegetationPositions, vegetationNew, finalizeOperation);
        });
    }

    /**
     * Main method for processing terrain
     */
    protected void processTerrain(World world, GeometryShape shape, BlockPos brushCenter,
                                  List<BlockPos> affectedPositions,
                                  List<BlockState> originalStates,
                                  List<BlockState> newStates) {
        // 1. Collect terrain information
        Map<BlockPos, TerrainColumn> columns = collectTerrainColumns(world, shape, brushCenter);

        if (columns.isEmpty()) {
            return;
        }

        // 2. Calculate target heights
        Map<BlockPos, Integer> targetHeights = new HashMap<>();
        for (Map.Entry<BlockPos, TerrainColumn> entry : columns.entrySet()) {
            BlockPos columnXZ = entry.getKey();
            TerrainColumn column = entry.getValue();

            int targetHeight = calculateTargetHeight(columns, column, columnXZ, brushCenter);
            targetHeights.put(columnXZ, targetHeight);
        }

        // 3. Apply height changes
        for (Map.Entry<BlockPos, Integer> entry : targetHeights.entrySet()) {
            BlockPos columnXZ = entry.getKey();
            int targetHeight = entry.getValue();
            TerrainColumn column = columns.get(columnXZ);

            applyHeightChange(world, columnXZ, column, targetHeight,
                           affectedPositions, originalStates, newStates);
        }
    }

    /**
     * Collect terrain information
     */
    protected Map<BlockPos, TerrainColumn> collectTerrainColumns(World world, GeometryShape shape, BlockPos brushCenter) {
        Map<BlockPos, TerrainColumn> columns = new HashMap<>();

        // Get all unique (X,Z) coordinates
        Set<BlockPos> uniqueXZPositions = shape.getBlockPositions().stream()
            .map(pos -> new BlockPos(pos.getX(), 0, pos.getZ()))
            .collect(java.util.stream.Collectors.toSet());

        for (BlockPos columnXZ : uniqueXZPositions) {
            BlockPos groundPos = findGroundBlock(world, columnXZ.withY(brushCenter.getY() + config.getRadius()));
            if (groundPos != null) {
                BlockState groundState = world.getBlockState(groundPos);

                // Ensure the found block is not an ignored block
                if (isIgnoredBlock(groundState) || groundState.isAir()) {
                    groundPos = findGroundBlock(world, groundPos.down());
                    if (groundPos == null) continue;
                    groundState = world.getBlockState(groundPos);
                }

                if (isIgnoredBlock(groundState) || groundState.isAir()) {
                    continue;
                }

                TerrainColumn column = new TerrainColumn(groundState, groundPos.getY());
                columns.put(columnXZ, column);
            }
        }

        return columns;
    }

    /**
     * Find ground block
     */
    protected BlockPos findGroundBlock(World world, BlockPos initialPos) {
        BlockPos.Mutable currentPos = new BlockPos.Mutable(initialPos.getX(), initialPos.getY(), initialPos.getZ());

        if (world.isAir(currentPos) || isWater(world, currentPos) || isIgnoredBlock(world.getBlockState(currentPos))) {
            // Starting from air, search downward
        } else {
            // If starting point is solid, first search upward to find the sky
            // REFINED: Use world.getHeight() for compatibility with dynamic world height
            while (currentPos.getY() < world.getHeight() &&
                   !world.isAir(currentPos) && !isWater(world, currentPos) &&
                   !isIgnoredBlock(world.getBlockState(currentPos))) {
                currentPos.move(0, 1, 0);
            }
        }

        // Search downward to find the first non-air/water/ignored block
        while (currentPos.getY() >= world.getBottomY() &&
               (world.isAir(currentPos) || isWater(world, currentPos) ||
                isIgnoredBlock(world.getBlockState(currentPos)))) {
            currentPos.move(0, -1, 0);
        }

        if (currentPos.getY() < world.getBottomY()) {
            return null;
        }
        return currentPos.toImmutable();
    }

    /**
     * Apply height changes
     */
    protected void applyHeightChange(World world, BlockPos columnXZ, TerrainColumn column,
                                   int targetHeight, List<BlockPos> affectedPositions,
                                   List<BlockState> originalStates, List<BlockState> newStates) {
        int currentHeight = column.getOriginalHeight();
        BlockState fillState = column.getMainBlockState();

        // Clamp target height to valid world range
        int clampedTargetHeight = Math.max(world.getBottomY(), Math.min(world.getHeight() - 1, targetHeight));

        if (clampedTargetHeight > currentHeight) {
            // Raise height
            // Select top layer block and fill block to make generated terrain more natural
            BlockState topState = fillState;
            BlockState fillerState = fillState;
                if (fillState.isOf(Blocks.GRASS_BLOCK)) {
                fillerState = Blocks.DIRT.getDefaultState();
                topState = Blocks.GRASS_BLOCK.getDefaultState();

                Random posRandom = PositionRandom.at(columnXZ);
                if (posRandom.nextFloat() < COARSE_DIRT_PROBABILITY) {
                    fillerState = Blocks.COARSE_DIRT.getDefaultState();
                }
            } else if (fillState.isOf(Blocks.PODZOL) || fillState.isOf(Blocks.MYCELIUM)) {
                // Podzol/Mycelium: use dirt inside, keep original surface
                fillerState = Blocks.DIRT.getDefaultState();
                topState = fillState;
            }

            for (int y = currentHeight + 1; y <= clampedTargetHeight; y++) {
                BlockPos pos = new BlockPos(columnXZ.getX(), y, columnXZ.getZ());
                BlockState originalState = world.getBlockState(pos);

                if (originalState.isReplaceable() || isWater(world, pos)) {
                    boolean isTopLayer = (y == clampedTargetHeight);
                    BlockState placeState = isTopLayer ? topState : fillerState;
                    affectedPositions.add(pos);
                    originalStates.add(originalState);
                    newStates.add(placeState);
                } else {
                    // If encountering a non-replaceable block, stop raising this column
                    break;
                }
            }
        } else if (clampedTargetHeight < currentHeight) {
            // Lower height
            for (int y = currentHeight; y > clampedTargetHeight; y--) {
                BlockPos pos = new BlockPos(columnXZ.getX(), y, columnXZ.getZ());
                BlockState originalState = world.getBlockState(pos);

                if (!world.isAir(pos)) {
                    affectedPositions.add(pos);
                    originalStates.add(originalState);
                    newStates.add(Blocks.AIR.getDefaultState());
                }
            }
        }
    }

    /**
     * Collect floating vegetation (executed after height changes are applied)
     */
    protected void collectFloatingVegetation(World world, GeometryShape shape,
                                          List<BlockPos> affectedPositions,
                                          List<BlockState> originalStates,
                                          List<BlockState> newStates) {
        // Get all unique (X,Z) coordinates to avoid duplicate checks
        Set<BlockPos> uniqueXZPositions = shape.getBlockPositions().stream()
            .map(pos -> new BlockPos(pos.getX(), 0, pos.getZ()))
            .collect(java.util.stream.Collectors.toSet());

        for (BlockPos columnXZ : uniqueXZPositions) {
            // Find the ground height for this column
            BlockPos groundPos = findGroundBlock(world, columnXZ.withY(world.getHeight()));
            if (groundPos == null) continue;

            int groundHeight = groundPos.getY();

            // Check for floating vegetation above ground (up to MAX_FLOATING_VEGETATION_CHECK_HEIGHT blocks)
            for (int y = groundHeight + 1; y <= groundHeight + MAX_FLOATING_VEGETATION_CHECK_HEIGHT; y++) {
                BlockPos pos = new BlockPos(columnXZ.getX(), y, columnXZ.getZ());
                BlockState state = world.getBlockState(pos);

                if (isIgnoredBlock(state)) {
                    BlockPos below = pos.down();
                    if (world.isAir(below) || isWater(world, below)) {
                        affectedPositions.add(pos);
                        originalStates.add(state);
                        newStates.add(Blocks.AIR.getDefaultState());
                    }
                }
            }
        }
    }

    /**
     * Check if water
     */
    protected boolean isWater(World world, BlockPos pos) {
        FluidState fluidState = world.getFluidState(pos);
        return !fluidState.isEmpty() && fluidState.isStill();
    }

    /**
     * Check if ignored block
     * REFINED: Uses BlockTags for better compatibility and extensibility
     */
    protected boolean isIgnoredBlock(BlockState state) {
        // Use tags to automatically be compatible with vanilla updates and blocks added by other mods
        if (state.isIn(BlockTags.LOGS) ||
            state.isIn(BlockTags.LEAVES) ||
            state.isIn(BlockTags.FLOWERS) ||
            state.isIn(BlockTags.SAPLINGS) ||
            state.isIn(BlockTags.CROPS) ||
            state.isIn(BlockTags.SMALL_FLOWERS)) {
            return true;
        }

        // Check bamboo (no suitable tag)
        if (state.getBlock() instanceof BambooBlock) {
            return true;
        }

        // For blocks without suitable tags, use simplified Set
        return IGNORED_BLOCKS.contains(state.getBlock());
    }

    /**
     * Abstract method: Calculate target height
     * Subclasses must implement this method to define specific terrain operation algorithms
     */
    protected abstract int calculateTargetHeight(Map<BlockPos, TerrainColumn> columns, 
                                               TerrainColumn currentColumn,
                                               BlockPos columnXZ, 
                                               BlockPos brushCenter);

    // Terrain operation constants
    private static final float COARSE_DIRT_PROBABILITY = 0.1f;
    private static final int MAX_FLOATING_VEGETATION_CHECK_HEIGHT = 10;

    // Gaussian smoothing parameters
    private static final float GAUSSIAN_KERNEL_RADIUS_FACTOR = 2.5f;
    private static final float MIN_SIGMA = 1.0f;

    /**
     * Calculate smoothed height for region (performance optimized version)
     * Reusable common method for subclasses
     */
    protected float calculateSmoothedHeight(Map<BlockPos, TerrainColumn> columns,
                                          BlockPos columnXZ,
                                          BlockPos brushCenter,
                                          int brushRadius) {
        float totalWeight = 0;
        float weightedHeightSum = 0;

        // Use configurable Gaussian parameters
        float sigmaFactor = getSigmaFactor(brushRadius);
        float sigma = brushRadius * sigmaFactor;
        if (sigma < MIN_SIGMA) sigma = MIN_SIGMA;

        double twoSigmaSquared = 2.0 * sigma * sigma;

        // Performance optimization: limit Gaussian kernel range
        float kernelRadius = sigma * GAUSSIAN_KERNEL_RADIUS_FACTOR;
        float maxDistanceSq = kernelRadius * kernelRadius;

        // Use current column as weight center to ensure smooth baseline varies more naturally with position
        BlockPos currentCenterXZ = new BlockPos(columnXZ.getX(), 0, columnXZ.getZ());

        // Iterate through all nearby columns to calculate weighted average height
        for (Map.Entry<BlockPos, TerrainColumn> entry : columns.entrySet()) {
            BlockPos neighborColumnXZ = entry.getKey();
            TerrainColumn neighborColumn = entry.getValue();

            // Use 2D plane distance from current column to neighbor column for calculation
            double distanceSq = neighborColumnXZ.getSquaredDistance(currentCenterXZ);

            // Performance optimization: skip blocks beyond Gaussian kernel range
            if (distanceSq > maxDistanceSq) continue;

            // Gaussian decay weight
            float weight = (float) Math.exp(-distanceSq / twoSigmaSquared);

            weightedHeightSum += neighborColumn.getOriginalHeight() * weight;
            totalWeight += weight;
        }

        if (totalWeight <= 0) {
            return 0; // Return default value, will be replaced with original height in actual use
        }

        return weightedHeightSum / totalWeight;
    }

    /**
     * Get Gaussian parameter factor
     * Dynamically adjust based on brush radius to ensure consistent effect at different radii
     */
    protected float getSigmaFactor(int brushRadius) {
        // Small radius uses larger factor for better local effects
        if (brushRadius <= 5) {
            return 0.5f;
        } else if (brushRadius <= 10) {
            return 0.4f;
        } else {
            return 0.35f; // Large radius uses smaller factor to avoid over-smoothing
        }
    }

    /**
     * Terrain column data class
     */
    protected static class TerrainColumn {
        private final int originalHeight;
        private final BlockState mainBlockState;

        public TerrainColumn(BlockState mainBlockState, int initialHeight) {
            this.mainBlockState = mainBlockState;
            this.originalHeight = initialHeight;
        }

        public int getOriginalHeight() {
            return originalHeight;
        }

        public BlockState getMainBlockState() {
            return mainBlockState;
        }
    }
} 