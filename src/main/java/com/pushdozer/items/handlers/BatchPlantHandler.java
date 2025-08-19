package com.pushdozer.items.handlers;

import com.pushdozer.PushdozerMod;
import com.pushdozer.config.PushdozerConfig;
import com.pushdozer.operations.UndoAction;
import com.pushdozer.shapes.GeometryShape;
import com.pushdozer.util.ShapeUtil;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.noise.SimplexNoiseSampler;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.World;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.gen.feature.ConfiguredFeature;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.TallPlantBlock;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.state.property.Properties;
import net.minecraft.block.enums.DoubleBlockHalf;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.world.Heightmap;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.registry.tag.BiomeTags;
import net.minecraft.util.math.Direction;
import net.minecraft.block.CoralWallFanBlock;

import java.util.*;

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

    // 密度控制常量
    private static final double MIN_TREE_PROB = 0.02; // 树的最小概率
    private static final double MIN_PLANT_PROB = 0.06; // 植物的最小概率
    private static final int CORE_SCAN_RADIUS = 4; // 核心扫描半径，用于变化检测

    /**
     * 数据驱动的植被注册表
     * 用于管理生物群系与植被之间的映射关系
     */
    private static class BiomeVegetationRegistry {
        // 树木特性映射
        private static final Map<TagKey<Biome>, RegistryKey<ConfiguredFeature<?, ?>>> TREE_FEATURES = new HashMap<>();
        // 花朵方块映射
        private static final Map<TagKey<Biome>, List<Block>> FLOWER_BLOCKS = new HashMap<>();
        // 草方块映射
        private static final Map<TagKey<Biome>, List<Block>> GRASS_BLOCKS = new HashMap<>();

        // 使用Minecraft常量替代硬编码字符串
        private static final RegistryKey<ConfiguredFeature<?, ?>> DEFAULT_TREE =
                RegistryKey.of(RegistryKeys.CONFIGURED_FEATURE, net.minecraft.util.Identifier.of("minecraft", "oak"));

        private static final List<Block> DEFAULT_FLOWERS =
                Arrays.asList(Blocks.DANDELION, Blocks.POPPY, Blocks.OXEYE_DAISY);

        private static final List<Block> DEFAULT_GRASS =
                Arrays.asList(Blocks.SHORT_GRASS, Blocks.TALL_GRASS, Blocks.FERN);

        static {
            initializeTreeFeatures();
            initializeFlowerBlocks();
            initializeGrassBlocks();
        }

        private static void initializeTreeFeatures() {
            TREE_FEATURES.put(com.pushdozer.tags.PushdozerBiomeTags.HAS_JUNGLE_TREES,
                    RegistryKey.of(RegistryKeys.CONFIGURED_FEATURE, net.minecraft.util.Identifier.of("minecraft", "jungle_tree")));
            TREE_FEATURES.put(com.pushdozer.tags.PushdozerBiomeTags.HAS_TAIGA_TREES,
                    RegistryKey.of(RegistryKeys.CONFIGURED_FEATURE, net.minecraft.util.Identifier.of("minecraft", "spruce")));
            TREE_FEATURES.put(com.pushdozer.tags.PushdozerBiomeTags.HAS_BIRCH_TREES,
                    RegistryKey.of(RegistryKeys.CONFIGURED_FEATURE, net.minecraft.util.Identifier.of("minecraft", "birch")));
            TREE_FEATURES.put(com.pushdozer.tags.PushdozerBiomeTags.HAS_OAK_TREES,
                    RegistryKey.of(RegistryKeys.CONFIGURED_FEATURE, net.minecraft.util.Identifier.of("minecraft", "oak")));
        }

        private static void initializeFlowerBlocks() {
            // 沙漠花朵
            FLOWER_BLOCKS.put(com.pushdozer.tags.PushdozerBiomeTags.HAS_DESERT_FLOWERS,
                    Arrays.asList(Blocks.DEAD_BUSH, Blocks.CACTUS));

            // 平原花朵
            FLOWER_BLOCKS.put(com.pushdozer.tags.PushdozerBiomeTags.HAS_PLAINS_FLOWERS,
                    Arrays.asList(Blocks.DANDELION, Blocks.POPPY, Blocks.OXEYE_DAISY, Blocks.CORNFLOWER));

            // 森林花朵
            FLOWER_BLOCKS.put(com.pushdozer.tags.PushdozerBiomeTags.HAS_FOREST_FLOWERS,
                    Arrays.asList(Blocks.LILY_OF_THE_VALLEY, Blocks.ALLIUM, Blocks.AZURE_BLUET,
                            Blocks.RED_TULIP, Blocks.ORANGE_TULIP, Blocks.WHITE_TULIP, Blocks.PINK_TULIP));

            // 海洋花朵（海草等）
            FLOWER_BLOCKS.put(BiomeTags.IS_OCEAN,
                    Arrays.asList(Blocks.SEAGRASS, Blocks.TALL_SEAGRASS));
        }

        private static void initializeGrassBlocks() {
            // 沙漠草
            GRASS_BLOCKS.put(com.pushdozer.tags.PushdozerBiomeTags.HAS_DESERT_GRASS,
                    Arrays.asList(Blocks.DEAD_BUSH, Blocks.CACTUS));

            // 平原草
            GRASS_BLOCKS.put(com.pushdozer.tags.PushdozerBiomeTags.HAS_PLAINS_GRASS,
                    Arrays.asList(Blocks.SHORT_GRASS, Blocks.TALL_GRASS, Blocks.FERN));

            // 海洋草（海草等）
            GRASS_BLOCKS.put(BiomeTags.IS_OCEAN,
                    Arrays.asList(Blocks.SEAGRASS, Blocks.TALL_SEAGRASS));
        }

        /**
         * 根据生物群系获取树木特性 (更健壮的版本)
         */
        public static Optional<RegistryKey<ConfiguredFeature<?, ?>>> getTreeFeature(RegistryEntry<Biome> biomeEntry) {
            // 1. 优先通过Tag匹配
            for (Map.Entry<TagKey<Biome>, RegistryKey<ConfiguredFeature<?, ?>>> entry : TREE_FEATURES.entrySet()) {
                if (biomeEntry.isIn(entry.getKey())) {
                    return Optional.of(entry.getValue());
                }
            }

            // 2. 如果Tag未匹配，提供一个绝对安全的默认值
            PushdozerMod.LOGGER.debug("Biome not matched for tree feature, using default oak");
            return Optional.of(DEFAULT_TREE);
        }

        /**
         * 根据生物群系获取花朵方块 (更健壮的版本)
         */
        public static List<Block> getFlowerBlocks(RegistryEntry<Biome> biomeEntry) {
            for (Map.Entry<TagKey<Biome>, List<Block>> entry : FLOWER_BLOCKS.entrySet()) {
                if (biomeEntry.isIn(entry.getKey())) {
                    // ⭐ 添加防御性检查：确保返回的不是 null
                    return entry.getValue() != null ? entry.getValue() : DEFAULT_FLOWERS;
                }
            }
            return DEFAULT_FLOWERS;
        }

        /**
         * 根据生物群系获取草方块 (更健壮的版本)
         */
        public static List<Block> getGrassBlocks(RegistryEntry<Biome> biomeEntry) {
            for (Map.Entry<TagKey<Biome>, List<Block>> entry : GRASS_BLOCKS.entrySet()) {
                if (biomeEntry.isIn(entry.getKey())) {
                    // ⭐ 添加防御性检查：确保返回的不是 null
                    return entry.getValue() != null ? entry.getValue() : DEFAULT_GRASS;
                }
            }
            return DEFAULT_GRASS;
        }
    }

    private final PushdozerConfig config;
    private final Random random;
    private final SimplexNoiseSampler noiseSampler;

    private static final int TREE_HEIGHT_LIMIT = 32; // 最大树高度限制
    // 已弃用的核心边界常量（使用扩展区域快照代替）

    // 常量定义：消除魔法数字
    private static final double NOISE_SCALE = 0.05; // 团簇噪声缩放

    public BatchPlantHandler(PushdozerConfig config) {
        this.config = config;
        this.random = Random.create();
        this.noiseSampler = new SimplexNoiseSampler(Random.create(1234L));
    }

    public void handleBatchPlant(PlayerEntity player, World world) {
        if (world.isClient || !(world instanceof ServerWorld serverWorld)) return;

        BlockPos basePos = ShapeUtil.getTargetBlockPos(player, config);
        GeometryShape shape = ShapeUtil.createShape(player, config, basePos);
        if (shape == null) return;

        // 添加调试日志
        PushdozerMod.LOGGER.info("Batch planting started at position: {}, plant type: {}", basePos, config.getPlantType());

        // 收集所有需要种植的位置
        List<PlantingPosition> plantingPositions = collectPlantingPositions(world, shape);
        if (plantingPositions.isEmpty()) {
            PushdozerMod.LOGGER.info("No planting positions found");
            return;
        }

        PushdozerMod.LOGGER.info("Found {} planting positions", plantingPositions.size());

        // 执行批量种植操作
        BatchPlantingResult result = executeBatchPlanting(serverWorld, plantingPositions);

        // 创建单个撤销操作
        if (!result.isEmpty()) {
            UndoAction undoAction = new UndoAction(
                    UndoAction.ActionType.BATCH_PLANT,
                    result.getAllPositions(),
                    result.getAllOriginalStates(),
                    result.getAllNewStates()
            );
            PushdozerMod.pushUndoAction(player, undoAction);
        }

        // 发送完成消息和性能统计
        if (result.getTotalCount() > 0) {
            player.sendMessage(net.minecraft.text.Text.translatable("pushdozer.message.batch_plant_complete", result.getTotalCount()), false);
            PushdozerMod.LOGGER.info("Batch planting completed: {} plants, {} trees, {} total blocks changed",
                    result.getSimplePlantCount(), result.getTreeCount(), result.getTotalCount());
        }
    }

    /**
     * 收集所有需要种植的位置
     * 性能优化版本：批量获取BlockState以提高性能
     */
    private List<PlantingPosition> collectPlantingPositions(World world, GeometryShape shape) {
        List<PlantingPosition> positions = new ArrayList<>();
        PushdozerConfig.PlantType plantType = config.getPlantType();

        // ⭐ 性能优化：批量获取BlockState
        Map<BlockPos, BlockState> blockStates = new HashMap<>();
        Map<BlockPos, BlockState> groundStates = new HashMap<>();

        for (BlockPos pos : shape.getBlockPositions()) {
            // 批量获取当前方块和地面方块状态
            blockStates.put(pos, world.getBlockState(pos));
            groundStates.put(pos.down(), world.getBlockState(pos.down()));
        }

        Set<Long> seenColumns = new HashSet<>();
        if (plantType == PushdozerConfig.PlantType.CUSTOM) {
            boolean preferWaterForCustom = containsLiveCoral(config.getCustomPlantBlocks());
            // 自定义类型：仅在地表或海底上方尝试，每个 XZ 列最多一次
            for (BlockPos pos : shape.getBlockPositions()) {
                long key = BlockPos.asLong(pos.getX(), 0, pos.getZ());
                if (seenColumns.contains(key)) continue;
                seenColumns.add(key);

                int x = pos.getX();
                int z = pos.getZ();

                int surfaceY = world.getTopY(Heightmap.Type.WORLD_SURFACE, x, z);
                int oceanFloorY = world.getTopY(Heightmap.Type.OCEAN_FLOOR, x, z);

                BlockPos topPos = new BlockPos(x, surfaceY, z);
                BlockState topState = world.getBlockState(topPos);
                BlockPos landCandidate = topState.isReplaceable() ? topPos : topPos.up();
                // 注意：world.getTopY(OCEAN_FLOOR) 已返回海床上方一格（通常是第一格水/空气）
                BlockPos seabedWaterPos = new BlockPos(x, oceanFloorY, z); // 海底上一格水体内（第一格水）

                // 候选位置按优先级尝试：若自定义集合包含活珊瑚则优先在水中尝试，否则优先地表
                List<BlockPos> candidates = preferWaterForCustom
                        ? Arrays.asList(seabedWaterPos, landCandidate)
                        : Arrays.asList(landCandidate, seabedWaterPos);
                for (BlockPos candidate : candidates) {
                    BlockState cs = world.getBlockState(candidate);
                    boolean land = candidate.equals(landCandidate);

                    // 对于活珊瑚，需要更严格的水中检查
                    if (preferWaterForCustom && !land) {
                        // 检查当前位置是否在水中
                        boolean inWater = cs.getFluidState().isIn(FluidTags.WATER);
                        if (!inWater) continue;

                        // 检查上方一格是否也在水中（确保不是水面）
                        BlockPos upperPos = candidate.up();
                        BlockState upperState = world.getBlockState(upperPos);
                        boolean upperInWater = upperState.getFluidState().isIn(FluidTags.WATER);
                        if (!upperInWater) continue;

                        // 额外检查：确保下方有合适的基底（沙子、珊瑚砂、石头等）
                        BlockPos lowerPos = candidate.down();
                        BlockState lowerState = world.getBlockState(lowerPos);
                        boolean validBase = lowerState.isIn(BlockTags.SAND) ||
                                lowerState.isIn(BlockTags.CORAL_BLOCKS) ||
                                lowerState.isIn(BlockTags.BASE_STONE_OVERWORLD) ||
                                lowerState.isIn(BlockTags.BASE_STONE_NETHER) ||
                                lowerState.isOf(Blocks.END_STONE) ||
                                lowerState.isOf(Blocks.GRAVEL) ||
                                lowerState.isOf(Blocks.CLAY);
                        if (!validBase) continue;
                    } else {
                        // 普通检查
                        boolean pass = land
                                ? (cs.isAir() || cs.isReplaceable())
                                : cs.getFluidState().isIn(FluidTags.WATER);
                        if (!pass) continue;
                    }

                    if (!shouldPlantHere(candidate)) continue;
                    positions.add(new PlantingPosition(candidate, plantType));
                    break; // 每列最多一个
                }
            }
        } else {
            // 非自定义类型也改为：每个 XZ 列最多一次，并落到地表
            Set<Long> blockedColumns = new HashSet<>();

            // 基于密度的最小间距，仅对简单植物生效
            int spacingRadius = 0;
            if (plantType == PushdozerConfig.PlantType.FLOWERS || plantType == PushdozerConfig.PlantType.GRASS) {
                float d = Math.max(0f, Math.min(1f, config.getPlantDensity()));
                spacingRadius = Math.max(1, Math.round(1 + (1.0f - d) * 2)); // 1..3 格的列级抑制
            }

            for (BlockPos pos : shape.getBlockPositions()) {
                long colKey = BlockPos.asLong(pos.getX(), 0, pos.getZ());
                if (seenColumns.contains(colKey) || blockedColumns.contains(colKey)) continue;
                seenColumns.add(colKey);

                int x = pos.getX();
                int z = pos.getZ();
                int surfaceY = world.getTopY(Heightmap.Type.WORLD_SURFACE, x, z);
                BlockPos topPos = new BlockPos(x, surfaceY, z);
                BlockState topState = world.getBlockState(topPos);
                // 树木使用地表方块作为生成中心；花/草优先替换顶层可替换方块（如短草），否则用顶层上方一格
                if (plantType == PushdozerConfig.PlantType.TREES) {
                    BlockPos placePos = new BlockPos(x, surfaceY + 1, z);
                    BlockPos treeCenter = new BlockPos(x, surfaceY, z);
                    BlockState currentState = world.getBlockState(placePos);
                    boolean passPrecheck = canPlantAtOptimized(currentState, world.getBlockState(treeCenter));
                    if (!passPrecheck) continue;
                    if (!shouldPlantHere(placePos)) continue;
                    positions.add(new PlantingPosition(treeCenter, plantType));
                } else {
                    BlockPos candidate = topState.isReplaceable() ? topPos : topPos.up();
                    BlockState currentState = world.getBlockState(candidate);
                    boolean passPrecheck = canPlantAtOptimized(currentState, world.getBlockState(candidate.down()));
                    if (!passPrecheck) continue;
                    if (!shouldPlantHere(candidate)) continue;
                    positions.add(new PlantingPosition(candidate, plantType));
                }

                // 简单植物最小间距抑制：屏蔽邻近列
                if (spacingRadius > 0) {
                    for (int dx = -spacingRadius; dx <= spacingRadius; dx++) {
                        for (int dz = -spacingRadius; dz <= spacingRadius; dz++) {
                            long k = BlockPos.asLong(x + dx, 0, z + dz);
                            blockedColumns.add(k);
                        }
                    }
                }
            }
        }

        return positions;
    }

    private boolean containsLiveCoral(List<Block> blocks) {
        if (blocks == null) return false;
        for (Block b : blocks) {
            if (b != null && isLiveCoral(b)) return true;
        }
        return false;
    }

    /**
     * 优化的canPlantAt方法，使用预获取的BlockState
     */
    private boolean canPlantAtOptimized(BlockState currentState, BlockState groundState) {
        // 放宽预筛选条件，将最终合法性交给方块自身的 canPlaceAt
        return currentState.isAir() || currentState.isReplaceable();
    }

    /**
     * 执行批量种植操作
     */
    private BatchPlantingResult executeBatchPlanting(ServerWorld world, List<PlantingPosition> positions) {
        BatchPlantingResult result = new BatchPlantingResult();

        // 分离树木和简单植物
        List<PlantingPosition> treePositions = new ArrayList<>();
        List<PlantingPosition> simplePlantPositions = new ArrayList<>();

        for (PlantingPosition pos : positions) {
            if (pos.plantType == PushdozerConfig.PlantType.TREES) {
                treePositions.add(pos);
            } else {
                simplePlantPositions.add(pos);
            }
        }

        // 处理简单植物（花草）
        processSimplePlants(world, simplePlantPositions, result);

        // 处理树木（需要特殊处理）
        processTrees(world, treePositions, result);

        return result;
    }

    /**
     * 处理简单植物（花草）
     */
    private void processSimplePlants(ServerWorld world, List<PlantingPosition> positions, BatchPlantingResult result) {
        PushdozerMod.LOGGER.info("Processing {} simple plants", positions.size());
        for (PlantingPosition pos : positions) {
            BlockPos basePos = pos.position;
            BlockState originalStateLower = world.getBlockState(basePos);
            BlockState newState = generateSimplePlant(world, basePos, pos.plantType);

            if (newState == null) {
                PushdozerMod.LOGGER.debug("No plant generated for position: {}", basePos);
                continue;
            }

            PushdozerMod.LOGGER.debug("Generated plant: {} at position: {}", newState.getBlock(), basePos);

            // 检查当前位置是否已有植物或装饰物，如果有则跳过
            if (hasExistingPlantOrDecoration(originalStateLower.getBlock())) {
                PushdozerMod.LOGGER.debug("Skipping plant placement at {}: existing plant/decoration detected", basePos);
                continue;
            }

            // 非水生植物在水中：直接忽略（不放置、不触发掉落）。
            // 允许的水中例外：活珊瑚、海草/高海草/海带/海带植株/海泡菜；睡莲需放在水面（空气上、下方为水）。
            // 垂滴叶是两栖植物，可以在水中或地面上种植
            boolean waterHere = originalStateLower.getFluidState().isIn(FluidTags.WATER)
                    || world.getFluidState(basePos).isIn(FluidTags.WATER);
            if (waterHere) {
                Block block = newState.getBlock();
                boolean allowedUnderwater = isLiveCoral(block) || isAquatic(block);
                boolean isLilyPad = block == Blocks.LILY_PAD;
                boolean isDripleaf = block == Blocks.SMALL_DRIPLEAF || block == Blocks.BIG_DRIPLEAF;
                if (!allowedUnderwater && !isLilyPad && !isDripleaf) {
                    continue;
                }
            }

            // 统一处理珊瑚（活珊瑚和失活珊瑚）
            if (pos.plantType == PushdozerConfig.PlantType.CUSTOM && (isLiveCoral(newState.getBlock()) || isDeadCoral(newState.getBlock()))) {
                if (handleCoralPlacement(world, basePos, newState, originalStateLower, result)) {
                    continue;
                }
            }

            // 统一处理垂滴叶（两栖植物，可以在水中或地面上种植）
            if (pos.plantType == PushdozerConfig.PlantType.CUSTOM && (newState.getBlock() == Blocks.SMALL_DRIPLEAF || newState.getBlock() == Blocks.BIG_DRIPLEAF)) {
                // 垂滴叶的处理逻辑已经在双高植物处理部分实现
                // 这里不需要额外处理，继续执行后续的双高植物逻辑
            }

            // 自定义盆栽规则：只能放在实体方块上，不能放在水中
            if (pos.plantType == PushdozerConfig.PlantType.CUSTOM && isPotted(newState.getBlock())) {
                // 检查是否在水中
                if (originalStateLower.getFluidState().isIn(FluidTags.WATER)) {
                    continue;
                }

                // 确保花盆可放置
                if (!newState.canPlaceAt(world, basePos)) {
                    continue;
                }

                // 放置花盆
                world.setBlockState(basePos, newState);
                result.addSimplePlant(basePos, originalStateLower, newState);
                continue;
            }

            // 双高植物特殊处理
            if (newState.getBlock() instanceof TallPlantBlock) {
                // 需要两个方块空间
                BlockPos upperPos = basePos.up();
                BlockState originalStateUpper = world.getBlockState(upperPos);

                // 组装上下半方块
                if (!newState.contains(Properties.DOUBLE_BLOCK_HALF)) {
                    // 防御：若映射差异导致属性不存在，则跳过该方块
                    continue;
                }

                // 特例：高海草（TALL_SEAGRASS）必须在连续两格水中，允许替换水体
                if (newState.getBlock() == Blocks.TALL_SEAGRASS) {
                    boolean lowerWater = world.getFluidState(basePos).isIn(FluidTags.WATER);
                    boolean upperWater = world.getFluidState(upperPos).isIn(FluidTags.WATER);
                    if (!(lowerWater && upperWater)) {
                        continue;
                    }
                    BlockState lower = newState.with(Properties.DOUBLE_BLOCK_HALF, DoubleBlockHalf.LOWER);
                    BlockState upper = newState.with(Properties.DOUBLE_BLOCK_HALF, DoubleBlockHalf.UPPER);

                    world.setBlockState(basePos, lower);
                    world.setBlockState(upperPos, upper);

                    result.addSimplePlant(basePos, originalStateLower, lower);
                    result.addSimplePlant(upperPos, originalStateUpper, upper);
                    continue;
                }

                // 特例：小型垂滴叶（SMALL_DRIPLEAF）两栖植物，可以在水中或地面上种植
                if (newState.getBlock() == Blocks.SMALL_DRIPLEAF) {
                    // 检查完整双高条件
                    BlockState upperCurrent = world.getBlockState(upperPos);
                    if (!(upperCurrent.isAir() || upperCurrent.isReplaceable())) {
                        continue;  // 上方阻塞，跳过
                    }

                    // 使用原版的 canPlaceAt（针对下半）
                    BlockState lower = newState.with(Properties.DOUBLE_BLOCK_HALF, DoubleBlockHalf.LOWER);
                    if (!lower.canPlaceAt(world, basePos)) {
                        continue;
                    }

                    // 根据是否在水中设置WATERLOGGED
                    boolean inWater = world.getFluidState(basePos).isIn(FluidTags.WATER);
                    boolean upperInWater = world.getFluidState(upperPos).isIn(FluidTags.WATER);
                    
                    if (inWater) {
                        lower = lower.with(Properties.WATERLOGGED, true);
                    }
                    BlockState upper;
                    if (upperInWater) {
                        upper = newState.with(Properties.DOUBLE_BLOCK_HALF, DoubleBlockHalf.UPPER).with(Properties.WATERLOGGED, true);
                    } else {
                        upper = newState.with(Properties.DOUBLE_BLOCK_HALF, DoubleBlockHalf.UPPER);
                    }
                    world.setBlockState(upperPos, upper);
                    result.addSimplePlant(upperPos, originalStateUpper, upper);

                    // 放置下半部分
                    world.setBlockState(basePos, lower);
                    result.addSimplePlant(basePos, originalStateLower, lower);

                    // 添加调试日志
                    PushdozerMod.LOGGER.debug("Placed Small Dripleaf at {}, height: 2, inWater: {}", basePos, inWater);
                    continue;
                }

                // 特例：大型垂滴叶（BIG_DRIPLEAF）两栖植物，可以在水中或地面上种植
                if (newState.getBlock() == Blocks.BIG_DRIPLEAF) {
                    // 检查完整双高条件
                    BlockState upperCurrent = world.getBlockState(upperPos);
                    if (!(upperCurrent.isAir() || upperCurrent.isReplaceable())) {
                        continue;  // 上方阻塞，跳过
                    }

                    // 使用原版的 canPlaceAt（针对下半）
                    BlockState lower = newState.with(Properties.DOUBLE_BLOCK_HALF, DoubleBlockHalf.LOWER);
                    if (!lower.canPlaceAt(world, basePos)) {
                        continue;
                    }

                    // 根据是否在水中设置WATERLOGGED
                    boolean inWater = world.getFluidState(basePos).isIn(FluidTags.WATER);
                    boolean upperInWater = world.getFluidState(upperPos).isIn(FluidTags.WATER);
                    
                    if (inWater) {
                        lower = lower.with(Properties.WATERLOGGED, true);
                    }
                    BlockState upper;
                    if (upperInWater) {
                        upper = newState.with(Properties.DOUBLE_BLOCK_HALF, DoubleBlockHalf.UPPER).with(Properties.WATERLOGGED, true);
                    } else {
                        upper = newState.with(Properties.DOUBLE_BLOCK_HALF, DoubleBlockHalf.UPPER);
                    }
                    world.setBlockState(upperPos, upper);
                    result.addSimplePlant(upperPos, originalStateUpper, upper);

                    // 放置下半部分
                    world.setBlockState(basePos, lower);
                    result.addSimplePlant(basePos, originalStateLower, lower);

                    // 添加调试日志
                    PushdozerMod.LOGGER.debug("Placed Big Dripleaf at {}, height: 2, inWater: {}", basePos, inWater);
                    continue;
                }

                if (!newState.canPlaceAt(world, basePos)) {
                    continue;
                }

                BlockState lower = newState.with(Properties.DOUBLE_BLOCK_HALF, DoubleBlockHalf.LOWER);
                BlockState upper = newState.with(Properties.DOUBLE_BLOCK_HALF, DoubleBlockHalf.UPPER);

                // 确保上方可替换（非水生双高植物）
                BlockState upperCurrent = world.getBlockState(upperPos);
                if (!(upperCurrent.isAir() || upperCurrent.isReplaceable())) {
                    continue;
                }

                world.setBlockState(basePos, lower);
                world.setBlockState(upperPos, upper);

                result.addSimplePlant(basePos, originalStateLower, lower);
                result.addSimplePlant(upperPos, originalStateUpper, upper);
                continue;
            }

            // 单格植物常规可放置性检查（失活珊瑚已在上方分支放置，这里跳过其 canPlaceAt）
            if (!isDeadCoral(newState.getBlock()) && !newState.canPlaceAt(world, basePos)) {
                continue;
            }

            world.setBlockState(basePos, newState);

            // 放置后验证：检查植物是否正确放置
            BlockState placedState = world.getBlockState(basePos);
            if (!placedState.equals(newState)) {
                // 如果放置失败（如珊瑚死亡），回滚并记录
                world.setBlockState(basePos, originalStateLower);
                PushdozerMod.LOGGER.debug("Plant placement failed at {}: expected {}, got {}",
                        basePos, newState.getBlock(), placedState.getBlock());
                continue;
            }

            // 额外检查活珊瑚是否存活
            if (isLiveCoral(newState.getBlock()) && isDeadCoral(placedState.getBlock())) {
                world.setBlockState(basePos, originalStateLower);
                PushdozerMod.LOGGER.debug("Live coral at {} turned into dead coral, reverting", basePos);
                continue;
            }

            result.addSimplePlant(basePos, originalStateLower, newState);
        }
    }

    /**
     * 统一处理珊瑚放置逻辑
     * 根据详细的珊瑚放置规则优化
     */
    private boolean handleCoralPlacement(ServerWorld world, BlockPos basePos, BlockState newState, 
                                       BlockState originalState, BatchPlantingResult result) {
        Block block = newState.getBlock();
        boolean isLive = isLiveCoral(block);
        boolean isDead = isDeadCoral(block);
        
        if (!isLive && !isDead) {
            return false; // 不是珊瑚，交给其他处理逻辑
        }

        // 检查当前位置是否已有植物或装饰物，如果有则跳过
        if (hasExistingPlantOrDecoration(originalState.getBlock())) {
            PushdozerMod.LOGGER.debug("Skipping coral placement at {}: existing plant/decoration detected", basePos);
            return false;
        }

        // 活珊瑚：必须在水中或与水相邻
        if (isLive) {
            // 检查是否在水中或与水相邻
            boolean inWater = originalState.getFluidState().isIn(FluidTags.WATER) 
                    || world.getFluidState(basePos).isIn(FluidTags.WATER);
            boolean adjacentToWater = isAdjacentToWater(world, basePos);
            
            if (!inWater && !adjacentToWater) {
                return false;
            }

            // 珊瑚扇需要检查上方是否也在水中
            if (isLiveCoralFan(block)) {
                BlockPos upperPos = basePos.up();
                BlockState upperState = world.getBlockState(upperPos);
                boolean upperInWater = upperState.getFluidState().isIn(FluidTags.WATER);
                if (!upperInWater) {
                    return false;
                }
            }

            // 墙面珊瑚扇：选择合适的侧面朝向
            BlockState toPlace = newState;
            if (block instanceof CoralWallFanBlock) {
                Direction facing = findValidWallDirection(world, basePos);
                if (facing == null) {
                    PushdozerMod.LOGGER.debug("Skipping wall coral fan at {}: no valid solid side", basePos);
                    return false;
                }
                toPlace = newState.with(Properties.HORIZONTAL_FACING, facing);
            }

            // 设置WATERLOGGED
            if (toPlace.contains(Properties.WATERLOGGED)) {
                toPlace = toPlace.with(Properties.WATERLOGGED, true);
            }

            // 检查是否可以放置
            if (!toPlace.canPlaceAt(world, basePos)) {
                PushdozerMod.LOGGER.debug("Skipping live coral at {}: canPlaceAt failed", basePos);
                return false;
            }

            world.setBlockState(basePos, toPlace);
            result.addSimplePlant(basePos, originalState, toPlace);
            return true;
        }

        // 失活珊瑚：允许放在任何实体方块上
        BlockState below = world.getBlockState(basePos.down());
        boolean spotFree = originalState.isAir() || originalState.isReplaceable();
        boolean solidBelow = below.isSolidBlock(world, basePos.down());

        if (!(spotFree && solidBelow)) {
            return false;
        }

        BlockState toPlace = newState;
        if (toPlace.contains(Properties.WATERLOGGED)) {
            toPlace = toPlace.with(Properties.WATERLOGGED, false);
        }

        world.setBlockState(basePos, toPlace);
        result.addSimplePlant(basePos, originalState, toPlace);
        return true;

    }

    /**
     * 检查位置是否与水相邻（六个方向）
     */
    private boolean isAdjacentToWater(World world, BlockPos pos) {
        for (Direction direction : Direction.values()) {
            BlockPos adjacentPos = pos.offset(direction);
            BlockState adjacentState = world.getBlockState(adjacentPos);
            if (adjacentState.getFluidState().isIn(FluidTags.WATER)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 为墙面珊瑚扇找到有效的墙面方向
     */
    private Direction findValidWallDirection(World world, BlockPos pos) {
        for (Direction direction : Direction.Type.HORIZONTAL) {
            BlockPos sidePos = pos.offset(direction);
            BlockState sideState = world.getBlockState(sidePos);
            if (sideState.isSolidBlock(world, sidePos)) {
                return direction.getOpposite();
            }
        }
        return null;
    }

    /**
     * 处理树木生成
     * 优化版本：使用更智能的边界检测和批量状态记录
     * 增强版本：改进重叠处理逻辑，减少内存使用
     */
    private void processTrees(ServerWorld world, List<PlantingPosition> positions, BatchPlantingResult result) {
        // 使用Set记录被屏蔽的XZ列，避免重叠生成（显著减少内存使用）
        Set<Long> blockedColumns = new HashSet<>();

        for (PlantingPosition pos : positions) {
            long colKey = BlockPos.asLong(pos.position.getX(), 0, pos.position.getZ());
            if (blockedColumns.contains(colKey)) {
                continue;
            }

            // ⭐ 新增：在生成前进行最终检查
            if (!canPlantAt(world, pos.position)) {
                continue;
            }

            // 生成树并记录所有受影响的方块
            TreeGenerationResult treeResult = generateTreeWithSmartBoundary(world, pos.position);

            if (!treeResult.isEmpty()) {
                result.incrementTreeCount();

                // ⭐ 优化重叠处理逻辑：仅屏蔽XZ列，减少内存使用
                for (int dx = -4; dx <= 4; dx++) {
                    for (int dz = -4; dz <= 4; dz++) {
                        long nearbyColKey = BlockPos.asLong(pos.position.getX() + dx, 0, pos.position.getZ() + dz);
                        blockedColumns.add(nearbyColKey);
                    }
                }

                // 记录所有受影响的方块
                for (int i = 0; i < treeResult.affectedPositions.size(); i++) {
                    BlockPos affectedPos = treeResult.affectedPositions.get(i);
                    result.addTreeBlock(affectedPos, treeResult.originalStates.get(i), treeResult.newStates.get(i));
                }
            }
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
     * 根据噪声和密度决定是否在此处种植，以形成团簇效果
     * 改进版本：使用动态团簇规模和修正的密度逻辑
     * 农作物优化：如果自定义植物包含农作物，使用更均匀的分布
     */
    private boolean shouldPlantHere(BlockPos pos) {
        // 统一密度控制：所有植物类型都应用密度设置
        double density = Math.max(0.0, Math.min(1.0, config.getPlantDensity()));

        // 农作物使用更均匀的分布，但仍受密度控制
        if (config.getPlantType() == PushdozerConfig.PlantType.CUSTOM) {
            List<Block> customBlocks = config.getCustomPlantBlocks();
            boolean containsCrops = customBlocks.stream().anyMatch(this::isCropBlock);
            if (containsCrops) {
                // 农作物使用简单的随机分布，但仍受密度控制
                return this.random.nextFloat() < density;
            }
        }

        // 树木沿用上一版语义：scale = clusterScale * NOISE_SCALE；
        // 其他类型保持“规模越大→频率越低”的语义
        float clusterScaleCfg = config.getClusterScale();
        double scale = (config.getPlantType() == PushdozerConfig.PlantType.TREES)
                ? (clusterScaleCfg * NOISE_SCALE)
                : (NOISE_SCALE / Math.max(0.1, clusterScaleCfg));

        int x = pos.getX();
        int z = pos.getZ();

        // fBm：多倍频叠加，柔化边界
        double n1 = noiseSampler.sample(x * scale, z * scale);
        double n2 = noiseSampler.sample(x * scale * 2.0, z * scale * 2.0);
        double n3 = noiseSampler.sample(x * scale * 4.0, z * scale * 4.0);
        double n = 0.6 * n1 + 0.3 * n2 + 0.1 * n3;

        // 归一化并加一点确定性抖动（简易蓝噪声）
        double finalProb = getFinalProb(n, x, z, density);
        return this.random.nextFloat() < (float) finalProb;
    }

    private double getFinalProb(double n, int x, int z, double density) {
        double p = (n + 1.0) * 0.5; // 0..1
        int h = (x * 73856093) ^ (z * 19349663);
        double jitter = ((h & 1023) / 1023.0) * 0.1 - 0.05; // ±0.05
        p = Math.max(0.0, Math.min(1.0, p + jitter));

        // 概率采样（带下限），避免小样本“全空”
        double baseProb = Math.max(0.0, Math.min(1.0, p * density));
        double minProb = (config.getPlantType() == PushdozerConfig.PlantType.TREES) ? MIN_TREE_PROB : MIN_PLANT_PROB;
        return Math.max(minProb, baseProb);
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



    private BlockState generateSimplePlant(World world, BlockPos pos, PushdozerConfig.PlantType type) {
        RegistryEntry<Biome> biomeEntry = world.getBiome(pos);
        if (type == PushdozerConfig.PlantType.FLOWERS) {
            // 若当前位置在水中，按需求直接忽略
            if (world.getFluidState(pos).isIn(FluidTags.WATER)) return null;
            return generateFlowerForBiome(biomeEntry);
        } else if (type == PushdozerConfig.PlantType.GRASS) {
            // 若当前位置在水中，按需求直接忽略
            if (world.getFluidState(pos).isIn(FluidTags.WATER)) return null;
            return generateGrassForBiome(biomeEntry);
        } else if (type == PushdozerConfig.PlantType.CUSTOM) {
            return generateCustomPlant(world, pos);
        }
        return null;
    }

    /**
     * 生成自定义选择的植物
     * 支持农作物：如果包含农作物，检查农田条件并忽略生物群系
     */
    private BlockState generateCustomPlant(World world, BlockPos pos) {
        List<Block> customBlocks = config.getCustomPlantBlocks();
        if (customBlocks.isEmpty()) {
            PushdozerMod.LOGGER.debug("No custom plants selected, skipping planting");
            return null;
        }

        // 过滤掉无效条目
        List<Block> validBlocks = customBlocks.stream()
                .filter(block -> block != null && block.getDefaultState() != null)
                .toList();

        if (validBlocks.isEmpty()) {
            PushdozerMod.LOGGER.debug("All custom plants are invalid, skipping planting");
            return null;
        }

        // 基于当前位置过滤可种植的候选方块（按规则动态判断：农作物/水生/陆地/沙地等）
        List<Block> placeableBlocks = validBlocks.stream()
                .filter(block -> canPlantCustomBlockAt(world, pos, block))
                .toList();

        if (placeableBlocks.isEmpty()) {
            // 当前位置不适合任何自定义植物，直接跳过
            PushdozerMod.LOGGER.debug("No placeable blocks found for position: {}. Valid blocks: {}, placeable: {}",
                    pos, validBlocks.stream().map(Block::toString).toList(),
                    placeableBlocks.stream().map(Block::toString).toList());
            return null;
        }

        // 添加调试信息，显示选择的植物
        Block chosen = placeableBlocks.get(random.nextInt(placeableBlocks.size()));
        PushdozerMod.LOGGER.debug("Chosen plant: {} from {} placeable options", chosen, placeableBlocks.size());

        BlockState state = chosen.getDefaultState();

        // 若是农作物，随机设置生长阶段（在可用时）
        if (state.contains(Properties.AGE_7)) {
            state = state.with(Properties.AGE_7, random.nextInt(8));
        }

        // 特殊方块：根据密度设置覆盖程度
        state = setCoverageBasedOnDensity(state, chosen);

        // Leaf Litter：随机设置朝向
        if (chosen.toString().toLowerCase().contains("leaf_litter") && state.contains(Properties.HORIZONTAL_FACING)) {
            Direction[] directions = Direction.Type.HORIZONTAL.stream().toArray(Direction[]::new);
            Direction randomDirection = directions[random.nextInt(directions.length)];
            state = state.with(Properties.HORIZONTAL_FACING, randomDirection);
        }

        // 小型垂滴叶：不在这里设置 WATERLOGGED，让放置逻辑处理

        return state;
    }

    // 自定义种植时的动态可种规则：
    // - 农作物：必须在耕地上且上方空气/可替换
    // - 水生/珊瑚类：当前位置必须在水中，且下方为合适基底（沙/珊瑚砂/岩等，简化为 canPlaceAt）
    // - 普通植物：允许在草方块、泥土、砂、雪顶、泥土小路等常见地表上，或替换顶层可替换物
    private boolean canPlantCustomBlockAt(World world, BlockPos pos, Block block) {
        BlockState targetState = world.getBlockState(pos);
        BlockState below = world.getBlockState(pos.down());
        BlockState state = block.getDefaultState();

        // 睡莲：只能种在水面（当前位置为空气，且下方是水）
        if (block == Blocks.LILY_PAD) {
            boolean airHere = targetState.isAir();
            boolean waterBelow = below.getFluidState().isIn(FluidTags.WATER);
            return airHere && waterBelow && state.canPlaceAt(world, pos);
        }

        // 双高植物要求上方可替换
        if (block instanceof TallPlantBlock) {
            if (!state.contains(Properties.DOUBLE_BLOCK_HALF)) return false;
            BlockPos upperPos = pos.up();
            BlockState upperCurrent = world.getBlockState(upperPos);
            if (!(upperCurrent.isAir() || upperCurrent.isReplaceable())) return false;
        }

        // 盆栽特例：只能放在实体方块上，不能放在花草植物、盆栽或水面上
        if (isPotted(block)) {
            // 检查当前位置：必须是空气或可替换，且不能是水
            boolean spotFree = targetState.isAir() || targetState.isReplaceable();
            boolean notInWater = !targetState.getFluidState().isIn(FluidTags.WATER);

            // 检查下方：必须是实体方块，且不能是花草植物或盆栽
            boolean solidBelow = below.isSolidBlock(world, pos.down());
            boolean notPlantBelow = !isPlantBlock(below.getBlock()) && !isPotted(below.getBlock());

            // 检查花盆本身是否可以放置
            boolean potCanPlace = Blocks.FLOWER_POT.getDefaultState().canPlaceAt(world, pos);

            return spotFree && notInWater && solidBelow && notPlantBelow && potCanPlace && state.canPlaceAt(world, pos);
        }

        // 农作物（AGE_7 特征或常见作物方块）
        boolean isCrop = isCropBlock(block) || state.contains(Properties.AGE_7);
        if (isCrop) {
            return below.isOf(Blocks.FARMLAND) && (targetState.isAir() || targetState.isReplaceable());
        }

        // 活珊瑚：必须在水中或与水相邻，且下方有合适的基底
        boolean inWater = targetState.getFluidState().isIn(FluidTags.WATER);
        boolean adjacentToWater = isAdjacentToWater(world, pos);
        
        if (isLiveCoral(block)) {
            // 检查上方一格是否也在水中（确保不是水面）
            BlockPos upperPos = pos.up();
            BlockState upperState = world.getBlockState(upperPos);
            boolean upperInWater = upperState.getFluidState().isIn(FluidTags.WATER);

            // 检查下方是否有合适的基底
            boolean validBase = below.isIn(BlockTags.SAND) ||
                    below.isIn(BlockTags.CORAL_BLOCKS) ||
                    below.isIn(BlockTags.BASE_STONE_OVERWORLD) ||
                    below.isIn(BlockTags.BASE_STONE_NETHER) ||
                    below.isOf(Blocks.END_STONE) ||
                    below.isOf(Blocks.GRAVEL) ||
                    below.isOf(Blocks.CLAY);

            // 墙面珊瑚扇需要固体侧面
            if (block instanceof CoralWallFanBlock) {
                boolean hasSolidSide = false;
                for (Direction direction : Direction.Type.HORIZONTAL) {
                    BlockPos sidePos = pos.offset(direction);
                    BlockState sideState = world.getBlockState(sidePos);
                    if (sideState.isSolidBlock(world, sidePos)) {
                        hasSolidSide = true;
                        break;
                    }
                }
                return (inWater || adjacentToWater) && upperInWater && hasSolidSide;
            }
            
            // 其他活珊瑚：需下方基底
            return (inWater || adjacentToWater) && upperInWater && validBase;
        }

        // 小型垂滴叶：两栖植物，可以在水中或地面上种植
        if (block == Blocks.SMALL_DRIPLEAF) {
            // 检查下方方块是否适合种植小型垂滴叶
            boolean validBase = below.isIn(BlockTags.SMALL_DRIPLEAF_PLACEABLE) ||
                    below.isOf(Blocks.CLAY) ||
                    below.isOf(Blocks.MOSS_BLOCK) ||
                    below.isOf(Blocks.ROOTED_DIRT) ||
                    below.isOf(Blocks.MYCELIUM) ||
                    below.isOf(Blocks.PODZOL) ||
                    // 水下种植的方块
                    (inWater && (below.isOf(Blocks.DIRT) ||
                            below.isOf(Blocks.COARSE_DIRT) ||
                            below.isOf(Blocks.GRASS_BLOCK) ||
                            below.isOf(Blocks.FARMLAND)));
            
            // 检查上方一格是否可替换（双高植物需要）
            BlockPos upperPos = pos.up();
            BlockState upperState = world.getBlockState(upperPos);
            boolean upperReplaceable = upperState.isAir() || upperState.isReplaceable();
            
            boolean result = validBase && upperReplaceable;
            PushdozerMod.LOGGER.debug("Small Dripleaf canPlant check at {}: validBase={}, upperReplaceable={}, result={}",
                    pos, validBase, upperReplaceable, result);
            return result;
        }

        // 大型垂滴叶：两栖植物，可以在水中或地面上种植
        if (block == Blocks.BIG_DRIPLEAF) {
            // 检查下方方块是否适合种植大型垂滴叶
            boolean validBase = below.isOf(Blocks.CLAY) ||
                    below.isOf(Blocks.MOSS_BLOCK) ||
                    below.isOf(Blocks.GRASS_BLOCK) ||
                    below.isOf(Blocks.MYCELIUM) ||
                    below.isOf(Blocks.PODZOL) ||
                    below.isOf(Blocks.DIRT) ||
                    below.isOf(Blocks.ROOTED_DIRT) ||
                    below.isOf(Blocks.COARSE_DIRT) ||
                    below.isOf(Blocks.FARMLAND) ||
                    below.isOf(Blocks.MUD) ||
                    below.isOf(Blocks.MUDDY_MANGROVE_ROOTS);
            
            // 检查上方一格是否可替换（双高植物需要）
            BlockPos upperPos = pos.up();
            BlockState upperState = world.getBlockState(upperPos);
            boolean upperReplaceable = upperState.isAir() || upperState.isReplaceable();
            
            boolean result = validBase && upperReplaceable;
            PushdozerMod.LOGGER.debug("Big Dripleaf canPlant check at {}: validBase={}, upperReplaceable={}, result={}",
                    pos, validBase, upperReplaceable, result);
            return result;
        }

        // 其他水生（如海草/高海草/海带/海带植株/海泡菜）：要求在水中，并满足 canPlaceAt
        if (isAquatic(block)) {
            return inWater && state.canPlaceAt(world, pos);
        }

        // 失活珊瑚：允许放在任何实体方块上（当前位置需为空气/可替换），忽略具体基底类型
        if (isDeadCoral(block)) {
            boolean spotFree = targetState.isAir() || targetState.isReplaceable();
            boolean solidBelow = below.isSolidBlock(world, pos.down());
            return spotFree && solidBelow;
        }

        // Leaf Litter：可以放置在任何具有完整固体顶面的方块上
        if (block.toString().toLowerCase().contains("leaf_litter")) {
            boolean spotFree = targetState.isAir() || targetState.isReplaceable();
            // 检查下方方块是否具有完整固体顶面
            boolean validBase = below.isSolidBlock(world, pos.down());
            boolean canPlace = spotFree && validBase;
            if (!canPlace) {
                PushdozerMod.LOGGER.debug("Cannot place leaf_litter at {}: spotFree={}, validBase={}", 
                        pos, spotFree, validBase);
            }
            return canPlace;
        }

        // 普通：放宽基底，草/泥土/沙/雪顶/泥土路等，且当前位置可替换或空气
        boolean validBase = below.isIn(net.minecraft.registry.tag.BlockTags.DIRT)
                || below.isOf(Blocks.GRASS_BLOCK)
                || below.isIn(net.minecraft.registry.tag.BlockTags.SAND)
                || below.isIn(net.minecraft.registry.tag.BlockTags.SNOW)
                || below.isOf(Blocks.DIRT_PATH)
                || below.isOf(Blocks.MOSS_BLOCK)
                || below.isOf(Blocks.CLAY);
        if (!validBase) return false;
        return (targetState.isAir() || targetState.isReplaceable()) && state.canPlaceAt(world, pos);
    }

    private boolean isAquatic(Block block) {
        // 水生植物（不含活/死珊瑚和垂滴叶）：海草、高海草、海带、海带植株、海泡菜
        return block == Blocks.SEAGRASS
                || block == Blocks.TALL_SEAGRASS
                || block == Blocks.KELP
                || block == Blocks.KELP_PLANT
                || block == Blocks.SEA_PICKLE;
    }

    /**
     * 珊瑚类型判断方法 - 修正版本
     */
    private boolean isLiveCoral(Block block) {
        String id = block.toString().toLowerCase();
        return id.contains("coral") && !id.contains("dead");
    }

    private boolean isLiveCoralFan(Block block) {
        String id = block.toString().toLowerCase();
        return id.contains("coral") && id.contains("fan") && !id.contains("dead");
    }
    private boolean isDeadCoral(Block block) {
        String id = block.toString().toLowerCase();
        return id.contains("coral") && id.contains("dead");
    }

    /**
     * 检查方块是否为已有的植物或装饰物
     * 包括仙人掌、仙人掌花、各种植物、装饰物等
     */
    private boolean hasExistingPlantOrDecoration(Block block) {
        // 仙人掌和仙人掌花
        if (block == Blocks.CACTUS || block == Blocks.CACTUS_FLOWER) {
            return true;
        }
        
        // 所有植物方块
        if (isPlantBlock(block)) {
            return true;
        }
        
        // 盆栽
        if (isPotted(block)) {
            return true;
        }
        
        // 活珊瑚和死珊瑚
        if (isLiveCoral(block) || isDeadCoral(block)) {
            return true;
        }
        
        // 其他装饰物
        return block == Blocks.DEAD_BUSH ||
                block == Blocks.SUGAR_CANE ||
                block == Blocks.BAMBOO ||
                block == Blocks.CHORUS_PLANT ||
                block == Blocks.CHORUS_FLOWER ||
                block == Blocks.NETHER_SPROUTS ||
                block == Blocks.WARPED_ROOTS ||
                block == Blocks.CRIMSON_ROOTS ||
                block == Blocks.WARPED_FUNGUS ||
                block == Blocks.CRIMSON_FUNGUS ||
                block == Blocks.WEEPING_VINES ||
                block == Blocks.TWISTING_VINES ||
                block == Blocks.GLOW_LICHEN ||
                block == Blocks.HANGING_ROOTS ||
                block == Blocks.SPORE_BLOSSOM ||
                block == Blocks.FLOWERING_AZALEA ||
                block == Blocks.AZALEA ||
                block == Blocks.MOSS_CARPET ||
                block == Blocks.SEAGRASS ||
                block == Blocks.TALL_SEAGRASS ||
                block == Blocks.KELP ||
                block == Blocks.KELP_PLANT ||
                block == Blocks.SEA_PICKLE ||
                block == Blocks.LILY_PAD ||
                block == Blocks.SMALL_DRIPLEAF ||
                block == Blocks.BIG_DRIPLEAF ||
                // Leaf Litter（自定义方块）
                block.toString().toLowerCase().contains("leaf_litter");
    }

    /**
     * 检查方块是否为花草植物（包括花朵、草、蕨类等）
     */
    private boolean isPlantBlock(Block block) {
        return block == Blocks.DANDELION || block == Blocks.POPPY || block == Blocks.BLUE_ORCHID ||
                block == Blocks.ALLIUM || block == Blocks.AZURE_BLUET || block == Blocks.RED_TULIP ||
                block == Blocks.ORANGE_TULIP || block == Blocks.WHITE_TULIP || block == Blocks.PINK_TULIP ||
                block == Blocks.OXEYE_DAISY || block == Blocks.CORNFLOWER || block == Blocks.LILY_OF_THE_VALLEY ||
                block == Blocks.SUNFLOWER || block == Blocks.LILAC || block == Blocks.ROSE_BUSH ||
                block == Blocks.PEONY || block == Blocks.SHORT_GRASS || block == Blocks.TALL_GRASS ||
                block == Blocks.FERN || block == Blocks.LARGE_FERN || block == Blocks.DEAD_BUSH ||
                block == Blocks.CACTUS || block == Blocks.SUGAR_CANE || block == Blocks.BAMBOO ||
                block == Blocks.CHORUS_PLANT || block == Blocks.CHORUS_FLOWER ||
                block == Blocks.NETHER_SPROUTS || block == Blocks.WARPED_ROOTS || block == Blocks.CRIMSON_ROOTS ||
                block == Blocks.WARPED_FUNGUS || block == Blocks.CRIMSON_FUNGUS ||
                block == Blocks.WEEPING_VINES || block == Blocks.TWISTING_VINES ||
                block == Blocks.GLOW_LICHEN || block == Blocks.HANGING_ROOTS ||
                block == Blocks.SPORE_BLOSSOM || block == Blocks.FLOWERING_AZALEA ||
                block == Blocks.AZALEA || block == Blocks.MOSS_CARPET ||
                block == Blocks.SEAGRASS || block == Blocks.TALL_SEAGRASS ||
                block == Blocks.KELP || block == Blocks.KELP_PLANT || block == Blocks.SEA_PICKLE ||
                block == Blocks.LILY_PAD || block == Blocks.SMALL_DRIPLEAF || block == Blocks.BIG_DRIPLEAF ||
                // 活珊瑚和死珊瑚
                isLiveCoral(block) || isDeadCoral(block) ||
                // 农作物
                isCropBlock(block) ||
                // 双高植物
                block instanceof TallPlantBlock ||
                // Leaf Litter（自定义方块）
                block.toString().toLowerCase().contains("leaf_litter");
    }

    private boolean isPotted(Block block) {
        // 盆栽在底层实现上也是 FlowerPotBlock，但与空花盆不同
        return (block instanceof net.minecraft.block.FlowerPotBlock) && block != Blocks.FLOWER_POT;
    }

    /**
     * 数据驱动：根据标签选择花类型，增加植被多样性
     * 优化版本：使用数据驱动的植被注册表
     * 增强版本：支持用户选择的花朵组和尊重生物群系设置
     */
    private BlockState generateFlowerForBiome(RegistryEntry<Biome> biomeEntry) {
        // 仅受花朵组选项控制
        PushdozerConfig.FlowerGroup selectedFlowerGroup = config.getSelectedFlowerGroup();

        // 生物群系自适应模式
        if (selectedFlowerGroup == PushdozerConfig.FlowerGroup.BIOME_ADAPTIVE) {
            List<Block> flowerBlocks = BiomeVegetationRegistry.getFlowerBlocks(biomeEntry);
            return getRandomBlock(flowerBlocks, this.random);
        }

        // 如果用户选择了特定花朵组，使用对应的花朵列表
        List<Block> flowerBlocks = getFlowerBlocksForGroup(selectedFlowerGroup);
        return getRandomBlock(flowerBlocks, this.random);
    }

    /**
     * 根据用户选择的花朵组获取对应的花朵方块列表
     */
    private List<Block> getFlowerBlocksForGroup(PushdozerConfig.FlowerGroup group) {
        return switch (group) {
            case PLAINS_FLOWERS -> Arrays.asList(
                    Blocks.DANDELION, Blocks.POPPY, Blocks.OXEYE_DAISY, Blocks.CORNFLOWER
            );
            case FOREST_FLOWERS -> Arrays.asList(
                    Blocks.LILY_OF_THE_VALLEY, Blocks.ALLIUM, Blocks.AZURE_BLUET,
                    Blocks.RED_TULIP, Blocks.ORANGE_TULIP, Blocks.WHITE_TULIP, Blocks.PINK_TULIP
            );
            case ALL_FLOWERS -> Arrays.asList(
                    Blocks.DANDELION, Blocks.POPPY, Blocks.BLUE_ORCHID, Blocks.ALLIUM,
                    Blocks.AZURE_BLUET, Blocks.RED_TULIP, Blocks.ORANGE_TULIP,
                    Blocks.WHITE_TULIP, Blocks.PINK_TULIP, Blocks.OXEYE_DAISY,
                    Blocks.CORNFLOWER, Blocks.LILY_OF_THE_VALLEY, Blocks.SUNFLOWER,
                    Blocks.LILAC, Blocks.ROSE_BUSH, Blocks.PEONY
            );
            case BIOME_ADAPTIVE -> BiomeVegetationRegistry.DEFAULT_FLOWERS; // 这种情况理论上不会发生，但为了安全
        };
    }

    /**
     * 数据驱动：根据标签选择草类型，增加植被多样性
     * 优化版本：使用数据驱动的植被注册表
     * 增强版本：支持尊重生物群系设置
     */
    private BlockState generateGrassForBiome(RegistryEntry<Biome> biomeEntry) {
        // 草类无独立选择项，默认按生物群系自适应生成
        List<Block> grassBlocks = BiomeVegetationRegistry.getGrassBlocks(biomeEntry);
        return getRandomBlock(grassBlocks, this.random);
    }

    /**
     * 检查方块是否为农作物
     */
    private boolean isCropBlock(Block block) {
        return block == Blocks.WHEAT ||
                block == Blocks.CARROTS ||
                block == Blocks.POTATOES ||
                block == Blocks.BEETROOTS ||
                block == Blocks.PUMPKIN_STEM ||
                block == Blocks.MELON_STEM;
    }

    /**
     * 从方块列表中随机选择一个方块
     */
    private BlockState getRandomBlock(List<Block> blocks, Random random) {
        if (blocks.isEmpty()) {
            return null;
        }
        Block selectedBlock = blocks.get(random.nextInt(blocks.size()));
        return selectedBlock.getDefaultState();
    }

    /**
     * 根据密度设置方块的覆盖程度
     * 支持粉红色花簇、野花簇、枯叶等有覆盖度属性的方块
     */
    private BlockState setCoverageBasedOnDensity(BlockState state, Block block) {
        String blockId = block.toString().toLowerCase();
        
        // 记录调试信息，查看方块的实际名称
        PushdozerMod.LOGGER.debug("Checking coverage for block: {} (blockId: {})", block, blockId);
        
        // 检查是否为需要设置覆盖度的方块
        boolean isCoverageBlock = blockId.contains("pink_petals") || 
                                 blockId.contains("wildflowers") || 
                                 blockId.contains("leaf_litter");
        
        if (!isCoverageBlock) {
            return state;
        }

        // 获取当前密度设置
        double density = Math.max(0.0, Math.min(1.0, config.getPlantDensity()));
        
        // 根据密度确定覆盖程度
        int coverageLevel;
        if (density <= 0.25) {
            // 密度低：1/4覆盖
            coverageLevel = 1;
        } else if (density <= 0.5) {
            // 密度中等：1/2覆盖
            coverageLevel = 2;
        } else if (density <= 0.75) {
            // 密度较高：3/4覆盖
            coverageLevel = 3;
        } else {
            // 密度高：全部覆盖
            coverageLevel = 4;
        }

        // 添加一些随机性，让覆盖度不完全固定
        if (random.nextFloat() < 0.3f) {
            // 30%概率随机调整覆盖度
            coverageLevel = Math.max(1, Math.min(4, coverageLevel + (random.nextBoolean() ? 1 : -1)));
        }

        // 记录所有可用的属性
        PushdozerMod.LOGGER.debug("Block {} has properties: {}", block, state.getProperties());
        for (net.minecraft.state.property.Property<?> property : state.getProperties()) {
            PushdozerMod.LOGGER.debug("Property: {} = {}", property.getName(), state.get(property));
        }
        
        // 检查所有可能的属性名称
        PushdozerMod.LOGGER.debug("Checking all possible properties for block: {}", block);
        if (state.contains(Properties.FLOWER_AMOUNT)) {
            PushdozerMod.LOGGER.debug("Found FLOWER_AMOUNT property");
        }
        // 检查其他可能的属性名称
        for (net.minecraft.state.property.Property<?> property : state.getProperties()) {
            String propName = property.getName().toLowerCase();
            if (propName.contains("leaves") || propName.contains("amount") || propName.contains("flower")) {
                PushdozerMod.LOGGER.debug("Found relevant property: {} = {}", property.getName(), state.get(property));
            }
        }

        // 尝试设置不同的属性名称
        // 粉红色花簇使用 FLOWER_AMOUNT
        if (blockId.contains("pink_petals") && state.contains(Properties.FLOWER_AMOUNT)) {
            BlockState newState = state.with(Properties.FLOWER_AMOUNT, coverageLevel);
            PushdozerMod.LOGGER.debug("Set FLOWER_AMOUNT for pink_petals: density={}, coverageLevel={}", 
                    density, coverageLevel);
            return newState;
        }
        
        // 野花簇可能使用不同的属性名称
        if (blockId.contains("wildflowers")) {
            // 尝试常见的属性名称
            if (state.contains(Properties.FLOWER_AMOUNT)) {
                BlockState newState = state.with(Properties.FLOWER_AMOUNT, coverageLevel);
                PushdozerMod.LOGGER.debug("Set FLOWER_AMOUNT for wildflowers: density={}, coverageLevel={}", 
                        density, coverageLevel);
                return newState;
            }
            // 如果野花簇使用其他属性名称，可以在这里添加
        }
        
        // Leaf Litter：使用 segment_amount 属性设置覆盖度（Java 版）
        if (blockId.contains("leaf_litter")) {
            // 尝试查找 segment_amount 属性（可能不在标准 Properties 类中）
            for (net.minecraft.state.property.Property<?> property : state.getProperties()) {
                String propName = property.getName().toLowerCase();
                if (propName.equals("segment_amount") || propName.equals("segmentamount")) {
                    try {
                        @SuppressWarnings("unchecked")
                        net.minecraft.state.property.Property<Integer> intProperty = (net.minecraft.state.property.Property<Integer>) property;
                        // 获取属性的有效值范围
                        Collection<Integer> validValues = intProperty.getValues();
                        int maxValue = validValues.stream().mapToInt(Integer::intValue).max().orElse(4);
                        int validCoverageLevel = Math.max(1, Math.min(maxValue, coverageLevel));
                        BlockState newState = state.with(intProperty, validCoverageLevel);
                        PushdozerMod.LOGGER.debug("Set {} for leaf_litter: density={}, coverageLevel={}, validLevel={}, maxValue={}", 
                                property.getName(), density, coverageLevel, validCoverageLevel, maxValue);
                        return newState;
                    } catch (ClassCastException e) {
                        PushdozerMod.LOGGER.debug("Property {} is not an integer property for leaf_litter", property.getName());
                    }
                }
            }
            
            // 如果没有找到 segment_amount 属性，尝试其他可能的属性名称
            for (net.minecraft.state.property.Property<?> property : state.getProperties()) {
                String propName = property.getName().toLowerCase();
                if (propName.contains("amount") || propName.contains("growth")) {
                    try {
                        @SuppressWarnings("unchecked")
                        net.minecraft.state.property.Property<Integer> intProperty = (net.minecraft.state.property.Property<Integer>) property;
                        // 获取属性的有效值范围
                        Collection<Integer> validValues = intProperty.getValues();
                        int maxValue = validValues.stream().mapToInt(Integer::intValue).max().orElse(4);
                        int validCoverageLevel = Math.max(1, Math.min(maxValue, coverageLevel));
                        BlockState newState = state.with(intProperty, validCoverageLevel);
                        PushdozerMod.LOGGER.debug("Set {} for leaf_litter: density={}, coverageLevel={}, validLevel={}, maxValue={}", 
                                property.getName(), density, coverageLevel, validCoverageLevel, maxValue);
                        return newState;
                    } catch (ClassCastException e) {
                        PushdozerMod.LOGGER.debug("Property {} is not an integer property for leaf_litter", property.getName());
                    }
                }
            }
            
            // 如果没有找到合适的属性，记录警告信息
            PushdozerMod.LOGGER.warn("Leaf_litter at {} does not have segment_amount property. Available properties: {}", 
                    block, state.getProperties().stream().map(net.minecraft.state.property.Property::getName).toList());
        }
        
        // 如果没有找到匹配的属性，记录详细的调试信息
        PushdozerMod.LOGGER.debug("Coverage block {} detected but no matching property found. Available properties: {}", 
                block, state.getProperties());

        return state;
    }

    /**
     * 种植位置数据类
     */
    private static class PlantingPosition {
        final BlockPos position;
        final PushdozerConfig.PlantType plantType;

        PlantingPosition(BlockPos position, PushdozerConfig.PlantType plantType) {
            this.position = position;
            this.plantType = plantType;
        }
    }

    /**
     * 树生成结果数据类
     */
    private static class TreeGenerationResult {
        final List<BlockPos> affectedPositions = new ArrayList<>();
        final List<BlockState> originalStates = new ArrayList<>();
        final List<BlockState> newStates = new ArrayList<>();

        void addChange(BlockPos pos, BlockState original, BlockState newState) {
            affectedPositions.add(pos);
            originalStates.add(original);
            newStates.add(newState);
        }

        boolean isEmpty() {
            return affectedPositions.isEmpty();
        }
    }

    /**
     * 批量种植结果数据类
     * 统一管理所有种植操作的撤销数据
     */
    private static class BatchPlantingResult {
        private final List<BlockPos> allPositions = new ArrayList<>();
        private final List<BlockState> allOriginalStates = new ArrayList<>();
        private final List<BlockState> allNewStates = new ArrayList<>();
        private int simplePlantCount = 0;
        private int treeCount = 0;

        void addSimplePlant(BlockPos pos, BlockState original, BlockState newState) {
            allPositions.add(pos);
            allOriginalStates.add(original);
            allNewStates.add(newState);
            simplePlantCount++;
        }

        void addTreeBlock(BlockPos pos, BlockState original, BlockState newState) {
            allPositions.add(pos);
            allOriginalStates.add(original);
            allNewStates.add(newState);
        }

        void incrementTreeCount() {
            treeCount++;
        }

        List<BlockPos> getAllPositions() {
            return allPositions;
        }

        List<BlockState> getAllOriginalStates() {
            return allOriginalStates;
        }

        List<BlockState> getAllNewStates() {
            return allNewStates;
        }

        boolean isEmpty() {
            return allPositions.isEmpty();
        }

        int getTotalCount() {
            return simplePlantCount + treeCount;
        }

        int getSimplePlantCount() {
            return simplePlantCount;
        }

        int getTreeCount() {
            return treeCount;
        }
    }
}