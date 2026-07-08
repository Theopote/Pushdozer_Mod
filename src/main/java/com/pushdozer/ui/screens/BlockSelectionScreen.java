package com.pushdozer.ui.screens;

import com.pushdozer.config.PushdozerConfig;
import com.pushdozer.ui.selection.AbstractPagedCategorySelectionScreen;
import com.pushdozer.ui.selection.BlockSearchIndex;
import com.pushdozer.ui.selection.MultiSelectStrategy;
import com.pushdozer.ui.selection.SelectionCategory;
import com.pushdozer.ui.selection.SelectionScreenStyle;
import com.pushdozer.util.ExceptionPolicy;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.minecraft.block.Block;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

public class BlockSelectionScreen extends AbstractPagedCategorySelectionScreen {
    private final PushdozerConfig config;
    private static List<SelectionCategory<Block>> cachedCategories;

    public BlockSelectionScreen(PushdozerConfigScreen parent, PushdozerConfig config) {
        super(parent,
            Text.translatable("pushdozer.screen.block_selection.title"),
            loadCategories(),
            new MultiSelectStrategy<>(config.getBreakableBlocks()),
            4,
            3);
        this.config = config;
    }

    private static List<SelectionCategory<Block>> loadCategories() {
        if (cachedCategories == null) {
            cachedCategories = buildCategories();
        }
        return cachedCategories;
    }

    @Override
    protected Text searchFieldLabel() {
        return Text.translatable("pushdozer.screen.block_selection.search");
    }

    @Override
    protected Text previousPageTooltip() {
        return Text.translatable("pushdozer.screen.block_selection.previous_page");
    }

    @Override
    protected Text nextPageTooltip() {
        return Text.translatable("pushdozer.screen.block_selection.next_page");
    }

    @Override
    protected void addFooterButtons(int startX, int buttonY) {
        addDrawableChild(ButtonWidget.builder(Text.translatable("pushdozer.screen.block_selection.select_all"), button -> selectAllVisibleItems())
            .dimensions(startX, buttonY, SelectionScreenStyle.BUTTON_WIDTH, SelectionScreenStyle.BUTTON_HEIGHT).build());
        addDrawableChild(ButtonWidget.builder(Text.translatable("pushdozer.screen.block_selection.deselect_all"), button -> clearSelection())
            .dimensions(startX + SelectionScreenStyle.BUTTON_WIDTH + SelectionScreenStyle.BUTTON_SPACING, buttonY,
                SelectionScreenStyle.BUTTON_WIDTH, SelectionScreenStyle.BUTTON_HEIGHT).build());
        addDrawableChild(ButtonWidget.builder(Text.translatable("pushdozer.screen.block_selection.confirm"), button -> onConfirm())
            .dimensions(startX + 2 * (SelectionScreenStyle.BUTTON_WIDTH + SelectionScreenStyle.BUTTON_SPACING), buttonY,
                SelectionScreenStyle.BUTTON_WIDTH, SelectionScreenStyle.BUTTON_HEIGHT).build());
    }

    @Override
    protected String formatStatusLine(int selectedCount, int totalCount) {
        return String.format("已选择: %d / 总计: %d 个方块", selectedCount, totalCount);
    }

    @Override
    protected void onConfirm() {
        config.setBreakableBlocks(new ArrayList<>(((MultiSelectStrategy<Block>) selectionStrategy).snapshot()));
        returnToParent();
    }


    private static List<SelectionCategory<Block>> buildCategories() {
            // 使用 Map 来动态创建和填充分类
            Map<String, SelectionCategory<Block>> categoryMap = new LinkedHashMap<>();
            // 全局重复检查，确保每个方块只被处理一次
            Set<String> processedBlockIds = new HashSet<>();

            // 遍历所有注册的方块
            for (Block block : Registries.BLOCK) {
                Item item = block.asItem();
                String blockId = Registries.BLOCK.getId(block).toString();
                String blockIdPath = Registries.BLOCK.getId(block).getPath().toLowerCase();
                
                // 跳过空气方块，但保留流体方块和特殊植株方块（即使它们没有物品形态）
                if (item == Items.AIR && !blockIdPath.equals("water") && !blockIdPath.equals("lava") && 
                    !blockIdPath.equals("kelp_plant") && !blockIdPath.equals("tall_seagrass")) {
                    continue;
                }
                
                // 跳过已处理的方块，避免重复
                if (processedBlockIds.contains(blockId)) {
                    continue;
                }
                processedBlockIds.add(blockId);

                // 根据方块的翻译键进行分类
                List<String> categoryKeys = getCategoriesForBlock(block);
                // 限制分类数量，避免过多重叠
                if (categoryKeys.size() > 3) {
                    // 按优先级排序并只保留前3个
                    categoryKeys.sort((a, b) -> Integer.compare(getCategoryPriority(a), getCategoryPriority(b)));
                    categoryKeys = categoryKeys.subList(0, 3);
                }
                
                for (String categoryKey : categoryKeys) {
                    SelectionCategory<Block> category = categoryMap.computeIfAbsent(categoryKey, 
                        key -> new SelectionCategory<>(key, getCategoryPriority(key)));
                    category.addItem(block, blockId);
                    BlockSearchIndex.indexBlock(block);
                }
            }

            // 按优先级排序分类，并对每个分类内的方块进行排序
            List<SelectionCategory<Block>> result = new ArrayList<>(categoryMap.values());
            SelectionCategory.sortByPriority(result);
            for (SelectionCategory<Block> category : result) {
                category.getItems().sort(Comparator.comparing(b -> Registries.BLOCK.getId(b).getPath()));
            }
            return result;
    }
    
    // 获取分类优先级
    private static int getCategoryPriority(String categoryKey) {
        return switch (categoryKey) {
            case "pushdozer.category.terrain" -> 1;
            case "pushdozer.category.ores" -> 2;
            case "pushdozer.category.wood" -> 3;
            case "pushdozer.category.doors_and_trapdoors" -> 4;
            case "pushdozer.category.leaves" -> 5;
            case "pushdozer.category.plants_and_flowers" -> 6;
            case "pushdozer.category.crops" -> 7;
            case "pushdozer.category.fungi" -> 8;
            case "pushdozer.category.coral" -> 9;
            case "pushdozer.category.nether" -> 10;
            case "pushdozer.category.stairs_and_slabs" -> 11;
            case "pushdozer.category.fences_and_walls" -> 12;
            case "pushdozer.category.buttons_and_pressure_plates" -> 13;
            case "pushdozer.category.copper" -> 14;
            case "pushdozer.category.amethyst" -> 15;
            case "pushdozer.category.valuable" -> 16;
            case "pushdozer.category.redstone" -> 17;
            case "pushdozer.category.decorative" -> 18;
            case "pushdozer.category.dyed" -> 19;
            case "pushdozer.category.functional" -> 20;
            case "pushdozer.category.heads" -> 21;
            case "pushdozer.category.rails" -> 22;
            case "pushdozer.category.cave_decorations" -> 23;
            case "pushdozer.category.fluids" -> 24;
            case "pushdozer.category.bricks_and_tiles" -> 25;
            case "pushdozer.category.technical" -> 26;
            case "pushdozer.category.miscellaneous" -> 27;
            default -> 100; // 未知分类优先级最低
        };
    }

    // 新的多分类方法 - 基于用户要求的28个分类
    private static List<String> getCategoriesForBlock(Block block) {
        List<String> categories = new ArrayList<>();
        String blockId = Registries.BLOCK.getId(block).getPath().toLowerCase();

        // 使用Minecraft内置标签进行分类
        try {
            // 1. 地形：minecraft中生成地形有关的所有方块
            if (block.getDefaultState().isIn(BlockTags.DIRT) || 
                block.getDefaultState().isIn(BlockTags.BASE_STONE_OVERWORLD) ||
                block.getDefaultState().isIn(BlockTags.SAND) ||
                block.getDefaultState().isIn(BlockTags.ICE) ||
                block.getDefaultState().isIn(BlockTags.SNOW) ||
                isTerrainManual(blockId)) {
                categories.add("pushdozer.category.terrain");
            }
            
            // 2. 矿石：目前的方块收集是正确的，但是排除孢子花，应该把孢子花放在植物和花朵分类中
            if ((blockId.contains("ore") || blockId.equals("ancient_debris")) && 
                !blockId.equals("spore_blossom") && !blockId.equals("heavy_core")) {
                categories.add("pushdozer.category.ores");
            }
            
            // 3. 木材：各种原木、去皮木、模板，但是不包含门、活扳门、栅栏、楼梯和台阶
            if (block.getDefaultState().isIn(BlockTags.LOGS) ||
                block.getDefaultState().isIn(BlockTags.PLANKS) ||
                isWoodManual(blockId)) {
                categories.add("pushdozer.category.wood");
            }
            
            // 4. 门和活扳门：囊括所有的门和活扳门
            if (block.getDefaultState().isIn(BlockTags.DOORS) ||
                block.getDefaultState().isIn(BlockTags.TRAPDOORS)) {
                categories.add("pushdozer.category.doors_and_trapdoors");
            }
            
            // 5. 树叶：目前是正确的
            if (block.getDefaultState().isIn(BlockTags.LEAVES)) {
                categories.add("pushdozer.category.leaves");
            }
            
            // 6. 植物和花朵：包含树苗、各种花草植物、水中的植物（海草、高海草、海带植株、还泡菜）
            if (block.getDefaultState().isIn(BlockTags.FLOWERS) ||
                block.getDefaultState().isIn(BlockTags.SAPLINGS) ||
                blockId.equals("spore_blossom") || blockId.equals("chorus_plant") || blockId.equals("chorus_flower") ||
                isPlantManual(blockId)) {
                categories.add("pushdozer.category.plants_and_flowers");
            }
            
            // 7. 农作物：下届疣、紫菘花和紫颂植株不应该包含在农作物中，甜菜根图标不对，其他不变
            if ((block.getDefaultState().isIn(BlockTags.CROPS) ||
                block.getDefaultState().isIn(BlockTags.BEE_GROWABLES) ||
                isCropManual(blockId)) &&
                !blockId.equals("nether_wart") && !blockId.equals("torchflower_crop") && 
                !blockId.equals("chorus_plant") && !blockId.equals("chorus_flower")) {
                categories.add("pushdozer.category.crops");
            }
            
            // 8. 蘑菇：把蘑菇分类名称改为菌类
            if (block.getDefaultState().isIn(BlockTags.MUSHROOM_GROW_BLOCK) ||
                isFungiManual(blockId)) {
                categories.add("pushdozer.category.fungi");
            }
            
            // 9. 珊瑚：包含活的珊瑚、活的珊瑚扇、失活的珊瑚、失活的珊瑚扇、各种珊瑚块
            if (block.getDefaultState().isIn(BlockTags.CORALS) ||
                block.getDefaultState().isIn(BlockTags.CORAL_BLOCKS) ||
                block.getDefaultState().isIn(BlockTags.CORAL_PLANTS) ||
                block.getDefaultState().isIn(BlockTags.WALL_CORALS) ||
                isCoralManual(blockId)) {
                categories.add("pushdozer.category.coral");
            }
            
            // 10. 下界
            if (block.getDefaultState().isIn(BlockTags.BASE_STONE_NETHER) ||
                block.getDefaultState().isIn(BlockTags.NYLIUM) ||
                block.getDefaultState().isIn(BlockTags.SOUL_FIRE_BASE_BLOCKS) ||
                block.getDefaultState().isIn(BlockTags.SOUL_SPEED_BLOCKS) ||
                blockId.equals("nether_wart") || blockId.equals("torchflower_crop") ||
                blockId.equals("nether_sprouts") || blockId.equals("nether_wart_block") ||
                isNetherManual(blockId)) {
                categories.add("pushdozer.category.nether");
            }
            
            // 11. 楼梯和台阶：包含所有材质类的楼梯和台阶
            if (block.getDefaultState().isIn(BlockTags.STAIRS) ||
                block.getDefaultState().isIn(BlockTags.SLABS)) {
                categories.add("pushdozer.category.stairs_and_slabs");
            }
            
            // 12. 栅栏和墙：包含所有材质类型的栅栏和墙
            if (block.getDefaultState().isIn(BlockTags.FENCES) ||
                block.getDefaultState().isIn(BlockTags.WALLS) ||
                block.getDefaultState().isIn(BlockTags.FENCE_GATES)) {
                categories.add("pushdozer.category.fences_and_walls");
            }
            
            // 13. 按钮与压力板：目前逻辑是正确的
            if (block.getDefaultState().isIn(BlockTags.BUTTONS) ||
                block.getDefaultState().isIn(BlockTags.PRESSURE_PLATES)) {
                categories.add("pushdozer.category.buttons_and_pressure_plates");
            }
            
            // 14. 铜：目前逻辑是正确的
            if (block.getDefaultState().isIn(BlockTags.COPPER_ORES) ||
                isCopperManual(blockId)) {
                categories.add("pushdozer.category.copper");
            }
            
            // 15. 紫水晶：目前逻辑是正确的
            if (isAmethystManual(blockId)) {
                categories.add("pushdozer.category.amethyst");
            }
            
            // 16. 贵重方块：幕墙逻辑是正确的
            if (block.getDefaultState().isIn(BlockTags.BEACON_BASE_BLOCKS) ||
                isValuableManual(blockId)) {
                categories.add("pushdozer.category.valuable");
            }
            
            // 17. 红石：与红石有关的所有方块和其他可放置物品
            if (block.getDefaultState().isIn(BlockTags.REDSTONE_ORES) ||
                isRedstoneManual(blockId)) {
                categories.add("pushdozer.category.redstone");
            }
            
            // 18. 装饰
            if (isDecorativeManual(blockId)) {
                categories.add("pushdozer.category.decorative");
            }
            
            // 19. 染色方块：把所有的染色方块归纳进来，目前的逻辑中排除各种颜色的蘑菇、各种颜色郁金香、粉红色花簇
            if (block.getDefaultState().isIn(BlockTags.WOOL) ||
                block.getDefaultState().isIn(BlockTags.WOOL_CARPETS) ||
                block.getDefaultState().isIn(BlockTags.TERRACOTTA) ||
                isDyedManual(blockId)) {
                categories.add("pushdozer.category.dyed");
            }
            
            // 20. 功能性方块
            if (block.getDefaultState().isIn(BlockTags.ANVIL) ||
                block.getDefaultState().isIn(BlockTags.CAULDRONS) ||
                block.getDefaultState().isIn(BlockTags.SHULKER_BOXES) ||
                isFunctionalManual(blockId)) {
                categories.add("pushdozer.category.functional");
            }
            
            // 21. 头颅：目前的逻辑中把所有的头颅都重复了一遍
            if (isHeadManual(blockId)) {
                categories.add("pushdozer.category.heads");
            }
            
            // 22. 轨道：目前的逻辑是正确的
            if (block.getDefaultState().isIn(BlockTags.RAILS)) {
                categories.add("pushdozer.category.rails");
            }
            
            // 23. 洞穴装饰
            if (isCaveManual(blockId)) {
                categories.add("pushdozer.category.cave_decorations");
            }
            
            // 24. 流体：水和岩浆
            if (isFluidManual(blockId)) {
                categories.add("pushdozer.category.fluids");
            }
            
            // 25. 盆栽：请参考MultiSelectPlantSelectionScreen代码中关于盆栽的统计
            // 删除盆栽分类，花盆归类到装饰方块中
            
            // 26. 砖和瓦：各种砖块
            if (isBrickManual(blockId)) {
                categories.add("pushdozer.category.bricks_and_tiles");
            }
            
            // 27. 技术性方块
            if (isTechnicalManual(blockId)) {
                categories.add("pushdozer.category.technical");
            }
            
        } catch (NoSuchFieldError e) {
            // 处理缺失的标签（模组兼容旧版）
            System.err.println("Missing tag for block: " + blockId + ", using manual classification");
        } catch (RuntimeException e) {
            ExceptionPolicy.rethrowIfProgrammingError(e);
            System.err.println("Failed to check tags for block: " + blockId + ", using manual classification: " + e.getMessage());
        }
        
        // 如果没有匹配任何分类，添加到杂项并记录日志
        if (categories.isEmpty()) {
            System.out.println("Unclassified block: " + blockId);
            categories.add("pushdozer.category.miscellaneous");
        }
        
        return categories;
    }

    // 手动分类方法 - 补充标签系统
    private static boolean isTerrainManual(String blockId) {
        return blockId.equals("obsidian") || blockId.equals("bedrock") ||
               blockId.equals("mud") || blockId.equals("muddy_mangrove_roots") ||
               blockId.equals("packed_mud") || blockId.equals("dirt_path") ||
               blockId.equals("farmland") || blockId.equals("gravel") ||
               blockId.equals("snow_block") || blockId.equals("powder_snow") ||
               blockId.equals("calcite") || blockId.equals("clay") ||
               blockId.equals("deepslate") || blockId.equals("stone") ||
               blockId.equals("cobblestone") || blockId.equals("cobbled_deepslate") ||
               blockId.equals("end_stone") || blockId.equals("glowstone") ||
               blockId.equals("infested_stone") || blockId.equals("infested_deepslate") ||
               blockId.equals("infested_cobblestone") || blockId.equals("suspicious_gravel") ||
               (blockId.contains("soil") && !blockId.contains("soul"));
    }
    
    private static boolean isWoodManual(String blockId) {
        return blockId.contains("bamboo_block") || blockId.contains("stripped_bamboo") ||
               blockId.contains("hyphae") || blockId.equals("bamboo_mosaic");
    }
    
    private static boolean isPlantManual(String blockId) {
        return (blockId.contains("grass") && !blockId.equals("grass_block") && !blockId.contains("seagrass")) ||
               blockId.contains("fern") || blockId.contains("vine") ||
               blockId.contains("azalea") || blockId.contains("bush") ||
               blockId.contains("roots") || blockId.contains("dripleaf") ||
               blockId.equals("cactus") || blockId.equals("dead_bush") ||
               blockId.equals("lily_pad") || blockId.equals("spore_blossom") ||
               blockId.equals("glow_lichen") || blockId.contains("hanging_roots") ||
               blockId.contains("big_dripleaf_stem") || blockId.equals("cocoa") ||
               blockId.equals("bamboo") || blockId.equals("sea_pickle") ||
               blockId.equals("tall_seagrass") || blockId.equals("kelp_plant") ||
               blockId.equals("seagrass") || blockId.contains("chorus") ||
               blockId.equals("kelp") || blockId.contains("leaf_litter");
    }
    
    private static boolean isCropManual(String blockId) {
        // 排除下界疣、紫菘花和紫颂植株
        if (blockId.equals("nether_wart") || blockId.equals("torchflower_crop") || 
            blockId.equals("chorus_plant") || blockId.equals("chorus_flower")) {
            return false;
        }
        
        return blockId.equals("sugar_cane") || blockId.equals("pumpkin") || blockId.equals("melon") ||
               blockId.equals("pumpkin_stem") || blockId.equals("melon_stem") ||
               blockId.equals("attached_pumpkin_stem") || blockId.equals("attached_melon_stem") ||
               blockId.equals("sweet_berry_bush");
    }
    
    private static boolean isFungiManual(String blockId) {
        return blockId.equals("brown_mushroom") || blockId.equals("red_mushroom") ||
               blockId.equals("brown_mushroom_block") || blockId.equals("red_mushroom_block") ||
               blockId.equals("mushroom_stem");
    }
    
    private static boolean isCoralManual(String blockId) {
        return blockId.contains("coral") || blockId.contains("coral_block") ||
               blockId.contains("coral_fan") || blockId.contains("coral_wall_fan");
    }
    
    private static boolean isNetherManual(String blockId) {
        return blockId.equals("netherrack") || blockId.equals("magma_block") ||
               blockId.equals("soul_sand") || blockId.equals("soul_soil") ||
               blockId.equals("nether_quartz_ore") || blockId.equals("nether_gold_ore") ||
               blockId.equals("ancient_debris") || blockId.equals("basalt") ||
               blockId.equals("smooth_basalt") || blockId.contains("blackstone") ||
               blockId.equals("crying_obsidian") || blockId.equals("crimson_nylium") ||
               blockId.equals("warped_nylium") || blockId.contains("crimson_stem") ||
               blockId.contains("warped_stem") || blockId.equals("crimson_fungus") ||
               blockId.equals("warped_fungus") || blockId.equals("crimson_roots") ||
               blockId.equals("warped_roots") || blockId.equals("nether_wart_block") ||
               blockId.equals("warped_wart_block") || blockId.equals("weeping_vines") ||
               blockId.equals("twisting_vines") || blockId.equals("shroomlight") ||
               blockId.equals("nether_sprouts") || blockId.contains("nether_brick") ||
               blockId.contains("red_nether_brick") || blockId.equals("blaze_spawner") ||
               blockId.equals("magma_cube_spawner") || blockId.equals("respawn_anchor") ||
               blockId.equals("lodestone");
    }
    
    private static boolean isCopperManual(String blockId) {
        return blockId.contains("copper") && !blockId.contains("copper_ore");
    }
    
    private static boolean isAmethystManual(String blockId) {
        return blockId.contains("amethyst");
    }
    
    private static boolean isValuableManual(String blockId) {
        return blockId.endsWith("_block") && 
               (blockId.contains("gold") || blockId.contains("iron") || blockId.contains("diamond") || 
                blockId.contains("emerald") || blockId.contains("netherite") || blockId.contains("lapis"));
    }
    
    private static boolean isRedstoneManual(String blockId) {
        return blockId.contains("redstone") || blockId.contains("piston") ||
               blockId.contains("dispenser") || blockId.contains("observer") ||
               blockId.contains("hopper") || blockId.contains("comparator") ||
               blockId.contains("repeater") || blockId.equals("tnt") ||
               blockId.contains("target") || blockId.contains("note_block") ||
               blockId.contains("jukebox") || blockId.contains("daylight_detector");
    }
    
    private static boolean isDecorativeManual(String blockId) {
        return (blockId.contains("torch") && !blockId.contains("torchflower")) ||
               blockId.contains("lantern") || blockId.contains("frame") || 
               blockId.contains("bell") || blockId.equals("scaffolding") || 
               blockId.equals("chain") || blockId.equals("campfire") || 
               blockId.equals("soul_campfire") || blockId.equals("sea_lantern") || 
               blockId.equals("conduit") || blockId.equals("end_rod") || 
               blockId.equals("beacon") || blockId.equals("decorated_pot") || 
               blockId.equals("painting") || blockId.equals("carved_pumpkin") || 
               blockId.equals("cake") || blockId.equals("iron_bars") || 
               blockId.equals("ladder") || blockId.equals("tripwire_hook") || 
               blockId.equals("lever") || blockId.equals("lightning_rod") || 
               blockId.equals("dried_kelp_block") || blockId.equals("hay_bale") ||
               blockId.startsWith("potted_") || blockId.contains("flower_pot");
    }
    
    private static boolean isDyedManual(String blockId) {
        // 明确排除基岩
        if (blockId.equals("bedrock")) {
            return false;
        }
        
        // 玻璃和染色玻璃
        if (blockId.contains("glass")) {
            return true;
        }
        
        // 混凝土和混凝土粉末
        if (blockId.contains("concrete")) {
            return true;
        }
        
        // 旗帜、蜡烛、床
        if (blockId.contains("banner") || blockId.contains("candle") || blockId.contains("bed")) {
            return true;
        }
        
        String[] colors = {"white", "orange", "magenta", "light_blue", "yellow", 
                          "lime", "pink", "gray", "light_gray", "cyan", 
                          "purple", "blue", "brown", "green", "red", "black"};
        
        for (String color : colors) {
            if (blockId.startsWith(color + "_")) {
                // 排除各种颜色的蘑菇、各种颜色郁金香、粉红色花簇、兰花
                if (!blockId.contains("mushroom") && !blockId.contains("tulip") && 
                    !blockId.equals("pink_petals") && !blockId.contains("orchid")) {
                    return true;
                }
            }
        }
        return false;
    }
    
    private static boolean isFunctionalManual(String blockId) {
        return blockId.contains("table") || blockId.contains("chest") ||
               blockId.contains("sign") || blockId.contains("anvil") ||
               blockId.contains("barrel") || blockId.contains("furnace") ||
               blockId.contains("smoker") || blockId.contains("grindstone") ||
               blockId.contains("loom") || blockId.contains("stonecutter") ||
               blockId.contains("lectern") || blockId.contains("composter") ||
               blockId.contains("cauldron") || blockId.contains("brewing_stand") ||
               blockId.contains("enchanting_table") || blockId.contains("ender_chest") ||
               blockId.contains("shulker_box") || blockId.contains("crafting_table") ||
               blockId.contains("bookshelf") || blockId.contains("jukebox") ||
               blockId.contains("note_block") || blockId.equals("crafter") ||
               blockId.equals("dropper") || blockId.equals("heavy_core") ||
               blockId.equals("spawner") || blockId.equals("trial_spawner") ||
               blockId.equals("vault");
    }
    
    private static boolean isHeadManual(String blockId) {
        // 包含所有头颅方块，包括地面变体和墙面变体
        return blockId.endsWith("_head") || blockId.endsWith("_skull");
    }
    
    private static boolean isCaveManual(String blockId) {
        return blockId.contains("dripstone") || 
               (blockId.contains("spore") && !blockId.equals("spore_blossom")) ||
               blockId.contains("lichen") || blockId.contains("moss") ||
               blockId.contains("sculk");
    }
    
    private static boolean isFluidManual(String blockId) {
        return blockId.equals("water") || blockId.equals("lava");
    }
    
  
    
    private static boolean isBrickManual(String blockId) {
        return blockId.contains("brick") || blockId.contains("tile") ||
               blockId.contains("sandstone") || blockId.contains("prismarine") ||
               blockId.contains("quartz") || blockId.equals("smooth_stone") ||
               blockId.equals("smooth_quartz") || blockId.equals("smooth_sandstone") ||
               blockId.equals("smooth_red_sandstone") || blockId.equals("cut_sandstone") ||
               blockId.equals("cut_red_sandstone") || blockId.equals("chiseled_sandstone") ||
               blockId.equals("chiseled_red_sandstone") || blockId.equals("chiseled_quartz_block") ||
               blockId.equals("quartz_pillar") || blockId.contains("terracotta") ||
               blockId.equals("chiseled_deepslate") || blockId.equals("chiseled_polished_blackstone") ||
               blockId.equals("chiseled_tuff") || blockId.equals("polished_andesite") ||
               blockId.equals("polished_basalt") || blockId.equals("polished_blackstone") ||
               blockId.equals("polished_deepslate") || blockId.equals("polished_diorite") ||
               blockId.equals("polished_granite") || blockId.equals("polished_tuff") ||
               blockId.equals("purpur_block") || blockId.equals("purpur_pillar") ||
               blockId.equals("reinforced_deepslate");
    }
    
    private static boolean isTechnicalManual(String blockId) {
        return blockId.equals("barrier") || blockId.equals("command_block") ||
               blockId.equals("repeating_command_block") || blockId.equals("chain_command_block") ||
               blockId.equals("structure_block") || blockId.equals("jigsaw") ||
               blockId.equals("light") || blockId.equals("fire") ||
               blockId.equals("soul_fire") || blockId.equals("structure_void") ||
               blockId.equals("test_block") || blockId.equals("test_instance") ||
               blockId.equals("test_example") || blockId.equals("test_sample") ||
               blockId.equals("test_case") || blockId.equals("test_unit");
    }
}
