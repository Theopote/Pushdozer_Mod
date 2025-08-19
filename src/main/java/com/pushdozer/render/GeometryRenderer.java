package com.pushdozer.render;

import com.pushdozer.config.PushdozerConfig;
import com.pushdozer.shapes.GeometryShape;

import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.BlockPos;

public class GeometryRenderer {
    public static void renderGeometryShape(MatrixStack matrices, VertexConsumerProvider vertexConsumers, GeometryShape shape, PushdozerConfig.DisplayMode displayMode, BlockPos basePos) {
        switch (displayMode) {
            case WIREFRAME:
                WireframeRenderer.render(matrices, vertexConsumers, shape, basePos);
                break;
            case POINT_CLOUD:
                PointCloudRenderer.render(matrices, vertexConsumers, shape, basePos);
                break;
            case NONE:
                // 不渲染任何内容
                break;
        }
    }
}
