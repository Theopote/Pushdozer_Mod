package com.pushdozer.items.handlers.shoreline.profile;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.registry.tag.BlockTags;

public enum ShorelineGroundType {
    SAND,
    DIRT,
    SANDSTONE,
    STONE,
    MUD,
    ANY;

    public boolean matches(BlockState ground) {
        return switch (this) {
            case SAND -> ground.isIn(BlockTags.SAND);
            case DIRT -> ground.isIn(BlockTags.DIRT) || ground.isOf(Blocks.GRASS_BLOCK) || ground.isOf(Blocks.MUD);
            case SANDSTONE -> ground.isOf(Blocks.SANDSTONE)
                || ground.isOf(Blocks.RED_SANDSTONE)
                || ground.isOf(Blocks.SMOOTH_SANDSTONE);
            case STONE -> ground.isOf(Blocks.STONE)
                || ground.isOf(Blocks.COBBLESTONE)
                || ground.isOf(Blocks.GRAVEL)
                || ground.isOf(Blocks.ANDESITE)
                || ground.isOf(Blocks.DIORITE)
                || ground.isOf(Blocks.GRANITE)
                || ground.isOf(Blocks.DEEPSLATE);
            case MUD -> ground.isOf(Blocks.MUD);
            case ANY -> true;
        };
    }

    public static ShorelineGroundType classify(BlockState ground) {
        if (MUD.matches(ground)) {
            return MUD;
        }
        for (ShorelineGroundType type : values()) {
            if (type != ANY && type != MUD && type.matches(ground)) {
                return type;
            }
        }
        return ANY;
    }
}
