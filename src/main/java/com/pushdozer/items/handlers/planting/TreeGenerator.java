package com.pushdozer.items.handlers.planting;

import com.pushdozer.PushdozerMod;
import com.pushdozer.config.PushdozerConfig;
import com.pushdozer.items.handlers.planting.model.BatchPlantingResult;
import com.pushdozer.items.handlers.planting.model.PlantingPosition;
import com.pushdozer.items.handlers.planting.model.TreeGenerationResult;
import com.pushdozer.operations.VegetationOperation;
import com.pushdozer.util.PositionRandom;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.World;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.gen.feature.ConfiguredFeature;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public class TreeGenerator {
    private static final int DEFAULT_SCAN_RADIUS = 5;
    private static final int LARGE_SCAN_RADIUS = 8;
    private static final int DEFAULT_TREE_HEIGHT = 32;
    private static final int LARGE_TREE_HEIGHT = 40;
    private static final int SCAN_DEPTH = 4;
    /** 跨 tick 调度时，每个 tick 最多生成的树木数量 */
    private static final int TREE_GENERATIONS_PER_TICK = 4;
    private static final long TREE_RANDOM_SALT = 0x54724565L;

    private final PushdozerConfig config;
    private final long worldSeed;

    public TreeGenerator(PushdozerConfig config, long worldSeed) {
        this.config = config;
        this.worldSeed = worldSeed;
    }

    /** @deprecated 使用 {@link #TreeGenerator(PushdozerConfig, long)} */
    @Deprecated
    public TreeGenerator(PushdozerConfig config, Random ignored) {
        this(config, 0L);
    }

    /**
     * 收集树木生成所需的保守锁定范围（含树冠/树根外扩）。
     */
    public static List<BlockPos> collectLockPositions(List<PlantingPosition> treePositions, PushdozerConfig config) {
        List<BlockPos> lockPositions = new ArrayList<>();
        int radius = getScanRadius(config.getSelectedTree());
        int height = getScanHeight(config.getSelectedTree());
        for (PlantingPosition plantingPosition : treePositions) {
            BlockPos center = plantingPosition.position();
            BlockPos min = center.add(-radius, -SCAN_DEPTH, -radius);
            BlockPos max = center.add(radius, height, radius);
            for (BlockPos pos : BlockPos.iterate(min, max)) {
                lockPositions.add(pos.toImmutable());
            }
        }
        return lockPositions;
    }

    /**
     * 跨 tick 生成树木，避免大范围批量种植时单 tick 卡顿。
     */
    public void scheduleTreesAcrossTicks(ServerWorld world, List<PlantingPosition> treePositions,
                                         int startIndex, Set<Long> blockedColumns,
                                         BatchPlantingResult result, VegetationOperation operation,
                                         Runnable onComplete) {
        int endIndex = Math.min(startIndex + TREE_GENERATIONS_PER_TICK, treePositions.size());

        for (int i = startIndex; i < endIndex; i++) {
            processSingleTree(world, treePositions.get(i), blockedColumns, result);
        }

        if (endIndex >= treePositions.size()) {
            try {
                onComplete.run();
            } finally {
                operation.release();
            }
            return;
        }

        operation.scheduleNextBatch(() ->
            scheduleTreesAcrossTicks(world, treePositions, endIndex, blockedColumns, result, operation, onComplete)
        );
    }

    private void processSingleTree(ServerWorld world, PlantingPosition pos, Set<Long> blockedColumns,
                                   BatchPlantingResult result) {
        long colKey = BlockPos.asLong(pos.position().getX(), 0, pos.position().getZ());
        if (blockedColumns.contains(colKey)) {
            return;
        }

        if (!canPlantAt(world, pos.position())) {
            return;
        }

        TreeGenerationResult treeResult = generateTreeWithBoundaryScan(world, pos.position());
        if (treeResult.isEmpty()) {
            return;
        }

        result.incrementTreeCount();

        for (int dx = -4; dx <= 4; dx++) {
            for (int dz = -4; dz <= 4; dz++) {
                long nearbyColKey = BlockPos.asLong(pos.position().getX() + dx, 0, pos.position().getZ() + dz);
                blockedColumns.add(nearbyColKey);
            }
        }

        for (int i = 0; i < treeResult.affectedPositions.size(); i++) {
            BlockPos affectedPos = treeResult.affectedPositions.get(i);
            result.addTreeBlock(affectedPos, treeResult.originalStates.get(i), treeResult.newStates.get(i));
        }
    }

    /**
     * 在保守边界盒内做生成前后快照 diff，捕获全部树木相关变更。
     */
    private TreeGenerationResult generateTreeWithBoundaryScan(ServerWorld world, BlockPos centerPos) {
        TreeGenerationResult result = new TreeGenerationResult();

        int radius = getScanRadius(config.getSelectedTree());
        int height = getScanHeight(config.getSelectedTree());
        BlockPos scanMin = centerPos.add(-radius, -SCAN_DEPTH, -radius);
        BlockPos scanMax = centerPos.add(radius, height, radius);

        Map<BlockPos, BlockState> originalStates = new HashMap<>();
        for (BlockPos pos : BlockPos.iterate(scanMin, scanMax)) {
            originalStates.put(pos.toImmutable(), world.getBlockState(pos));
        }

        Optional<RegistryKey<ConfiguredFeature<?, ?>>> treeFeature = getTreeFeatureForBiome(world.getBiome(centerPos));
        if (treeFeature.isPresent()) {
            var registry = world.getRegistryManager().getOrThrow(RegistryKeys.CONFIGURED_FEATURE);
            ConfiguredFeature<?, ?> feature = registry.get(treeFeature.get());

            if (feature != null && world.getChunkManager().getChunkGenerator() != null) {
                Random treeRandom = PositionRandom.forOperation(centerPos, worldSeed, TREE_RANDOM_SALT);
                feature.generate(world, world.getChunkManager().getChunkGenerator(), treeRandom, centerPos);
            } else {
                PushdozerMod.LOGGER.warn("Tree generation failed: feature={}, chunkGenerator={}",
                        feature != null, world.getChunkManager().getChunkGenerator() != null);
                return result;
            }
        } else {
            PushdozerMod.LOGGER.warn("No tree feature found for biome at position: {}", centerPos);
            return result;
        }

        for (Map.Entry<BlockPos, BlockState> entry : originalStates.entrySet()) {
            BlockPos pos = entry.getKey();
            BlockState originalState = entry.getValue();
            BlockState newState = world.getBlockState(pos);
            if (!originalState.equals(newState)) {
                result.addChange(pos, originalState, newState);
            }
        }

        return result;
    }

    private static int getScanRadius(PushdozerConfig.TreeSpecies species) {
        return switch (species) {
            case JUNGLE, DARK_OAK, BIOME_ADAPTIVE -> LARGE_SCAN_RADIUS;
            case SPRUCE -> 6;
            default -> DEFAULT_SCAN_RADIUS;
        };
    }

    private static int getScanHeight(PushdozerConfig.TreeSpecies species) {
        return switch (species) {
            case JUNGLE, BIOME_ADAPTIVE -> LARGE_TREE_HEIGHT;
            case SPRUCE, DARK_OAK -> 36;
            default -> DEFAULT_TREE_HEIGHT;
        };
    }

    private boolean canPlantAt(World world, BlockPos pos) {
        BlockState groundState = world.getBlockState(pos.down());
        boolean isSoil = groundState.isIn(BlockTags.DIRT)
                || groundState.isIn(BlockTags.SAND)
                || groundState.isIn(BlockTags.SNOW)
                || groundState.isOf(Blocks.GRASS_BLOCK)
                || groundState.isOf(Blocks.DIRT_PATH)
                || groundState.isOf(Blocks.FARMLAND)
                || groundState.isOf(Blocks.MYCELIUM)
                || groundState.isOf(Blocks.ROOTED_DIRT)
                || groundState.isOf(Blocks.MOSS_BLOCK)
                || groundState.isOf(Blocks.CLAY);
        BlockState currentState = world.getBlockState(pos);
        return isSoil && (currentState.isAir() || currentState.isReplaceable());
    }

    private Optional<RegistryKey<ConfiguredFeature<?, ?>>> getTreeFeatureForBiome(RegistryEntry<Biome> biomeEntry) {
        PushdozerConfig.TreeSpecies selectedTree = config.getSelectedTree();

        if (selectedTree == PushdozerConfig.TreeSpecies.BIOME_ADAPTIVE) {
            return BiomeVegetationRegistry.getTreeFeature(biomeEntry);
        }

        return Optional.of(getTreeFeatureForSpecies(selectedTree));
    }

    private RegistryKey<ConfiguredFeature<?, ?>> getTreeFeatureForSpecies(PushdozerConfig.TreeSpecies species) {
        return switch (species) {
            case OAK -> RegistryKey.of(RegistryKeys.CONFIGURED_FEATURE, net.minecraft.util.Identifier.of("minecraft", "oak"));
            case SPRUCE -> RegistryKey.of(RegistryKeys.CONFIGURED_FEATURE, net.minecraft.util.Identifier.of("minecraft", "spruce"));
            case BIRCH -> RegistryKey.of(RegistryKeys.CONFIGURED_FEATURE, net.minecraft.util.Identifier.of("minecraft", "birch"));
            case JUNGLE -> RegistryKey.of(RegistryKeys.CONFIGURED_FEATURE, net.minecraft.util.Identifier.of("minecraft", "jungle_tree"));
            case ACACIA -> RegistryKey.of(RegistryKeys.CONFIGURED_FEATURE, net.minecraft.util.Identifier.of("minecraft", "acacia"));
            case DARK_OAK -> RegistryKey.of(RegistryKeys.CONFIGURED_FEATURE, net.minecraft.util.Identifier.of("minecraft", "dark_oak"));
            case BIOME_ADAPTIVE -> BiomeVegetationRegistry.DEFAULT_TREE;
        };
    }
}
