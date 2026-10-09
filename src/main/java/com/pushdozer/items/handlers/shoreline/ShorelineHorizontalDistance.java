package com.pushdozer.items.handlers.shoreline;

import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

/**
 * Computes horizontal (XZ) distance from land columns to the nearest water column.
 */
public final class ShorelineHorizontalDistance {

    private static final int MAX_COLUMNS = 10000;

    private ShorelineHorizontalDistance() {
    }

    public static Set<BlockPos> projectWaterColumns(Set<BlockPos> waterBlocks) {
        Set<BlockPos> columns = new HashSet<>();
        for (BlockPos water : waterBlocks) {
            columns.add(ShorelineModifyBounds.columnKey(water));
        }
        return columns;
    }

    /**
     * @return map of land column (y=0) to horizontal distance from water (1 = adjacent)
     */
    public static Map<BlockPos, Integer> compute(Set<BlockPos> waterBlocks, int maxDistance) {
        Set<BlockPos> waterColumns = projectWaterColumns(waterBlocks);
        Map<BlockPos, Integer> distances = new HashMap<>();
        Queue<BlockPos> queue = new LinkedList<>();

        for (BlockPos waterColumn : waterColumns) {
            for (Direction dir : Direction.Type.HORIZONTAL) {
                BlockPos landColumn = waterColumn.offset(dir);
                if (waterColumns.contains(landColumn) || distances.containsKey(landColumn)) {
                    continue;
                }
                distances.put(landColumn, 1);
                queue.add(landColumn);
            }
        }

        while (!queue.isEmpty() && distances.size() < MAX_COLUMNS) {
            BlockPos current = queue.poll();
            int currentDistance = distances.get(current);
            if (currentDistance >= maxDistance) {
                continue;
            }

            for (Direction dir : Direction.Type.HORIZONTAL) {
                BlockPos neighbor = current.offset(dir);
                if (waterColumns.contains(neighbor) || distances.containsKey(neighbor)) {
                    continue;
                }
                distances.put(neighbor, currentDistance + 1);
                queue.add(neighbor);
            }
        }

        return distances;
    }
}
