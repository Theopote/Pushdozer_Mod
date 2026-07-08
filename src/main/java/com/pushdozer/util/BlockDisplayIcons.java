package com.pushdozer.util;

import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

/**
 * 方块图标展示用的物品栈解析，避免 UI 层重复逻辑与大范围捕获异常。
 */
public final class BlockDisplayIcons {

    private BlockDisplayIcons() {}

    public static ItemStack getDisplayStack(Block block) {
        ItemStack special = resolveCommonCases(block);
        if (!special.isEmpty()) {
            return special;
        }

        ItemStack technical = resolveTechnicalBlockItem(block);
        if (!technical.isEmpty()) {
            return technical;
        }

        ItemStack itemStack = block.asItem().getDefaultStack();
        if (!itemStack.isEmpty()) {
            return itemStack;
        }
        return resolveMissingItemFallback(block);
    }

    /**
     * 植被选择界面专用：在通用映射基础上补充作物/盆栽等图标。
     */
    public static ItemStack getPlantDisplayStack(Block block) {
        String idPath = Registries.BLOCK.getId(block).getPath().toLowerCase();

        if (block == Blocks.WHEAT) return new ItemStack(Items.WHEAT_SEEDS);
        if (block == Blocks.CARROTS) return new ItemStack(Items.CARROT);
        if (block == Blocks.POTATOES) return new ItemStack(Items.POTATO);
        if (block == Blocks.BEETROOTS) return new ItemStack(Items.BEETROOT_SEEDS);
        if (block == Blocks.MELON_STEM) return new ItemStack(Items.MELON_SEEDS);
        if (block == Blocks.PUMPKIN_STEM) return new ItemStack(Items.PUMPKIN_SEEDS);
        if (block == Blocks.SWEET_BERRY_BUSH) return new ItemStack(Items.SWEET_BERRIES);
        if (block == Blocks.COCOA) return new ItemStack(Items.COCOA_BEANS);
        if (block == Blocks.KELP_PLANT) return new ItemStack(Items.KELP);
        if (block == Blocks.SEAGRASS) return new ItemStack(Items.SEAGRASS);
        if (block == Blocks.TALL_SEAGRASS) return new ItemStack(Items.SEAGRASS);
        if (block == Blocks.CAVE_VINES || block == Blocks.CAVE_VINES_PLANT) return new ItemStack(Items.GLOW_BERRIES);
        if (block == Blocks.WEEPING_VINES || block == Blocks.WEEPING_VINES_PLANT) return new ItemStack(Items.WEEPING_VINES);
        if (block == Blocks.TWISTING_VINES || block == Blocks.TWISTING_VINES_PLANT) return new ItemStack(Items.TWISTING_VINES);
        if (block == Blocks.CHORUS_PLANT) return new ItemStack(Items.CHORUS_FRUIT);
        if (block == Blocks.SHORT_GRASS) return new ItemStack(Blocks.SHORT_GRASS);
        if (block == Blocks.TALL_GRASS) return new ItemStack(Blocks.TALL_GRASS);
        if (block == Blocks.NETHER_WART) return new ItemStack(Items.NETHER_WART);
        if (block == Blocks.TORCHFLOWER_CROP) return new ItemStack(Items.TORCHFLOWER_SEEDS);
        if (block == Blocks.PITCHER_CROP) return new ItemStack(Items.PITCHER_POD);
        if (block == Blocks.BAMBOO_SAPLING) return new ItemStack(Items.BAMBOO);

        if (idPath.startsWith("potted_")) {
            return resolvePottedPlantItem(idPath.substring("potted_".length()));
        }

        ItemStack display = getDisplayStack(block);
        return display.isEmpty() ? new ItemStack(block) : display;
    }

    private static ItemStack resolveCommonCases(Block block) {
        if (block == Blocks.WATER) return Items.WATER_BUCKET.getDefaultStack();
        if (block == Blocks.LAVA) return Items.LAVA_BUCKET.getDefaultStack();
        if (block == Blocks.FROSTED_ICE) return new ItemStack(Blocks.ICE);
        if (block == Blocks.TALL_SEAGRASS) return Items.SEAGRASS.getDefaultStack();
        if (block == Blocks.KELP_PLANT) return Items.KELP.getDefaultStack();
        if (block == Blocks.WEEPING_VINES_PLANT) return Items.WEEPING_VINES.getDefaultStack();
        if (block == Blocks.TWISTING_VINES_PLANT) return Items.TWISTING_VINES.getDefaultStack();

        String translationKey = block.getTranslationKey();
        if (translationKey.contains("wall_sign")) {
            ItemStack wallSignStack = resolveRelatedBlockItem(block, translationKey.replace("wall_sign", "sign"));
            if (!wallSignStack.isEmpty()) {
                return wallSignStack;
            }
        }

        if (block == Blocks.WHEAT) return Items.WHEAT_SEEDS.getDefaultStack();
        if (block == Blocks.CARROTS) return Items.CARROT.getDefaultStack();
        if (block == Blocks.POTATOES) return Items.POTATO.getDefaultStack();
        if (block == Blocks.BEETROOTS) return Items.BEETROOT.getDefaultStack();
        if (block == Blocks.FIRE) return Items.FLINT_AND_STEEL.getDefaultStack();

        String blockId = Registries.BLOCK.getId(block).getPath();
        if (blockId.contains("wall_head") || blockId.contains("wall_skull")) {
            ItemStack headStack = resolveRelatedBlockItem(block, blockId.replace("wall_", ""));
            if (!headStack.isEmpty()) {
                return headStack;
            }
        }

        return ItemStack.EMPTY;
    }

    private static ItemStack resolveTechnicalBlockItem(Block block) {
        if (block == Blocks.BEDROCK) return Items.BEDROCK.getDefaultStack();
        if (block == Blocks.COMMAND_BLOCK) return Items.COMMAND_BLOCK.getDefaultStack();
        if (block == Blocks.STRUCTURE_BLOCK) return Items.STRUCTURE_BLOCK.getDefaultStack();
        if (block == Blocks.JIGSAW) return Items.JIGSAW.getDefaultStack();
        if (block == Blocks.BARRIER) return Items.BARRIER.getDefaultStack();
        if (block == Blocks.LIGHT) return Items.LIGHT.getDefaultStack();
        if (block == Blocks.SPAWNER) return Items.SPAWNER.getDefaultStack();
        if (block == Blocks.TRIAL_SPAWNER) return Items.TRIAL_SPAWNER.getDefaultStack();
        if (block == Blocks.DRAGON_EGG) return Items.DRAGON_EGG.getDefaultStack();
        if (block == Blocks.END_PORTAL) return Items.END_PORTAL_FRAME.getDefaultStack();
        if (block == Blocks.END_GATEWAY) return Items.END_PORTAL_FRAME.getDefaultStack();
        if (block == Blocks.NETHER_PORTAL) return Items.OBSIDIAN.getDefaultStack();
        if (block == Blocks.END_PORTAL_FRAME) return Items.END_PORTAL_FRAME.getDefaultStack();
        if (block == Blocks.NETHER_WART) return Items.NETHER_WART.getDefaultStack();
        if (block == Blocks.CRIMSON_FUNGUS) return Items.CRIMSON_FUNGUS.getDefaultStack();
        if (block == Blocks.WARPED_FUNGUS) return Items.WARPED_FUNGUS.getDefaultStack();
        if (block == Blocks.CRIMSON_ROOTS) return Items.CRIMSON_ROOTS.getDefaultStack();
        if (block == Blocks.WARPED_ROOTS) return Items.WARPED_ROOTS.getDefaultStack();
        if (block == Blocks.NETHER_SPROUTS) return Items.NETHER_SPROUTS.getDefaultStack();
        if (block == Blocks.WEEPING_VINES) return Items.WEEPING_VINES.getDefaultStack();
        if (block == Blocks.TWISTING_VINES) return Items.TWISTING_VINES.getDefaultStack();
        if (block == Blocks.SHROOMLIGHT) return Items.SHROOMLIGHT.getDefaultStack();
        if (block == Blocks.GLOW_LICHEN) return Items.GLOW_LICHEN.getDefaultStack();
        if (block == Blocks.SCULK_VEIN) return Items.SCULK_VEIN.getDefaultStack();
        if (block == Blocks.SCULK_CATALYST) return Items.SCULK_CATALYST.getDefaultStack();
        if (block == Blocks.SCULK_SHRIEKER) return Items.SCULK_SHRIEKER.getDefaultStack();
        if (block == Blocks.SCULK_SENSOR) return Items.SCULK_SENSOR.getDefaultStack();
        if (block == Blocks.CALIBRATED_SCULK_SENSOR) return Items.CALIBRATED_SCULK_SENSOR.getDefaultStack();
        if (block == Blocks.SCULK) return Items.SCULK.getDefaultStack();
        return ItemStack.EMPTY;
    }

    private static ItemStack resolveMissingItemFallback(Block block) {
        if (block == Blocks.AIR || block == Blocks.CAVE_AIR || block == Blocks.VOID_AIR || block == Blocks.STRUCTURE_VOID) {
            return ItemStack.EMPTY;
        }

        String blockIdPath = Registries.BLOCK.getId(block).getPath().toLowerCase();
        if (blockIdPath.contains("portal")) return Items.OBSIDIAN.getDefaultStack();
        if (blockIdPath.contains("light")) return Items.GLOWSTONE.getDefaultStack();
        if (blockIdPath.contains("air") || blockIdPath.contains("void")) return ItemStack.EMPTY;
        return Items.STONE.getDefaultStack();
    }

    private static ItemStack resolvePottedPlantItem(String base) {
        Identifier baseId = Identifier.tryParse("minecraft:" + base);
        if (baseId == null) {
            return new ItemStack(Items.FLOWER_POT);
        }
        Block baseBlock = RegistryBlocks.getIfPresent(baseId);
        if (baseBlock == Blocks.AIR) {
            return new ItemStack(Items.FLOWER_POT);
        }
        if (base.equals("azalea")) return new ItemStack(Blocks.AZALEA);
        if (base.equals("flowering_azalea")) return new ItemStack(Blocks.FLOWERING_AZALEA);
        return new ItemStack(baseBlock);
    }

    private static ItemStack resolveRelatedBlockItem(Block block, String relatedPath) {
        Identifier relatedId = Registries.BLOCK.getId(block).withPath(relatedPath);
        Block related = RegistryBlocks.getIfPresent(relatedId);
        if (related != Blocks.AIR) {
            return related.asItem().getDefaultStack();
        }
        return ItemStack.EMPTY;
    }
}
