package com.pushdozer.items.handlers.shoreline;

import com.pushdozer.PushdozerMod;
import com.pushdozer.config.PushdozerConfig;
import com.pushdozer.items.handlers.shoreline.model.VegetationPlacement;
import com.pushdozer.items.handlers.vegetation.PlantBlockClassifier;
import com.pushdozer.items.handlers.vegetation.PlantPlacementValidator;
import net.minecraft.block.*;
import net.minecraft.block.enums.DoubleBlockHalf;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.state.property.Properties;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.World;
import net.minecraft.world.biome.Biome;

import java.util.ArrayList;
import java.util.List;

public class ShorelineVegetationPlanner {
    private static final int MAX_PLANTS = 500;
    private static final int MAX_PLANT_ATTEMPTS = 1000;

    private final PushdozerConfig config;
    private final ShorelineEdgeFinder edgeFinder;
    private final ShorelineTransitionPlanner transitionPlanner;

    public ShorelineVegetationPlanner(PushdozerConfig config, ShorelineEdgeFinder edgeFinder, ShorelineTransitionPlanner transitionPlanner) {
        this.config = config;
        this.edgeFinder = edgeFinder;
        this.transitionPlanner = transitionPlanner;
    }
    public List<VegetationPlacement> collectVegetationPositions(World world, List<BlockPos> positions) {
        List<VegetationPlacement> placements = new ArrayList<>();
        
        PushdozerMod.LOGGER.debug("collectVegetationPositions: processing {} positions, plantVegetationEnabled = {}", 
            positions.size(), config.isPlantVegetationEnabled());
        
        for (BlockPos pos : positions) {
            // 检查是否启用植物种植
            if (!config.isPlantVegetationEnabled()) {
                PushdozerMod.LOGGER.debug("Plant vegetation is disabled, skipping position {}", pos);
                continue;
            }
            
            // 检查下方方块是否为实体方块（不能是水方块）
            BlockState belowState = world.getBlockState(pos);
            if (!PlantPlacementValidator.isSolidBlock(world, pos, belowState)) {
                PushdozerMod.LOGGER.debug("Position {} has non-solid block below: {}, skipping", pos, belowState.getBlock().toString());
                continue;
            }
            
            BlockState plant = getVegetationForBiome(world, pos, world.getBiome(pos).value(), belowState);
            PushdozerMod.LOGGER.debug("getVegetationForBiome returned: {} for position {}", 
                plant != null ? plant.getBlock().toString() : "null", pos);
            
            if (plant != null && canPlantGrowOnBlock(world, pos, plant.getBlock(), belowState)) {
                // 优化：检查高植物种植条件
                int requiredHeight = getPlantRequiredHeight(world, plant.getBlock());
                if (PlantPlacementValidator.hasEnoughSpaceForPlant(world, pos, requiredHeight)) {
                    boolean isTallPlant = plant.getBlock() instanceof TallPlantBlock;
                    placements.add(new VegetationPlacement(pos, plant, isTallPlant, belowState));
                    // 调试：记录植物选择
                    PushdozerMod.LOGGER.debug("Successfully added plant {} for position {} on ground {}", 
                        plant.getBlock().toString(), pos, belowState.getBlock().toString());
                } else {
                    PushdozerMod.LOGGER.debug("Not enough space for plant {} at position {}", plant.getBlock().toString(), pos);
                }
            } else if (plant != null) {
                PushdozerMod.LOGGER.debug("Plant {} cannot grow on block {} at position {}", 
                    plant.getBlock().toString(), belowState.getBlock().toString(), pos);
            }
        }
        
        PushdozerMod.LOGGER.debug("collectVegetationPositions: collected {} placements", placements.size());
        return placements;
    }
    
    /**
     * 判断是否应该在此位置种植植物
     * 优化：雪地直接返回false，不种植植物
     * 逻辑优化：添加密度衰减公式，创造更自然的分布
     * 修复：添加标高限制检查
     */
    public boolean shouldPlantVegetation(World world, BlockPos pos, int distance, PlayerEntity player) {
        // 检查是否启用植物种植
        if (!config.isPlantVegetationEnabled()) return false;
        
        // 扩大植物种植范围：距离水1-4格的位置都可以种植植物
        if (distance < 1 || distance > 8) return false;
        
        // 修复：添加标高限制检查
        if (!transitionPlanner.isValidHeightForShorelineProcess(pos, player)) {
            return false;
        }
        
        // 检查上方是否有空间
        BlockPos above = pos.up();
        if (!world.getBlockState(above).isAir()) return false;
        
        // 优化：雪地直接返回false，不种植植物
        var biomeEntry = world.getBiome(pos);
        String biomeName = biomeEntry.value().toString().toLowerCase();
        if (biomeName.contains("snowy") || biomeName.contains("ice")) {
            return false; // 雪地不种植植物
        }
        
        // 基础密度
        float density = config.getVegetationDensity();
        
        // 逻辑优化：添加密度衰减公式，但保持最小密度
        float distanceFactor = Math.max(0.3f, 1.0f - (distance - 1) / (float)config.getShorelineWidth());
        float adjustedDensity = density * distanceFactor;
        
        // 根据生物群系调整密度
        if (biomeName.contains("desert")) {
            adjustedDensity *= 1.2f; // 沙漠中仙人掌较多
        } else if (biomeName.contains("swamp") || biomeName.contains("river")) {
            adjustedDensity *= 1.8f; // 沼泽和河流中植物更密集
        } else if (biomeName.contains("forest") || biomeName.contains("jungle")) {
            adjustedDensity *= 1.3f; // 森林中植物较多
        } else if (biomeName.contains("savanna") || biomeName.contains("plains")) {
            adjustedDensity *= 1.1f; // 热带草原和平原中植物较多
        } else if (biomeName.contains("mountain") || biomeName.contains("hill")) {
            adjustedDensity *= 1.4f; // 山地中植物较多（苔藓、花朵等）
        } else if (biomeName.contains("badlands")) {
            adjustedDensity *= 0.8f; // 坏地中植物较稀疏
        }
        
        // 检查邻居是否有植物，增加集群效果
        // 优化：同时检查水平和垂直方向的邻居植物
        int neighborPlants = 0;
        
        // 检查水平方向邻居
        for (Direction dir : Direction.Type.HORIZONTAL) {
            BlockPos neighborPos = pos.offset(dir);
            if (world.getBlockState(neighborPos.up()).getBlock() instanceof PlantBlock ||
                world.getBlockState(neighborPos.up()).getBlock() instanceof TallPlantBlock) {
                neighborPlants++;
            }
        }
        
        // 检查垂直方向邻居（支持垂直水岸的植物种植）
        for (Direction dir : Direction.Type.VERTICAL) {
            BlockPos neighborPos = pos.offset(dir);
            if (world.getBlockState(neighborPos.up()).getBlock() instanceof PlantBlock ||
                world.getBlockState(neighborPos.up()).getBlock() instanceof TallPlantBlock) {
                neighborPlants++;
            }
        }
        
        // 根据邻居植物数量调整密度
        if (neighborPlants >= 2) {
            adjustedDensity *= 1.5f; // 多个邻居有植物，显著增加种植概率
        } else if (neighborPlants == 1) {
            adjustedDensity *= 1.2f; // 一个邻居有植物，适度增加种植概率
        }
        
        return world.getRandom().nextFloat() < adjustedDensity;
    }

    /**
     * 种植植物
     * 优化：添加canPlaceAt检查，确保植物能正确生存
     * 逻辑优化：分离植物收集和种植，添加撤销记录
     * 修复：添加player参数用于标高检查
     * 性能优化：添加植物数量限制，防止性能问题
     */
    public int planVegetation(World world, List<VegetationPlacement> placements,
                               List<BlockPos> affectedPositions, List<BlockState> originalStates, List<BlockState> newStates,
                               PlayerEntity player) {
        int plantedCount = 0;
        int attemptCount = 0;
        
        for (VegetationPlacement placement : placements) {
            // 性能优化：限制最大植物数量和尝试次数
            if (plantedCount >= MAX_PLANTS || attemptCount >= MAX_PLANT_ATTEMPTS) {
                PushdozerMod.LOGGER.debug("Planting stopped: reached limit (planted: {}, attempts: {})", plantedCount, attemptCount);
                break;
            }
            
            attemptCount++;
            BlockPos pos = placement.pos;
            BlockPos plantPos = pos.up();
            
            // 兼容性检查：确保chunk已加载
            if (!edgeFinder.isChunkLoaded(world, pos) || !edgeFinder.isChunkLoaded(world, plantPos)) {
                continue; // 跳过未加载的chunk
            }
            
            // 优化：添加canPlaceAt检查，确保植物能正确生存
            if (!placement.plant.canPlaceAt(world, plantPos)) {
                // 调试：记录植物无法生存的情况
                PushdozerMod.LOGGER.debug("Plant cannot survive at {}: {}", plantPos, placement.plant.getBlock().toString());
                continue; // 植物无法在此位置生存，跳过
            }
            
            // ⭐ 新增：为甘蔗添加水源检查
            if (placement.plant.getBlock() == Blocks.SUGAR_CANE) {
                boolean hasWater = false;
                for (Direction dir : Direction.Type.HORIZONTAL) {
                    if (world.getFluidState(pos.offset(dir)).isIn(FluidTags.WATER)) {
                        hasWater = true;
                        break;
                    }
                }
                if (!hasWater) {
                    continue; // 如果没有水源，不种植甘蔗
                }
            }
            
            if (placement.isTallPlant) {
                // 处理高植物（如芦苇）
                BlockPos upperPos = plantPos.up();
                
                // 修复：为高植物添加标高限制检查
                if (!transitionPlanner.isValidHeightForShorelineProcess(upperPos, player)) {
                    continue; // 高植物上部超出标高限制，跳过
                }
                
                if (world.getBlockState(plantPos).isAir() && 
                    world.getBlockState(upperPos).isAir()) {
                    
                    // 优化：为高植物添加canPlaceAt检查
                    BlockState lowerPlant = placement.plant.with(TallPlantBlock.HALF, DoubleBlockHalf.LOWER);
                    BlockState upperPlant = placement.plant.with(TallPlantBlock.HALF, DoubleBlockHalf.UPPER);
                    
                    if (!lowerPlant.canPlaceAt(world, plantPos) ||
                        !upperPlant.canPlaceAt(world, upperPos)) {
                        continue; // 高植物无法在此位置生存，跳过
                    }
                    
                    // 记录原始状态用于撤销
                    originalStates.add(world.getBlockState(plantPos));
                    originalStates.add(world.getBlockState(upperPos));
                    affectedPositions.add(plantPos);
                    affectedPositions.add(upperPos);

                    newStates.add(lowerPlant);
                    newStates.add(upperPlant);
                    plantedCount++;
                }
            } else {
                // 处理普通植物和高植物（非TallPlantBlock类型）
                int requiredHeight = getPlantRequiredHeight(world, placement.plant.getBlock());
                
                // 检查是否有足够空间
                if (!PlantPlacementValidator.hasEnoughSpaceForPlant(world, plantPos, requiredHeight)) {
                    continue; // 空间不足，跳过
                }
                
                // 检查标高限制
                boolean heightValid = true;
                for (int i = 0; i < requiredHeight; i++) {
                    if (!transitionPlanner.isValidHeightForShorelineProcess(plantPos.up(i), player)) {
                        heightValid = false;
                        break;
                    }
                }
                if (!heightValid) {
                    continue; // 超出标高限制，跳过
                }
                
                // 检查植物生存条件
                if (!placement.plant.canPlaceAt(world, plantPos)) {
                    continue; // 植物无法在此位置生存，跳过
                }
                
                // 记录原始状态用于撤销
                for (int i = 0; i < requiredHeight; i++) {
                    BlockPos checkPos = plantPos.up(i);
                    originalStates.add(world.getBlockState(checkPos));
                    affectedPositions.add(checkPos);
                }
                
                // 放置植物（对于高植物，可能需要特殊处理）
                if (requiredHeight == 1) {
                    // 普通植物
                    BlockState plantToPlace = placement.plant;
                    
                    // 特殊处理：失活珊瑚不应该带水
                    if (PlantBlockClassifier.isDeadCoral(placement.plant.getBlock())) {
                        // 确保失活珊瑚不带有水属性
                        plantToPlace = placement.plant.getBlock().getDefaultState();
                        // 移除任何可能的水属性
                        if (plantToPlace.contains(Properties.WATERLOGGED)) {
                            plantToPlace = plantToPlace.with(Properties.WATERLOGGED, false);
                        }
                    }

                    newStates.add(plantToPlace);
                } else {
                    collectHighPlant(world, plantPos, placement.plant, requiredHeight, newStates);

                    if (placement.plant.getBlock() == Blocks.CACTUS &&
                        config.getShorelineType() == PushdozerConfig.ShorelineType.BEACH) {
                        if (world.getRandom().nextFloat() < 0.3f) {
                            BlockPos flowerPos = plantPos.up(requiredHeight);
                            if (world.getBlockState(flowerPos).isAir()) {
                                BlockState flowerState = Blocks.CACTUS_FLOWER.getDefaultState();
                                newStates.add(flowerState);
                                originalStates.add(world.getBlockState(flowerPos));
                                affectedPositions.add(flowerPos);
                            }
                        }
                    }
                }
                
                plantedCount++;
            }
        }
        
        // 性能监控：记录种植统计
        PushdozerMod.LOGGER.debug("Vegetation planting completed: {} planted, {} attempts, {} placements provided", 
            plantedCount, attemptCount, placements.size());
        
        // 调试：如果没有种植任何植物，记录详细信息
        if (plantedCount == 0 && !placements.isEmpty()) {
            PushdozerMod.LOGGER.warn("No plants were planted despite having {} placements. This might indicate planting condition issues.", placements.size());
        }
        
        return plantedCount;
    }
    
    /**
     * 放置高植物
     * 支持陆地、水中和半水中的高植物种植
     * 优化：使用随机高度，让植物更自然
     * 
     * @param world 世界实例
     * @param pos 种植位置
     * @param plantState 植物状态
     * @param height 植物高度
     * @param newStates 新状态列表（用于撤销）
     */
    private void collectHighPlant(World world, BlockPos pos, BlockState plantState, int height, List<BlockState> newStates) {
        Block plantBlock = plantState.getBlock();
        Random random = world.getRandom();
        
        if (plantBlock == Blocks.SUGAR_CANE) {
            // 甘蔗：使用传入的随机高度，底部有随机年龄
            for (int i = 0; i < height; i++) {
                BlockPos plantPos = pos.up(i);
                BlockState caneState = Blocks.SUGAR_CANE.getDefaultState();
                if (i == 0) {
                    // 底部甘蔗有随机年龄
                    caneState = caneState.with(SugarCaneBlock.AGE, random.nextInt(15));
                }
                newStates.add(caneState);
            }
        } else if (plantBlock == Blocks.BAMBOO) {
            // 竹子：使用传入的随机高度，底部有随机年龄
            for (int i = 0; i < height; i++) {
                BlockPos plantPos = pos.up(i);
                BlockState bambooState = Blocks.BAMBOO.getDefaultState();
                if (i == 0) {
                    // 底部竹子有随机年龄
                    bambooState = bambooState.with(BambooBlock.AGE, random.nextInt(1));
                }
                newStates.add(bambooState);
            }
        } else if (plantBlock == Blocks.CACTUS || plantBlock == Blocks.CACTUS_FLOWER) {
            // 仙人掌：使用传入的随机高度，底部有随机年龄
            for (int i = 0; i < height; i++) {
                BlockPos plantPos = pos.up(i);
                BlockState cactusState;
                if (plantBlock == Blocks.CACTUS_FLOWER) {
                    cactusState = Blocks.CACTUS_FLOWER.getDefaultState();
                } else {
                    cactusState = Blocks.CACTUS.getDefaultState();
                }
                if (i == 0) {
                    // 底部仙人掌有随机年龄
                    cactusState = cactusState.with(CactusBlock.AGE, random.nextInt(15));
                }
                newStates.add(cactusState);
            }
        } else if (plantBlock == Blocks.KELP) {
            // 海带：使用传入的随机高度，每格都有随机生长阶段
            for (int i = 0; i < height; i++) {
                BlockPos plantPos = pos.up(i);
                BlockState kelpState = Blocks.KELP.getDefaultState().with(KelpBlock.AGE, random.nextInt(4));
                newStates.add(kelpState);
            }
        } else if (plantBlock == Blocks.CHORUS_PLANT) {
            // 紫颂植物：使用传入的随机高度
            for (int i = 0; i < height; i++) {
                BlockPos plantPos = pos.up(i);
                BlockState chorusState = Blocks.CHORUS_PLANT.getDefaultState();
                newStates.add(chorusState);
            }
        } else if (plantBlock == Blocks.CHORUS_FLOWER) {
            // 紫颂花：使用传入的随机高度
            for (int i = 0; i < height; i++) {
                BlockPos plantPos = pos.up(i);
                BlockState flowerState = Blocks.CHORUS_FLOWER.getDefaultState();
                newStates.add(flowerState);
            }
        } else {
            newStates.add(plantState);
        }
    }
    
    /**
     * ⭐ 新增：检查植物是否能在特定方块上生长
     * 确保植物种植的兼容性
     * 扩展：支持更多植物类型
     * 修复：添加自定义植物支持，使用更严格的种植条件检查
     * 优化：参考批量种植的种植条件逻辑
     */
    private boolean canPlantGrowOnBlock(World world, BlockPos pos, Block plantBlock, BlockState groundBlock) {
        // 修复：只有在自定义水岸类型时才检查自定义植物
        PushdozerConfig.ShorelineType shorelineType = config.getShorelineType();
        if (shorelineType == PushdozerConfig.ShorelineType.CUSTOM) {
            List<Block> customPlants = config.getCustomShorelinePlantList();
            if (!customPlants.isEmpty()) {
                if (customPlants.contains(plantBlock)) {
                    // 修复：传入种植位置（pos.up()）而不是基底位置（pos）
                    return PlantPlacementValidator.canPlantCustomBlockAt(world, pos.up(), plantBlock);
                } else {
                    // 如果植物不在自定义列表中，直接返回false
                    PushdozerMod.LOGGER.debug("Plant {} is not in custom plants list, rejecting", plantBlock.toString());
                    return false;
                }
            } else {
                // 如果没有配置自定义植物，直接返回false
                PushdozerMod.LOGGER.debug("No custom plants configured, rejecting plant {}", plantBlock.toString());
                return false;
            }
        }
        
        // 检查当前位置是否在水中
        BlockState currentState = world.getBlockState(pos);
        boolean inWater = currentState.getFluidState().isIn(FluidTags.WATER);
        
        // 睡莲：只能种在水面（当前位置为空气，且下方是水）
        if (plantBlock == Blocks.LILY_PAD) {
            boolean airHere = currentState.isAir();
            boolean waterBelow = groundBlock.getFluidState().isIn(FluidTags.WATER);
            return airHere && waterBelow;
        }
        
        // 双高植物要求上方可替换
        if (plantBlock instanceof TallPlantBlock) {
            BlockPos upperPos = pos.up();
            BlockState upperCurrent = world.getBlockState(upperPos);
            if (!(upperCurrent.isAir() || upperCurrent.isReplaceable())) {
                return false;
            }
        }
        
        // 盆栽特例：只能放在实体方块上，不能放在花草植物、盆栽或水面上
        if (PlantBlockClassifier.isPotted(plantBlock)) {
            boolean spotFree = currentState.isAir() || currentState.isReplaceable();
            boolean notInWater = !currentState.getFluidState().isIn(FluidTags.WATER);
            boolean solidBelow = groundBlock.isSolidBlock(world, pos.down());
            boolean notPlantBelow = !PlantBlockClassifier.isPlantBlock(groundBlock.getBlock()) && !PlantBlockClassifier.isPotted(groundBlock.getBlock());
            return spotFree && notInWater && solidBelow && notPlantBelow;
        }
        
        // 农作物（AGE_7 特征或常见作物方块）
        boolean isCrop = PlantBlockClassifier.isCropBlock(plantBlock) || plantBlock.getDefaultState().contains(Properties.AGE_7);
        if (isCrop) {
            return groundBlock.isOf(Blocks.FARMLAND) && (currentState.isAir() || currentState.isReplaceable());
        }
        
        // 活珊瑚：必须在水中或与水相邻，且下方有合适的基底
        boolean adjacentToWater = PlantPlacementValidator.isAdjacentToWater(world, pos);
        
        if (PlantBlockClassifier.isLiveCoral(plantBlock)) {
            // 检查上方一格是否也在水中（确保不是水面）
            BlockPos upperPos = pos.up();
            BlockState upperState = world.getBlockState(upperPos);
            boolean upperInWater = upperState.getFluidState().isIn(FluidTags.WATER);
            
            // 检查下方是否有合适的基底
            boolean validBase = groundBlock.isIn(BlockTags.SAND) ||
                    groundBlock.isIn(BlockTags.CORAL_BLOCKS) ||
                    groundBlock.isIn(BlockTags.BASE_STONE_OVERWORLD) ||
                    groundBlock.isIn(BlockTags.BASE_STONE_NETHER) ||
                    groundBlock.isOf(Blocks.END_STONE) ||
                    groundBlock.isOf(Blocks.GRAVEL) ||
                    groundBlock.isOf(Blocks.CLAY);
            
            // 墙面珊瑚扇需要固体侧面
            if (plantBlock instanceof CoralWallFanBlock) {
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
        if (plantBlock == Blocks.SMALL_DRIPLEAF) {
            // 检查下方方块是否适合种植小型垂滴叶
            boolean validBase = groundBlock.isIn(BlockTags.SMALL_DRIPLEAF_PLACEABLE) ||
                    groundBlock.isOf(Blocks.CLAY) ||
                    groundBlock.isOf(Blocks.MOSS_BLOCK) ||
                    groundBlock.isOf(Blocks.ROOTED_DIRT) ||
                    groundBlock.isOf(Blocks.MYCELIUM) ||
                    groundBlock.isOf(Blocks.PODZOL) ||
                    // 水下种植的方块
                    (inWater && (groundBlock.isOf(Blocks.DIRT) ||
                            groundBlock.isOf(Blocks.COARSE_DIRT) ||
                            groundBlock.isOf(Blocks.GRASS_BLOCK) ||
                            groundBlock.isOf(Blocks.FARMLAND)));
            
            // 检查上方一格是否可替换（双高植物需要）
            BlockPos upperPos = pos.up();
            BlockState upperState = world.getBlockState(upperPos);
            boolean upperReplaceable = upperState.isAir() || upperState.isReplaceable();
            
            return validBase && upperReplaceable;
        }
        
        // 大型垂滴叶：两栖植物，可以在水中或地面上种植
        if (plantBlock == Blocks.BIG_DRIPLEAF) {
            // 检查下方方块是否适合种植大型垂滴叶
            boolean validBase = groundBlock.isOf(Blocks.CLAY) ||
                    groundBlock.isOf(Blocks.MOSS_BLOCK) ||
                    groundBlock.isOf(Blocks.GRASS_BLOCK) ||
                    groundBlock.isOf(Blocks.MYCELIUM) ||
                    groundBlock.isOf(Blocks.PODZOL) ||
                    groundBlock.isOf(Blocks.DIRT) ||
                    groundBlock.isOf(Blocks.ROOTED_DIRT) ||
                    groundBlock.isOf(Blocks.COARSE_DIRT) ||
                    groundBlock.isOf(Blocks.FARMLAND) ||
                    groundBlock.isOf(Blocks.MUD) ||
                    groundBlock.isOf(Blocks.MUDDY_MANGROVE_ROOTS);
            
            // 检查上方一格是否可替换（双高植物需要）
            BlockPos upperPos = pos.up();
            BlockState upperState = world.getBlockState(upperPos);
            boolean upperReplaceable = upperState.isAir() || upperState.isReplaceable();
            
            return validBase && upperReplaceable;
        }
        
        // 其他水生（如海草/高海草/海带/海带植株/海泡菜）：要求在水中，并满足 canPlaceAt
        if (PlantBlockClassifier.isAquatic(plantBlock)) {
            return inWater;
        }
        
        // 失活珊瑚：允许放在任何实体方块上（当前位置需为空气/可替换），忽略具体基底类型
        if (PlantBlockClassifier.isDeadCoral(plantBlock)) {
            boolean spotFree = currentState.isAir() || currentState.isReplaceable();
            boolean solidBelow = groundBlock.isSolidBlock(world, pos.down());
            return spotFree && solidBelow;
        }
        
        // Leaf Litter：可以放置在任何具有完整固体顶面的方块上
        if (plantBlock.toString().toLowerCase().contains("leaf_litter")) {
            boolean spotFree = currentState.isAir() || currentState.isReplaceable();
            boolean validBase = groundBlock.isSolidBlock(world, pos.down());
            return spotFree && validBase;
        }
        
        // 仙人掌：只能在沙子上生长
        if (plantBlock == Blocks.CACTUS || plantBlock == Blocks.CACTUS_FLOWER) {
            return groundBlock.isIn(BlockTags.SAND);
        }
        
        // 甘蔗：可以在沙子、泥土、草方块上生长
        if (plantBlock == Blocks.SUGAR_CANE) {
            return groundBlock.isIn(BlockTags.SAND) || 
                   groundBlock.isIn(BlockTags.DIRT) || 
                   groundBlock.getBlock() == Blocks.GRASS_BLOCK;
        }
        
        // 草丛：只能在泥土或草方块上生长
        if (plantBlock == Blocks.SHORT_GRASS || plantBlock == Blocks.TALL_GRASS) {
            return groundBlock.isIn(BlockTags.DIRT) || groundBlock.getBlock() == Blocks.GRASS_BLOCK;
        }
        
        // 蕨类：可以在泥土、草方块、石头（某些情况下）上生长
        if (plantBlock == Blocks.FERN || plantBlock == Blocks.LARGE_FERN) {
            return groundBlock.isIn(BlockTags.DIRT) || 
                   groundBlock.getBlock() == Blocks.GRASS_BLOCK ||
                   groundBlock.getBlock() == Blocks.STONE ||
                   groundBlock.getBlock() == Blocks.ANDESITE ||
                   groundBlock.getBlock() == Blocks.DIORITE ||
                   groundBlock.getBlock() == Blocks.GRANITE;
        }
        
        // 枯死的灌木：可以在沙子上生长
        if (plantBlock == Blocks.DEAD_BUSH) {
            return groundBlock.isIn(BlockTags.SAND);
        }
        
        // 海草：可以在沙子、泥土上生长（水下）
        if (plantBlock == Blocks.SEAGRASS) {
            return groundBlock.isIn(BlockTags.SAND) || groundBlock.isIn(BlockTags.DIRT);
        }
        
        // 海带：可以在沙子、泥土上生长（水下）
        if (plantBlock == Blocks.KELP) {
            return groundBlock.isIn(BlockTags.SAND) || groundBlock.isIn(BlockTags.DIRT);
        }
        
        // 竹子：可以在沙子、泥土、草方块上生长
        if (plantBlock == Blocks.BAMBOO) {
            return groundBlock.isIn(BlockTags.SAND) || 
                   groundBlock.isIn(BlockTags.DIRT) || 
                   groundBlock.getBlock() == Blocks.GRASS_BLOCK;
        }
        
        // 紫颂植物：可以在末地石上生长
        if (plantBlock == Blocks.CHORUS_PLANT || plantBlock == Blocks.CHORUS_FLOWER) {
            return groundBlock.getBlock() == Blocks.END_STONE;
        }
        
        // 花朵：可以在泥土、草方块、沙子（某些花朵）上生长
        if (plantBlock == Blocks.DANDELION || plantBlock == Blocks.POPPY || 
            plantBlock == Blocks.BLUE_ORCHID || plantBlock == Blocks.ALLIUM ||
            plantBlock == Blocks.AZURE_BLUET || plantBlock == Blocks.RED_TULIP ||
            plantBlock == Blocks.ORANGE_TULIP || plantBlock == Blocks.WHITE_TULIP ||
            plantBlock == Blocks.PINK_TULIP || plantBlock == Blocks.OXEYE_DAISY ||
            plantBlock == Blocks.CORNFLOWER || plantBlock == Blocks.LILY_OF_THE_VALLEY) {
            return groundBlock.isIn(BlockTags.DIRT) || 
                   groundBlock.getBlock() == Blocks.GRASS_BLOCK ||
                   groundBlock.isIn(BlockTags.SAND);
        }
        
        // 普通：放宽基底，草/泥土/沙/雪顶/泥土路等，且当前位置可替换或空气
        boolean validBase = groundBlock.isIn(BlockTags.DIRT)
                || groundBlock.isOf(Blocks.GRASS_BLOCK)
                || groundBlock.isIn(BlockTags.SAND)
                || groundBlock.isIn(BlockTags.SNOW)
                || groundBlock.isOf(Blocks.DIRT_PATH)
                || groundBlock.isOf(Blocks.MOSS_BLOCK)
                || groundBlock.isOf(Blocks.CLAY);
        if (!validBase) return false;
        return (currentState.isAir() || currentState.isReplaceable());
    }

    /**
     * 检查方块是否为表面方块
     * 表面方块是指与空气或水接触的方块，这些方块是可见的
     * 优化：忽略植物，确保水岸类型切换时能替换植物下方的方块
     * 
     * @param world 世界实例
     * @param pos 方块位置
     * @return 如果方块是表面方块返回true，否则返回false
     */
    private boolean isSurfaceBlock(World world, BlockPos pos) {
        // 检查上方是否为空气、水或植物
        BlockPos above = pos.up();
        BlockState aboveState = world.getBlockState(above);
        if (aboveState.isAir() || aboveState.getFluidState().isIn(FluidTags.WATER) || PlantBlockClassifier.isPlantOrDecoration(aboveState)) {
            return true;
        }
        
        // 检查水平方向是否有空气、水或植物
        for (Direction dir : Direction.Type.HORIZONTAL) {
            BlockPos neighborPos = pos.offset(dir);
            BlockState neighborState = world.getBlockState(neighborPos);
            if (neighborState.isAir() || neighborState.getFluidState().isIn(FluidTags.WATER) || PlantBlockClassifier.isPlantOrDecoration(neighborState)) {
                return true;
            }
        }
        
        // 检查下方是否为空气或水（处理悬空方块）
        BlockPos below = pos.down();
        BlockState belowState = world.getBlockState(below);
        return belowState.isAir() || belowState.getFluidState().isIn(FluidTags.WATER);
        
        // 如果周围都是实体方块（非空气、非水、非植物），则不是表面方块
    }

    /**
     * 获取植物所需的高度
     * 支持陆地、水中和半水中的高植物
     * 优化：添加随机高度支持，让植物更自然
     * 
     * @param world 世界实例（用于随机数生成）
     * @param plantBlock 植物方块
     * @return 植物所需的高度（格数）
     */
    private int getPlantRequiredHeight(World world, Block plantBlock) {
        Random random = world.getRandom();
        
        // 高植物（两格以上）
        if (plantBlock == Blocks.SUGAR_CANE) {
            // 甘蔗：1-3格高，随机
            return 1 + random.nextInt(3);
        } else if (plantBlock == Blocks.BAMBOO) {
            // 竹子：2-5格高，随机
            return 2 + random.nextInt(4);
        } else if (plantBlock == Blocks.CACTUS || plantBlock == Blocks.CACTUS_FLOWER) {
            // 仙人掌：1-3格高，随机
            return 1 + random.nextInt(3);
        } else if (plantBlock == Blocks.KELP) {
            // 海带：3-7格高，随机
            return 3 + random.nextInt(5);
        } else if (plantBlock == Blocks.LARGE_FERN) {
            // 大型蕨类：固定2格高
            return 2;
        } else if (plantBlock == Blocks.SUNFLOWER) {
            // 向日葵：固定2格高
            return 2;
        } else if (plantBlock == Blocks.LILAC) {
            // 丁香：固定2格高
            return 2;
        } else if (plantBlock == Blocks.ROSE_BUSH) {
            // 玫瑰丛：固定2格高
            return 2;
        } else if (plantBlock == Blocks.PEONY) {
            // 牡丹：固定2格高
            return 2;
        } else if (plantBlock == Blocks.CHORUS_PLANT) {
            // 紫颂植物：2-5格高，随机
            return 2 + random.nextInt(4);
        } else if (plantBlock == Blocks.CHORUS_FLOWER) {
            // 紫颂花：2-5格高，随机
            return 2 + random.nextInt(4);
        }
        
        // 默认：普通植物1格高
        return 1;
    }

    /**
     * 根据生物群系和方块类型获取适合的植物
     * 扩展：增加更多植物种类，创造更丰富的植被
     * 修复：添加自定义植物支持，确保自定义植物能被正确种植
     * 优化：为每种水岸类型创建专门的植物预设
     * 建议：未来可添加config.getXXXPlantList()配置，允许用户自定义植物池
     */
    private BlockState getVegetationForBiome(World world, BlockPos pos, Biome biome, BlockState groundBlock) {
        // 修复：只有在自定义水岸类型时才使用自定义植物
        PushdozerConfig.ShorelineType shorelineType = config.getShorelineType();
        PushdozerMod.LOGGER.debug("getVegetationForBiome: shorelineType = {}, pos = {}", shorelineType, pos);
        
        if (shorelineType == PushdozerConfig.ShorelineType.CUSTOM) {
            List<Block> customPlants = config.getCustomShorelinePlantList();
            PushdozerMod.LOGGER.debug("Custom shoreline type: customPlants size = {}", customPlants.size());
            
            if (!customPlants.isEmpty()) {
                // 从自定义植物中找出适合种植条件的植物
                Block selectedPlant = getRandomSuitableCustomPlant(world, pos, customPlants, groundBlock);
                PushdozerMod.LOGGER.debug("Selected custom plant: {}", selectedPlant != null ? selectedPlant.toString() : "null");
                
                if (selectedPlant != null) {
                    BlockState plantState = selectedPlant.getDefaultState();
                    
                    // 特殊处理：失活珊瑚不应该带水
                    if (PlantBlockClassifier.isDeadCoral(selectedPlant)) {
                        // 确保失活珊瑚不带有水属性
                        if (plantState.contains(Properties.WATERLOGGED)) {
                            plantState = plantState.with(Properties.WATERLOGGED, false);
                        }
                    }
                    
                    return plantState;
                }
                
                // 如果自定义植物都不适合，返回null（不种植任何植物）
                PushdozerMod.LOGGER.debug("No suitable custom plants found for current conditions");
            } else {
                PushdozerMod.LOGGER.debug("Custom shoreline type but no custom plants configured!");
                // 如果没有配置自定义植物，返回null（不种植任何植物）
            }
            return null;
        }
        
        // 优化：根据水岸类型选择专门的植物预设
        return switch (shorelineType) {
            case BEACH -> getBeachVegetation(world, pos, biome, groundBlock);
            case EMBANKMENT -> getEmbankmentVegetation(world, pos, biome, groundBlock);
            case MUDDY -> getMuddyVegetation(world, pos, biome, groundBlock);
            case ROCKY -> getRockyVegetation(world, pos, biome, groundBlock);
            case ADAPTIVE -> getAdaptiveVegetation(world, pos, biome, groundBlock);
            // 自定义类型已经在上面处理了
            default -> throw new IllegalStateException("Unexpected value: " + shorelineType);
        };
    }
    
    /**
     * 从自定义植物中随机找出适合种植条件的植物
     * 优化：支持多种植物类型，智能筛选适合的植物
     * 
     * @param world 世界实例
     * @param pos 种植位置
     * @param customPlants 自定义植物列表
     * @param groundBlock 地面方块
     * @return 适合种植的植物方块，如果没有找到返回null
     */
    private Block getRandomSuitableCustomPlant(World world, BlockPos pos, List<Block> customPlants, BlockState groundBlock) {
        if (customPlants.isEmpty()) {
            return null;
        }
        
        PushdozerMod.LOGGER.debug("Checking {} custom plants for position {} with ground block {}", 
            customPlants.size(), pos, groundBlock.getBlock().toString());
        
        // 过滤出适合当前位置的植物
        List<Block> suitablePlants = new ArrayList<>();
        for (Block plant : customPlants) {
            if (plant != null) {
                boolean canGrow = canPlantGrowOnBlock(world, pos, plant, groundBlock);
                if (canGrow) {
                    suitablePlants.add(plant);
                    PushdozerMod.LOGGER.debug("Plant {} is suitable for position {}", plant, pos);
                } else {
                    PushdozerMod.LOGGER.debug("Plant {} is NOT suitable for position {}", plant, pos);
                }
            }
        }
        
        if (suitablePlants.isEmpty()) {
            // 调试：记录没有找到适合的植物
            PushdozerMod.LOGGER.debug("No suitable custom plants found for position {} with ground block {}", 
                pos, groundBlock.getBlock().toString());
            return null;
        }
        
        // 从适合的植物中随机选择一个
        Block selectedPlant = suitablePlants.get(world.getRandom().nextInt(suitablePlants.size()));
        
        // 调试：记录选择的植物
        PushdozerMod.LOGGER.debug("Selected custom plant {} from {} suitable options for position {}", 
            selectedPlant.toString(), suitablePlants.size(), pos);
        
        return selectedPlant;
    }
    
    /**
     * 获取随机花朵
     * 逻辑优化：使用硬编码花朵列表，确保兼容性
     */
    private BlockState getRandomFlower(World world) {
        // 使用硬编码的花朵列表，确保兼容性
        Block[] flowers = {
            Blocks.DANDELION,
            Blocks.POPPY,
            Blocks.BLUE_ORCHID,
            Blocks.ALLIUM,
            Blocks.AZURE_BLUET,
            Blocks.RED_TULIP,
            Blocks.ORANGE_TULIP,
            Blocks.WHITE_TULIP,
            Blocks.PINK_TULIP,
            Blocks.OXEYE_DAISY,
            Blocks.CORNFLOWER,
            Blocks.LILY_OF_THE_VALLEY
        };
        
        return flowers[world.getRandom().nextInt(flowers.length)].getDefaultState();
    }
    
    /**
     * 获取随机水生植物
     * 新增：专门用于水岸的水生植物池
     * 优化：添加特殊植物逻辑，支持生长阶段和深度检查
     */
    private BlockState getRandomAquaticPlant(World world) {
        Block[] aquaticPlants = {
            Blocks.SEAGRASS,
            Blocks.KELP,
            Blocks.SEA_PICKLE,
            Blocks.TUBE_CORAL,
            Blocks.BRAIN_CORAL,
            Blocks.BUBBLE_CORAL,
            Blocks.FIRE_CORAL,
            Blocks.HORN_CORAL
        };
        
        Block selectedPlant = aquaticPlants[world.getRandom().nextInt(aquaticPlants.length)];
        
        // 优化：为海带添加生长阶段随机
        if (selectedPlant == Blocks.KELP) {
            return selectedPlant.getDefaultState().with(KelpBlock.AGE, world.getRandom().nextInt(4));
        }
        
        return selectedPlant.getDefaultState();
    }
    
    /**
     * 获取随机蕨类植物
     * 新增：专门用于森林和湿地的蕨类植物池
     * 优化：添加特殊植物逻辑，支持生长阶段
     */
    private BlockState getRandomFern(World world) {
        Block[] ferns = {
            Blocks.FERN,
            Blocks.LARGE_FERN
        };
        
        Block selectedFern = ferns[world.getRandom().nextInt(ferns.length)];
        
        // 优化：为大型蕨类添加生长阶段随机
        if (selectedFern == Blocks.LARGE_FERN) {
            return selectedFern.getDefaultState().with(TallPlantBlock.HALF,
                world.getRandom().nextBoolean() ? DoubleBlockHalf.LOWER : DoubleBlockHalf.UPPER);
        }
        
        return selectedFern.getDefaultState();
    }
    
    /**
     * 获取随机苔藓植物
     * 新增：专门用于岩石和高山的苔藓植物池
     */
    private BlockState getRandomMoss(World world) {
        Block[] mossPlants = {
            Blocks.MOSS_CARPET,
            Blocks.GLOW_LICHEN,
            Blocks.SMALL_DRIPLEAF,
            Blocks.BIG_DRIPLEAF
        };
        
        return mossPlants[world.getRandom().nextInt(mossPlants.length)].getDefaultState();
    }
    
    /**
     * 获取带花的仙人掌
     * 新增：专门用于沙滩类型的带花仙人掌
     * 修复：仙人掌花应该种植在仙人掌顶部，而不是直接在地面种植
     * 
     * @param world 世界实例
     * @return 带花的仙人掌状态
     */
    private BlockState getCactusWithFlower(World world) {
        // 修复：仙人掌花应该种植在仙人掌顶部，而不是直接在地面种植
        // 这里返回普通仙人掌，仙人掌花会在种植逻辑中处理
        return Blocks.CACTUS.getDefaultState();
    }

        /**
     * 查找水体边缘位置
     * 优化：支持水平和垂直方向的水岸边缘检测
     * 处理水岸的水平边缘和垂直边缘（如悬崖、斜坡）
     * 
     * @param world 世界实例
     * @param shape 处理形状
     * @return 水体边缘位置集合
     */
    private Set<BlockPos> findShorelineEdges(World world, GeometryShape shape) {
        Set<BlockPos> edges = new HashSet<>();
        Set<BlockPos> waterBlocks = new HashSet<>();
        Set<BlockPos> checkedPositions = new HashSet<>();

        // 首先收集笔刷范围内的所有水体
        for (BlockPos pos : shape.getBlockPositions()) {
            if (world.getFluidState(pos).isIn(FluidTags.WATER)) {
                waterBlocks.add(pos);
            }
        }

        // 处理水平和垂直方向的水岸边缘
        for (BlockPos waterPos : waterBlocks) {
            // 检查水平方向（XZ平面）
            for (Direction dir : Direction.Type.HORIZONTAL) {
                BlockPos neighborPos = waterPos.offset(dir);
                
                if (!checkedPositions.contains(neighborPos)) {
                    checkedPositions.add(neighborPos);
                    
                    if (!waterBlocks.contains(neighborPos) && 
                        !world.getFluidState(neighborPos).isIn(FluidTags.WATER) &&
                        isReplaceableLandBlock(world, neighborPos, world.getBlockState(neighborPos))) {
                        edges.add(neighborPos);
                    }
                }
            }
            
            // 检查垂直方向（Y轴）- 处理水岸的垂直边缘
            for (Direction dir : Direction.Type.VERTICAL) {
                BlockPos neighborPos = waterPos.offset(dir);
                
                if (!checkedPositions.contains(neighborPos)) {
                    checkedPositions.add(neighborPos);
                    
                    if (!waterBlocks.contains(neighborPos) && 
                        !world.getFluidState(neighborPos).isIn(FluidTags.WATER) &&
                        isReplaceableLandBlock(world, neighborPos, world.getBlockState(neighborPos))) {
                        edges.add(neighborPos);
                    }
                }
            }
        }

        return edges;
    }
    
    /**
     * 检查chunk是否已加载
     * 防止在未加载的chunk中放置方块
     * 
     * @param world 世界实例
     * @param pos 要检查的位置
     * @return 如果chunk已加载返回true，否则返回false
     */
    private boolean edgeFinder.isChunkLoaded(World world, BlockPos pos) {
        return world.edgeFinder.isChunkLoaded(pos.getX() >> 4, pos.getZ() >> 4);
    }
    
    /**
     * 沙滩类型植物预设
     * 专注于海边和水边的植物
     * 优化：增加花朵多样性、扩展生物群系覆盖、支持更多地面类型
     * 新增：高度影响、冰沙滩支持、植物池整合
     */
    private BlockState getBeachVegetation(World world, BlockPos pos, Biome biome, BlockState groundBlock) {
        String biomeName = biome.toString().toLowerCase();
        
        // 调试：记录沙滩植物选择
        PushdozerMod.LOGGER.debug("Beach vegetation selection - Biome: {}, Ground: {}", biomeName, groundBlock.getBlock().toString());
        
        // 新增：高度影响调整（假设pos可用，实际使用时需要传递pos参数）
        float heightAdjustment = 1.0f;
        // 注意：这里需要pos参数，暂时注释掉，实际使用时取消注释
        // if (pos.getY() > 60) {
        //     heightAdjustment = 0.8f; // 高海拔减少植物密度
        // }
        
        if (groundBlock.isIn(BlockTags.SAND)) {
            // 优化：沙滩沙子方块植物多样性大幅提升
            if (biomeName.contains("ocean") || biomeName.contains("beach")) {
                // 海洋/海滩：水生植物为主
                return getRandomAquaticPlant(world);
            } else if (biomeName.contains("desert")) {
                // 沙漠：仙人掌和枯死灌木，10%概率带花
                float randomValue = world.getRandom().nextFloat();
                if (randomValue < 0.7f) {
                    return Blocks.CACTUS.getDefaultState();
                } else if (randomValue < 0.8f) {
                    return Blocks.DEAD_BUSH.getDefaultState();
                } else {
                    // 10%概率生成带花的仙人掌
                    return Blocks.CACTUS.getDefaultState(); // 返回普通仙人掌，花会在种植时处理
                }
            } else if (biomeName.contains("river") || biomeName.contains("swamp")) {
                // 河流/沼泽：甘蔗和水生植物混合
                return world.getRandom().nextFloat() < 0.6f ? Blocks.SUGAR_CANE.getDefaultState() : getRandomAquaticPlant(world);
            } else if (biomeName.contains("jungle") || biomeName.contains("forest")) {
                // 丛林/森林：竹子、仙人掌、甘蔗混合，10%概率带花
                float randomValue = world.getRandom().nextFloat();
                if (randomValue < 0.4f) {
                    return Blocks.BAMBOO.getDefaultState();
                } else if (randomValue < 0.6f) {
                    return Blocks.CACTUS.getDefaultState();
                } else if (randomValue < 0.7f) {
                    // 10%概率生成带花的仙人掌
                    return Blocks.CACTUS.getDefaultState(); // 返回普通仙人掌，花会在种植时处理
                } else {
                    return Blocks.SUGAR_CANE.getDefaultState();
                }
            } else if (biomeName.contains("savanna") || biomeName.contains("plains")) {
                // 热带草原/平原：仙人掌、枯死灌木、甘蔗，10%概率带花
                float randomValue = world.getRandom().nextFloat();
                if (randomValue < 0.4f) {
                    return Blocks.CACTUS.getDefaultState();
                } else if (randomValue < 0.5f) {
                    // 10%概率生成带花的仙人掌
                    return Blocks.CACTUS.getDefaultState(); // 返回普通仙人掌，花会在种植时处理
                } else if (randomValue < 0.8f) {
                    return Blocks.DEAD_BUSH.getDefaultState();
                } else {
                    return Blocks.SUGAR_CANE.getDefaultState();
                }
            } else if (biomeName.contains("badlands")) {
                // 坏地：枯死灌木为主
                return world.getRandom().nextFloat() < 0.8f ? Blocks.DEAD_BUSH.getDefaultState() : Blocks.CACTUS.getDefaultState();
            } else {
                // 其他生物群系：仙人掌、甘蔗、枯死灌木混合，10%概率带花
                float randomValue = world.getRandom().nextFloat();
                if (randomValue < 0.3f) {
                    return Blocks.CACTUS.getDefaultState();
                } else if (randomValue < 0.4f) {
                    // 10%概率生成带花的仙人掌
                    return Blocks.CACTUS.getDefaultState(); // 返回普通仙人掌，花会在种植时处理
                } else if (randomValue < 0.7f) {
                    return Blocks.SUGAR_CANE.getDefaultState();
                } else {
                    return Blocks.DEAD_BUSH.getDefaultState();
                }
            }
        } else if (groundBlock.isIn(BlockTags.DIRT) || groundBlock.getBlock() == Blocks.GRASS_BLOCK) {
            // 优化：沙滩泥土地面植物多样性大幅提升
            if (biomeName.contains("snowy")) {
                // 雪地：少量草丛，确保概率平衡
                float randomValue = world.getRandom().nextFloat();
                if (randomValue < 0.1f) {
                    return Blocks.SHORT_GRASS.getDefaultState();
                } else {
                    return null; // 雪地大部分区域保持无植物
                }
            } else if (biomeName.contains("desert")) {
                // 沙漠：仙人掌和枯死灌木，10%概率带花
                float randomValue = world.getRandom().nextFloat();
                if (randomValue < 0.7f) {
                    return Blocks.CACTUS.getDefaultState();
                } else if (randomValue < 0.8f) {
                    return Blocks.DEAD_BUSH.getDefaultState();
                } else {
                    // 10%概率生成带花的仙人掌
                    return Blocks.CACTUS.getDefaultState(); // 返回普通仙人掌，花会在种植时处理
                }
            } else if (biomeName.contains("river") || biomeName.contains("swamp")) {
                // 河流/沼泽：甘蔗和蕨类
                return world.getRandom().nextFloat() < 0.7f ? Blocks.SUGAR_CANE.getDefaultState() : getRandomFern(world);
            } else if (biomeName.contains("jungle") || biomeName.contains("forest")) {
                // 丛林/森林：仙人掌、蕨类、草丛、花朵混合，10%概率带花
                float randomValue = world.getRandom().nextFloat();
                if (randomValue < 0.2f) {
                    return Blocks.CACTUS.getDefaultState();
                } else if (randomValue < 0.3f) {
                    // 10%概率生成带花的仙人掌
                    return Blocks.CACTUS.getDefaultState(); // 返回普通仙人掌，花会在种植时处理
                } else if (randomValue < 0.5f) {
                    return Blocks.SHORT_GRASS.getDefaultState();
                } else if (randomValue < 0.7f) {
                    return getRandomFern(world);
                } else if (randomValue < 0.9f) {
                    return Blocks.TALL_GRASS.getDefaultState();
                } else {
                    return getRandomFlower(world);
                }
            } else if (biomeName.contains("savanna") || biomeName.contains("plains")) {
                // 热带草原/平原：仙人掌、草丛、花朵，10%概率带花
                float randomValue = world.getRandom().nextFloat();
                if (randomValue < 0.3f) {
                    return Blocks.CACTUS.getDefaultState();
                } else if (randomValue < 0.4f) {
                    // 10%概率生成带花的仙人掌
                    return Blocks.CACTUS.getDefaultState(); // 返回普通仙人掌，花会在种植时处理
                } else if (randomValue < 0.7f) {
                    return Blocks.SHORT_GRASS.getDefaultState();
                } else if (randomValue < 0.9f) {
                    return Blocks.TALL_GRASS.getDefaultState();
                } else {
                    return getRandomFlower(world);
                }
            } else if (biomeName.contains("badlands")) {
                // 坏地：仙人掌和枯死灌木
                return world.getRandom().nextFloat() < 0.6f ? Blocks.CACTUS.getDefaultState() : Blocks.DEAD_BUSH.getDefaultState();
            } else {
                // 其他生物群系：仙人掌、草丛、花朵混合，10%概率带花
                float randomValue = world.getRandom().nextFloat();
                if (randomValue < 0.2f) {
                    return Blocks.CACTUS.getDefaultState();
                } else if (randomValue < 0.3f) {
                    // 10%概率生成带花的仙人掌
                    return Blocks.CACTUS.getDefaultState(); // 返回普通仙人掌，花会在种植时处理
                } else if (randomValue < 0.6f) {
                    return Blocks.SHORT_GRASS.getDefaultState();
                } else if (randomValue < 0.8f) {
                    return Blocks.TALL_GRASS.getDefaultState();
                } else {
                    return getRandomFlower(world);
                }
            }
        } else if (groundBlock.getBlock() == Blocks.SANDSTONE || 
                   groundBlock.getBlock() == Blocks.RED_SANDSTONE ||
                   groundBlock.getBlock() == Blocks.SMOOTH_SANDSTONE) {
            // 优化：砂岩地面植物多样性提升
            if (biomeName.contains("desert") || biomeName.contains("badlands")) {
                // 沙漠/坏地：仙人掌和枯死灌木
                return world.getRandom().nextFloat() < 0.6f ? Blocks.CACTUS.getDefaultState() : Blocks.DEAD_BUSH.getDefaultState();
            } else if (biomeName.contains("savanna") || biomeName.contains("plains")) {
                // 热带草原/平原：仙人掌和枯死灌木
                return world.getRandom().nextFloat() < 0.5f ? Blocks.CACTUS.getDefaultState() : Blocks.DEAD_BUSH.getDefaultState();
            } else {
                // 其他生物群系：仙人掌和枯死灌木
                return world.getRandom().nextFloat() < 0.4f ? Blocks.CACTUS.getDefaultState() : Blocks.DEAD_BUSH.getDefaultState();
            }
        }
        
        return null;
    }
    
    /**
     * 堤岸类型植物预设
     * 专注于堤岸和河岸的植物
     * 优化：添加岩石/砾石地面支持、扩展山地覆盖、增加水生元素
     */
    private BlockState getEmbankmentVegetation(World world, BlockPos pos, Biome biome, BlockState groundBlock) {
        String biomeName = biome.toString().toLowerCase();
        
        if (groundBlock.isIn(BlockTags.DIRT) || groundBlock.getBlock() == Blocks.GRASS_BLOCK) {
            if (biomeName.contains("snowy")) {
                return null; // 雪地不种植植物
            } else if (biomeName.contains("desert")) {
                return world.getRandom().nextFloat() < 0.7f ? Blocks.CACTUS.getDefaultState() : Blocks.DEAD_BUSH.getDefaultState();
            } else if (biomeName.contains("river") || biomeName.contains("swamp")) {
                // 优化：整合蕨类植物池，增加多样性
                float randomValue = world.getRandom().nextFloat();
                if (randomValue < 0.5f) {
                    return Blocks.SUGAR_CANE.getDefaultState();
                } else if (randomValue < 0.8f) {
                    return getRandomFern(world); // 优化：使用蕨类植物池
                } else {
                    return Blocks.BLUE_ORCHID.getDefaultState(); // 新增：湿地花朵
                }
            } else if (biomeName.contains("forest") || biomeName.contains("jungle")) {
                // 优化：整合蕨类植物池，增加多样性
                float randomValue = world.getRandom().nextFloat();
                if (randomValue < 0.4f) {
                    return getRandomFern(world); // 优化：使用蕨类植物池
                } else if (randomValue < 0.6f) {
                    return Blocks.SHORT_GRASS.getDefaultState();
                } else if (randomValue < 0.8f) {
                    return Blocks.TALL_GRASS.getDefaultState();
                } else {
                    return getRandomFlower(world);
                }
            } else if (biomeName.contains("mountain") || biomeName.contains("hill")) {
                // 优化：整合蕨类植物池，增加多样性
                float randomValue = world.getRandom().nextFloat();
                if (randomValue < 0.3f) {
                    return Blocks.SHORT_GRASS.getDefaultState();
                } else if (randomValue < 0.5f) {
                    return Blocks.TALL_GRASS.getDefaultState(); // 新增：高草丛
                } else if (randomValue < 0.7f) {
                    return getRandomFern(world); // 优化：使用蕨类植物池
                } else if (randomValue < 0.9f) {
                    return getRandomFlower(world); // 新增：随机花朵
                } else {
                    return Blocks.OXEYE_DAISY.getDefaultState(); // 新增：高山花朵
                }
            } else if (biomeName.contains("nether")) {
                // 新增：Nether堤岸支持，提高概率
                float randomValue = world.getRandom().nextFloat();
                if (randomValue < 0.5f) {
                    return Blocks.CRIMSON_ROOTS.getDefaultState();
                } else if (randomValue < 0.7f) {
                    return Blocks.WARPED_ROOTS.getDefaultState();
                } else {
                    return null;
                }
            } else if (biomeName.contains("end")) {
                // 新增：End堤岸支持
                return world.getRandom().nextFloat() < 0.3f ? Blocks.CHORUS_PLANT.getDefaultState() : null;
            } else {
                // 其他生物群系堤岸：草丛、花朵、蕨类
                float randomValue = world.getRandom().nextFloat();
                if (randomValue < 0.4f) {
                    return Blocks.SHORT_GRASS.getDefaultState();
                } else if (randomValue < 0.6f) {
                    return Blocks.TALL_GRASS.getDefaultState();
                } else if (randomValue < 0.8f) {
                    return getRandomFern(world); // 新增：蕨类植物
                } else {
                    return getRandomFlower(world);
                }
            }
        } else if (groundBlock.isIn(BlockTags.SAND)) {
            if (biomeName.contains("river") || biomeName.contains("swamp")) {
                // 优化：增加水生元素
                return world.getRandom().nextFloat() < 0.8f ? Blocks.SUGAR_CANE.getDefaultState() : Blocks.KELP.getDefaultState();
            } else if (biomeName.contains("desert")) {
                // 新增：沙漠沙子支持
                return world.getRandom().nextFloat() < 0.6f ? Blocks.DEAD_BUSH.getDefaultState() : Blocks.CACTUS.getDefaultState();
            } else {
                // 其他沙子地面：草丛、花朵
                float randomValue = world.getRandom().nextFloat();
                if (randomValue < 0.6f) {
                    return Blocks.SHORT_GRASS.getDefaultState();
                } else if (randomValue < 0.8f) {
                    return Blocks.TALL_GRASS.getDefaultState(); // 新增：高草丛
                } else {
                    return getRandomFlower(world); // 新增：随机花朵
                }
            }
        } else if (groundBlock.getBlock() == Blocks.STONE || 
                   groundBlock.getBlock() == Blocks.COBBLESTONE ||
                   groundBlock.getBlock() == Blocks.GRAVEL ||
                   groundBlock.getBlock() == Blocks.ANDESITE ||
                   groundBlock.getBlock() == Blocks.DIORITE ||
                   groundBlock.getBlock() == Blocks.GRANITE) {
            // 优化：整合苔藓植物池，增加多样性
            if (biomeName.contains("mountain") || biomeName.contains("hill")) {
                float randomValue = world.getRandom().nextFloat();
                if (randomValue < 0.5f) {
                    return getRandomMoss(world); // 优化：使用苔藓植物池
                } else if (randomValue < 0.8f) {
                    return Blocks.OXEYE_DAISY.getDefaultState(); // 新增：高山花朵
                } else {
                    return getRandomFlower(world); // 新增：随机花朵
                }
            } else if (biomeName.contains("forest")) {
                float randomValue = world.getRandom().nextFloat();
                if (randomValue < 0.3f) {
                    return Blocks.GLOW_LICHEN.getDefaultState();
                } else if (randomValue < 0.6f) {
                    return getRandomMoss(world); // 新增：苔藓植物
                } else {
                    return getRandomFern(world); // 新增：蕨类植物
                }
            } else if (biomeName.contains("river") || biomeName.contains("swamp")) {
                return world.getRandom().nextFloat() < 0.2f ? Blocks.SUGAR_CANE.getDefaultState() : null;
            } else {
                // 其他岩石地面：苔藓、花朵
                float randomValue = world.getRandom().nextFloat();
                if (randomValue < 0.4f) {
                    return getRandomMoss(world); // 新增：苔藓植物
                } else if (randomValue < 0.7f) {
                    return getRandomFern(world); // 新增：蕨类植物
                } else {
                    return getRandomFlower(world); // 新增：随机花朵
                }
            }
        }
        
        return null;
    }
    
    /**
     * 泥泞类型植物预设
     * 专注于沼泽和湿地植物
     * 优化：扩展多样性、覆盖更多生物群系、支持岩石地面、强调泥巴特色
     */
    private BlockState getMuddyVegetation(World world, BlockPos pos, Biome biome, BlockState groundBlock) {
        String biomeName = biome.toString().toLowerCase();
        
        if (groundBlock.isIn(BlockTags.DIRT) || groundBlock.getBlock() == Blocks.GRASS_BLOCK || groundBlock.getBlock() == Blocks.MUD) {
            if (biomeName.contains("snowy")) {
                return null; // 雪地不种植植物
            } else if (biomeName.contains("swamp") || biomeName.contains("river")) {
                // 优化：整合蕨类植物池，增加湿地花朵和高草丛
                float randomValue = world.getRandom().nextFloat();
                if (randomValue < 0.3f) {
                    return Blocks.SUGAR_CANE.getDefaultState();
                } else if (randomValue < 0.5f) {
                    return getRandomFern(world); // 优化：使用蕨类植物池
                } else if (randomValue < 0.7f) {
                    return Blocks.SHORT_GRASS.getDefaultState(); // 新增：矮草
                } else if (randomValue < 0.85f) {
                    return Blocks.TALL_GRASS.getDefaultState(); // 新增：高草丛
                } else if (randomValue < 0.95f) {
                    return Blocks.BLUE_ORCHID.getDefaultState(); // 新增：湿地花朵
                } else {
                    return getRandomFlower(world); // 新增：随机花朵
                }
            } else if (biomeName.contains("jungle")) {
                // 优化：整合蕨类植物池，增加多样性
                float randomValue = world.getRandom().nextFloat();
                if (randomValue < 0.4f) {
                    return getRandomFern(world);
                } else if (randomValue < 0.6f) {
                    return Blocks.SUGAR_CANE.getDefaultState();
                } else if (randomValue < 0.75f) {
                    return Blocks.SHORT_GRASS.getDefaultState(); // 新增：矮草
                } else if (randomValue < 0.9f) {
                    return Blocks.TALL_GRASS.getDefaultState(); // 新增：高草丛
                } else {
                    return getRandomFlower(world); // 新增：随机花朵
                }
            } else if (biomeName.contains("forest") || biomeName.contains("plains")) {
                // 优化：整合蕨类植物池，增加多样性
                float randomValue = world.getRandom().nextFloat();
                if (randomValue < 0.3f) {
                    return Blocks.SHORT_GRASS.getDefaultState();
                } else if (randomValue < 0.5f) {
                    return getRandomFern(world);
                } else if (randomValue < 0.7f) {
                    return Blocks.TALL_GRASS.getDefaultState(); // 新增：高草丛
                } else if (randomValue < 0.85f) {
                    return getRandomFlower(world); // 新增：随机花朵
                } else {
                    return Blocks.BLUE_ORCHID.getDefaultState(); // 新增：湿地花朵
                }
            } else if (biomeName.contains("desert")) {
                // 优化：沙漠添加枯死灌木，平衡概率
                float randomValue = world.getRandom().nextFloat();
                if (randomValue < 0.4f) {
                    return Blocks.DEAD_BUSH.getDefaultState();
                } else if (randomValue < 0.7f) {
                    return Blocks.SHORT_GRASS.getDefaultState();
                } else if (randomValue < 0.9f) {
                    return Blocks.TALL_GRASS.getDefaultState(); // 新增：高草丛
                } else {
                    return getRandomFlower(world); // 新增：随机花朵
                }
            } else if (biomeName.contains("nether")) {
                // 新增：Nether泥泞支持
                return world.getRandom().nextFloat() < 0.4f ? Blocks.CRIMSON_ROOTS.getDefaultState() : null;
            } else if (biomeName.contains("end")) {
                // 新增：End泥泞支持
                return world.getRandom().nextFloat() < 0.2f ? Blocks.CHORUS_PLANT.getDefaultState() : null;
            } else {
                // 优化：使用蕨类植物池，增加多样性
                float randomValue = world.getRandom().nextFloat();
                if (randomValue < 0.4f) {
                    return getRandomFern(world);
                } else if (randomValue < 0.6f) {
                    return Blocks.SHORT_GRASS.getDefaultState(); // 新增：矮草
                } else if (randomValue < 0.8f) {
                    return Blocks.TALL_GRASS.getDefaultState(); // 新增：高草丛
                } else {
                    return getRandomFlower(world); // 新增：随机花朵
                }
            }
        } else if (groundBlock.isIn(BlockTags.SAND)) {
            if (biomeName.contains("swamp") || biomeName.contains("river")) {
                return Blocks.SUGAR_CANE.getDefaultState();
            } else if (biomeName.contains("ocean")) {
                // 新增：水生泥地扩展，提高概率平衡
                float randomValue = world.getRandom().nextFloat();
                if (randomValue < 0.6f) {
                    return getRandomAquaticPlant(world);
                } else {
                    return Blocks.SEAGRASS.getDefaultState(); // 确保水生植物覆盖
                }
            }
        } else if (groundBlock.getBlock() == Blocks.STONE || 
                   groundBlock.getBlock() == Blocks.COBBLESTONE ||
                   groundBlock.getBlock() == Blocks.GRAVEL ||
                   groundBlock.getBlock() == Blocks.ANDESITE ||
                   groundBlock.getBlock() == Blocks.DIORITE ||
                   groundBlock.getBlock() == Blocks.GRANITE) {
            // 新增：支持岩石地面，匹配泥泞的潮湿感
            if (biomeName.contains("swamp") || biomeName.contains("river")) {
                return world.getRandom().nextFloat() < 0.4f ? Blocks.MOSS_CARPET.getDefaultState() : null;
            }
        } else if (groundBlock.getBlock() == Blocks.MUD) {
            // 新增：强调泥巴地面专属，添加小水滴叶
            if (biomeName.contains("swamp") || biomeName.contains("river")) {
                return world.getRandom().nextFloat() < 0.2f ? Blocks.SMALL_DRIPLEAF.getDefaultState() : Blocks.FERN.getDefaultState();
            }
        }
        
        return null;
    }
    
    /**
     * 岩石类型植物预设
     * 专注于岩石和山地植物
     * 优化：增加多样性、扩展覆盖、添加沙子地面支持、引入高度检查
     */
    private BlockState getRockyVegetation(World world, BlockPos pos, Biome biome, BlockState groundBlock) {
        String biomeName = biome.toString().toLowerCase();
        
        if (groundBlock.getBlock() == Blocks.STONE || 
            groundBlock.getBlock() == Blocks.COBBLESTONE ||
            groundBlock.getBlock() == Blocks.GRAVEL ||
            groundBlock.getBlock() == Blocks.ANDESITE ||
            groundBlock.getBlock() == Blocks.DIORITE ||
            groundBlock.getBlock() == Blocks.GRANITE ||
            groundBlock.getBlock() == Blocks.DEEPSLATE) {
            
            if (biomeName.contains("mountain") || biomeName.contains("hill")) {
                // 优化：整合苔藓植物池，增加高山花朵，确保概率总和为1
                float randomValue = world.getRandom().nextFloat();
                if (randomValue < 0.5f) {
                    return getRandomMoss(world); // 优化：使用苔藓植物池
                } else if (randomValue < 0.8f) {
                    return Blocks.OXEYE_DAISY.getDefaultState(); // 新增：高山花朵
                } else {
                    return getRandomFlower(world); // 新增：随机花朵
                }
            } else if (biomeName.contains("desert")) {
                // 新增：沙漠岩石添加仙人掌和枯死灌木
                return world.getRandom().nextFloat() < 0.5f ? Blocks.CACTUS.getDefaultState() : Blocks.DEAD_BUSH.getDefaultState();
            } else if (biomeName.contains("ocean") || biomeName.contains("beach")) {
                // 优化：使用水生植物池，增加珊瑚多样性
                return world.getRandom().nextFloat() < 0.2f ? getRandomAquaticPlant(world) : null;
            } else if (biomeName.contains("forest")) {
                // 优化：使用蕨类植物池，增加多样性
                return world.getRandom().nextFloat() < 0.3f ? getRandomFern(world) : null;
            } else if (biomeName.contains("river") || biomeName.contains("swamp")) {
                // 河岸岩石：海草
                return world.getRandom().nextFloat() < 0.2f ? Blocks.SUGAR_CANE.getDefaultState() : null;
            } else if (biomeName.contains("nether")) {
                // 新增：Nether岩石支持
                return world.getRandom().nextFloat() < 0.2f ? Blocks.WARPED_ROOTS.getDefaultState() : null;
            } else if (biomeName.contains("end")) {
                // 新增：End岩石支持
                return world.getRandom().nextFloat() < 0.2f ? Blocks.CHORUS_PLANT.getDefaultState() : null;
            }
            // 其他岩石不种植植物
            return null;
        } else if (groundBlock.isIn(BlockTags.DIRT) || groundBlock.getBlock() == Blocks.GRASS_BLOCK) {
            if (biomeName.contains("mountain") || biomeName.contains("hill")) {
                // 山地泥土：草丛、蕨类
                float randomValue = world.getRandom().nextFloat();
                if (randomValue < 0.6f) {
                    return Blocks.SHORT_GRASS.getDefaultState();
                } else if (randomValue < 0.8f) {
                    return Blocks.FERN.getDefaultState();
                } else {
                    return getRandomFlower(world);
                }
            } else if (biomeName.contains("desert")) {
                // 新增：沙漠泥土添加仙人掌
                return world.getRandom().nextFloat() < 0.3f ? Blocks.CACTUS.getDefaultState() : null;
            } else {
                // 其他生物群系：草丛、花朵、蕨类
                float randomValue = world.getRandom().nextFloat();
                if (randomValue < 0.4f) {
                    return Blocks.SHORT_GRASS.getDefaultState();
                } else if (randomValue < 0.6f) {
                    return Blocks.TALL_GRASS.getDefaultState();
                } else if (randomValue < 0.8f) {
                    return getRandomFern(world); // 新增：蕨类植物
                } else {
                    return getRandomFlower(world); // 新增：随机花朵
                }
            }
        } else if (groundBlock.isIn(BlockTags.SAND)) {
            // 优化：添加沙子地面支持，匹配岩石沙滩变体
            if (biomeName.contains("desert")) {
                return Blocks.DEAD_BUSH.getDefaultState();
            } else if (biomeName.contains("ocean") || biomeName.contains("beach")) {
                // 优化：提高概率平衡，确保有植物覆盖
                float randomValue = world.getRandom().nextFloat();
                if (randomValue < 0.5f) {
                    return getRandomAquaticPlant(world);
                } else {
                    return Blocks.DEAD_BUSH.getDefaultState(); // 确保有植物覆盖
                }
            }
        }
        
        return null;
    }
    
    /**
     * 自适应类型植物预设
     * 根据生物群系智能选择植物
     * 优化：增强岩石地面、添加遗漏生物群系、优化概率分布
     */
    private BlockState getAdaptiveVegetation(World world, BlockPos pos, Biome biome, BlockState groundBlock) {
        String biomeName = biome.toString().toLowerCase();
        
        if (groundBlock.isIn(BlockTags.SAND)) {
            if (biomeName.contains("desert")) {
                return Blocks.CACTUS.getDefaultState();
            } else if (biomeName.contains("river") || biomeName.contains("swamp")) {
                return Blocks.SUGAR_CANE.getDefaultState();
            } else if (biomeName.contains("ocean") || biomeName.contains("beach")) {
                // 优化：整合水生植物池，增加珊瑚多样性
                return getRandomAquaticPlant(world);
            } else if (biomeName.contains("badlands")) {
                // 新增：坏地沙子地面增加枯死灌木
                return world.getRandom().nextFloat() < 0.4f ? Blocks.DEAD_BUSH.getDefaultState() : Blocks.CACTUS.getDefaultState();
            } else {
                return Blocks.SUGAR_CANE.getDefaultState();
            }
        } else if (groundBlock.isIn(BlockTags.DIRT) || groundBlock.getBlock() == Blocks.GRASS_BLOCK) {
            if (biomeName.contains("snowy")) {
                return null; // 雪地不种植植物
            } else if (biomeName.contains("desert")) {
                return world.getRandom().nextFloat() < 0.7f ? Blocks.CACTUS.getDefaultState() : Blocks.DEAD_BUSH.getDefaultState();
            } else if (biomeName.contains("river") || biomeName.contains("swamp")) {
                // 优化：整合蕨类植物池，增加湿地花朵
                float randomValue = world.getRandom().nextFloat();
                if (randomValue < 0.4f) {
                    return Blocks.SUGAR_CANE.getDefaultState();
                } else if (randomValue < 0.7f) {
                    return getRandomFern(world); // 优化：使用蕨类植物池
                } else if (randomValue < 0.9f) {
                    return Blocks.BLUE_ORCHID.getDefaultState(); // 新增：湿地花朵
                } else {
                    return getRandomFlower(world); // 新增：随机花朵
                }
            } else if (biomeName.contains("forest") || biomeName.contains("jungle")) {
                // 优化：整合蕨类植物池，增加多样性
                float randomValue = world.getRandom().nextFloat();
                if (randomValue < 0.4f) {
                    return Blocks.SHORT_GRASS.getDefaultState();
                } else if (randomValue < 0.6f) {
                    return Blocks.TALL_GRASS.getDefaultState();
                } else if (randomValue < 0.8f) {
                    return getRandomFern(world); // 优化：使用蕨类植物池
                } else {
                    return getRandomFlower(world);
                }
            } else if (biomeName.contains("savanna") || biomeName.contains("plains")) {
                // 热带草原和平原：草丛、花朵
                float randomValue = world.getRandom().nextFloat();
                if (randomValue < 0.7f) {
                    return Blocks.SHORT_GRASS.getDefaultState();
                } else {
                    return getRandomFlower(world);
                }
            } else if (biomeName.contains("taiga") || biomeName.contains("cold")) {
                // 针叶林和寒冷生物群系：蕨类
                return world.getRandom().nextFloat() < 0.6f ? Blocks.FERN.getDefaultState() : null;
            } else if (biomeName.contains("ocean") || biomeName.contains("beach")) {
                // 优化：整合水生植物池，增加多样性
                return world.getRandom().nextFloat() < 0.8f ? Blocks.SHORT_GRASS.getDefaultState() : getRandomAquaticPlant(world);
            } else if (biomeName.contains("end")) {
                // 新增：End生物群系支持
                return world.getRandom().nextFloat() < 0.2f ? Blocks.CHORUS_PLANT.getDefaultState() : null;
            } else {
                // 其他生物群系：草丛、花朵、蕨类
                float randomValue = world.getRandom().nextFloat();
                if (randomValue < 0.5f) {
                    return Blocks.SHORT_GRASS.getDefaultState();
                } else if (randomValue < 0.7f) {
                    return Blocks.TALL_GRASS.getDefaultState();
                } else if (randomValue < 0.85f) {
                    return getRandomFern(world); // 优化：使用蕨类植物池
                } else {
                    return getRandomFlower(world);
                }
            }
        } else if (groundBlock.getBlock() == Blocks.STONE || 
                   groundBlock.getBlock() == Blocks.COBBLESTONE ||
                   groundBlock.getBlock() == Blocks.GRAVEL ||
                   groundBlock.getBlock() == Blocks.ANDESITE ||
                   groundBlock.getBlock() == Blocks.DIORITE ||
                   groundBlock.getBlock() == Blocks.GRANITE ||
                   groundBlock.getBlock() == Blocks.DEEPSLATE) {
            // 优化：整合植物池，增强岩石地面植物多样性
            if (biomeName.contains("mountain") || biomeName.contains("hill")) {
                // 优化：使用苔藓植物池，增加多样性
                float randomValue = world.getRandom().nextFloat();
                if (randomValue < 0.5f) {
                    return getRandomMoss(world); // 优化：使用苔藓植物池
                } else if (randomValue < 0.8f) {
                    return Blocks.OXEYE_DAISY.getDefaultState(); // 新增：高山花朵
                } else {
                    return getRandomFlower(world); // 新增：随机花朵
                }
            } else if (biomeName.contains("ocean") || biomeName.contains("beach")) {
                // 优化：使用水生植物池，增加珊瑚多样性
                return world.getRandom().nextFloat() < 0.2f ? getRandomAquaticPlant(world) : null;
            } else if (biomeName.contains("river") || biomeName.contains("swamp")) {
                return world.getRandom().nextFloat() < 0.3f ? Blocks.SUGAR_CANE.getDefaultState() : null;
            } else if (biomeName.contains("forest")) {
                return world.getRandom().nextFloat() < 0.2f ? Blocks.FERN.getDefaultState() : null;
            }
            return null;
        }
        
        return null;
    }
}

