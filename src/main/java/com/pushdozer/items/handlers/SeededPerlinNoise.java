package com.pushdozer.items.handlers;

import java.util.Random;

/**
 * Deterministic 2D Perlin noise from a full 64-bit seed and world-space coordinates.
 */
final class SeededPerlinNoise {

    private final int[] permutation;

    SeededPerlinNoise(long seed) {
        this.permutation = buildPermutation(seed);
    }

    float sample(double worldX, double worldZ, float frequency, float persistence, int octaves) {
        float noise = 0.0f;
        float amplitude = 1.0f;
        float freq = frequency;
        float maxAmplitude = 0.0f;

        for (int i = 0; i < octaves; i++) {
            noise += amplitude * perlinNoise(worldX * freq, worldZ * freq);
            maxAmplitude += amplitude;
            amplitude *= persistence;
            freq *= 2.0;
        }

        return maxAmplitude > 0.0f ? noise / maxAmplitude : 0.0f;
    }

    private static int[] buildPermutation(long seed) {
        int[] source = new int[256];
        for (int i = 0; i < 256; i++) {
            source[i] = i;
        }

        Random random = new Random(seed);
        for (int i = 255; i > 0; i--) {
            int swapIndex = random.nextInt(i + 1);
            int temp = source[i];
            source[i] = source[swapIndex];
            source[swapIndex] = temp;
        }

        int[] perm = new int[512];
        for (int i = 0; i < 512; i++) {
            perm[i] = source[i & 255];
        }
        return perm;
    }

    private float perlinNoise(double x, double z) {
        int xi = floorToIndex(x);
        int zi = floorToIndex(z);
        double xf = x - Math.floor(x);
        double zf = z - Math.floor(z);

        double u = fade(xf);
        double w = fade(zf);

        int a = permutation[xi] + zi;
        int aa = permutation[a & 255];
        int ab = permutation[(a + 1) & 255];
        int b = permutation[(xi + 1) & 255] + zi;
        int ba = permutation[b & 255];
        int bb = permutation[(b + 1) & 255];

        double x1 = lerp(grad(aa, xf, zf), grad(ba, xf - 1.0, zf), u);
        double x2 = lerp(grad(ab, xf, zf - 1.0), grad(bb, xf - 1.0, zf - 1.0), u);
        return (float) lerp(x1, x2, w);
    }

    private static int floorToIndex(double coordinate) {
        int floored = (int) Math.floor(coordinate);
        return floored & 255;
    }

    private static double fade(double t) {
        return t * t * t * (t * (t * 6.0 - 15.0) + 10.0);
    }

    private static double lerp(double a, double b, double t) {
        return a + t * (b - a);
    }

    private static double grad(int hash, double x, double z) {
        int h = hash & 15;
        double u = h < 8 ? x : z;
        double v = h < 4 ? z : (h == 12 || h == 14 ? x : z);
        return ((h & 1) == 0 ? u : -u) + ((h & 2) == 0 ? v : -v);
    }
}
