package com.pushdozer;

import com.pushdozer.client.KeyBindings;
import com.pushdozer.config.PushdozerConfig;
import com.pushdozer.items.PushdozerItem;
import com.pushdozer.network.ClientNetworkHandler;
import com.pushdozer.render.GeometryRenderer;
import com.pushdozer.shapes.GeometryShape;
import com.pushdozer.util.ShapeUtil;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
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
        // Initialize configuration
        this.config = PushdozerMod.getConfig();

        // Register key bindings
        KeyBindings.register();

        // Register world render event (END_MAIN replaces the removed AFTER_TRANSLUCENT)
        WorldRenderEvents.END_MAIN.register(this::onWorldRenderEndMain);

        // Register client network handler
        ClientNetworkHandler.registerClientNetworking();

        // Immediately sync local config when joining multiplayer server
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) ->
            ClientNetworkHandler.sendConfigSync(PushdozerConfig.getInstance())
        );
    }

    private void onWorldRenderEndMain(WorldRenderContext context) {
        MinecraftClient client = MinecraftClient.getInstance();
        PlayerEntity player = client.player;
        if (player != null && player.getMainHandStack().getItem() instanceof PushdozerItem) {
            BlockPos basePos = ShapeUtil.getTargetBlockPos(player, config);
            GeometryShape shape = ShapeUtil.createShape(player, config, basePos);

            // Ensure display mode is not null
            if (shape != null && config.getDisplayMode() != null && config.getDisplayMode() != PushdozerConfig.DisplayMode.NONE) {
                renderGeometryShape(context, shape, config, config.getDisplayMode());
            }
        }
    }

    private void renderGeometryShape(WorldRenderContext context, GeometryShape shape, PushdozerConfig config, PushdozerConfig.DisplayMode displayMode) {
        MatrixStack matrices = context.matrices();
        VertexConsumerProvider vertexConsumers = context.consumers();
        Vec3d cameraPos = context.worldState().cameraRenderState.pos;

        if (matrices != null) {
            matrices.push();
        }
        if (matrices != null) {
            matrices.translate(-cameraPos.x, -cameraPos.y, -cameraPos.z);
        }

        // Use target position instead of geometry center position
        BlockPos targetPos = null;
        if (MinecraftClient.getInstance().player != null) {
            targetPos = ShapeUtil.getTargetBlockPos(MinecraftClient.getInstance().player, config);
        }
        GeometryRenderer.renderGeometryShape(matrices, vertexConsumers, shape, displayMode, targetPos);

        if (vertexConsumers instanceof VertexConsumerProvider.Immediate immediate) {
            immediate.draw();
        }

        if (matrices != null) {
            matrices.pop();
        }
    }
}
