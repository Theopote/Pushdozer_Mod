package com.pushdozer.gametest;

import com.pushdozer.config.PushdozerConfig;
import net.minecraft.block.Block;

import java.util.List;

final class PushdozerGameTestSupport {
    private PushdozerGameTestSupport() {
    }

    static PushdozerConfig createExcavationConfig(Block... breakableBlocks) {
        PushdozerConfig config = new PushdozerConfig();
        config.setBreakableBlocks(List.of(breakableBlocks));
        config.setHeightMode(PushdozerConfig.HeightMode.NO_LIMIT);
        return config;
    }
}
