package com.pushdozer.items.handlers.planting;

import com.pushdozer.PushdozerTestBase;
import com.pushdozer.config.PushdozerConfig;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.noise.SimplexNoiseSampler;
import net.minecraft.util.math.random.Random;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DensitySamplerTest extends PushdozerTestBase {

    @Test
    void shouldPlantHere_isDeterministicForSameWorldSeedAndPosition() {
        PushdozerConfig config = new PushdozerConfig();
        config.setPlantType(PushdozerConfig.PlantType.GRASS);
        config.setPlantDensity(0.5f);

        SimplexNoiseSampler noise = new SimplexNoiseSampler(Random.create(1234L));
        DensitySampler samplerA = new DensitySampler(config, 98765L, noise);
        DensitySampler samplerB = new DensitySampler(config, 98765L, noise);

        BlockPos pos = new BlockPos(120, 64, -45);
        assertEquals(samplerA.shouldPlantHere(pos), samplerB.shouldPlantHere(pos));
    }

    @Test
    void shouldPlantHere_isIndependentOfTraversalOrder() {
        PushdozerConfig config = new PushdozerConfig();
        config.setPlantType(PushdozerConfig.PlantType.FLOWERS);
        config.setPlantDensity(0.7f);

        SimplexNoiseSampler noise = new SimplexNoiseSampler(Random.create(1234L));
        DensitySampler sampler = new DensitySampler(config, 42L, noise);

        BlockPos a = new BlockPos(10, 64, 10);
        BlockPos b = new BlockPos(11, 64, 10);

        boolean aFirst = sampler.shouldPlantHere(a);
        boolean bSecond = sampler.shouldPlantHere(b);

        DensitySampler samplerReverse = new DensitySampler(config, 42L, noise);
        boolean bFirst = samplerReverse.shouldPlantHere(b);
        boolean aSecond = samplerReverse.shouldPlantHere(a);

        assertEquals(aFirst, aSecond);
        assertEquals(bSecond, bFirst);
    }
}
