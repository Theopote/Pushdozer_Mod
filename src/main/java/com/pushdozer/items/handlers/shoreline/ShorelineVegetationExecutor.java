package com.pushdozer.items.handlers.shoreline;

import com.pushdozer.PushdozerMod;
import com.pushdozer.config.PushdozerConfig;
import com.pushdozer.items.handlers.shoreline.model.VegetationPlacement;
import com.pushdozer.items.handlers.vegetation.PlantBlockClassifier;
import com.pushdozer.items.handlers.vegetation.PlantPlacementValidator;
import net.minecraft.block.*;
import net.minecraft.block.enums.DoubleBlockHalf;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.state.property.Properties;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.World;

import java.util.List;

public class ShorelineVegetationExecutor {
    private static final int MAX_PLANTS = 500;
    private static final int MAX_PLANT_ATTEMPTS = 1000;

    private final PushdozerConfig config;
    private final ShorelineEdgeFinder edgeFinder;
    private final ShorelineTransitionPlanner transitionPlanner;
    private final ShorelinePlantCompatibility compatibility;

    public ShorelineVegetationExecutor(PushdozerConfig config, ShorelineEdgeFinder edgeFinder,
                                         ShorelineTransitionPlanner transitionPlanner,
                                         ShorelinePlantCompatibility compatibility) {
        this.config = config;
        this.edgeFinder = edgeFinder;
        this.transitionPlanner = transitionPlanner;
        this.compatibility = compatibility;
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
                int requiredHeight = compatibility.getPlantRequiredHeight(world, placement.plant.getBlock());
                
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
}
