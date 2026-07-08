package com.pushdozer.config;

import com.pushdozer.PushdozerTestBase;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PushdozerConfigTest extends PushdozerTestBase {

    @Test
    void clampBrushSize_clampsToConfiguredRange() {
        assertEquals(PushdozerConfig.MIN_BRUSH_RADIUS, PushdozerConfig.clampBrushSize(0));
        assertEquals(PushdozerConfig.MIN_BRUSH_RADIUS, PushdozerConfig.clampBrushSize(-10));
        assertEquals(32, PushdozerConfig.clampBrushSize(32));
        assertEquals(PushdozerConfig.MAX_BRUSH_RADIUS, PushdozerConfig.clampBrushSize(999));
    }

    @Test
    void isBrushSizeAllowed_matchesClampRange() {
        assertFalse(PushdozerConfig.isBrushSizeAllowed(0));
        assertTrue(PushdozerConfig.isBrushSizeAllowed(PushdozerConfig.MIN_BRUSH_RADIUS));
        assertTrue(PushdozerConfig.isBrushSizeAllowed(PushdozerConfig.MAX_BRUSH_RADIUS));
        assertFalse(PushdozerConfig.isBrushSizeAllowed(PushdozerConfig.MAX_BRUSH_RADIUS + 1));
    }

    @Test
    void settersClampBrushDimensions() {
        PushdozerConfig config = new PushdozerConfig();

        config.setRadius(200);
        config.setSphereRadius(0);
        config.setLength(PushdozerConfig.MAX_BRUSH_RADIUS + 50);

        assertEquals(PushdozerConfig.MAX_BRUSH_RADIUS, config.getRadius());
        assertEquals(PushdozerConfig.MIN_BRUSH_RADIUS, config.getSphereRadius());
        assertEquals(PushdozerConfig.MAX_BRUSH_RADIUS, config.getLength());
    }

    @Test
    void ensureDefaults_clampsLoadedJsonValues() {
        PushdozerConfig config = PushdozerConfig.fromJson("""
            {
              "radius": 200,
              "sphereRadius": 150,
              "length": 80
            }
            """);

        assertEquals(PushdozerConfig.MAX_BRUSH_RADIUS, config.getRadius());
        assertEquals(PushdozerConfig.MAX_BRUSH_RADIUS, config.getSphereRadius());
        assertEquals(PushdozerConfig.MAX_BRUSH_RADIUS, config.getLength());
    }

    @Test
    void getLargestBrushDimension_returnsMaxAcrossGeometryFields() {
        PushdozerConfig config = new PushdozerConfig();
        config.setRadius(3);
        config.setSphereRadius(12);
        config.setCylinderHeight(7);

        assertEquals(12, config.getLargestBrushDimension());
    }

    @Test
    void nestedJson_roundTrips() {
        PushdozerConfig original = new PushdozerConfig();
        original.setWorkMode(PushdozerConfig.WorkMode.SMOOTH);
        original.setRadius(10);
        original.setSphereRadius(15);
        original.setSmoothStrength(0.75f);
        original.setPlantType(PushdozerConfig.PlantType.FLOWERS);
        original.setShorelineWidth(5);
        original.setDisplayMode(PushdozerConfig.DisplayMode.POINT_CLOUD);

        PushdozerConfig loaded = PushdozerConfig.fromJson(original.toJson());

        assertEquals(PushdozerConfig.WorkMode.SMOOTH, loaded.getWorkMode());
        assertEquals(10, loaded.getRadius());
        assertEquals(15, loaded.getSphereRadius());
        assertEquals(0.75f, loaded.getSmoothStrength(), 0.001f);
        assertEquals(PushdozerConfig.PlantType.FLOWERS, loaded.getPlantType());
        assertEquals(5, loaded.getShorelineWidth());
        assertEquals(PushdozerConfig.DisplayMode.POINT_CLOUD, loaded.getDisplayMode());
    }

    @Test
    void legacyFlatMigration_populatesBrushRadius() {
        PushdozerConfig config = PushdozerConfig.fromJson("""
            {
              "radius": 42,
              "shape": "Sphere",
              "geometryType": "SPHERE"
            }
            """);

        assertEquals(42, config.getRadius());
        assertEquals("Sphere", config.getShape());
        assertEquals(PushdozerConfig.GeometryType.SPHERE, config.getGeometryType());
        assertEquals(42, config.getBrush().getRadius());
    }
}
