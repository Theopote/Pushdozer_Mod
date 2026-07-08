package com.pushdozer.items.handlers.planting;

import com.pushdozer.PushdozerMod;
import com.pushdozer.config.PushdozerConfig;
import com.pushdozer.items.handlers.planting.model.BatchPlantingResult;
import com.pushdozer.items.handlers.planting.model.PlantingPosition;
import com.pushdozer.items.handlers.vegetation.PlantBlockClassifier;
import com.pushdozer.items.handlers.vegetation.PlantPlacementValidator;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.CoralWallFanBlock;
import net.minecraft.block.TallPlantBlock;
import net.minecraft.block.enums.DoubleBlockHalf;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.state.property.Properties;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.World;
import net.minecraft.world.biome.Biome;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;

public class SimplePlantProcessor {
    private final PushdozerConfig config;
    private final Random random;

    public SimplePlantProcessor(PushdozerConfig config, Random random) {
        this.config = config;
        this.random = random;
    }

    public void process(ServerWorld world, List<PlantingPosition> positions, BatchPlantingResult result) {
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
            if (PlantBlockClassifier.hasExistingPlantOrDecoration(originalStateLower.getBlock())) {
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
                boolean allowedUnderwater = PlantBlockClassifier.isLiveCoral(block) || PlantBlockClassifier.isAquatic(block);
                boolean isLilyPad = block == Blocks.LILY_PAD;
                boolean isDripleaf = block == Blocks.SMALL_DRIPLEAF || block == Blocks.BIG_DRIPLEAF;
                if (!allowedUnderwater && !isLilyPad && !isDripleaf) {
                    continue;
                }
            }

            // 统一处理珊瑚（活珊瑚和失活珊瑚）
            if (pos.plantType == PushdozerConfig.PlantType.CUSTOM && (PlantBlockClassifier.isLiveCoral(newState.getBlock()) || PlantBlockClassifier.isDeadCoral(newState.getBlock()))) {
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
            if (pos.plantType == PushdozerConfig.PlantType.CUSTOM && PlantBlockClassifier.isPotted(newState.getBlock())) {
                // 检查是否在水中
                if (originalStateLower.getFluidState().isIn(FluidTags.WATER)) {
                    continue;
                }

                // 确保花盆可放置
                if (!newState.canPlaceAt(world, basePos)) {
                    continue;
                }

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
                    result.addSimplePlant(upperPos, originalStateUpper, upper);
                    result.addSimplePlant(basePos, originalStateLower, lower);

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
                    result.addSimplePlant(upperPos, originalStateUpper, upper);
                    result.addSimplePlant(basePos, originalStateLower, lower);

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

                result.addSimplePlant(basePos, originalStateLower, lower);
                result.addSimplePlant(upperPos, originalStateUpper, upper);
                continue;
            }

            if (!PlantBlockClassifier.isDeadCoral(newState.getBlock()) && !newState.canPlaceAt(world, basePos)) {
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
        boolean isLive = PlantBlockClassifier.isLiveCoral(block);
        boolean isDead = PlantBlockClassifier.isDeadCoral(block);

        if (!isLive && !isDead) {
            return false; // 不是珊瑚，交给其他处理逻辑
        }

        // 检查当前位置是否已有植物或装饰物，如果有则跳过
        if (PlantBlockClassifier.hasExistingPlantOrDecoration(originalState.getBlock())) {
            PushdozerMod.LOGGER.debug("Skipping coral placement at {}: existing plant/decoration detected", basePos);
            return false;
        }

        // 活珊瑚：必须在水中或与水相邻
        if (isLive) {
            // 检查是否在水中或与水相邻
            boolean inWater = originalState.getFluidState().isIn(FluidTags.WATER)
                    || world.getFluidState(basePos).isIn(FluidTags.WATER);
            boolean adjacentToWater = PlantPlacementValidator.isAdjacentToWater(world, basePos);

            if (!inWater && !adjacentToWater) {
                return false;
            }

            // 珊瑚扇需要检查上方是否也在水中
            if (PlantBlockClassifier.isLiveCoralFan(block)) {
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

        result.addSimplePlant(basePos, originalState, toPlace);
        return true;

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
                .filter(block -> PlantPlacementValidator.canPlantCustomBlockAt(world, pos, block))
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

        // 为需要随机方向的方块设置朝向
        if (state.contains(Properties.HORIZONTAL_FACING)) {
            String blockName = chosen.toString().toLowerCase();
            if (blockName.contains("leaf_litter") || blockName.contains("pink_petals") || blockName.contains("wildflowers")) {
                Direction[] directions = Direction.Type.HORIZONTAL.stream().toArray(Direction[]::new);
                Direction randomDirection = directions[random.nextInt(directions.length)];
                state = state.with(Properties.HORIZONTAL_FACING, randomDirection);
            }
        }

        // 小型垂滴叶：不在这里设置 WATERLOGGED，让放置逻辑处理

        return state;
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

            // 为粉红色花簇添加随机方向
            if (newState.contains(Properties.HORIZONTAL_FACING)) {
                Direction[] directions = Direction.Type.HORIZONTAL.stream().toArray(Direction[]::new);
                Direction randomDirection = directions[random.nextInt(directions.length)];
                newState = newState.with(Properties.HORIZONTAL_FACING, randomDirection);
            }

            PushdozerMod.LOGGER.debug("Set FLOWER_AMOUNT for pink_petals: density={}, coverageLevel={}",
                    density, coverageLevel);
            return newState;
        }

        // 野花簇可能使用不同的属性名称
        if (blockId.contains("wildflowers")) {
            // 尝试常见的属性名称
            if (state.contains(Properties.FLOWER_AMOUNT)) {
                BlockState newState = state.with(Properties.FLOWER_AMOUNT, coverageLevel);

                // 为野花簇添加随机方向
                if (newState.contains(Properties.HORIZONTAL_FACING)) {
                    Direction[] directions = Direction.Type.HORIZONTAL.stream().toArray(Direction[]::new);
                    Direction randomDirection = directions[random.nextInt(directions.length)];
                    newState = newState.with(Properties.HORIZONTAL_FACING, randomDirection);
                }

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

                        // 为 Leaf Litter 添加随机方向
                        if (newState.contains(Properties.HORIZONTAL_FACING)) {
                            Direction[] directions = Direction.Type.HORIZONTAL.stream().toArray(Direction[]::new);
                            Direction randomDirection = directions[random.nextInt(directions.length)];
                            newState = newState.with(Properties.HORIZONTAL_FACING, randomDirection);
                        }

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

                        // 为 Leaf Litter 添加随机方向
                        if (newState.contains(Properties.HORIZONTAL_FACING)) {
                            Direction[] directions = Direction.Type.HORIZONTAL.stream().toArray(Direction[]::new);
                            Direction randomDirection = directions[random.nextInt(directions.length)];
                            newState = newState.with(Properties.HORIZONTAL_FACING, randomDirection);
                        }

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
}
