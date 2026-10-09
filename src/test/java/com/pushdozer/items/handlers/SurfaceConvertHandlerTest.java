package com.pushdozer.items.handlers;

import com.pushdozer.PushdozerTestBase;
import com.pushdozer.config.PushdozerConfig;
import com.pushdozer.config.domain.SurfaceConfig;
import com.pushdozer.items.handlers.surface.NaturalTerrainClassifier;
import com.pushdozer.items.handlers.surface.SurfaceConvertMaterialSelector;
import com.pushdozer.items.handlers.surface.SurfacePlantSurvival;
import com.pushdozer.items.handlers.terrain.TerrainSurfaceQueries;
import com.pushdozer.tags.PushdozerBlockTags;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;

class SurfaceConvertHandlerTest extends PushdozerTestBase {

    @Test
    void naturalClassifier_protectsBuildingBlocks() {
        BlockState planks = taggedState(Blocks.OAK_PLANKS, BlockTags.PLANKS);
        BlockState chest = explicitState(Blocks.CHEST);

        assertTrue(NaturalTerrainClassifier.isProtectedSource(planks));
        assertTrue(NaturalTerrainClassifier.isProtectedSource(chest));
        assertFalse(NaturalTerrainClassifier.isConvertibleSource(planks, false));
    }

    @Test
    void naturalClassifier_allowsNaturalTerrain() {
        assertTrue(NaturalTerrainClassifier.isNaturalConvertibleSource(explicitState(Blocks.GRASS_BLOCK)));
        assertTrue(NaturalTerrainClassifier.isNaturalConvertibleSource(taggedState(Blocks.SAND, BlockTags.SAND)));
        assertTrue(NaturalTerrainClassifier.isNaturalConvertibleSource(
            taggedState(Blocks.STONE, BlockTags.BASE_STONE_OVERWORLD)));
    }

    @Test
    void targetValidation_rejectsDoorsAndAcceptsGrassBlock() {
        Block doorBlock = Mockito.mock(Block.class);
        BlockState doorState = taggedState(doorBlock, BlockTags.DOORS);
        when(doorBlock.getDefaultState()).thenReturn(doorState);

        Block grassBlock = Mockito.mock(Block.class);
        BlockState grassState = explicitState(Blocks.GRASS_BLOCK);
        when(grassBlock.getDefaultState()).thenReturn(grassState);
        when(grassState.getCollisionShape(any(), any())).thenReturn(Blocks.STONE.getDefaultState().getCollisionShape(null, BlockPos.ORIGIN));

        Block dirtBlock = Mockito.mock(Block.class);
        BlockState dirtState = explicitState(Blocks.DIRT);
        when(dirtBlock.getDefaultState()).thenReturn(dirtState);
        when(dirtState.getCollisionShape(any(), any())).thenReturn(Blocks.STONE.getDefaultState().getCollisionShape(null, BlockPos.ORIGIN));

        Block stoneBlock = Mockito.mock(Block.class);
        BlockState stoneState = explicitState(Blocks.STONE);
        when(stoneBlock.getDefaultState()).thenReturn(stoneState);
        when(stoneState.getCollisionShape(any(), any())).thenReturn(Blocks.STONE.getDefaultState().getCollisionShape(null, BlockPos.ORIGIN));

        assertFalse(NaturalTerrainClassifier.isValidTargetBlock(doorBlock));
        assertTrue(NaturalTerrainClassifier.isValidTargetBlock(grassBlock));
        assertTrue(NaturalTerrainClassifier.isValidTargetBlock(dirtBlock));
        assertTrue(NaturalTerrainClassifier.isValidTargetBlock(stoneBlock));
    }

    @Test
    void materialSelector_acceptsStoneTarget() {
        SurfaceConvertMaterialSelector.SelectionContext context = stoneContext();
        assertFalse(context.isEmpty());
        assertEquals(Blocks.STONE, SurfaceConvertMaterialSelector.selectBlock(context, new BlockPos(3, 0, 4)));
    }

    @Test
    void materialSelector_normalizesWeightsAndIsDeterministic() {
        SurfaceConvertMaterialSelector.SelectionContext context = new SurfaceConvertMaterialSelector.SelectionContext(
            List.of(
                new SurfaceConvertMaterialSelector.ResolvedMaterial(Blocks.GRASS_BLOCK, 50f),
                new SurfaceConvertMaterialSelector.ResolvedMaterial(Blocks.SAND, 50f)
            ),
            100f,
            new SeededPerlinNoise(4242L),
            4242L,
            PushdozerConfig.SurfaceConvertDistribution.PATCHY,
            0.05f
        );
        assertFalse(context.isEmpty());
        assertEquals(100f, context.totalWeight(), 0.01f);

        BlockPos sample = new BlockPos(4, 0, 3);
        Block first = SurfaceConvertMaterialSelector.selectBlock(context, sample);
        Block second = SurfaceConvertMaterialSelector.selectBlock(context, sample);
        assertEquals(first, second);

        SurfaceConvertMaterialSelector.SelectionContext otherSeed = new SurfaceConvertMaterialSelector.SelectionContext(
            List.of(
                new SurfaceConvertMaterialSelector.ResolvedMaterial(Blocks.GRASS_BLOCK, 50f),
                new SurfaceConvertMaterialSelector.ResolvedMaterial(Blocks.SAND, 50f)
            ),
            100f,
            new SeededPerlinNoise(9999L),
            9999L,
            PushdozerConfig.SurfaceConvertDistribution.PATCHY,
            0.05f
        );
        Block other = SurfaceConvertMaterialSelector.selectBlock(otherSeed, sample);
        assertNotNull(first);
        assertNotNull(other);
    }

    @Test
    void materialSelector_rejectsInvalidTargets() {
        PushdozerConfig config = surfaceConvertConfig();
        config.getSurfaceConvertBlocks().clear();
        config.getSurfaceConvertBlocks().add(new SurfaceConfig.SurfaceConvertBlock("not_a_valid_block", 100f));
        SurfaceConvertMaterialSelector.SelectionContext context = SurfaceConvertMaterialSelector.prepare(config);
        assertTrue(context.isEmpty());
    }

    @Test
    void scatterMode_isDeterministicForSameSeed() {
        SurfaceConvertMaterialSelector.SelectionContext context = new SurfaceConvertMaterialSelector.SelectionContext(
            List.of(
                new SurfaceConvertMaterialSelector.ResolvedMaterial(Blocks.GRASS_BLOCK, 50f),
                new SurfaceConvertMaterialSelector.ResolvedMaterial(Blocks.SAND, 50f)
            ),
            100f,
            null,
            111L,
            PushdozerConfig.SurfaceConvertDistribution.SCATTER,
            0.05f
        );

        BlockPos sample = new BlockPos(5, 0, 7);
        Block first = SurfaceConvertMaterialSelector.selectBlock(context, sample);
        Block second = SurfaceConvertMaterialSelector.selectBlock(context, sample);
        assertEquals(first, second);
    }

    @Test
    void plantSurvival_keepsGrassOnDirt_removesOnStone() {
        BlockState grass = explicitState(Blocks.SHORT_GRASS);
        BlockState dirt = taggedState(Blocks.DIRT, BlockTags.DIRT);
        BlockState stone = explicitState(Blocks.STONE);

        assertTrue(SurfacePlantSurvival.canSurviveOn(grass, dirt));
        assertFalse(SurfacePlantSurvival.canSurviveOn(grass, stone));
    }

    @Test
    void heightmapGuard_rejectsWhenWorldMissing() {
        assertFalse(TerrainSurfaceQueries.isWithinSurfaceDepth(null, 0, 0, 64, 3));
    }

    @Test
    void heightmapGuard_unlimitedDepthStillRequiresWorld() {
        assertFalse(TerrainSurfaceQueries.isWithinSurfaceDepth(null, 0, 0, -100, -1));
    }

    @Test
    void heightmapGuard_unlimitedDepthSkipsReferenceLookup() {
        World world = mockWorldWithBlocks(Map.of());
        assertTrue(TerrainSurfaceQueries.isWithinSurfaceDepth(world, 0, 0, -100, -1));
    }

    @Test
    void heightmapGuard_depthZeroAllowsOnlyReferenceSurface() {
        BlockPos surface = new BlockPos(0, 10, 0);
        BlockPos below = new BlockPos(0, 9, 0);
        World world = mockWorldWithBlocks(Map.of(
            surface, explicitState(Blocks.GRASS_BLOCK),
            below, taggedState(Blocks.DIRT, BlockTags.DIRT)
        ));
        when(world.getTopY(net.minecraft.world.Heightmap.Type.WORLD_SURFACE, 0, 0)).thenReturn(10);
        when(world.getTopY(net.minecraft.world.Heightmap.Type.OCEAN_FLOOR, 0, 0)).thenReturn(10);

        assertTrue(TerrainSurfaceQueries.isWithinSurfaceDepth(world, 0, 0, 10, 0));
        assertFalse(TerrainSurfaceQueries.isWithinSurfaceDepth(world, 0, 0, 9, 0));
    }

    @Test
    void heightmapGuard_rejectsWhenReferenceSurfaceIndeterminate() {
        World world = mockWorldWithBlocks(Map.of());
        when(world.getTopY(net.minecraft.world.Heightmap.Type.WORLD_SURFACE, 0, 0)).thenReturn(8);
        when(world.getTopY(net.minecraft.world.Heightmap.Type.OCEAN_FLOOR, 0, 0)).thenReturn(8);

        assertFalse(TerrainSurfaceQueries.isWithinSurfaceDepth(world, 0, 0, 8, 3));
    }

    @Test
    void heightmapGuard_rejectsDeepCandidateFarFromReference() {
        BlockPos surface = new BlockPos(0, 10, 0);
        BlockPos deep = new BlockPos(0, -40, 0);
        World world = mockWorldWithBlocks(Map.of(
            surface, explicitState(Blocks.GRASS_BLOCK),
            deep, taggedState(Blocks.STONE, BlockTags.BASE_STONE_OVERWORLD)
        ));
        when(world.getTopY(net.minecraft.world.Heightmap.Type.WORLD_SURFACE, 0, 0)).thenReturn(10);
        when(world.getTopY(net.minecraft.world.Heightmap.Type.OCEAN_FLOOR, 0, 0)).thenReturn(10);

        assertFalse(TerrainSurfaceQueries.isWithinSurfaceDepth(world, 0, 0, -40, 3));
    }

    @Test
    void resolveConvertibleSurface_findsGrassAboveStone() {
        BlockPos grass = new BlockPos(10, 1, 10);
        BlockPos stone = new BlockPos(10, 0, 10);
        World world = mockWorldWithBlocks(Map.of(
            grass, explicitState(Blocks.GRASS_BLOCK),
            stone, taggedState(Blocks.STONE, BlockTags.BASE_STONE_OVERWORLD)
        ));

        BlockPos result = SurfaceConvertHandler.resolveConvertibleSurface(
            world, new BlockPos(10, 0, 10), 2, 3, false);

        assertEquals(grass, result);
    }

    @Test
    void resolveConvertibleSurface_rejectsProtectedBuildingBlocks() {
        BlockPos planks = new BlockPos(4, 1, 4);
        BlockPos grass = new BlockPos(4, 0, 4);
        BlockState planksState = taggedState(Blocks.OAK_PLANKS, BlockTags.PLANKS);
        BlockState grassState = explicitState(Blocks.GRASS_BLOCK);
        World world = mockWorldWithBlocks(Map.of(
            planks, planksState,
            grass, grassState
        ));

        BlockPos result = SurfaceConvertHandler.resolveConvertibleSurface(
            world, new BlockPos(4, 0, 4), 2, 3, false);

        assertEquals(grass, result);
    }

    private static BlockState airState() {
        BlockState state = Mockito.mock(BlockState.class);
        when(state.isAir()).thenReturn(true);
        when(state.isIn(any(TagKey.class))).thenReturn(false);
        when(state.getFluidState()).thenReturn(net.minecraft.fluid.Fluids.EMPTY.getDefaultState());
        return state;
    }

    private static World mockWorldWithBlocks(Map<BlockPos, BlockState> blocks) {
        Map<BlockPos, BlockState> layout = new HashMap<>(blocks);
        BlockState air = airState();
        World world = Mockito.mock(World.class);
        when(world.getBottomY()).thenReturn(-64);
        when(world.getHeight()).thenReturn(320);
        when(world.getBlockState(any())).thenAnswer(invocation -> {
            BlockPos pos = invocation.getArgument(0);
            return layout.getOrDefault(pos, air);
        });
        when(world.isAir(any())).thenAnswer(invocation -> {
            BlockPos pos = invocation.getArgument(0);
            return !layout.containsKey(pos);
        });
        when(world.getFluidState(any())).thenReturn(net.minecraft.fluid.Fluids.EMPTY.getDefaultState());
        when(world.getTopY(any(), anyInt(), anyInt())).thenAnswer(invocation -> {
            int x = invocation.getArgument(1);
            int z = invocation.getArgument(2);
            int highest = Integer.MIN_VALUE;
            for (Map.Entry<BlockPos, BlockState> entry : layout.entrySet()) {
                BlockPos pos = entry.getKey();
                if (pos.getX() == x && pos.getZ() == z && !entry.getValue().isAir()) {
                    highest = Math.max(highest, pos.getY());
                }
            }
            return highest == Integer.MIN_VALUE ? 0 : highest;
        });
        return world;
    }

    private static SurfaceConvertMaterialSelector.SelectionContext stoneContext() {
        return new SurfaceConvertMaterialSelector.SelectionContext(
            List.of(new SurfaceConvertMaterialSelector.ResolvedMaterial(Blocks.STONE, 100f)),
            100f,
            null,
            42L,
            PushdozerConfig.SurfaceConvertDistribution.SCATTER,
            0.05f
        );
    }

    private static BlockState explicitState(Block block) {
        BlockState state = Mockito.mock(BlockState.class);
        when(state.isAir()).thenReturn(false);
        when(state.hasBlockEntity()).thenReturn(false);
        when(state.getBlock()).thenReturn(block);
        when(state.isIn(any(TagKey.class))).thenReturn(false);
        when(state.isIn(PushdozerBlockTags.SURFACE_CONVERT_PROTECTED)).thenReturn(false);
        when(state.isIn(PushdozerBlockTags.SURFACE_CONVERTIBLE_SOURCE)).thenReturn(false);
        when(state.isOf(block)).thenReturn(true);
        when(state.getFluidState()).thenReturn(net.minecraft.fluid.Fluids.EMPTY.getDefaultState());
        return state;
    }

    private static BlockState taggedState(Block block, TagKey<Block> tag) {
        BlockState state = explicitState(block);
        when(state.isIn(tag)).thenReturn(true);
        return state;
    }

    private static PushdozerConfig surfaceConvertConfig() {
        PushdozerConfig config = new PushdozerConfig();
        config.setNoiseSeed(12345L);
        config.setNoiseFrequency(0.05f);
        config.getSurfaceConvertBlocks().clear();
        config.getSurfaceConvertBlocks().add(
            new SurfaceConfig.SurfaceConvertBlock("minecraft:grass_block", 100f));
        return config;
    }
}
