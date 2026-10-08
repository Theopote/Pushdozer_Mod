package com.pushdozer.items.handlers;

import com.pushdozer.PushdozerTestBase;
import com.pushdozer.config.PushdozerConfig;
import com.pushdozer.util.OperationPermissions;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ExcavationHandlerTest extends PushdozerTestBase {

    @Test
    void handleExcavation_returnsEmptyWhenPermissionDenied() {
        ServerWorld world = Mockito.mock(ServerWorld.class);
        PushdozerConfig config = new PushdozerConfig();

        try (MockedStatic<OperationPermissions> permissions = Mockito.mockStatic(OperationPermissions.class)) {
            permissions.when(() -> OperationPermissions.checkForTerrainOperation(Mockito.any(), Mockito.eq(world), Mockito.eq(config)))
                .thenReturn(false);

            List<BlockPos> result = new ExcavationHandler().handleExcavation(null, world, config);

            assertTrue(result.isEmpty());
            permissions.verify(() -> OperationPermissions.checkForTerrainOperation(Mockito.any(), Mockito.eq(world), Mockito.eq(config)));
        }
    }
}
