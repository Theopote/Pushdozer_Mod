package com.pushdozer.items.handlers;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.pushdozer.PushdozerMod;
import com.pushdozer.config.PushdozerConfig;
import com.pushdozer.shapes.GeometryShape;
import com.pushdozer.util.ShapeUtil;
import com.pushdozer.util.TerrainBlockSelector;
import com.pushdozer.operations.BlockOperation;
import com.pushdozer.operations.UndoAction;
import com.pushdozer.network.NetworkManager;

import net.minecraft.block.*;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PlacementHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger("pushdozer");
    private final PushdozerConfig config;

    // 使用标签系统替代硬编码的装饰性方块集合
    private static final TagKey<Block> DECORATIVE_BLOCKS = TagKey.of(RegistryKeys.BLOCK, 
        net.minecraft.util.Identifier.of("pushdozer", "decorative"));

    // 方块替换映射表 - 将复杂的 if-else if 链替换为清晰的映射关系
    private static final Map<Block, Block> BLOCK_REPLACEMENT_MAP = new HashMap<>();
    
    static {
        // 特殊方块替换规则
        BLOCK_REPLACEMENT_MAP.put(Blocks.LAVA, Blocks.STONE);
        BLOCK_REPLACEMENT_MAP.put(Blocks.SAND, Blocks.SANDSTONE);
        BLOCK_REPLACEMENT_MAP.put(Blocks.GRAVEL, Blocks.STONE);
        BLOCK_REPLACEMENT_MAP.put(Blocks.SNOW, Blocks.SNOW_BLOCK);
        BLOCK_REPLACEMENT_MAP.put(Blocks.POINTED_DRIPSTONE, Blocks.STONE);
        BLOCK_REPLACEMENT_MAP.put(Blocks.GLOW_LICHEN, Blocks.STONE);
        BLOCK_REPLACEMENT_MAP.put(Blocks.COCOA, Blocks.GRASS_BLOCK);
        
        // 功能方块替换规则
        BLOCK_REPLACEMENT_MAP.put(Blocks.COMPOSTER, Blocks.GRASS_BLOCK);
        
        // 植物方块替换规则
        BLOCK_REPLACEMENT_MAP.put(Blocks.SUGAR_CANE, Blocks.GRASS_BLOCK);
        BLOCK_REPLACEMENT_MAP.put(Blocks.CACTUS, Blocks.GRASS_BLOCK);
        BLOCK_REPLACEMENT_MAP.put(Blocks.BAMBOO, Blocks.GRASS_BLOCK);
    }

    public PlacementHandler(PushdozerConfig config) {
        this.config = config;
    }

    public List<BlockPos> handlePlacement(PlayerEntity player, World world) {
        List<BlockPos> placedPositions = new ArrayList<>();
        if (world.isClient()) {
            return placedPositions; // 如果是客户端，直接返回空列表
        }

        // 多人游戏权限检查
        if (!hasPlacementPermission(player, world)) {
            return placedPositions;
        }

        LOGGER.debug("开始放置操作，玩家: {}", player.getName().getString());

        BlockPos basePos = ShapeUtil.getTargetBlockPos(player, config);
        LOGGER.debug("基准位置：{}", basePos);

        GeometryShape shape = ShapeUtil.createShape(player, config, basePos);
        if (shape == null) {
            LOGGER.error("创建几何体失败，放置操作中断。");
            return placedPositions;
        }

        placedPositions = processBlockPlacement(shape, player, world);
        return placedPositions;
    }
    
    /**
     * 检查玩家是否有放置权限
     */
    private boolean hasPlacementPermission(PlayerEntity player, World world) {
        // 单人游戏总是允许
        if (world.getServer() != null && world.getServer().isSingleplayer()) {
            return true;
        }
        
        // 多人游戏中所有玩家都可以使用放置工具
        if (player instanceof ServerPlayerEntity) {
            // 检查操作范围限制（防止恶意大范围操作）
            if (config.getRadius() > 100) {  // 提高限制到100格
                LOGGER.warn("玩家 {} 尝试执行过大范围放置操作: 半径 {}", 
                    player.getName().getString(), config.getRadius());
                return false;
            }
            
            // 可以在这里添加区域保护检查
            // 例如检查是否在保护区内等
        }
        
        return true;
    }

    private List<BlockPos> processBlockPlacement(GeometryShape shape, PlayerEntity player, World world) {
        List<BlockPos> placedBlocks = new ArrayList<>();
        List<BlockState> originalStates = new ArrayList<>();
        List<BlockState> newStates = new ArrayList<>();

        BlockPos center = shape.getCenter();
        int minY = shape.getMinY(center);
        int maxY = shape.getMaxY(center);

        boolean startedPlacing = false;

        // 从上往下遍历每一层
        for (int y = maxY; y >= minY; y--) {
            List<BlockPos> layerBlocks = new ArrayList<>();
            List<BlockState> layerNewStates = new ArrayList<>();

            boolean layerHasNonAllowedBlocks = false;
            // 检查当前层是否有非允许的方块
            for (BlockPos pos : shape.getBlocksInLayer(center, y)) {
                if (!isAllowedBlock(world.getBlockState(pos), world, pos)) {
                    layerHasNonAllowedBlocks = true;
                    break;
                }
            }

            // 如果当前层有非允许的方块或已经开始放置，则处理当前层
            if (layerHasNonAllowedBlocks || startedPlacing) {
                startedPlacing = true;
                for (BlockPos pos : shape.getBlocksInLayer(center, y)) {
                    if (isValidPlacementPosition(pos, world, shape, player)) {
                        layerBlocks.add(pos);
                        // 记录原始状态
                        originalStates.add(world.getBlockState(pos));
                        placedBlocks.add(pos);

                        // 获取要放置的方块
                        Block fillBlock = getFillBlock(pos, world);
                        
                        // 应用方块替换规则
                        fillBlock = applyBlockReplacementRules(fillBlock);
                        
                        BlockState newState = fillBlock.getDefaultState();
                        newStates.add(newState);
                        layerNewStates.add(newState);
                    }
                }

                // 批量放置方块
                placeBlocksBulk(layerBlocks, layerNewStates, world);
            }
        }

        // 推入撤销栈并同步到其他玩家
        if (!placedBlocks.isEmpty()) {
            LOGGER.info("创建撤销操作，放置方块数: {}", placedBlocks.size());
            
            // 收集边界扩展信息
            BlockOperation.BoundaryExtension boundaryExtension = BlockOperation.collectBoundaryExtension(placedBlocks, world);
            LOGGER.info("边界扩展收集完成，扩展位置数: {}", boundaryExtension.getSize());
            
            UndoAction undoAction = new UndoAction(
                UndoAction.ActionType.PLACE,
                placedBlocks,
                originalStates,
                newStates,
                boundaryExtension.getPositions(),
                boundaryExtension.getOriginalStates(),
                boundaryExtension.getNewStates()
            );
            
            LOGGER.info("撤销操作创建完成，验证状态: {}", undoAction.isValid());
            PushdozerMod.pushUndoAction(player, undoAction);
            
            // 调试：检查撤销栈状态
            PushdozerMod.debugUndoStacks(player);
            
            // 在多人游戏中广播放置操作到其他玩家
            if (world instanceof ServerWorld serverWorld && !serverWorld.getServer().isSingleplayer()) {
                NetworkManager.broadcastTerrainOperation(
                    serverWorld,
                    "PLACE",
                    placedBlocks,
                    newStates
                );
                LOGGER.info("广播放置操作到其他玩家，影响方块数: {}，边界扩展: {}", 
                    placedBlocks.size(), boundaryExtension.getSize());
            }
        } else {
            LOGGER.info("没有放置任何方块，跳过撤销操作创建");
        }

        return placedBlocks;
    }

    /**
     * 获取要放置的方块
     */
    private Block getFillBlock(BlockPos pos, World world) {
        if (config.getPlaceMode() == PushdozerConfig.PlaceMode.NATURAL_BLOCK) {
            // 自然方块铺设模式
            return config.getSelectedNaturalBlock();
        } else {
            // 自适应生物群系模式
            return TerrainBlockSelector.getNaturalTerrainBlock(pos, world);
        }
    }

    /**
     * 应用方块替换规则
     */
    private Block applyBlockReplacementRules(Block originalBlock) {
        // 首先检查直接替换映射
        Block replacement = BLOCK_REPLACEMENT_MAP.get(originalBlock);
        if (replacement != null) {
            return replacement;
        }

        // 检查特殊方块类型
        if (shouldReplaceSpecialBlock(originalBlock)) {
            return getReplacementForSpecialBlock(originalBlock);
        }

        // 检查标签替换规则
        if (shouldReplaceByTag(originalBlock)) {
            return getReplacementByTag(originalBlock);
        }

        // 检查是否为生长的植物
        if (isGrowingPlant(originalBlock)) {
            return Blocks.GRASS_BLOCK;
        }

        return originalBlock;
    }

    /**
     * 检查是否应该替换特殊方块
     */
    private boolean shouldReplaceSpecialBlock(Block block) {
        return block instanceof TorchBlock || 
               block instanceof BellBlock ||
               block instanceof ComposterBlock ||
               block instanceof StairsBlock ||
               block instanceof SlabBlock;
    }

    /**
     * 获取特殊方块的替换方块
     */
    private Block getReplacementForSpecialBlock(Block block) {
        if (block instanceof TorchBlock || block instanceof BellBlock) {
            return Blocks.COBBLESTONE;
        } else if (block instanceof ComposterBlock) {
            return Blocks.GRASS_BLOCK;
        } else if (block instanceof StairsBlock || block instanceof SlabBlock) {
            return Blocks.DIRT;
        }
        return block;
    }

    /**
     * 检查是否应该根据标签替换方块
     */
    private boolean shouldReplaceByTag(Block block) {
        return block.getDefaultState().isIn(BlockTags.FENCES) ||
               block.getDefaultState().isIn(BlockTags.FENCE_GATES) ||
               block.getDefaultState().isIn(BlockTags.PLANKS) ||
               block.getDefaultState().isIn(BlockTags.WOOL);
    }

    /**
     * 根据标签获取替换方块
     */
    private Block getReplacementByTag(Block block) {
        if (block.getDefaultState().isIn(BlockTags.FENCES) || 
            block.getDefaultState().isIn(BlockTags.FENCE_GATES)) {
            return Blocks.COBBLESTONE;
        } else if (block.getDefaultState().isIn(BlockTags.PLANKS)) {
            return Blocks.DIRT;
        } else if (block.getDefaultState().isIn(BlockTags.WOOL)) {
            return Blocks.GRASS_BLOCK;
        }
        return block;
    }

    private void placeBlocksBulk(List<BlockPos> positions, List<BlockState> states, World world) {
        // 步骤 1: "安静地"放置所有方块，不触发邻居更新
        // NOTIFY_LISTENERS: 通知客户端
        // FORCE_STATE: 强制设置状态，即使它和旧的一样
        // SKIP_DROPS: 避免被替换的方块掉落物品
        final int flags = Block.NOTIFY_LISTENERS | Block.FORCE_STATE | Block.SKIP_DROPS;
        
        for (int i = 0; i < positions.size(); i++) {
            BlockPos pos = positions.get(i);
            BlockState newState = states.get(i);
            
            // 放置新的方块，使用优化的标志
            world.setBlockState(pos, newState, flags);
            
            // 如果是下落方块，使用方块刻度调度系统
            if (newState.getBlock() instanceof FallingBlock) {
                world.scheduleBlockTick(pos, newState.getBlock(), 2);
            }
        }

        // 步骤 2: 在操作完成后，批量更新受影响区域边界的方块和光照
        if (world instanceof ServerWorld serverWorld) {
            for (int i = 0; i < positions.size(); i++) {
                BlockPos pos = positions.get(i);
                BlockState newState = states.get(i);
                
                // 更新光照
                serverWorld.getLightingProvider().checkBlock(pos);
                
                // 通知邻居方块更新
                world.updateNeighbors(pos, newState.getBlock());
                
                // 如果放置的方块会影响它下方的方块（比如沙子），也更新下方
                world.updateNeighbors(pos.down(), newState.getBlock());
            }
        }
    }

    private boolean isValidPlacementPosition(BlockPos pos, World world, GeometryShape shape, PlayerEntity player) {
        if (pos == null || world == null || shape == null || player == null) {
            PushdozerMod.LOGGER.error("isValidPlacementPosition 方法接收到 null 参");
            return false;
        }

        BlockState state = world.getBlockState(pos);

        // 检查标高限制（铺设模式：只能在此标高以下铺设）
        PushdozerConfig.HeightMode heightMode = config.getHeightMode();
        if (heightMode == PushdozerConfig.HeightMode.FOLLOW_PLAYER) {
            // 跟随玩家标高：只能在玩家当前高度-1及以下铺设
            if (pos.getY() > player.getBlockY() - 1) {
                return false;
            }
        } else if (heightMode == PushdozerConfig.HeightMode.LOCKED_ONCE || heightMode == PushdozerConfig.HeightMode.CUSTOM) {
            // 锁定到玩家标高/自定义标高：只能在锁定高度及以下铺设
            if (pos.getY() > config.getLockedHeight()) {
                return false;
            }
        }
        // NO_LIMIT模式不限制标高

        // 检查是否在玩家上方或与玩家同高度
        if (isAboveOrAtPlayerLevel(pos, player)) {
            return false;
        }

        if (!shape.isWithinBounds(pos, shape.getCenter())) {
            return false;
        }

        // 使用高性能的方块忽略检查
        if (config.isBlockIgnored(state.getBlock())) {
            return false;
        }

        return isAirOrDecorative(state, world, pos);
    }

    private boolean isAboveOrAtPlayerLevel(BlockPos pos, PlayerEntity player) {
        return pos.getX() == player.getBlockX() &&
               pos.getZ() == player.getBlockZ() &&
               pos.getY() >= player.getBlockY();
    }

    private boolean isAirOrDecorative(BlockState state, World world, BlockPos pos) {
        Block block = state.getBlock();
        return state.isAir() || isDecorativeBlock(block) || isNonSolidBlock(state, world, pos);
    }

    private boolean isNonSolidBlock(BlockState state, World world, BlockPos pos) {
        return state.isAir() || 
               state.getCollisionShape(world, pos).isEmpty() || 
               state.getBlock() instanceof FallingBlock;
    }

    private boolean isAllowedBlock(BlockState state, World world, BlockPos pos) {
        Block block = state.getBlock();
        return state.isAir() || 
               isDecorativeBlock(block) || 
               block instanceof LeavesBlock || 
               isWoodBlock(block) ||
               block instanceof BambooBlock || // 检查竹子
               block instanceof FluidBlock || // 检查是否为流体方块（包括水和岩浆）
               isNonSolidBlock(state, world, pos);
    }

    private boolean isWoodBlock(Block block) {
        // 使用标签来检查方块是否为木头
        return block.getDefaultState().isIn(BlockTags.LOGS);
    }

    // 新增方法：检查方块是否为生长的植物
    private boolean isGrowingPlant(Block block) {
        return block instanceof PlantBlock || 
               block instanceof LeavesBlock || 
               block instanceof MushroomBlock ||
               block instanceof VineBlock ||
               block instanceof SugarCaneBlock ||
               block instanceof CactusBlock ||
               block instanceof BambooBlock ||
               block instanceof CropBlock ||
               block instanceof SaplingBlock;
        // 移除了 isWoodBlock(block)，因为原木不应该被当作生长的植物处理
    }

    // 新增方法：检查方块是否为装饰性方块
    private boolean isDecorativeBlock(Block block) {
        return block.getDefaultState().isIn(DECORATIVE_BLOCKS);
    }
}