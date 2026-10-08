package com.pushdozer.items.handlers.surface;

import com.pushdozer.tags.PushdozerBlockTags;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.EmptyBlockView;

/**
 * Determines whether source blocks may be converted and whether target blocks are valid.
 */
public final class NaturalTerrainClassifier {

    private NaturalTerrainClassifier() {
    }

    public static boolean isProtectedSource(BlockState state) {
        if (state.isAir() || state.hasBlockEntity()) {
            return true;
        }
        Block block = state.getBlock();
        if (state.isIn(PushdozerBlockTags.SURFACE_CONVERT_PROTECTED)) {
            return true;
        }
        if (state.isIn(BlockTags.PLANKS)
            || state.isIn(BlockTags.DOORS)
            || state.isIn(BlockTags.TRAPDOORS)
            || state.isIn(BlockTags.BEDS)
            || state.isIn(BlockTags.ANVIL)
            || state.isIn(BlockTags.SHULKER_BOXES)
            || state.isIn(BlockTags.STAIRS)
            || state.isIn(BlockTags.SLABS)
            || state.isIn(BlockTags.WALLS)
            || state.isIn(BlockTags.FENCES)
            || state.isIn(BlockTags.IMPERMEABLE)
            || state.isIn(BlockTags.BANNERS)
            || state.isIn(BlockTags.BUTTONS)
            || state.isIn(BlockTags.PRESSURE_PLATES)
            || state.isIn(BlockTags.RAILS)
            || state.isIn(BlockTags.CAULDRONS)
            || isGlassBlock(block)) {
            return true;
        }
        return block == Blocks.CHEST
            || block == Blocks.TRAPPED_CHEST
            || block == Blocks.BARREL
            || block == Blocks.FURNACE
            || block == Blocks.BLAST_FURNACE
            || block == Blocks.SMOKER
            || block == Blocks.CRAFTING_TABLE
            || block == Blocks.ENCHANTING_TABLE
            || block == Blocks.ANVIL
            || block == Blocks.CHIPPED_ANVIL
            || block == Blocks.DAMAGED_ANVIL
            || block == Blocks.BRICKS
            || block == Blocks.STONE_BRICKS
            || block == Blocks.NETHER_BRICKS
            || block == Blocks.RED_NETHER_BRICKS
            || block == Blocks.END_STONE_BRICKS
            || block == Blocks.PRISMARINE_BRICKS
            || block == Blocks.POLISHED_BLACKSTONE_BRICKS
            || block == Blocks.DEEPSLATE_BRICKS
            || block == Blocks.MUD_BRICKS
            || block == Blocks.QUARTZ_BRICKS
            || block == Blocks.WHITE_CONCRETE
            || block == Blocks.ORANGE_CONCRETE
            || block == Blocks.MAGENTA_CONCRETE
            || block == Blocks.LIGHT_BLUE_CONCRETE
            || block == Blocks.YELLOW_CONCRETE
            || block == Blocks.LIME_CONCRETE
            || block == Blocks.PINK_CONCRETE
            || block == Blocks.GRAY_CONCRETE
            || block == Blocks.LIGHT_GRAY_CONCRETE
            || block == Blocks.CYAN_CONCRETE
            || block == Blocks.PURPLE_CONCRETE
            || block == Blocks.BLUE_CONCRETE
            || block == Blocks.BROWN_CONCRETE
            || block == Blocks.GREEN_CONCRETE
            || block == Blocks.RED_CONCRETE
            || block == Blocks.BLACK_CONCRETE
            || isColoredConcretePowder(block)
            || isColoredGlazedTerracotta(block)
            || isColoredTerracotta(block);
    }

    public static boolean isNaturalConvertibleSource(BlockState state) {
        if (state.isAir() || state.hasBlockEntity()) {
            return false;
        }
        if (state.isIn(PushdozerBlockTags.SURFACE_CONVERTIBLE_SOURCE)) {
            return true;
        }
        return state.isIn(BlockTags.DIRT)
            || state.isIn(BlockTags.SAND)
            || state.isIn(BlockTags.BASE_STONE_OVERWORLD)
            || state.isIn(BlockTags.BASE_STONE_NETHER)
            || state.isOf(Blocks.GRAVEL)
            || state.isOf(Blocks.GRASS_BLOCK)
            || state.isOf(Blocks.MOSS_BLOCK)
            || state.isOf(Blocks.CLAY)
            || state.isOf(Blocks.MUD)
            || state.isOf(Blocks.MUDDY_MANGROVE_ROOTS)
            || state.isOf(Blocks.PODZOL)
            || state.isOf(Blocks.MYCELIUM)
            || state.isOf(Blocks.DIRT_PATH)
            || state.isOf(Blocks.FARMLAND)
            || state.isOf(Blocks.SNOW_BLOCK)
            || state.isOf(Blocks.ICE)
            || state.isOf(Blocks.PACKED_ICE)
            || state.isOf(Blocks.BLUE_ICE)
            || state.isOf(Blocks.SOUL_SAND)
            || state.isOf(Blocks.SOUL_SOIL)
            || state.isOf(Blocks.NETHERRACK)
            || state.isOf(Blocks.END_STONE)
            || state.isOf(Blocks.TERRACOTTA)
            || state.isOf(Blocks.ROOTED_DIRT)
            || state.isOf(Blocks.COARSE_DIRT)
            || state.isOf(Blocks.SANDSTONE)
            || state.isOf(Blocks.RED_SANDSTONE)
            || state.isOf(Blocks.SMOOTH_SANDSTONE)
            || state.isOf(Blocks.SMOOTH_RED_SANDSTONE)
            || state.isOf(Blocks.CALCITE)
            || state.isOf(Blocks.TUFF)
            || state.isOf(Blocks.DRIPSTONE_BLOCK)
            || state.isIn(BlockTags.SNOW);
    }

    public static boolean isConvertibleSource(BlockState state, boolean allowArtificialSurfaces) {
        if (isProtectedSource(state)) {
            return false;
        }
        if (isNaturalConvertibleSource(state)) {
            return true;
        }
        return allowArtificialSurfaces && hasSolidCollision(state);
    }

    public static boolean isValidTargetBlock(Block block) {
        if (block == null || block == Blocks.AIR) {
            return false;
        }
        BlockState defaultState = block.getDefaultState();
        if (defaultState.hasBlockEntity()) {
            return false;
        }
        if (!defaultState.getFluidState().isEmpty()) {
            return false;
        }
        if (defaultState.isIn(BlockTags.DOORS)
            || defaultState.isIn(BlockTags.TRAPDOORS)
            || defaultState.isIn(BlockTags.STAIRS)
            || defaultState.isIn(BlockTags.SLABS)
            || defaultState.isIn(BlockTags.WALLS)
            || defaultState.isIn(BlockTags.FENCES)
            || defaultState.isIn(BlockTags.IMPERMEABLE)
            || defaultState.isIn(BlockTags.BANNERS)
            || isGlassBlock(block)
            || defaultState.isIn(BlockTags.BUTTONS)
            || defaultState.isIn(BlockTags.PRESSURE_PLATES)
            || defaultState.isIn(BlockTags.RAILS)
            || defaultState.isIn(BlockTags.CAULDRONS)
            || defaultState.isIn(BlockTags.LEAVES)
            || defaultState.isIn(BlockTags.SAPLINGS)
            || defaultState.isIn(BlockTags.FLOWERS)
            || defaultState.isIn(BlockTags.CROPS)) {
            return false;
        }
        return hasSolidCollision(defaultState);
    }

    private static boolean hasSolidCollision(BlockState state) {
        return !state.getCollisionShape(EmptyBlockView.INSTANCE, BlockPos.ORIGIN).isEmpty();
    }

    private static boolean isGlassBlock(Block block) {
        String id = block.toString().toLowerCase();
        return id.contains("glass");
    }

    private static boolean isColoredTerracotta(Block block) {
        return block == Blocks.WHITE_TERRACOTTA
            || block == Blocks.ORANGE_TERRACOTTA
            || block == Blocks.MAGENTA_TERRACOTTA
            || block == Blocks.LIGHT_BLUE_TERRACOTTA
            || block == Blocks.YELLOW_TERRACOTTA
            || block == Blocks.LIME_TERRACOTTA
            || block == Blocks.PINK_TERRACOTTA
            || block == Blocks.GRAY_TERRACOTTA
            || block == Blocks.LIGHT_GRAY_TERRACOTTA
            || block == Blocks.CYAN_TERRACOTTA
            || block == Blocks.PURPLE_TERRACOTTA
            || block == Blocks.BLUE_TERRACOTTA
            || block == Blocks.BROWN_TERRACOTTA
            || block == Blocks.GREEN_TERRACOTTA
            || block == Blocks.RED_TERRACOTTA
            || block == Blocks.BLACK_TERRACOTTA;
    }

    private static boolean isColoredGlazedTerracotta(Block block) {
        return block == Blocks.WHITE_GLAZED_TERRACOTTA
            || block == Blocks.ORANGE_GLAZED_TERRACOTTA
            || block == Blocks.MAGENTA_GLAZED_TERRACOTTA
            || block == Blocks.LIGHT_BLUE_GLAZED_TERRACOTTA
            || block == Blocks.YELLOW_GLAZED_TERRACOTTA
            || block == Blocks.LIME_GLAZED_TERRACOTTA
            || block == Blocks.PINK_GLAZED_TERRACOTTA
            || block == Blocks.GRAY_GLAZED_TERRACOTTA
            || block == Blocks.LIGHT_GRAY_GLAZED_TERRACOTTA
            || block == Blocks.CYAN_GLAZED_TERRACOTTA
            || block == Blocks.PURPLE_GLAZED_TERRACOTTA
            || block == Blocks.BLUE_GLAZED_TERRACOTTA
            || block == Blocks.BROWN_GLAZED_TERRACOTTA
            || block == Blocks.GREEN_GLAZED_TERRACOTTA
            || block == Blocks.RED_GLAZED_TERRACOTTA
            || block == Blocks.BLACK_GLAZED_TERRACOTTA;
    }

    private static boolean isColoredConcretePowder(Block block) {
        return block == Blocks.WHITE_CONCRETE_POWDER
            || block == Blocks.ORANGE_CONCRETE_POWDER
            || block == Blocks.MAGENTA_CONCRETE_POWDER
            || block == Blocks.LIGHT_BLUE_CONCRETE_POWDER
            || block == Blocks.YELLOW_CONCRETE_POWDER
            || block == Blocks.LIME_CONCRETE_POWDER
            || block == Blocks.PINK_CONCRETE_POWDER
            || block == Blocks.GRAY_CONCRETE_POWDER
            || block == Blocks.LIGHT_GRAY_CONCRETE_POWDER
            || block == Blocks.CYAN_CONCRETE_POWDER
            || block == Blocks.PURPLE_CONCRETE_POWDER
            || block == Blocks.BLUE_CONCRETE_POWDER
            || block == Blocks.BROWN_CONCRETE_POWDER
            || block == Blocks.GREEN_CONCRETE_POWDER
            || block == Blocks.RED_CONCRETE_POWDER
            || block == Blocks.BLACK_CONCRETE_POWDER;
    }
}
