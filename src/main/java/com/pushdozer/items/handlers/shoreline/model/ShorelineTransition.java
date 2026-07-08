package com.pushdozer.items.handlers.shoreline.model;

import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;

public final class ShorelineTransition {
    public final BlockPos pos;
    public final BlockState newState;
    public final int distance;
    private final boolean valid;

    private ShorelineTransition(BlockPos pos, BlockState newState, int distance, boolean valid) {
        this.pos = pos;
        this.newState = newState;
        this.distance = distance;
        this.valid = valid;
    }

    public static ShorelineTransition valid(BlockPos pos, BlockState newState, int distance) {
        return new ShorelineTransition(pos, newState, distance, true);
    }

    public boolean isValid() {
        return valid;
    }
}
