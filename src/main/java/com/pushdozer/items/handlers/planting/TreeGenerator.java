package com.pushdozer.items.handlers.planting;

import com.pushdozer.PushdozerMod;
import com.pushdozer.config.PushdozerConfig;
import com.pushdozer.items.handlers.planting.model.BatchPlantingResult;
import com.pushdozer.items.handlers.planting.model.PlantingPosition;
import com.pushdozer.items.handlers.planting.model.TreeGenerationResult;
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

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;

public class TreeGenerator {
    private static final int CORE_SCAN_RADIUS = 4; // 核心扫描半径，用于变化检测
    private static final int TREE_HEIGHT_LIMIT = 32; // 最大树高度限制
    /** 跨 tick 调度时，每个 tick 最多生成的树木数量 */
    private static final int TREE_GENERATIONS_PER_TICK = 4;

    private final PushdozerConfig config;
    private final Random random;

    public TreeGenerator(PushdozerConfig config, Random random) {
        this.config = config;
        this.random = random;
    }

    /**
     * 跨 tick 生成树木，避免大范围批量种植时单 tick 卡顿。
     */
    public void scheduleTreesAcrossTicks(ServerWorld world, List<PlantingPosition> treePositions,
                                          int startIndex, Set<Long> blockedColumns,
                                          BatchPlantingResult result, Runnable onComplete) {
        int endIndex = Math.min(startIndex + TREE_GENERATIONS_PER_TICK, treePositions.size());

        for (int i = startIndex; i < endIndex; i++) {
            processSingleTree(world, treePositions.get(i), blockedColumns, result);
        }

        if (endIndex >= treePositions.size()) {
            onComplete.run();
            return;
        }

        Objects.requireNonNull(world.getServer()).execute(() ->
            scheduleTreesAcrossTicks(world, treePositions, endIndex, blockedColumns, result, onComplete)
        );
    }

    private void processSingleTree(ServerWorld world, PlantingPosition pos, Set<Long> blockedColumns,
                                   BatchPlantingResult result) {
        long colKey = BlockPos.asLong(pos.position.getX(), 0, pos.position.getZ());
        if (blockedColumns.contains(colKey)) {
            return;
        }

        if (!canPlantAt(world, pos.position)) {
            return;
        }

        TreeGenerationResult treeResult = generateTreeWithSmartBoundary(world, pos.position);
        if (treeResult.isEmpty()) {
            return;
        }

        result.incrementTreeCount();

        for (int dx = -4; dx <= 4; dx++) {
            for (int dz = -4; dz <= 4; dz++) {
                long nearbyColKey = BlockPos.asLong(pos.position.getX() + dx, 0, pos.position.getZ() + dz);
                blockedColumns.add(nearbyColKey);
            }
        }

        for (int i = 0; i < treeResult.affectedPositions.size(); i++) {
            BlockPos affectedPos = treeResult.affectedPositions.get(i);
            result.addTreeBlock(affectedPos, treeResult.originalStates.get(i), treeResult.newStates.get(i));
        }
    }

    /**
     * 智能边界树生成
     * 优化版本：只记录实际发生变化的方块，使用更小的核心区域
     * 增强版本：添加空指针检查和生成验证
     * 性能优化版本：使用BFS扫描变化，而非全区域扫描
     */
    private TreeGenerationResult generateTreeWithSmartBoundary(ServerWorld world, BlockPos centerPos) {
        TreeGenerationResult result = new TreeGenerationResult();

        // 预先记录核心区域的原始状态（生成前快照）
        BlockPos coreMinPos = centerPos.add(-CORE_SCAN_RADIUS, -CORE_SCAN_RADIUS, -CORE_SCAN_RADIUS);
        BlockPos coreMaxPos = centerPos.add(CORE_SCAN_RADIUS, TREE_HEIGHT_LIMIT, CORE_SCAN_RADIUS);
        Map<BlockPos, BlockState> originalStates = new HashMap<>();
        for (BlockPos pos : BlockPos.iterate(coreMinPos, coreMaxPos)) {
            originalStates.put(pos.toImmutable(), world.getBlockState(pos));
        }

        // 生成树
        Optional<RegistryKey<ConfiguredFeature<?, ?>>> treeFeature = getTreeFeatureForBiome(world.getBiome(centerPos));
        if (treeFeature.isPresent()) {
            var registry = world.getRegistryManager().getOrThrow(RegistryKeys.CONFIGURED_FEATURE);
            ConfiguredFeature<?, ?> feature = registry.get(treeFeature.get());

            // ⭐ 添加空指针检查：确保feature和chunkGenerator都不为null
            if (feature != null && world.getChunkManager().getChunkGenerator() != null) {
                feature.generate(world, world.getChunkManager().getChunkGenerator(), this.random, centerPos);
            } else {
                // 记录树生成失败
                PushdozerMod.LOGGER.warn("Tree generation failed: feature={}, chunkGenerator={}",
                        feature != null, world.getChunkManager().getChunkGenerator() != null);
                return result;
            }
        } else {
            // 记录无法获取树特性
            PushdozerMod.LOGGER.warn("No tree feature found for biome at position: {}", centerPos);
            return result;
        }

        // 使用BFS扫描变化，从中心点开始扩散
        Set<BlockPos> visited = new HashSet<>();
        Queue<BlockPos> queue = new LinkedList<>();
        queue.offer(centerPos);
        visited.add(centerPos);

        while (!queue.isEmpty()) {
            BlockPos pos = queue.poll();
            BlockState originalState = originalStates.get(pos);
            BlockState newState = world.getBlockState(pos);

            if (originalState != null && !originalState.equals(newState)) {
                result.addChange(pos, originalState, newState);

                // 向周围扩散检查
                for (int dx = -1; dx <= 1; dx++) {
                    for (int dy = -1; dy <= 1; dy++) {
                        for (int dz = -1; dz <= 1; dz++) {
                            BlockPos neighbor = pos.add(dx, dy, dz);
                            if (!visited.contains(neighbor) &&
                                    neighbor.getY() >= coreMinPos.getY() && neighbor.getY() <= coreMaxPos.getY() &&
                                    Math.abs(neighbor.getX() - centerPos.getX()) <= CORE_SCAN_RADIUS &&
                                    Math.abs(neighbor.getZ() - centerPos.getZ()) <= CORE_SCAN_RADIUS) {
                                visited.add(neighbor);
                                queue.offer(neighbor);
                            }
                        }
                    }
                }
            }
        }

        return result;
    }

    /**
     * 检查是否可以在指定位置生成植物
     * 检查脚下土壤（使用标签），当前位置为空气或可替换
     * 增强版本：支持更多基底类型，包括mycelium、rooted_dirt等
     */
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

    /**
     * 数据驱动：根据标签选择树特性，优先用PushdozerBiomeTags，无则用字符串contains兜底
     * 修复版本：修复逻辑缺陷，确保所有判断都能正确执行
     * 优化版本：使用数据驱动的植被注册表
     * 增强版本：支持用户选择的树种和尊重生物群系设置
     */
    private Optional<RegistryKey<ConfiguredFeature<?, ?>>> getTreeFeatureForBiome(RegistryEntry<Biome> biomeEntry) {
        // 改为仅受树木类型选项控制
        PushdozerConfig.TreeSpecies selectedTree = config.getSelectedTree();

        // 生物群系自适应模式
        if (selectedTree == PushdozerConfig.TreeSpecies.BIOME_ADAPTIVE) {
            return BiomeVegetationRegistry.getTreeFeature(biomeEntry);
        }

        // 如果用户选择了特定树种，直接返回对应的特性
        return Optional.of(getTreeFeatureForSpecies(selectedTree));
    }

    /**
     * 根据用户选择的树种获取对应的树特性
     */
    private RegistryKey<ConfiguredFeature<?, ?>> getTreeFeatureForSpecies(PushdozerConfig.TreeSpecies species) {
        return switch (species) {
            case OAK -> RegistryKey.of(RegistryKeys.CONFIGURED_FEATURE, net.minecraft.util.Identifier.of("minecraft", "oak"));
            case SPRUCE -> RegistryKey.of(RegistryKeys.CONFIGURED_FEATURE, net.minecraft.util.Identifier.of("minecraft", "spruce"));
            case BIRCH -> RegistryKey.of(RegistryKeys.CONFIGURED_FEATURE, net.minecraft.util.Identifier.of("minecraft", "birch"));
            case JUNGLE -> RegistryKey.of(RegistryKeys.CONFIGURED_FEATURE, net.minecraft.util.Identifier.of("minecraft", "jungle_tree"));
            case ACACIA -> RegistryKey.of(RegistryKeys.CONFIGURED_FEATURE, net.minecraft.util.Identifier.of("minecraft", "acacia"));
            case DARK_OAK -> RegistryKey.of(RegistryKeys.CONFIGURED_FEATURE, net.minecraft.util.Identifier.of("minecraft", "dark_oak"));
            case BIOME_ADAPTIVE -> BiomeVegetationRegistry.DEFAULT_TREE; // 这种情况理论上不会发生，但为了安全
        };
    }
}
