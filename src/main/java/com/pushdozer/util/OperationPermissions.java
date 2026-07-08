package com.pushdozer.util;

import com.pushdozer.config.PushdozerConfig;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.world.World;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 地形工具操作权限校验；拒绝时向玩家发送可见反馈，避免静默失败。
 */
public final class OperationPermissions {

    private static final Logger LOGGER = LoggerFactory.getLogger("pushdozer");

    private OperationPermissions() {}

    public static boolean checkForTerrainOperation(PlayerEntity player, World world, PushdozerConfig config) {
        if (world.getServer() != null && world.getServer().isSingleplayer()) {
            return true;
        }
        return checkBrushSize(player, config.getLargestBrushDimension());
    }

    public static boolean checkBrushRadius(ServerPlayerEntity player, int radius) {
        return checkBrushSize(player, radius);
    }

    private static boolean checkBrushSize(PlayerEntity player, int brushSize) {
        if (PushdozerConfig.isBrushSizeAllowed(brushSize)) {
            return true;
        }
        notifyBrushSizeDenied(player, brushSize);
        return false;
    }

    private static void notifyBrushSizeDenied(PlayerEntity player, int brushSize) {
        LOGGER.warn("玩家 {} 操作被拒绝：笔刷尺寸 {} 超出允许范围 ({}-{})",
            player.getName().getString(), brushSize,
            PushdozerConfig.MIN_BRUSH_RADIUS, PushdozerConfig.MAX_BRUSH_RADIUS);
        if (player instanceof ServerPlayerEntity serverPlayer) {
            serverPlayer.sendMessage(
                Text.translatable(
                    "pushdozer.message.operation_denied.brush_too_large",
                    brushSize,
                    PushdozerConfig.MIN_BRUSH_RADIUS,
                    PushdozerConfig.MAX_BRUSH_RADIUS
                ),
                true
            );
        }
    }
}
