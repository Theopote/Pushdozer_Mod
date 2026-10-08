package com.pushdozer.test;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.world.World;

import java.util.UUID;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public final class TestFixtures {
    private TestFixtures() {
    }

    public static PlayerEntity mockPlayer(UUID id) {
        return mockPlayer(id, World.OVERWORLD);
    }

    public static PlayerEntity mockPlayer(UUID id, net.minecraft.registry.RegistryKey<World> worldKey) {
        PlayerEntity player = mock(PlayerEntity.class);
        World world = mock(World.class);
        Text name = mock(Text.class);
        when(name.getString()).thenReturn("TestPlayer");
        when(player.getUuid()).thenReturn(id);
        when(player.getName()).thenReturn(name);
        when(player.getEntityWorld()).thenReturn(world);
        when(world.getRegistryKey()).thenReturn(worldKey);
        return player;
    }
}
