package com.pushdozer.render;

import com.pushdozer.PushdozerMod;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.util.Identifier;

public final class PushdozerRenderLayers {
    private static final Identifier WHITE_TEXTURE = Identifier.of(PushdozerMod.MOD_ID, "textures/misc/white.png");

    private PushdozerRenderLayers() {
    }

    public static RenderLayer getSurfaceFillLayer() {
        return RenderLayer.getEntityTranslucent(WHITE_TEXTURE);
    }
}
