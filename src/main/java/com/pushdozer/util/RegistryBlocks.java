package com.pushdozer.util;

import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

/**
 * 安全的方块注册表查找，避免为无效 ID 使用 {@code catch (Exception)}。
 */
public final class RegistryBlocks {

    private RegistryBlocks() {}

    public static Block resolve(String blockId, Block fallback) {
        Identifier identifier = Identifier.tryParse(blockId);
        if (identifier == null) {
            return fallback;
        }
        return Registries.BLOCK.getOrEmpty(identifier).orElse(fallback);
    }

    public static Block resolveOrAir(String blockId) {
        return resolve(blockId, Blocks.AIR);
    }
}
