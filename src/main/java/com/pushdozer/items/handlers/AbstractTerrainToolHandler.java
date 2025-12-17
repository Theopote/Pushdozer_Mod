package com.pushdozer.items.handlers;

import com.pushdozer.PushdozerMod;
import com.pushdozer.config.PushdozerConfig;
import com.pushdozer.shapes.GeometryShape;
import com.pushdozer.util.ShapeUtil;
import com.pushdozer.operations.UndoAction;
import com.pushdozer.network.NetworkManager;
import net.minecraft.block.*;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.fluid.FluidState;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.*;

/**
 * 抽象地形工具处理器基类
 * 提供所有地形工具共享的基础功能，子类只需实现特定的高度计算算法
 */
public abstract class AbstractTerrainToolHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger("pushdozer");
    
    protected final PushdozerConfig config;
    
    // REFINED: 简化忽略方块列表，使用BlockTags替代大部分硬编码
    protected static final Set<Block> IGNORED_BLOCKS = Set.of(
        // 只保留没有合适标签的方块
        Blocks.VINE, Blocks.SNOW, Blocks.BROWN_MUSHROOM, Blocks.RED_MUSHROOM
        // 其他方块如树木、树叶、花等已通过BlockTags处理
    );

    public AbstractTerrainToolHandler(PushdozerConfig config) {
        this.config = config;
    }

    /**
     * 处理地形操作的主入口方法
     * 增加了多人游戏支持和权限验证
     */
    public void handleOperation(PlayerEntity player, World world, UndoAction.ActionType actionType) {
        if (world.isClient()) return;

        // 多人游戏权限检查
        if (!hasOperationPermission(player, world, actionType)) {
            return;
        }

        BlockPos basePos = ShapeUtil.getTargetBlockPos(player, config);
        GeometryShape shape = ShapeUtil.createShape(player, config, basePos);

        if (shape == null) {
            return;
        }

        List<BlockPos> affectedPositions = new ArrayList<>();
        List<BlockState> originalStates = new ArrayList<>();
        List<BlockState> newStates = new ArrayList<>();

        // 执行地形操作
        processTerrain(world, shape, basePos, affectedPositions, originalStates, newStates);

        // 创建撤销操作并同步到其他玩家
        if (!affectedPositions.isEmpty()) {
            UndoAction undoAction = new UndoAction(
                actionType,
                affectedPositions,
                originalStates,
                newStates
            );
            PushdozerMod.pushUndoAction(player, undoAction);
            
            // 在多人游戏中广播地形操作到其他玩家
            if (world instanceof ServerWorld serverWorld && !serverWorld.getServer().isSingleplayer()) {
                NetworkManager.broadcastTerrainOperation(
                    serverWorld,
                    actionType.name(),
                    affectedPositions,
                    newStates
                );
            }
        }
    }
    
    /**
     * 检查玩家是否有执行此操作的权限
     */
    private boolean hasOperationPermission(PlayerEntity player, World world, UndoAction.ActionType actionType) {
        // 单人游戏总是允许
        if (world.getServer() != null && world.getServer().isSingleplayer()) {
            return true;
        }
        
        // 多人游戏中所有玩家都可以使用地形工具
        if (player instanceof ServerPlayerEntity) {
            // 检查操作范围限制（防止恶意大范围操作）
            if (config.getRadius() > 100) {  // 提高限制到100格
                LOGGER.warn("玩家 {} 尝试执行过大范围操作: 半径 {}", 
                    player.getName().getString(), config.getRadius());
                return false;
            }
            
            // 可以在这里添加区域保护检查
            // 例如检查是否在保护区内等
        }
        
        return true;
    }

    /**
     * 处理地形的主要方法
     */
    protected void processTerrain(World world, GeometryShape shape, BlockPos brushCenter,
                                List<BlockPos> affectedPositions,
                                List<BlockState> originalStates,
                                List<BlockState> newStates) {
        // 1. 采集地形信息
        Map<BlockPos, TerrainColumn> columns = collectTerrainColumns(world, shape, brushCenter);
        
        if (columns.isEmpty()) {
            return;
        }

        // 2. 计算目标高度
        Map<BlockPos, Integer> targetHeights = new HashMap<>();
        for (Map.Entry<BlockPos, TerrainColumn> entry : columns.entrySet()) {
            BlockPos columnXZ = entry.getKey();
            TerrainColumn column = entry.getValue();
            
            int targetHeight = calculateTargetHeight(columns, column, columnXZ, brushCenter);
            targetHeights.put(columnXZ, targetHeight);
        }

        // 3. 应用高度变化
        for (Map.Entry<BlockPos, Integer> entry : targetHeights.entrySet()) {
            BlockPos columnXZ = entry.getKey();
            int targetHeight = entry.getValue();
            TerrainColumn column = columns.get(columnXZ);
            
            applyHeightChange(world, columnXZ, column, targetHeight,
                           affectedPositions, originalStates, newStates);
        }

        // 4. 后期处理
        postProcess(world, shape, affectedPositions, originalStates, newStates);
    }

    /**
     * 采集地形信息
     */
    protected Map<BlockPos, TerrainColumn> collectTerrainColumns(World world, GeometryShape shape, BlockPos brushCenter) {
        Map<BlockPos, TerrainColumn> columns = new HashMap<>();
        
        // 获取所有唯一的(X,Z)坐标
        Set<BlockPos> uniqueXZPositions = shape.getBlockPositions().stream()
            .map(pos -> new BlockPos(pos.getX(), 0, pos.getZ()))
            .collect(java.util.stream.Collectors.toSet());

        for (BlockPos columnXZ : uniqueXZPositions) {
            BlockPos groundPos = findGroundBlock(world, columnXZ.withY(brushCenter.getY() + config.getRadius()));
            if (groundPos != null) {
                BlockState groundState = world.getBlockState(groundPos);
                
                // 确保找到的不是被忽略的方块
                if (isIgnoredBlock(groundState) || groundState.isAir()) {
                    groundPos = findGroundBlock(world, groundPos.down());
                    if (groundPos == null) continue;
                    groundState = world.getBlockState(groundPos);
                }

                if (isIgnoredBlock(groundState) || groundState.isAir()) {
                    continue;
                }
                
                TerrainColumn column = new TerrainColumn(groundState, groundPos.getY());
                columns.put(columnXZ, column);
            }
        }
        
        return columns;
    }

    /**
     * 查找地面方块
     */
    protected BlockPos findGroundBlock(World world, BlockPos initialPos) {
        BlockPos.Mutable currentPos = new BlockPos.Mutable(initialPos.getX(), initialPos.getY(), initialPos.getZ());
        
        if (world.isAir(currentPos) || isWater(world, currentPos) || isIgnoredBlock(world.getBlockState(currentPos))) {
            // 从空中开始向下搜索
        } else {
            // 如果起始点是固体，先向上找到天空
            // REFINED: 使用 world.getHeight() 以兼容动态世界高度
            while (currentPos.getY() < world.getHeight() && 
                   !world.isAir(currentPos) && !isWater(world, currentPos) && 
                   !isIgnoredBlock(world.getBlockState(currentPos))) {
                currentPos.move(0, 1, 0);
            }
        }
        
        // 向下找到第一个非空气/水/忽略的方块
        while (currentPos.getY() >= world.getBottomY() && 
               (world.isAir(currentPos) || isWater(world, currentPos) || 
                isIgnoredBlock(world.getBlockState(currentPos)))) {
            currentPos.move(0, -1, 0);
        }

        if (currentPos.getY() < world.getBottomY()) {
            return null;
        }
        return currentPos.toImmutable();
    }

    /**
     * 应用高度变化
     */
    protected void applyHeightChange(World world, BlockPos columnXZ, TerrainColumn column,
                                   int targetHeight, List<BlockPos> affectedPositions,
                                   List<BlockState> originalStates, List<BlockState> newStates) {
        int currentHeight = column.getOriginalHeight();
        BlockState fillState = column.getMainBlockState();

        // 将目标高度限制在世界有效范围内
        int clampedTargetHeight = Math.max(world.getBottomY(), Math.min(world.getHeight() - 1, targetHeight));

        if (clampedTargetHeight > currentHeight) {
            // 提升高度
            // 选择顶层方块与填充方块，使生成的地形更自然
            BlockState topState = fillState;
            BlockState fillerState = fillState;
            if (fillState.isOf(Blocks.GRASS_BLOCK)) {
                fillerState = Blocks.DIRT.getDefaultState();
                topState = Blocks.GRASS_BLOCK.getDefaultState();
                
                // 【优化】表面多样性：草地偶尔添加粗泥土或灰化土以纹理
                if (Math.random() < 0.1) { // 10%几率
                    fillerState = Blocks.COARSE_DIRT.getDefaultState();
                }
            } else if (fillState.isOf(Blocks.PODZOL) || fillState.isOf(Blocks.MYCELIUM)) {
                // 灰化土/菌丝：内部使用泥土，表面保持原样
                fillerState = Blocks.DIRT.getDefaultState();
                topState = fillState;
            }

            for (int y = currentHeight + 1; y <= clampedTargetHeight; y++) {
                BlockPos pos = new BlockPos(columnXZ.getX(), y, columnXZ.getZ());
                BlockState originalState = world.getBlockState(pos);
                
                if (originalState.isReplaceable() || isWater(world, pos)) {
                    boolean isTopLayer = (y == clampedTargetHeight);
                    BlockState placeState = isTopLayer ? topState : fillerState;
                    affectedPositions.add(pos);
                    originalStates.add(originalState);
                    newStates.add(placeState);
                    world.setBlockState(pos, placeState, Block.NOTIFY_ALL);
                } else {
                    // 如果遇到无法替换的方块，停止这一列的提升
                    break;
                }
            }
        } else if (clampedTargetHeight < currentHeight) {
            // 降低高度
            for (int y = currentHeight; y > clampedTargetHeight; y--) {
                BlockPos pos = new BlockPos(columnXZ.getX(), y, columnXZ.getZ());
                BlockState originalState = world.getBlockState(pos);
                
                if (!world.isAir(pos)) {
                    affectedPositions.add(pos);
                    originalStates.add(originalState);
                    newStates.add(Blocks.AIR.getDefaultState());
                    world.setBlockState(pos, Blocks.AIR.getDefaultState(), Block.NOTIFY_ALL);
                }
            }
        }
    }

    /**
     * 后期处理：移除悬空植物
     */
    protected void postProcess(World world, GeometryShape shape,
                             List<BlockPos> affectedPositions,
                             List<BlockState> originalStates,
                             List<BlockState> newStates) {
        removeFloatingVegetation(world, shape, affectedPositions, originalStates, newStates);
    }

    /**
     * 移除悬空植物
     * REFINED: 性能优化 - 只检查地表上方几个方块
     */
    protected void removeFloatingVegetation(World world, GeometryShape shape,
                                          List<BlockPos> affectedPositions,
                                          List<BlockState> originalStates,
                                          List<BlockState> newStates) {
        // 获取所有唯一的(X,Z)坐标，避免重复检查
        Set<BlockPos> uniqueXZPositions = shape.getBlockPositions().stream()
            .map(pos -> new BlockPos(pos.getX(), 0, pos.getZ()))
            .collect(java.util.stream.Collectors.toSet());

        for (BlockPos columnXZ : uniqueXZPositions) {
            // 找到该列的地表高度
            BlockPos groundPos = findGroundBlock(world, columnXZ.withY(world.getHeight()));
            if (groundPos == null) continue;
            
            int groundHeight = groundPos.getY();
            
            // 【优化】检查地表上方更多方块内的悬空植物，处理更高的树/结构
            for (int y = groundHeight + 1; y <= groundHeight + 10; y++) {
                BlockPos pos = new BlockPos(columnXZ.getX(), y, columnXZ.getZ());
                BlockState state = world.getBlockState(pos);
                
                if (isIgnoredBlock(state)) {
                    BlockPos below = pos.down();
                    if (world.isAir(below) || isWater(world, below)) {
                        affectedPositions.add(pos);
                        originalStates.add(state);
                        newStates.add(Blocks.AIR.getDefaultState());
                        world.setBlockState(pos, Blocks.AIR.getDefaultState(), Block.NOTIFY_ALL);
                    }
                }
            }
        }
    }

    /**
     * 检查是否为水
     */
    protected boolean isWater(World world, BlockPos pos) {
        FluidState fluidState = world.getFluidState(pos);
        return !fluidState.isEmpty() && fluidState.isStill();
    }

    /**
     * 检查是否为忽略的方块
     * REFINED: 使用BlockTags，更具兼容性和可扩展性
     */
    protected boolean isIgnoredBlock(BlockState state) {
        // 使用标签，自动兼容原版更新和其他模组添加的同类方块
        if (state.isIn(BlockTags.LOGS) || 
            state.isIn(BlockTags.LEAVES) ||
            state.isIn(BlockTags.FLOWERS) ||
            state.isIn(BlockTags.SAPLINGS) ||
            state.isIn(BlockTags.CROPS) ||
            state.isIn(BlockTags.SMALL_FLOWERS)) {
            return true;
        }
        
        // 检查竹子（没有合适的标签）
        if (state.getBlock() instanceof BambooBlock) {
            return true;
        }
        
        // 对于没有合适标签的，使用简化的Set
        return IGNORED_BLOCKS.contains(state.getBlock());
    }

    /**
     * 抽象方法：计算目标高度
     * 子类必须实现此方法来定义特定的地形操作算法
     */
    protected abstract int calculateTargetHeight(Map<BlockPos, TerrainColumn> columns, 
                                               TerrainColumn currentColumn,
                                               BlockPos columnXZ, 
                                               BlockPos brushCenter);

    // 高斯参数配置
    private static final float GAUSSIAN_KERNEL_RADIUS_FACTOR = 2.5f; // 高斯核半径因子
    private static final float MIN_SIGMA = 1.0f;

    /**
     * 计算区域平滑高度（性能优化版本）
     * 供子类复用的通用方法
     */
    protected float calculateSmoothedHeight(Map<BlockPos, TerrainColumn> columns, 
                                          BlockPos columnXZ, 
                                          BlockPos brushCenter, 
                                          int brushRadius) {
        float totalWeight = 0;
        float weightedHeightSum = 0;

        // 使用可配置的高斯参数
        float sigmaFactor = getSigmaFactor(brushRadius);
        float sigma = brushRadius * sigmaFactor;
        if (sigma < MIN_SIGMA) sigma = MIN_SIGMA;
        
        double twoSigmaSquared = 2.0 * sigma * sigma;
        
        // 性能优化：限制高斯核范围
        float kernelRadius = sigma * GAUSSIAN_KERNEL_RADIUS_FACTOR;
        float maxDistanceSq = kernelRadius * kernelRadius;
        
        // 以当前列为权重中心，确保平滑基准随位置变化更自然
        BlockPos currentCenterXZ = new BlockPos(columnXZ.getX(), 0, columnXZ.getZ());
        
        // 遍历所有邻近的柱子计算加权平均高度
        for (Map.Entry<BlockPos, TerrainColumn> entry : columns.entrySet()) {
            BlockPos neighborColumnXZ = entry.getKey();
            TerrainColumn neighborColumn = entry.getValue();

            // 使用当前列到邻居列的2D平面距离计算
            double distanceSq = neighborColumnXZ.getSquaredDistance(currentCenterXZ);

            // 性能优化：跳过超出高斯核范围的方块
            if (distanceSq > maxDistanceSq) continue;

            // 高斯衰减权重
            float weight = (float) Math.exp(-distanceSq / twoSigmaSquared);
            
            weightedHeightSum += neighborColumn.getOriginalHeight() * weight;
            totalWeight += weight;
        }

        if (totalWeight <= 0) {
            return 0; // 返回默认值，实际使用时会被原始高度替代
        }

        return weightedHeightSum / totalWeight;
    }

    /**
     * 获取高斯参数因子
     * 根据笔刷半径动态调整，确保在不同半径下效果一致
     */
    protected float getSigmaFactor(int brushRadius) {
        // 小半径使用较大的因子以获得更好的局部效果
        if (brushRadius <= 5) {
            return 0.5f;
        } else if (brushRadius <= 10) {
            return 0.4f;
        } else {
            return 0.35f; // 大半径使用较小的因子以避免过度平滑
        }
    }

    /**
     * 地形列数据类
     */
    protected static class TerrainColumn {
        private final int originalHeight;
        private final BlockState mainBlockState;

        public TerrainColumn(BlockState mainBlockState, int initialHeight) {
            this.mainBlockState = mainBlockState;
            this.originalHeight = initialHeight;
        }

        public int getOriginalHeight() {
            return originalHeight;
        }

        public BlockState getMainBlockState() {
            return mainBlockState;
        }
    }
} 