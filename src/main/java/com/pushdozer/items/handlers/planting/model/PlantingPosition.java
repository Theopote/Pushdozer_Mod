package com.pushdozer.items.handlers.planting.model;

import com.pushdozer.config.PushdozerConfig;
import net.minecraft.util.math.BlockPos;

public final class PlantingPosition {
    public final BlockPos position;
    public final PushdozerConfig.PlantType plantType;

    public PlantingPosition(BlockPos position, PushdozerConfig.PlantType plantType) {
        this.position = position;
        this.plantType = plantType;
    }
}
