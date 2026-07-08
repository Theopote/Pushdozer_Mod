package com.pushdozer.config;

import com.pushdozer.PushdozerTestBase;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
}
