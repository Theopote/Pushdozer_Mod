package com.pushdozer.render;

import net.minecraft.client.render.*;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import com.pushdozer.shapes.*;
import com.pushdozer.util.ExceptionPolicy;


/**
 * 几何体点云渲染器
 * <p>
 * 负责渲染各种几何体表面的顶点，呈现为离散的点云效果，包括：
 * - 球体 (Sphere)
 * - 圆锥体 (Cone)
 * - 圆柱体 (Cylinder)
 * - 椭球体 (Ellipsoid)
 * - 八面体 (Octahedron)
 * - 长方体 (Box)
 * <p>
 * 主要特性：
 * 1. 在几何体表面生成密集的顶点
 * 2. 渲染为离散的点（非连线）
 * 3. 正确的深度测试
 * 4. 统一的光照处理
 */
public class PointCloudRenderer {

    private static final float LINE_WIDTH = 2.0f;

    /** 球体纬度分段数 */
    private static final int SPHERE_LATS = 32; // 增加分段数以提高点密度

    /** 球体经度分段数 */
    private static final int SPHERE_LONGS = 32;

    /** 椭球体纬度分段数 */
    private static final int ELLIPSOID_LATS = 24;

    /** 椭球体经度分段数 */
    private static final int ELLIPSOID_LONGS = 40;

    /** 默认圆周分段数 - 用于圆锥、圆柱等圆形几何体 */
    private static final int DEFAULT_SEGMENTS = 48; // 增加分段数

    /** 高度方向分段数 - 用于圆锥、圆柱 */
    private static final int HEIGHT_SEGMENTS = 10; // 增加高度分段数

    /**
     * 渲染几何体点云的主方法
     *
     * @param matrices 矩阵堆栈 - 用于3D变换
     * @param vertexConsumers 顶点消费者提供器 - 用于获取渲染缓冲区
     * @param shape 要渲染的几何形状
     * @param basePos 几何体的基准位置
     */
    public static void render(MatrixStack matrices, VertexConsumerProvider vertexConsumers, GeometryShape shape, BlockPos basePos) {
        // 使用 RenderLayers.LINES 来获取线条渲染层
        RenderLayer renderLayer = RenderLayers.LINES;
        
        try {
            // 使用现有的渲染层
            VertexConsumer buffer = vertexConsumers.getBuffer(renderLayer);
            Vec3d center = Vec3d.ofCenter(basePos);

            // 根据几何体类型调用相应的渲染方法
            if (shape instanceof SphereShape sphere) {
                renderSpherePoints(buffer, matrices.peek().getPositionMatrix(), center, (float) sphere.getRadius());
            } else if (shape instanceof ConeShape cone) {
                renderConePoints(buffer, matrices.peek().getPositionMatrix(), center, (float) cone.getBaseRadius(), (float) cone.getHeight());
            } else if (shape instanceof CylinderShape cylinder) {
                renderCylinderPoints(buffer, matrices.peek().getPositionMatrix(), center, (float) cylinder.getRadius(), (float) cylinder.getHeight());
            } else if (shape instanceof EllipsoidShape ellipsoid) {
                renderEllipsoidPoints(buffer, matrices.peek().getPositionMatrix(), center, (float) ellipsoid.getRadiusX(), (float) ellipsoid.getRadiusY(), (float) ellipsoid.getRadiusZ());
            } else if (shape instanceof OctahedronShape octahedron) {
                renderOctahedronPoints(buffer, matrices.peek().getPositionMatrix(), center, (float) octahedron.getRadius());
            } else if (shape instanceof TetrahedronShape tetrahedronShape) {
                renderTetrahedronPoints(buffer, matrices.peek().getPositionMatrix(), center, (float) tetrahedronShape.getEdgeLength());
            } else if (shape instanceof TriangularPrismShape triangularPrismShape) {
                renderTriangularPrismPoints(buffer, matrices.peek().getPositionMatrix(), center, (float) triangularPrismShape.getSideLength(), (float) triangularPrismShape.getHeight());
            } else if (shape instanceof BoxShape boxShape) {
                // 使用BoxShape的边界框进行点云渲染
                Box boundingBox = boxShape.getBoundingBox(new BlockPos((int) center.x, (int) center.y, (int) center.z));
                renderBoxPoints(buffer, matrices.peek().getPositionMatrix(), boundingBox);
            } else if (shape instanceof net.minecraft.util.math.Box box) {
                renderBoxPoints(buffer, matrices.peek().getPositionMatrix(), box);
            }

            // 立即绘制所有顶点数据
            if (vertexConsumers instanceof VertexConsumerProvider.Immediate immediate) {
                immediate.draw();
            }

        } catch (RuntimeException e) {
            ExceptionPolicy.rethrowIfProgrammingError(e);
            System.err.println("PointCloudRenderer error: " + e.getMessage());
        }
    }

    /**
     * 渲染球体表面点云
     */
    private static void renderSpherePoints(VertexConsumer buffer, Matrix4f matrix, Vec3d center, float radius) {
        renderSpheroidPoints(buffer, matrix, center, radius, radius, radius, SPHERE_LATS, SPHERE_LONGS);
    }

    /**
     * 渲染椭球体表面点云
     */
    private static void renderEllipsoidPoints(VertexConsumer buffer, Matrix4f matrix, Vec3d center, float radiusX, float radiusY, float radiusZ) {
        renderSpheroidPoints(buffer, matrix, center, radiusX, radiusY, radiusZ, ELLIPSOID_LATS, ELLIPSOID_LONGS);
    }

    /**
     * 渲染球体或椭球体表面点云
     */
    private static void renderSpheroidPoints(VertexConsumer buffer, Matrix4f matrix, Vec3d center, float radiusX, float radiusY, float radiusZ, int lats, int longs) {
        for (int lat = 0; lat <= lats; lat++) {
            // 修复纬度范围：从 -π/2 到 π/2，覆盖整个球体
            double theta = Math.PI * (-0.5 + (double) lat / lats);
            for (int lon = 0; lon <= longs; lon++) {
                double phi = lon * 2 * Math.PI / longs;

                Vec3d point = getSpheroidVertex(center, radiusX, radiusY, radiusZ, theta, phi);
                addPointAsLine(buffer, matrix, point);
            }
        }
    }

    /**
     * 渲染圆锥体表面点云
     */
    private static void renderConePoints(VertexConsumer buffer, Matrix4f matrix, Vec3d center, float radius, float height) {
        // 底面圆周点（y = -h/2）
        double bottomY = -height / 2.0;
        for (int i = 0; i < DEFAULT_SEGMENTS; i++) {
            double angle = i * 2 * Math.PI / DEFAULT_SEGMENTS;
            Vec3d basePoint = center.add(radius * Math.cos(angle), bottomY, radius * Math.sin(angle));
            addPointAsLine(buffer, matrix, basePoint);
        }
        // 顶点（y = +h/2）
        Vec3d apex = center.add(0, height / 2.0, 0);
        addPointAsLine(buffer, matrix, apex);
        // 侧面点
        for (int i = 0; i < DEFAULT_SEGMENTS; i++) {
            double angle = i * 2 * Math.PI / DEFAULT_SEGMENTS;
            for (int h = 0; h <= HEIGHT_SEGMENTS; h++) {
                double heightRatio = h / (double) HEIGHT_SEGMENTS;
                double currentRadius = radius * (1 - heightRatio);
                double y = bottomY + height * heightRatio;
                Vec3d sidePoint = center.add(
                        currentRadius * Math.cos(angle),
                        y,
                        currentRadius * Math.sin(angle)
                );
                addPointAsLine(buffer, matrix, sidePoint);
            }
        }
    }

    /**
     * 渲染圆柱体表面点云
     */
    private static void renderCylinderPoints(VertexConsumer buffer, Matrix4f matrix, Vec3d center, float radius, float height) {
        double bottomY = -height / 2.0;
        double topY = height / 2.0;
        for (int i = 0; i < DEFAULT_SEGMENTS; i++) {
            double angle = i * 2 * Math.PI / DEFAULT_SEGMENTS;
            Vec3d basePoint = center.add(radius * Math.cos(angle), bottomY, radius * Math.sin(angle));
            addPointAsLine(buffer, matrix, basePoint);
        }
        for (int i = 0; i < DEFAULT_SEGMENTS; i++) {
            double angle = i * 2 * Math.PI / DEFAULT_SEGMENTS;
            Vec3d topPoint = center.add(radius * Math.cos(angle), topY, radius * Math.sin(angle));
            addPointAsLine(buffer, matrix, topPoint);
        }
        for (int i = 0; i < DEFAULT_SEGMENTS; i++) {
            double angle = i * 2 * Math.PI / DEFAULT_SEGMENTS;
            for (int h = 0; h <= HEIGHT_SEGMENTS; h++) {
                double heightRatio = h / (double) HEIGHT_SEGMENTS;
                double y = bottomY + height * heightRatio;
                Vec3d sidePoint = center.add(
                        radius * Math.cos(angle),
                        y,
                        radius * Math.sin(angle)
                );
                addPointAsLine(buffer, matrix, sidePoint);
            }
        }
    }

    /**
     * 渲染八面体表面点云
     */
    private static void renderOctahedronPoints(VertexConsumer buffer, Matrix4f matrix, Vec3d center, float radius) {
        // 八面体的6个顶点
        Vec3d[] vertices = {
                center.add(0, radius, 0),      // 顶部
                center.add(0, -radius, 0),     // 底部
                center.add(radius, 0, 0),      // 右
                center.add(-radius, 0, 0),     // 左
                center.add(0, 0, radius),      // 前
                center.add(0, 0, -radius)      // 后
        };

        // 八面体的8个面（三角形）
        int[][] faces = {
                {0, 2, 4}, // 前面
                {0, 4, 3}, // 左面
                {0, 3, 5}, // 后面
                {0, 5, 2}, // 右面
                {1, 4, 2}, // 下面
                {1, 3, 4}, // 下面
                {1, 5, 3}, // 下面
                {1, 2, 5}  // 下面
        };

        // 为每个面生成表面点
        for (int[] face : faces) {
            Vec3d v1 = vertices[face[0]];
            Vec3d v2 = vertices[face[1]];
            Vec3d v3 = vertices[face[2]];

            // 在三角形面上生成网格点
            int gridSize = 8;
            for (int i = 0; i <= gridSize; i++) {
                for (int j = 0; j <= gridSize - i; j++) {
                    // 重心坐标
                    double u = (double) i / gridSize;
                    double v = (double) j / gridSize;
                    double w = 1.0 - u - v;
                    
                    // 确保重心坐标和为1且都非负
                    if (w >= 0) {
                        // 重心坐标插值
                        Vec3d point = v1.multiply(w).add(v2.multiply(u)).add(v3.multiply(v));
                        addPointAsLine(buffer, matrix, point);
                    }
                }
            }
        }
    }

    /**
     * 渲染长方体表面点云
     */
    private static void renderBoxPoints(VertexConsumer buffer, Matrix4f matrix, net.minecraft.util.math.Box box) {
        // 长方体的6个面
        double minX = box.minX, maxX = box.maxX;
        double minY = box.minY, maxY = box.maxY;
        double minZ = box.minZ, maxZ = box.maxZ;

        // 每个面的网格密度
        int gridDensity = 12;

        // 渲染6个面
        // 前面 (Z = minZ)
        renderBoxFace(buffer, matrix, minX, maxX, minY, maxY, minZ, minZ, gridDensity, true);
        // 后面 (Z = maxZ)
        renderBoxFace(buffer, matrix, minX, maxX, minY, maxY, maxZ, maxZ, gridDensity, true);
        // 左面 (X = minX)
        renderBoxFace(buffer, matrix, minX, minX, minY, maxY, minZ, maxZ, gridDensity, false);
        // 右面 (X = maxX)
        renderBoxFace(buffer, matrix, maxX, maxX, minY, maxY, minZ, maxZ, gridDensity, false);
        // 下面 (Y = minY)
        renderBoxFace(buffer, matrix, minX, maxX, minY, minY, minZ, maxZ, gridDensity, false);
        // 上面 (Y = maxY)
        renderBoxFace(buffer, matrix, minX, maxX, maxY, maxY, minZ, maxZ, gridDensity, false);
    }

    /**
     * 渲染长方体单个面
     */
    private static void renderBoxFace(VertexConsumer buffer, Matrix4f matrix, 
                                    double x1, double x2, double y1, double y2, double z1, double z2, 
                                    int density, boolean isFrontBack) {
        double stepX = (x2 - x1) / density;
        double stepY = (y2 - y1) / density;
        double stepZ = (z2 - z1) / density;

        for (int i = 0; i <= density; i++) {
            for (int j = 0; j <= density; j++) {
                double x, y, z;
                
                if (isFrontBack) {
                    // 前后面的X和Y变化
                    x = x1 + i * stepX;
                    y = y1 + j * stepY;
                    z = z1; // Z固定
                } else {
                    // 其他面的Y和Z变化
                    y = y1 + i * stepY;
                    z = z1 + j * stepZ;
                    x = x1; // X固定
                }
                
                addPointAsLine(buffer, matrix, new Vec3d(x, y, z));
            }
        }
    }

    /**
     * 渲染正四面体表面点云
     */
    private static void renderTetrahedronPoints(VertexConsumer buffer, Matrix4f matrix, Vec3d center, float edgeLength) {
        // 正四面体的4个顶点
        // 使用正确的正四面体顶点坐标，边长为a的正四面体：
        // 顶点1: (a/(2*sqrt(2)), a/(2*sqrt(2)), a/(2*sqrt(2)))
        // 顶点2: (a/(2*sqrt(2)), -a/(2*sqrt(2)), -a/(2*sqrt(2)))
        // 顶点3: (-a/(2*sqrt(2)), a/(2*sqrt(2)), -a/(2*sqrt(2)))
        // 顶点4: (-a/(2*sqrt(2)), -a/(2*sqrt(2)), a/(2*sqrt(2)))
        double scale = edgeLength / (2.0 * Math.sqrt(2.0));
        
        Vec3d[] vertices = {
            center.add(scale, scale, scale),                    // 顶点1
            center.add(scale, -scale, -scale),                  // 顶点2
            center.add(-scale, scale, -scale),                  // 顶点3
            center.add(-scale, -scale, scale)                   // 顶点4
        };

        // 正四面体的4个面（三角形）
        int[][] faces = {
            {0, 1, 2}, // 面1
            {0, 2, 3}, // 面2
            {0, 3, 1}, // 面3
            {1, 3, 2}  // 面4
        };

        // 为每个面生成表面点
        for (int[] face : faces) {
            Vec3d v1 = vertices[face[0]];
            Vec3d v2 = vertices[face[1]];
            Vec3d v3 = vertices[face[2]];

            // 在三角形面上生成网格点
            int gridSize = 8;
            for (int i = 0; i <= gridSize; i++) {
                for (int j = 0; j <= gridSize - i; j++) {
                    // 重心坐标
                    double u = (double) i / gridSize;
                    double v = (double) j / gridSize;
                    double w = 1.0 - u - v;
                    
                    // 确保重心坐标和为1且都非负
                    if (w >= 0) {
                        // 重心坐标插值
                        Vec3d point = v1.multiply(w).add(v2.multiply(u)).add(v3.multiply(v));
                        addPointAsLine(buffer, matrix, point);
                    }
                }
            }
        }
    }

    /**
     * 渲染三棱柱表面点云
     */
    private static void renderTriangularPrismPoints(VertexConsumer buffer, Matrix4f matrix, Vec3d center, float sideLength, float height) {
        // 等边三角形的高
        double triangleHeight = sideLength * Math.sqrt(3) / 2;
        
        // 底面三角形的3个顶点
        Vec3d[] bottomVertices = {
            center.add(0, -height / 2, triangleHeight / 2),                    // 顶部顶点
            center.add(-sideLength / 2, -height / 2, -triangleHeight / 2),     // 左下顶点
            center.add(sideLength / 2, -height / 2, -triangleHeight / 2)       // 右下顶点
        };

        // 顶面三角形的3个顶点
        Vec3d[] topVertices = {
            center.add(0, height / 2, triangleHeight / 2),                    // 顶部顶点
            center.add(-sideLength / 2, height / 2, -triangleHeight / 2),     // 左下顶点
            center.add(sideLength / 2, height / 2, -triangleHeight / 2)       // 右下顶点
        };

        // 只渲染3个矩形侧面，不渲染底面和顶面
        renderRectangleFacePoints(buffer, matrix, bottomVertices[0], bottomVertices[1], topVertices[1], topVertices[0]);
        renderRectangleFacePoints(buffer, matrix, bottomVertices[1], bottomVertices[2], topVertices[2], topVertices[1]);
        renderRectangleFacePoints(buffer, matrix, bottomVertices[2], bottomVertices[0], topVertices[0], topVertices[2]);
    }

    /**
     * 渲染矩形面点云
     */
    private static void renderRectangleFacePoints(VertexConsumer buffer, Matrix4f matrix, Vec3d v1, Vec3d v2, Vec3d v3, Vec3d v4) {
        int gridSize = 8;
        for (int i = 0; i <= gridSize; i++) {
            for (int j = 0; j <= gridSize; j++) {
                double u = (double) i / gridSize;
                double v = (double) j / gridSize;
                
                // 双线性插值
                Vec3d point = v1.multiply((1 - u) * (1 - v))
                    .add(v2.multiply(u * (1 - v)))
                    .add(v3.multiply(u * v))
                    .add(v4.multiply((1 - u) * v));
                
                addPointAsLine(buffer, matrix, point);
            }
        }
    }

    /**
     * 添加一个点到渲染缓冲区（通过短线模拟点）
     */
    private static void addPointAsLine(VertexConsumer buffer, Matrix4f matrix, Vec3d point) {
        // 点的大小
        float pointSize = 0.1f;
        
        // 创建一个小线段来模拟点
        Vec3d start = point.add(-pointSize, 0, 0);
        Vec3d end = point.add(pointSize, 0, 0);

        // 绘制线段，添加法线信息
        buffer.vertex(matrix, (float) start.x, (float) start.y, (float) start.z)
                .color(1.0f, 1.0f, 1.0f, 1.0f)
                .normal(1.0f, 0.0f, 0.0f)
                .lineWidth(LINE_WIDTH);
        buffer.vertex(matrix, (float) end.x, (float) end.y, (float) end.z)
                .color(1.0f, 1.0f, 1.0f, 1.0f)
                .normal(1.0f, 0.0f, 0.0f)
                .lineWidth(LINE_WIDTH);
    }

    /**
     * 计算球体或椭球体顶点位置
     */
    private static Vec3d getSpheroidVertex(Vec3d center, float radiusX, float radiusY, float radiusZ, double lat, double lon) {
        // 球坐标到笛卡尔坐标的转换
        double x = Math.cos(lat) * Math.cos(lon);
        double y = Math.sin(lat);
        double z = Math.cos(lat) * Math.sin(lon);

        // 使用不同半径进行缩放并平移到中心位置
        return center.add(x * radiusX, y * radiusY, z * radiusZ);
    }
}