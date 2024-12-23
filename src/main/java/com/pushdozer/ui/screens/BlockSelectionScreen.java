package com.pushdozer.ui.screens;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import com.pushdozer.config.PushdozerConfig;

import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.render.item.ItemRenderer;
import net.minecraft.item.Items;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

public class BlockSelectionScreen extends Screen {
    private final PushdozerConfigScreen parent;
    private final PushdozerConfig config;
    private final List<BlockCategory> blockCategories;
    private final List<Block> selectedBlocks;
    private int currentPage = 0;
    private String searchText = "";
    private TextFieldWidget searchBox;
    private static final int BUTTON_HEIGHT = 20;
    private static final int BUTTON_WIDTH = 80;
    private static final int BUTTON_SPACING = 10;
    private static final int SEARCH_WIDTH = BUTTON_WIDTH * 3 + BUTTON_SPACING * 2;
    private static final int CATEGORIES_PER_PAGE = 4;
    private static final int CATEGORY_BUTTON_HEIGHT = 20;
    private static final int BLOCK_SIZE = 20;
    private static final int SPACING = 4;

    public BlockSelectionScreen(PushdozerConfigScreen parent, PushdozerConfig config) {
        super(Text.translatable("pushdozer.screen.block_selection.title"));
        this.parent = parent;
        this.config = config;
        this.blockCategories = getAllRelevantBlocks();
        this.selectedBlocks = new ArrayList<>(config.getBreakableBlocks());
    }

    @Override
    protected void init() {
        super.init();
        // Initialize search box
        int totalWidth = BUTTON_WIDTH * 3 + BUTTON_SPACING * 2;
        int startX = (width - totalWidth) / 2;
        searchBox = new TextFieldWidget(textRenderer, startX, height - 60, SEARCH_WIDTH, BUTTON_HEIGHT, Text.empty());
        searchBox.setMaxLength(50);
        searchBox.setDrawsBackground(true);
        searchBox.setVisible(true);
        searchBox.setEditable(true);
        searchBox.setText(""); // 清空初始文本
        searchBox.setChangedListener(this::onSearchTextChanged);
        addDrawableChild(searchBox);
        
        addPageButtons();
        addSelectionButtons();
        refreshCategoryButtons();
    }

    @Override
    public void tick() {
        super.tick();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        boolean result = super.mouseClicked(mouseX, mouseY, button);
        if (searchBox != null) {
            if (searchBox.isMouseOver(mouseX, mouseY)) {
                searchBox.setFocused(true);
                return true;
            } else {
                searchBox.setFocused(false);
            }
        }
        return result;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (searchBox != null && searchBox.isFocused()) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                searchBox.setFocused(false);
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_ENTER) {
                focusOnMatch();
                return true;
            }
            if (searchBox.keyPressed(keyCode, scanCode, modifiers)) {
                return true;
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        if (searchBox != null && searchBox.isFocused()) {
            if (searchBox.charTyped(chr, modifiers)) {
                return true;
            }
        }
        return super.charTyped(chr, modifiers);
    }

    private void focusOnMatch() {
        if (searchText.isEmpty()) {
            return;
        }

        // 遍历所有类别和方块
        for (int i = 0; i < blockCategories.size(); i++) {
            BlockCategory category = blockCategories.get(i);
            for (Block block : category.blocks) {
                // 获取方块的本地化名称和ID
                String blockName = Text.translatable(block.getTranslationKey()).getString().toLowerCase();
                String blockId = block.getTranslationKey().toLowerCase();
                
                // 检查是否匹配
                if (blockName.contains(searchText.toLowerCase()) || blockId.contains(searchText.toLowerCase())) {
                    // 计算并切换到包含该方块的页面
                    int targetPage = i / CATEGORIES_PER_PAGE;
                    if (currentPage != targetPage) {
                        currentPage = targetPage;
                        refreshCategoryButtons();
                    }
                    return;
                }
            }
        }
    }

    private void onSearchTextChanged(String text) {
        if (!text.equals(searchText)) {
            searchText = text;
            refreshCategoryButtons();
        }
    }

    private boolean matchesSearch(Block block) {
        if (searchText.isEmpty()) {
            return true;
        }
        // 获取方块的本地化名称和ID
        String blockName = Text.translatable(block.getTranslationKey()).getString().toLowerCase();
        String blockId = block.getTranslationKey().toLowerCase();
        String searchLower = searchText.toLowerCase();
        return blockName.contains(searchLower) || blockId.contains(searchLower);
    }

    private void addPageButtons() {
        addDrawableChild(ButtonWidget.builder(Text.literal("<"), button -> changePage(-1))
                .dimensions(10, height / 2, 20, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal(">"), button -> changePage(1))
                .dimensions(width - 30, height / 2, 20, 20).build());
    }

    private void changePage(int delta) {
        currentPage += delta;
        currentPage = Math.max(0, Math.min(currentPage, (blockCategories.size() - 1) / CATEGORIES_PER_PAGE));
        refreshCategoryButtons();
    }

    private void refreshCategoryButtons() {
        String currentText = searchBox != null ? searchBox.getText() : "";
        boolean wasFocused = searchBox != null && searchBox.isFocused();
        
        clearChildren();
        
        // 重新添加搜索框，与底部按钮对齐
        int totalWidth = BUTTON_WIDTH * 3 + BUTTON_SPACING * 2;
        int startX = (width - totalWidth) / 2;
        searchBox = new TextFieldWidget(textRenderer, startX, height - 60, SEARCH_WIDTH, BUTTON_HEIGHT, Text.empty());
        searchBox.setMaxLength(50);
        searchBox.setDrawsBackground(true);
        searchBox.setVisible(true);
        searchBox.setEditable(true);
        searchBox.setText(currentText);
        searchBox.setFocused(wasFocused);
        searchBox.setChangedListener(this::onSearchTextChanged);
        addDrawableChild(searchBox);

        addPageButtons();
        addSelectionButtons();
        int categorySpacing = 8;
        int blockSpacing = 4;
        int categoryToBlockSpacing = 10;
        int scrollBarWidth = 4;

        int categoryWidth = BLOCK_SIZE * 4 + blockSpacing * 3 + scrollBarWidth;
        int totalWidth2 = CATEGORIES_PER_PAGE * categoryWidth + (CATEGORIES_PER_PAGE - 1) * categorySpacing;
        int startX2 = (width - totalWidth2) / 2;
        int startY = 25;

        int startIndex = currentPage * CATEGORIES_PER_PAGE;
        for (int i = 0; i < CATEGORIES_PER_PAGE && startIndex + i < blockCategories.size(); i++) {
            BlockCategory category = blockCategories.get(startIndex + i);
            int columnX = startX2 + i * (categoryWidth + categorySpacing);

            addDrawableChild(ButtonWidget.builder(
                category.getTranslatedName(),
                button -> toggleCategorySelection(category))
                .dimensions(columnX, startY, categoryWidth, CATEGORY_BUTTON_HEIGHT)
                .build());

            List<Block> filteredBlocks = category.blocks.stream()
                .filter(this::matchesSearch)
                .collect(Collectors.toList());

            int panelHeight = height - startY - CATEGORY_BUTTON_HEIGHT - categoryToBlockSpacing - 80;
            ScrollablePanel scrollPanel = new ScrollablePanel(
                    columnX,
                    startY + CATEGORY_BUTTON_HEIGHT + categoryToBlockSpacing,
                    categoryWidth,
                    panelHeight,
                    filteredBlocks
            );

            addDrawableChild(scrollPanel);
        }
    }

    private void addSelectionButtons() {
        int y = height - BUTTON_HEIGHT - 10;
        int totalWidth = BUTTON_WIDTH * 3 + BUTTON_SPACING * 2;
        int startX = (width - totalWidth) / 2;

        // "Select All" button
        addDrawableChild(ButtonWidget.builder(Text.translatable("pushdozer.screen.block_selection.select_all"), 
            button -> selectAll())
            .dimensions(startX, y, BUTTON_WIDTH, BUTTON_HEIGHT).build());

        // "Deselect All" button
        addDrawableChild(ButtonWidget.builder(Text.translatable("pushdozer.screen.block_selection.deselect_all"), 
            button -> deselectAll())
            .dimensions(startX + BUTTON_WIDTH + BUTTON_SPACING, y, BUTTON_WIDTH, BUTTON_HEIGHT).build());

        // "Confirm" button
        addDrawableChild(ButtonWidget.builder(Text.translatable("pushdozer.screen.block_selection.confirm"), 
            button -> confirmSelection())
            .dimensions(startX + (BUTTON_WIDTH + BUTTON_SPACING) * 2, y, BUTTON_WIDTH, BUTTON_HEIGHT).build());
    }

    private String getPageText() {
        int totalPages = (blockCategories.size() - 1) / CATEGORIES_PER_PAGE + 1;
        return String.format("%d/%d", currentPage + 1, totalPages);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackground(context, mouseX, mouseY, delta);
        
        // 渲染搜索框占位符
        if (searchBox.getText().isEmpty() && !searchBox.isFocused()) {
            context.drawTextWithShadow(textRenderer, 
                Text.translatable("pushdozer.screen.block_selection.search"), 
                searchBox.getX() + 4, 
                searchBox.getY() + (searchBox.getHeight() - 8) / 2, 
                0xFF808080);
        }

        super.render(context, mouseX, mouseY, delta);
        context.drawCenteredTextWithShadow(textRenderer, getTitle(), width / 2, 10, 0xFFFFFF);

        // 将页码文字移到搜索框上方
        String pageText = getPageText();
        context.drawCenteredTextWithShadow(textRenderer, Text.literal(pageText), width / 2, height - 75, 0xFFFFFF);

        // 渲染悬停提示
        for (Element child : children()) {
            if (child instanceof BlockButtonWidget blockButton && blockButton.isMouseOver(mouseX, mouseY)) {
                renderTooltip(context, blockButton.getBlockName(), mouseX, mouseY);
                break;
            }
        }
    }

    private void renderTooltip(DrawContext context, Text text, int x, int y) {
        context.drawTooltip(textRenderer, text, x, y);
    }

    private void toggleBlockSelection(Block block) {
        if (selectedBlocks.contains(block)) {
            selectedBlocks.remove(block);
        } else {
            selectedBlocks.add(block);
        }
    }

    private void toggleCategorySelection(BlockCategory category) {
        boolean allSelected = category.blocks.stream().allMatch(selectedBlocks::contains);
        if (allSelected) {
            selectedBlocks.removeAll(category.blocks);
        } else {
            selectedBlocks.addAll(category.blocks);
        }
        refreshScreen();
    }

    private void selectAll() {
        selectedBlocks.clear();
        for (BlockCategory category : blockCategories) {
            selectedBlocks.addAll(category.blocks);
        }
        refreshScreen();
    }

    private void deselectAll() {
        selectedBlocks.clear();
        refreshScreen();
    }

    private void confirmSelection() {
        config.setBreakableBlocks(selectedBlocks);
        MinecraftClient.getInstance().setScreen(parent);
    }

    private void refreshScreen() {
        clearChildren();
        init();
    }

    // 内部类：BlockButtonWidget
    private class BlockButtonWidget extends ButtonWidget {
        private final Block block;

        public BlockButtonWidget(int x, int y, int width, int height, Block block, PressAction onPress) {
            super(x, y, width, height, Text.empty(), onPress, DEFAULT_NARRATION_SUPPLIER);
            this.block = block;
        }

        @Override
        public void renderWidget(DrawContext context, int mouseX, int mouseY, float delta) {
            super.renderWidget(context, mouseX, mouseY, delta);
            ItemRenderer itemRenderer = MinecraftClient.getInstance().getItemRenderer();
            
            // 特殊处理流体方块
            if (block == Blocks.WATER) {
                // 使用水桶作为水的图标
                context.drawItem(Items.WATER_BUCKET.getDefaultStack(), getX() + 2, getY() + 2);
            } else if (block == Blocks.LAVA) {
                // 使用岩浆桶作为岩浆的图标
                context.drawItem(Items.LAVA_BUCKET.getDefaultStack(), getX() + 2, getY() + 2);
            } else {
                // 其他方块正常显示
                context.drawItem(block.asItem().getDefaultStack(), getX() + 2, getY() + 2);
            }
            
            if (selectedBlocks.contains(block)) {
                // 绘制半透明白色背景
                context.fill(getX(), getY(), getX() + getWidth(), getY() + getHeight(), 0x80FFFFFF);
                // 绘制勾选标记
                context.drawText(textRenderer, Text.literal("☑"), getX() + getWidth() - 8, getY() + getHeight() - 9, 0xFF000000, false);
            }
        }

        public Text getBlockName() {
            return block.getName();
        }

        @Override
        public boolean isMouseOver(double mouseX, double mouseY) {
            return this.visible && mouseX >= this.getX() && mouseY >= this.getY() && mouseX < this.getX() + this.width && mouseY < this.getY() + this.height;
        }
    }

    // 修改 ScrollablePanel 类
    private class ScrollablePanel extends ButtonWidget {
        private final List<Block> blocks;
        private int scrollOffset = 0;
        private static final int SCROLL_BAR_WIDTH = 4; // 将滚动条宽度改为4像素
        private static final int SCROLL_BAR_MARGIN = 0; // 移除边距，以便滚动条能够完全填充4像素的宽度

        private Block hoveredBlock;
        private int hoveredX;
        private int hoveredY;

        public ScrollablePanel(int x, int y, int width, int height, List<Block> blocks) {
            super(x, y, width, height, Text.empty(), button -> {}, DEFAULT_NARRATION_SUPPLIER);
            this.blocks = blocks;
        }

        @Override
        public void renderWidget(DrawContext context, int mouseX, int mouseY, float delta) {
            int blockSpacing = 4;
            int visibleRows = 6; // 固定显示6行
            int totalRows = (blocks.size() + 3) / 4;

            hoveredBlock = null;

            // 绘制半透明背景
            int backgroundWidth = 4 * BLOCK_SIZE + 3 * blockSpacing;
            int backgroundHeight = 6 * (BLOCK_SIZE + blockSpacing) - blockSpacing; // 调整背景高度
            context.fill(getX(), getY(), getX() + backgroundWidth, getY() + backgroundHeight, 0x40000000);

            context.enableScissor(getX(), getY(), getX() + width, getY() + height);
            for (int i = 0; i < blocks.size(); i++) {
                int row = i / 4;
                int col = i % 4;
                if (row >= scrollOffset / (BLOCK_SIZE + blockSpacing) && row < scrollOffset / (BLOCK_SIZE + blockSpacing) + visibleRows) {
                    int blockX = getX() + col * (BLOCK_SIZE + blockSpacing);
                    int blockY = getY() + (row - scrollOffset / (BLOCK_SIZE + blockSpacing)) * (BLOCK_SIZE + blockSpacing);
                    renderBlockButton(context, blocks.get(i), blockX, blockY, mouseX, mouseY);
                    
                    if (mouseX >= blockX && mouseX < blockX + BLOCK_SIZE && mouseY >= blockY && mouseY < blockY + BLOCK_SIZE) {
                        hoveredBlock = blocks.get(i);
                        hoveredX = mouseX;
                        hoveredY = mouseY;
                    }
                }
            }
            context.disableScissor();

            // 始终绘制滚动条背景槽
            context.fill(getX() + width - SCROLL_BAR_WIDTH, getY(), getX() + width, getY() + backgroundHeight, 0x33FFFFFF);

            // 只有在需要滚动时才绘制滚动条
            if (totalRows > visibleRows) {
                int scrollBarHeight = Math.max(20, backgroundHeight * visibleRows / totalRows);
                int scrollBarY = getY() + (int) ((backgroundHeight - scrollBarHeight) * (float) scrollOffset / ((totalRows - visibleRows) * (BLOCK_SIZE + blockSpacing)));
                
                // 绘制滚动条
                drawScrollBar(context, getX() + width - SCROLL_BAR_WIDTH, scrollBarY, SCROLL_BAR_WIDTH, scrollBarHeight);
            }

            // 在最后渲染工具提示
            if (hoveredBlock != null) {
                context.drawTooltip(textRenderer, hoveredBlock.getName(), hoveredX, hoveredY);
            }
        }

        private void renderBlockButton(DrawContext context, Block block, int x, int y, int mouseX, int mouseY) {
            ItemRenderer itemRenderer = MinecraftClient.getInstance().getItemRenderer();
            
            // 特殊处理流体方块和水生植物
            if (block == Blocks.WATER) {
                // 使用水桶作为水的图标
                context.drawItem(Items.WATER_BUCKET.getDefaultStack(), x, y);
            } else if (block == Blocks.LAVA) {
                // 使用岩浆桶作为岩浆的图标
                context.drawItem(Items.LAVA_BUCKET.getDefaultStack(), x, y);
            } else if (block == Blocks.TALL_SEAGRASS) {
                // 使用海草作为高的海草的图标
                context.drawItem(Items.SEAGRASS.getDefaultStack(), x, y);
            } else if (block == Blocks.KELP_PLANT) {
                // 使用海带作为海带植株的图标
                context.drawItem(Items.KELP.getDefaultStack(), x, y);
            } else if (block == Blocks.WEEPING_VINES_PLANT) {
                // 使用垂泪藤的物品作为植株的图标
                context.drawItem(Items.WEEPING_VINES.getDefaultStack(), x, y);
            } else if (block == Blocks.TWISTING_VINES_PLANT) {
                // 使用缠怨藤的物品作为植株的图标
                context.drawItem(Items.TWISTING_VINES.getDefaultStack(), x, y);
            } else {
                // 其他方块正常显示
                context.drawItem(block.asItem().getDefaultStack(), x, y);
            }
            
            if (selectedBlocks.contains(block)) {
                context.fill(x, y, x + BLOCK_SIZE, y + BLOCK_SIZE, 0x80FFFFFF);
                context.drawText(textRenderer, Text.literal("☑"), x + BLOCK_SIZE - 8, y + BLOCK_SIZE - 9, 0xFF000000, false);
            }
        }

        @Override
        public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
            if (isMouseOver(mouseX, mouseY)) {
                int blockSpacing = 4;
                int totalRows = (blocks.size() + 3) / 4;
                int visibleRows = height / (BLOCK_SIZE + blockSpacing);
                int maxScroll = (totalRows - visibleRows) * (BLOCK_SIZE + blockSpacing);
                scrollOffset = Math.max(0, Math.min(scrollOffset - (int)(verticalAmount * 10), maxScroll));
                return true;
            }
            return false;
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            if (isMouseOver(mouseX, mouseY)) {
                int blockSpacing = 4;
                int visibleRows = height / (BLOCK_SIZE + blockSpacing);
                int startRow = scrollOffset / (BLOCK_SIZE + blockSpacing);
                for (int i = startRow * 4; i < Math.min(blocks.size(), (startRow + visibleRows) * 4); i++) {
                    int row = i / 4 - startRow;
                    int col = i % 4;
                    int blockX = getX() + col * (BLOCK_SIZE + blockSpacing);
                    int blockY = getY() + row * (BLOCK_SIZE + blockSpacing);
                    if (mouseX >= blockX && mouseX < blockX + BLOCK_SIZE && mouseY >= blockY && mouseY < blockY + BLOCK_SIZE) {
                        toggleBlockSelection(blocks.get(i));
                        return true;
                    }
                }
            }
            return false;
        }

        private void drawScrollBar(DrawContext context, int x, int y, int width, int height) {
            int color = 0xFFAAAAAA;
            int highlightColor = 0xFFFFFFFF;
            int shadowColor = 0xFF555555;

            // 绘制主体
            context.fill(x, y, x + width, y + height, color);

            // 绘制高光边
            context.fill(x, y, x + width - 1, y + 1, highlightColor);
            context.fill(x, y, x + 1, y + height - 1, highlightColor);

            // 绘制阴影边
            context.fill(x + width - 1, y, x + width, y + height, shadowColor);
            context.fill(x, y + height - 1, x + width, y + height, shadowColor);
        }
    }

    // 修改 BlockCategory 类
    private static class BlockCategory {
        String translationKey;  // 改名以更好地表达这是翻译键
        List<Block> blocks;

        BlockCategory(String translationKey) {
            this.translationKey = translationKey;
            this.blocks = new ArrayList<>();
        }

        // 添加获取翻译后文本的方法
        public Text getTranslatedName() {
            return Text.translatable(translationKey);
        }
    }

    private List<BlockCategory> getAllRelevantBlocks() {
        List<BlockCategory> categories = new ArrayList<>();

        BlockCategory terrain = new BlockCategory("pushdozer.category.terrain");
        terrain.blocks.addAll(Arrays.asList(
                Blocks.GRASS_BLOCK, Blocks.DIRT, Blocks.COARSE_DIRT, Blocks.PODZOL, Blocks.MYCELIUM,
                Blocks.ROOTED_DIRT, Blocks.MUD, Blocks.MUDDY_MANGROVE_ROOTS, Blocks.STONE, Blocks.GRANITE,
                Blocks.DIORITE, Blocks.ANDESITE, Blocks.DEEPSLATE, Blocks.TUFF, Blocks.CALCITE,
                Blocks.DRIPSTONE_BLOCK, Blocks.MOSS_BLOCK, Blocks.SUSPICIOUS_GRAVEL, Blocks.SUSPICIOUS_SAND,
                Blocks.GRAVEL, Blocks.CLAY, Blocks.PACKED_MUD, Blocks.MUD_BRICKS, 
                Blocks.SCULK, Blocks.SCULK_VEIN, Blocks.SCULK_CATALYST, Blocks.SCULK_SHRIEKER, Blocks.SCULK_SENSOR,
                Blocks.OCHRE_FROGLIGHT, Blocks.VERDANT_FROGLIGHT, Blocks.PEARLESCENT_FROGLIGHT,
                Blocks.COBBLESTONE,          // 原石
                Blocks.MOSSY_COBBLESTONE,    // 苔石
                Blocks.OBSIDIAN,             // 黑曜石
                Blocks.CRYING_OBSIDIAN,      // 哭泣的黑曜石
                Blocks.BASALT,               // 玄武岩
                Blocks.SMOOTH_BASALT,        // 平滑玄武岩
                Blocks.POLISHED_BASALT,      // 磨制玄武岩
                Blocks.TUFF_BRICKS,          // 凝灰岩砖
                Blocks.CHISELED_TUFF_BRICKS, // 雕文凝灰岩砖
                Blocks.TUFF_BRICK_STAIRS,    // 凝灰岩砖楼梯
                Blocks.TUFF_BRICK_SLAB,      // 凝灰岩砖台阶
                Blocks.TUFF_BRICK_WALL,      // 凝灰岩砖墙
                Blocks.INFESTED_STONE,           // 虫蚀石头
                Blocks.INFESTED_DEEPSLATE,       // 虫蚀深板岩
                Blocks.INFESTED_COBBLESTONE,     // 虫蚀圆石
                Blocks.INFESTED_STONE_BRICKS,    // 虫蚀石砖
                Blocks.INFESTED_MOSSY_STONE_BRICKS,  // 虫蚀苔石砖
                Blocks.INFESTED_CRACKED_STONE_BRICKS, // 虫蚀裂纹石砖
                Blocks.INFESTED_CHISELED_STONE_BRICKS, // 虫蚀錾制石砖
                Blocks.COBBLED_DEEPSLATE,        // 深板岩圆石
                Blocks.POLISHED_TUFF,            // 磨制凝灰岩
                Blocks.DIRT_PATH,                // 土径
                Blocks.POINTED_DRIPSTONE,        // 添加尖锐的滴水石
                Blocks.SUSPICIOUS_GRAVEL,        // 添加可疑的沙砾
                Blocks.CALIBRATED_SCULK_SENSOR,  // 添加校准过的幽匿感测体
                Blocks.TRIAL_SPAWNER,            // 添加试炼刷怪笼
                Blocks.CRAFTER,                  // 添加工匠站
                Blocks.CHISELED_COPPER,          // 添加錾制铜块
                Blocks.EXPOSED_CHISELED_COPPER,  // 添加斑驳的錾制铜块
                Blocks.WEATHERED_CHISELED_COPPER, // 添加锈蚀的錾制铜块
                Blocks.OXIDIZED_CHISELED_COPPER,  // 添加氧化的錾制铜块
                Blocks.WAXED_CHISELED_COPPER,     // 添加涂蜡的錾制铜块
                Blocks.WAXED_EXPOSED_CHISELED_COPPER,  // 添加涂蜡的斑驳錾制铜块
                Blocks.WAXED_WEATHERED_CHISELED_COPPER, // 添加涂蜡的锈蚀錾制铜块
                Blocks.WAXED_OXIDIZED_CHISELED_COPPER   // 添加涂蜡的氧化錾制铜块
        ));
        categories.add(terrain);

        BlockCategory sand = new BlockCategory("pushdozer.category.sand_and_gravel");
        sand.blocks.addAll(Arrays.asList(
                Blocks.SAND, Blocks.RED_SAND, Blocks.SOUL_SAND, Blocks.SOUL_SOIL,
                Blocks.SUSPICIOUS_SAND  // 添加可疑的沙子
        ));
        categories.add(sand);

        BlockCategory snow = new BlockCategory("pushdozer.category.snow_and_ice");
        snow.blocks.addAll(Arrays.asList(
                Blocks.SNOW,                // 雪
                Blocks.SNOW_BLOCK,         // 雪块
                Blocks.ICE,                // 冰
                Blocks.PACKED_ICE,         // 浮冰
                Blocks.BLUE_ICE            // 蓝冰
        ));
        categories.add(snow);

        BlockCategory clay = new BlockCategory("pushdozer.category.clay_and_terracotta");
        clay.blocks.addAll(Arrays.asList(
                Blocks.CLAY, Blocks.TERRACOTTA, Blocks.WHITE_TERRACOTTA, Blocks.ORANGE_TERRACOTTA,
                Blocks.MAGENTA_TERRACOTTA, Blocks.LIGHT_BLUE_TERRACOTTA, Blocks.YELLOW_TERRACOTTA,
                Blocks.LIME_TERRACOTTA, Blocks.PINK_TERRACOTTA, Blocks.GRAY_TERRACOTTA,
                Blocks.LIGHT_GRAY_TERRACOTTA, Blocks.CYAN_TERRACOTTA, Blocks.PURPLE_TERRACOTTA,
                Blocks.BLUE_TERRACOTTA, Blocks.BROWN_TERRACOTTA, Blocks.GREEN_TERRACOTTA,
                Blocks.RED_TERRACOTTA, Blocks.BLACK_TERRACOTTA,
                Blocks.RED_GLAZED_TERRACOTTA,
                Blocks.WHITE_GLAZED_TERRACOTTA,
                Blocks.ORANGE_GLAZED_TERRACOTTA,
                Blocks.MAGENTA_GLAZED_TERRACOTTA,
                Blocks.LIGHT_BLUE_GLAZED_TERRACOTTA,
                Blocks.YELLOW_GLAZED_TERRACOTTA,
                Blocks.LIME_GLAZED_TERRACOTTA,
                Blocks.PINK_GLAZED_TERRACOTTA,
                Blocks.GRAY_GLAZED_TERRACOTTA,
                Blocks.LIGHT_GRAY_GLAZED_TERRACOTTA,
                Blocks.CYAN_GLAZED_TERRACOTTA,
                Blocks.PURPLE_GLAZED_TERRACOTTA,
                Blocks.BLUE_GLAZED_TERRACOTTA,
                Blocks.BROWN_GLAZED_TERRACOTTA,
                Blocks.GREEN_GLAZED_TERRACOTTA,
                Blocks.RED_GLAZED_TERRACOTTA,
                Blocks.BLACK_GLAZED_TERRACOTTA
        ));
        categories.add(clay);

        BlockCategory nether = new BlockCategory("pushdozer.category.nether");
        nether.blocks.addAll(Arrays.asList(
                Blocks.NETHERRACK, Blocks.BASALT, Blocks.BLACKSTONE, Blocks.CRIMSON_NYLIUM,
                Blocks.WARPED_NYLIUM, Blocks.NETHER_WART_BLOCK, Blocks.WARPED_WART_BLOCK,
                Blocks.MAGMA_BLOCK, Blocks.GLOWSTONE, Blocks.SHROOMLIGHT, Blocks.ANCIENT_DEBRIS, Blocks.NETHERITE_BLOCK,
                Blocks.GILDED_BLACKSTONE, Blocks.CHISELED_POLISHED_BLACKSTONE,
                Blocks.POLISHED_BLACKSTONE, Blocks.POLISHED_BLACKSTONE_BRICKS,
                Blocks.CRACKED_POLISHED_BLACKSTONE_BRICKS,
                Blocks.POLISHED_BLACKSTONE_BRICK_SLAB, Blocks.POLISHED_BLACKSTONE_BRICK_STAIRS
        ));
        categories.add(nether);

        BlockCategory end = new BlockCategory("pushdozer.category.end");
        end.blocks.addAll(Arrays.asList(
                Blocks.END_STONE, Blocks.END_STONE_BRICKS, Blocks.PURPUR_BLOCK, Blocks.PURPUR_PILLAR,
                Blocks.CHORUS_PLANT, Blocks.CHORUS_FLOWER
        ));
        categories.add(end);

        BlockCategory ores = new BlockCategory("pushdozer.category.ores");
        ores.blocks.addAll(Arrays.asList(
                Blocks.COAL_ORE, Blocks.IRON_ORE, Blocks.GOLD_ORE, Blocks.REDSTONE_ORE,
                Blocks.DIAMOND_ORE, Blocks.LAPIS_ORE, Blocks.EMERALD_ORE, Blocks.COPPER_ORE,
                Blocks.DEEPSLATE_COAL_ORE, Blocks.DEEPSLATE_IRON_ORE, Blocks.DEEPSLATE_GOLD_ORE,
                Blocks.DEEPSLATE_REDSTONE_ORE, Blocks.DEEPSLATE_DIAMOND_ORE, Blocks.DEEPSLATE_LAPIS_ORE,
                Blocks.DEEPSLATE_EMERALD_ORE, Blocks.DEEPSLATE_COPPER_ORE,
                Blocks.NETHER_GOLD_ORE, Blocks.NETHER_QUARTZ_ORE, // 添加下界金矿和下界石英矿
                Blocks.ANCIENT_DEBRIS // 添加远古残骸
        ));
        categories.add(ores);

        BlockCategory leaves = new BlockCategory("pushdozer.category.leaves");
        leaves.blocks.addAll(Arrays.asList(
                Blocks.OAK_LEAVES, Blocks.SPRUCE_LEAVES, Blocks.BIRCH_LEAVES, Blocks.JUNGLE_LEAVES,
                Blocks.ACACIA_LEAVES, Blocks.DARK_OAK_LEAVES, Blocks.MANGROVE_LEAVES, Blocks.CHERRY_LEAVES,
                Blocks.AZALEA_LEAVES, Blocks.FLOWERING_AZALEA_LEAVES
        ));
        categories.add(leaves);

        BlockCategory wood = new BlockCategory("pushdozer.category.wood");
        wood.blocks.addAll(Arrays.asList(
                // 原木
                Blocks.OAK_LOG, Blocks.SPRUCE_LOG, Blocks.BIRCH_LOG, Blocks.JUNGLE_LOG,
                Blocks.ACACIA_LOG, Blocks.DARK_OAK_LOG, Blocks.MANGROVE_LOG, Blocks.CHERRY_LOG,
                // 去皮原木
                Blocks.STRIPPED_OAK_LOG, Blocks.STRIPPED_SPRUCE_LOG, Blocks.STRIPPED_BIRCH_LOG, 
                Blocks.STRIPPED_JUNGLE_LOG, Blocks.STRIPPED_ACACIA_LOG, Blocks.STRIPPED_DARK_OAK_LOG,
                Blocks.STRIPPED_MANGROVE_LOG, Blocks.STRIPPED_CHERRY_LOG,
                // 木头方块
                Blocks.OAK_WOOD, Blocks.SPRUCE_WOOD, Blocks.BIRCH_WOOD, Blocks.JUNGLE_WOOD,
                Blocks.ACACIA_WOOD, Blocks.DARK_OAK_WOOD, Blocks.MANGROVE_WOOD, Blocks.CHERRY_WOOD,
                // 去皮木头方块
                Blocks.STRIPPED_OAK_WOOD, Blocks.STRIPPED_SPRUCE_WOOD, Blocks.STRIPPED_BIRCH_WOOD,
                Blocks.STRIPPED_JUNGLE_WOOD, Blocks.STRIPPED_ACACIA_WOOD, Blocks.STRIPPED_DARK_OAK_WOOD,
                Blocks.STRIPPED_MANGROVE_WOOD, Blocks.STRIPPED_CHERRY_WOOD,
                // 下界和诡异木
                Blocks.CRIMSON_STEM, Blocks.WARPED_STEM,
                Blocks.STRIPPED_CRIMSON_STEM, Blocks.STRIPPED_WARPED_STEM,
                Blocks.CRIMSON_HYPHAE, Blocks.WARPED_HYPHAE,
                Blocks.STRIPPED_CRIMSON_HYPHAE, Blocks.STRIPPED_WARPED_HYPHAE,
                // 竹子方块
                Blocks.BAMBOO_BLOCK, Blocks.STRIPPED_BAMBOO_BLOCK
        ));
        categories.add(wood);

        BlockCategory flowers = new BlockCategory("pushdozer.category.flowers_and_plants");
        flowers.blocks.addAll(Arrays.asList(
                // 花朵
                Blocks.DANDELION, Blocks.POPPY, Blocks.BLUE_ORCHID, Blocks.ALLIUM, Blocks.AZURE_BLUET,
                Blocks.RED_TULIP, Blocks.ORANGE_TULIP, Blocks.WHITE_TULIP, Blocks.PINK_TULIP,
                Blocks.OXEYE_DAISY, Blocks.CORNFLOWER, Blocks.LILY_OF_THE_VALLEY,
                // 植物
                Blocks.SHORT_GRASS, // 矮草丛
                Blocks.TALL_GRASS,
                Blocks.FERN,
                Blocks.LARGE_FERN,
                Blocks.VINE,
                Blocks.LILY_PAD,
                Blocks.SUNFLOWER,
                Blocks.LILAC,
                Blocks.ROSE_BUSH,
                Blocks.PEONY,
                Blocks.PINK_PETALS,    // 粉红色花簇
                Blocks.SEAGRASS,
                Blocks.TALL_SEAGRASS,  // 高的海草
                Blocks.KELP,           // 海带
                Blocks.KELP_PLANT,     // 海带植株
                Blocks.BAMBOO,
                Blocks.SUGAR_CANE,
                Blocks.CACTUS,
                Blocks.DEAD_BUSH,      // 枯萎的灌木
                Blocks.PITCHER_PLANT,   // 瓶子草
                Blocks.PITCHER_CROP,    // 瓶子草作物
                Blocks.TORCHFLOWER,     // 火把花
                Blocks.TORCHFLOWER_CROP, // 火把花作物
                Blocks.AZALEA,          // 杜鹃花丛
                Blocks.FLOWERING_AZALEA, // 开花的杜鹃花丛
                Blocks.SPORE_BLOSSOM,   // 孢子花
                Blocks.HANGING_ROOTS,    // 悬挂根
                Blocks.BIG_DRIPLEAF,    // 大型垂滴叶
                Blocks.SMALL_DRIPLEAF,  // 小型垂滴叶
                Blocks.CAVE_VINES,      // 洞穴藤蔓
                Blocks.GLOW_LICHEN,     // 发光地衣
                Blocks.NETHER_SPROUTS,  // 下界苗
                Blocks.CRIMSON_ROOTS,   // 绯红菌索
                Blocks.WARPED_ROOTS,    // 诡异菌索
                Blocks.WEEPING_VINES,   // 垂泪藤
                Blocks.WEEPING_VINES_PLANT, // 垂泪藤植株
                Blocks.TWISTING_VINES,  // 缠怨藤
                Blocks.TWISTING_VINES_PLANT // 缠怨藤植株
        ));
        categories.add(flowers);

        BlockCategory mushrooms = new BlockCategory("pushdozer.category.mushrooms");
        mushrooms.blocks.addAll(Arrays.asList(
                Blocks.BROWN_MUSHROOM, 
                Blocks.RED_MUSHROOM, 
                Blocks.MUSHROOM_STEM,
                Blocks.BROWN_MUSHROOM_BLOCK, 
                Blocks.CRIMSON_FUNGUS,        // 绯红菌
                Blocks.WARPED_FUNGUS,         // 诡异菌
                Blocks.RED_MUSHROOM_BLOCK
        ));
        categories.add(mushrooms);

        BlockCategory coral = new BlockCategory("pushdozer.category.coral");
        coral.blocks.addAll(Arrays.asList(
                Blocks.BRAIN_CORAL_BLOCK, Blocks.BUBBLE_CORAL_BLOCK, Blocks.FIRE_CORAL_BLOCK,
                Blocks.HORN_CORAL_BLOCK, Blocks.TUBE_CORAL_BLOCK, Blocks.DEAD_BRAIN_CORAL_BLOCK,
                Blocks.DEAD_BUBBLE_CORAL_BLOCK, Blocks.DEAD_FIRE_CORAL_BLOCK, Blocks.DEAD_HORN_CORAL_BLOCK,
                Blocks.DEAD_TUBE_CORAL_BLOCK
        ));
        categories.add(coral);

        BlockCategory construction = new BlockCategory("pushdozer.category.construction");
        construction.blocks.addAll(Arrays.asList(
                Blocks.STONE_BRICKS, Blocks.MOSSY_STONE_BRICKS, Blocks.CRACKED_STONE_BRICKS,
                Blocks.CHISELED_STONE_BRICKS, Blocks.BRICKS, Blocks.PRISMARINE, Blocks.PRISMARINE_BRICKS,
                Blocks.DARK_PRISMARINE, Blocks.NETHER_BRICKS, Blocks.RED_NETHER_BRICKS,
                Blocks.SANDSTONE, Blocks.CHISELED_SANDSTONE, Blocks.CUT_SANDSTONE,
                Blocks.RED_SANDSTONE, Blocks.CHISELED_RED_SANDSTONE, Blocks.CUT_RED_SANDSTONE,
                Blocks.QUARTZ_BLOCK, Blocks.CHISELED_QUARTZ_BLOCK, Blocks.QUARTZ_PILLAR,
                Blocks.SMOOTH_QUARTZ, Blocks.SMOOTH_STONE, Blocks.SMOOTH_SANDSTONE, 
                Blocks.SMOOTH_RED_SANDSTONE,
                Blocks.POLISHED_GRANITE, Blocks.POLISHED_DIORITE, Blocks.POLISHED_ANDESITE,
                Blocks.POLISHED_DEEPSLATE, Blocks.DEEPSLATE_BRICKS, Blocks.CRACKED_DEEPSLATE_BRICKS,
                Blocks.DEEPSLATE_TILES, Blocks.CRACKED_DEEPSLATE_TILES,
                Blocks.CHISELED_DEEPSLATE,
                // 木板系列
                Blocks.OAK_PLANKS, Blocks.SPRUCE_PLANKS, Blocks.BIRCH_PLANKS,
                Blocks.JUNGLE_PLANKS, Blocks.ACACIA_PLANKS, Blocks.DARK_OAK_PLANKS,
                Blocks.MANGROVE_PLANKS, Blocks.CHERRY_PLANKS, Blocks.BAMBOO_PLANKS,
                Blocks.CRIMSON_PLANKS, Blocks.WARPED_PLANKS,
                // 特殊方块
                Blocks.SPAWNER,              // 刷怪笼
                Blocks.TRIAL_SPAWNER,        // 试炼刷怪笼
                Blocks.VAULT,                // 宝库
                Blocks.BONE_BLOCK         // 骨块
        ));
        categories.add(construction);

        BlockCategory concrete = new BlockCategory("pushdozer.category.concrete");
        concrete.blocks.addAll(Arrays.asList(
                Blocks.WHITE_CONCRETE, Blocks.ORANGE_CONCRETE, Blocks.MAGENTA_CONCRETE,
                Blocks.LIGHT_BLUE_CONCRETE, Blocks.YELLOW_CONCRETE, Blocks.LIME_CONCRETE,
                Blocks.PINK_CONCRETE, Blocks.GRAY_CONCRETE, Blocks.LIGHT_GRAY_CONCRETE,
                Blocks.CYAN_CONCRETE, Blocks.PURPLE_CONCRETE, Blocks.BLUE_CONCRETE,
                Blocks.BROWN_CONCRETE, Blocks.GREEN_CONCRETE, Blocks.RED_CONCRETE,
                Blocks.BLACK_CONCRETE
        ));
        categories.add(concrete);

        BlockCategory glass = new BlockCategory("pushdozer.category.glass");
        glass.blocks.addAll(Arrays.asList(
                // 普通玻璃
                Blocks.GLASS,
                // 染色玻璃
                Blocks.WHITE_STAINED_GLASS, Blocks.ORANGE_STAINED_GLASS,
                Blocks.MAGENTA_STAINED_GLASS, Blocks.LIGHT_BLUE_STAINED_GLASS,
                Blocks.YELLOW_STAINED_GLASS, Blocks.LIME_STAINED_GLASS, Blocks.PINK_STAINED_GLASS,
                Blocks.GRAY_STAINED_GLASS, Blocks.LIGHT_GRAY_STAINED_GLASS, Blocks.CYAN_STAINED_GLASS,
                Blocks.PURPLE_STAINED_GLASS, Blocks.BLUE_STAINED_GLASS, Blocks.BROWN_STAINED_GLASS,
                Blocks.GREEN_STAINED_GLASS, Blocks.RED_STAINED_GLASS, Blocks.BLACK_STAINED_GLASS,
                // 玻璃板
                Blocks.GLASS_PANE,
                // 染色玻璃板
                Blocks.WHITE_STAINED_GLASS_PANE, Blocks.ORANGE_STAINED_GLASS_PANE,
                Blocks.MAGENTA_STAINED_GLASS_PANE, Blocks.LIGHT_BLUE_STAINED_GLASS_PANE,
                Blocks.YELLOW_STAINED_GLASS_PANE, Blocks.LIME_STAINED_GLASS_PANE, Blocks.PINK_STAINED_GLASS_PANE,
                Blocks.GRAY_STAINED_GLASS_PANE, Blocks.LIGHT_GRAY_STAINED_GLASS_PANE, Blocks.CYAN_STAINED_GLASS_PANE,
                Blocks.PURPLE_STAINED_GLASS_PANE, Blocks.BLUE_STAINED_GLASS_PANE, Blocks.BROWN_STAINED_GLASS_PANE,
                Blocks.GREEN_STAINED_GLASS_PANE, Blocks.RED_STAINED_GLASS_PANE, Blocks.BLACK_STAINED_GLASS_PANE,
                // 特殊玻璃
                Blocks.TINTED_GLASS // 遮光玻璃
        ));
        categories.add(glass);

        BlockCategory banners = new BlockCategory("pushdozer.category.banners");
        banners.blocks.addAll(Arrays.asList(
                // 旗帜
                Blocks.WHITE_BANNER, Blocks.ORANGE_BANNER, Blocks.MAGENTA_BANNER,
                Blocks.LIGHT_BLUE_BANNER, Blocks.YELLOW_BANNER, Blocks.LIME_BANNER,
                Blocks.PINK_BANNER, Blocks.GRAY_BANNER, Blocks.LIGHT_GRAY_BANNER,
                Blocks.CYAN_BANNER, Blocks.PURPLE_BANNER, Blocks.BLUE_BANNER,
                Blocks.BROWN_BANNER, Blocks.GREEN_BANNER, Blocks.RED_BANNER,
                Blocks.BLACK_BANNER,
                // 墙上的旗帜
                Blocks.WHITE_WALL_BANNER, Blocks.ORANGE_WALL_BANNER, Blocks.MAGENTA_WALL_BANNER,
                Blocks.LIGHT_BLUE_WALL_BANNER, Blocks.YELLOW_WALL_BANNER, Blocks.LIME_WALL_BANNER,
                Blocks.PINK_WALL_BANNER, Blocks.GRAY_WALL_BANNER, Blocks.LIGHT_GRAY_WALL_BANNER,
                Blocks.CYAN_WALL_BANNER, Blocks.PURPLE_WALL_BANNER, Blocks.BLUE_WALL_BANNER,
                Blocks.BROWN_WALL_BANNER, Blocks.GREEN_WALL_BANNER, Blocks.RED_WALL_BANNER,
                Blocks.BLACK_WALL_BANNER
        ));
        categories.add(banners);

        BlockCategory wool = new BlockCategory("pushdozer.category.wool");
        wool.blocks.addAll(Arrays.asList(
                Blocks.WHITE_WOOL, Blocks.ORANGE_WOOL, Blocks.MAGENTA_WOOL,
                Blocks.LIGHT_BLUE_WOOL, Blocks.YELLOW_WOOL, Blocks.LIME_WOOL,
                Blocks.PINK_WOOL, Blocks.GRAY_WOOL, Blocks.LIGHT_GRAY_WOOL,
                Blocks.CYAN_WOOL, Blocks.PURPLE_WOOL, Blocks.BLUE_WOOL,
                Blocks.BROWN_WOOL, Blocks.GREEN_WOOL, Blocks.RED_WOOL,
                Blocks.BLACK_WOOL
        ));
        categories.add(wool);

        BlockCategory copper = new BlockCategory("pushdozer.category.copper");
        copper.blocks.addAll(Arrays.asList(
                // 铜块系列
                Blocks.COPPER_BLOCK, Blocks.EXPOSED_COPPER, Blocks.WEATHERED_COPPER, Blocks.OXIDIZED_COPPER,
                Blocks.CUT_COPPER, Blocks.EXPOSED_CUT_COPPER, Blocks.WEATHERED_CUT_COPPER, Blocks.OXIDIZED_CUT_COPPER,
                Blocks.CUT_COPPER_STAIRS, Blocks.EXPOSED_CUT_COPPER_STAIRS, Blocks.WEATHERED_CUT_COPPER_STAIRS, Blocks.OXIDIZED_CUT_COPPER_STAIRS,
                Blocks.CUT_COPPER_SLAB, Blocks.EXPOSED_CUT_COPPER_SLAB, Blocks.WEATHERED_CUT_COPPER_SLAB, Blocks.OXIDIZED_CUT_COPPER_SLAB,
                // 涂蜡的铜块
                Blocks.WAXED_COPPER_BLOCK, Blocks.WAXED_EXPOSED_COPPER, Blocks.WAXED_WEATHERED_COPPER, Blocks.WAXED_OXIDIZED_COPPER,
                Blocks.WAXED_CUT_COPPER, Blocks.WAXED_EXPOSED_CUT_COPPER, Blocks.WAXED_WEATHERED_CUT_COPPER, Blocks.WAXED_OXIDIZED_CUT_COPPER,
                Blocks.WAXED_CUT_COPPER_STAIRS, Blocks.WAXED_EXPOSED_CUT_COPPER_STAIRS, Blocks.WAXED_WEATHERED_CUT_COPPER_STAIRS, Blocks.WAXED_OXIDIZED_CUT_COPPER_STAIRS,
                Blocks.WAXED_CUT_COPPER_SLAB, Blocks.WAXED_EXPOSED_CUT_COPPER_SLAB, Blocks.WAXED_WEATHERED_CUT_COPPER_SLAB, Blocks.WAXED_OXIDIZED_CUT_COPPER_SLAB,
                // 雕文铜块
                Blocks.CHISELED_COPPER, Blocks.EXPOSED_CHISELED_COPPER, Blocks.WEATHERED_CHISELED_COPPER, Blocks.OXIDIZED_CHISELED_COPPER,
                Blocks.WAXED_CHISELED_COPPER, Blocks.WAXED_EXPOSED_CHISELED_COPPER, Blocks.WAXED_WEATHERED_CHISELED_COPPER, Blocks.WAXED_OXIDIZED_CHISELED_COPPER,
                // 铜灯
                Blocks.COPPER_BULB, Blocks.EXPOSED_COPPER_BULB, Blocks.WEATHERED_COPPER_BULB, Blocks.OXIDIZED_COPPER_BULB,
                Blocks.WAXED_COPPER_BULB, Blocks.WAXED_EXPOSED_COPPER_BULB, Blocks.WAXED_WEATHERED_COPPER_BULB, Blocks.WAXED_OXIDIZED_COPPER_BULB,
                // 铜格栅
                Blocks.COPPER_GRATE, Blocks.EXPOSED_COPPER_GRATE, Blocks.WEATHERED_COPPER_GRATE, Blocks.OXIDIZED_COPPER_GRATE,
                Blocks.WAXED_COPPER_GRATE, Blocks.WAXED_EXPOSED_COPPER_GRATE, Blocks.WAXED_WEATHERED_COPPER_GRATE, Blocks.WAXED_OXIDIZED_COPPER_GRATE,
                // 铜门
                Blocks.COPPER_DOOR, Blocks.EXPOSED_COPPER_DOOR, Blocks.WEATHERED_COPPER_DOOR, Blocks.OXIDIZED_COPPER_DOOR,
                Blocks.WAXED_COPPER_DOOR, Blocks.WAXED_EXPOSED_COPPER_DOOR, Blocks.WAXED_WEATHERED_COPPER_DOOR, Blocks.WAXED_OXIDIZED_COPPER_DOOR,
                // 铜活板门
                Blocks.COPPER_TRAPDOOR, Blocks.EXPOSED_COPPER_TRAPDOOR, Blocks.WEATHERED_COPPER_TRAPDOOR, Blocks.OXIDIZED_COPPER_TRAPDOOR,
                Blocks.WAXED_COPPER_TRAPDOOR, Blocks.WAXED_EXPOSED_COPPER_TRAPDOOR, Blocks.WAXED_WEATHERED_COPPER_TRAPDOOR, Blocks.WAXED_OXIDIZED_COPPER_TRAPDOOR,
                // 其他
                Blocks.RAW_COPPER_BLOCK      // 粗铜块
        ));
        categories.add(copper);

        BlockCategory amethyst = new BlockCategory("pushdozer.category.amethyst");
        amethyst.blocks.addAll(Arrays.asList(
                Blocks.AMETHYST_BLOCK, Blocks.BUDDING_AMETHYST,
                Blocks.SMALL_AMETHYST_BUD, Blocks.MEDIUM_AMETHYST_BUD, Blocks.LARGE_AMETHYST_BUD, Blocks.AMETHYST_CLUSTER
        ));
        categories.add(amethyst);

        BlockCategory cave = new BlockCategory("pushdozer.category.cave");
        cave.blocks.addAll(Arrays.asList(
                Blocks.DRIPSTONE_BLOCK,      // 滴水石块
                Blocks.POINTED_DRIPSTONE,    // 滴水石锥
                Blocks.SPORE_BLOSSOM,        // 孢子花
                Blocks.HANGING_ROOTS,        // 悬根
                Blocks.GLOW_LICHEN           // 发光地衣
                
        ));
        categories.add(cave);

        BlockCategory valuable = new BlockCategory("pushdozer.category.valuable");
        valuable.blocks.addAll(Arrays.asList(
                Blocks.GOLD_BLOCK,           // 金块
                Blocks.IRON_BLOCK,           // 铁块
                Blocks.DIAMOND_BLOCK,        // 钻石块
                Blocks.EMERALD_BLOCK,        // 绿宝石块
                Blocks.NETHERITE_BLOCK,      // 下界合金块
                Blocks.LAPIS_BLOCK,          // 青金石块
                Blocks.RAW_GOLD_BLOCK,       // 粗金块
                Blocks.RAW_IRON_BLOCK        // 粗铁块
        ));
        categories.add(valuable);

        BlockCategory decorative = new BlockCategory("pushdozer.category.decorative");
        decorative.blocks.addAll(Arrays.asList(
                Blocks.SCAFFOLDING, Blocks.CHAIN, Blocks.LANTERN, Blocks.SOUL_LANTERN,
                Blocks.CAMPFIRE, Blocks.SOUL_CAMPFIRE,
                Blocks.TORCH, Blocks.SOUL_TORCH,
                Blocks.WALL_TORCH, Blocks.SOUL_WALL_TORCH,  // 添加墙上的火把
                Blocks.SEA_LANTERN, Blocks.CONDUIT,
                Blocks.END_ROD, 
                Blocks.BEACON, Blocks.LODESTONE,
                Blocks.BELL,                 // 钟
                Blocks.DECORATED_POT,        // 饰纹陶罐
                Blocks.FLOWER_POT,           // 花盆
                // 蜡烛
                Blocks.CANDLE,
                Blocks.WHITE_CANDLE,
                Blocks.ORANGE_CANDLE,
                Blocks.MAGENTA_CANDLE,
                Blocks.LIGHT_BLUE_CANDLE,
                Blocks.YELLOW_CANDLE,
                Blocks.LIME_CANDLE,
                Blocks.PINK_CANDLE,
                Blocks.GRAY_CANDLE,
                Blocks.LIGHT_GRAY_CANDLE,
                Blocks.CYAN_CANDLE,
                Blocks.PURPLE_CANDLE,
                Blocks.BLUE_CANDLE,
                Blocks.BROWN_CANDLE,
                Blocks.GREEN_CANDLE,
                Blocks.RED_CANDLE,
                Blocks.BLACK_CANDLE
                // 画框
                //Blocks.ITEM_FRAME,              // 物品展示框
                //Blocks.GLOW_ITEM_FRAME          // 发光物品展示框
        ));
        categories.add(decorative);

        BlockCategory redstone = new BlockCategory("pushdozer.category.redstone");
        redstone.blocks.addAll(Arrays.asList(
                // 红石基础
                Blocks.REDSTONE_BLOCK,        // 红石块
                Blocks.REDSTONE_WIRE,         // 红石粉
                Blocks.REDSTONE_TORCH,        // 红石火把
                Blocks.REDSTONE_WALL_TORCH,   // 墙上的红石火把
                Blocks.REPEATER,              // 红石中继器
                Blocks.COMPARATOR,            // 红石比较器
                // 动力装置
                Blocks.DISPENSER,             // 发射器
                Blocks.DROPPER,               // 投掷器
                Blocks.PISTON,                // 活塞
                Blocks.STICKY_PISTON,         // 粘性活塞
                Blocks.OBSERVER,              // 侦测器
                Blocks.HOPPER,                // 漏斗
                Blocks.TNT,                   // TNT
                // 感应装置
                Blocks.TRIPWIRE,              // 绊线
                Blocks.TRIPWIRE_HOOK,         // 绊线钩
                Blocks.DAYLIGHT_DETECTOR,     // 阳光传感器
                Blocks.SCULK_SENSOR,          // 幽匿感测体
                Blocks.CALIBRATED_SCULK_SENSOR, // 校准过的幽匿感测体
                Blocks.LIGHTNING_ROD,         // 避雷针
                // 目标方块
                Blocks.TARGET                 // 标靶
        ));
        categories.add(redstone);

        // 创建新的按钮和压力板类别
        BlockCategory buttonsAndPlates = new BlockCategory("pushdozer.category.buttons_and_plates");
        buttonsAndPlates.blocks.addAll(Arrays.asList(
                // 木质按钮
                Blocks.OAK_BUTTON,            // 橡木按钮
                Blocks.SPRUCE_BUTTON,         // 云杉按钮
                Blocks.BIRCH_BUTTON,          // 白桦按钮
                Blocks.JUNGLE_BUTTON,         // 丛林按钮
                Blocks.ACACIA_BUTTON,         // 金合欢按钮
                Blocks.DARK_OAK_BUTTON,       // 深色橡木按钮
                Blocks.MANGROVE_BUTTON,       // 红树按钮
                Blocks.CHERRY_BUTTON,         // 樱花按钮
                Blocks.BAMBOO_BUTTON,         // 竹按钮
                Blocks.CRIMSON_BUTTON,        // 绯红按钮
                Blocks.WARPED_BUTTON,         // 诡异按钮
                // 石质按钮
                Blocks.STONE_BUTTON,          // 石头按钮
                Blocks.POLISHED_BLACKSTONE_BUTTON, // 磨制黑石按钮
                // 木质压力板
                Blocks.OAK_PRESSURE_PLATE,    // 橡木压力板
                Blocks.SPRUCE_PRESSURE_PLATE, // 云杉压力板
                Blocks.BIRCH_PRESSURE_PLATE,  // 白桦压力板
                Blocks.JUNGLE_PRESSURE_PLATE, // 丛��压力板
                Blocks.ACACIA_PRESSURE_PLATE, // 金合欢压力板
                Blocks.DARK_OAK_PRESSURE_PLATE, // 深色橡木压力板
                Blocks.MANGROVE_PRESSURE_PLATE, // 红树压力板
                Blocks.CHERRY_PRESSURE_PLATE,  // 樱花压力板
                Blocks.BAMBOO_PRESSURE_PLATE,  // 竹压力板
                Blocks.CRIMSON_PRESSURE_PLATE, // 绯红压力板
                Blocks.WARPED_PRESSURE_PLATE,  // 诡异压力板
                // 石质压力板
                Blocks.STONE_PRESSURE_PLATE,   // 石头压力板
                Blocks.POLISHED_BLACKSTONE_PRESSURE_PLATE, // 磨制黑石压力板
                // 特殊压力板
                Blocks.LIGHT_WEIGHTED_PRESSURE_PLATE,      // 轻质测重压力板
                Blocks.HEAVY_WEIGHTED_PRESSURE_PLATE       // 重质测重压力板
        ));
        categories.add(buttonsAndPlates);

        BlockCategory doors = new BlockCategory("pushdozer.category.doors");
        doors.blocks.addAll(Arrays.asList(
                // 木门
                Blocks.OAK_DOOR,              // 橡木门
                Blocks.SPRUCE_DOOR,           // 云杉木门
                Blocks.BIRCH_DOOR,            // 白桦木门
                Blocks.JUNGLE_DOOR,           // 丛林木门
                Blocks.ACACIA_DOOR,           // 金合欢木门
                Blocks.DARK_OAK_DOOR,         // 深色橡木门
                Blocks.MANGROVE_DOOR,         // 红树林门
                Blocks.CHERRY_DOOR,           // 樱花门
                Blocks.BAMBOO_DOOR,           // 竹门
                Blocks.CRIMSON_DOOR,          // 绯红门
                Blocks.WARPED_DOOR,           // 诡异门
                // 其他材质门
                Blocks.IRON_DOOR,             // 铁门
                // 铜门系列
                Blocks.COPPER_DOOR,           // 铜门
                Blocks.EXPOSED_COPPER_DOOR,   // 斑驳的铜门
                Blocks.WEATHERED_COPPER_DOOR, // 锈蚀的铜门
                Blocks.OXIDIZED_COPPER_DOOR,  // 氧化的铜门
                // 涂蜡铜门系列
                Blocks.WAXED_COPPER_DOOR,           // 涂蜡的铜门
                Blocks.WAXED_EXPOSED_COPPER_DOOR,   // 涂蜡的斑驳铜门
                Blocks.WAXED_WEATHERED_COPPER_DOOR, // 涂蜡的锈蚀铜门
                Blocks.WAXED_OXIDIZED_COPPER_DOOR,  // 涂蜡的氧化铜门
                // 木活板门
                Blocks.OAK_TRAPDOOR,          // 橡木活板门
                Blocks.SPRUCE_TRAPDOOR,       // 云杉木活板门
                Blocks.BIRCH_TRAPDOOR,        // 白桦���活板门
                Blocks.JUNGLE_TRAPDOOR,       // 丛林木活板门
                Blocks.ACACIA_TRAPDOOR,       // 金合欢木活板门
                Blocks.DARK_OAK_TRAPDOOR,     // 深色橡木活板门
                Blocks.MANGROVE_TRAPDOOR,     // 红树林活板门
                Blocks.CHERRY_TRAPDOOR,       // 樱花活板门
                Blocks.BAMBOO_TRAPDOOR,       // 竹活板门
                Blocks.CRIMSON_TRAPDOOR,      // 绯红活板门
                Blocks.WARPED_TRAPDOOR,       // 诡异活板门
                // 其他材质活板门
                Blocks.IRON_TRAPDOOR,         // 铁活板门
                // 铜活板门系列
                Blocks.COPPER_TRAPDOOR,           // 铜活板门
                Blocks.EXPOSED_COPPER_TRAPDOOR,   // 斑驳的铜活板门
                Blocks.WEATHERED_COPPER_TRAPDOOR, // 锈蚀的铜活板门
                Blocks.OXIDIZED_COPPER_TRAPDOOR,  // 氧化的铜活板门
                // 涂蜡铜活板门系列
                Blocks.WAXED_COPPER_TRAPDOOR,           // 涂蜡的铜活板门
                Blocks.WAXED_EXPOSED_COPPER_TRAPDOOR,   // 涂蜡的斑驳铜活板门
                Blocks.WAXED_WEATHERED_COPPER_TRAPDOOR, // 涂蜡的锈蚀铜活板门
                Blocks.WAXED_OXIDIZED_COPPER_TRAPDOOR   // 涂蜡的氧化铜活板门
        ));
        categories.add(doors);

        BlockCategory fences = new BlockCategory("pushdozer.category.fences");
        fences.blocks.addAll(Arrays.asList(
                // 栅栏
                Blocks.OAK_FENCE, Blocks.SPRUCE_FENCE, Blocks.BIRCH_FENCE, Blocks.JUNGLE_FENCE,
                Blocks.ACACIA_FENCE, Blocks.DARK_OAK_FENCE, Blocks.CRIMSON_FENCE, Blocks.WARPED_FENCE,
                Blocks.MANGROVE_FENCE, Blocks.CHERRY_FENCE, Blocks.BAMBOO_FENCE,
                Blocks.NETHER_BRICK_FENCE,
                // 栅栏门
                Blocks.OAK_FENCE_GATE, Blocks.SPRUCE_FENCE_GATE, Blocks.BIRCH_FENCE_GATE, Blocks.JUNGLE_FENCE_GATE,
                Blocks.ACACIA_FENCE_GATE, Blocks.DARK_OAK_FENCE_GATE, Blocks.CRIMSON_FENCE_GATE, Blocks.WARPED_FENCE_GATE,
                Blocks.MANGROVE_FENCE_GATE, Blocks.CHERRY_FENCE_GATE, Blocks.BAMBOO_FENCE_GATE,
                // 墙
                Blocks.COBBLESTONE_WALL, Blocks.MOSSY_COBBLESTONE_WALL, Blocks.STONE_BRICK_WALL,
                Blocks.MOSSY_STONE_BRICK_WALL, Blocks.BRICK_WALL, Blocks.PRISMARINE_WALL,
                Blocks.RED_SANDSTONE_WALL, Blocks.SANDSTONE_WALL, Blocks.NETHER_BRICK_WALL,
                Blocks.RED_NETHER_BRICK_WALL, Blocks.END_STONE_BRICK_WALL, Blocks.BLACKSTONE_WALL,
                Blocks.POLISHED_BLACKSTONE_WALL, Blocks.POLISHED_BLACKSTONE_BRICK_WALL,
                Blocks.COBBLED_DEEPSLATE_WALL, Blocks.POLISHED_DEEPSLATE_WALL,
                Blocks.DEEPSLATE_BRICK_WALL, Blocks.DEEPSLATE_TILE_WALL
        ));
        categories.add(fences);

        BlockCategory stairs = new BlockCategory("pushdozer.category.stairs");
        stairs.blocks.addAll(Arrays.asList(
                Blocks.OAK_STAIRS, Blocks.SPRUCE_STAIRS, Blocks.BIRCH_STAIRS, Blocks.JUNGLE_STAIRS,
                Blocks.ACACIA_STAIRS, Blocks.DARK_OAK_STAIRS, Blocks.CRIMSON_STAIRS, Blocks.WARPED_STAIRS,
                Blocks.MANGROVE_STAIRS, Blocks.CHERRY_STAIRS, Blocks.BAMBOO_STAIRS,
                Blocks.STONE_STAIRS, Blocks.COBBLESTONE_STAIRS, Blocks.MOSSY_COBBLESTONE_STAIRS,
                Blocks.STONE_BRICK_STAIRS, Blocks.MOSSY_STONE_BRICK_STAIRS, Blocks.GRANITE_STAIRS,
                Blocks.POLISHED_GRANITE_STAIRS, Blocks.DIORITE_STAIRS, Blocks.POLISHED_DIORITE_STAIRS,
                Blocks.ANDESITE_STAIRS, Blocks.POLISHED_ANDESITE_STAIRS, Blocks.COBBLED_DEEPSLATE_STAIRS,
                Blocks.POLISHED_DEEPSLATE_STAIRS, Blocks.DEEPSLATE_BRICK_STAIRS, Blocks.DEEPSLATE_TILE_STAIRS,
                Blocks.SANDSTONE_STAIRS, Blocks.RED_SANDSTONE_STAIRS,
                Blocks.PRISMARINE_STAIRS, Blocks.PRISMARINE_BRICK_STAIRS, Blocks.DARK_PRISMARINE_STAIRS,
                Blocks.NETHER_BRICK_STAIRS, Blocks.RED_NETHER_BRICK_STAIRS,
                Blocks.BLACKSTONE_STAIRS, Blocks.POLISHED_BLACKSTONE_STAIRS,
                Blocks.POLISHED_BLACKSTONE_BRICK_STAIRS, Blocks.END_STONE_BRICK_STAIRS
        ));
        categories.add(stairs);

        BlockCategory slabs = new BlockCategory("pushdozer.category.slabs");
        slabs.blocks.addAll(Arrays.asList(
                Blocks.OAK_SLAB, Blocks.SPRUCE_SLAB, Blocks.BIRCH_SLAB, Blocks.JUNGLE_SLAB,
                Blocks.ACACIA_SLAB, Blocks.DARK_OAK_SLAB, Blocks.CRIMSON_SLAB, Blocks.WARPED_SLAB,
                Blocks.MANGROVE_SLAB, Blocks.CHERRY_SLAB, Blocks.BAMBOO_SLAB,
                Blocks.STONE_SLAB, Blocks.COBBLESTONE_SLAB, Blocks.MOSSY_COBBLESTONE_SLAB,
                Blocks.STONE_BRICK_SLAB, Blocks.MOSSY_STONE_BRICK_SLAB, Blocks.GRANITE_SLAB,
                Blocks.POLISHED_GRANITE_SLAB, Blocks.DIORITE_SLAB, Blocks.POLISHED_DIORITE_SLAB,
                Blocks.ANDESITE_SLAB, Blocks.POLISHED_ANDESITE_SLAB, Blocks.COBBLED_DEEPSLATE_SLAB,
                Blocks.POLISHED_DEEPSLATE_SLAB, Blocks.DEEPSLATE_BRICK_SLAB, Blocks.DEEPSLATE_TILE_SLAB,
                Blocks.SANDSTONE_SLAB, Blocks.RED_SANDSTONE_SLAB,
                Blocks.PRISMARINE_SLAB, Blocks.PRISMARINE_BRICK_SLAB, Blocks.DARK_PRISMARINE_SLAB,
                Blocks.NETHER_BRICK_SLAB, Blocks.RED_NETHER_BRICK_SLAB,
                Blocks.BLACKSTONE_SLAB, Blocks.POLISHED_BLACKSTONE_SLAB,
                Blocks.POLISHED_BLACKSTONE_BRICK_SLAB, Blocks.END_STONE_BRICK_SLAB
        ));
        categories.add(slabs);

        BlockCategory fluids = new BlockCategory("pushdozer.category.fluids");
        fluids.blocks.addAll(Arrays.asList(
                Blocks.WATER, Blocks.LAVA
        ));
        categories.add(fluids);

        BlockCategory aquatic = new BlockCategory("pushdozer.category.aquatic_plants");
        aquatic.blocks.addAll(Arrays.asList(
            Blocks.SEAGRASS,
            Blocks.TALL_SEAGRASS,
            Blocks.KELP,
            Blocks.KELP_PLANT,
            Blocks.SEA_PICKLE,
            // 珊瑚扇
            Blocks.BRAIN_CORAL_FAN,
            Blocks.BUBBLE_CORAL_FAN,
            Blocks.FIRE_CORAL_FAN,
            Blocks.HORN_CORAL_FAN,
            Blocks.TUBE_CORAL_FAN,
            // 珊瑚
            Blocks.BRAIN_CORAL,
            Blocks.BUBBLE_CORAL,
            Blocks.FIRE_CORAL,
            Blocks.HORN_CORAL,
            Blocks.TUBE_CORAL,
            // 墙上的珊瑚扇
            Blocks.BRAIN_CORAL_WALL_FAN,
            Blocks.BUBBLE_CORAL_WALL_FAN,
            Blocks.FIRE_CORAL_WALL_FAN,
            Blocks.HORN_CORAL_WALL_FAN,
            Blocks.TUBE_CORAL_WALL_FAN
        ));
        categories.add(aquatic);

        BlockCategory miscellaneous = new BlockCategory("pushdozer.category.miscellaneous");
        miscellaneous.blocks.addAll(Arrays.asList(
                // 南瓜系列
                Blocks.PUMPKIN,
                Blocks.CARVED_PUMPKIN,
                Blocks.JACK_O_LANTERN,
                // 蜜蜂相关
                Blocks.BEEHIVE,
                Blocks.BEE_NEST,
                Blocks.HONEYCOMB_BLOCK,
                // 蛋糕和蜂蜜块
                Blocks.CAKE,
                Blocks.HONEY_BLOCK,
                // 干草块
                Blocks.HAY_BLOCK,
                // 菌丝
                Blocks.MYCELIUM,
                // 蜘蛛网
                Blocks.COBWEB,
                // 海绵
                Blocks.SPONGE,
                Blocks.WET_SPONGE
        ));
        categories.add(miscellaneous);

        // 添加新的类别：床和地毯
        BlockCategory bedding = new BlockCategory("pushdozer.category.bedding");
        bedding.blocks.addAll(Arrays.asList(
                // 床
                Blocks.WHITE_BED, Blocks.ORANGE_BED, Blocks.MAGENTA_BED,
                Blocks.LIGHT_BLUE_BED, Blocks.YELLOW_BED, Blocks.LIME_BED,
                Blocks.PINK_BED, Blocks.GRAY_BED, Blocks.LIGHT_GRAY_BED,
                Blocks.CYAN_BED, Blocks.PURPLE_BED, Blocks.BLUE_BED,
                Blocks.BROWN_BED, Blocks.GREEN_BED, Blocks.RED_BED,
                Blocks.BLACK_BED,
                // 地毯
                Blocks.WHITE_CARPET, Blocks.ORANGE_CARPET, Blocks.MAGENTA_CARPET,
                Blocks.LIGHT_BLUE_CARPET, Blocks.YELLOW_CARPET, Blocks.LIME_CARPET,
                Blocks.PINK_CARPET, Blocks.GRAY_CARPET, Blocks.LIGHT_GRAY_CARPET,
                Blocks.CYAN_CARPET, Blocks.PURPLE_CARPET, Blocks.BLUE_CARPET,
                Blocks.BROWN_CARPET, Blocks.GREEN_CARPET, Blocks.RED_CARPET,
                Blocks.BLACK_CARPET,
                // 苔藓地毯
                Blocks.MOSS_CARPET
        ));
        categories.add(bedding);

        // 添加功能性方块类别
        BlockCategory functional = new BlockCategory("pushdozer.category.functional");
        functional.blocks.addAll(Arrays.asList(
                // 工作台和工作站
                Blocks.CRAFTING_TABLE,           // 工作台
                Blocks.SMITHING_TABLE,           // 锻造台
                Blocks.FLETCHING_TABLE,          // 制箭台
                Blocks.CARTOGRAPHY_TABLE,        // 制���台
                Blocks.LOOM,                     // 织布机
                Blocks.STONECUTTER,              // 切石机
                Blocks.GRINDSTONE,               // 砂轮
                Blocks.ANVIL,                    // 铁砧
                Blocks.CHIPPED_ANVIL,            // 开裂的铁砧
                Blocks.DAMAGED_ANVIL,            // 损坏的铁砧
                // 存储类
                Blocks.CHEST,                    // 箱子
                Blocks.TRAPPED_CHEST,            // 陷阱箱
                Blocks.BARREL,                   // 木桶
                Blocks.SHULKER_BOX,              // 潜影盒
                Blocks.WHITE_SHULKER_BOX,        // 白色潜影盒
                Blocks.ORANGE_SHULKER_BOX,       // 橙色潜影盒
                Blocks.MAGENTA_SHULKER_BOX,      // 品红色潜影盒
                Blocks.LIGHT_BLUE_SHULKER_BOX,   // 淡蓝色潜影盒
                Blocks.YELLOW_SHULKER_BOX,       // 黄色潜影盒
                Blocks.LIME_SHULKER_BOX,         // 黄绿色潜影盒
                Blocks.PINK_SHULKER_BOX,         // 粉红色潜影盒
                Blocks.GRAY_SHULKER_BOX,         // 灰色潜影盒
                Blocks.LIGHT_GRAY_SHULKER_BOX,   // 淡灰色潜影盒
                Blocks.CYAN_SHULKER_BOX,         // 青色潜影盒
                Blocks.PURPLE_SHULKER_BOX,       // 紫色潜影盒
                Blocks.BLUE_SHULKER_BOX,         // 蓝色潜影盒
                Blocks.BROWN_SHULKER_BOX,        // 棕色潜影盒
                Blocks.GREEN_SHULKER_BOX,        // 绿色潜影盒
                Blocks.RED_SHULKER_BOX,          // 红色潜影盒
                Blocks.BLACK_SHULKER_BOX,        // 黑色潜影盒
                // 告示牌
                Blocks.OAK_SIGN,                 // 橡木告示牌
                Blocks.SPRUCE_SIGN,              // 云杉木告示牌
                Blocks.BIRCH_SIGN,               // 白桦木告示牌
                Blocks.JUNGLE_SIGN,              // 丛林木告示牌
                Blocks.ACACIA_SIGN,              // 金合欢木告示牌
                Blocks.DARK_OAK_SIGN,            // 深色橡木告示牌
                Blocks.MANGROVE_SIGN,            // 红树林木告示牌
                Blocks.BAMBOO_SIGN,              // 竹告示牌
                Blocks.CHERRY_SIGN,              // 樱花木告示牌
                Blocks.CRIMSON_SIGN,             // 绯红木告示牌
                Blocks.WARPED_SIGN,              // 诡异木告示牌
                // 墙上的告示牌
                Blocks.OAK_WALL_SIGN,            // 墙上的橡木告示牌
                Blocks.SPRUCE_WALL_SIGN,         // 墙上的云杉木告示牌
                Blocks.BIRCH_WALL_SIGN,          // 墙上的白桦木告示牌
                Blocks.JUNGLE_WALL_SIGN,         // 墙上的丛林木告示牌
                Blocks.ACACIA_WALL_SIGN,         // 墙上的金合欢木告示牌
                Blocks.DARK_OAK_WALL_SIGN,       // 墙上的深色橡木告示牌
                Blocks.MANGROVE_WALL_SIGN,       // 墙上的红树林木告示牌
                Blocks.BAMBOO_WALL_SIGN,         // 墙上的竹告示牌
                Blocks.CHERRY_WALL_SIGN,         // 墙上的樱花木告示牌
                Blocks.CRIMSON_WALL_SIGN,        // 墙上的绯红木告示牌
                Blocks.WARPED_WALL_SIGN,         // 墙上的诡异木告示牌
                // 悬挂告示牌
                Blocks.OAK_HANGING_SIGN,         // 悬挂的橡木告示牌
                Blocks.SPRUCE_HANGING_SIGN,      // 悬挂的云杉木告示牌
                Blocks.BIRCH_HANGING_SIGN,       // 悬挂的白桦木告示牌
                Blocks.JUNGLE_HANGING_SIGN,      // 悬挂的丛林木告示牌
                Blocks.ACACIA_HANGING_SIGN,      // 悬挂的金合欢木告示牌
                Blocks.DARK_OAK_HANGING_SIGN,    // 悬挂的深色橡木告示牌
                Blocks.MANGROVE_HANGING_SIGN,    // 悬挂的红树林木告示牌
                Blocks.BAMBOO_HANGING_SIGN,      // 悬挂的竹告示牌
                Blocks.CHERRY_HANGING_SIGN,      // 悬挂的樱花木告示牌
                Blocks.CRIMSON_HANGING_SIGN,     // 悬挂的绯红木告示牌
                Blocks.WARPED_HANGING_SIGN,      // 悬挂的诡异木告示牌
                // 墙上的悬挂告示牌
                Blocks.OAK_WALL_HANGING_SIGN,    // 墙上的悬挂橡木告示牌
                Blocks.SPRUCE_WALL_HANGING_SIGN, // 墙上的悬挂云杉木告示牌
                Blocks.BIRCH_WALL_HANGING_SIGN,  // 墙上的悬挂白桦木告示牌
                Blocks.JUNGLE_WALL_HANGING_SIGN, // 墙上的悬挂丛林木告示牌
                Blocks.ACACIA_WALL_HANGING_SIGN, // 墙上的悬挂金合欢木告示牌
                Blocks.DARK_OAK_WALL_HANGING_SIGN, // 墙上的悬挂深色橡木告示牌
                Blocks.MANGROVE_WALL_HANGING_SIGN, // 墙上的悬挂红树林木告示牌
                Blocks.BAMBOO_WALL_HANGING_SIGN,   // 墙上的悬挂竹告示牌
                Blocks.CHERRY_WALL_HANGING_SIGN,   // 墙上的悬挂樱花木告示牌
                Blocks.CRIMSON_WALL_HANGING_SIGN,  // 墙上的悬挂绯红木告示牌
                Blocks.WARPED_WALL_HANGING_SIGN  // 墙上的悬挂诡异木告示牌
                // 装饰画
        ));
        categories.add(functional);

        // 农作物类别
        BlockCategory crops = new BlockCategory("pushdozer.category.crops");
        crops.blocks.addAll(Arrays.asList(
                // 基础农作物
                Blocks.WHEAT,                 // 小麦
                Blocks.CARROTS,               // 胡萝卜
                Blocks.POTATOES,              // 马铃薯
                Blocks.BEETROOTS,             // 甜菜根
                Blocks.SWEET_BERRY_BUSH,      // 甜浆果丛
                // 特殊农作物
                Blocks.PUMPKIN_STEM,          // 南瓜茎
                Blocks.MELON_STEM,            // 西瓜茎
                Blocks.COCOA,                 // 可可豆
                // 新增：竹子和甘蔗
                Blocks.BAMBOO,                // 竹子
                Blocks.SUGAR_CANE             // 甘蔗
        ));
        categories.add(crops);

        // ���颅类别
        BlockCategory heads = new BlockCategory("pushdozer.category.heads");
        heads.blocks.addAll(Arrays.asList(
            Blocks.PLAYER_HEAD,           // 玩家头颅
            Blocks.PLAYER_WALL_HEAD,      // 墙上的玩家头颅  
            Blocks.ZOMBIE_HEAD,           // 僵尸头颅
            Blocks.ZOMBIE_WALL_HEAD,      // 墙上的僵尸头颅
            Blocks.CREEPER_HEAD,          // 苦力怕头颅  
            Blocks.CREEPER_WALL_HEAD,     // 墙上的苦力怕头颅
            Blocks.DRAGON_HEAD,           // 龙首
            Blocks.DRAGON_WALL_HEAD,      // 墙上的龙首
            Blocks.SKELETON_SKULL,        // 骷髅头颅
            Blocks.SKELETON_WALL_SKULL,   // 墙上的骷髅头颅
            Blocks.WITHER_SKELETON_SKULL, // 凋灵骷髅头颅
            Blocks.WITHER_SKELETON_WALL_SKULL, // 墙上的凋灵骷髅头颅
            Blocks.PIGLIN_HEAD,           // 猪灵头颅
            Blocks.PIGLIN_WALL_HEAD,      // 墙上的猪灵头颅
            Blocks.DRAGON_EGG, 
            Blocks.DRAGON_HEAD
        ));
        categories.add(heads);

        // 轨道类别
        BlockCategory rails = new BlockCategory("pushdozer.category.rails");
        rails.blocks.addAll(Arrays.asList(
            Blocks.RAIL,                // 铁轨
            Blocks.POWERED_RAIL,        // 动力铁轨
            Blocks.DETECTOR_RAIL,       // 检测铁轨
            Blocks.ACTIVATOR_RAIL       // 激活铁轨
        ));
        categories.add(rails);

        return categories;
    }
}