package com.pushdozer.operations;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.world.World;

public interface BlockOperation {
    void execute(PlayerEntity player, World world);
}