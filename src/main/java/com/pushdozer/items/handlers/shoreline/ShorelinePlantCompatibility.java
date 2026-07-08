package com.pushdozer.items.handlers.shoreline;

import com.pushdozer.PushdozerMod;
import com.pushdozer.config.PushdozerConfig;
import com.pushdozer.items.handlers.vegetation.PlantBlockClassifier;
import com.pushdozer.items.handlers.vegetation.PlantPlacementValidator;
import net.minecraft.block.*;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.state.property.Properties;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.World;

import java.util.List;

public class ShorelinePlantCompatibility {
    private final PushdozerConfig config;

    public ShorelinePlantCompatibility(PushdozerConfig config) {
        this.config = config;
    }

    /**
     * ⭐ 新增：检查植物是否能在特定方块上生长
     * 确保植物种植的兼容性
     * 扩展：支持更多植物类型
     * 修复：添加自定义植物支持，使用更严格的种植条件检查
     * 优化：参考批量种植的种植条件逻辑
     */
    public boolean canPlantGrowOnBlock(World world, BlockPos pos, Block plantBlock, BlockState groundBlock) {
        // 修复：只有在自定义水岸类型时才检查自定义植物
        PushdozerConfig.ShorelineType shorelineType = config.getShorelineType();
        if (shorelineType == PushdozerConfig.ShorelineType.CUSTOM) {
            List<Block> customPlants = config.getCustomShorelinePlantList();
            if (!customPlants.isEmpty()) {
                if (customPlants.contains(plantBlock)) {
                    // 修复：传入种植位置（pos.up()）而不是基底位置（pos）
                    return PlantPlacementValidator.canPlantCustomBlockAt(world, pos.up(), plantBlock);
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
        if (PlantBlockClassifier.isPotted(plantBlock)) {
            boolean spotFree = currentState.isAir() || currentState.isReplaceable();
            boolean notInWater = !currentState.getFluidState().isIn(FluidTags.WATER);
            boolean solidBelow = groundBlock.isSolidBlock(world, pos.down());
            boolean notPlantBelow = !PlantBlockClassifier.isPlantBlock(groundBlock.getBlock()) && !PlantBlockClassifier.isPotted(groundBlock.getBlock());
            return spotFree && notInWater && solidBelow && notPlantBelow;
        }
        
        // 农作物（AGE_7 特征或常见作物方块）
        boolean isCrop = PlantBlockClassifier.isCropBlock(plantBlock) || plantBlock.getDefaultState().contains(Properties.AGE_7);
        if (isCrop) {
            return groundBlock.isOf(Blocks.FARMLAND) && (currentState.isAir() || currentState.isReplaceable());
        }
        
        // 活珊瑚：必须在水中或与水相邻，且下方有合适的基底
        boolean adjacentToWater = PlantPlacementValidator.isAdjacentToWater(world, pos);
        
        if (PlantBlockClassifier.isLiveCoral(plantBlock)) {
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
        if (PlantBlockClassifier.isAquatic(plantBlock)) {
            return inWater;
        }
        
        // 失活珊瑚：允许放在任何实体方块上（当前位置需为空气/可替换），忽略具体基底类型
        if (PlantBlockClassifier.isDeadCoral(plantBlock)) {
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
     * 获取植物所需的高度
     * 支持陆地、水中和半水中的高植物
     * 优化：添加随机高度支持，让植物更自然
     * 
     * @param world 世界实例（用于随机数生成）
     * @param plantBlock 植物方块
     * @return 植物所需的高度（格数）
     */
    public int getPlantRequiredHeight(World world, Block plantBlock) {
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
}
