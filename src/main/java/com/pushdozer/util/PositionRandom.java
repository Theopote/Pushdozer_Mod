package com.pushdozer.util;

import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;

/**
 * 基于方块坐标的可复现随机源，便于测试、多人一致与问题复盘。
 */
public final class PositionRandom {

    private PositionRandom() {
    }

    public static Random at(BlockPos pos) {
        return Random.create(pos.asLong());
    }

    public static Random at(BlockPos pos, long salt) {
        return Random.create(pos.asLong() ^ salt);
    }
}
