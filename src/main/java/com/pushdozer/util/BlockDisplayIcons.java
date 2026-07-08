package com.pushdozer.util;

import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

/**
 * 方块图标展示用的物品栈解析，避免 UI 层大范围捕获异常。
 */
public final class BlockDisplayIcons {

    private BlockDisplayIcons() {}

    public static ItemStack getDisplayStack(Block block) {
        if (block == Blocks.WATER) {
            return Items.WATER_BUCKET.getDefaultStack();
        }
        if (block == Blocks.LAVA) {
            return Items.LAVA_BUCKET.getDefaultStack();
        }
        if (block == Blocks.TALL_SEAGRASS) {
            return Items.SEAGRASS.getDefaultStack();
        }
        if (block == Blocks.KELP_PLANT) {
            return Items.KELP.getDefaultStack();
        }
        if (block == Blocks.WEEPING_VINES_PLANT) {
            return Items.WEEPING_VINES.getDefaultStack();
        }
        if (block == Blocks.TWISTING_VINES_PLANT) {
            return Items.TWISTING_VINES.getDefaultStack();
        }

        String translationKey = block.getTranslationKey();
        if (translationKey.contains("wall_sign")) {
            ItemStack wallSignStack = resolveRelatedBlockItem(block, translationKey.replace("wall_sign", "sign"));
            if (!wallSignStack.isEmpty()) {
                return wallSignStack;
            }
        }

        if (block == Blocks.WHEAT) {
            return Items.WHEAT_SEEDS.getDefaultStack();
        }
        if (block == Blocks.CARROTS) {
            return Items.CARROT.getDefaultStack();
        }
        if (block == Blocks.POTATOES) {
            return Items.POTATO.getDefaultStack();
        }
        if (block == Blocks.BEETROOTS) {
            return Items.BEETROOT.getDefaultStack();
        }
        if (block == Blocks.FIRE) {
            return Items.FLINT_AND_STEEL.getDefaultStack();
        }
        if (block == Blocks.FROSTED_ICE) {
            return new ItemStack(Blocks.ICE);
        }

        String blockId = Registries.BLOCK.getId(block).getPath();
        if (blockId.contains("wall_head") || blockId.contains("wall_skull")) {
            ItemStack headStack = resolveRelatedBlockItem(block, blockId.replace("wall_", ""));
            if (!headStack.isEmpty()) {
                return headStack;
            }
        }

        return block.asItem().getDefaultStack();
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
