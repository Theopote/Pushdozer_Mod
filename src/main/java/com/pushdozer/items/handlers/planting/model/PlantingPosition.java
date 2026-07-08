package com.pushdozer.items.handlers.planting.model;

import com.pushdozer.config.PushdozerConfig;
import net.minecraft.util.math.BlockPos;

public record PlantingPosition(BlockPos position, PushdozerConfig.PlantType plantType) {
}
