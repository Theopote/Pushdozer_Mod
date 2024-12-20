package com.pushdozer;

import com.pushdozer.network.UndoRedoPayload;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.pushdozer.client.KeyBindings;
import com.pushdozer.config.PushdozerConfig;
import com.pushdozer.items.PushdozerItem;
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
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;

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

        // 注册Payload类型
        PayloadTypeRegistry.playS2C().register(
            UndoRedoPayload.ID,
            UndoRedoPayload.CODEC
        );
    }

    private void onWorldRenderAfterTranslucent(WorldRenderContext context) {
        MinecraftClient client = MinecraftClient.getInstance();
        PlayerEntity player = client.player;
        if (player != null && player.getMainHandStack().getItem() instanceof PushdozerItem pushdozerItem) {
            BlockPos basePos = ShapeUtil.getTargetBlockPos(player, config);
            GeometryShape shape = ShapeUtil.createShape(player, config, basePos);

            if (shape != null && config.getDisplayMode() != PushdozerConfig.DisplayMode.NONE) {
                renderGeometryShape(context, shape, config, config.getDisplayMode());
            }
        }
    }

    private void renderGeometryShape(WorldRenderContext context, GeometryShape shape, PushdozerConfig config, PushdozerConfig.DisplayMode displayMode) {
        MatrixStack matrices = context.matrixStack();
        VertexConsumerProvider vertexConsumers = context.consumers();
        Vec3d cameraPos = context.camera().getPos();

        matrices.push();
        matrices.translate(-cameraPos.x, -cameraPos.y, -cameraPos.z);

        GeometryRenderer.renderGeometryShape(matrices, vertexConsumers, shape, displayMode, shape.getCenter());

        matrices.pop();
    }

    public void renderGeometryShape(MatrixStack matrices, VertexConsumerProvider vertexConsumers, GeometryShape shape, BlockPos basePos) {
        GeometryRenderer.renderGeometryShape(matrices, vertexConsumers, shape, config.getDisplayMode(), basePos);
    }
}