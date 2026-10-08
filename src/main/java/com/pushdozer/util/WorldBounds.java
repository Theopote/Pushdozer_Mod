package com.pushdozer.util;

import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;

/**
 * Shared world coordinate validation for terrain operations.
 */
public final class WorldBounds {
    private WorldBounds() {
    }

    public static int topBuildYExclusive(World world) {
        return world.getBottomY() + world.getHeight();
    }

    public static int topBuildYInclusive(World world) {
        return topBuildYExclusive(world) - 1;
    }

    public static boolean isBuildableY(World world, int y) {
        return y >= world.getBottomY() && y < topBuildYExclusive(world);
    }

    public static boolean isBuildablePos(World world, BlockPos pos) {
        return isBuildableY(world, pos.getY());
    }

    public static boolean isLoadedBuildablePos(ServerWorld world, BlockPos pos) {
        return isBuildablePos(world, pos) && world.isChunkLoaded(new ChunkPos(pos).toLong());
    }

    public static int clampBuildableY(World world, int y) {
        return Math.max(world.getBottomY(), Math.min(topBuildYInclusive(world), y));
    }
}
