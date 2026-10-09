package com.pushdozer.items.handlers.shoreline;

import com.pushdozer.shapes.GeometryShape;
import net.minecraft.util.math.BlockPos;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Separates detection range from modify range for shoreline processing.
 * Modify range = brush X/Z columns only (shoreline width controls blend distance, not modify extent).
 */
public final class ShorelineModifyBounds {

    private ShorelineModifyBounds() {
    }

    public static BlockPos columnKey(int x, int z) {
        return new BlockPos(x, 0, z);
    }

    public static BlockPos columnKey(BlockPos pos) {
        return columnKey(pos.getX(), pos.getZ());
    }

    public static Set<BlockPos> collectBrushColumns(GeometryShape shape) {
        return shape.getBlockPositions().stream()
            .map(ShorelineModifyBounds::columnKey)
            .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    public static Set<BlockPos> expandColumns(Set<BlockPos> centerColumns, int padding) {
        if (padding <= 0) {
            return new LinkedHashSet<>(centerColumns);
        }
        LinkedHashSet<BlockPos> expanded = new LinkedHashSet<>(centerColumns);
        int padSq = padding * padding;
        for (BlockPos column : centerColumns) {
            for (int dz = -padding; dz <= padding; dz++) {
                for (int dx = -padding; dx <= padding; dx++) {
                    if (dx * dx + dz * dz <= padSq) {
                        expanded.add(new BlockPos(column.getX() + dx, 0, column.getZ() + dz));
                    }
                }
            }
        }
        return expanded;
    }

    public static Set<BlockPos> allowedModifyColumns(GeometryShape shape) {
        return collectBrushColumns(shape);
    }

    public static boolean isColumnInModifyBounds(BlockPos pos, Set<BlockPos> allowedColumns) {
        return allowedColumns.contains(columnKey(pos));
    }
}
