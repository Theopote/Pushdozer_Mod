package com.pushdozer.items;

// 导入所需的类和包
import com.pushdozer.PushdozerMod;
import com.pushdozer.config.PushdozerConfig;
import com.pushdozer.shapes.GeometryShape;
import com.pushdozer.util.ShapeUtil;
import com.pushdozer.operations.UndoAction;
import net.minecraft.block.*;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.fluid.FluidState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import java.util.*;

public class SmoothingHandler {
    private final PushdozerConfig config;
    private GeometryShape currentShape;

    // 定义一组在平滑过程中需要忽略的方块
    private static final Set<Block> IGNORED_BLOCKS = Set.of(
        // 树木和树叶
        Blocks.OAK_LOG, Blocks.SPRUCE_LOG, Blocks.BIRCH_LOG, Blocks.JUNGLE_LOG, Blocks.ACACIA_LOG, Blocks.DARK_OAK_LOG,
        Blocks.OAK_LEAVES, Blocks.SPRUCE_LEAVES, Blocks.BIRCH_LEAVES, Blocks.JUNGLE_LEAVES, Blocks.ACACIA_LEAVES, Blocks.DARK_OAK_LEAVES,
        // 草和花
        Blocks.TALL_GRASS, Blocks.FERN, Blocks.LARGE_FERN, Blocks.DANDELION, Blocks.POPPY,
        Blocks.BLUE_ORCHID, Blocks.ALLIUM, Blocks.AZURE_BLUET, Blocks.RED_TULIP, Blocks.ORANGE_TULIP,
        Blocks.WHITE_TULIP, Blocks.PINK_TULIP, Blocks.OXEYE_DAISY, Blocks.CORNFLOWER, Blocks.LILY_OF_THE_VALLEY,
        Blocks.SUNFLOWER, Blocks.LILAC, Blocks.ROSE_BUSH, Blocks.PEONY
    );

    // 构造函数
    public SmoothingHandler(PushdozerConfig config) {
        this.config = config;
    }

    // 处理平滑操作的主方法
    public void handleSmoothing(PlayerEntity player, World world) {
        if (world.isClient) return;
        // 取目标方块位置和创建几何形状
        BlockPos basePos = ShapeUtil.getTargetBlockPos(player, config);
        GeometryShape shape = ShapeUtil.createShape(player, config, basePos);

        if (shape == null) {
            // PushdozerMod.LOGGER.error("创建几何体失败，平滑操作中断。");
            return;
        }

        // 初始化用于存储受影响方块的列表
        List<BlockPos> affectedPositions = new ArrayList<>();
        List<BlockState> originalStates = new ArrayList<>();
        List<BlockState> newStates = new ArrayList<>();

        // 执行平滑操作
        smoothTerrain(world, shape, affectedPositions, originalStates, newStates);
        convertTopLayerToGrass(world, shape, affectedPositions, originalStates, newStates);

        // 如果有影响的方块，创建撤销操作
        if (!affectedPositions.isEmpty()) {
            UndoAction undoAction = new UndoAction(
                UndoAction.ActionType.SMOOTH,
                affectedPositions,
                originalStates,
                newStates
            );
            PushdozerMod.pushUndoAction(player, undoAction);
        }
    }

    // 平滑地形的主要方法
    private void smoothTerrain(World world, GeometryShape shape, List<BlockPos> affectedPositions, 
                             List<BlockState> originalStates, List<BlockState> newStates) {
        this.currentShape = shape;
        Map<BlockPos, TerrainColumn> columns = new HashMap<>();
        int iterations = 1; // 减少迭代次数，避免过度平滑

        // 第一遍：收集地形信息
        for (BlockPos pos : shape.getBlockPositions()) {
            BlockState state = world.getBlockState(pos);
            if (!world.isAir(pos) && !isWater(world, pos) && !isIgnoredBlock(state)) {
                BlockPos columnPos = new BlockPos(pos.getX(), 0, pos.getZ());
                TerrainColumn column = columns.computeIfAbsent(columnPos, 
                    k -> new TerrainColumn(state));
                column.addBlock(pos.getY(), state);
            }
        }

        // 应用高斯模糊平滑
        for (int iter = 0; iter < iterations; iter++) {
            Map<BlockPos, Float> smoothedHeights = new HashMap<>();
            
            // 计算每列的平滑高度
            for (Map.Entry<BlockPos, TerrainColumn> entry : columns.entrySet()) {
                BlockPos columnPos = entry.getKey();
                float smoothedHeight = calculateGaussianHeight(columns, columnPos, config.getRadius());
                smoothedHeights.put(columnPos, smoothedHeight);
            }
            
            // 应用平滑高度
            for (Map.Entry<BlockPos, TerrainColumn> entry : columns.entrySet()) {
                BlockPos columnPos = entry.getKey();
                TerrainColumn column = entry.getValue();
                float smoothedHeight = smoothedHeights.get(columnPos);
                
                // 将平滑后的高度四舍五入到最接近的整数
                int targetHeight = Math.round(smoothedHeight);
                applySmoothedHeight(world, columnPos, column, targetHeight, 
                                  affectedPositions, originalStates, newStates);
            }
        }

        // 在平滑完成后处理悬空的植物
        removeFloatingVegetation(world, shape, affectedPositions, originalStates, newStates);
    }

    // 使用高斯模糊计算平滑高度，添加边缘强度衰减
    private float calculateGaussianHeight(Map<BlockPos, TerrainColumn> columns, BlockPos centerPos, int radius) {
        float totalWeight = 0;
        float weightedHeight = 0;
        TerrainColumn centerColumn = columns.get(centerPos);
        
        // 计算中心点到操作范围中心的距离
        BlockPos shapeCenter = currentShape.getCenter();
        double distanceToCenter = Math.sqrt(
            Math.pow(centerPos.getX() - shapeCenter.getX(), 2) +
            Math.pow(centerPos.getZ() - shapeCenter.getZ(), 2)
        );

        // 修改衰减计算
        float falloff = 1.0f;
        if (distanceToCenter > radius * 0.8f) { // 只在边缘20%的区域应用衰减
            // 将距离映射到[0,1]范围，其中0.8r->1.0, r->0.0
            float t = (float)((distanceToCenter - radius * 0.8f) / (radius * 0.2f));
            t = Math.min(1.0f, Math.max(0.0f, t));
            // 使用平滑的余弦插值
            falloff = (float)(Math.cos(t * Math.PI) * 0.5f + 0.5f);
        }

        // 高斯核参数
        float sigma = radius / 2.0f;
        float twoSigmaSquared = 2.0f * sigma * sigma;

        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                // 跳过超出圆形范围的方块
                if (dx * dx + dz * dz > radius * radius) continue;

                BlockPos neighborPos = new BlockPos(centerPos.getX() + dx, 0, centerPos.getZ() + dz);
                TerrainColumn neighborColumn = columns.get(neighborPos);
                
                if (neighborColumn != null) {
                    // 计算高斯权重
                    float distance = dx * dx + dz * dz;
                    float weight = (float) Math.exp(-distance / twoSigmaSquared);
                    
                    // 根据高度差调整权重，保持陡峭特征
                    float heightDiff = Math.abs(neighborColumn.getHeight() - centerColumn.getHeight());
                    if (heightDiff > 2) {
                        weight *= Math.exp(-heightDiff * 0.1f);
                    }
                    
                    // 应用边缘衰减
                    weight *= falloff;
                    
                    weightedHeight += neighborColumn.getHeight() * weight;
                    totalWeight += weight;
                }
            }
        }

        if (totalWeight <= 0) {
            return centerColumn.getHeight();
        }

        // 根据衰减因子插值计算最终高度
        float smoothedHeight = weightedHeight / totalWeight;
        return centerColumn.getHeight() * (1 - falloff) + smoothedHeight * falloff;
    }

    // 应用平滑后的高度
    private void applySmoothedHeight(World world, BlockPos columnPos, TerrainColumn column, 
                                   int targetHeight, List<BlockPos> affectedPositions,
                                   List<BlockState> originalStates, List<BlockState> newStates) {
        int currentHeight = column.getHeight();
        Map<Integer, BlockState> blocks = column.blocks;

        // 限制最大高度变化
        int maxChange = 2;
        targetHeight = Math.max(currentHeight - maxChange, 
                              Math.min(currentHeight + maxChange, targetHeight));

        if (currentHeight != targetHeight) {
            // 保持原有方块类型
            BlockState fillState = column.getMainBlockState();
            
            if (currentHeight < targetHeight) {
                // 增加高度时使用下方方块的类型
                for (int y = currentHeight + 1; y <= targetHeight; y++) {
                    BlockPos pos = new BlockPos(columnPos.getX(), y, columnPos.getZ());
                    BlockState originalState = world.getBlockState(pos);
                    world.setBlockState(pos, fillState, Block.NOTIFY_ALL | Block.FORCE_STATE);
                    affectedPositions.add(pos);
                    originalStates.add(originalState);
                    newStates.add(fillState);
                }
            } else {
                // 降低高度时转换为空气
                for (int y = currentHeight; y > targetHeight; y--) {
                    BlockPos pos = new BlockPos(columnPos.getX(), y, columnPos.getZ());
                    BlockState originalState = world.getBlockState(pos);
                    world.setBlockState(pos, Blocks.AIR.getDefaultState(), Block.NOTIFY_ALL | Block.FORCE_STATE);
                    affectedPositions.add(pos);
                    originalStates.add(originalState);
                    newStates.add(Blocks.AIR.getDefaultState());
                }
            }
            
            column.setHeight(targetHeight);
        }
    }

    // 地形列数据结构改进
    private static class TerrainColumn {
        private int height = Integer.MIN_VALUE;
        private final BlockState mainBlockState;
        private final Map<Integer, BlockState> blocks = new HashMap<>();
        private final List<Integer> heightHistory = new ArrayList<>(); // 用于追踪高度变化

        public TerrainColumn(BlockState mainBlockState) {
            this.mainBlockState = mainBlockState;
        }

        public void addBlock(int y, BlockState state) {
            blocks.put(y, state);
            if (y > height) {
                height = y;
                heightHistory.add(height);
            }
        }

        public void setHeight(int newHeight) {
            height = newHeight;
            heightHistory.add(height);
        }

        public int getHeight() {
            return height;
        }

        public BlockState getMainBlockState() {
            return mainBlockState;
        }

    }

    // 检查指定位置是否为水
    private boolean isWater(World world, BlockPos pos) {
        FluidState fluidState = world.getFluidState(pos);
        return !fluidState.isEmpty() && fluidState.isStill();
    }

    // 检查方块是否应被忽略(装饰性方块)
    private boolean isIgnoredBlock(BlockState state) {
        Block block = state.getBlock();
        return IGNORED_BLOCKS.contains(block) ||
               block instanceof LeavesBlock ||
               block instanceof PlantBlock ||
               block instanceof VineBlock ||
               block instanceof MushroomBlock ||
               block instanceof SaplingBlock ||
               block instanceof FlowerBlock ||
               block instanceof TallPlantBlock ||
               !state.isOpaque(); // 包括大多数装饰性方块
    }

    // 将顶层转换为草方块，保持地形���然
    private void convertTopLayerToGrass(World world, GeometryShape shape, List<BlockPos> affectedPositions, 
                                      List<BlockState> originalStates, List<BlockState> newStates) {
        Map<BlockPos, Integer> columnHeights = new HashMap<>();

        // 找出每一列的最高非空气方块
        for (BlockPos pos : shape.getBlockPositions()) {
            if (!world.isAir(pos) && !isWater(world, pos)) {
                BlockPos columnPos = new BlockPos(pos.getX(), 0, pos.getZ());
                columnHeights.put(columnPos, Math.max(columnHeights.getOrDefault(columnPos, 0), pos.getY()));
            }
        }

        // 将最高的泥土方块转换为草方块
        for (Map.Entry<BlockPos, Integer> entry : columnHeights.entrySet()) {
            BlockPos topPos = new BlockPos(entry.getKey().getX(), entry.getValue(), entry.getKey().getZ());
            BlockState topState = world.getBlockState(topPos);

            if (topState.getBlock() == Blocks.DIRT) {
                BlockState originalState = topState;
                BlockState newState = Blocks.GRASS_BLOCK.getDefaultState();

                world.setBlockState(topPos, newState, Block.NOTIFY_ALL | Block.FORCE_STATE);

                if (!affectedPositions.contains(topPos)) {
                    affectedPositions.add(topPos);
                    originalStates.add(originalState);
                    newStates.add(newState);
                } else {
                    int index = affectedPositions.indexOf(topPos);
                    newStates.set(index, newState);
                }

            }
        }
    }

    // 检查方块是否为植物
    private boolean isVegetation(BlockState state) {
        Block block = state.getBlock();
        return block instanceof PlantBlock ||
               block instanceof TallPlantBlock ||
               block instanceof FlowerBlock ||
               block instanceof GrassBlock ||
               block instanceof SaplingBlock ||
               block instanceof VineBlock ||
               block instanceof MushroomPlantBlock ||
               block == Blocks.TALL_GRASS ||
               block == Blocks.FERN ||
               block == Blocks.LARGE_FERN;
    }

    // 检查方块是否能支撑植物
    private boolean canSupportVegetation(BlockState state) {
        Block block = state.getBlock();
        return block.getDefaultState().isOpaque() || // 不透明方块
               block instanceof GrassBlock ||
               block == Blocks.DIRT ||
               block == Blocks.FARMLAND ||
               block == Blocks.PODZOL ||
               block == Blocks.MYCELIUM ||
               block == Blocks.COARSE_DIRT ||
               block == Blocks.ROOTED_DIRT;
    }

    // 处理悬空的植物
    private void removeFloatingVegetation(World world, GeometryShape shape, 
                                        List<BlockPos> affectedPositions,
                                        List<BlockState> originalStates, 
                                        List<BlockState> newStates) {
        Set<BlockPos> toRemove = new HashSet<>();
        
        // 收集所有需要检查的位置
        for (BlockPos pos : shape.getBlockPositions()) {
            BlockState state = world.getBlockState(pos);
            if (isVegetation(state)) {
                // 检查下方方块
                BlockPos below = pos.down();
                BlockState belowState = world.getBlockState(below);
                
                // 如果下方是空气或者不能支撑植物的方块，标记为需要移除
                if (world.isAir(below) || !canSupportVegetation(belowState)) {
                    toRemove.add(pos);
                }
            }
        }
        
        // 移除悬空的植物
        for (BlockPos pos : toRemove) {
            BlockState originalState = world.getBlockState(pos);
            world.setBlockState(pos, Blocks.AIR.getDefaultState(), Block.NOTIFY_ALL | Block.FORCE_STATE);
            
            affectedPositions.add(pos);
            originalStates.add(originalState);
            newStates.add(Blocks.AIR.getDefaultState());
        }
    }
}