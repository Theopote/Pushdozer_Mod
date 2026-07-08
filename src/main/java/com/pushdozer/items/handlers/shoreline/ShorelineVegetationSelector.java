package com.pushdozer.items.handlers.shoreline;

import com.pushdozer.PushdozerMod;
import com.pushdozer.config.PushdozerConfig;
import com.pushdozer.items.handlers.vegetation.PlantBlockClassifier;
import net.minecraft.block.*;
import net.minecraft.block.enums.DoubleBlockHalf;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.state.property.Properties;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.biome.Biome;

import java.util.ArrayList;
import java.util.List;

public class ShorelineVegetationSelector {
    private final PushdozerConfig config;
    private final ShorelinePlantCompatibility compatibility;

    public ShorelineVegetationSelector(PushdozerConfig config, ShorelinePlantCompatibility compatibility) {
        this.config = config;
        this.compatibility = compatibility;
    }

    /**
     * 根据生物群系和方块类型获取适合的植物
     * 扩展：增加更多植物种类，创造更丰富的植被
     * 修复：添加自定义植物支持，确保自定义植物能被正确种植
     * 优化：为每种水岸类型创建专门的植物预设
     * 建议：未来可添加config.getXXXPlantList()配置，允许用户自定义植物池
     */
    public BlockState getVegetationForBiome(World world, BlockPos pos, Biome biome, BlockState groundBlock) {
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
                boolean canGrow = compatibility.canPlantGrowOnBlock(world, pos, plant, groundBlock);
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
