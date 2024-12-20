package com.pushdozer.shapes;

import com.pushdozer.config.PushdozerConfig;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

public class GeometryShapeFactory {
    public static GeometryShape createShape(String shapeType, PushdozerConfig config, BlockPos center) {
        switch (shapeType.toLowerCase()) {
            case "sphere":
                return new SphereShape(config.getRadius(), Vec3d.ofCenter(center));
            case "box":
            default:
                return new BoxShape(config.getLength(), config.getWidth(), config.getHeight(), center);
        }
    }

    public static BoxShape createBoxShape(PushdozerConfig config, BlockPos center) {
        return new BoxShape(config.getLength(), config.getWidth(), config.getHeight(), center);
    }
}