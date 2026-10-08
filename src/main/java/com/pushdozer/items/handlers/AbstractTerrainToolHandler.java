package com.pushdozer.items.handlers;

import com.pushdozer.PushdozerMod;
import com.pushdozer.config.PushdozerConfig;
import com.pushdozer.items.handlers.terrain.TerrainSurfaceQueries;
import com.pushdozer.shapes.GeometryShape;
import com.pushdozer.util.PositionRandom;
import com.pushdozer.util.OperationPermissions;
import com.pushdozer.util.ShapeUtil;
import com.pushdozer.util.TerrainOperationFeedback;
import com.pushdozer.util.WorldBounds;
import com.pushdozer.operations.AppliedChangeResult;
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

    /** Set for the synchronous planning phase of each operation; not read from async callbacks. */
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

        processTerrain(world, shape, basePos, config, affectedPositions, originalStates, newStates);

        if (affectedPositions.isEmpty() || !(world instanceof ServerWorld serverWorld)) {
            return;
        }

        final AppliedChangeResult[] accumulated = {AppliedChangeResult.empty()};

        Runnable pushUndoAndBroadcast = () -> {
            AppliedChangeResult total = accumulated[0];
            if (total.isEmpty()) {
                return;
            }

            UndoAction undoAction = new UndoAction(
                actionType,
                serverWorld.getRegistryKey(),
                total.positions(),
                total.originalStates(),
                total.appliedStates()
            );
            PushdozerMod.pushUndoAction(player, undoAction);

            if (!Objects.requireNonNull(serverWorld.getServer()).isSingleplayer()) {
                NetworkManager.broadcastTerrainOperation(
                    serverWorld,
                    actionType.name(),
                    total.positions(),
                    total.appliedStates()
                );
            }
        };

        Optional<BlockOperation.TerrainOperationToken> operationToken =
            BlockOperation.beginTerrainOperation(serverWorld, affectedPositions);
        if (operationToken.isEmpty()) {
            TerrainOperationFeedback.notifyRegionBusy(player);
            return;
        }

        BlockOperation.TerrainOperationToken token = operationToken.get();
        BlockOperation.applyTerrainPhase(token, affectedPositions, newStates, phase1 -> {
            accumulated[0] = accumulated[0].mergedWith(phase1);

            List<BlockPos> vegetationPositions = new ArrayList<>();
            List<BlockState> vegetationOriginal = new ArrayList<>();
            List<BlockState> vegetationNew = new ArrayList<>();
            collectFloatingVegetation(world, shape, vegetationPositions, vegetationOriginal, vegetationNew);

            if (vegetationPositions.isEmpty()) {
                try {
                    pushUndoAndBroadcast.run();
                } finally {
                    BlockOperation.releaseTerrainOperation(token);
                }
                return;
            }

            if (!BlockOperation.extendTerrainOperation(token, vegetationPositions)) {
                TerrainOperationFeedback.notifyRegionBusy(player);
                try {
                    pushUndoAndBroadcast.run();
                } finally {
                    BlockOperation.releaseTerrainOperation(token);
                }
                return;
            }

            BlockOperation.applyTerrainPhase(token, vegetationPositions, vegetationNew, phase2 -> {
                accumulated[0] = accumulated[0].mergedWith(phase2);
                try {
                    pushUndoAndBroadcast.run();
                } finally {
                    BlockOperation.releaseTerrainOperation(token);
                }
            });
        });
    }

    /**
     * Main method for processing terrain
     */
    protected void processTerrain(World world, GeometryShape shape, BlockPos brushCenter, PushdozerConfig config,
                                  List<BlockPos> affectedPositions,
                                  List<BlockState> originalStates,
                                  List<BlockState> newStates) {
        Set<BlockPos> modifyColumns = collectModifyColumnPositions(shape);
        if (modifyColumns.isEmpty()) {
            return;
        }

        int padding = getSamplePaddingBlocks(config);
        Set<BlockPos> samplePositions = expandColumnPositions(modifyColumns, padding);
        int searchStartY = shape.getMaxY(brushCenter);
        Map<BlockPos, TerrainColumn> sampleColumns =
            collectTerrainColumnsForPositions(world, samplePositions, searchStartY);

        if (sampleColumns.isEmpty()) {
            return;
        }

        Map<BlockPos, Integer> targetHeights = new HashMap<>();
        for (BlockPos columnXZ : modifyColumns) {
            TerrainColumn column = sampleColumns.get(columnXZ);
            if (column == null) {
                continue;
            }
            targetHeights.put(columnXZ, calculateTargetHeight(sampleColumns, column, columnXZ, brushCenter));
        }

        for (Map.Entry<BlockPos, Integer> entry : targetHeights.entrySet()) {
            BlockPos columnXZ = entry.getKey();
            TerrainColumn column = sampleColumns.get(columnXZ);
            applyHeightChange(world, columnXZ, column, entry.getValue(),
                affectedPositions, originalStates, newStates);
        }
    }

    /** Extra blocks beyond the brush footprint used for height sampling only. */
    protected int getSamplePaddingBlocks(PushdozerConfig config) {
        return 0;
    }

    protected int getEffectiveBrushRadius(PushdozerConfig config) {
        return config.getLargestBrushDimension();
    }

    protected static Set<BlockPos> collectModifyColumnPositions(GeometryShape shape) {
        return shape.getBlockPositions().stream()
            .map(pos -> new BlockPos(pos.getX(), 0, pos.getZ()))
            .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
    }

    protected static Set<BlockPos> expandColumnPositions(Set<BlockPos> centerColumns, int padding) {
        if (padding <= 0) {
            return centerColumns;
        }
        LinkedHashSet<BlockPos> expanded = new LinkedHashSet<>(centerColumns);
        int padSq = padding * padding;
        for (BlockPos column : centerColumns) {
            for (int dz = -padding; dz <= padding; dz++) {
                for (int dx = -padding; dx <= padding; dx++) {
                    if (dx * dx + dz * dz <= padSq) {
                        expanded.add(new BlockPos(column.getX() + dx, 0, column.getZ() + dz));
                    }
                }
            }
        }
        return expanded;
    }

    protected Map<BlockPos, TerrainColumn> collectTerrainColumnsForPositions(World world, Set<BlockPos> columnPositions,
                                                                             int searchStartY) {
        Map<BlockPos, TerrainColumn> columns = new HashMap<>();
        for (BlockPos columnXZ : columnPositions) {
            BlockPos groundPos = findGroundBlock(world, columnXZ.withY(searchStartY));
            if (groundPos == null) {
                continue;
            }

            BlockState groundState = world.getBlockState(groundPos);
            if (isIgnoredBlock(groundState) || groundState.isAir()) {
                groundPos = findGroundBlock(world, groundPos.down());
                if (groundPos == null) {
                    continue;
                }
                groundState = world.getBlockState(groundPos);
            }

            if (isIgnoredBlock(groundState) || groundState.isAir()) {
                continue;
            }

            columns.put(columnXZ, new TerrainColumn(groundState, groundPos.getY()));
        }
        return columns;
    }

    /**
     * Collect terrain information (legacy wrapper; prefer sample/modify split in processTerrain).
     */
    protected Map<BlockPos, TerrainColumn> collectTerrainColumns(World world, GeometryShape shape, BlockPos brushCenter,
                                                                 PushdozerConfig config) {
        Set<BlockPos> modifyColumns = collectModifyColumnPositions(shape);
        Set<BlockPos> samplePositions = expandColumnPositions(modifyColumns, getSamplePaddingBlocks(config));
        return collectTerrainColumnsForPositions(world, samplePositions, shape.getMaxY(brushCenter));
    }

    /** Cosine edge falloff from 80% to 100% of brush radius (0 at edge, 1 in core). */
    protected static float calculateBrushEdgeFalloff(BlockPos columnXZ, BlockPos brushCenter, int brushRadius) {
        int dx = columnXZ.getX() - brushCenter.getX();
        int dz = columnXZ.getZ() - brushCenter.getZ();
        float distanceSq = dx * dx + dz * dz;

        float innerRadius = brushRadius * 0.8f;
        float outerRadiusSq = (float) brushRadius * brushRadius;
        float innerRadiusSq = innerRadius * innerRadius;

        if (distanceSq <= innerRadiusSq) {
            return 1.0f;
        }
        if (distanceSq >= outerRadiusSq) {
            return 0.0f;
        }

        float t = (distanceSq - innerRadiusSq) / (outerRadiusSq - innerRadiusSq);
        t = Math.max(0.0f, Math.min(1.0f, t));
        return (float) (Math.cos(t * Math.PI) * 0.5 + 0.5);
    }

    protected static float applySmoothstep(float strength) {
        float t = Math.max(0.0f, Math.min(1.0f, strength));
        return t * t * (3.0f - 2.0f * t);
    }

    protected static float applySmootherstep(float strength) {
        float t = Math.max(0.0f, Math.min(1.0f, strength));
        return t * t * t * (t * (t * 6.0f - 15.0f) + 10.0f);
    }

    protected static float blendHeightWithStrengthAndFalloff(float originalHeight, float smoothedHeight,
                                                             float mappedStrength, float falloff) {
        return originalHeight + (smoothedHeight - originalHeight) * mappedStrength * falloff;
    }

    protected static float clampHeightDelta(float originalHeight, float targetHeight, float maxDelta) {
        if (maxDelta <= 0.0f) {
            return targetHeight;
        }
        float delta = targetHeight - originalHeight;
        return originalHeight + Math.max(-maxDelta, Math.min(maxDelta, delta));
    }

    /**
     * Find ground block
     */
    protected BlockPos findGroundBlock(World world, BlockPos initialPos) {
        return TerrainSurfaceQueries.findGroundBlock(world, initialPos);
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
        int clampedTargetHeight = WorldBounds.clampBuildableY(world, targetHeight);

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
     * Remove vegetation blocks inside the brush volume before terrain height edits.
     */
    protected void collectVegetationInBrushShape(World world, GeometryShape shape,
                                                 List<BlockPos> affectedPositions,
                                                 List<BlockState> originalStates,
                                                 List<BlockState> newStates) {
        for (BlockPos pos : shape.getBlockPositions()) {
            BlockState state = world.getBlockState(pos);
            if (!isIgnoredBlock(state)) {
                continue;
            }
            affectedPositions.add(pos);
            originalStates.add(state);
            newStates.add(Blocks.AIR.getDefaultState());
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
        return TerrainSurfaceQueries.isWater(world, pos);
    }

    /**
     * Check if ignored block
     * REFINED: Uses BlockTags for better compatibility and extensibility
     */
    protected boolean isIgnoredBlock(BlockState state) {
        return TerrainSurfaceQueries.isIgnoredBlock(state);
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
            return Float.NaN;
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