package com.pushdozer.items;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import com.pushdozer.PushdozerMod;
import com.pushdozer.config.PushdozerConfig;
import com.pushdozer.shapes.GeometryShape;
import com.pushdozer.util.ShapeUtil;
import com.pushdozer.util.TerrainBlockSelector;
import com.pushdozer.operations.UndoAction;

import net.minecraft.block.*;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.registry.Registries;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;

public class PlacementHandler {
    private final PushdozerConfig config;

    private static final Set<Block> decorativeBlocks = Set.of(
        Blocks.TALL_GRASS, Blocks.SHORT_GRASS, Blocks.FERN, Blocks.DANDELION, Blocks.POPPY,
        Blocks.BROWN_MUSHROOM, Blocks.RED_MUSHROOM, Blocks.VINE,
        Blocks.DEAD_BUSH, Blocks.SEAGRASS, Blocks.SEA_PICKLE,
        Blocks.LILY_PAD,Blocks.SUNFLOWER, Blocks.LILAC, Blocks.ROSE_BUSH, Blocks.PEONY,
        Blocks.ALLIUM, Blocks.AZURE_BLUET, Blocks.RED_TULIP, Blocks.ORANGE_TULIP,
        Blocks.WHITE_TULIP, Blocks.PINK_TULIP, Blocks.OXEYE_DAISY, Blocks.CORNFLOWER,
        Blocks.LILY_OF_THE_VALLEY, Blocks.WITHER_ROSE, Blocks.SWEET_BERRY_BUSH,Blocks.POINTED_DRIPSTONE,Blocks.SNOW
    );

    public PlacementHandler(PushdozerConfig config) {
        this.config = config;
    }

    public List<BlockPos> handlePlacement(PlayerEntity player, World world) {
        List<BlockPos> placedPositions = new ArrayList<>();
        if (world.isClient) {
            return placedPositions; // 如果是客户端，直接返回空列表
        }

        // PushdozerMod.LOGGER.info("开始放置操作");

        BlockPos basePos = ShapeUtil.getTargetBlockPos(player, config);
        // PushdozerMod.LOGGER.info("基准位置：" + basePos);

        GeometryShape shape = ShapeUtil.createShape(player, config, basePos);
        if (shape == null) {
            // PushdozerMod.LOGGER.error("创建几何体失败，放置操作中断。");
            return placedPositions;
        }

        placedPositions = processBlockPlacement(shape, player, world);
        return placedPositions;
    }

    private List<BlockPos> processBlockPlacement(GeometryShape shape, PlayerEntity player, World world) {
        if (world.isClient) return new ArrayList<>();

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

            // 如果当��层有非允许的方块或已经开始放置，则处理当前层
            if (layerHasNonAllowedBlocks || startedPlacing) {
                startedPlacing = true;
                for (BlockPos pos : shape.getBlocksInLayer(center, y)) {
                    if (isValidPlacementPosition(pos, world, shape, player)) {
                        layerBlocks.add(pos);
                        // 记录原始状态
                        originalStates.add(world.getBlockState(pos));
                        placedBlocks.add(pos);

                        // 确定新的方块状态
                        Block fillBlock = TerrainBlockSelector.getNaturalTerrainBlock(pos, world);
                        
                        // 特殊方块替换逻辑
                        if (fillBlock instanceof TorchBlock || fillBlock instanceof BellBlock) {
                            fillBlock = Blocks.COBBLESTONE;
                        } else if (fillBlock instanceof ComposterBlock) {
                            fillBlock = Blocks.GRASS_BLOCK;
                        } else if (fillBlock instanceof StairsBlock) {
                            // 所有楼梯方块都替换为泥土
                            fillBlock = Blocks.DIRT;
                        } else if (fillBlock instanceof SlabBlock) {
                            // 所有台阶��块都替换为泥土
                            fillBlock = Blocks.DIRT;
                        } else if (fillBlock instanceof FenceBlock && (
                                fillBlock.getTranslationKey().contains("oak_fence") ||
                                fillBlock.getTranslationKey().contains("spruce_fence") ||
                                fillBlock.getTranslationKey().contains("birch_fence") ||
                                fillBlock.getTranslationKey().contains("jungle_fence") ||
                                fillBlock.getTranslationKey().contains("acacia_fence") ||
                                fillBlock.getTranslationKey().contains("dark_oak_fence") ||
                                fillBlock.getTranslationKey().contains("crimson_fence") ||
                                fillBlock.getTranslationKey().contains("warped_fence") ||
                                fillBlock.getTranslationKey().contains("mangrove_fence") ||
                                fillBlock.getTranslationKey().contains("bamboo_fence") ||
                                fillBlock.getTranslationKey().contains("cherry_fence"))) {
                            fillBlock = Blocks.COBBLESTONE;
                        } else if (fillBlock instanceof FenceGateBlock && (
                                fillBlock.getTranslationKey().contains("oak_fence_gate") ||
                                fillBlock.getTranslationKey().contains("spruce_fence_gate") ||
                                fillBlock.getTranslationKey().contains("birch_fence_gate") ||
                                fillBlock.getTranslationKey().contains("jungle_fence_gate") ||
                                fillBlock.getTranslationKey().contains("acacia_fence_gate") ||
                                fillBlock.getTranslationKey().contains("dark_oak_fence_gate") ||
                                fillBlock.getTranslationKey().contains("crimson_fence_gate") ||
                                fillBlock.getTranslationKey().contains("warped_fence_gate") ||
                                fillBlock.getTranslationKey().contains("mangrove_fence_gate") ||
                                fillBlock.getTranslationKey().contains("bamboo_fence_gate") ||
                                fillBlock.getTranslationKey().contains("cherry_fence_gate"))) {
                            fillBlock = Blocks.COBBLESTONE;
                        } else if (fillBlock.getTranslationKey().contains("_planks")) {
                            fillBlock = Blocks.DIRT;
                        } else if (fillBlock.getTranslationKey().contains("_wool")) {
                            fillBlock = Blocks.GRASS_BLOCK;
                        }
                        
                        if (isGrowingPlant(fillBlock)) {
                            fillBlock = Blocks.GRASS_BLOCK;
                        } else if (fillBlock == Blocks.SAND) {
                            fillBlock = Blocks.SANDSTONE;
                        } else if (fillBlock == Blocks.GRAVEL) {
                            fillBlock = Blocks.STONE;
                        } else if (fillBlock == Blocks.SNOW) {
                            fillBlock = Blocks.SNOW_BLOCK;
                        }
                        BlockState newState = fillBlock.getDefaultState();
                        newStates.add(newState);
                        layerNewStates.add(newState);
                    }
                }

                // 批量放置方块
                placeBlocksBulk(layerBlocks, layerNewStates, world);
            }
        }

        // 推入撤销栈
        if (!placedBlocks.isEmpty()) {
            UndoAction undoAction = new UndoAction(
                UndoAction.ActionType.PLACE,
                placedBlocks,
                originalStates,
                newStates
            );
            PushdozerMod.pushUndoAction(player, undoAction);
        }

        return placedBlocks;
    }

    private void placeBlocksBulk(List<BlockPos> positions, List<BlockState> states, World world) {
        for (int i = 0; i < positions.size(); i++) {
            BlockPos pos = positions.get(i);
            BlockState newState = states.get(i);
            BlockState oldState = world.getBlockState(pos);

            // 如果原有方块是装饰性植物，先移除它
            if (decorativeBlocks.contains(oldState.getBlock())) {
                world.setBlockState(pos, Blocks.AIR.getDefaultState(), Block.NOTIFY_ALL);
            }
            
            // 如果原有方块是岩浆，将新状态改为石头
            if (oldState.getBlock() == Blocks.LAVA) {
                newState = Blocks.STONE.getDefaultState();
            }

            // 处理特殊方块的替换
            Block block = newState.getBlock();
            if (block == Blocks.POINTED_DRIPSTONE || block == Blocks.GLOW_LICHEN) {
                newState = Blocks.STONE.getDefaultState();
            } else if (block == Blocks.COCOA) {
                newState = Blocks.GRASS_BLOCK.getDefaultState();
            }

            // 放置新的方块，使用标准的更新标志
            world.setBlockState(pos, newState, Block.NOTIFY_ALL);
            
            // 如果是下落方块，使用方块刻度调度系统
            if (newState.getBlock() instanceof FallingBlock) {
                world.scheduleBlockTick(pos, newState.getBlock(), 2);
            }
        }

        // 批量更新区块和光照
        if (world instanceof ServerWorld serverWorld) {
            for (BlockPos pos : positions) {
                BlockState state = world.getBlockState(pos);
                
                // 更新光照
                serverWorld.getLightingProvider().checkBlock(pos);
                
                // 标记区块需要保存
                serverWorld.getChunk(pos).setNeedsSaving(true);
            }
        }
    }

    private boolean isValidPlacementPosition(BlockPos pos, World world, GeometryShape shape, PlayerEntity player) {
        if (pos == null || world == null || shape == null || player == null) {
            PushdozerMod.LOGGER.error("isValidPlacementPosition 方法接收到 null 参");
            return false;
        }

        BlockState state = world.getBlockState(pos);

        // 检查标高锁定
        if (config.isHeightLocked() && pos.getY() >= config.getLockedHeight()) {
            return false;
        }

        // 检查是否在玩家上方或与玩家同高度
        if (isAboveOrAtPlayerLevel(pos, player)) {
            return false;
        }

        if (!shape.isWithinBounds(pos, shape.getCenter())) {
            return false;
        }

        if (config.getIgnoredBlockIds().contains(Registries.BLOCK.getId(state.getBlock()).toString())) {
            return false;
        }

        return isAirOrDecorative(state);
    }

    private boolean isAboveOrAtPlayerLevel(BlockPos pos, PlayerEntity player) {
        return pos.getX() == player.getBlockX() &&
               pos.getZ() == player.getBlockZ() &&
               pos.getY() >= player.getBlockY();
    }

    private boolean isAirOrDecorative(BlockState state) {
        Block block = state.getBlock();
        return state.isAir() || decorativeBlocks.contains(block) || isNonSolidBlock(state, null, BlockPos.ORIGIN);
    }

    private boolean isNonSolidBlock(BlockState state, World world, BlockPos pos) {
        return state.isAir() || 
               state.getCollisionShape(world, pos).isEmpty() || 
               state.getBlock() instanceof FallingBlock;
    }

    private boolean isAllowedBlock(BlockState state, World world, BlockPos pos) {
        Block block = state.getBlock();
        return state.isAir() || 
               decorativeBlocks.contains(block) || 
               block instanceof LeavesBlock || 
               isWoodBlock(block) ||
               block instanceof FluidBlock || // 检查是否为流体方块（包括水和岩浆）
               isNonSolidBlock(state, world, pos);
    }

    private boolean isWoodBlock(Block block) {
        // 检查方块是否为木头
        return block == Blocks.OAK_LOG || block == Blocks.SPRUCE_LOG ||
               block == Blocks.BIRCH_LOG || block == Blocks.JUNGLE_LOG ||
               block == Blocks.ACACIA_LOG || block == Blocks.DARK_OAK_LOG ||
               block == Blocks.CRIMSON_STEM || block == Blocks.WARPED_STEM ||
               block == Blocks.STRIPPED_OAK_LOG || block == Blocks.STRIPPED_SPRUCE_LOG ||
               block == Blocks.STRIPPED_BIRCH_LOG || block == Blocks.STRIPPED_JUNGLE_LOG ||
               block == Blocks.STRIPPED_ACACIA_LOG || block == Blocks.STRIPPED_DARK_OAK_LOG ||
               block == Blocks.STRIPPED_CRIMSON_STEM || block == Blocks.STRIPPED_WARPED_STEM ||
               block == Blocks.MANGROVE_LOG || block == Blocks.STRIPPED_MANGROVE_LOG ||
               block == Blocks.CHERRY_LOG || block == Blocks.STRIPPED_CHERRY_LOG;
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
               block instanceof SaplingBlock ||
               isWoodBlock(block);
    }
}