package com.pushdozer.ui.screens;

import com.pushdozer.ui.selection.AbstractPagedCategorySelectionScreen;
import com.pushdozer.ui.selection.BlockSearchIndex;
import com.pushdozer.ui.selection.SelectionCategory;
import com.pushdozer.ui.selection.SelectionScreenStyle;
import com.pushdozer.ui.selection.SingleSelectStrategy;
import com.pushdozer.util.ExceptionPolicy;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.registry.Registries;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

/**
 * 地形方块选择屏幕
 * 专门用于表层转换配置，包含自然方块、地形方块和流体
 */
public class NaturalBlockSelectionScreen extends AbstractPagedCategorySelectionScreen {
    private final SingleSelectStrategy<Block> singleSelect;
    private final Consumer<Block> onBlockSelected;
    private static List<SelectionCategory<Block>> cachedCategories;

    public NaturalBlockSelectionScreen(Screen parent, Consumer<Block> onBlockSelected) {
        this(parent, onBlockSelected, new SingleSelectStrategy<>());
    }

    private NaturalBlockSelectionScreen(Screen parent, Consumer<Block> onBlockSelected, SingleSelectStrategy<Block> singleSelect) {
        super(parent,
            Text.translatable("pushdozer.screen.terrain_block_selection.title"),
            loadCategories(),
            singleSelect,
            4,
            2);
        this.singleSelect = singleSelect;
        this.onBlockSelected = onBlockSelected;
    }

    private static List<SelectionCategory<Block>> loadCategories() {
        if (cachedCategories == null) {
            cachedCategories = buildCategories();
        }
        return cachedCategories;
    }

    @Override
    protected Text searchFieldLabel() {
        return Text.translatable("pushdozer.screen.terrain_block_selection.search");
    }

    @Override
    protected Text previousPageTooltip() {
        return Text.translatable("pushdozer.screen.terrain_block_selection.previous_page");
    }

    @Override
    protected Text nextPageTooltip() {
        return Text.translatable("pushdozer.screen.terrain_block_selection.next_page");
    }

    @Override
    protected void addFooterButtons(int startX, int buttonY) {
        addDrawableChild(ButtonWidget.builder(Text.translatable("gui.ok"), button -> onConfirm())
            .dimensions(startX, buttonY, SelectionScreenStyle.BUTTON_WIDTH, SelectionScreenStyle.BUTTON_HEIGHT).build());
        addDrawableChild(ButtonWidget.builder(Text.translatable("gui.cancel"), button -> returnToParent())
            .dimensions(startX + SelectionScreenStyle.BUTTON_WIDTH + SelectionScreenStyle.BUTTON_SPACING, buttonY,
                SelectionScreenStyle.BUTTON_WIDTH, SelectionScreenStyle.BUTTON_HEIGHT).build());
    }

    @Override
    protected String formatStatusLine(int selectedCount, int totalCount) {
        Block selected = singleSelect.getSelected();
        if (selected != null) {
            String selectedBlockName = Text.translatable(selected.getTranslationKey()).getString();
            return String.format("选中: %s / 总计: %d 个方块", selectedBlockName, totalCount);
        }
        return String.format("请选择一个方块 / 总计: %d 个方块", totalCount);
    }

    @Override
    protected void onConfirm() {
        Block selected = singleSelect.getSelected();
        if (selected != null) {
            onBlockSelected.accept(selected);
        }
        returnToParent();
    }

    private static List<SelectionCategory<Block>> buildCategories() {
        System.out.println("正在重建地形方块分类缓存...");
        Map<String, SelectionCategory<Block>> categoryMap = new LinkedHashMap<>();
        Set<String> processedBlockIds = new HashSet<>();

        for (Block block : Registries.BLOCK) {
            String blockId = Registries.BLOCK.getId(block).toString();

            if (processedBlockIds.contains(blockId)) {
                continue;
            }
            processedBlockIds.add(blockId);

            List<String> categoryKeys = getCategoriesForBlock(block);

            for (String categoryKey : categoryKeys) {
                SelectionCategory<Block> category = categoryMap.computeIfAbsent(categoryKey,
                    key -> new SelectionCategory<>(key, getCategoryPriority(key)));
                category.addItem(block, blockId);
                BlockSearchIndex.indexBlock(block);
            }
        }

        List<SelectionCategory<Block>> result = new ArrayList<>(categoryMap.values());
        SelectionCategory.sortByPriority(result);

        for (SelectionCategory<Block> category : result) {
            category.getItems().sort(Comparator.comparing(b -> Registries.BLOCK.getId(b).getPath()));
        }

        System.out.println("地形方块分类完成，共 " + result.size() + " 个分类：");
        for (SelectionCategory<Block> category : result) {
            System.out.println("  " + category.getTranslatedName().getString() + ": " + category.getItems().size() + " 个方块");

            if (category.getTranslationKey().equals("pushdozer.category.leaves")) {
                System.out.println("    树叶分类中的方块：");
                for (Block block : category.getItems()) {
                    String blockId = Registries.BLOCK.getId(block).getPath();
                    System.out.println("      - " + blockId);
                }

                boolean hasCherryLeaves = category.getItems().stream().anyMatch(b -> Registries.BLOCK.getId(b).getPath().equals("cherry_leaves"));
                boolean hasFloweringAzaleaLeaves = category.getItems().stream().anyMatch(b -> Registries.BLOCK.getId(b).getPath().equals("flowering_azalea_leaves"));
                System.out.println("    检查结果：");
                System.out.println("      cherry_leaves: " + (hasCherryLeaves ? "✅ 已包含" : "❌ 未包含"));
                System.out.println("      flowering_azalea_leaves: " + (hasFloweringAzaleaLeaves ? "✅ 已包含" : "❌ 未包含"));
            }
        }
        return result;
    }

    private static int getCategoryPriority(String categoryKey) {
        return switch (categoryKey) {
            case "pushdozer.category.terrain" -> 1;
            case "pushdozer.category.ice_snow" -> 2;
            case "pushdozer.category.ores" -> 3;
            case "pushdozer.category.copper" -> 4;
            case "pushdozer.category.wood" -> 5;
            case "pushdozer.category.leaves" -> 6;
            case "pushdozer.category.biological" -> 7;
            case "pushdozer.category.stairs_slabs" -> 8;
            case "pushdozer.category.dyed" -> 9;
            case "pushdozer.category.functional" -> 10;
            case "pushdozer.category.glowing" -> 11;
            case "pushdozer.category.decorative" -> 12;
            case "pushdozer.category.redstone" -> 13;
            case "pushdozer.category.fluid" -> 14;
            case "pushdozer.category.miscellaneous" -> 15;
            default -> 100;
        };
    }

    private static List<String> getCategoriesForBlock(Block block) {
        List<String> categories = new ArrayList<>();
        String blockId = Registries.BLOCK.getId(block).getPath().toLowerCase();

        // 优先处理流体，确保它们被归类到流体分类
        if (blockId.equals("water") || blockId.equals("lava")) {
            categories.add("pushdozer.category.fluid");
            return categories;
        }

        // 过滤非地形方块
        // 使用硬度和常见模式来判断是否为地形方块
        BlockState defaultState = block.getDefaultState();
        
        // 1. 排除空气方块
        if (defaultState.isAir()) {
            return categories;
        }
        
        // 2. 排除硬度为负数的方块（通常是不可破坏的特殊方块或空气），但保留流体
        if (block.getHardness() < 0 && !blockId.equals("obsidian") && !blockId.equals("bedrock")) {
            return categories;
        }
        
        // 3. 排除已知的非地形方块类型（但保留流体）
        if (blockId.contains("sign") || blockId.contains("button") || blockId.contains("pressure_plate") || blockId.contains("lever")) {
            return categories;
        }
        
        try {
            // 使用标签排除植物/花草/农作物/可替换植物等（但保留树叶和生物方块）
            // 排除门、活板门
            // 排除栅栏和栅栏门
            // 手动排除其他装饰物和不需要的方块
            // 排除玻璃板
            // 排除酿造台
            // 排除红石组件
            // 排除其他不需要的方块
            // 排除盆栽方块
            // 排除结果的西瓜茎和结果的南瓜茎
            // 排除珊瑚、失活的珊瑚、下界疣、红色蘑菇、棕色蘑菇、苍白垂须
            if ((block.getDefaultState().isIn(BlockTags.FLOWERS) || block.getDefaultState().isIn(BlockTags.CROPS) || block.getDefaultState().isIn(BlockTags.SAPLINGS) || block.getDefaultState().isIn(BlockTags.REPLACEABLE) || block.getDefaultState().isIn(BlockTags.BEDS) || block.getDefaultState().isIn(BlockTags.BANNERS) || block.getDefaultState().isIn(BlockTags.WOOL) || block.getDefaultState().isIn(BlockTags.CANDLES) || block.getDefaultState().isIn(BlockTags.CAMPFIRES) || block.getDefaultState().isIn(BlockTags.CLIMBABLE) || block.getDefaultState().isIn(BlockTags.CORAL_PLANTS) || block.getDefaultState().isIn(BlockTags.DOORS) || block.getDefaultState().isIn(BlockTags.TRAPDOORS) || block.getDefaultState().isIn(BlockTags.FENCES) || block.getDefaultState().isIn(BlockTags.FENCE_GATES) || blockId.contains("torch") || blockId.contains("lantern") || blockId.contains("chain") || blockId.contains("end_rod") || blockId.contains("lily_pad") || blockId.contains("sugar_cane") || blockId.contains("bamboo") || blockId.contains("fungus") || blockId.contains("sea_pickle") || blockId.contains("vine") || blockId.contains("grass") || blockId.contains("fern") || blockId.contains("bush") || blockId.equals("painting") || blockId.contains("pale_hanging_moss") || blockId.contains("item_frame") || blockId.contains("carpet") || blockId.contains("pane") || blockId.equals("brewing_stand") || blockId.contains("repeater") || blockId.contains("comparator") || blockId.contains("tripwire_hook") || blockId.contains("redstone_wire") || blockId.contains("rail") || blockId.contains("powered_rail") || blockId.contains("detector_rail") || blockId.contains("activator_rail") || blockId.equals("bell") || blockId.contains("dripleaf") || blockId.contains("cake") || blockId.equals("cobweb") || blockId.equals("cocoa") || blockId.equals("conduit") || blockId.equals("dragon_egg") || blockId.contains("dragon_head") || blockId.equals("flower_pot") || blockId.equals("frogspawn") || blockId.equals("iron_bars") || blockId.contains("kelp") || blockId.equals("lightning_rod") || blockId.equals("sculk_vein") || blockId.contains("skull") || blockId.contains("head") || blockId.equals("pointed_dripstone") || blockId.equals("tripwire") || blockId.equals("turtle_egg") || blockId.equals("sculk_catalyst") || blockId.equals("sculk_shrieker") || blockId.equals("sculk_sensor") || blockId.contains("potted_") || blockId.equals("attached_melon_stem") || blockId.equals("attached_pumpkin_stem") || (blockId.contains("coral") && !blockId.contains("coral_block")) || blockId.equals("nether_wart") || blockId.equals("red_mushroom") || blockId.equals("brown_mushroom")) && 
                !blockId.equals("cherry_leaves") && !blockId.equals("flowering_azalea_leaves") && !blockId.equals("frosted_ice") &&
                !blockId.equals("grass_block") &&
                !blockId.equals("glowstone") && !blockId.equals("sea_lantern") && !blockId.contains("shroomlight") && 
                !blockId.contains("froglight") && !blockId.equals("redstone_lamp") && !blockId.contains("glow_lichen") && 
                !blockId.equals("beacon") && !blockId.equals("lantern") && !blockId.equals("soul_lantern") &&
                !blockId.equals("campfire") && !blockId.equals("soul_campfire") && 
                !blockId.equals("end_rod") && !blockId.equals("torch") && 
                !blockId.equals("soul_torch") && !blockId.equals("redstone_torch") && !blockId.equals("respawn_anchor")) {
                return categories;
            }
        } catch (RuntimeException e) {
            ExceptionPolicy.rethrowIfProgrammingError(e);
            System.err.println("Failed to check tags for block: " + blockId + ", skipping: " + e.getMessage());
            return categories;
        }

        try {
            // 1. 地形方块 - 只包含基础自然地形材料，排除加工建筑构件
            if ((block.getDefaultState().isIn(BlockTags.DIRT) || block.getDefaultState().isIn(BlockTags.BASE_STONE_OVERWORLD) ||
                block.getDefaultState().isIn(BlockTags.SAND) || blockId.equals("obsidian") || blockId.equals("bedrock") ||
                blockId.equals("mud") || blockId.equals("muddy_mangrove_roots") || blockId.equals("packed_mud") || blockId.equals("dirt_path") ||
                blockId.equals("farmland") || blockId.equals("gravel") || blockId.equals("calcite") ||
                blockId.equals("clay") || blockId.equals("deepslate") || blockId.equals("stone") ||
                blockId.equals("cobblestone") || blockId.equals("cobbled_deepslate") || blockId.equals("end_stone") ||
                blockId.contains("blackstone") || blockId.equals("crying_obsidian") || blockId.equals("crimson_nylium") ||
                blockId.equals("warped_nylium") || blockId.equals("netherrack") || blockId.equals("soul_sand") ||
                blockId.equals("soul_soil") || blockId.equals("basalt") || blockId.equals("smooth_basalt") ||
                blockId.equals("magma_block") || blockId.equals("infested_stone") || blockId.equals("infested_deepslate") ||
                blockId.equals("infested_cobblestone") || blockId.equals("suspicious_gravel") ||
                (blockId.contains("soil") && !blockId.contains("soul"))) &&
                // 排除建筑构件：墙、台阶、楼梯等
                !block.getDefaultState().isIn(BlockTags.WALLS) &&
                !block.getDefaultState().isIn(BlockTags.STAIRS) &&
                !block.getDefaultState().isIn(BlockTags.SLABS) &&
                !blockId.contains("wall") && !blockId.contains("slab") && !blockId.contains("stairs")) {
                // 添加调试信息
                if (blockId.equals("grass_block")) {
                    System.out.println("DEBUG: 找到草方块: " + blockId + ", 是否在BlockTags.DIRT中: " + block.getDefaultState().isIn(BlockTags.DIRT));
                    System.out.println("DEBUG: " + blockId + " 被分类到地形分类");
                }
                categories.add("pushdozer.category.terrain");
                return categories;
            }
            
            // 2. 冰与雪
            if (block.getDefaultState().isIn(BlockTags.ICE) || block.getDefaultState().isIn(BlockTags.SNOW) ||
                blockId.equals("snow") || blockId.equals("snow_block") || blockId.equals("powder_snow") || 
                blockId.equals("packed_ice") || blockId.equals("blue_ice") || blockId.equals("frosted_ice")) {
                // 添加调试信息
                if (blockId.equals("frosted_ice")) {
                    System.out.println("DEBUG: 找到霜冰方块: " + blockId + ", 是否在BlockTags.ICE中: " + block.getDefaultState().isIn(BlockTags.ICE));
                    System.out.println("DEBUG: " + blockId + " 是否在BlockTags.REPLACEABLE中: " + block.getDefaultState().isIn(BlockTags.REPLACEABLE));
                    System.out.println("DEBUG: " + blockId + " 被分类到冰与雪分类");
                }
                categories.add("pushdozer.category.ice_snow");
                return categories;
            }
            
            // 3. 矿物方块（排除铜）
            if ((blockId.contains("ore") && !blockId.contains("spore") && !blockId.contains("copper")) || 
                blockId.equals("ancient_debris") ||
                blockId.endsWith("_block") && (blockId.contains("coal") || blockId.contains("iron") ||
                blockId.contains("gold") || blockId.contains("diamond") || blockId.contains("emerald") ||
                blockId.contains("lapis") || blockId.contains("netherite") || blockId.contains("raw_") && !blockId.contains("copper"))) {
                categories.add("pushdozer.category.ores");
                return categories;
            }
            
            // 4. 铜方块
            if (blockId.contains("copper")) {
                categories.add("pushdozer.category.copper");
                return categories;
            }
            
            // 5. 木材方块
            if (block.getDefaultState().isIn(BlockTags.LOGS) || block.getDefaultState().isIn(BlockTags.PLANKS) ||
                blockId.contains("bamboo_block") || blockId.contains("stripped_bamboo") ||
                blockId.contains("hyphae") || blockId.equals("bamboo_mosaic")) {
                categories.add("pushdozer.category.wood");
                return categories;
            }
            
            // 6. 树叶方块
            if (block.getDefaultState().isIn(BlockTags.LEAVES) || 
                blockId.equals("cherry_leaves") || blockId.equals("flowering_azalea_leaves")) {
                // 添加调试信息
                if (blockId.equals("cherry_leaves") || blockId.equals("flowering_azalea_leaves")) {
                    System.out.println("DEBUG: 找到特殊树叶方块: " + blockId + ", 是否在BlockTags.LEAVES中: " + block.getDefaultState().isIn(BlockTags.LEAVES));
                    System.out.println("DEBUG: " + blockId + " 是否在BlockTags.REPLACEABLE中: " + block.getDefaultState().isIn(BlockTags.REPLACEABLE));
                }
                categories.add("pushdozer.category.leaves");
                return categories;
            }
            
            // 7. 生物方块
            if (blockId.contains("coral_block") || blockId.contains("dead_coral_block") ||
                blockId.equals("dried_kelp_block") || blockId.equals("sponge") || blockId.equals("wet_sponge") ||
                blockId.equals("melon") || blockId.equals("pumpkin") || blockId.equals("carved_pumpkin") ||
                blockId.equals("jack_o_lantern") || blockId.equals("hay_bale") || blockId.equals("beehive") ||
                blockId.equals("honeycomb_block") || blockId.equals("slime_block") || blockId.equals("honey_block") ||
                blockId.equals("resin_block") || blockId.equals("cactus") || blockId.equals("brown_mushroom_block") ||
                blockId.equals("red_mushroom_block") || blockId.equals("mushroom_stem") || blockId.equals("nether_wart_block") ||
                blockId.equals("warped_wart_block") || blockId.equals("bone_block") || blockId.equals("sniffer_egg") ||
                blockId.equals("moss_block") || blockId.equals("pale_moss_block") ||
                // 红树根方块
                blockId.equals("mangrove_roots") ||
                // 各种珊瑚块和失活的珊瑚块
                blockId.equals("tube_coral_block") || blockId.equals("brain_coral_block") || blockId.equals("bubble_coral_block") ||
                blockId.equals("fire_coral_block") || blockId.equals("horn_coral_block") ||
                blockId.equals("dead_tube_coral_block") || blockId.equals("dead_brain_coral_block") || blockId.equals("dead_bubble_coral_block") ||
                blockId.equals("dead_fire_coral_block") || blockId.equals("dead_horn_coral_block")) {
                categories.add("pushdozer.category.biological");
                return categories;
            }
            
            // 8. 楼梯与台阶
            if (block.getDefaultState().isIn(BlockTags.STAIRS) || block.getDefaultState().isIn(BlockTags.SLABS)) {
                categories.add("pushdozer.category.stairs_slabs");
                return categories;
            }
            
            // 9. 染色方块
            if (blockId.contains("stained_") || blockId.contains("terracotta") || blockId.contains("concrete") ||
                (blockId.contains("glass") && !blockId.equals("glass")) || // 排除普通玻璃
                blockId.contains("glazed_terracotta")) {
                categories.add("pushdozer.category.dyed");
                return categories;
            }
            
            // 10. 功能方块
            if (block.getDefaultState().isIn(BlockTags.ANVIL) || block.getDefaultState().isIn(BlockTags.CAULDRONS) ||
                block.getDefaultState().isIn(BlockTags.SHULKER_BOXES) || blockId.contains("chest") ||
                blockId.contains("barrel") || blockId.contains("furnace") || blockId.contains("smoker") ||
                blockId.contains("grindstone") || blockId.contains("loom") || blockId.contains("stonecutter") ||
                blockId.contains("lectern") || blockId.contains("composter") ||
                blockId.contains("enchanting_table") || blockId.contains("ender_chest") ||
                blockId.contains("crafting_table") || blockId.contains("bookshelf") ||
                blockId.equals("bee_nest") || blockId.equals("spawner") ||
                blockId.equals("trial_spawner")) {
                categories.add("pushdozer.category.functional");
                return categories;
            }
            
            // 11. 发光方块
            if (blockId.equals("glowstone") || blockId.equals("sea_lantern") || blockId.contains("shroomlight") || 
                blockId.contains("froglight") || blockId.equals("redstone_lamp") || blockId.contains("glow_lichen") || 
                blockId.equals("beacon") || blockId.equals("lantern") || blockId.equals("soul_lantern") ||
                blockId.equals("campfire") || blockId.equals("soul_campfire") || 
                blockId.equals("end_rod") || blockId.equals("torch") || 
                blockId.equals("soul_torch") || blockId.equals("redstone_torch") || blockId.equals("respawn_anchor")) {
                categories.add("pushdozer.category.glowing");
                return categories;
            }
            
            // 12. 装饰方块
            if (blockId.contains("brick") || blockId.contains("sandstone") || blockId.contains("prismarine") ||
                blockId.contains("quartz") || blockId.equals("smooth_stone") || blockId.contains("glass") ||
                blockId.contains("polished_") || blockId.contains("chiseled_") || blockId.equals("purpur_block") ||
                blockId.equals("purpur_pillar") || block.getDefaultState().isIn(BlockTags.WALLS)) {
                categories.add("pushdozer.category.decorative");
                return categories;
            }
            
            // 13. 红石与机械
            if (blockId.contains("piston") || blockId.contains("dispenser") || blockId.contains("observer") ||
                blockId.contains("hopper") || blockId.equals("tnt") || blockId.contains("target") || 
                blockId.contains("button") || blockId.contains("pressure_plate") || blockId.contains("lever") ||
                blockId.contains("redstone") || blockId.equals("dropper")) {
                categories.add("pushdozer.category.redstone");
                return categories;
            }
            
        } catch (RuntimeException e) {
            ExceptionPolicy.rethrowIfProgrammingError(e);
            System.err.println("Failed to check tags for block: " + blockId + ", using miscellaneous: " + e.getMessage());
        }
        
        // 13. 杂项（兜底）

        categories.add("pushdozer.category.miscellaneous");
        return categories;
    }

} 
