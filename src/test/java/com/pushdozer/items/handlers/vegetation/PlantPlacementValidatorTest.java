package com.pushdozer.items.handlers.vegetation;

import com.pushdozer.PushdozerTestBase;
import net.minecraft.block.BlockState;
import net.minecraft.fluid.FluidState;
import net.minecraft.fluid.Fluid;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PlantPlacementValidatorTest extends PushdozerTestBase {

    @Test
    void isAdjacentToWater_detectsWaterInAnyDirection() {
        World world = mock(World.class);
        BlockPos pos = new BlockPos(10, 64, 10);

        BlockState defaultState = mock(BlockState.class);
        FluidState defaultFluid = mock(FluidState.class);
        when(defaultState.getFluidState()).thenReturn(defaultFluid);
        when(defaultFluid.isIn(org.mockito.ArgumentMatchers.<TagKey<Fluid>>any())).thenReturn(false);
        when(world.getBlockState(any())).thenReturn(defaultState);

        BlockState waterLike = mock(BlockState.class);
        FluidState waterFluid = mock(FluidState.class);
        when(waterLike.getFluidState()).thenReturn(waterFluid);
        when(waterFluid.isIn(org.mockito.ArgumentMatchers.<TagKey<Fluid>>any())).thenReturn(true);
        when(world.getBlockState(eq(pos.offset(Direction.NORTH)))).thenReturn(waterLike);

        assertTrue(PlantPlacementValidator.isAdjacentToWater(world, pos));
    }

    @Test
    void hasEnoughSpaceForPlant_rejectsSolidObstacleAbove() {
        World world = mock(World.class);
        BlockPos base = new BlockPos(0, 64, 0);

        BlockState obstacle = mock(BlockState.class);
        FluidState obstacleFluid = mock(FluidState.class);
        when(obstacle.getFluidState()).thenReturn(obstacleFluid);
        when(obstacleFluid.isIn(org.mockito.ArgumentMatchers.<TagKey<Fluid>>any())).thenReturn(false);
        when(obstacle.isAir()).thenReturn(false);
        when(obstacle.isReplaceable()).thenReturn(false);

        // Put obstacle at base.up(1)
        when(world.getBlockState(eq(base.up(1)))).thenReturn(obstacle);

        assertFalse(PlantPlacementValidator.hasEnoughSpaceForPlant(world, base, 2));
    }
}

