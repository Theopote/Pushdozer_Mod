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
}
