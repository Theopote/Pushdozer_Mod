package com.pushdozer.ui.screens;

import com.pushdozer.util.BlockDisplayIcons;
import com.pushdozer.util.ExceptionPolicy;

import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.input.CharInput;
import net.minecraft.client.input.KeyInput;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

/**
 * 地形方块选择屏幕
 * 专门用于表层转换配置，包含自然方块、地形方块和流体
 */
public class NaturalBlockSelectionScreen extends Screen {
    private final Screen parent;
    private final Consumer<Block> onBlockSelected;
    private List<BlockCategory> blockCategories;
    private Block selectedBlock = null; // 当前选中的方块

    // 性能优化：静态缓存
    private static List<BlockCategory> cachedCategories = null;
    // 全局搜索字符串缓存 - 性能优化
    private static Map<Block, String> globalSearchStrings = new HashMap<>();
    
    private int currentPage = 0;
    private String searchText = "";
    private TextFieldWidget searchBox;

    // 常量定义
    private static final int BLOCKS_PER_ROW = 4;
    private static final int BLOCK_SIZE = 20;
    private static final int BUTTON_HEIGHT = 20;
    private static final int BUTTON_WIDTH = 80;
    private static final int BUTTON_SPACING = 10;
    private static final int CATEGORIES_PER_PAGE = 4;
    private static final int CATEGORY_BUTTON_HEIGHT = 20;
    private static final int TOP_MARGIN = 35;
    private static final int BOTTOM_MARGIN = 10;
    private static final int SEARCH_BOTTOM_SPACING = 5;
    private static final int PAGE_NUMBER_SPACING = 10;
    
    // UI样式常量
    private static final int PANEL_BACKGROUND_COLOR = 0x40000000;
    private static final int BLOCK_BORDER_COLOR = 0xFF8B8B8B;
    private static final int BLOCK_BACKGROUND_COLOR = 0xFF373737;
    private static final int BLOCK_HOVER_COLOR = 0xFF555555;
    private static final int SCROLL_BAR_BACKGROUND_COLOR = 0x33FFFFFF;
    private static final int SCROLL_BAR_COLOR = 0xFFAAAAAA;
    private static final int SCROLL_BAR_HIGHLIGHT_COLOR = 0xFFFFFFFF;
    private static final int SCROLL_BAR_SHADOW_COLOR = 0xFF555555;
    private static final int SELECTED_BLOCK_BORDER_COLOR = 0xFFFFFFFF; // 选中方块的白色边框

    public NaturalBlockSelectionScreen(Screen parent, Consumer<Block> onBlockSelected) {
        super(Text.translatable("pushdozer.screen.terrain_block_selection.title"));
        this.parent = parent;
        this.onBlockSelected = onBlockSelected;
        this.blockCategories = getAllRelevantBlocks();
    }

    @Override
    protected void init() {
        super.init();
        addSearchAndButtons(false);
        refreshCategoryButtons();
    }

    @Override
    public void tick() {
        super.tick();
    }

    private void addSearchAndButtons(boolean restoreFocus) {
        // 计算布局位置
        int totalWidth = BUTTON_WIDTH * 2 + BUTTON_SPACING;
        int startX = (width - totalWidth) / 2;
        int buttonY = height - BUTTON_HEIGHT - BOTTOM_MARGIN;
        int searchBoxY = buttonY - BUTTON_HEIGHT - SEARCH_BOTTOM_SPACING;
        
        // 添加搜索框
        searchBox = new TextFieldWidget(textRenderer, startX, searchBoxY, totalWidth, BUTTON_HEIGHT, 
            Text.translatable("pushdozer.screen.terrain_block_selection.search"));
        searchBox.setMaxLength(50);
        searchBox.setDrawsBackground(true);
        searchBox.setVisible(true);
        searchBox.setEditable(true);
        searchBox.setText(searchText);
        searchBox.setFocused(restoreFocus);
        searchBox.setChangedListener(this::onSearchTextChanged);
        addDrawableChild(searchBox);
        
        addPageButtons();
        addSelectionButtons();
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubleClick) {
        boolean result = super.mouseClicked(click, doubleClick);
        if (searchBox != null) {
            double mouseX = click.x();
            double mouseY = click.y();
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
    public boolean keyPressed(KeyInput input) {
        if (searchBox != null && searchBox.isFocused()) {
            int keyCode = input.key();
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                searchBox.setFocused(false);
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_ENTER) {
                focusOnMatch();
                return true;
            }
            if (searchBox.keyPressed(input)) {
                return true;
            }
        }
        return super.keyPressed(input);
    }

    @Override
    public boolean charTyped(CharInput input) {
        if (searchBox != null && searchBox.isFocused()) {
            if (searchBox.charTyped(input)) {
                return true;
            }
        }
        return super.charTyped(input);
    }

    private void focusOnMatch() {
        if (searchText.isEmpty()) {
            return;
        }

        for (int i = 0; i < blockCategories.size(); i++) {
            BlockCategory category = blockCategories.get(i);
            for (int j = 0; j < category.blocks.size(); j++) {
                Block block = category.blocks.get(j);
                String blockName = Text.translatable(block.getTranslationKey()).getString().toLowerCase();
                String blockId = Registries.BLOCK.getId(block).getPath().toLowerCase();
                
                if (blockName.contains(searchText.toLowerCase()) || blockId.contains(searchText.toLowerCase())) {
                    int targetPage = i / CATEGORIES_PER_PAGE;
                    if (currentPage != targetPage) {
                        currentPage = targetPage;
                        refreshCategoryButtons();
                    }
                    
                    scrollToBlock(block, j);
                    return;
                }
            }
        }
    }

    private void scrollToBlock(Block targetBlock, int blockIndex) {
        for (Element child : children()) {
            if (child instanceof ScrollablePanel panel) {
                if (panel.blocks.contains(targetBlock)) {
                    int row = blockIndex / BLOCKS_PER_ROW;
                    int targetScrollOffset = row * (BLOCK_SIZE + ScrollablePanel.BLOCK_SPACING);
                    
                    int maxScroll = Math.max(0, (panel.blocks.size() + BLOCKS_PER_ROW - 1) / BLOCKS_PER_ROW - panel.getVisibleRows()) * (BLOCK_SIZE + ScrollablePanel.BLOCK_SPACING);
                    panel.scrollOffset = Math.max(0, Math.min(targetScrollOffset, maxScroll));
                    break;
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
        String searchLower = searchText.toLowerCase();
        
        String searchString = globalSearchStrings.get(block);
        if (searchString != null) {
            return searchString.contains(searchLower);
        }
        
        String blockName = Text.translatable(block.getTranslationKey()).getString().toLowerCase();
        String blockId = Registries.BLOCK.getId(block).getPath().toLowerCase();
        return blockName.contains(searchLower) || blockId.contains(searchLower);
    }

    private void addPageButtons() {
        ButtonWidget prevButton = ButtonWidget.builder(net.minecraft.text.Text.literal("<"), button -> changePage(-1))
                .dimensions(10, height / 2, 20, 20)
                .tooltip(Tooltip.of(net.minecraft.text.Text.translatable("pushdozer.screen.terrain_block_selection.previous_page")))
                .build();
        addDrawableChild(prevButton);

        ButtonWidget nextButton = ButtonWidget.builder(net.minecraft.text.Text.literal(">"), button -> changePage(1))
                .dimensions(width - 30, height / 2, 20, 20)
                .tooltip(Tooltip.of(net.minecraft.text.Text.translatable("pushdozer.screen.terrain_block_selection.next_page")))
                .build();
        addDrawableChild(nextButton);
    }

    private void changePage(int delta) {
        int totalPages = (blockCategories.size() - 1) / CATEGORIES_PER_PAGE + 1;
        currentPage += delta;
        
        if (currentPage < 0) {
            currentPage = totalPages - 1;
        } else if (currentPage >= totalPages) {
            currentPage = 0;
        }
        
        refreshCategoryButtons();
    }

    private void refreshCategoryButtons() {
        boolean wasFocused = searchBox != null && searchBox.isFocused();
        
        clearChildren();
        
        addSearchAndButtons(wasFocused);

        int categorySpacing = 8;
        int blockSpacing = 4;
        int categoryToBlockSpacing = 10;
        int scrollBarWidth = 4;

        int categoryWidth = BLOCK_SIZE * BLOCKS_PER_ROW + blockSpacing * (BLOCKS_PER_ROW - 1) + scrollBarWidth;
        int totalWidth2 = CATEGORIES_PER_PAGE * categoryWidth + (CATEGORIES_PER_PAGE - 1) * categorySpacing;
        int startX2 = (width - totalWidth2) / 2;
        int startY = TOP_MARGIN;

        int startIndex = currentPage * CATEGORIES_PER_PAGE;
        for (int i = 0; i < CATEGORIES_PER_PAGE && startIndex + i < blockCategories.size(); i++) {
            BlockCategory category = blockCategories.get(startIndex + i);
            int columnX = startX2 + i * (categoryWidth + categorySpacing);

            List<Block> filteredBlocks = category.blocks.stream()
                .filter(this::matchesSearch)
                .collect(Collectors.toList());
            
            Text categoryText = Text.literal(category.getTranslatedName().getString() + 
                " (" + filteredBlocks.size() + ")");
            
            addDrawableChild(ButtonWidget.builder(
                categoryText,
                button -> toggleCategorySelection(category))
                .dimensions(columnX, startY, categoryWidth, CATEGORY_BUTTON_HEIGHT)
                .build());

            int bottomSpace = BUTTON_HEIGHT + BOTTOM_MARGIN + BUTTON_HEIGHT + SEARCH_BOTTOM_SPACING + PAGE_NUMBER_SPACING;
            int panelHeight = height - startY - CATEGORY_BUTTON_HEIGHT - categoryToBlockSpacing - bottomSpace + 2;
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
        int y = height - BUTTON_HEIGHT - BOTTOM_MARGIN;
        int totalWidth = BUTTON_WIDTH * 2 + BUTTON_SPACING;
        int startX = (width - totalWidth) / 2;

        addDrawableChild(ButtonWidget.builder(Text.translatable("gui.ok"), button -> onConfirm())
            .dimensions(startX, y, BUTTON_WIDTH, BUTTON_HEIGHT).build());

        addDrawableChild(ButtonWidget.builder(Text.translatable("gui.cancel"), button -> close())
            .dimensions(startX + BUTTON_WIDTH + BUTTON_SPACING, y, BUTTON_WIDTH, BUTTON_HEIGHT).build());
    }

    private String getPageText() {
        int totalPages = (blockCategories.size() - 1) / CATEGORIES_PER_PAGE + 1;
        return String.format("%d/%d", currentPage + 1, totalPages);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        // 使用自定义背景渲染避免blur冲突
        context.fill(0, 0, width, height, 0x80000000);
        
        super.render(context, mouseX, mouseY, delta);
        
        // 绘制顶部标题背景
        int titleBackgroundHeight = 35;
        context.fill(0, 0, width, titleBackgroundHeight, 0x80000000);
        
        // 绘制标题
        context.drawCenteredTextWithShadow(textRenderer, title, width / 2, 8, 0xFFFFFF);
        
        // 在顶部显示选中的方块名称和总数量 - 使用更醒目的颜色
        int totalBlocks = blockCategories.stream().mapToInt(cat -> cat.blocks.size()).sum();
        if (selectedBlock != null) {
            String selectedBlockName = Text.translatable(selectedBlock.getTranslationKey()).getString();
            String countText = String.format("选中: %s / 总计: %d 个方块", selectedBlockName, totalBlocks);
            context.drawCenteredTextWithShadow(textRenderer, Text.literal(countText), width / 2, 20, 0xFFFFFFFF);
        } else {
            // 如果没有选中方块，显示提示信息和总数量
            String countText = String.format("请选择一个方块 / 总计: %d 个方块", totalBlocks);
            context.drawCenteredTextWithShadow(textRenderer, Text.literal(countText), width / 2, 20, 0xFFAAAAAA);
        }

        String pageText = getPageText();
        int searchBoxY = height - BUTTON_HEIGHT - BOTTOM_MARGIN - BUTTON_HEIGHT - SEARCH_BOTTOM_SPACING;
        context.drawCenteredTextWithShadow(textRenderer, Text.literal(pageText), width / 2, searchBoxY - PAGE_NUMBER_SPACING, 0xFFFFFF);
    }

    private void toggleBlockSelection(Block block) {
        // 单选逻辑：如果点击的是已选中的方块，则取消选择；否则选择新方块
        if (selectedBlock == block) {
            selectedBlock = null;
        } else {
            selectedBlock = block;
        }
    }

    private void toggleCategorySelection(BlockCategory category) {
        // 单选逻辑：如果分类中有已选中的方块，则取消选择；否则选择分类中的第一个方块
        if (category.blocks.contains(selectedBlock)) {
            selectedBlock = null;
        } else if (!category.blocks.isEmpty()) {
            selectedBlock = category.blocks.getFirst();
        }
        refreshCategoryButtons();
    }

    private void onConfirm() {
        // 如果有选中的方块，应用选择并关闭窗口
        if (selectedBlock != null) {
            onBlockSelected.accept(selectedBlock);
        }
        close();
    }

    @Override
    public void close() {
        if (client != null) {
            client.setScreen(parent);
        }
    }

    // ScrollablePanel 类
    private class ScrollablePanel extends ButtonWidget {
        private final List<Block> blocks;
        private int scrollOffset = 0;
        private static final int SCROLL_BAR_WIDTH = 4;
        private static final int BLOCK_SPACING = 4;

        private int hoveredX;
        private int hoveredY;
        private boolean isDraggingScrollBar = false;
        private int dragStartY = 0;
        private int dragStartScrollOffset = 0;
        private long hoverStartTime = 0;
        private static final long TOOLTIP_DELAY = 500;
        private Block lastHoveredBlock = null;

        public ScrollablePanel(int x, int y, int width, int height, List<Block> blocks) {
            super(x, y, width, height, net.minecraft.text.Text.empty(), button -> {}, DEFAULT_NARRATION_SUPPLIER);
            this.blocks = blocks;
        }

        private int getVisibleRows() {
            return height / (BLOCK_SIZE + BLOCK_SPACING);
        }

        @Override
        protected void drawIcon(DrawContext context, int mouseX, int mouseY, float delta) {
            int totalRows = (blocks.size() + BLOCKS_PER_ROW - 1) / BLOCKS_PER_ROW;
            int visibleRows = getVisibleRows();

            Block hoveredBlock = null;

            int backgroundWidth = BLOCKS_PER_ROW * BLOCK_SIZE + (BLOCKS_PER_ROW - 1) * BLOCK_SPACING;
            int backgroundHeight = visibleRows * (BLOCK_SIZE + BLOCK_SPACING) - BLOCK_SPACING;
            context.fill(getX(), getY(), getX() + backgroundWidth, getY() + backgroundHeight, PANEL_BACKGROUND_COLOR);

            context.enableScissor(getX(), getY(), getX() + width, getY() + height);
            for (int i = 0; i < blocks.size(); i++) {
                int row = i / BLOCKS_PER_ROW;
                int col = i % BLOCKS_PER_ROW;
                if (row >= scrollOffset / (BLOCK_SIZE + BLOCK_SPACING) && row < scrollOffset / (BLOCK_SIZE + BLOCK_SPACING) + visibleRows) {
                    int blockX = getX() + col * (BLOCK_SIZE + BLOCK_SPACING);
                    int blockY = getY() + (row - scrollOffset / (BLOCK_SIZE + BLOCK_SPACING)) * (BLOCK_SIZE + BLOCK_SPACING);
                    renderBlockButton(context, blocks.get(i), blockX, blockY, mouseX, mouseY);
                    
                    if (mouseX >= blockX && mouseX < blockX + BLOCK_SIZE && mouseY >= blockY && mouseY < blockY + BLOCK_SIZE) {
                        hoveredBlock = blocks.get(i);
                        hoveredX = mouseX;
                        hoveredY = mouseY;
                    }
                }
            }
            context.disableScissor();

            context.fill(getX() + width - SCROLL_BAR_WIDTH, getY(), getX() + width, getY() + backgroundHeight, SCROLL_BAR_BACKGROUND_COLOR);

            if (totalRows > visibleRows) {
                int scrollBarHeight = Math.max(20, backgroundHeight * visibleRows / totalRows);
                int scrollBarY = getY() + (int) ((backgroundHeight - scrollBarHeight) * (float) scrollOffset / ((totalRows - visibleRows) * (BLOCK_SIZE + BLOCK_SPACING)));
                
                drawScrollBar(context, getX() + width - SCROLL_BAR_WIDTH, scrollBarY, scrollBarHeight);
            }

            if (hoveredBlock != null) {
                long currentTime = System.currentTimeMillis();
                if (lastHoveredBlock != hoveredBlock) {
                    hoverStartTime = currentTime;
                    lastHoveredBlock = hoveredBlock;
                }
                
                if (currentTime - hoverStartTime >= TOOLTIP_DELAY) {
                    context.drawTooltip(textRenderer, hoveredBlock.getName(), hoveredX, hoveredY);
                }
            } else {
                lastHoveredBlock = null;
            }
        }

        private void renderBlockButton(DrawContext context, Block block, int x, int y, int mouseX, int mouseY) {
            int borderColor = (block == selectedBlock) ? SELECTED_BLOCK_BORDER_COLOR : BLOCK_BORDER_COLOR;
            
            context.fill(x, y, x + BLOCK_SIZE, y + BLOCK_SIZE, borderColor);
            context.fill(x + 1, y + 1, x + BLOCK_SIZE - 1, y + BLOCK_SIZE - 1, BLOCK_BACKGROUND_COLOR);
            
            if (mouseX >= x && mouseX < x + BLOCK_SIZE && mouseY >= y && mouseY < y + BLOCK_SIZE) {
                context.fill(x + 1, y + 1, x + BLOCK_SIZE - 1, y + BLOCK_SIZE - 1, BLOCK_HOVER_COLOR);
            }
            
            ItemStack displayStack = BlockDisplayIcons.getDisplayStack(block);
            if (!displayStack.isEmpty()) {
                context.drawItem(displayStack, x + (BLOCK_SIZE - 16) / 2, y + (BLOCK_SIZE - 16) / 2);
            }
            
            // 为墙头、墙标牌等特殊方块添加下划线标识
            String blockId = Registries.BLOCK.getId(block).getPath();
            if (blockId.contains("wall_head") || blockId.contains("wall_skull") || blockId.contains("wall_sign")) {
                int underlineY = y + BLOCK_SIZE - 3;
                int underlineColor = (block == selectedBlock) ? 0xFFFFFFFF : 0xFF666666;
                context.fill(x + 2, underlineY, x + BLOCK_SIZE - 2, underlineY + 1, underlineColor);
            }
            
            if (block == selectedBlock) {
                context.fill(x + 1, y + 1, x + BLOCK_SIZE - 1, y + BLOCK_SIZE - 1, 0x80FFFFFF);
                context.drawText(textRenderer, net.minecraft.text.Text.literal("☑"), x + BLOCK_SIZE - 8, y + BLOCK_SIZE - 9, 0xFF000000, false);
            }
        }

        @Override
        public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
            if (isMouseOver(mouseX, mouseY)) {
                int totalRows = (blocks.size() + BLOCKS_PER_ROW - 1) / BLOCKS_PER_ROW;
                int maxScroll = Math.max(0, (totalRows - getVisibleRows()) * (BLOCK_SIZE + BLOCK_SPACING));
                scrollOffset = Math.max(0, Math.min(scrollOffset - (int)(verticalAmount * 10), maxScroll));
                return true;
            }
            return false;
        }

        @Override
        public boolean mouseClicked(Click click, boolean doubleClick) {
            double mouseX = click.x();
            double mouseY = click.y();
            if (isMouseOver(mouseX, mouseY)) {
                int totalRows = (blocks.size() + BLOCKS_PER_ROW - 1) / BLOCKS_PER_ROW;
                int backgroundHeight = getVisibleRows() * (BLOCK_SIZE + BLOCK_SPACING) - BLOCK_SPACING;
                
                if (mouseX >= getX() + width - SCROLL_BAR_WIDTH && mouseX < getX() + width && 
                    mouseY >= getY() && mouseY < getY() + backgroundHeight) {
                    if (totalRows > getVisibleRows()) {
                    isDraggingScrollBar = true;
                    dragStartY = (int) mouseY;
                    dragStartScrollOffset = scrollOffset;
                    return true;
                    }
                }
                
                int startRow = scrollOffset / (BLOCK_SIZE + BLOCK_SPACING);
                for (int i = startRow * BLOCKS_PER_ROW; i < Math.min(blocks.size(), (startRow + getVisibleRows()) * BLOCKS_PER_ROW); i++) {
                    int row = i / BLOCKS_PER_ROW - startRow;
                    int col = i % BLOCKS_PER_ROW;
                    int blockX = getX() + col * (BLOCK_SIZE + BLOCK_SPACING);
                    int blockY = getY() + row * (BLOCK_SIZE + BLOCK_SPACING);
                        if (mouseX >= blockX && mouseX < blockX + BLOCK_SIZE && mouseY >= blockY && mouseY < blockY + BLOCK_SIZE) {
                        toggleBlockSelection(blocks.get(i));
                            return true;
                    }
                }
            }
            return false;
        }
        
        @Override
        public boolean mouseDragged(Click click, double deltaX, double deltaY) {
            double mouseY = click.y();
            if (isDraggingScrollBar) {
                int totalRows = (blocks.size() + BLOCKS_PER_ROW - 1) / BLOCKS_PER_ROW;
                int backgroundHeight = getVisibleRows() * (BLOCK_SIZE + BLOCK_SPACING) - BLOCK_SPACING;
                int maxScroll = Math.max(0, (totalRows - getVisibleRows()) * (BLOCK_SIZE + BLOCK_SPACING));
                
                    int dragDistance = (int) mouseY - dragStartY;
                int scrollBarHeight = Math.max(20, backgroundHeight * getVisibleRows() / totalRows);
                int scrollableHeight = backgroundHeight - scrollBarHeight;
                    
                if (scrollableHeight > 0) {
                    double scrollRatio = (double) dragDistance / scrollableHeight;
                    int newScrollOffset = dragStartScrollOffset + (int) (scrollRatio * maxScroll);
                    scrollOffset = Math.max(0, Math.min(newScrollOffset, maxScroll));
                }
                
                return true;
            }
            return false;
        }
        
        @Override
        public boolean mouseReleased(Click click) {
            if (isDraggingScrollBar) {
                isDraggingScrollBar = false;
                return true;
            }
            return false;
        }
        
        private void drawScrollBar(DrawContext context, int x, int y, int height) {
            context.fill(x, y, x + SCROLL_BAR_WIDTH, y + height, SCROLL_BAR_BACKGROUND_COLOR);
            
            context.fill(x, y, x + SCROLL_BAR_WIDTH, y + height, SCROLL_BAR_COLOR);
            context.fill(x, y, x + SCROLL_BAR_WIDTH - 1, y + 1, SCROLL_BAR_HIGHLIGHT_COLOR);
            context.fill(x, y, x + 1, y + height - 1, SCROLL_BAR_HIGHLIGHT_COLOR);
            context.fill(x + SCROLL_BAR_WIDTH - 1, y, x + SCROLL_BAR_WIDTH, y + height, SCROLL_BAR_SHADOW_COLOR);
            context.fill(x, y + height - 1, x + SCROLL_BAR_WIDTH, y + height, SCROLL_BAR_SHADOW_COLOR);
        }
    }

    // BlockCategory 类
    private static class BlockCategory {
        String translationKey;
        List<Block> blocks;
        private int priority;
        private Set<String> addedBlockIds;

        BlockCategory(String translationKey, int priority) {
            this.translationKey = translationKey;
            this.priority = priority;
            this.blocks = new ArrayList<>();
            this.addedBlockIds = new HashSet<>();
        }

        public Text getTranslatedName() {
            return Text.translatable(translationKey);
        }
        
        public void addBlock(Block block) {
            String blockId = Registries.BLOCK.getId(block).toString();
            if (!addedBlockIds.contains(blockId)) {
                blocks.add(block);
                addedBlockIds.add(blockId);
                if (!globalSearchStrings.containsKey(block)) {
                    String blockName = Text.translatable(block.getTranslationKey()).getString().toLowerCase();
                    String blockIdPath = Registries.BLOCK.getId(block).getPath().toLowerCase();
                    globalSearchStrings.put(block, blockName + "|" + blockIdPath);
                }
            }
        }
        
        public int getPriority() {
            return priority;
        }
    }

    private List<BlockCategory> getAllRelevantBlocks() {
        // 强制重建缓存以反映新的分类系统
        cachedCategories = null;

        System.out.println("正在重建地形方块分类缓存...");
        Map<String, BlockCategory> categoryMap = new LinkedHashMap<>();
        Set<String> processedBlockIds = new HashSet<>();

        for (Block block : Registries.BLOCK) {
                String blockId = Registries.BLOCK.getId(block).toString();

                if (processedBlockIds.contains(blockId)) {
                    continue;
                }
                processedBlockIds.add(blockId);

                List<String> categoryKeys = getCategoriesForBlock(block);

                for (String categoryKey : categoryKeys) {
                    BlockCategory category = categoryMap.computeIfAbsent(categoryKey,
                        key -> new BlockCategory(key, getCategoryPriority(key)));
                    category.addBlock(block);
                }
            }

        cachedCategories = new ArrayList<>(categoryMap.values());
        cachedCategories.sort(Comparator.comparingInt(BlockCategory::getPriority));

        for (BlockCategory category : cachedCategories) {
            category.blocks.sort(Comparator.comparing(b -> Registries.BLOCK.getId(b).getPath()));
        }

        // 打印分类统计信息
        System.out.println("地形方块分类完成，共 " + cachedCategories.size() + " 个分类：");
        for (BlockCategory category : cachedCategories) {
            System.out.println("  " + category.getTranslatedName().getString() + ": " + category.blocks.size() + " 个方块");
            
            // 特别打印树叶分类中的所有方块
            if (category.translationKey.equals("pushdozer.category.leaves")) {
                System.out.println("    树叶分类中的方块：");
                for (Block block : category.blocks) {
                    String blockId = Registries.BLOCK.getId(block).getPath();
                    System.out.println("      - " + blockId);
                }
                
                // 检查是否包含目标方块
                boolean hasCherryLeaves = category.blocks.stream().anyMatch(b -> Registries.BLOCK.getId(b).getPath().equals("cherry_leaves"));
                boolean hasFloweringAzaleaLeaves = category.blocks.stream().anyMatch(b -> Registries.BLOCK.getId(b).getPath().equals("flowering_azalea_leaves"));
                System.out.println("    检查结果：");
                System.out.println("      cherry_leaves: " + (hasCherryLeaves ? "✅ 已包含" : "❌ 未包含"));
                System.out.println("      flowering_azalea_leaves: " + (hasFloweringAzaleaLeaves ? "✅ 已包含" : "❌ 未包含"));
            }
        }
        return cachedCategories;
    }
    
    private int getCategoryPriority(String categoryKey) {
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

    private List<String> getCategoriesForBlock(Block block) {
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