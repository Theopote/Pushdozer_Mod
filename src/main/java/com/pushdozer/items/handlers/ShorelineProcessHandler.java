package com.pushdozer.items.handlers;

import com.pushdozer.PushdozerMod;
import com.pushdozer.config.PushdozerConfig;
import com.pushdozer.operations.UndoAction;
import com.pushdozer.shapes.GeometryShape;
import com.pushdozer.tags.PushdozerBiomeTags;
import com.pushdozer.util.ShapeUtil;
import net.minecraft.block.*;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.registry.tag.BiomeTags;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.state.property.Properties;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.biome.Biome;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.block.enums.DoubleBlockHalf;
import net.minecraft.util.math.Direction;

import java.util.*;
import java.util.function.BiFunction;
import net.minecraft.util.math.random.Random;
import net.minecraft.registry.tag.TagKey;

/**
 * 水岸处理处理器
 * 根据生物群系自动生成沙滩或堤岸过渡
 * 升级版：支持距离渐变、材质混合和植物种植，创造更自然的水岸效果
 * <p>
 * 优化版本特性：
 * - 性能优化：BFS深度限制、生物群系缓存、预分配容器容量
 * - API正确性：使用world.getRandom()确保可重现性，现代API调用
 * - 逻辑优化：水平方向搜索、智能回退机制、参数验证
 * - 兼容性：标签优先检查、跨版本支持、错误处理
 * - 最佳实践：性能监控、调试日志、内存限制
 * <p>
 * API正确性和兼容性优化：
 * - 使用RegistryEntry<Biome>替代BiomeHolder，符合1.21 API
 * - 使用BiomeTags标签检查替代字符串比较，提高mod兼容性
 * - 合并isLandBlock和isReplaceableBlock，减少重复检查
 * - 添加chunk加载检查，防止在未加载chunk中放置方块
 * - 添加植物生存检查，确保植物能正确生长
 * <p>
 * 逻辑和功能优化：
 * - 分层处理：边缘检测->过渡生成->植物装饰
 * - 分离植物收集和种植，简化维护和调试
 * - 添加密度衰减公式，创造更自然的植被分布
 * - 添加垂直检查，处理多层水体边缘
 * - 完善撤销系统，包含植被记录
 * - 全面配置验证，防止无效配置崩溃
 * <p>
 * 代码结构和维护性优化：
 * - 模块化设计：分解长方法为小方法，提高可测试性
 * - 常量提取：使用命名常量替代魔法数字，提高可维护性
 * - 完善Javadoc：添加详细的方法注释和参数说明
 * - 数据封装：使用ProcessingResult和VegetationPlacement封装数据
 * - 分层架构：清晰的方法职责分离，易于理解和维护
 * <p>
 * 概率分布优化：
 * - 沙滩类型：使用专门的沙滩概率分布，保持更多沙子
 * - 减少泥土比例：远离水岸的泥土方块比例大幅降低
 * - 自然过渡：更符合真实沙滩的过渡效果
 * - 三阶段过渡：实现沙子→泥土→草方块的渐进过渡，创造更自然的水岸效果
 * <p>
 * 自适应生物群系优化：
 * - 修复参数传递：避免重复获取生物群系信息
 * - 标签优先级：按重要性顺序检查生物群系标签
 * - 调试日志：添加详细的匹配过程日志
 * - 回退机制：完善智能回退逻辑，确保所有生物群系都有合适的处理
 * - 沙漠生物群系修复：确保沙漠使用沙滩类型而不是堤岸类型
 * - 三阶段过渡：所有水岸类型都支持渐进过渡到草方块
 */
public class ShorelineProcessHandler {
    private final PushdozerConfig config;
    // 移除全局Random实例，改为使用world.getRandom()确保可重现性
    
    // 常量定义：提高可维护性和可读性
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

    public ShorelineProcessHandler(PushdozerConfig config) {
        if (config == null) {
            throw new IllegalArgumentException("PushdozerConfig cannot be null");
        }
        
        // 配置验证：检查所有配置参数
        validateConfig(config);
        
        this.config = config;
        // 移除random初始化，改为使用world.getRandom()
    }
    
    /**
     * 验证配置参数，防止无效配置导致崩溃
     */
    private void validateConfig(PushdozerConfig config) {
        if (config.getShorelineWidth() < 1) {
            PushdozerMod.LOGGER.warn("Invalid shoreline width {}, resetting to default ({})", config.getShorelineWidth(), DEFAULT_SHORELINE_WIDTH);
            config.setShorelineWidth(DEFAULT_SHORELINE_WIDTH);
        }
        
        if (config.getVegetationDensity() < 0.0f || config.getVegetationDensity() > 1.0f) {
            PushdozerMod.LOGGER.warn("Invalid vegetation density {}, resetting to default ({})", config.getVegetationDensity(), DEFAULT_VEGETATION_DENSITY);
            config.setVegetationDensity(DEFAULT_VEGETATION_DENSITY);
        }
        
        if (config.getShorelineWidth() > MAX_SHORELINE_WIDTH) {
            PushdozerMod.LOGGER.warn("Shoreline width {} is too large, capping at {}", config.getShorelineWidth(), MAX_SHORELINE_WIDTH);
            config.setShorelineWidth(MAX_SHORELINE_WIDTH);
        }
    }
    
    /**
     * 验证输入参数
     * 代码结构优化：提取参数验证逻辑
     */
    private boolean validateParameters(PlayerEntity player, World world) {
        if (player == null) {
            PushdozerMod.LOGGER.warn("Invalid parameters for shoreline processing: player is null");
            return false;
        }
        return true;
    }
    
    /**
     * 获取处理形状
     * 代码结构优化：提取形状获取逻辑
     */
    private GeometryShape getProcessingShape(PlayerEntity player) {
        BlockPos basePos = ShapeUtil.getTargetBlockPos(player, config);
        GeometryShape shape = ShapeUtil.createShape(player, config, basePos);

        if (shape == null) {
            PushdozerMod.LOGGER.debug("No valid shape created for shoreline processing");
            return null;
        }
        return shape;
    }
    
    /**
     * 计算水岸过渡
     * 代码结构优化：提取过渡计算逻辑
     */
    private Map<BlockPos, ShorelineTransition> computeShorelineTransitions(World world, Set<BlockPos> shorelineEdges) {
        // 性能优化：预分配容量，减少rehash
        int expectedSize = Math.min(shorelineEdges.size() * 2, MAX_VISITED_BLOCKS);
        Map<BlockPos, ShorelineTransition> transitions = new HashMap<>(expectedSize);
        
        // 使用队列进行广度优先搜索
        Queue<BlockPos> queue = new LinkedList<>(shorelineEdges);
        Set<BlockPos> visited = new HashSet<>(shorelineEdges);
        
        // 性能优化：缓存生物群系查询，避免重复API调用
        Map<BlockPos, Biome> biomeCache = new HashMap<>();

        // 初始化第一层 (distance = 1)
        initializeFirstLayer(world, shorelineEdges, transitions, biomeCache);

        // 逐层向内陆扩展 (BFS)
        expandShorelineLayers(world, queue, visited, transitions, biomeCache);

        return transitions;
    }
    
    /**
     * 初始化第一层过渡
     * 代码结构优化：提取第一层初始化逻辑
     */
    private void initializeFirstLayer(World world, Set<BlockPos> shorelineEdges, 
                                    Map<BlockPos, ShorelineTransition> transitions, 
                                    Map<BlockPos, Biome> biomeCache) {
        for (BlockPos edgePos : shorelineEdges) {
            Biome biome = biomeCache.computeIfAbsent(edgePos, p -> world.getBiome(p).value());
            BlockState newState = generateShorelineBlock(world, edgePos, 1, biome);
            if (newState != null && isReplaceableLandBlock(world, edgePos, world.getBlockState(edgePos))) {
                transitions.put(edgePos, ShorelineTransition.valid(edgePos, newState, 1));
            }
        }
    }
    
    /**
     * 扩展水岸层
     * 代码结构优化：提取BFS扩展逻辑
     */
    private void expandShorelineLayers(World world, Queue<BlockPos> queue, Set<BlockPos> visited,
                                     Map<BlockPos, ShorelineTransition> transitions, 
                                     Map<BlockPos, Biome> biomeCache) {
        int currentDistance = 1;
        int iterationCount = 0;
        
        while (!queue.isEmpty() && currentDistance < config.getShorelineWidth() && iterationCount < MAX_ITERATIONS) {
            int levelSize = queue.size();
            currentDistance++;
            iterationCount++;

            for (int i = 0; i < levelSize; i++) {
                BlockPos currentPos = queue.poll();
                if (currentPos == null) continue;
                
                processNeighbors(world, currentPos, queue, visited, transitions, biomeCache, currentDistance);
            }
        }
    }
    
    /**
     * 处理邻居方块
     * 优化：支持水平和垂直方向的邻居处理
     * 处理水岸的水平扩展和垂直扩展
     */
    private void processNeighbors(World world, BlockPos currentPos, Queue<BlockPos> queue, 
                                Set<BlockPos> visited, Map<BlockPos, ShorelineTransition> transitions,
                                Map<BlockPos, Biome> biomeCache, int currentDistance) {
        // 处理水平方向邻居（XZ平面）
        for (Direction dir : Direction.Type.HORIZONTAL) {
            processNeighbor(world, currentPos, dir, queue, visited, transitions, biomeCache, currentDistance);
        }
        
        // 处理垂直方向邻居（Y轴）- 支持水岸的垂直扩展
        for (Direction dir : Direction.Type.VERTICAL) {
            processNeighbor(world, currentPos, dir, queue, visited, transitions, biomeCache, currentDistance);
        }
    }
    
    /**
     * 处理单个邻居方块
     * 提取公共逻辑，支持任意方向的邻居处理
     */
    private void processNeighbor(World world, BlockPos currentPos, Direction dir, Queue<BlockPos> queue, 
                               Set<BlockPos> visited, Map<BlockPos, ShorelineTransition> transitions,
                               Map<BlockPos, Biome> biomeCache, int currentDistance) {
        BlockPos neighborPos = currentPos.offset(dir);

        // 性能优化：添加visited大小限制，防止内存爆炸
        if (visited.size() >= MAX_VISITED_BLOCKS) {
            PushdozerMod.LOGGER.warn("Shoreline processing reached maximum visited blocks limit");
            return;
        }

        // 如果邻居未被访问过，并且是可处理的陆地方块
        if (!visited.contains(neighborPos) && isReplaceableLandBlock(world, neighborPos, world.getBlockState(neighborPos))) {
            visited.add(neighborPos);
            queue.add(neighborPos);

            // 为这个新位置生成过渡方块（使用缓存的生物群系）
            Biome biome = biomeCache.computeIfAbsent(neighborPos, p -> world.getBiome(p).value());
            BlockState newState = generateShorelineBlock(world, neighborPos, currentDistance, biome);

            if (newState != null && isReplaceableLandBlock(world, neighborPos, world.getBlockState(neighborPos))) {
                transitions.put(neighborPos, ShorelineTransition.valid(neighborPos, newState, currentDistance));
            }
        }
    }

    /**
     * 处理水岸生成的主要方法
     * 使用分层架构：边缘检测 -> 过渡计算 -> 应用变化 -> 植物装饰
     */
    public void handleShorelineProcess(PlayerEntity player, World world) {
        if (world.isClient()) {
            return;
        }

        // 参数验证
        if (!validateParameters(player, world)) {
            return;
        }

        // 获取形状
        GeometryShape shape = getProcessingShape(player);
        if (shape == null) {
            return;
        }

        // 分层处理：边缘检测 -> 过渡计算 -> 应用变化 -> 植物装饰
        Set<BlockPos> shorelineEdges = findShorelineEdges(world, shape);
        if (shorelineEdges.isEmpty()) {
            return;
        }

        Map<BlockPos, ShorelineTransition> transitions = computeShorelineTransitions(world, shorelineEdges);
        ProcessingResult result = applyShorelineTransitions(world, player, transitions);
        
        List<VegetationPlacement> vegetationPlacements = collectVegetationPositions(world, result.vegetationPositions);
        int vegetationCount = plantVegetation(world, vegetationPlacements, result.affectedPositions, result.originalStates, result.newStates, player);

        // 创建撤销操作并发送消息
        createUndoActionAndNotifyPlayer(player, result, vegetationCount);
    }
    
    /**
     * 应用水岸过渡
     * 代码结构优化：提取过渡应用逻辑
     */
    private ProcessingResult applyShorelineTransitions(World world, PlayerEntity player, 
                                                      Map<BlockPos, ShorelineTransition> transitions) {
        List<BlockPos> affectedPositions = new ArrayList<>();
        List<BlockState> originalStates = new ArrayList<>();
        List<BlockState> newStates = new ArrayList<>();
        List<BlockPos> vegetationPositions = new ArrayList<>();
        int processedCount = 0;

        for (ShorelineTransition transition : transitions.values()) {
            if (transition.isValid() && isValidHeightForShorelineProcess(transition.pos, player)) {
                // 兼容性检查：确保chunk已加载
                if (!isChunkLoaded(world, transition.pos)) {
                    continue; // 跳过未加载的chunk
                }
                
                BlockState originalState = world.getBlockState(transition.pos);
                world.setBlockState(transition.pos, transition.newState);
                affectedPositions.add(transition.pos);
                originalStates.add(originalState);
                newStates.add(transition.newState);
                processedCount++;
                
                        // 记录适合种植植物的位置
        if (shouldPlantVegetation(world, transition.pos, transition.distance, player)) {
            vegetationPositions.add(transition.pos);
            // 调试：记录植物位置收集
            PushdozerMod.LOGGER.debug("Added vegetation position at {} with distance {}", transition.pos, transition.distance);
        }
            }
        }

        return new ProcessingResult(affectedPositions, originalStates, newStates, vegetationPositions, processedCount, 0);
    }
    
    /**
     * 创建撤销操作
     * 代码结构优化：提取撤销逻辑
     */
    private void createUndoActionAndNotifyPlayer(PlayerEntity player, ProcessingResult result, int vegetationCount) {
        // 创建撤销操作
        if (!result.affectedPositions.isEmpty()) {
            UndoAction undoAction = new UndoAction(
                UndoAction.ActionType.PLACE,
                result.affectedPositions,
                result.originalStates,
                result.newStates
            );
            PushdozerMod.pushUndoAction(player, undoAction);
        }

        // 性能监控：记录处理统计
        if (result.processedCount > 0) {
            PushdozerMod.LOGGER.debug("Shoreline processing completed: {} blocks processed, {} plants planted", 
                result.processedCount, vegetationCount);
        } else {
            PushdozerMod.LOGGER.debug("Shoreline processing completed with no changes");
        }
    }

    /**
     * 检查标高是否适合水岸处理
     * 根据配置的标高限制模式进行检查
     * 
     * @param pos 要检查的位置
     * @param player 执行操作的玩家
     * @return 如果标高适合处理返回true，否则返回false
     */
    private boolean isValidHeightForShorelineProcess(BlockPos pos, PlayerEntity player) {
        // 如果两个标高限制都未启用，则不限制标高
        if (!config.isShorelineHeightAboveEnabled() && !config.isShorelineHeightBelowEnabled()) {
            return true;
        }

        int targetHeight = getTargetHeight(player);
        int posY = pos.getY();

        if (config.isShorelineHeightAboveEnabled()) {
            // 标高上操作：只能在标高以上进行
            return posY >= targetHeight;
        } else if (config.isShorelineHeightBelowEnabled()) {
            // 标高下操作：只能在标高以下进行
            return posY <= targetHeight;
        }

        return true; // 默认允许
    }

    /**
     * 获取目标标高
     * 根据标高配置模式返回相应的标高值
     * 
     * @param player 执行操作的玩家
     * @return 目标标高值
     */
    private int getTargetHeight(PlayerEntity player) {
        PushdozerConfig.HeightMode heightMode = config.getHeightMode();

        return switch (heightMode) {
            case FOLLOW_PLAYER -> player.getBlockY();
            case LOCKED_ONCE, CUSTOM -> config.getLockedHeight();
            default -> 0; // 对于NO_LIMIT模式，返回0作为默认值（实际不会被使用）
        };
    }

    /**
     * 水岸过渡信息
     */
    private static class ShorelineTransition {
        final BlockPos pos;
        final BlockState newState;
        final int distance;
        final boolean isValid;

        ShorelineTransition(BlockPos pos, BlockState newState, int distance, boolean isValid) {
            this.pos = pos;
            this.newState = newState;
            this.distance = distance;
            this.isValid = isValid;
        }
        static ShorelineTransition valid(BlockPos pos, BlockState newState, int distance) {
            return new ShorelineTransition(pos, newState, distance, true);
        }

        boolean isValid() {
            return isValid;
        }
    }
    
    /**
     * 植物种植信息
     * 逻辑优化：分离植物收集和种植
     */
    private static class VegetationPlacement {
        final BlockPos pos;
        final BlockState plant;
        final boolean isTallPlant;
        final BlockState groundBlock;

        VegetationPlacement(BlockPos pos, BlockState plant, boolean isTallPlant, BlockState groundBlock) {
            this.pos = pos;
            this.plant = plant;
            this.isTallPlant = isTallPlant;
            this.groundBlock = groundBlock;
        }
    }
    
    /**
     * 处理结果封装
     * 代码结构优化：封装处理结果数据
     */
    private static class ProcessingResult {
        final List<BlockPos> affectedPositions;
        final List<BlockState> originalStates;
        final List<BlockState> newStates;
        final List<BlockPos> vegetationPositions;
        final int processedCount;
        final int iterationCount;

        ProcessingResult(List<BlockPos> affectedPositions, List<BlockState> originalStates, 
                        List<BlockState> newStates, List<BlockPos> vegetationPositions, 
                        int processedCount, int iterationCount) {
            this.affectedPositions = affectedPositions;
            this.originalStates = originalStates;
            this.newStates = newStates;
            this.vegetationPositions = vegetationPositions;
            this.processedCount = processedCount;
            this.iterationCount = iterationCount;
        }
    }

    /**
     * 根据生物群系和距离生成水岸方块
     * 专注于真正的过渡效果，而不是粗暴放置
     * 
     * @param world 世界实例
     * @param pos 方块位置
     * @param distance 离水的距离
     * @param biome 生物群系（避免重复查询）
     * @return 生成的方块状态，null表示保持原样
     */
    private BlockState generateShorelineBlock(World world, BlockPos pos, int distance, Biome biome) {
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

    /**
     * 检查方块是否为可替换的陆地方块
     * 优化：使用更简洁的方法，优先使用标签和isReplaceable()
     * 修复：包含自定义水岸方块，确保自定义方块可以被再次替换
     * 修复：忽略植物等装饰物，只替换地面方块
     * 优化：只替换表面方块，跳过内部方块
     * 
     * @param world 世界实例
     * @param pos 方块位置
     * @param state 要检查的方块状态
     * @return 如果方块可以被替换返回true，否则返回false
     */
    private boolean isReplaceableLandBlock(World world, BlockPos pos, BlockState state) {
        // 检查是否为水方块或空气方块
        if (state.getFluidState().isIn(FluidTags.WATER) || state.isAir()) {
            return false;
        }
        
        // 修复：忽略植物等装饰物，直接跳过
        if (isPlantOrDecoration(state)) {
            return false;
        }
        
        // 优化：只替换表面方块，跳过内部方块
        if (!isSurfaceBlock(world, pos)) {
            return false;
        }
        
        // 修复：检查是否为自定义水岸方块
        List<Block> customBlocks = config.getCustomShorelineBlockList();
        if (!customBlocks.isEmpty() && customBlocks.contains(state.getBlock())) {
            return true; // 自定义水岸方块可以被替换
        }
        
        // 优化：使用isReplaceable()和标签检查，简化逻辑
        return state.isReplaceable() || 
               state.isIn(BlockTags.DIRT) || 
               state.isIn(BlockTags.SAND) || 
               state.isIn(BlockTags.LOGS) ||
               state.getBlock() == Blocks.GRASS_BLOCK ||
               state.getBlock() == Blocks.STONE ||
               state.getBlock() == Blocks.COBBLESTONE ||
               state.getBlock() == Blocks.GRAVEL ||
               state.getBlock() == Blocks.SANDSTONE ||
               state.getBlock() == Blocks.SMOOTH_SANDSTONE ||
               state.getBlock() == Blocks.RED_SANDSTONE ||
               state.getBlock() == Blocks.DIRT_PATH ||
               state.getBlock() == Blocks.COARSE_DIRT ||
               state.getBlock() == Blocks.PODZOL ||
               state.getBlock() == Blocks.MYCELIUM ||
               state.getBlock() == Blocks.SNOW_BLOCK ||
               state.getBlock() == Blocks.ICE ||
               state.getBlock() == Blocks.PACKED_ICE ||
               state.getBlock() == Blocks.ANDESITE ||
               state.getBlock() == Blocks.DIORITE ||
               state.getBlock() == Blocks.GRANITE ||
               state.getBlock() == Blocks.DEEPSLATE ||
               state.getBlock() == Blocks.TUFF ||
               state.getBlock() == Blocks.CALCITE ||
               state.getBlock() == Blocks.SMOOTH_BASALT ||
               state.getBlock() == Blocks.ROOTED_DIRT ||
               state.getBlock() == Blocks.MOSS_BLOCK ||
               state.getBlock() == Blocks.SUSPICIOUS_SAND ||
               state.getBlock() == Blocks.SUSPICIOUS_GRAVEL ||
               state.getBlock() == Blocks.CLAY ||
               state.getBlock() == Blocks.MOSS_CARPET ||
               state.getBlock() == Blocks.MUD; // 修复：添加MUD方块
    }

    /**
     * 逻辑优化：收集植物种植位置
     * 分离植物收集和种植，简化维护
     * 优化：支持高植物（两格以上高度）的种植检查
     */
    private List<VegetationPlacement> collectVegetationPositions(World world, List<BlockPos> positions) {
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
            if (!isSolidBlock(world, pos, belowState)) {
                PushdozerMod.LOGGER.debug("Position {} has non-solid block below: {}, skipping", pos, belowState.getBlock().toString());
                continue;
            }
            
            BlockState plant = getVegetationForBiome(world, pos, world.getBiome(pos).value(), belowState);
            PushdozerMod.LOGGER.debug("getVegetationForBiome returned: {} for position {}", 
                plant != null ? plant.getBlock().toString() : "null", pos);
            
            if (plant != null && canPlantGrowOnBlock(world, pos, plant.getBlock(), belowState)) {
                // 优化：检查高植物种植条件
                int requiredHeight = getPlantRequiredHeight(world, plant.getBlock());
                if (hasEnoughSpaceForPlant(world, pos, requiredHeight)) {
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
    private boolean shouldPlantVegetation(World world, BlockPos pos, int distance, PlayerEntity player) {
        // 检查是否启用植物种植
        if (!config.isPlantVegetationEnabled()) return false;
        
        // 扩大植物种植范围：距离水1-4格的位置都可以种植植物
        if (distance < 1 || distance > 8) return false;
        
        // 修复：添加标高限制检查
        if (!isValidHeightForShorelineProcess(pos, player)) {
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
    private int plantVegetation(World world, List<VegetationPlacement> placements, 
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
            if (!isChunkLoaded(world, pos) || !isChunkLoaded(world, plantPos)) {
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
                if (!isValidHeightForShorelineProcess(upperPos, player)) {
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
                    
                    // 放置植物
                    world.setBlockState(plantPos, lowerPlant);
                    world.setBlockState(upperPos, upperPlant);
                    
                    newStates.add(lowerPlant);
                    newStates.add(upperPlant);
                    plantedCount++;
                }
            } else {
                // 处理普通植物和高植物（非TallPlantBlock类型）
                int requiredHeight = getPlantRequiredHeight(world, placement.plant.getBlock());
                
                // 检查是否有足够空间
                if (!hasEnoughSpaceForPlant(world, plantPos, requiredHeight)) {
                    continue; // 空间不足，跳过
                }
                
                // 检查标高限制
                boolean heightValid = true;
                for (int i = 0; i < requiredHeight; i++) {
                    if (!isValidHeightForShorelineProcess(plantPos.up(i), player)) {
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
                    if (isDeadCoral(placement.plant.getBlock())) {
                        // 确保失活珊瑚不带有水属性
                        plantToPlace = placement.plant.getBlock().getDefaultState();
                        // 移除任何可能的水属性
                        if (plantToPlace.contains(Properties.WATERLOGGED)) {
                            plantToPlace = plantToPlace.with(Properties.WATERLOGGED, false);
                        }
                    }
                    
                    world.setBlockState(plantPos, plantToPlace);
                    newStates.add(plantToPlace);
                } else {
                    // 高植物：根据类型进行特殊处理
                    placeHighPlant(world, plantPos, placement.plant, requiredHeight, newStates);
                    
                    // 特殊处理：仙人掌有机会在顶部放置仙人掌花
                    if (placement.plant.getBlock() == Blocks.CACTUS && 
                        config.getShorelineType() == PushdozerConfig.ShorelineType.BEACH) {
                        // 在沙滩类型时，仙人掌有30%概率在顶部放置仙人掌花
                        if (world.getRandom().nextFloat() < 0.3f) {
                            BlockPos flowerPos = plantPos.up(requiredHeight);
                            if (world.getBlockState(flowerPos).isAir()) {
                                BlockState flowerState = Blocks.CACTUS_FLOWER.getDefaultState();
                                world.setBlockState(flowerPos, flowerState);
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
    private void placeHighPlant(World world, BlockPos pos, BlockState plantState, int height, List<BlockState> newStates) {
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
                world.setBlockState(plantPos, caneState);
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
                world.setBlockState(plantPos, bambooState);
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
                world.setBlockState(plantPos, cactusState);
                newStates.add(cactusState);
            }
        } else if (plantBlock == Blocks.KELP) {
            // 海带：使用传入的随机高度，每格都有随机生长阶段
            for (int i = 0; i < height; i++) {
                BlockPos plantPos = pos.up(i);
                BlockState kelpState = Blocks.KELP.getDefaultState().with(KelpBlock.AGE, random.nextInt(4));
                world.setBlockState(plantPos, kelpState);
                newStates.add(kelpState);
            }
        } else if (plantBlock == Blocks.CHORUS_PLANT) {
            // 紫颂植物：使用传入的随机高度
            for (int i = 0; i < height; i++) {
                BlockPos plantPos = pos.up(i);
                BlockState chorusState = Blocks.CHORUS_PLANT.getDefaultState();
                world.setBlockState(plantPos, chorusState);
                newStates.add(chorusState);
            }
        } else if (plantBlock == Blocks.CHORUS_FLOWER) {
            // 紫颂花：使用传入的随机高度
            for (int i = 0; i < height; i++) {
                BlockPos plantPos = pos.up(i);
                BlockState flowerState = Blocks.CHORUS_FLOWER.getDefaultState();
                world.setBlockState(plantPos, flowerState);
                newStates.add(flowerState);
            }
        } else {
            // 其他高植物：默认放置
            world.setBlockState(pos, plantState);
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
                    return canPlantCustomBlockAt(world, pos.up(), plantBlock);
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
        if (isPotted(plantBlock)) {
            boolean spotFree = currentState.isAir() || currentState.isReplaceable();
            boolean notInWater = !currentState.getFluidState().isIn(FluidTags.WATER);
            boolean solidBelow = groundBlock.isSolidBlock(world, pos.down());
            boolean notPlantBelow = !isPlantBlock(groundBlock.getBlock()) && !isPotted(groundBlock.getBlock());
            return spotFree && notInWater && solidBelow && notPlantBelow;
        }
        
        // 农作物（AGE_7 特征或常见作物方块）
        boolean isCrop = isCropBlock(plantBlock) || plantBlock.getDefaultState().contains(Properties.AGE_7);
        if (isCrop) {
            return groundBlock.isOf(Blocks.FARMLAND) && (currentState.isAir() || currentState.isReplaceable());
        }
        
        // 活珊瑚：必须在水中或与水相邻，且下方有合适的基底
        boolean adjacentToWater = isAdjacentToWater(world, pos);
        
        if (isLiveCoral(plantBlock)) {
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
        if (isAquatic(plantBlock)) {
            return inWater;
        }
        
        // 失活珊瑚：允许放在任何实体方块上（当前位置需为空气/可替换），忽略具体基底类型
        if (isDeadCoral(plantBlock)) {
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
     * 检查方块是否为植物或装饰物
     * 这些方块应该在水岸处理时被跳过，不被替换
     * 
     * @param state 要检查的方块状态
     * @return 如果方块是植物或装饰物返回true，否则返回false
     */
    private boolean isPlantOrDecoration(BlockState state) {
        // 检查是否为植物方块
        if (state.getBlock() instanceof PlantBlock ||
            state.getBlock() instanceof TallPlantBlock ||
            state.getBlock() instanceof FlowerBlock ||
            state.getBlock() instanceof SaplingBlock ||
            state.getBlock() instanceof MushroomBlock) {
            return true;
        }
        
        // 检查具体的植物和装饰物方块
        return state.getBlock() == Blocks.SHORT_GRASS ||
               state.getBlock() == Blocks.TALL_GRASS ||
               state.getBlock() == Blocks.FERN ||
               state.getBlock() == Blocks.LARGE_FERN ||
               state.getBlock() == Blocks.DEAD_BUSH ||
               state.getBlock() == Blocks.SUGAR_CANE ||
               state.getBlock() == Blocks.CACTUS ||
               state.getBlock() == Blocks.CACTUS_FLOWER ||
               state.getBlock() == Blocks.SEAGRASS ||
               state.getBlock() == Blocks.KELP ||
               state.getBlock() == Blocks.DANDELION ||
               state.getBlock() == Blocks.POPPY ||
               state.getBlock() == Blocks.BLUE_ORCHID ||
               state.getBlock() == Blocks.ALLIUM ||
               state.getBlock() == Blocks.AZURE_BLUET ||
               state.getBlock() == Blocks.RED_TULIP ||
               state.getBlock() == Blocks.ORANGE_TULIP ||
               state.getBlock() == Blocks.WHITE_TULIP ||
               state.getBlock() == Blocks.PINK_TULIP ||
               state.getBlock() == Blocks.OXEYE_DAISY ||
               state.getBlock() == Blocks.CORNFLOWER ||
               state.getBlock() == Blocks.LILY_OF_THE_VALLEY ||
               state.getBlock() == Blocks.SUNFLOWER ||
               state.getBlock() == Blocks.LILAC ||
               state.getBlock() == Blocks.ROSE_BUSH ||
               state.getBlock() == Blocks.PEONY ||
               state.getBlock() == Blocks.OAK_SAPLING ||
               state.getBlock() == Blocks.SPRUCE_SAPLING ||
               state.getBlock() == Blocks.BIRCH_SAPLING ||
               state.getBlock() == Blocks.JUNGLE_SAPLING ||
               state.getBlock() == Blocks.ACACIA_SAPLING ||
               state.getBlock() == Blocks.DARK_OAK_SAPLING ||
               state.getBlock() == Blocks.BROWN_MUSHROOM ||
               state.getBlock() == Blocks.RED_MUSHROOM ||
               state.getBlock() == Blocks.CRIMSON_FUNGUS ||
               state.getBlock() == Blocks.WARPED_FUNGUS ||
               state.getBlock() == Blocks.CRIMSON_ROOTS ||
               state.getBlock() == Blocks.WARPED_ROOTS ||
               state.getBlock() == Blocks.NETHER_SPROUTS ||
               state.getBlock() == Blocks.WEEPING_VINES ||
               state.getBlock() == Blocks.TWISTING_VINES ||
               state.getBlock() == Blocks.GLOW_LICHEN ||
               state.getBlock() == Blocks.MOSS_CARPET ||
               state.getBlock() == Blocks.SMALL_DRIPLEAF ||
               state.getBlock() == Blocks.BIG_DRIPLEAF ||
               state.getBlock() == Blocks.SPORE_BLOSSOM ||
               state.getBlock() == Blocks.AZALEA ||
               state.getBlock() == Blocks.FLOWERING_AZALEA ||
               state.getBlock() == Blocks.PINK_PETALS ||
               state.getBlock() == Blocks.TORCHFLOWER ||
               state.getBlock() == Blocks.PITCHER_PLANT ||
               state.getBlock() == Blocks.CHERRY_SAPLING ||
               state.getBlock() == Blocks.BAMBOO ||
               state.getBlock() == Blocks.BAMBOO_SAPLING ||
               state.getBlock() == Blocks.CHORUS_PLANT ||
               state.getBlock() == Blocks.CHORUS_FLOWER ||
               state.getBlock() == Blocks.NETHER_WART ||
               state.getBlock() == Blocks.SHROOMLIGHT ||
               state.getBlock() == Blocks.SOUL_FIRE ||
               state.getBlock() == Blocks.SOUL_TORCH ||
               state.getBlock() == Blocks.SOUL_WALL_TORCH ||
               state.getBlock() == Blocks.SOUL_LANTERN ||
               state.getBlock() == Blocks.SOUL_CAMPFIRE ||
               state.getBlock() == Blocks.TORCH ||
               state.getBlock() == Blocks.WALL_TORCH ||
               state.getBlock() == Blocks.LANTERN ||
               state.getBlock() == Blocks.CAMPFIRE ||
               state.getBlock() == Blocks.SOUL_CAMPFIRE ||
               state.getBlock() == Blocks.FIRE ||
               state.getBlock() == Blocks.LAVA ||
               state.getBlock() == Blocks.WATER ||
               state.getBlock() == Blocks.BUBBLE_COLUMN ||
               state.getBlock() == Blocks.SEA_PICKLE ||
               state.getBlock() == Blocks.TUBE_CORAL ||
               state.getBlock() == Blocks.BRAIN_CORAL ||
               state.getBlock() == Blocks.BUBBLE_CORAL ||
               state.getBlock() == Blocks.FIRE_CORAL ||
               state.getBlock() == Blocks.HORN_CORAL ||
               state.getBlock() == Blocks.TUBE_CORAL_FAN ||
               state.getBlock() == Blocks.BRAIN_CORAL_FAN ||
               state.getBlock() == Blocks.BUBBLE_CORAL_FAN ||
               state.getBlock() == Blocks.FIRE_CORAL_FAN ||
               state.getBlock() == Blocks.HORN_CORAL_FAN ||
               state.getBlock() == Blocks.TUBE_CORAL_WALL_FAN ||
               state.getBlock() == Blocks.BRAIN_CORAL_WALL_FAN ||
               state.getBlock() == Blocks.BUBBLE_CORAL_WALL_FAN ||
               state.getBlock() == Blocks.FIRE_CORAL_WALL_FAN ||
               state.getBlock() == Blocks.HORN_CORAL_WALL_FAN;
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
        if (aboveState.isAir() || aboveState.getFluidState().isIn(FluidTags.WATER) || isPlantOrDecoration(aboveState)) {
            return true;
        }
        
        // 检查水平方向是否有空气、水或植物
        for (Direction dir : Direction.Type.HORIZONTAL) {
            BlockPos neighborPos = pos.offset(dir);
            BlockState neighborState = world.getBlockState(neighborPos);
            if (neighborState.isAir() || neighborState.getFluidState().isIn(FluidTags.WATER) || isPlantOrDecoration(neighborState)) {
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
     * 检查是否有足够空间种植植物
     * 支持陆地、水中和半水中的高植物种植
     * 
     * @param world 世界实例
     * @param pos 种植位置
     * @param requiredHeight 所需高度
     * @return 如果有足够空间返回true，否则返回false
     */
    private boolean hasEnoughSpaceForPlant(World world, BlockPos pos, int requiredHeight) {
        // 检查从种植位置向上的所有格数
        for (int i = 1; i < requiredHeight; i++) {
            BlockPos checkPos = pos.up(i);
            BlockState checkState = world.getBlockState(checkPos);
            
            // 允许空气、水或植物（可以穿过现有植物）
            if (!checkState.isAir() && 
                !checkState.getFluidState().isIn(FluidTags.WATER) && 
                !isPlantOrDecoration(checkState)) {
                return false; // 空间不足
            }
        }
        
        return true; // 空间充足
    }

    /**
     * 检查方块是否为实体方块
     * 使用Minecraft内置的更通用的方法来简化判断
     * 实体方块可以支撑植物，水方块不能
     */
    private boolean isSolidBlock(World world, BlockPos pos, BlockState state) {
        // 检查是否为水方块
        if (state.getFluidState().isIn(FluidTags.WATER)) {
            return false;
        }
        
        // 使用Minecraft内置方法判断方块是否有碰撞体积（即是否为实体）
        return !state.getCollisionShape(world, pos).isEmpty();
    }
    
    /**
     * 自定义种植时的动态可种规则
     * 参考批量种植的种植条件逻辑，简化并统一检查逻辑
     */
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
            
            return validBase && upperReplaceable;
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
            
            return validBase && upperReplaceable;
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
            return spotFree && validBase;
        }

        // 普通：放宽基底，草/泥土/沙/雪顶/泥土路等，且当前位置可替换或空气
        boolean validBase = below.isIn(BlockTags.DIRT)
                || below.isOf(Blocks.GRASS_BLOCK)
                || below.isIn(BlockTags.SAND)
                || below.isIn(BlockTags.SNOW)
                || below.isOf(Blocks.DIRT_PATH)
                || below.isOf(Blocks.MOSS_BLOCK)
                || below.isOf(Blocks.CLAY);
        
        if (!validBase) {
            PushdozerMod.LOGGER.debug("Custom plant {} cannot grow on invalid base block {} at pos {}",
                    block, below.getBlock().toString(), pos);
            return false;
        }
        
        boolean spotFree = targetState.isAir() || targetState.isReplaceable();
        boolean canPlace = state.canPlaceAt(world, pos);
        
        if (!spotFree) {
            PushdozerMod.LOGGER.debug("Custom plant {} cannot grow at pos {}: spot not free (current state: {})",
                    block, pos, targetState.getBlock().toString());
            return false;
        }
        
        if (!canPlace) {
            PushdozerMod.LOGGER.debug("Custom plant {} cannot be placed at pos {}: canPlaceAt returned false",
                    block, pos);
            return false;
        }
        
        PushdozerMod.LOGGER.debug("Custom plant {} can grow at pos {} on base block {}",
                block, pos, below.getBlock().toString());
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
     * 检查方块是否为盆栽
     */
    private boolean isPotted(Block block) {
        return (block instanceof FlowerPotBlock) && block != Blocks.FLOWER_POT;
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
                block == Blocks.CACTUS || block == Blocks.CACTUS_FLOWER || block == Blocks.SUGAR_CANE || block == Blocks.BAMBOO ||
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
                isLiveCoral(block) || isDeadCoral(block) ||
                isCropBlock(block) ||
                block instanceof TallPlantBlock ||
                block.toString().toLowerCase().contains("leaf_litter");
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
     * 珊瑚类型判断方法
     */
    private boolean isLiveCoral(Block block) {
        String id = block.toString().toLowerCase();
        return id.contains("coral") && !id.contains("dead");
    }
    
    private boolean isDeadCoral(Block block) {
        String id = block.toString().toLowerCase();
        return id.contains("coral") && id.contains("dead");
    }
    
    /**
     * 检查方块是否为水生植物
     */
    private boolean isAquatic(Block block) {
        return block == Blocks.SEAGRASS
                || block == Blocks.TALL_SEAGRASS
                || block == Blocks.KELP
                || block == Blocks.KELP_PLANT
                || block == Blocks.SEA_PICKLE;
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
                    if (isDeadCoral(selectedPlant)) {
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
    private boolean isChunkLoaded(World world, BlockPos pos) {
        return world.isChunkLoaded(pos.getX() >> 4, pos.getZ() >> 4);
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