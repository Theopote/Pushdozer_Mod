package com.pushdozer.render;

import com.pushdozer.util.ExceptionPolicy;
import net.minecraft.client.render.*;
import org.joml.Matrix4f;
import com.pushdozer.shapes.GeometryShape;
import com.pushdozer.shapes.SphereShape;
import com.pushdozer.shapes.ConeShape;
import com.pushdozer.shapes.CylinderShape;
import com.pushdozer.shapes.EllipsoidShape;
import com.pushdozer.shapes.OctahedronShape;
import com.pushdozer.shapes.TetrahedronShape;
import com.pushdozer.shapes.TriangularPrismShape;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

public class WireframeRenderer {
    private static final float LINE_WIDTH = 2.0f;

    public static void render(MatrixStack matrices, VertexConsumerProvider vertexConsumers, GeometryShape shape, BlockPos basePos) {
        // 使用 RenderLayers.LINES 来获取线条渲染层
        RenderLayer renderLayer = RenderLayers.LINES;
        
        try {
            VertexConsumer lines = vertexConsumers.getBuffer(renderLayer);
            matrices.push();
            try {
                Vec3d center = Vec3d.ofCenter(basePos);
                matrices.translate(center.x, center.y, center.z);

                switch (shape) {
                    case SphereShape sphere -> renderSphereWireframe(lines, matrices.peek().getPositionMatrix(),
                            (float) sphere.getRadius());
                    case ConeShape cone -> renderConeWireframe(lines, matrices.peek().getPositionMatrix(),
                            cone.getBaseRadius(), cone.getHeight());
                    case CylinderShape cylinder -> renderCylinderWireframe(lines, matrices.peek().getPositionMatrix(),
                            cylinder.getRadius(), cylinder.getHeight());
                    case EllipsoidShape ellipsoid ->
                            renderEllipsoidWireframe(lines, matrices.peek().getPositionMatrix(),
                                    ellipsoid.getRadiusX(), ellipsoid.getRadiusY(), ellipsoid.getRadiusZ());
                    case OctahedronShape octahedron ->
                            renderOctahedronWireframe(lines, matrices.peek().getPositionMatrix(),
                                    octahedron.getRadius());
                    case TetrahedronShape tetrahedron ->
                            renderTetrahedronWireframe(lines, matrices.peek().getPositionMatrix(),
                                    (int) tetrahedron.getEdgeLength());
                    case TriangularPrismShape prism ->
                            renderTriangularPrismWireframe(lines, matrices.peek().getPositionMatrix(),
                                    (int) prism.getSideLength(), (int) prism.getHeight());
                    case null, default -> {
                        // 对于长方体，使用相对坐标的边界框
                        matrices.translate(-center.x, -center.y, -center.z);
                        Box box = null;
                        if (shape != null) {
                            box = shape.getBoundingBox(basePos);
                        }
                        if (box != null) {
                            drawBoxWireframe(lines, matrices.peek().getPositionMatrix(), box);
                        }
                    }
                }
            } finally {
                matrices.pop();
            }
        } catch (RuntimeException e) {
            ExceptionPolicy.rethrowIfProgrammingError(e);
            System.err.println("WireframeRenderer error: " + e.getMessage());
        }
    }
    
    private static void renderSphereWireframe(VertexConsumer lines, Matrix4f matrix, float radius) {
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
                        x, y, z
                    );
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
                        x, y, z
                    );
                }
                lastPoint = new Vec3d(x, y, z);
            }
        }
    }

    private static void renderConeWireframe(VertexConsumer lines, Matrix4f matrix, int baseRadius, int height) {
        int segments = 32;
        Vec3d lastPoint = null;
        float bottomY = -height / 2f;
        float topY = height / 2f;
        for (int i = 0; i <= segments; i++) {
            double angle = i * 2 * Math.PI / segments;
            float x = (float)(baseRadius * Math.cos(angle));
            float z = (float)(baseRadius * Math.sin(angle));
            if (lastPoint != null) {
                drawLine(lines, matrix,
                    (float)lastPoint.x, (float)lastPoint.y, (float)lastPoint.z,
                    x, bottomY, z
                );
            }
            lastPoint = new Vec3d(x, bottomY, z);
        }
        float topX = 0;
        float topZ = 0;
        for (int i = 0; i < segments; i += 4) {
            double angle = i * 2 * Math.PI / segments;
            float x = (float)(baseRadius * Math.cos(angle));
            float z = (float)(baseRadius * Math.sin(angle));
            drawLine(lines, matrix,
                x, bottomY, z,
                topX, topY, topZ
            );
        }
    }

    private static void renderCylinderWireframe(VertexConsumer lines, Matrix4f matrix, int radius, int height) {
        int segments = 32;
        Vec3d lastPointBottom = null;
        Vec3d lastPointTop = null;
        float bottomY = -height / 2f;
        float topY = height / 2f;
        for (int i = 0; i <= segments; i++) {
            double angle = i * 2 * Math.PI / segments;
            float x = (float)(radius * Math.cos(angle));
            float z = (float)(radius * Math.sin(angle));
            if (lastPointBottom != null) {
                drawLine(lines, matrix,
                    (float)lastPointBottom.x, (float)lastPointBottom.y, (float)lastPointBottom.z,
                    x, bottomY, z
                );
                drawLine(lines, matrix,
                    (float)lastPointTop.x, (float)lastPointTop.y, (float)lastPointTop.z,
                    x, topY, z
                );
            }
            lastPointBottom = new Vec3d(x, bottomY, z);
            lastPointTop = new Vec3d(x, topY, z);
        }
        for (int i = 0; i < segments; i += 8) {
            double angle = i * 2 * Math.PI / segments;
            float x = (float)(radius * Math.cos(angle));
            float z = (float)(radius * Math.sin(angle));
            drawLine(lines, matrix,
                x, bottomY, z,
                x, topY, z
            );
        }
    }

    private static void renderEllipsoidWireframe(VertexConsumer lines, Matrix4f matrix, int radiusX, int radiusY, int radiusZ) {
        // 绘制椭球体的经纬线
        int latitudeBands = 12;
        int longitudeBands = 16;
        
        // 绘制纬线
        for (int lat = 0; lat <= latitudeBands; lat++) {
            double theta = lat * Math.PI / latitudeBands;
            double sinTheta = Math.sin(theta);
            double cosTheta = Math.cos(theta);
            
            Vec3d lastPoint = null;
            for (int lon = 0; lon <= longitudeBands; lon++) {
                double phi = lon * 2 * Math.PI / longitudeBands;
                
                float x = (float)(radiusX * Math.cos(phi) * sinTheta);
                float y = (float)(radiusY * cosTheta);
                float z = (float)(radiusZ * Math.sin(phi) * sinTheta);
                
                if (lastPoint != null) {
                    drawLine(lines, matrix, 
                        (float)lastPoint.x, (float)lastPoint.y, (float)lastPoint.z,
                        x, y, z
                    );
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
                
                float x = (float)(radiusX * Math.cos(phi) * Math.sin(theta));
                float y = (float)(radiusY * Math.cos(theta));
                float z = (float)(radiusZ * Math.sin(phi) * Math.sin(theta));
                
                if (lastPoint != null) {
                    drawLine(lines, matrix,
                        (float)lastPoint.x, (float)lastPoint.y, (float)lastPoint.z,
                        x, y, z
                    );
                }
                lastPoint = new Vec3d(x, y, z);
            }
        }
    }

    private static void renderOctahedronWireframe(VertexConsumer lines, Matrix4f matrix, int radius) {
        // 正八面体的6个顶点
        float[] vertices = {
            0, radius, 0,    // 顶部
            0, -radius, 0,   // 底部
            radius, 0, 0,    // 右
            -radius, 0, 0,   // 左
            0, 0, radius,    // 前
            0, 0, -radius    // 后
        };
        
        // 绘制12条边
        int[] edges = {
            0, 2,  // 顶部到右
            0, 3,  // 顶部到左
            0, 4,  // 顶部到前
            0, 5,  // 顶部到后
            1, 2,  // 底部到右
            1, 3,  // 底部到左
            1, 4,  // 底部到前
            1, 5,  // 底部到后
            2, 4,  // 右到前
            2, 5,  // 右到后
            3, 4,  // 左到前
            3, 5   // 左到后
        };
        
        for (int i = 0; i < edges.length; i += 2) {
            int v1 = edges[i] * 3;
            int v2 = edges[i + 1] * 3;
            
            float x1 = vertices[v1];
            float y1 = vertices[v1 + 1];
            float z1 = vertices[v1 + 2];
            float x2 = vertices[v2];
            float y2 = vertices[v2 + 1];
            float z2 = vertices[v2 + 2];
            
            drawLine(lines, matrix, x1, y1, z1, x2, y2, z2);
        }
    }

    private static void renderTetrahedronWireframe(VertexConsumer lines, Matrix4f matrix, int radius) {
        // 正四面体的4个顶点
        // 使用正确的正四面体顶点坐标，边长为a的正四面体：
        // 顶点1: (a/(2*sqrt(2)), a/(2*sqrt(2)), a/(2*sqrt(2)))
        // 顶点2: (a/(2*sqrt(2)), -a/(2*sqrt(2)), -a/(2*sqrt(2)))
        // 顶点3: (-a/(2*sqrt(2)), a/(2*sqrt(2)), -a/(2*sqrt(2)))
        // 顶点4: (-a/(2*sqrt(2)), -a/(2*sqrt(2)), a/(2*sqrt(2)))
        double scale = radius / (2.0 * Math.sqrt(2.0));
        
        float[] vertices = {
            (float)scale, (float)scale, (float)scale,    // 顶点1
            (float)scale, (float)(-scale), (float)(-scale), // 顶点2
            (float)(-scale), (float)scale, (float)(-scale), // 顶点3
            (float)(-scale), (float)(-scale), (float)scale  // 顶点4
        };
        
        // 绘制6条边
        int[] edges = {
            0, 1,  // 顶点1到顶点2
            0, 2,  // 顶点1到顶点3
            0, 3,  // 顶点1到顶点4
            1, 2,  // 顶点2到顶点3
            1, 3,  // 顶点2到顶点4
            2, 3   // 顶点3到顶点4
        };
        
        for (int i = 0; i < edges.length; i += 2) {
            int v1 = edges[i] * 3;
            int v2 = edges[i + 1] * 3;
            
            float x1 = vertices[v1];
            float y1 = vertices[v1 + 1];
            float z1 = vertices[v1 + 2];
            float x2 = vertices[v2];
            float y2 = vertices[v2 + 1];
            float z2 = vertices[v2 + 2];
            
            drawLine(lines, matrix, x1, y1, z1, x2, y2, z2);
        }
    }

    private static void renderTriangularPrismWireframe(VertexConsumer lines, Matrix4f matrix, int sideLength, int height) {
        double triangleHeight = sideLength * Math.sqrt(3) / 2;
        float bottomY = -height / 2f;
        float topY = height / 2f;
        float[] bottomVertices = {
            0, bottomY, (float)(triangleHeight / 2),
            (float)(-sideLength / 2), bottomY, (float)(-triangleHeight / 2),
            (float)(sideLength / 2), bottomY, (float)(-triangleHeight / 2)
        };
        float[] topVertices = {
            0, topY, (float)(triangleHeight / 2),
            (float)(-sideLength / 2), topY, (float)(-triangleHeight / 2),
            (float)(sideLength / 2), topY, (float)(-triangleHeight / 2)
        };
        drawLine(lines, matrix, bottomVertices[0], bottomVertices[1], bottomVertices[2], bottomVertices[3], bottomVertices[4], bottomVertices[5]);
        drawLine(lines, matrix, bottomVertices[3], bottomVertices[4], bottomVertices[5], bottomVertices[6], bottomVertices[7], bottomVertices[8]);
        drawLine(lines, matrix, bottomVertices[6], bottomVertices[7], bottomVertices[8], bottomVertices[0], bottomVertices[1], bottomVertices[2]);
        drawLine(lines, matrix, topVertices[0], topVertices[1], topVertices[2], topVertices[3], topVertices[4], topVertices[5]);
        drawLine(lines, matrix, topVertices[3], topVertices[4], topVertices[5], topVertices[6], topVertices[7], topVertices[8]);
        drawLine(lines, matrix, topVertices[6], topVertices[7], topVertices[8], topVertices[0], topVertices[1], topVertices[2]);
        drawLine(lines, matrix, bottomVertices[0], bottomVertices[1], bottomVertices[2], topVertices[0], topVertices[1], topVertices[2]);
        drawLine(lines, matrix, bottomVertices[3], bottomVertices[4], bottomVertices[5], topVertices[3], topVertices[4], topVertices[5]);
        drawLine(lines, matrix, bottomVertices[6], bottomVertices[7], bottomVertices[8], topVertices[6], topVertices[7], topVertices[8]);
    }

    private static void drawBoxWireframe(VertexConsumer lines, Matrix4f matrix, Box box) {
        float minX = (float) box.minX;
        float minY = (float) box.minY;
        float minZ = (float) box.minZ;
        float maxX = (float) box.maxX;
        float maxY = (float) box.maxY;
        float maxZ = (float) box.maxZ;

        // 绘制 X 轴方向的线 (红色)
        drawLine(lines, matrix, minX, minY, minZ, maxX, minY, minZ);
        drawLine(lines, matrix, minX, minY, maxZ, maxX, minY, maxZ);
        drawLine(lines, matrix, minX, maxY, minZ, maxX, maxY, minZ);
        drawLine(lines, matrix, minX, maxY, maxZ, maxX, maxY, maxZ);

        // 绘制 Y 轴方向的线 (绿色)
        drawLine(lines, matrix, minX, minY, minZ, minX, maxY, minZ);
        drawLine(lines, matrix, maxX, minY, minZ, maxX, maxY, minZ);
        drawLine(lines, matrix, minX, minY, maxZ, minX, maxY, maxZ);
        drawLine(lines, matrix, maxX, minY, maxZ, maxX, maxY, maxZ);

        // 绘制 Z 轴方向的线 (蓝色)
        drawLine(lines, matrix, minX, minY, minZ, minX, minY, maxZ);
        drawLine(lines, matrix, maxX, minY, minZ, maxX, minY, maxZ);
        drawLine(lines, matrix, minX, maxY, minZ, minX, maxY, maxZ);
        drawLine(lines, matrix, maxX, maxY, minZ, maxX, maxY, maxZ);
    }

    private static void drawLine(VertexConsumer lines, Matrix4f matrix, 
                               float x1, float y1, float z1, 
                               float x2, float y2, float z2) {
        lines.vertex(matrix, x1, y1, z1).color((float) 1.0, (float) 1.0, (float) 1.0, (float) 1.0).normal(1, 0, 0).lineWidth(LINE_WIDTH);
        lines.vertex(matrix, x2, y2, z2).color((float) 1.0, (float) 1.0, (float) 1.0, (float) 1.0).normal(1, 0, 0).lineWidth(LINE_WIDTH);
    }
}