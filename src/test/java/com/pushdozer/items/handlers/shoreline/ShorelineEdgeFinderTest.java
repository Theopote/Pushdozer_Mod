package com.pushdozer.items.handlers.shoreline;

import com.pushdozer.PushdozerTestBase;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.registry.tag.TagKey;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

class ShorelineEdgeFinderTest extends PushdozerTestBase {

    @Test
    void isNaturalShorelineBlock_allowsNaturalTerrain() {
        assertTrue(ShorelineEdgeFinder.isNaturalShorelineBlock(explicitState(Blocks.GRASS_BLOCK)));
        assertTrue(ShorelineEdgeFinder.isNaturalShorelineBlock(taggedState(Blocks.SAND, BlockTags.SAND)));
        assertTrue(ShorelineEdgeFinder.isNaturalShorelineBlock(explicitState(Blocks.STONE)));
    }

    @Test
    void isNaturalShorelineBlock_rejectsArchaeologyAndLogs() {
        assertFalse(ShorelineEdgeFinder.isNaturalShorelineBlock(explicitState(Blocks.SUSPICIOUS_SAND)));
        assertFalse(ShorelineEdgeFinder.isNaturalShorelineBlock(explicitState(Blocks.SUSPICIOUS_GRAVEL)));
        assertFalse(ShorelineEdgeFinder.isNaturalShorelineBlock(taggedState(Blocks.OAK_LOG, BlockTags.LOGS)));
    }

    private static BlockState explicitState(Block block) {
        BlockState state = Mockito.mock(BlockState.class);
        when(state.isAir()).thenReturn(false);
        when(state.hasBlockEntity()).thenReturn(false);
        when(state.getBlock()).thenReturn(block);
        when(state.isIn(any(TagKey.class))).thenReturn(false);
        return state;
    }

    private static BlockState taggedState(Block block, TagKey<Block> tag) {
        BlockState state = explicitState(block);
        when(state.isIn(tag)).thenReturn(true);
        return state;
    }
}
