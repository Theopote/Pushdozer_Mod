package com.pushdozer.operations;

import net.minecraft.registry.RegistryKey;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks in-flight terrain operations per dimension and prevents overlapping chunk writes.
 */
public final class TerrainOperationScheduler {
    private static final Logger LOGGER = LoggerFactory.getLogger("pushdozer");
    private static final TerrainOperationScheduler INSTANCE = new TerrainOperationScheduler();

    private record WorldKey(RegistryKey<World> worldKey) {
    }

    private final Map<WorldKey, Object> worldLocks = new ConcurrentHashMap<>();
    private final Map<WorldKey, Map<ChunkPos, UUID>> activeChunks = new ConcurrentHashMap<>();
    private final Map<WorldKey, Map<UUID, Set<ChunkPos>>> operationChunks = new ConcurrentHashMap<>();

    public static TerrainOperationScheduler getInstance() {
        return INSTANCE;
    }

    /**
     * @return false when any target chunk is already locked by another operation
     */
    public boolean tryAcquire(ServerWorld world, UUID operationId, Collection<BlockPos> positions) {
        if (positions.isEmpty()) {
            return true;
        }

        Set<ChunkPos> chunks = collectChunks(positions);
        WorldKey key = worldKey(world);
        Object lock = worldLocks.computeIfAbsent(key, ignored -> new Object());

        synchronized (lock) {
            Map<ChunkPos, UUID> lockedChunks = activeChunks.computeIfAbsent(key, ignored -> new HashMap<>());
            for (ChunkPos chunkPos : chunks) {
                UUID owner = lockedChunks.get(chunkPos);
                if (owner != null && !owner.equals(operationId)) {
                    LOGGER.debug("Chunk {} in {} is busy for operation {}", chunkPos, key.worldKey().getValue(), owner);
                    return false;
                }
            }

            for (ChunkPos chunkPos : chunks) {
                lockedChunks.put(chunkPos, operationId);
            }
            operationChunks.computeIfAbsent(key, ignored -> new HashMap<>()).put(operationId, chunks);
            return true;
        }
    }

    public void release(ServerWorld world, UUID operationId) {
        WorldKey key = worldKey(world);
        Object lock = worldLocks.get(key);
        if (lock == null) {
            return;
        }

        synchronized (lock) {
            Map<UUID, Set<ChunkPos>> operations = operationChunks.get(key);
            if (operations == null) {
                return;
            }

            Set<ChunkPos> chunks = operations.remove(operationId);
            if (chunks == null) {
                return;
            }

            Map<ChunkPos, UUID> lockedChunks = activeChunks.get(key);
            if (lockedChunks != null) {
                for (ChunkPos chunkPos : chunks) {
                    UUID owner = lockedChunks.get(chunkPos);
                    if (operationId.equals(owner)) {
                        lockedChunks.remove(chunkPos);
                    }
                }
                if (lockedChunks.isEmpty()) {
                    activeChunks.remove(key);
                }
            }

            if (operations.isEmpty()) {
                operationChunks.remove(key);
            }
        }
    }

    public boolean isChunkBusy(ServerWorld world, ChunkPos chunkPos) {
        WorldKey key = worldKey(world);
        Map<ChunkPos, UUID> lockedChunks = activeChunks.get(key);
        return lockedChunks != null && lockedChunks.containsKey(chunkPos);
    }

    private static Set<ChunkPos> collectChunks(Collection<BlockPos> positions) {
        Set<ChunkPos> chunks = new HashSet<>();
        for (BlockPos pos : positions) {
            chunks.add(new ChunkPos(pos));
        }
        return chunks;
    }

    private static WorldKey worldKey(ServerWorld world) {
        return new WorldKey(world.getRegistryKey());
    }

    /** Test-only cleanup between cases. */
    public void resetForTests() {
        worldLocks.clear();
        activeChunks.clear();
        operationChunks.clear();
    }
}
