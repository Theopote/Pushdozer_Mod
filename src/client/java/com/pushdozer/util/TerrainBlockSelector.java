package com.pushdozer.util;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.biome.Biome;
import net.minecraft.registry.RegistryKeys;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class TerrainBlockSelector {
    private static final Logger LOGGER = LogManager.getLogger(TerrainBlockSelector.class);
    private static final Map<String, Block> BIOME_BLOCK_MAP;

    static {
        Map<String, Block> map = new HashMap<>();
        map.put("minecraft:ocean", Blocks.WATER);
        map.put("minecraft:deep_ocean", Blocks.WATER);
        map.put("minecraft:frozen_ocean", Blocks.WATER);
        map.put("minecraft:deep_frozen_ocean", Blocks.WATER);
        map.put("minecraft:plains", Blocks.GRASS_BLOCK);
        map.put("minecraft:sunflower_plains", Blocks.GRASS_BLOCK);
        map.put("minecraft:desert", Blocks.SAND);
        map.put("minecraft:desert_hills", Blocks.SAND);
        map.put("minecraft:mountains", Blocks.STONE);
        map.put("minecraft:mountain_edge", Blocks.STONE);
        map.put("minecraft:wooded_mountains", Blocks.STONE);
        map.put("minecraft:forest", Blocks.DIRT);
        map.put("minecraft:wooded_hills", Blocks.DIRT);
        map.put("minecraft:birch_forest", Blocks.DIRT);
        map.put("minecraft:birch_forest_hills", Blocks.DIRT);
        map.put("minecraft:taiga", Blocks.PODZOL);
        map.put("minecraft:taiga_hills", Blocks.PODZOL);
        map.put("minecraft:snowy_taiga", Blocks.PODZOL);
        map.put("minecraft:snowy_taiga_hills", Blocks.PODZOL);
        map.put("minecraft:swamp", Blocks.MUD);
        map.put("minecraft:swamp_hills", Blocks.MUD);
        map.put("minecraft:savanna", Blocks.GRASS_BLOCK);
        map.put("minecraft:savanna_plateau", Blocks.GRASS_BLOCK);
        map.put("minecraft:badlands", Blocks.RED_SAND);
        map.put("minecraft:wooded_badlands_plateau", Blocks.RED_SAND);
        map.put("minecraft:badlands_plateau", Blocks.RED_SAND);
        map.put("minecraft:beach", Blocks.SAND);
        map.put("minecraft:stone_shore", Blocks.STONE);
        map.put("minecraft:snowy_tundra", Blocks.SNOW_BLOCK);
        map.put("minecraft:snowy_mountains", Blocks.SNOW_BLOCK);
        map.put("minecraft:mushroom_fields", Blocks.MYCELIUM);
        map.put("minecraft:mushroom_field_shore", Blocks.MYCELIUM);
        map.put("minecraft:nether_wastes", Blocks.NETHERRACK);
        map.put("minecraft:the_end", Blocks.END_STONE);
        BIOME_BLOCK_MAP = Collections.unmodifiableMap(map);
    }

    public static Block getNaturalTerrainBlock(BlockPos pos, World world) {
        // 获取当前位置下方的方块
        BlockPos groundPos = findGroundPosition(pos, world);
        BlockState groundState = world.getBlockState(groundPos);
        Block groundBlock = groundState.getBlock();

        // 如果地面是水，我们使用生物群系的默认方块
        if (groundBlock == Blocks.WATER) {
            return getBiomeDefaultBlock(pos, world);
        }

        // 对于一些特殊的方块，我们可能想要使用不同的填充方块
        if (groundBlock == Blocks.GRASS_BLOCK || groundBlock == Blocks.DIRT || groundBlock == Blocks.COARSE_DIRT) {
            // 检查是否是最上层的方块
            if (world.getBlockState(pos.up()).isAir()) {
                return Blocks.GRASS_BLOCK;
            }
            return Blocks.DIRT;
        }
        if (groundBlock == Blocks.SAND || groundBlock == Blocks.RED_SAND) {
            return groundBlock; // 保持沙子的颜色
        }
        if (groundBlock == Blocks.STONE || groundBlock == Blocks.GRANITE || groundBlock == Blocks.DIORITE || groundBlock == Blocks.ANDESITE) {
            return Blocks.STONE;
        }
        if (groundBlock == Blocks.NETHERRACK || groundBlock == Blocks.CRIMSON_NYLIUM || groundBlock == Blocks.WARPED_NYLIUM) {
            return Blocks.NETHERRACK;
        }

        // 对于其他方块，我们直接使用地面的方块
        return groundBlock;
    }

    private static BlockPos findGroundPosition(BlockPos startPos, World world) {
        BlockPos.Mutable mutablePos = new BlockPos.Mutable(startPos.getX(), startPos.getY(), startPos.getZ());
        while (mutablePos.getY() > 0) {
            mutablePos.move(0, -1, 0);
            BlockState state = world.getBlockState(mutablePos);
            if (!state.isAir() && !state.getFluidState().isStill()) {
                return mutablePos.toImmutable();
            }
        }
        return startPos; // 如果没有找到地面，返回原始位置
    }

    private static Block getBiomeDefaultBlock(BlockPos pos, World world) {
        Biome biome = world.getBiome(pos).value();
        String biomeName = world.getRegistryManager().get(RegistryKeys.BIOME).getId(biome).toString();
        Block block = BIOME_BLOCK_MAP.get(biomeName);
        if (block == null) {
            LOGGER.warn("未知的生物群系: " + biomeName + "，使用默认方块 Blocks.STONE");
            return Blocks.STONE;
        }
        return block;
    }
}