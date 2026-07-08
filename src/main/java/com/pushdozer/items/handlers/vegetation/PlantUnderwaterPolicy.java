package com.pushdozer.items.handlers.vegetation;

import net.minecraft.block.Block;
import net.minecraft.block.Blocks;

/**
 * 水中种植白名单，供批量种植与岸线执行共享。
 */
public final class PlantUnderwaterPolicy {

    private PlantUnderwaterPolicy() {
    }

    public static boolean isAllowedUnderwater(Block block) {
        return PlantBlockClassifier.isLiveCoral(block)
            || PlantBlockClassifier.isAquatic(block)
            || block == Blocks.LILY_PAD
            || block == Blocks.SMALL_DRIPLEAF
            || block == Blocks.BIG_DRIPLEAF;
    }
}
