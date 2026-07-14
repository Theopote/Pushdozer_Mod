package com.pushdozer.render;

import com.pushdozer.render.mesh.BlockOutlineMeshCache;
import com.pushdozer.shapes.GeometryShape;
import com.pushdozer.util.ExceptionPolicy;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import org.joml.Matrix4f;

import java.util.List;

/**
 * Renders brush volume as semi-transparent exterior faces.
 * Uses the same greedy mesh approach as Director's tool preview.
 */
public final class SurfaceRenderer {
    private static final BlockOutlineMeshCache MESH_CACHE = new BlockOutlineMeshCache();

    private static final float FILL_RED = 1.0f;
    private static final float FILL_GREEN = 0.8f;
    private static final float FILL_BLUE = 0.0f;
    private static final float FILL_ALPHA = 0.275f;

    private SurfaceRenderer() {
    }

    public static void render(MatrixStack matrices, VertexConsumerProvider vertexConsumers,
                              GeometryShape shape, BlockPos basePos) {
        if (matrices == null || vertexConsumers == null || shape == null) {
            return;
        }

        try {
            List<BlockPos> blocks = shape.getBlockPositions();
            if (blocks.isEmpty()) {
                return;
            }

            BlockOutlineMeshCache.Mesh mesh = MESH_CACHE.getOrCreateMesh(blocks);
            if (mesh.quads().isEmpty()) {
                return;
            }

            RenderLayer fillLayer = PushdozerRenderLayers.getSurfaceFillLayer();
            VertexConsumer fillConsumer = vertexConsumers.getBuffer(fillLayer);
            Matrix4f matrix = matrices.peek().getPositionMatrix();

            for (BlockOutlineMeshCache.Quad quad : mesh.quads()) {
                renderQuad(fillConsumer, matrix, quad);
            }
        } catch (RuntimeException exception) {
            ExceptionPolicy.rethrowIfProgrammingError(exception);
        }
    }

    private static void renderQuad(VertexConsumer consumer, Matrix4f matrix, BlockOutlineMeshCache.Quad quad) {
        Direction direction = quad.dir();
        float nx = direction != null ? direction.getOffsetX() : 0f;
        float ny = direction != null ? direction.getOffsetY() : 0f;
        float nz = direction != null ? direction.getOffsetZ() : 0f;

        emitVertex(consumer, matrix, quad.p1(), nx, ny, nz);
        emitVertex(consumer, matrix, quad.p2(), nx, ny, nz);
        emitVertex(consumer, matrix, quad.p3(), nx, ny, nz);
        emitVertex(consumer, matrix, quad.p4(), nx, ny, nz);
    }

    private static void emitVertex(VertexConsumer consumer, Matrix4f matrix, BlockPos pos,
                                   float nx, float ny, float nz) {
        consumer.vertex(matrix, pos.getX(), pos.getY(), pos.getZ())
            .color(FILL_RED, FILL_GREEN, FILL_BLUE, FILL_ALPHA)
            .texture(0.5f, 0.5f)
            .overlay(OverlayTexture.DEFAULT_UV)
            .light(LightmapTextureManager.MAX_LIGHT_COORDINATE)
            .normal(nx, ny, nz);
    }
}
