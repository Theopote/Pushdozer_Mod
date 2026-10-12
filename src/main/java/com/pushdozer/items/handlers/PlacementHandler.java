package com.pushdozer.items.handlers;

import java.util.*;

import com.pushdozer.PushdozerMod;
import com.pushdozer.config.PushdozerConfig;
import com.pushdozer.shapes.GeometryShape;
import com.pushdozer.util.OperationPermissions;
import com.pushdozer.util.ShapeUtil;
import com.pushdozer.util.TerrainOperationFeedback;
import com.pushdozer.util.TerrainBlockSelector;
import com.pushdozer.operations.AppliedChangeResult;
import com.pushdozer.operations.BlockOperation;
import com.pushdozer.operations.UndoAction;
import com.pushdozer.network.NetworkManager;

import net.minecraft.block.*;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.world.World;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PlacementHandler implements TerrainToolHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger("pushdozer");

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

    public PlacementHandler() {
    }

    public List<BlockPos> handlePlacement(PlayerEntity player, World world, PushdozerConfig config) {
        List<BlockPos> placedPositions = new ArrayList<>();
        if (world.isClient()) {
            return placedPositions; // 如果是客户端，直接返回空列表
        }

        // 多人游戏权限检查
        if (!OperationPermissions.checkForTerrainOperation(player, world, config)) {
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

        return processBlockPlacement(shape, player, world, config);
    }

    /**
     * 在指定世界坐标执行铺设并记录撤销栈（供 Game Test 与确定性调用场景使用）。
     */
    public void placeBlocksAt(PlayerEntity player, ServerWorld world, PushdozerConfig config,
                              List<BlockPos> worldPositions, Block fillBlock) {
        if (!OperationPermissions.checkForTerrainOperation(player, world, config)) {
            return;
        }

        Block resolvedFill = applyBlockReplacementRules(fillBlock);
        BlockState fillState = resolvedFill.getDefaultState();
        List<BlockPos> filtered = new ArrayList<>();
        List<BlockState> newStates = new ArrayList<>();

        for (BlockPos pos : worldPositions) {
            BlockState state = world.getBlockState(pos);
            if (isValidExplicitPlacementTarget(state, world, pos, player, config)) {
                filtered.add(pos);
                newStates.add(fillState);
            }
        }

        if (!filtered.isEmpty()) {
            if (!BlockOperation.applyPlacementChanges(world, filtered, newStates, applied ->
                pushPlacementUndo(player, world, world, applied)
            )) {
                TerrainOperationFeedback.notifyRegionBusy(player);
            }
        }
    }

    private boolean isValidExplicitPlacementTarget(BlockState state, World world, BlockPos pos,
                                                   PlayerEntity player, PushdozerConfig config) {
        if (!isValidHeightForPlacement(pos, player, config)) {
            return false;
        }
        if (intersectsPlayer(player, pos)) {
            return false;
        }
        if (config.isBlockIgnored(state.getBlock())) {
            return false;
        }
        return isAllowedBlock(state, world, pos);
    }

    private boolean isValidHeightForPlacement(BlockPos pos, PlayerEntity player, PushdozerConfig config) {
        PushdozerConfig.HeightMode heightMode = config.getHeightMode();
        if (heightMode == PushdozerConfig.HeightMode.NO_LIMIT) {
            return true;
        }
        if (heightMode == PushdozerConfig.HeightMode.FOLLOW_PLAYER) {
            return pos.getY() <= player.getBlockY() - 1;
        }
        if (heightMode == PushdozerConfig.HeightMode.LOCKED_ONCE || heightMode == PushdozerConfig.HeightMode.CUSTOM) {
            return pos.getY() <= config.getLockedHeight();
        }
        return true;
    }

    private List<BlockPos> processBlockPlacement(GeometryShape shape, PlayerEntity player, World world,
                                                 PushdozerConfig config) {
        List<BlockPos> placedBlocks = new ArrayList<>();
        List<BlockState> originalStates = new ArrayList<>();
        List<BlockState> newStates = new ArrayList<>();

        BlockPos center = shape.getCenter();
        int minY = shape.getMinY(center);
        int maxY = shape.getMaxY(center);

        // 遍历笔刷体积内每一层；空中与接触地形均可铺设，仅替换空气/装饰/非实心方块
        for (int y = maxY; y >= minY; y--) {
            for (BlockPos pos : shape.getBlocksInLayer(center, y)) {
                if (isValidPlacementPosition(pos, world, shape, player, config)) {
                    originalStates.add(world.getBlockState(pos));
                    placedBlocks.add(pos);

                    Block fillBlock = getFillBlock(pos, world, config);
                    fillBlock = applyBlockReplacementRules(fillBlock);
                    newStates.add(fillBlock.getDefaultState());
                }
            }
        }

        if (!placedBlocks.isEmpty() && world instanceof ServerWorld serverWorld) {
            LOGGER.info("创建撤销操作，放置方块数: {}", placedBlocks.size());

            if (!BlockOperation.applyPlacementChanges(serverWorld, placedBlocks, newStates, applied ->
                pushPlacementUndo(player, world, serverWorld, applied)
            )) {
                TerrainOperationFeedback.notifyRegionBusy(player);
            }
        } else if (placedBlocks.isEmpty()) {
            LOGGER.info("没有放置任何方块，跳过撤销操作创建");
        }

        return placedBlocks;
    }

    private static void pushPlacementUndo(PlayerEntity player, World world, ServerWorld serverWorld,
                                            AppliedChangeResult applied) {
        if (applied.isEmpty()) {
            return;
        }

        BlockOperation.BoundaryExtension boundaryExtension =
            BlockOperation.collectBoundaryExtension(applied.positions(), world);
        LOGGER.info("边界扩展收集完成，扩展位置数: {}", boundaryExtension.getSize());

        UndoAction undoAction = new UndoAction(
            UndoAction.ActionType.PLACE,
            serverWorld.getRegistryKey(),
            applied.positions(),
            applied.originalStates(),
            applied.appliedStates(),
            UndoAction.orderedBoundarySet(boundaryExtension.positions()),
            boundaryExtension.originalStates(),
            boundaryExtension.newStates()
        );

        LOGGER.info("撤销操作创建完成，验证状态: {}", undoAction.isValid());
        PushdozerMod.pushUndoAction(player, undoAction);
        PushdozerMod.debugUndoStacks(player);

        if (!Objects.requireNonNull(serverWorld.getServer()).isSingleplayer()) {
            NetworkManager.broadcastTerrainOperation(
                serverWorld,
                "PLACE",
                applied.positions(),
                applied.appliedStates()
            );
            LOGGER.info("广播放置操作到其他玩家，影响方块数: {}，边界扩展: {}",
                applied.positions().size(), boundaryExtension.getSize());
        }
    }

    /**
     * 获取要放置的方块
     */
    private Block getFillBlock(BlockPos pos, World world, PushdozerConfig config) {
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

    private boolean isValidPlacementPosition(BlockPos pos, World world, GeometryShape shape, PlayerEntity player,
                                             PushdozerConfig config) {
        if (pos == null || world == null || shape == null || player == null) {
            PushdozerMod.LOGGER.error("isValidPlacementPosition 方法接收到 null 参");
            return false;
        }

        BlockState state = world.getBlockState(pos);

        if (!isValidHeightForPlacement(pos, player, config)) {
            return false;
        }

        if (intersectsPlayer(player, pos)) {
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

    static boolean intersectsPlayer(PlayerEntity player, BlockPos pos) {
        Box playerBox = player.getBoundingBox();
        Box blockBox = new Box(pos.getX(), pos.getY(), pos.getZ(), pos.getX() + 1, pos.getY() + 1, pos.getZ() + 1);
        return playerBox.intersects(blockBox);
    }

    private boolean isAirOrDecorative(BlockState state, World world, BlockPos pos) {
        Block block = state.getBlock();
        return state.isAir() || isDecorativeBlock(block) || isNonSolidBlock(state, world, pos);
    }

    private boolean isNonSolidBlock(BlockState state, World world, BlockPos pos) {
        return state.isAir() || state.getCollisionShape(world, pos).isEmpty();
    }

    public boolean isAllowedBlock(BlockState state, World world, BlockPos pos) {
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