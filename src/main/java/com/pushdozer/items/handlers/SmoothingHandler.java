package com.pushdozer.items.handlers;

import com.pushdozer.config.PushdozerConfig;
import com.pushdozer.operations.UndoAction;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.Map;

/**
 * Standard smooth mode: flattens bumps and fills depressions using regular spatial Gaussian blur.
 * Stronger per-pass height change than adaptive mode; intended for visible peak/valley removal.
 */
public class SmoothingHandler extends AbstractTerrainToolHandler {

    private static final float MAX_HEIGHT_DELTA = 3.0f;

    public SmoothingHandler() {
    }

    public void handleSmoothing(PlayerEntity player, World world, PushdozerConfig config) {
        handleOperation(player, world, UndoAction.ActionType.SMOOTH, config);
    }

    @Override
    protected int getSamplePaddingBlocks(PushdozerConfig config) {
        int brushRadius = getEffectiveBrushRadius(config);
        float sigma = brushRadius * getSigmaFactor(brushRadius);
        float kernelRadius = sigma * 2.5f;
        return (int) Math.ceil(kernelRadius);
    }

    @Override
    protected int calculateTargetHeight(Map<BlockPos, TerrainColumn> columns,
                                      TerrainColumn currentColumn,
                                      BlockPos columnXZ,
                                      BlockPos brushCenter) {
        int brushRadius = getEffectiveBrushRadius(config);
        float originalHeight = currentColumn.getOriginalHeight();

        float smoothedHeight = calculateSmoothedHeight(columns, columnXZ, brushCenter, brushRadius);
        if (Float.isNaN(smoothedHeight)) {
            smoothedHeight = originalHeight;
        }

        float mappedStrength = applySmoothstep(config.getSmoothStrength());
        float falloff = calculateBrushEdgeFalloff(columnXZ, brushCenter, brushRadius);
        float targetHeight = blendHeightWithStrengthAndFalloff(
            originalHeight, smoothedHeight, mappedStrength, falloff);
        targetHeight = clampHeightDelta(originalHeight, targetHeight, MAX_HEIGHT_DELTA);

        return Math.round(targetHeight);
    }
}
