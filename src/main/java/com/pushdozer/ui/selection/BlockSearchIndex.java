package com.pushdozer.ui.selection;

import net.minecraft.block.Block;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 方块搜索字符串缓存，供多个选择界面复用。
 */
public final class BlockSearchIndex {
    private static final Map<Block, String> CACHE = new ConcurrentHashMap<>();

    private BlockSearchIndex() {
    }

    public static void indexBlock(Block block) {
        if (block == null || CACHE.containsKey(block)) {
            return;
        }
        String blockName = Text.translatable(block.getTranslationKey()).getString().toLowerCase();
        String blockIdPath = Registries.BLOCK.getId(block).getPath().toLowerCase();
        CACHE.put(block, blockName + "|" + blockIdPath);
    }

    public static boolean matches(Block block, String query) {
        if (query == null || query.isEmpty()) {
            return true;
        }
        String searchLower = query.toLowerCase();
        String cached = CACHE.get(block);
        if (cached != null) {
            return cached.contains(searchLower);
        }
        String blockName = Text.translatable(block.getTranslationKey()).getString().toLowerCase();
        String blockId = Registries.BLOCK.getId(block).getPath().toLowerCase();
        return blockName.contains(searchLower) || blockId.contains(searchLower);
    }
}
