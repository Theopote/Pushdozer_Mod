package com.pushdozer.render;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import net.minecraft.client.render.OverlayTexture;
import com.pushdozer.shapes.GeometryShape;
import com.pushdozer.shapes.SphereShape;

/**
 * 表面渲染器类
 * 负责渲染几何体的表面，支持球体和长方体两种形状
 */
public class SurfaceRenderer {
    // 表面纹理的标识符
    private static final Identifier SURFACE_TEXTURE = Identifier.of("pushdozer", "textures/item/surface_texture.png");

    // 静态字和初始化代码
    private static final int LONGITUDE_SEGMENTS = 24;  // 经度分段数（水平方向）
    private static final int LATITUDE_SEGMENTS = 12;   // 纬度分段数（垂直方向）
    private static final float[][] SPHERE_VERTEX_CACHE;
    private static final float[] SIN_TABLE;
    private static final float[] COS_TABLE;
    private static final int[] INDEX_BUFFER = new int[LATITUDE_SEGMENTS * LONGITUDE_SEGMENTS * 6];

    static {
        // 初始化三角函数查找表
        SIN_TABLE = new float[LATITUDE_SEGMENTS + 1];
        COS_TABLE = new float[LATITUDE_SEGMENTS + 1];
        for (int i = 0; i <= LATITUDE_SEGMENTS; i++) {
            float angle = (float) i / LATITUDE_SEGMENTS * (float) (Math.PI * 2);
            SIN_TABLE[i] = (float) Math.sin(angle);
            COS_TABLE[i] = (float) Math.cos(angle);
        }

        // 初始化球面顶点缓存
        SPHERE_VERTEX_CACHE = new float[LATITUDE_SEGMENTS * LONGITUDE_SEGMENTS][3];
        for (int i = 0; i < LATITUDE_SEGMENTS; i++) {
            for (int j = 0; j < LONGITUDE_SEGMENTS; j++) {
                float theta = (float) i / LATITUDE_SEGMENTS * (float) Math.PI;
                float phi = (float) j / LONGITUDE_SEGMENTS * (float) (Math.PI * 2);
                SPHERE_VERTEX_CACHE[i * LONGITUDE_SEGMENTS + j] = getSpherePointInternal(1.0f, theta, phi);
            }
        }

        // 初始化索引缓冲区
        int idx = 0;
        for (int i = 0; i < LATITUDE_SEGMENTS; i++) {
            for (int j = 0; j < LONGITUDE_SEGMENTS; j++) {
                int current = i * LONGITUDE_SEGMENTS + j;
                int next = i * LONGITUDE_SEGMENTS + (j + 1) % LONGITUDE_SEGMENTS;
                int bottom = ((i + 1) % LATITUDE_SEGMENTS) * LONGITUDE_SEGMENTS + j;
                int bottomNext = ((i + 1) % LATITUDE_SEGMENTS) * LONGITUDE_SEGMENTS + (j + 1) % LONGITUDE_SEGMENTS;

                INDEX_BUFFER[idx++] = current;
                INDEX_BUFFER[idx++] = next;
                INDEX_BUFFER[idx++] = bottom;
                INDEX_BUFFER[idx++] = next;
                INDEX_BUFFER[idx++] = bottomNext;
                INDEX_BUFFER[idx++] = bottom;
            }
        }
    }

    /**
     * 渲染几何体的主方法
     * @param matrices 矩阵堆栈，用于变换
     * @param vertexConsumers 顶点消费者提供器
     * @param shape 要渲染的几何形状
     * @param basePos 基准位置
     */
    public static void render(MatrixStack matrices, VertexConsumerProvider vertexConsumers, GeometryShape shape, BlockPos basePos) {
        System.out.println("开始渲染表面");

        // 设置渲染状态
        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GlStateManager.SrcFactor.SRC_ALPHA, GlStateManager.DstFactor.ONE_MINUS_SRC_ALPHA);
        RenderSystem.disableCull();
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 0.7f);
        RenderSystem.setShaderTexture(0, SURFACE_TEXTURE);

        RenderSystem.disableCull();
        RenderSystem.enableDepthTest();
        RenderSystem.depthFunc(org.lwjgl.opengl.GL11.GL_LEQUAL);
        RenderSystem.depthMask(true);

        // 使用不同的渲染层
        VertexConsumer buffer = vertexConsumers.getBuffer(RenderLayer.getEntityTranslucent(SURFACE_TEXTURE));

        Vec3d center = Vec3d.ofCenter(basePos);

        // 检查形状类型并调用相应的渲染方法
        if (shape instanceof SphereShape sphere) {
            System.out.println("检测到球体形状，开始渲染球体");
            renderSphereOptimized(matrices, buffer, center, (float)sphere.getRadius(), 1.0f, 1.0f, 1.0f, 0.5f);
        } else {
            System.out.println("检测到长方体形状，开始渲染长方体");
            Box box = shape.getBoundingBox(basePos);
            drawBoxSurfaceOptimized(matrices, buffer, box, 1.0f, 1.0f, 1.0f, 0.7f);
        }

        // 恢复渲染状态
        RenderSystem.enableCull();
        RenderSystem.depthFunc(org.lwjgl.opengl.GL11.GL_LEQUAL);  // 恢复默认深度测试函数
        RenderSystem.depthMask(true);
        RenderSystem.disableBlend();
    }

    /**
     * 优化的球体渲染方法
     * 使用球面坐标系生成顶点
     */
    private static void renderSphereOptimized(MatrixStack matrices, VertexConsumer buffer, Vec3d center, float radius, float r, float g, float b, float a) {
        Matrix4f matrix = matrices.peek().getPositionMatrix();

        // 使用较少的分段以提高性能
        int latitudeBands = 16;
        int longitudeBands = 16;

        for (int lat = 0; lat < latitudeBands; lat++) {
            float theta1 = (float) lat * (float) Math.PI / latitudeBands;
            float theta2 = (float) (lat + 1) * (float) Math.PI / latitudeBands;

            for (int lon = 0; lon < longitudeBands; lon++) {
                float phi1 = (float) lon * 2.0f * (float) Math.PI / longitudeBands;
                float phi2 = (float) (lon + 1) * 2.0f * (float) Math.PI / longitudeBands;

                // 计算四个顶点
                float x1 = (float) (center.x + radius * Math.sin(theta1) * Math.cos(phi1));
                float y1 = (float) (center.y + radius * Math.cos(theta1));
                float z1 = (float) (center.z + radius * Math.sin(theta1) * Math.sin(phi1));

                float x2 = (float) (center.x + radius * Math.sin(theta1) * Math.cos(phi2));
                float y2 = (float) (center.y + radius * Math.cos(theta1));
                float z2 = (float) (center.z + radius * Math.sin(theta1) * Math.sin(phi2));

                float x3 = (float) (center.x + radius * Math.sin(theta2) * Math.cos(phi1));
                float y3 = (float) (center.y + radius * Math.cos(theta2));
                float z3 = (float) (center.z + radius * Math.sin(theta2) * Math.sin(phi1));

                float x4 = (float) (center.x + radius * Math.sin(theta2) * Math.cos(phi2));
                float y4 = (float) (center.y + radius * Math.cos(theta2));
                float z4 = (float) (center.z + radius * Math.sin(theta2) * Math.sin(phi2));

                // 计算纹理坐标
                float t1 = (float) lon / longitudeBands;
                float t2 = (float) (lon + 1) / longitudeBands;
                float s1 = (float) lat / latitudeBands;
                float s2 = (float) (lat + 1) / latitudeBands;

                // 计算法线
                float[] normal1 = normalizeVector(x1 - (float)center.x, y1 - (float)center.y, z1 - (float)center.z);
                float[] normal2 = normalizeVector(x2 - (float)center.x, y2 - (float)center.y, z2 - (float)center.z);
                float[] normal3 = normalizeVector(x3 - (float)center.x, y3 - (float)center.y, z3 - (float)center.z);
                float[] normal4 = normalizeVector(x4 - (float)center.x, y4 - (float)center.y, z4 - (float)center.z);

                // 绘制第一个三角形
                buffer.vertex(matrix, x1, y1, z1)
                        .color(r, g, b, a)
                        .texture(t1, s1)
                        .overlay(OverlayTexture.DEFAULT_UV)
                        .light(15728880)
                        .normal(normal1[0], normal1[1], normal1[2]);

                buffer.vertex(matrix, x2, y2, z2)
                        .color(r, g, b, a)
                        .texture(t2, s1)
                        .overlay(OverlayTexture.DEFAULT_UV)
                        .light(15728880)
                        .normal(normal2[0], normal2[1], normal2[2]);

                buffer.vertex(matrix, x3, y3, z3)
                        .color(r, g, b, a)
                        .texture(t1, s2)
                        .overlay(OverlayTexture.DEFAULT_UV)
                        .light(15728880)
                        .normal(normal3[0], normal3[1], normal3[2]);

                // 绘制第二个三角形
                buffer.vertex(matrix, x2, y2, z2)
                        .color(r, g, b, a)
                        .texture(t2, s1)
                        .overlay(OverlayTexture.DEFAULT_UV)
                        .light(15728880)
                        .normal(normal2[0], normal2[1], normal2[2]);

                buffer.vertex(matrix, x4, y4, z4)
                        .color(r, g, b, a)
                        .texture(t2, s2)
                        .overlay(OverlayTexture.DEFAULT_UV)
                        .light(15728880)
                        .normal(normal4[0], normal4[1], normal4[2]);

                buffer.vertex(matrix, x3, y3, z3)
                        .color(r, g, b, a)
                        .texture(t1, s2)
                        .overlay(OverlayTexture.DEFAULT_UV)
                        .light(15728880)
                        .normal(normal3[0], normal3[1], normal3[2]);
            }
        }
    }
    /**
     * 规范化向量
     */
    private static float[] normalizeVector(float x, float y, float z) {
        float len = (float)Math.sqrt(x*x + y*y + z*z);
        if (len < 0.0001f) {
            return new float[]{0, 1, 0}; // 防止除以零
        }
        return new float[]{x/len, y/len, z/len};
    }

    /**
     * 优化的长方体表面渲染方法
     * 按照标准顶点顺序渲染六个面
     * 顶点顺序：
     * 底面：A(左下前) -> B(右下前) -> C(右下后) -> D(左下后)
     * 顶面：E(左上前) -> F(右上前) -> G(右上后) -> H(左上后)
     */
    private static void drawBoxSurfaceOptimized(MatrixStack matrices, VertexConsumer buffer, Box box, float r, float g, float b, float a) {
        Matrix4f matrix = matrices.peek().getPositionMatrix();

        // 设置渲染状态
        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GlStateManager.SrcFactor.SRC_ALPHA, GlStateManager.DstFactor.ONE_MINUS_SRC_ALPHA);
        RenderSystem.disableCull();
        RenderSystem.setShaderColor(r, g, b, a);
        RenderSystem.setShaderTexture(0, SURFACE_TEXTURE);
        RenderSystem.enableDepthTest();
        RenderSystem.depthFunc(org.lwjgl.opengl.GL11.GL_LEQUAL);
        RenderSystem.depthMask(true);

        // 修改 drawColoredQuad 调用，只渲染正面
        // 后面
        drawSingleSideQuad(buffer, matrix,
                (float)box.maxX, (float)box.minY, (float)box.minZ,
                (float)box.minX, (float)box.minY, (float)box.minZ,
                (float)box.minX, (float)box.maxY, (float)box.minZ,
                (float)box.maxX, (float)box.maxY, (float)box.minZ,
                r, g, b, a, 0, 0, -1);

        // 底面
        drawSingleSideQuad(buffer, matrix,
                (float)box.minX, (float)box.minY, (float)box.maxZ,
                (float)box.maxX, (float)box.minY, (float)box.maxZ,
                (float)box.maxX, (float)box.minY, (float)box.minZ,
                (float)box.minX, (float)box.minY, (float)box.minZ,
                r, g, b, a, 0, -1, 0);

        // 左面
        drawSingleSideQuad(buffer, matrix,
                (float)box.minX, (float)box.minY, (float)box.minZ,
                (float)box.minX, (float)box.minY, (float)box.maxZ,
                (float)box.minX, (float)box.maxY, (float)box.maxZ,
                (float)box.minX, (float)box.maxY, (float)box.minZ,
                r, g, b, a, -1, 0, 0);

        // 右面
        drawSingleSideQuad(buffer, matrix,
                (float)box.maxX, (float)box.minY, (float)box.maxZ,
                (float)box.maxX, (float)box.minY, (float)box.minZ,
                (float)box.maxX, (float)box.maxY, (float)box.minZ,
                (float)box.maxX, (float)box.maxY, (float)box.maxZ,
                r, g, b, a, 1, 0, 0);

        // 顶面
        drawSingleSideQuad(buffer, matrix,
                (float)box.minX, (float)box.maxY, (float)box.minZ,
                (float)box.maxX, (float)box.maxY, (float)box.minZ,
                (float)box.maxX, (float)box.maxY, (float)box.maxZ,
                (float)box.minX, (float)box.maxY, (float)box.maxZ,
                r, g, b, a, 0, 1, 0);

        // 前面
        drawSingleSideQuad(buffer, matrix,
                (float)box.minX, (float)box.minY, (float)box.maxZ,
                (float)box.maxX, (float)box.minY, (float)box.maxZ,
                (float)box.maxX, (float)box.maxY, (float)box.maxZ,
                (float)box.minX, (float)box.maxY, (float)box.maxZ,
                r, g, b, a, 0, 0, 1);

        // 恢复渲染状态
        RenderSystem.depthMask(true);
        RenderSystem.disableBlend();
    }
    private static float[] getSpherePointInternal(float radius, float theta, float phi) {
        float sinTheta = SIN_TABLE[(int)(theta / (float)Math.PI * LATITUDE_SEGMENTS)];
        float cosTheta = COS_TABLE[(int)(theta / (float)Math.PI * LATITUDE_SEGMENTS)];
        float sinPhi = SIN_TABLE[(int)(phi / (float)(Math.PI * 2) * LATITUDE_SEGMENTS)];
        float cosPhi = COS_TABLE[(int)(phi / (float)(Math.PI * 2) * LATITUDE_SEGMENTS)];

        float x = radius * sinTheta * cosPhi;
        float y = radius * cosTheta;
        float z = radius * sinTheta * sinPhi;

        return new float[]{x, y, z};
    }

    // 添加新方法，只渲染单面
    private static void drawSingleSideQuad(VertexConsumer buffer, Matrix4f matrix,
                                           float x1, float y1, float z1,
                                           float x2, float y2, float z2,
                                           float x3, float y3, float z3,
                                           float x4, float y4, float z4,
                                           float r, float g, float b, float a,
                                           float nx, float ny, float nz) {
        int lightValue = 0xF000F0;

        // 只渲染正面
        buffer.vertex(matrix, x1, y1, z1).color(r, g, b, a).texture(0, 0).overlay(OverlayTexture.DEFAULT_UV).light(lightValue).normal(nx, ny, nz);
        buffer.vertex(matrix, x2, y2, z2).color(r, g, b, a).texture(1, 0).overlay(OverlayTexture.DEFAULT_UV).light(lightValue).normal(nx, ny, nz);
        buffer.vertex(matrix, x3, y3, z3).color(r, g, b, a).texture(1, 1).overlay(OverlayTexture.DEFAULT_UV).light(lightValue).normal(nx, ny, nz);
        buffer.vertex(matrix, x4, y4, z4).color(r, g, b, a).texture(0, 1).overlay(OverlayTexture.DEFAULT_UV).light(lightValue).normal(nx, ny, nz);
    }
}
