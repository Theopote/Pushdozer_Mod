package com.pushdozer.items.handlers;

import com.pushdozer.config.PushdozerConfig;
import com.pushdozer.operations.UndoAction;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.world.World;

/**
 * Smooth lower mode: builds a signed Gaussian depression (H1), spatially blends it (H2),
 * then applies edge falloff and per-stroke lower limits so basins still cut reliably.
 */
public class SmoothLowerHandler extends AbstractDirectionalSmoothHandler {

    private static final float MAX_LOWER_DEPTH = 8.0f;

    public SmoothLowerHandler() {
    }

    public void handleSmoothLower(PlayerEntity player, World world, PushdozerConfig config) {
        handleOperation(player, world, UndoAction.ActionType.SMOOTH_LOWER, config);
    }

    @Override
    protected int getDirection() {
        return -1;
    }

    @Override
    protected float getMaxDeltaPerStroke() {
        return MAX_LOWER_DEPTH;
    }
}
