package com.pushdozer.items.handlers.shoreline.profile;

import com.pushdozer.tags.PushdozerBiomeTags;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.tag.BiomeTags;
import net.minecraft.world.biome.Biome;

import java.util.function.Predicate;

public enum ShorelineBiomeGroup {
    OCEAN_OR_BEACH(entry -> entry.isIn(BiomeTags.IS_OCEAN) || entry.isIn(BiomeTags.IS_BEACH)),
    DESERT(entry -> entry.isIn(PushdozerBiomeTags.HAS_DESERT_SHORES) || entry.isIn(BiomeTags.IS_BADLANDS)),
    BADLANDS(entry -> entry.isIn(BiomeTags.IS_BADLANDS)),
    RIVER_OR_SWAMP(entry -> entry.isIn(BiomeTags.IS_RIVER)
        || entry.isIn(PushdozerBiomeTags.HAS_RIVER_SHORES)
        || entry.isIn(PushdozerBiomeTags.HAS_SWAMP_SHORES)),
    JUNGLE(entry -> entry.isIn(BiomeTags.IS_JUNGLE)),
    FOREST(entry -> entry.isIn(BiomeTags.IS_FOREST)),
    JUNGLE_OR_FOREST(entry -> entry.isIn(BiomeTags.IS_JUNGLE) || entry.isIn(BiomeTags.IS_FOREST)),
    SAVANNA_OR_PLAINS(entry -> entry.isIn(BiomeTags.IS_SAVANNA) || entry.isIn(PushdozerBiomeTags.HAS_PLAINS_GRASS)),
    SNOWY(entry -> entry.isIn(PushdozerBiomeTags.HAS_SNOWY_SHORES)),
    MOUNTAIN_OR_HILL(entry -> entry.isIn(BiomeTags.IS_MOUNTAIN)),
    TAIGA_OR_COLD(entry -> entry.isIn(BiomeTags.IS_TAIGA)),
    NETHER(entry -> entry.isIn(BiomeTags.IS_NETHER)),
    END(entry -> entry.isIn(BiomeTags.IS_END)),
    ANY(entry -> true);

    private final Predicate<RegistryEntry<Biome>> matcher;

    ShorelineBiomeGroup(Predicate<RegistryEntry<Biome>> matcher) {
        this.matcher = matcher;
    }

    public boolean matches(RegistryEntry<Biome> biome) {
        return matcher.test(biome);
    }
}
