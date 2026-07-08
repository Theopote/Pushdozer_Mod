package com.pushdozer.items.handlers.shoreline;

import com.pushdozer.config.PushdozerConfig;
import net.minecraft.block.PlantBlock;
import net.minecraft.block.TallPlantBlock;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;

public class ShorelineVegetationDensity {
    private final PushdozerConfig config;
    private final ShorelineTransitionPlanner transitionPlanner;

    public ShorelineVegetationDensity(PushdozerConfig config, ShorelineTransitionPlanner transitionPlanner) {
        this.config = config;
        this.transitionPlanner = transitionPlanner;
    }

    /**
     * 判断是否应该在此位置种植植物
     * 优化：雪地直接返回false，不种植植物
     * 逻辑优化：添加密度衰减公式，创造更自然的分布
     * 修复：添加标高限制检查
     */
    public boolean shouldPlantVegetation(World world, BlockPos pos, int distance, PlayerEntity player) {
        // 检查是否启用植物种植
        if (!config.isPlantVegetationEnabled()) return false;
        
        // 扩大植物种植范围：距离水1-4格的位置都可以种植植物
        if (distance < 1 || distance > 8) return false;
        
        // 修复：添加标高限制检查
        if (!transitionPlanner.isValidHeightForShorelineProcess(pos, player)) {
            return false;
        }
        
        // 检查上方是否有空间
        BlockPos above = pos.up();
        if (!world.getBlockState(above).isAir()) return false;
        
        // 优化：雪地直接返回false，不种植植物
        var biomeEntry = world.getBiome(pos);
        String biomeName = biomeEntry.value().toString().toLowerCase();
        if (biomeName.contains("snowy") || biomeName.contains("ice")) {
            return false; // 雪地不种植植物
        }
        
        // 基础密度
        float density = config.getVegetationDensity();
        
        // 逻辑优化：添加密度衰减公式，但保持最小密度
        float distanceFactor = Math.max(0.3f, 1.0f - (distance - 1) / (float)config.getShorelineWidth());
        float adjustedDensity = density * distanceFactor;
        
        // 根据生物群系调整密度
        if (biomeName.contains("desert")) {
            adjustedDensity *= 1.2f; // 沙漠中仙人掌较多
        } else if (biomeName.contains("swamp") || biomeName.contains("river")) {
            adjustedDensity *= 1.8f; // 沼泽和河流中植物更密集
        } else if (biomeName.contains("forest") || biomeName.contains("jungle")) {
            adjustedDensity *= 1.3f; // 森林中植物较多
        } else if (biomeName.contains("savanna") || biomeName.contains("plains")) {
            adjustedDensity *= 1.1f; // 热带草原和平原中植物较多
        } else if (biomeName.contains("mountain") || biomeName.contains("hill")) {
            adjustedDensity *= 1.4f; // 山地中植物较多（苔藓、花朵等）
        } else if (biomeName.contains("badlands")) {
            adjustedDensity *= 0.8f; // 坏地中植物较稀疏
        }
        
        // 检查邻居是否有植物，增加集群效果
        // 优化：同时检查水平和垂直方向的邻居植物
        int neighborPlants = 0;
        
        // 检查水平方向邻居
        for (Direction dir : Direction.Type.HORIZONTAL) {
            BlockPos neighborPos = pos.offset(dir);
            if (world.getBlockState(neighborPos.up()).getBlock() instanceof PlantBlock ||
                world.getBlockState(neighborPos.up()).getBlock() instanceof TallPlantBlock) {
                neighborPlants++;
            }
        }
        
        // 检查垂直方向邻居（支持垂直水岸的植物种植）
        for (Direction dir : Direction.Type.VERTICAL) {
            BlockPos neighborPos = pos.offset(dir);
            if (world.getBlockState(neighborPos.up()).getBlock() instanceof PlantBlock ||
                world.getBlockState(neighborPos.up()).getBlock() instanceof TallPlantBlock) {
                neighborPlants++;
            }
        }
        
        // 根据邻居植物数量调整密度
        if (neighborPlants >= 2) {
            adjustedDensity *= 1.5f; // 多个邻居有植物，显著增加种植概率
        } else if (neighborPlants == 1) {
            adjustedDensity *= 1.2f; // 一个邻居有植物，适度增加种植概率
        }
        
        return world.getRandom().nextFloat() < adjustedDensity;
    }
}
