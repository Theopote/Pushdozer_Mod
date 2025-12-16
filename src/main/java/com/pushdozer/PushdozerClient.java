package com.pushdozer;

import com.pushdozer.client.KeyBindings;
import com.pushdozer.config.PushdozerConfig;
import com.pushdozer.items.PushdozerItem;
import com.pushdozer.network.ClientNetworkHandler;
import com.pushdozer.render.GeometryRenderer;
import com.pushdozer.shapes.GeometryShape;
import com.pushdozer.util.ShapeUtil;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

public class PushdozerClient implements ClientModInitializer {
    private PushdozerConfig config;

    @Override
    public void onInitializeClient() {
        // 初始化配置
        this.config = PushdozerMod.getConfig();

        // 注册按键绑定
        KeyBindings.register();

        // 注册世界渲染事件
        WorldRenderEvents.AFTER_TRANSLUCENT.register(this::onWorldRenderAfterTranslucent);

        // 注册客户端网络处理器
        ClientNetworkHandler.registerClientNetworking();
    }

    private void onWorldRenderAfterTranslucent(WorldRenderContext context) {
        MinecraftClient client = MinecraftClient.getInstance();
        PlayerEntity player = client.player;
        if (player != null && player.getMainHandStack().getItem() instanceof PushdozerItem) {
            BlockPos basePos = ShapeUtil.getTargetBlockPos(player, config);
            GeometryShape shape = ShapeUtil.createShape(player, config, basePos);

            // 确保显示模式不为null
            if (shape != null && config.getDisplayMode() != null && config.getDisplayMode() != PushdozerConfig.DisplayMode.NONE) {
                renderGeometryShape(context, shape, config, config.getDisplayMode());
            }
        }
    }

    private void renderGeometryShape(WorldRenderContext context, GeometryShape shape, PushdozerConfig config, PushdozerConfig.DisplayMode displayMode) {
        MatrixStack matrices = context.matrixStack();
        VertexConsumerProvider vertexConsumers = context.consumers();
        Vec3d cameraPos = context.camera().getPos();

        if (matrices != null) {
            matrices.push();
        }
        if (matrices != null) {
            matrices.translate(-cameraPos.x, -cameraPos.y, -cameraPos.z);
        }

        // 使用目标位置而不是几何体的中心位置
        BlockPos targetPos = null;
        if (MinecraftClient.getInstance().player != null) {
            targetPos = ShapeUtil.getTargetBlockPos(MinecraftClient.getInstance().player, config);
        }
        GeometryRenderer.renderGeometryShape(matrices, vertexConsumers, shape, displayMode, targetPos);

        if (matrices != null) {
            matrices.pop();
        }
    }
}