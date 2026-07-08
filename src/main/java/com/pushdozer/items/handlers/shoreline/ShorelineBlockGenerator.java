package com.pushdozer.items.handlers.shoreline;

import com.pushdozer.PushdozerMod;
import com.pushdozer.config.PushdozerConfig;
import com.pushdozer.tags.PushdozerBiomeTags;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.tag.BiomeTags;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.World;
import net.minecraft.world.biome.Biome;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;

public class ShorelineBlockGenerator {
    private final PushdozerConfig config;

    public ShorelineBlockGenerator(PushdozerConfig config) {
        this.config = config;
    }
    private static final float BASE_KEEP_PROBABILITY = 0.1f;
    private static final float DISTANCE_PROBABILITY_INCREMENT = 0.075f;
    private static final float MAX_KEEP_PROBABILITY = 0.9f;
    private static final int MAX_SHORELINE_WIDTH = 20;
    private static final int DEFAULT_SHORELINE_WIDTH = 5;
    private static final float DEFAULT_VEGETATION_DENSITY = 0.3f;
    private static final int MAX_VISITED_BLOCKS = 10000;
    private static final int MAX_ITERATIONS = 10000;
    
    // 性能优化常量
    private static final int MAX_PLANTS = 500; // 最大植物数量限制
    private static final int MAX_PLANT_ATTEMPTS = 1000; // 最大种植尝试次数
    
    // 生物群系方块生成器映射表
    private static final Map<TagKey<Biome>, BiFunction<Integer, Random, BlockState>> BIOME_BLOCK_GENERATORS = new HashMap<>();

    static {
        BIOME_BLOCK_GENERATORS.put(PushdozerBiomeTags.HAS_SWAMP_SHORES, 
            (distance, random) -> applyTransitionProbability(distance, Blocks.MUD.getDefaultState(), Blocks.DIRT.getDefaultState(), random));
        BIOME_BLOCK_GENERATORS.put(PushdozerBiomeTags.HAS_RIVER_SHORES, 
            (distance, random) -> applyTransitionProbability(distance, Blocks.GRAVEL.getDefaultState(), Blocks.DIRT.getDefaultState(), random));
        BIOME_BLOCK_GENERATORS.put(PushdozerBiomeTags.HAS_SNOWY_SHORES, 
            (distance, random) -> applyTransitionProbability(distance, Blocks.SNOW_BLOCK.getDefaultState(), Blocks.ICE.getDefaultState(), random));
        // 沙漠沙滩：使用普通沙滩处理，优先级高于沙漠水岸
        BIOME_BLOCK_GENERATORS.put(PushdozerBiomeTags.HAS_DESERT_BEACHES, 
            (distance, random) -> applyBeachTransitionProbability(distance, Blocks.SAND.getDefaultState(), Blocks.DIRT.getDefaultState(), random, null));
        BIOME_BLOCK_GENERATORS.put(PushdozerBiomeTags.HAS_DESERT_SHORES, 
            (distance, random) -> applyTransitionProbability(distance, Blocks.RED_SAND.getDefaultState(), Blocks.RED_SANDSTONE.getDefaultState(), random));
        BIOME_BLOCK_GENERATORS.put(PushdozerBiomeTags.HAS_SANDY_BEACHES, 
            (distance, random) -> applyTransitionProbability(distance, Blocks.SAND.getDefaultState(), Blocks.DIRT.getDefaultState(), random));
        BIOME_BLOCK_GENERATORS.put(PushdozerBiomeTags.HAS_ROCKY_SHORES, 
            (distance, random) -> applyTransitionProbability(distance, Blocks.STONE.getDefaultState(), Blocks.COBBLESTONE.getDefaultState(), random));
        BIOME_BLOCK_GENERATORS.put(PushdozerBiomeTags.HAS_LUSH_SHORES, 
            (distance, random) -> applyTransitionProbability(distance, Blocks.GRASS_BLOCK.getDefaultState(), Blocks.DIRT.getDefaultState(), random));
    }

    public BlockState generate(World world, BlockPos pos, int distance, Biome biome) {
        if (distance > config.getShorelineWidth()) {
            PushdozerMod.LOGGER.warn("Invalid distance {} at pos {}", distance, pos);
            return null;
        }

        PushdozerConfig.ShorelineType shorelineType = config.getShorelineType();
        BlockState result = switch (shorelineType) {
            case BEACH -> generateBeachBlock(world, pos, distance);
            case EMBANKMENT -> generateEmbankmentBlock(world, pos, distance);
            case ADAPTIVE -> generateAdaptiveShorelineBlock(world, pos, distance, biome);
            case MUDDY -> generateMuddyBlock(world, pos, distance);
            case ROCKY -> generateRockyBlock(world, pos, distance);
            case CUSTOM -> generateCustomShorelineBlock(world, pos, distance);
        };
        
        if (result == null) {
            PushdozerMod.LOGGER.warn("No block generated for shoreline type {} at pos {}, distance {}", 
                shorelineType, pos, distance);
        }
        return result;
    }

    /**
     * 生成沙滩方块，专注于平滑过渡
     * 根据生物群系类型选择合适的沙滩材料
     * 
     * @param world 世界实例
     * @param pos 方块位置
     * @param distance 离水的距离
     * @return 生成的沙滩方块状态
     */
    private BlockState generateBeachBlock(World world, BlockPos pos, int distance) {
        var biomeEntry = world.getBiome(pos);

        final BlockState primary;
        final BlockState secondary;

        // 完全使用标签，确保更好的兼容性
        if (biomeEntry.isIn(BiomeTags.IS_OVERWORLD) &&
            biomeEntry.value().toString().toLowerCase().contains("snowy")) {
            primary = Blocks.SNOW_BLOCK.getDefaultState();
            secondary = Blocks.ICE.getDefaultState(); // 雪地过渡到冰
        } else if (biomeEntry.isIn(BiomeTags.IS_BADLANDS)) {
            primary = Blocks.RED_SAND.getDefaultState();
            secondary = Blocks.RED_SANDSTONE.getDefaultState();
        } else {
            primary = Blocks.SAND.getDefaultState();
            secondary = Blocks.DIRT.getDefaultState();
        }

        // 优化：沙滩类型使用更保守的概率分布，保持更多沙子
        return applyBeachTransitionProbability(distance, primary, secondary, world.getRandom(), world.getBiome(pos));
    }

    /**
     * 生成堤岸方块，专注于平滑过渡
     * 根据生物群系类型选择合适的堤岸材料
     * 
     * @param world 世界实例
     * @param pos 方块位置
     * @param distance 离水的距离
     * @return 生成的堤岸方块状态
     */
    private BlockState generateEmbankmentBlock(World world, BlockPos pos, int distance) {
        var biomeEntry = world.getBiome(pos);

        // ⭐ 修正点 2：先确定材料
        final BlockState primary;
        final BlockState secondary;

        // 完全使用标签，确保更好的兼容性
        if (biomeEntry.isIn(BiomeTags.IS_MOUNTAIN)) {
            primary = Blocks.STONE.getDefaultState();
            secondary = Blocks.COBBLESTONE.getDefaultState();
        } else if (biomeEntry.isIn(BiomeTags.IS_BADLANDS)) {
            primary = Blocks.SANDSTONE.getDefaultState();
            secondary = Blocks.SMOOTH_SANDSTONE.getDefaultState();
        } else if (biomeEntry.isIn(BiomeTags.IS_OVERWORLD) &&
                   biomeEntry.value().toString().toLowerCase().contains("snowy")) {
            primary = Blocks.SNOW_BLOCK.getDefaultState();
            secondary = Blocks.PACKED_ICE.getDefaultState();
        } else {
            primary = Blocks.COBBLESTONE.getDefaultState();
            secondary = Blocks.GRAVEL.getDefaultState();
        }

        return applyTransitionProbability(distance, primary, secondary, world.getRandom());
    }

    /**
     * 生成自适应水岸方块，专注于平滑过渡
     * 根据生物群系特征智能选择合适的水岸类型
     * 
     * @param world 世界实例
     * @param pos 方块位置
     * @param distance 离水的距离
     * @param biome 生物群系
     * @return 生成的自适应水岸方块状态
     */
    private BlockState generateAdaptiveShorelineBlock(World world, BlockPos pos, int distance, Biome biome) {
        // 优化：直接使用传入的biome参数，避免重复API调用
        // 注意：这里我们仍然需要获取RegistryEntry<Biome>来进行标签检查
        var biomeEntry = world.getBiome(pos);

        // ⭐ 核心逻辑：使用配置驱动的方式，减少硬编码的if-else结构
        // 优化：按优先级顺序检查标签，提高匹配效率
        PushdozerMod.LOGGER.debug("Checking biome {} for adaptive shoreline at pos {}", biomeEntry.value().toString(), pos);
        for (var entry : BIOME_BLOCK_GENERATORS.entrySet()) {
            TagKey<Biome> tagKey = entry.getKey();
            if (biomeEntry.isIn(tagKey)) {
                PushdozerMod.LOGGER.debug("Matched biome tag {} for adaptive shoreline at pos {}", tagKey.id(), pos);
                return entry.getValue().apply(distance, world.getRandom());
            }
        }
        
        // ⭐ 改进：智能回退机制 - 根据生物群系特征选择合适的水岸类型
        PushdozerMod.LOGGER.debug("No specific biome tag matched, using fallback for biome {} at pos {}", 
            biomeEntry.value().toString(), pos);
        return getSmartFallbackBlock(world, pos, distance, biomeEntry);
    }
    
    /**
     * ⭐ 新增：智能回退机制
     * 根据生物群系特征选择最合适的水岸类型，而不是简单使用用户设置
     * 简化：移除用户配置依赖，使用固定的默认回退
     */
    private BlockState getSmartFallbackBlock(World world, BlockPos pos, int distance, RegistryEntry<Biome> biomeEntry) {
        // 首先尝试根据生物群系特征智能选择
        BlockState smartChoice = getBiomeAwareFallback(world, pos, distance, biomeEntry);
        if (smartChoice != null) {
            return smartChoice;
        }
        
        // 如果智能选择失败，使用固定的默认回退类型（沙滩）
        // 简化：移除用户配置依赖，提供更一致的用户体验
        return applyTransitionProbability(distance, Blocks.SAND.getDefaultState(), Blocks.DIRT.getDefaultState(), world.getRandom());
    }
    
    /**
     * ⭐ 新增：生物群系感知的回退选择
     * 根据生物群系特征选择最合适的水岸类型
     * 重构：使用生物群系标签，彻底告别字符串比较
     */
    private BlockState getBiomeAwareFallback(World world, BlockPos pos, int distance, RegistryEntry<Biome> biomeEntry) {
        // 优先检查最明确的标签
        if (biomeEntry.isIn(BiomeTags.IS_OVERWORLD) &&
            biomeEntry.value().toString().toLowerCase().contains("snowy")) {
            return applyTransitionProbability(distance, Blocks.SNOW_BLOCK.getDefaultState(), Blocks.ICE.getDefaultState(), world.getRandom());
        }
        
        // 沙漠生物群系：使用普通沙滩，而不是红沙
        if (biomeEntry.value().toString().toLowerCase().contains("desert")) {
            return applyBeachTransitionProbability(distance, Blocks.SAND.getDefaultState(), Blocks.DIRT.getDefaultState(), world.getRandom(), world.getBiome(pos));
        }
        
        if (biomeEntry.isIn(BiomeTags.IS_BADLANDS)) {
            return applyTransitionProbability(distance, Blocks.RED_SAND.getDefaultState(), Blocks.RED_SANDSTONE.getDefaultState(), world.getRandom());
        }
        
        // 沼泽：使用字符串检查，因为可能没有专门的标签
        if (biomeEntry.value().toString().toLowerCase().contains("swamp")) {
            return applyTransitionProbability(distance, Blocks.MUD.getDefaultState(), Blocks.DIRT.getDefaultState(), world.getRandom());
        }
        
        if (biomeEntry.isIn(BiomeTags.IS_RIVER)) {
            return applyTransitionProbability(distance, Blocks.GRAVEL.getDefaultState(), Blocks.DIRT.getDefaultState(), world.getRandom());
        }
        
        if (biomeEntry.isIn(BiomeTags.IS_MOUNTAIN)) {
            return applyTransitionProbability(distance, Blocks.STONE.getDefaultState(), Blocks.COBBLESTONE.getDefaultState(), world.getRandom());
        }
        
        if (biomeEntry.isIn(BiomeTags.IS_BEACH)) {
            return applyTransitionProbability(distance, Blocks.SAND.getDefaultState(), Blocks.DIRT.getDefaultState(), world.getRandom());
        }
        
        // 对于植被茂盛的岸边，可以组合使用森林和丛林标签
        if (biomeEntry.isIn(BiomeTags.IS_JUNGLE) ||
            biomeEntry.isIn(BiomeTags.IS_FOREST)) {
            return applyTransitionProbability(distance, Blocks.GRASS_BLOCK.getDefaultState(), Blocks.DIRT.getDefaultState(), world.getRandom());
        }
        
        // 如果没有任何特定标签匹配，返回null，让上层逻辑去使用默认的回退选项
        return null;
    }

    /**
     * 应用过渡概率，实现自然的水岸过渡效果
     * 根据距离计算保持原样的概率，距离越远保持原样概率越高
     * 优化：在远离水岸的地方引入草方块，创造更自然的过渡
     * 
     * @param distance 距离水体的格数
     * @param primary 主要方块类型
     * @param secondary 次要方块类型
     * @param random 随机数生成器
     * @return 生成的方块状态，null表示保持原样
     */
    private static BlockState applyTransitionProbability(int distance, BlockState primary, BlockState secondary, Random random) {
        // 根据距离计算保持原样的概率
        float keepOriginalProbability = calculateKeepOriginalProbability(distance);
        
        // 首先决定是否保持原样
        if (random.nextFloat() < keepOriginalProbability) {
            return null; // 保持原样
        }
        
        // 优化：在远离水岸的地方引入草方块
        return getThreeStageBlock(distance, primary, secondary, random);
    }
    
    /**
     * 应用沙滩过渡概率，优化沙滩类型的概率分布
     * 保持更多的沙子，减少远离水岸的泥土比例，在远处引入草方块
     * 
     * @param distance 距离水体的格数
     * @param primary 主要方块类型（沙子）
     * @param secondary 次要方块类型（泥土）
     * @param random 随机数生成器
     * @return 生成的方块状态，null表示保持原样
     */
    private static BlockState applyBeachTransitionProbability(int distance, BlockState primary, BlockState secondary, 
                                                            Random random, RegistryEntry<Biome> biomeEntry) {
        // 根据距离计算保持原样的概率
        float keepOriginalProbability = calculateKeepOriginalProbability(distance);
        
        // 首先决定是否保持原样
        if (random.nextFloat() < keepOriginalProbability) {
            return null; // 保持原样
        }
        
        // 沙滩类型使用三阶段过渡：沙子 → 泥土 → 草方块
        return getBeachThreeStageBlock(distance, primary, secondary, random, biomeEntry);
    }
    
    /**
     * 获取沙滩类型的主方块概率
     * 优化：沙滩类型保持更多的沙子，减少泥土比例
     * 
     * @param distance 距离水体的格数
     * @return 主方块（沙子）的概率
     */
    private static float getBeachPrimaryProbability(int distance) {
        if (distance == 1) {
            return 0.9f; // 距离1时90%沙子，10%泥土
        } else if (distance == 2) {
            return 0.8f; // 距离2时80%沙子，20%泥土
        } else if (distance == 3) {
            return 0.7f; // 距离3时70%沙子，30%泥土
        } else if (distance <= 5) {
            return 0.6f; // 距离4-5时60%沙子，40%泥土
        } else if (distance <= 8) {
            return 0.5f; // 距离6-8时50%沙子，50%泥土
        } else {
            return 0.3f; // 距离>8时30%沙子，70%泥土
        }
    }
    
    /**
     * 获取沙滩三阶段过渡方块
     * 实现沙子 → 泥土 → 草方块的渐进过渡
     * 优化：草方块比例增加，更符合自然生态
     * 
     * @param distance 距离水体的格数
     * @param primary 主要方块类型（沙子）
     * @param secondary 次要方块类型（泥土）
     * @param random 随机数生成器
     * @return 生成的方块状态
     */
    private static BlockState getBeachThreeStageBlock(int distance, BlockState primary, BlockState secondary, 
                                                    Random random, RegistryEntry<Biome> biomeEntry) {
        // 优化：沙漠使用特殊概率分布，不包含草方块
        if (biomeEntry != null && biomeEntry.value().toString().toLowerCase().contains("desert")) {
            return getDesertThreeStageBlock(distance, primary, secondary, random);
        } else {
            // 沙滩类型：增加草方块比例，减少泥土比例
            return getBeachOptimizedThreeStageBlock(distance, primary, secondary, random);
        }
    }
    
    /**
     * 获取沙滩优化的三阶段过渡方块
     * 优化：草方块比例增加，泥土比例减少，更符合自然生态
     * 
     * @param distance 距离水体的格数
     * @param primary 主要方块类型（沙子）
     * @param secondary 次要方块类型（泥土）
     * @param random 随机数生成器
     * @return 生成的方块状态
     */
    private static BlockState getBeachOptimizedThreeStageBlock(int distance, BlockState primary, BlockState secondary, Random random) {
        float rand = random.nextFloat();
        
        if (distance <= 3) {
            // 近距离：主要使用沙子，少量泥土
            float sandProb = getBeachPrimaryProbability(distance);
            return rand < sandProb ? primary : secondary;
        } else if (distance <= 6) {
            // 中距离：沙子、泥土混合，开始引入草方块
            if (rand < 0.4f) {
                return primary; // 40% 沙子
            } else if (rand < 0.5f) {
                return secondary; // 10% 泥土
            } else {
                return Blocks.GRASS_BLOCK.getDefaultState(); // 50% 草方块
            }
        } else if (distance <= 10) {
            // 远距离：减少沙子，增加草方块，减少泥土
            if (rand < 0.2f) {
                return primary; // 20% 沙子
            } else if (rand < 0.3f) {
                return secondary; // 10% 泥土
            } else {
                return Blocks.GRASS_BLOCK.getDefaultState(); // 70% 草方块
            }
        } else {
            // 最远距离：主要是草方块，少量泥土
            if (rand < 0.1f) {
                return primary; // 10% 沙子
            } else if (rand < 0.2f) {
                return secondary; // 10% 泥土
            } else {
                return Blocks.GRASS_BLOCK.getDefaultState(); // 80% 草方块
            }
        }
    }

    /**
     * 获取沙漠三阶段过渡方块（实际是两阶段）
     * 优化：沙漠不包含草方块
     * 
     * @param distance 距离水体的格数
     * @param primary 主要方块类型（沙子）
     * @param secondary 次要方块类型（泥土）
     * @param random 随机数生成器
     * @return 生成的方块状态
     */
    private static BlockState getDesertThreeStageBlock(int distance, BlockState primary, BlockState secondary, Random random) {
        float rand = random.nextFloat();
        
        if (distance <= 3) {
            float sandProb = getBeachPrimaryProbability(distance);
            return rand < sandProb ? primary : secondary;
        } else if (distance <= 6) {
            return rand < 0.4f ? primary : secondary; // 40% 沙子, 60% 泥土
        } else if (distance <= 10) {
            return rand < 0.2f ? primary : secondary; // 20% 沙子, 80% 泥土
        } else {
            return rand < 0.1f ? primary : secondary; // 10% 沙子, 90% 泥土
        }
    }
    
    /**
     * 获取通用三阶段过渡方块
     * 实现主方块 → 次方块 → 草方块的渐进过渡
     * 优化：草方块比例增加，更符合自然生态
     * 
     * @param distance 距离水体的格数
     * @param primary 主要方块类型
     * @param secondary 次要方块类型
     * @param random 随机数生成器
     * @return 生成的方块状态
     */
    private static BlockState getThreeStageBlock(int distance, BlockState primary, BlockState secondary, Random random) {
        float rand = random.nextFloat();
        
        if (distance <= 3) {
            // 近距离：主要使用主方块，少量次方块
            float primaryProbability = getPrimaryProbability(distance);
            return rand < primaryProbability ? primary : secondary;
        } else if (distance <= 6) {
            // 中距离：主方块、次方块混合，开始引入草方块
            if (rand < 0.4f) {
                return primary; // 40% 主方块
            } else if (rand < 0.6f) {
                return secondary; // 20% 次方块
            } else {
                return Blocks.GRASS_BLOCK.getDefaultState(); // 40% 草方块
            }
        } else if (distance <= 10) {
            // 远距离：减少主方块，增加草方块，减少次方块
            if (rand < 0.2f) {
                return primary; // 20% 主方块
            } else if (rand < 0.4f) {
                return secondary; // 20% 次方块
            } else {
                return Blocks.GRASS_BLOCK.getDefaultState(); // 60% 草方块
            }
        } else {
            // 最远距离：主要是草方块，少量次方块
            if (rand < 0.1f) {
                return primary; // 10% 主方块
            } else if (rand < 0.3f) {
                return secondary; // 20% 次方块
            } else {
                return Blocks.GRASS_BLOCK.getDefaultState(); // 70% 草方块
            }
        }
    }
    
    /**
     * 计算保持原样的概率
     * 代码结构优化：提取概率计算逻辑
     * 
     * @param distance 距离水体的格数
     * @return 保持原样的概率
     */
    private static float calculateKeepOriginalProbability(int distance) {
        if (distance == 1) {
            return BASE_KEEP_PROBABILITY; // 第一排：10%保持原样，90%替换
        } else if (distance == 2) {
            return BASE_KEEP_PROBABILITY * 2; // 第二排：20%保持原样，80%替换
        } else if (distance == 3) {
            return BASE_KEEP_PROBABILITY * 3; // 第三排：30%保持原样，70%替换
        } else if (distance <= 5) {
            // 距离4-5格：30-45%保持原样
            return BASE_KEEP_PROBABILITY * 3 + (distance - 3) * DISTANCE_PROBABILITY_INCREMENT;
        } else if (distance <= 8) {
            // 距离6-8格：45-55%保持原样
            return 0.45f + (distance - 5) * 0.033f; // 每增加1格增加3.3%
        } else {
            // 距离>8格：60%+保持原样
            float probability = 0.6f + (distance - 8) * 0.02f; // 每增加1格增加2%
            return Math.min(probability, MAX_KEEP_PROBABILITY); // 最大90%保持原样
        }
    }

    /**
     * 获取主方块概率
     * 优化：调整沙滩类型的概率分布，减少远离水岸的泥土比例
     * 
     * @param distance 距离水体的格数
     * @return 主方块的概率
     */
    private static float getPrimaryProbability(int distance) {
        float primaryProbability;
        if (distance == 1) {
            primaryProbability = 0.8f; // 距离1时80%主方块，20%次方块
        } else if (distance == 2) {
            primaryProbability = 0.7f; // 距离2时70%主方块，30%次方块（提高）
        } else if (distance == 3) {
            primaryProbability = 0.6f; // 距离3时60%主方块，40%次方块（提高）
        } else if (distance <= 5) {
            primaryProbability = 0.4f; // 距离4-5时40%主方块，60%次方块（大幅提高）
        } else if (distance <= 8) {
            primaryProbability = 0.2f; // 距离6-8时20%主方块，80%次方块（提高）
        } else {
            primaryProbability = 0.1f; // 距离>8时10%主方块，90%次方块（提高）
        }
        return primaryProbability;
    }

    /**
     * 生成泥泞方块
     */
    private BlockState generateMuddyBlock(World world, BlockPos pos, int distance) {
        return applyTransitionProbability(distance, Blocks.MUD.getDefaultState(), Blocks.DIRT.getDefaultState(), world.getRandom());
    }

    /**
     * 生成岩石方块
     */
    private BlockState generateRockyBlock(World world, BlockPos pos, int distance) {
        return applyTransitionProbability(distance, Blocks.STONE.getDefaultState(), Blocks.COBBLESTONE.getDefaultState(), world.getRandom());
    }

    /**
     * 生成自定义水岸方块
     * 根据用户选择的自定义方块生成水岸效果
     * 修复：添加过渡概率支持，确保自定义方块也有自然的过渡效果
     * 
     * @param world 世界实例
     * @param pos 方块位置
     * @param distance 离水的距离
     * @return 生成的自定义水岸方块状态
     */
    private BlockState generateCustomShorelineBlock(World world, BlockPos pos, int distance) {
        List<Block> customBlocks = config.getCustomShorelineBlockList();
        
        if (customBlocks.isEmpty()) {
            // 如果没有自定义方块，使用默认的沙滩方块
            return generateBeachBlock(world, pos, distance);
        }
        
        // 修复：使用过渡概率，而不是简单根据距离选择
        // 根据距离计算保持原样的概率
        float keepOriginalProbability = calculateKeepOriginalProbability(distance);
        
        // 首先决定是否保持原样
        if (world.getRandom().nextFloat() < keepOriginalProbability) {
            return null; // 保持原样
        }
        
        // 根据距离选择不同的自定义方块，并应用过渡概率
        int blockIndex = Math.min(distance - 1, customBlocks.size() - 1);

        // 如果有多个自定义方块，可以创建过渡效果
        Block primaryBlock = customBlocks.getFirst(); // 第一个方块作为主要方块
        if (customBlocks.size() > 1 && distance > 1) {
            // 在多个自定义方块之间创建过渡
            Block secondaryBlock = customBlocks.get(Math.min(1, customBlocks.size() - 1)); // 第二个方块作为次要方块
            
            return applyTransitionProbability(distance, primaryBlock.getDefaultState(), secondaryBlock.getDefaultState(), world.getRandom());
        } else {
            // 修复：即使只有一个自定义方块，也要应用过渡概率
            // 这样可以确保自定义方块能够被再次替换
            // 使用泥土作为次要方块，创造从自定义方块到泥土的过渡
            return applyTransitionProbability(distance, primaryBlock.getDefaultState(), Blocks.DIRT.getDefaultState(), world.getRandom());
        }
    }
}

