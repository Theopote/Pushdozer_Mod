package com.pushdozer.items.handlers;

import com.pushdozer.config.PushdozerConfig;
import com.pushdozer.operations.UndoAction;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.world.World;

/**
 * Smooth raise mode: builds a signed Gaussian mound (H1), spatially blends it (H2),
 * then applies edge falloff and per-stroke raise limits so peaks still lift reliably.
 */
public class SmoothRaiseHandler extends AbstractDirectionalSmoothHandler {

    private static final float MAX_RAISE_DEPTH = 10.0f;

    public SmoothRaiseHandler() {
    }

    public void handleSmoothRaise(PlayerEntity player, World world, PushdozerConfig config) {
        handleOperation(player, world, UndoAction.ActionType.SMOOTH_RAISE, config);
    }

    @Override
    protected int getDirection() {
        return 1;
    }

    @Override
    protected float getMaxDeltaPerStroke() {
        return MAX_RAISE_DEPTH;
    }
}
