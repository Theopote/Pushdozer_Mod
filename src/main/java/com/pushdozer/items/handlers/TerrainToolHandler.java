package com.pushdozer.items.handlers;

import com.pushdozer.config.PushdozerConfig;
import com.pushdozer.operations.UndoAction;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.List;

/**
 * Common interface for all terrain tool handlers.
 * Default methods allow each handler to implement only the operations it supports.
 */
public interface TerrainToolHandler {

    default List<BlockPos> handleExcavation(PlayerEntity player, World world, PushdozerConfig config) {
        return List.of();
    }

    default List<BlockPos> handlePlacement(PlayerEntity player, World world, PushdozerConfig config) {
        return List.of();
    }

    default void handleSmoothRaise(PlayerEntity player, World world, PushdozerConfig config) {
    }

    default void handleSmoothLower(PlayerEntity player, World world, PushdozerConfig config) {
    }

    default void handleSurfaceRoughen(PlayerEntity player, World world, PushdozerConfig config) {
    }

    default void handleOperation(PlayerEntity player, World world, UndoAction.ActionType actionType, PushdozerConfig config) {
    }

    default void handleSurfaceConvert(PlayerEntity player, World world, PushdozerConfig config) {
    }

    default void handleBoneMeal(PlayerEntity player, World world, PushdozerConfig config) {
    }

    default void handleBatchPlant(PlayerEntity player, World world, PushdozerConfig config) {
    }

    default void handleShorelineProcess(PlayerEntity player, World world, PushdozerConfig config) {
    }
}
