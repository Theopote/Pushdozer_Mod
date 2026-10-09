package com.pushdozer.config.domain;

import com.pushdozer.PushdozerTestBase;
import net.minecraft.block.Blocks;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class ShorelineConfigTest extends PushdozerTestBase {

    @Test
    void normalize_clampsWidthAndPreservesBlockOrder() {
        ShorelineConfig config = new ShorelineConfig();
        config.setShorelineWidth(99);
        config.setCustomShorelineBlocks(List.of("minecraft:sand", "minecraft:gravel", "minecraft:dirt"));

        config.normalize();

        assertEquals(ShorelineConfig.MAX_SHORELINE_WIDTH, config.getShorelineWidth());
        assertEquals(Blocks.SAND, config.getCustomShorelineBlockList().get(0));
        assertEquals(Blocks.GRAVEL, config.getCustomShorelineBlockList().get(1));
        assertEquals(Blocks.DIRT, config.getCustomShorelineBlockList().get(2));
    }

    @Test
    void customBlockList_keepsInsertionOrder() {
        ShorelineConfig config = new ShorelineConfig();
        config.setCustomShorelineBlocks(List.of("minecraft:stone", "minecraft:cobblestone"));

        assertSame(Blocks.STONE, config.getCustomShorelineBlockList().getFirst());
        assertSame(Blocks.COBBLESTONE, config.getCustomShorelineBlockList().get(1));
    }
}
