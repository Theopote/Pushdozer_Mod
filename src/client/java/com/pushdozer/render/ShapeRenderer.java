package com.pushdozer.render;

import com.pushdozer.items.PushdozerItem;
import com.pushdozer.items.PushdozerItem.DisplayMode;
import com.pushdozer.shapes.GeometryShape;
import com.pushdozer.config.PushdozerConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;

public class ShapeRenderer {
    
    public static boolean shouldRenderShape() {
        MinecraftClient client = MinecraftClient.getInstance();
        PlayerEntity player = client.player;
        
        return player != null && isHoldingPushdozer(player) && getCurrentDisplayMode(player) != DisplayMode.NONE;
    }
    
    public static void renderShape(GeometryShape shape, PushdozerConfig config) {
        if (shouldRenderShape()) {
            DisplayMode displayMode = getCurrentDisplayMode(MinecraftClient.getInstance().player);
            renderShapeAccordingToMode(shape, displayMode, config);
        }
    }
    
    private static boolean isHoldingPushdozer(PlayerEntity player) {
        ItemStack mainHand = player.getMainHandStack();
        ItemStack offHand = player.getOffHandStack();
        
        return mainHand.getItem() instanceof PushdozerItem || 
               offHand.getItem() instanceof PushdozerItem;
    }

    private static DisplayMode getCurrentDisplayMode(PlayerEntity player) {
        ItemStack mainHand = player.getMainHandStack();
        ItemStack offHand = player.getOffHandStack();
        
        if (mainHand.getItem() instanceof PushdozerItem) {
            return ((PushdozerItem) mainHand.getItem()).getDisplayMode();
        } else if (offHand.getItem() instanceof PushdozerItem) {
            return ((PushdozerItem) offHand.getItem()).getDisplayMode();
        }
        
        return DisplayMode.NONE;
    }

    private static void renderShapeAccordingToMode(GeometryShape shape, DisplayMode mode, PushdozerConfig config) {
        switch (mode) {
            case WIREFRAME:
                renderWireframe(shape, config);
                break;
            case SURFACE:
                renderSurface(shape, config);
                break;
            default:
                // 不渲染任何内容
                break;
        }
    }

    private static void renderWireframe(GeometryShape shape, PushdozerConfig config) {
        // 实现线框渲染逻辑
    }

    private static void renderSurface(GeometryShape shape, PushdozerConfig config) {
        // 实现表面渲染逻辑
    }
}