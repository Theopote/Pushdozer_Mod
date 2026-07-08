package com.pushdozer.test;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;

import java.util.UUID;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public final class TestFixtures {
    private TestFixtures() {
    }

    public static PlayerEntity mockPlayer(UUID id) {
        PlayerEntity player = mock(PlayerEntity.class);
        Text name = mock(Text.class);
        when(name.getString()).thenReturn("TestPlayer");
        when(player.getUuid()).thenReturn(id);
        when(player.getName()).thenReturn(name);
        return player;
    }
}
