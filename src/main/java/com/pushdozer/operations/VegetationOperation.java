package com.pushdozer.operations;

import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

import java.util.Collection;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Holds a terrain-operation lock for multi-tick vegetation work (bone meal, tree generation).
 */
public final class VegetationOperation {
    private final BlockOperation.TerrainOperationToken token;
    private final ServerWorld world;

    private VegetationOperation(BlockOperation.TerrainOperationToken token, ServerWorld world) {
        this.token = token;
        this.world = world;
    }

    public static Optional<VegetationOperation> tryBegin(ServerWorld world, Collection<BlockPos> lockPositions) {
        Optional<BlockOperation.TerrainOperationToken> token = BlockOperation.beginTerrainOperation(world, lockPositions);
        return token.map(t -> new VegetationOperation(t, world));
    }

    public UUID operationId() {
        return token.operationId();
    }

    public ServerWorld world() {
        return world;
    }

    public BlockOperation.TerrainOperationToken token() {
        return token;
    }

    public boolean tryExtend(Collection<BlockPos> additionalPositions) {
        return BlockOperation.extendTerrainOperation(token, additionalPositions);
    }

    public void release() {
        BlockOperation.releaseTerrainOperation(token);
    }

    public void scheduleNextBatch(Runnable task) {
        Objects.requireNonNull(world.getServer()).execute(task);
    }
}
