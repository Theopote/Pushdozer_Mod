package com.pushdozer.items.handlers.shoreline.model;

import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;

public final class VegetationPlacement {
    public final BlockPos pos;
    public final BlockState plant;
    public final boolean isTallPlant;
    public final BlockState groundBlock;

    public VegetationPlacement(BlockPos pos, BlockState plant, boolean isTallPlant, BlockState groundBlock) {
        this.pos = pos;
        this.plant = plant;
        this.isTallPlant = isTallPlant;
        this.groundBlock = groundBlock;
    }
}
