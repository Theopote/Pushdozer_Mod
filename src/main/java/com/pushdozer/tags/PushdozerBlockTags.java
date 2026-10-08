package com.pushdozer.tags;

import net.minecraft.block.Block;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.util.Identifier;

/**
 * Pushdozer block tags for surface convert rules.
 */
public final class PushdozerBlockTags {

    public static final TagKey<Block> SURFACE_CONVERTIBLE_SOURCE = TagKey.of(
        RegistryKeys.BLOCK, Identifier.of("pushdozer", "surface_convertible_source"));
    public static final TagKey<Block> SURFACE_CONVERT_PROTECTED = TagKey.of(
        RegistryKeys.BLOCK, Identifier.of("pushdozer", "surface_convert_protected"));

    private PushdozerBlockTags() {
    }
}
