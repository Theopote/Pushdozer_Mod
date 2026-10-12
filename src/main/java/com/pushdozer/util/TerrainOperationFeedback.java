package com.pushdozer.util;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Visible player feedback when terrain operations cannot proceed.
 */
public final class TerrainOperationFeedback {
    private static final Logger LOGGER = LoggerFactory.getLogger("pushdozer");

    private TerrainOperationFeedback() {
    }

    public static void notifyRegionBusy(PlayerEntity player) {
        LOGGER.debug("Terrain operation skipped for {}: target region is busy", player.getName().getString());
        if (player instanceof ServerPlayerEntity serverPlayer) {
            serverPlayer.sendMessage(Text.translatable("pushdozer.message.operation_denied.region_busy"), true);
        }
    }

    public static void notifyUndoConflict(PlayerEntity player) {
        LOGGER.debug("Undo/redo skipped for {}: block state conflict or busy region", player.getName().getString());
        if (player instanceof ServerPlayerEntity serverPlayer) {
            serverPlayer.sendMessage(Text.translatable("pushdozer.message.operation_denied.undo_conflict"), true);
        }
    }

    public static void notifyPartialUndoRedo(PlayerEntity player, boolean isUndo, int applied, int total) {
        LOGGER.debug("Partial {} for {}: {}/{} blocks applied",
            isUndo ? "undo" : "redo", player.getName().getString(), applied, total);
        if (player instanceof ServerPlayerEntity serverPlayer) {
            String key = isUndo
                ? "pushdozer.message.undo_redo.partial.undo"
                : "pushdozer.message.undo_redo.partial.redo";
            serverPlayer.sendMessage(Text.translatable(key, applied, total), true);
        }
    }

    public static void notifyInvalidSurfaceConvertConfig(PlayerEntity player) {
        LOGGER.debug("Surface convert skipped for {}: no valid target materials", player.getName().getString());
        if (player instanceof ServerPlayerEntity serverPlayer) {
            serverPlayer.sendMessage(Text.translatable("pushdozer.message.surface_convert.invalid_materials"), true);
        }
    }
}
