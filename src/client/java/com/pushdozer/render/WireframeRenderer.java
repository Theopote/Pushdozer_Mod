package com.pushdozer.render;

import net.minecraft.client.render.*;
import org.joml.Matrix4f;
import com.mojang.blaze3d.systems.RenderSystem;
import com.pushdozer.shapes.GeometryShape;
import com.pushdozer.shapes.SphereShape;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.opengl.GL11;

public class WireframeRenderer {
    
    public static void render(MatrixStack matrices, VertexConsumerProvider vertexConsumers, GeometryShape shape, BlockPos basePos) {
        // 修改渲染状态设置顺序和参数
        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        RenderSystem.setShader(GameRenderer::getPositionColorProgram);
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
        RenderSystem.disableCull();
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(false); // 修改为 false，避免深度写入影响线框显示
        RenderSystem.lineWidth(1.0f);

        try {
            VertexConsumer lines = vertexConsumers.getBuffer(RenderLayer.getLines());
            matrices.push();
            try {
                if (shape instanceof SphereShape sphere) {
                    // 使用与 SurfaceRenderer 相同的中心点计算方法
                    Vec3d center = Vec3d.ofCenter(basePos);
                    matrices.translate(center.x, center.y, center.z);
                    renderSphereWireframe(lines, matrices.peek().getPositionMatrix(), 
                                    Vec3d.ZERO, (float)sphere.getRadius());
                } else {
                    // 对于长方体，使用相对坐标的边界框
                    matrices.translate(basePos.getX(), basePos.getY(), basePos.getZ());
                    Box box = shape.getBoundingBox(BlockPos.ORIGIN); // 使用ORIGIN因为已经在矩阵中应用了偏移
                    drawBoxWireframe(lines, matrices.peek().getPositionMatrix(), box);
                }
            } finally {
                matrices.pop();
            }
        } finally {
            // 恢复渲染状态
            RenderSystem.enableCull();
            RenderSystem.depthMask(true);
            RenderSystem.disableBlend();
            RenderSystem.defaultBlendFunc();
            RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
        }
    }
    
    private static void renderSphereWireframe(VertexConsumer lines, Matrix4f matrix, Vec3d center, float radius) {
        // 减少经纬线数量以提高性能
        int latitudeBands = 14;
        int longitudeBands = 20;
        
        // 绘制纬线
        for (int lat = 0; lat <= latitudeBands; lat++) {
            double theta = lat * Math.PI / latitudeBands;
            double sinTheta = Math.sin(theta);
            double cosTheta = Math.cos(theta);
            
            Vec3d lastPoint = null;
            for (int lon = 0; lon <= longitudeBands; lon++) {
                double phi = lon * 2 * Math.PI / longitudeBands;
                
                float x = (float)(radius * Math.cos(phi) * sinTheta);
                float y = (float)(radius * cosTheta);
                float z = (float)(radius * Math.sin(phi) * sinTheta);
                
                if (lastPoint != null) {
                    drawLine(lines, matrix, 
                        (float)lastPoint.x, (float)lastPoint.y, (float)lastPoint.z,
                        x, y, z,
                        1.0f, 1.0f, 1.0f, 1.0f);
                }
                lastPoint = new Vec3d(x, y, z);
            }
        }
        
        // 绘制经线
        for (int lon = 0; lon < longitudeBands; lon++) {
            double phi = lon * 2 * Math.PI / longitudeBands;
            Vec3d lastPoint = null;
            
            for (int lat = 0; lat <= latitudeBands; lat++) {
                double theta = lat * Math.PI / latitudeBands;
                
                float x = (float)(radius * Math.cos(phi) * Math.sin(theta));
                float y = (float)(radius * Math.cos(theta));
                float z = (float)(radius * Math.sin(phi) * Math.sin(theta));
                
                if (lastPoint != null) {
                    drawLine(lines, matrix,
                        (float)lastPoint.x, (float)lastPoint.y, (float)lastPoint.z,
                        x, y, z,
                        1.0f, 1.0f, 1.0f, 1.0f);
                }
                lastPoint = new Vec3d(x, y, z);
            }
        }
    }

    private static void drawBoxWireframe(VertexConsumer lines, Matrix4f matrix, Box box) {
        float minX = (float) box.minX;
        float minY = (float) box.minY;
        float minZ = (float) box.minZ;
        float maxX = (float) box.maxX;
        float maxY = (float) box.maxY;
        float maxZ = (float) box.maxZ;

        // 绘制 X 轴方向的线 (红色)
        drawLine(lines, matrix, minX, minY, minZ, maxX, minY, minZ, 1.0F, 1.0F, 1.0F, 1.0F);
        drawLine(lines, matrix, minX, minY, maxZ, maxX, minY, maxZ, 1.0F, 1.0F, 1.0F, 1.0F);
        drawLine(lines, matrix, minX, maxY, minZ, maxX, maxY, minZ, 1.0F, 1.0F, 1.0F, 1.0F);
        drawLine(lines, matrix, minX, maxY, maxZ, maxX, maxY, maxZ, 1.0F, 1.0F, 1.0F, 1.0F);

        // 绘制 Y 轴方向的线 (绿色)
        drawLine(lines, matrix, minX, minY, minZ, minX, maxY, minZ, 1.0F, 1.0F, 1.0F, 1.0F);
        drawLine(lines, matrix, maxX, minY, minZ, maxX, maxY, minZ, 1.0F, 1.0F, 1.0F, 1.0F);
        drawLine(lines, matrix, minX, minY, maxZ, minX, maxY, maxZ, 1.0F, 1.0F, 1.0F, 1.0F);
        drawLine(lines, matrix, maxX, minY, maxZ, maxX, maxY, maxZ, 1.0F, 1.0F, 1.0F, 1.0F);

        // 绘制 Z 轴方向的线 (蓝色)
        drawLine(lines, matrix, minX, minY, minZ, minX, minY, maxZ, 1.0F, 1.0F, 1.0F, 1.0F);
        drawLine(lines, matrix, maxX, minY, minZ, maxX, minY, maxZ, 1.0F, 1.0F, 1.0F, 1.0F);
        drawLine(lines, matrix, minX, maxY, minZ, minX, maxY, maxZ, 1.0F, 1.0F, 1.0F, 1.0F);
        drawLine(lines, matrix, maxX, maxY, minZ, maxX, maxY, maxZ, 1.0F, 1.0F, 1.0F, 1.0F);
    }

    private static void drawLine(VertexConsumer lines, Matrix4f matrix, 
                               float x1, float y1, float z1, 
                               float x2, float y2, float z2, 
                               float r, float g, float b, float a) {
        lines.vertex(matrix, x1, y1, z1).color(r, g, b, a).normal(1, 0, 0);
        lines.vertex(matrix, x2, y2, z2).color(r, g, b, a).normal(1, 0, 0);
    }
}