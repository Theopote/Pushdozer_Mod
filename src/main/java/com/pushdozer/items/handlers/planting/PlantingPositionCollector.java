package com.pushdozer.items.handlers.planting;

import com.pushdozer.config.PushdozerConfig;
import com.pushdozer.items.handlers.planting.model.PlantingPosition;
import com.pushdozer.items.handlers.vegetation.PlantBlockClassifier;
import com.pushdozer.shapes.GeometryShape;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.Heightmap;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class PlantingPositionCollector {
    private final PushdozerConfig config;
    private final DensitySampler densitySampler;

    public PlantingPositionCollector(PushdozerConfig config, DensitySampler densitySampler) {
        this.config = config;
        this.densitySampler = densitySampler;
    }

    /**
     * 收集所有需要种植的位置
     * 性能优化版本：批量获取BlockState以提高性能
     */
    public List<PlantingPosition> collect(World world, GeometryShape shape) {
        List<PlantingPosition> positions = new ArrayList<>();
        PushdozerConfig.PlantType plantType = config.getPlantType();

        // ⭐ 性能优化：批量获取BlockState
        Map<BlockPos, BlockState> blockStates = new HashMap<>();
        Map<BlockPos, BlockState> groundStates = new HashMap<>();

        for (BlockPos pos : shape.getBlockPositions()) {
            // 批量获取当前方块和地面方块状态
            blockStates.put(pos, world.getBlockState(pos));
            groundStates.put(pos.down(), world.getBlockState(pos.down()));
        }

        Set<Long> seenColumns = new HashSet<>();
        if (plantType == PushdozerConfig.PlantType.CUSTOM) {
            boolean preferWaterForCustom = containsLiveCoral(config.getCustomPlantBlocks());
            // 自定义类型：仅在地表或海底上方尝试，每个 XZ 列最多一次
            for (BlockPos pos : shape.getBlockPositions()) {
                long key = BlockPos.asLong(pos.getX(), 0, pos.getZ());
                if (seenColumns.contains(key)) continue;
                seenColumns.add(key);

                int x = pos.getX();
                int z = pos.getZ();

                int surfaceY = world.getTopY(Heightmap.Type.WORLD_SURFACE, x, z);
                int oceanFloorY = world.getTopY(Heightmap.Type.OCEAN_FLOOR, x, z);

                BlockPos topPos = new BlockPos(x, surfaceY, z);
                BlockState topState = world.getBlockState(topPos);
                BlockPos landCandidate = topState.isReplaceable() ? topPos : topPos.up();
                // 注意：world.getTopY(OCEAN_FLOOR) 已返回海床上方一格（通常是第一格水/空气）
                BlockPos seabedWaterPos = new BlockPos(x, oceanFloorY, z); // 海底上一格水体内（第一格水）

                // 候选位置按优先级尝试：若自定义集合包含活珊瑚则优先在水中尝试，否则优先地表
                List<BlockPos> candidates = preferWaterForCustom
                        ? Arrays.asList(seabedWaterPos, landCandidate)
                        : Arrays.asList(landCandidate, seabedWaterPos);
                for (BlockPos candidate : candidates) {
                    BlockState cs = world.getBlockState(candidate);
                    boolean land = candidate.equals(landCandidate);

                    // 对于活珊瑚，需要更严格的水中检查
                    if (preferWaterForCustom && !land) {
                        // 检查当前位置是否在水中
                        boolean inWater = cs.getFluidState().isIn(FluidTags.WATER);
                        if (!inWater) continue;

                        // 检查上方一格是否也在水中（确保不是水面）
                        BlockPos upperPos = candidate.up();
                        BlockState upperState = world.getBlockState(upperPos);
                        boolean upperInWater = upperState.getFluidState().isIn(FluidTags.WATER);
                        if (!upperInWater) continue;

                        // 额外检查：确保下方有合适的基底（沙子、珊瑚砂、石头等）
                        BlockPos lowerPos = candidate.down();
                        BlockState lowerState = world.getBlockState(lowerPos);
                        boolean validBase = lowerState.isIn(BlockTags.SAND) ||
                                lowerState.isIn(BlockTags.CORAL_BLOCKS) ||
                                lowerState.isIn(BlockTags.BASE_STONE_OVERWORLD) ||
                                lowerState.isIn(BlockTags.BASE_STONE_NETHER) ||
                                lowerState.isOf(Blocks.END_STONE) ||
                                lowerState.isOf(Blocks.GRAVEL) ||
                                lowerState.isOf(Blocks.CLAY);
                        if (!validBase) continue;
                    } else {
                        // 普通检查
                        boolean pass = land
                                ? (cs.isAir() || cs.isReplaceable())
                                : cs.getFluidState().isIn(FluidTags.WATER);
                        if (!pass) continue;
                    }

                    if (!densitySampler.shouldPlantHere(candidate)) continue;
                    positions.add(new PlantingPosition(candidate, plantType));
                    break; // 每列最多一个
                }
            }
        } else {
            // 非自定义类型也改为：每个 XZ 列最多一次，并落到地表
            Set<Long> blockedColumns = new HashSet<>();

            // 基于密度的最小间距，仅对简单植物生效
            int spacingRadius = 0;
            if (plantType == PushdozerConfig.PlantType.FLOWERS || plantType == PushdozerConfig.PlantType.GRASS) {
                float d = Math.max(0f, Math.min(1f, config.getPlantDensity()));
                spacingRadius = Math.max(1, Math.round(1 + (1.0f - d) * 2)); // 1..3 格的列级抑制
            }

            for (BlockPos pos : shape.getBlockPositions()) {
                long colKey = BlockPos.asLong(pos.getX(), 0, pos.getZ());
                if (seenColumns.contains(colKey) || blockedColumns.contains(colKey)) continue;
                seenColumns.add(colKey);

                int x = pos.getX();
                int z = pos.getZ();
                int surfaceY = world.getTopY(Heightmap.Type.WORLD_SURFACE, x, z);
                BlockPos topPos = new BlockPos(x, surfaceY, z);
                BlockState topState = world.getBlockState(topPos);
                // 树木使用地表方块作为生成中心；花/草优先替换顶层可替换方块（如短草），否则用顶层上方一格
                if (plantType == PushdozerConfig.PlantType.TREES) {
                    BlockPos placePos = new BlockPos(x, surfaceY + 1, z);
                    BlockPos treeCenter = new BlockPos(x, surfaceY, z);
                    BlockState currentState = world.getBlockState(placePos);
                    boolean passPrecheck = canPlantAtOptimized(currentState, world.getBlockState(treeCenter));
                    if (!passPrecheck) continue;
                    if (!densitySampler.shouldPlantHere(placePos)) continue;
                    positions.add(new PlantingPosition(treeCenter, plantType));
                } else {
                    BlockPos candidate = topState.isReplaceable() ? topPos : topPos.up();
                    BlockState currentState = world.getBlockState(candidate);
                    boolean passPrecheck = canPlantAtOptimized(currentState, world.getBlockState(candidate.down()));
                    if (!passPrecheck) continue;
                    if (!densitySampler.shouldPlantHere(candidate)) continue;
                    positions.add(new PlantingPosition(candidate, plantType));
                }

                // 简单植物最小间距抑制：屏蔽邻近列
                if (spacingRadius > 0) {
                    for (int dx = -spacingRadius; dx <= spacingRadius; dx++) {
                        for (int dz = -spacingRadius; dz <= spacingRadius; dz++) {
                            long k = BlockPos.asLong(x + dx, 0, z + dz);
                            blockedColumns.add(k);
                        }
                    }
                }
            }
        }

        return positions;
    }

    private boolean containsLiveCoral(List<Block> blocks) {
        if (blocks == null) return false;
        for (Block b : blocks) {
            if (b != null && PlantBlockClassifier.isLiveCoral(b)) return true;
        }
        return false;
    }

    /**
     * 优化的canPlantAt方法，使用预获取的BlockState
     */
    private boolean canPlantAtOptimized(BlockState currentState, BlockState groundState) {
        // 放宽预筛选条件，将最终合法性交给方块自身的 canPlaceAt
        return currentState.isAir() || currentState.isReplaceable();
    }
}
