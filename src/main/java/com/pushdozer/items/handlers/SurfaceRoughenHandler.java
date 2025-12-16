package com.pushdozer.items.handlers;

import com.pushdozer.config.PushdozerConfig;
import com.pushdozer.operations.UndoAction;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import java.util.*;

/**
 * 表面粗糙模式处理器 (重构版)
 * 核心算法: 平滑基准 + 受控随机噪声 (Smoothed Base + Controlled Random Noise)
 * <p>
 * 优化点:
 * 1.  【算法重构】使用平滑后的高度作为基准，解决了地形"只增不减"和效果混乱的问题。
 * 2.  【性能修复】修复了 noiseCache 导致的内存泄漏问题。
 * 3.  【代码质量】重命名了易混淆的参数，提升了可读性。
 */
public class SurfaceRoughenHandler extends AbstractTerrainToolHandler {

    // 柏林噪声的置换表
    private static final int[] PERMUTATION = {
        151,160,137,91,90,15,131,13,201,95,96,53,194,233,7,225,140,36,103,30,69,142,8,99,37,240,21,10,23,
        190,6,148,247,120,234,75,0,26,197,62,94,252,219,203,117,35,11,32,57,177,33,88,237,149,56,87,174,20,125,136,171,168,
        68,175,74,165,71,134,139,48,27,166,77,146,158,231,83,111,229,122,60,211,133,230,220,105,92,41,55,46,245,40,244,
        102,143,54,65,25,63,161,1,216,80,73,209,76,132,187,208,89,18,169,200,196,135,130,116,188,159,86,164,100,109,198,173,186,3,64,52,217,226,250,124,123,
        5,202,38,147,118,126,255,82,85,212,207,206,59,227,47,16,58,17,182,189,28,42,223,183,170,213,119,248,152,2,44,154,163,70,221,153,101,155,167,43,172,9,
        129,22,39,253,19,98,108,110,79,113,224,232,178,185,112,104,218,246,97,228,251,34,242,193,238,210,144,12,191,179,162,241,81,51,145,235,249,14,239,107,
        49,192,214,31,181,199,106,157,184,84,204,176,115,121,50,45,127,4,150,254,138,236,205,93,222,114,67,29,24,72,243,141,128,195,78,66,215,61,156,180
    };

    // 噪声参数配置 (默认值，当自动缩放开启时使用)
    private static final float DEFAULT_NOISE_FREQUENCY = 0.02f;
    private static final float DEFAULT_NOISE_PERSISTENCE = 0.5f;
    private static final float MAX_ROUGHNESS_AMPLITUDE = 5.0f; // 最大粗糙度振幅
    
    // 种子参数（可配置）
    private final long noiseSeed;
    // 可选：缓存局部区域的噪声结果以降低重复计算成本
    private final Map<Long, Float> noiseCache = new HashMap<>();

    public SurfaceRoughenHandler(PushdozerConfig config) {
        super(config);
        // 使用配置中的种子或默认种子
        this.noiseSeed = config.getNoiseSeed();
    }

    /**
     * 处理表面粗糙操作
     * 【优化】在每次操作前清空噪声缓存，防止内存泄漏。
     */
    public void handleSurfaceRoughen(PlayerEntity player, World world) {
        // Clear cache before each new operation to prevent memory leak
        this.noiseCache.clear(); 
        handleOperation(player, world, UndoAction.ActionType.SURFACE_ROUGHEN);
    }

    /**
     * 【核心算法重构】计算表面粗糙的目标高度
     * 新算法: 新高度 = 区域平滑高度 + (噪声值 * 强度 * 衰减)
     *
     * @param currentColumn 【代码质量】重命名参数，避免混淆 (原名 centerColumn)
     */
        @Override
    protected int calculateTargetHeight(Map<BlockPos, TerrainColumn> columns,
                                     TerrainColumn currentColumn, // <-- Renamed for clarity
                                     BlockPos columnXZ,
                                     BlockPos brushCenter) {
        int brushRadius = config.getRadius();

        // 1. 【新算法核心】计算区域的平滑基准高度，而不是使用当前点的原始高度。
        //    这使得工具的效果是"重塑"表面，而不是在现有表面上"抖动"。
        float originalHeight = currentColumn.getOriginalHeight();
        float smoothedHeight = calculateSmoothedHeight(columns, columnXZ, brushCenter, brushRadius);
        if (smoothedHeight <= 0) { // 如果没有有效的邻居，则退回到使用原始高度
            smoothedHeight = originalHeight;
        }

        // 2. 【新增】根据平滑强度参数，在原始高度和平滑高度之间进行混合
        //    0.0 = 完全保留原始地形形状，1.0 = 完全重塑为平滑基准
        float smoothingIntensity = config.getSmoothingIntensity();
        float baseHeight = originalHeight * (1.0f - smoothingIntensity) + smoothedHeight * smoothingIntensity;

        // 3. 生成柏林噪声值 (范围: -1.0 to 1.0)
        float noiseValue = generateSeededPerlinNoise(columnXZ.getX(), columnXZ.getZ());

        // 4. 计算粗糙强度，与UI中的strength滑块关联
        float roughnessStrength = config.getRoughnessStrength();
        float roughnessAmount = Math.min(roughnessStrength * 3.0f, MAX_ROUGHNESS_AMPLITUDE);
        
        // 【优化】根据方块类型调整粗糙度振幅
        BlockState mainBlock = currentColumn.getMainBlockState();
        if (mainBlock.isOf(Blocks.SAND)) {
            roughnessAmount *= 0.7f; // 沙子更柔和，避免沙丘
        } else if (mainBlock.isOf(Blocks.STONE) || mainBlock.isOf(Blocks.DEEPSLATE)) {
            roughnessAmount *= 1.2f; // 石头更粗糙
        } else if (mainBlock.isOf(Blocks.GRASS_BLOCK)) {
            roughnessAmount *= 0.9f; // 草地稍微柔和
        }

        // 5. 计算最终高度偏移量
        float heightOffset = noiseValue * roughnessAmount;

        // 6. 应用边缘衰减，使笔刷边缘平滑过渡
        float falloff = calculateEdgeFalloff(columnXZ, brushCenter, brushRadius);

        // 7. 最终高度公式：在混合基准上应用带衰减的噪声
        float targetHeight = baseHeight + (heightOffset * falloff);

        // 8. 使用改进的高度插值，而非简单的 Math.round()，可以使结果更平滑
        //    这里我们暂时还用 Math.round()，因为平滑基准已经改善了绝大部分问题。
        return Math.round(targetHeight);
    }

    /**
     * 生成带种子的柏林噪声值
     * 使用种子参数确保可重现的噪声
     * 改进版本：使用严谨的归一化方法
     */
    private float generateSeededPerlinNoise(int x, int z) {
        long key = (((long) x) << 32) | (z & 0xFFFFFFFFL);
        Float cached = noiseCache.get(key);
        if (cached != null) return cached;

        // 使用多层柏林噪声以获得更自然的效果
        float noise = 0;
        float amplitude = 1.0f;
        float frequency = getNoiseFrequency();
        float persistence = getNoisePersistence();
        int octaves = getNoiseOctaves();
        float maxAmplitude = 0.0f; // 用于累加最大振幅

        for (int i = 0; i < octaves; i++) {
            // 使用种子偏移坐标
            int seededX = x + (int)(noiseSeed & 0xFFFF);
            int seededZ = z + (int)((noiseSeed >> 16) & 0xFFFF);

            noise += amplitude * perlinNoise(seededX * frequency, seededZ * frequency);
            maxAmplitude += amplitude; // 累加当前八度的振幅
            amplitude *= persistence;
            frequency *= 2.0f;
        }

        float result = (maxAmplitude > 0) ? (noise / maxAmplitude) : 0f;
        noiseCache.put(key, result);
        return result;
    }

    /**
     * 获取噪声频率参数
     */
    private float getNoiseFrequency() {
        if (!config.isNoiseAutoScale()) {
            return config.getNoiseFrequency();
        }
        // 【优化】更激进地基于半径缩放：半径=1时freq=0.1以精细细节；半径=20时freq=0.01以丘陵
        int r = Math.max(1, config.getRadius());
        float scale = (float) Math.max(0.5, Math.min(4.0, 12.0 / r)); // 对小r更紧凑
        float freq = DEFAULT_NOISE_FREQUENCY * scale;
        return Math.max(0.01f, Math.min(0.15f, freq)); // 上限提高到0.15
    }

    /**
     * 获取噪声持久性参数
     */
    private float getNoisePersistence() {
        if (!config.isNoiseAutoScale()) {
            return config.getNoisePersistence();
        }
        return DEFAULT_NOISE_PERSISTENCE;
    }

    /**
     * 获取噪声八度数参数
     */
    private int getNoiseOctaves() {
        if (!config.isNoiseAutoScale()) {
            return config.getNoiseOctaves();
        }
        // 大半径时增加八度以带来多尺度起伏，小半径时减少以避免过度噪点
        int r = Math.max(1, config.getRadius());
        // r<=6:3阶; r≈14:4阶; r>=22:5阶
        return 3 + Math.min(2, Math.max(0, (r - 6) / 8));
    }

    /**
     * 单层柏林噪声实现
     * 基于经典的柏林噪声算法
     */
    private float perlinNoise(float x, float z) {
        // 确定包含点的单位立方体的整数部分
        int xi = (int) Math.floor(x) & 255;
        int zi = (int) Math.floor(z) & 255;
        
        // 计算小数部分
        float xf = x - (float) Math.floor(x);
        float zf = z - (float) Math.floor(z);
        
        // 计算衰减函数
        float u = fade(xf);
        float w = fade(zf);
        
        // 计算四个角的哈希值
        int A = PERMUTATION[xi] + zi;
        int AA = PERMUTATION[A & 255];
        int AB = PERMUTATION[(A + 1) & 255];
        int B = PERMUTATION[(xi + 1) & 255] + zi;
        int BA = PERMUTATION[B & 255];
        int BB = PERMUTATION[(B + 1) & 255];
        
        // 计算四个角的梯度贡献
        float g00 = grad(AA, xf, zf);
        float g10 = grad(BA, xf - 1, zf);
        float g01 = grad(AB, xf, zf - 1);
        float g11 = grad(BB, xf - 1, zf - 1);
        
        // 双线性插值
        float x1 = lerp(g00, g10, u);
        float x2 = lerp(g01, g11, u);
        
        return lerp(x1, x2, w);
    }

    /**
     * 线性插值
     */
    private float lerp(float a, float b, float t) {
        return a + t * (b - a);
    }

    /**
     * 衰减函数
     */
    private float fade(float t) {
        return t * t * t * (t * (t * 6 - 15) + 10);
    }

    /**
     * 梯度函数
     */
    private float grad(int hash, float x, float z) {
        // 将哈希值转换为梯度
        int h = hash & 15;
        float u = h < 8 ? x : z;
        float v = h < 4 ? z : h == 12 || h == 14 ? x : z;
        return ((h & 1) == 0 ? u : -u) + ((h & 2) == 0 ? v : -v);
    }

    /**
     * 计算边缘衰减（优化版本）
     * 【优化】使用全笔刷高斯衰减而非仅外20%，创建更柔和过渡，更适合自然地形
     */
    private float calculateEdgeFalloff(BlockPos columnXZ, BlockPos brushCenter, int brushRadius) {
        // 计算到笔刷中心的2D距离平方
        BlockPos brushCenterXZ = new BlockPos(brushCenter.getX(), 0, brushCenter.getZ());
        double distanceSq = columnXZ.getSquaredDistance(brushCenterXZ);
        
        // 使用高斯衰减，sigma = 笔刷半径 * 0.4f
        float sigma = brushRadius * 0.4f;
        return (float) Math.exp(-distanceSq / (2.0 * sigma * sigma));
    }
} 