package com.pushdozer.shapes;

import com.pushdozer.config.PushdozerConfig;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

public class GeometryShapeFactory {
    public static GeometryShape createShape(String shapeType, PushdozerConfig config, BlockPos center) {
        String lowerShapeType = shapeType.toLowerCase();
        
        return switch (lowerShapeType) {
            case "sphere" -> new SphereShape(config.getSphereRadius(), Vec3d.ofCenter(center));
            case "octahedron" -> new OctahedronShape(config.getOctahedronRadius(), center);
            case "cylinder" -> new CylinderShape(config.getCylinderRadius(), config.getCylinderHeight(), center);
            case "cone" -> new ConeShape(config.getConeRadius(), config.getConeHeight(), center);
            case "ellipsoid" -> new EllipsoidShape(config.getLength(), config.getEllipsoidHeight(), config.getWidth(), center);
            case "tetrahedron" -> new TetrahedronShape(config.getTetrahedronEdgeLength(), center);
            case "triangular_prism" -> new TriangularPrismShape(config.getTriangularPrismSideLength(), config.getTriangularPrismHeight(), center);
            default -> new BoxShape(config.getLength(), config.getWidth(), config.getBoxHeight(), center);
        };
    }

    public static GeometryShape createShape(PushdozerConfig.GeometryType geometryType, PushdozerConfig config, BlockPos center) {
        return createShape(geometryType.getShapeString(), config, center);
    }
}