package com.pushdozer.items.handlers.shoreline;

import com.pushdozer.PushdozerMod;
import com.pushdozer.config.PushdozerConfig;
import com.pushdozer.items.handlers.shoreline.model.VegetationPlacement;
import com.pushdozer.items.handlers.vegetation.PlantPlacementValidator;
import net.minecraft.block.BlockState;
import net.minecraft.block.TallPlantBlock;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.List;

public class ShorelineVegetationPlanner {
    private final PushdozerConfig config;
    private final ShorelineEdgeFinder edgeFinder;
    private final ShorelineTransitionPlanner transitionPlanner;
    private final ShorelinePlantCompatibility compatibility;
    private final ShorelineVegetationDensity density;
    private final ShorelineVegetationSelector selector;
    private final ShorelineVegetationExecutor executor;

    public ShorelineVegetationPlanner(PushdozerConfig config, ShorelineEdgeFinder edgeFinder, ShorelineTransitionPlanner transitionPlanner) {
        this.config = config;
        this.edgeFinder = edgeFinder;
        this.transitionPlanner = transitionPlanner;
        this.compatibility = new ShorelinePlantCompatibility(config);
        this.density = new ShorelineVegetationDensity(config, transitionPlanner);
        this.selector = new ShorelineVegetationSelector(config, compatibility);
        this.executor = new ShorelineVegetationExecutor(config, edgeFinder, transitionPlanner, compatibility);
    }

    public List<VegetationPlacement> collectVegetationPositions(World world, List<BlockPos> positions) {
        List<VegetationPlacement> placements = new ArrayList<>();
        
        PushdozerMod.LOGGER.debug("collectVegetationPositions: processing {} positions, plantVegetationEnabled = {}", 
            positions.size(), config.isPlantVegetationEnabled());
        
        for (BlockPos pos : positions) {
            // 检查是否启用植物种植
            if (!config.isPlantVegetationEnabled()) {
                PushdozerMod.LOGGER.debug("Plant vegetation is disabled, skipping position {}", pos);
                continue;
            }
            
            // 检查下方方块是否为实体方块（不能是水方块）
            BlockState belowState = world.getBlockState(pos);
            if (!PlantPlacementValidator.isSolidBlock(world, pos, belowState)) {
                PushdozerMod.LOGGER.debug("Position {} has non-solid block below: {}, skipping", pos, belowState.getBlock().toString());
                continue;
            }
            
            BlockState plant = selector.getVegetationForBiome(world, pos, world.getBiome(pos).value(), belowState);
            PushdozerMod.LOGGER.debug("getVegetationForBiome returned: {} for position {}", 
                plant != null ? plant.getBlock().toString() : "null", pos);
            
            if (plant != null && compatibility.canPlantGrowOnBlock(world, pos, plant.getBlock(), belowState)) {
                // 优化：检查高植物种植条件
                int requiredHeight = compatibility.getPlantRequiredHeight(world, plant.getBlock());
                if (PlantPlacementValidator.hasEnoughSpaceForPlant(world, pos, requiredHeight)) {
                    boolean isTallPlant = plant.getBlock() instanceof TallPlantBlock;
                    placements.add(new VegetationPlacement(pos, plant, isTallPlant, belowState));
                    // 调试：记录植物选择
                    PushdozerMod.LOGGER.debug("Successfully added plant {} for position {} on ground {}", 
                        plant.getBlock().toString(), pos, belowState.getBlock().toString());
                } else {
                    PushdozerMod.LOGGER.debug("Not enough space for plant {} at position {}", plant.getBlock().toString(), pos);
                }
            } else if (plant != null) {
                PushdozerMod.LOGGER.debug("Plant {} cannot grow on block {} at position {}", 
                    plant.getBlock().toString(), belowState.getBlock().toString(), pos);
            }
        }
        
        PushdozerMod.LOGGER.debug("collectVegetationPositions: collected {} placements", placements.size());
        return placements;
    }

    public boolean shouldPlantVegetation(World world, BlockPos pos, int distance, PlayerEntity player) {
        return density.shouldPlantVegetation(world, pos, distance, player);
    }

    public int planVegetation(World world, List<VegetationPlacement> placements,
                               List<BlockPos> affectedPositions, List<BlockState> originalStates, List<BlockState> newStates,
                               PlayerEntity player) {
        return executor.planVegetation(world, placements, affectedPositions, originalStates, newStates, player);
    }
}
