package com.pushdozer.network;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;

/**
 * Idempotent payload type registration shared by server and client initializers.
 * Required because integrated single-player runs both in the same JVM.
 */
public final class PushdozerPayloads {
    private static boolean c2sRegistered;
    private static boolean s2cRegistered;

    private PushdozerPayloads() {
    }

    public static void registerC2S() {
        if (c2sRegistered) {
            return;
        }
        PayloadTypeRegistry.playC2S().register(UndoRedoPayload.ID, UndoRedoPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(ConfigSyncPayload.ID, ConfigSyncPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(PermissionCheckPayload.ID, PermissionCheckPayload.CODEC);
        c2sRegistered = true;
    }

    public static void registerS2C() {
        if (s2cRegistered) {
            return;
        }
        PayloadTypeRegistry.playS2C().register(TerrainOperationPayload.ID, TerrainOperationPayload.CODEC);
        s2cRegistered = true;
    }
}
