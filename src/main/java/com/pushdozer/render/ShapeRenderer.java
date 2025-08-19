package com.pushdozer.render;

import com.pushdozer.items.PushdozerItem;
import com.pushdozer.items.PushdozerItem.DisplayMode;
import com.pushdozer.shapes.GeometryShape;
import com.pushdozer.config.PushdozerConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;

public class ShapeRenderer {
    
    public static boolean shouldRenderShape() {
        MinecraftClient client = MinecraftClient.getInstance();
        PlayerEntity player = client.player;
        
        return player != null && isHoldingPushdozer(player) && getCurrentDisplayMode(player) != DisplayMode.NONE;
    }
    
    public static void renderShape(GeometryShape shape, PushdozerConfig config) {
        if (shouldRenderShape()) {
            DisplayMode displayMode = null;
            if (MinecraftClient.getInstance().player != null) {
                displayMode = getCurrentDisplayMode(MinecraftClient.getInstance().player);
            }
            if (displayMode != null) {
                renderShapeAccordingToMode(shape, displayMode, config);
            }
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
            return ((PushdozerItem) mainHand.getItem()).getDisplayMode(mainHand);
        } else if (offHand.getItem() instanceof PushdozerItem) {
            return ((PushdozerItem) offHand.getItem()).getDisplayMode(offHand);
        }
        
        return DisplayMode.NONE;
    }

    private static void renderShapeAccordingToMode(GeometryShape shape, DisplayMode mode, PushdozerConfig config) {
        switch (mode) {
            case WIREFRAME:
                renderWireframe(shape, config);
                break;
            case POINT_CLOUD:
                renderPointCloud(shape, config);
                break;
            default:
                // 不渲染任何内容
                break;
        }
    }

    private static void renderWireframe(GeometryShape shape, PushdozerConfig config) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player != null) {
            MatrixStack matrices = new MatrixStack();
            VertexConsumerProvider vertexConsumers = client.getBufferBuilders().getEntityVertexConsumers();
            BlockPos basePos = client.player.getBlockPos();
            
            WireframeRenderer.render(matrices, vertexConsumers, shape, basePos);
        }
    }



    private static void renderPointCloud(GeometryShape shape, PushdozerConfig config) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player != null) {
            MatrixStack matrices = new MatrixStack();
            VertexConsumerProvider vertexConsumers = client.getBufferBuilders().getEntityVertexConsumers();
            BlockPos basePos = client.player.getBlockPos();
            
            PointCloudRenderer.render(matrices, vertexConsumers, shape, basePos);
        }
    }
}