package com.pushdozer.services;

import com.pushdozer.PushdozerTestBase;
import com.pushdozer.config.PushdozerConfig;
import com.pushdozer.network.ConfigSyncPayload;
import com.pushdozer.test.TestFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;

class ConfigServiceTest extends PushdozerTestBase {

    private ConfigService configService;

    @BeforeEach
    void setUp() {
        configService = ConfigService.getInstance();
    }

    @Test
    void getConfig_returnsIndependentDefaultsPerPlayer() {
        UUID playerA = UUID.randomUUID();
        UUID playerB = UUID.randomUUID();

        PushdozerConfig configA = configService.getConfig(TestFixtures.mockPlayer(playerA));
        PushdozerConfig configB = configService.getConfig(TestFixtures.mockPlayer(playerB));

        assertNotSame(configA, configB);
        configA.setRadius(20);
        assertEquals(20, configA.getRadius());
        assertEquals(5, configB.getRadius());
    }

    @Test
    void applySync_overwritesPlayerConfigAndClampsValues() {
        UUID playerId = UUID.randomUUID();
        ConfigSyncPayload payload = new ConfigSyncPayload("{\"radius\":99}");

        configService.applySync(TestFixtures.mockPlayer(playerId), payload);

        assertEquals(
            PushdozerConfig.MAX_BRUSH_RADIUS,
            configService.getConfig(TestFixtures.mockPlayer(playerId)).getRadius()
        );
    }

    @Test
    void removePlayer_dropsCachedConfig() {
        UUID playerId = UUID.randomUUID();
        PushdozerConfig before = configService.getConfig(TestFixtures.mockPlayer(playerId));
        before.setRadius(11);

        configService.removePlayer(playerId);
        PushdozerConfig after = configService.getConfig(TestFixtures.mockPlayer(playerId));

        assertNotSame(before, after);
        assertEquals(5, after.getRadius());
    }
}
