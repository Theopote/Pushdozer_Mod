package com.pushdozer.items.handlers.shoreline;

import com.pushdozer.PushdozerTestBase;
import com.pushdozer.shapes.BoxShape;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShorelineModifyBoundsTest extends PushdozerTestBase {

    @Test
    void allowedModifyColumns_matchesBrushColumnsOnly() {
        BlockPos center = new BlockPos(10, 64, 10);
        BoxShape shape = new BoxShape(3, 3, 3, center);
        Set<BlockPos> allowed = ShorelineModifyBounds.allowedModifyColumns(shape);

        assertTrue(allowed.contains(new BlockPos(10, 0, 10)));
        assertTrue(allowed.contains(new BlockPos(11, 0, 10)));
        assertFalse(allowed.contains(new BlockPos(15, 0, 10)));
    }

    @Test
    void isColumnInModifyBounds_checksXZOnly() {
        Set<BlockPos> allowed = Set.of(new BlockPos(4, 0, 4));
        assertTrue(ShorelineModifyBounds.isColumnInModifyBounds(new BlockPos(4, 80, 4), allowed));
        assertFalse(ShorelineModifyBounds.isColumnInModifyBounds(new BlockPos(9, 64, 4), allowed));
    }
}
