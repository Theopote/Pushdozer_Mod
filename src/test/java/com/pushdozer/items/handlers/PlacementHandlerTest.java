package com.pushdozer.items.handlers;

import com.pushdozer.PushdozerTestBase;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.world.World;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

class PlacementHandlerTest extends PushdozerTestBase {

    @Test
    void intersectsPlayer_detectsBoundingBoxOverlap() {
        PlayerEntity player = Mockito.mock(PlayerEntity.class);
        when(player.getBoundingBox()).thenReturn(new Box(1.0, 64.0, 1.0, 2.0, 65.0, 2.0));

        assertTrue(PlacementHandler.intersectsPlayer(player, new BlockPos(1, 64, 1)));
        assertFalse(PlacementHandler.intersectsPlayer(player, new BlockPos(5, 64, 5)));
    }

    @Test
    void isAllowedBlock_allowsReplaceableNonSolidBlocks() {
        PlacementHandler handler = new PlacementHandler();
        World world = Mockito.mock(World.class);
        BlockState air = Blocks.AIR.getDefaultState();
        BlockPos pos = new BlockPos(0, 64, 0);
        when(world.getBlockState(any())).thenReturn(air);

        assertTrue(handler.isAllowedBlock(air, world, pos));
    }
}
