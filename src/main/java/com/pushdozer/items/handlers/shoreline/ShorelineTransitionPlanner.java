package com.pushdozer.items.handlers.shoreline;

import com.pushdozer.PushdozerMod;
import com.pushdozer.config.PushdozerConfig;
import com.pushdozer.items.handlers.shoreline.model.ShorelineResult;
import com.pushdozer.items.handlers.shoreline.model.ShorelineTransition;
import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import net.minecraft.world.biome.Biome;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

public class ShorelineTransitionPlanner {
    private static final int MAX_VISITED_BLOCKS = 10000;
    private static final int MAX_ITERATIONS = 10000;

    private final PushdozerConfig config;
    private final ShorelineBlockGenerator blockGenerator;
    private final ShorelineEdgeFinder edgeFinder;

    public ShorelineTransitionPlanner(PushdozerConfig config, ShorelineBlockGenerator blockGenerator, ShorelineEdgeFinder edgeFinder) {
        this.config = config;
        this.blockGenerator = blockGenerator;
        this.edgeFinder = edgeFinder;
    }
    public Map<BlockPos, ShorelineTransition> computeShorelineTransitions(World world, Set<BlockPos> shorelineEdges) {
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
            BlockState newState = blockGenerator.generate(world, edgePos, 1, biome);
            if (newState != null && edgeFinder.isReplaceableLandBlock(world, edgePos, world.getBlockState(edgePos))) {
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
        if (!visited.contains(neighborPos) && edgeFinder.isReplaceableLandBlock(world, neighborPos, world.getBlockState(neighborPos))) {
            visited.add(neighborPos);
            queue.add(neighborPos);

            // 为这个新位置生成过渡方块（使用缓存的生物群系）
            Biome biome = biomeCache.computeIfAbsent(neighborPos, p -> world.getBiome(p).value());
            BlockState newState = blockGenerator.generate(world, neighborPos, currentDistance, biome);

            if (newState != null && edgeFinder.isReplaceableLandBlock(world, neighborPos, world.getBlockState(neighborPos))) {
                transitions.put(neighborPos, ShorelineTransition.valid(neighborPos, newState, currentDistance));
            }
        }
    }

    public ShorelineResult collectApplyableTransitions(World world, PlayerEntity player, Map<BlockPos, ShorelineTransition> transitions, ShorelineVegetationPlanner vegetationPlanner) {
        List<BlockPos> affectedPositions = new ArrayList<>();
        List<BlockState> originalStates = new ArrayList<>();
        List<BlockState> newStates = new ArrayList<>();
        List<BlockPos> vegetationPositions = new ArrayList<>();
        int processedCount = 0;

        for (ShorelineTransition transition : transitions.values()) {
            if (transition.isValid() && isValidHeightForShorelineProcess(transition.pos, player)) {
                // 兼容性检查：确保chunk已加载
                if (!edgeFinder.isChunkLoaded(world, transition.pos)) {
                    continue; // 跳过未加载的chunk
                }
                
                BlockState originalState = world.getBlockState(transition.pos);
                affectedPositions.add(transition.pos);
                originalStates.add(originalState);
                newStates.add(transition.newState);
                processedCount++;
                
                        // 记录适合种植植物的位置
        if (vegetationPlanner.shouldPlantVegetation(world, transition.pos, transition.distance, player)) {
            vegetationPositions.add(transition.pos);
            // 调试：记录植物位置收集
            PushdozerMod.LOGGER.debug("Added vegetation position at {} with distance {}", transition.pos, transition.distance);
        }
            }
        }

        return new ShorelineResult(affectedPositions, originalStates, newStates, vegetationPositions, processedCount, 0);
    }

    public boolean isValidHeightForShorelineProcess(BlockPos pos, PlayerEntity player) {
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
    public int getTargetHeight(PlayerEntity player) {
        PushdozerConfig.HeightMode heightMode = config.getHeightMode();

        return switch (heightMode) {
            case FOLLOW_PLAYER -> player.getBlockY();
            case LOCKED_ONCE, CUSTOM -> config.getLockedHeight();
            default -> 0; // 对于NO_LIMIT模式，返回0作为默认值（实际不会被使用）
        };
    }
}

