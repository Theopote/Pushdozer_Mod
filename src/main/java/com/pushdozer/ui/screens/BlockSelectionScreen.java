package com.pushdozer.ui.screens;

import java.util.*;
import java.util.stream.Collectors;

import com.pushdozer.config.PushdozerConfig;
import com.pushdozer.util.BlockDisplayIcons;
import com.pushdozer.util.ExceptionPolicy;

import net.minecraft.block.Block;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.input.CharInput;
import net.minecraft.client.input.KeyInput;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

public class BlockSelectionScreen extends Screen {
    private final PushdozerConfigScreen parent;
    private final PushdozerConfig config;
    private List<BlockCategory> blockCategories;
    private final Set<Block> selectedBlocks; // 改为HashSet以提高性能
    
    // 性能优化：静态缓存
    private static List<BlockCategory> cachedCategories = null;
    // 全局搜索字符串缓存 - 性能优化
    private static Map<Block, String> globalSearchStrings = new HashMap<>();
    
    private int currentPage = 0;
    private String searchText = "";
    private TextFieldWidget searchBox;
    
    // UI常量
    private static final int BUTTON_HEIGHT = 20;
    private static final int BUTTON_WIDTH = 80;
    private static final int BUTTON_SPACING = 10;
    private static final int SEARCH_WIDTH = BUTTON_WIDTH * 3 + BUTTON_SPACING * 2;
    private static final int CATEGORIES_PER_PAGE = 4;
    private static final int CATEGORY_BUTTON_HEIGHT = 20;
    private static final int BLOCK_SIZE = 20;
    private static final int TOP_MARGIN = 35;
    private static final int BOTTOM_MARGIN = 10;
    private static final int SEARCH_BOTTOM_SPACING = 5;
    private static final int PAGE_NUMBER_SPACING = 10;
    
    // UI样式常量 - 参考NaturalBlockSelectionScreen
    private static final int PANEL_BACKGROUND_COLOR = 0x40000000;
    private static final int BLOCK_BORDER_COLOR = 0xFF8B8B8B;
    private static final int BLOCK_BACKGROUND_COLOR = 0xFF373737;
    private static final int BLOCK_HOVER_COLOR = 0xFF555555;
    private static final int SCROLL_BAR_BACKGROUND_COLOR = 0x33FFFFFF;
    private static final int SCROLL_BAR_COLOR = 0xFFAAAAAA;
    private static final int SCROLL_BAR_HIGHLIGHT_COLOR = 0xFFFFFFFF;
    private static final int SCROLL_BAR_SHADOW_COLOR = 0xFF555555;
    private static final int SELECTED_BLOCK_BORDER_COLOR = 0xFFFFFFFF; // 选中方块的白色边框

    public BlockSelectionScreen(PushdozerConfigScreen parent, PushdozerConfig config) {
        super(Text.translatable("pushdozer.screen.block_selection.title"));
        this.parent = parent;
        this.config = config;
        this.blockCategories = getAllRelevantBlocks();
        this.selectedBlocks = new HashSet<>(config.getBreakableBlocks());
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

    // 提取重复的搜索框和按钮添加逻辑
    private void addSearchAndButtons(boolean restoreFocus) {
        // 计算布局位置
        int totalWidth = BUTTON_WIDTH * 3 + BUTTON_SPACING * 2;
        int startX = (width - totalWidth) / 2;
        int buttonY = height - BUTTON_HEIGHT - BOTTOM_MARGIN;
        int searchBoxY = buttonY - BUTTON_HEIGHT - SEARCH_BOTTOM_SPACING;
        
        // 添加搜索框
        searchBox = new TextFieldWidget(textRenderer, startX, searchBoxY, SEARCH_WIDTH, BUTTON_HEIGHT, 
            Text.translatable("pushdozer.screen.block_selection.search"));
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

        // 遍历所有类别和方块
        for (int i = 0; i < blockCategories.size(); i++) {
            BlockCategory category = blockCategories.get(i);
            for (int j = 0; j < category.blocks.size(); j++) {
                Block block = category.blocks.get(j);
                // 获取方块的本地化名称和ID
                String blockName = Text.translatable(block.getTranslationKey()).getString().toLowerCase();
                String blockId = Registries.BLOCK.getId(block).getPath().toLowerCase();
                
                // 检查是否匹配
                if (blockName.contains(searchText.toLowerCase()) || blockId.contains(searchText.toLowerCase())) {
                    // 计算并切换到包含该方块的页面
                    int targetPage = i / CATEGORIES_PER_PAGE;
                    if (currentPage != targetPage) {
                        currentPage = targetPage;
                        refreshCategoryButtons();
                    }
                    
                    // 找到对应的ScrollablePanel并滚动到目标方块
                    scrollToBlock(block, j);
                    return;
                }
            }
        }
    }

    private void scrollToBlock(Block targetBlock, int blockIndex) {
        // 找到包含目标方块的ScrollablePanel
        for (Element child : children()) {
            if (child instanceof ScrollablePanel panel) {
                if (panel.blocks.contains(targetBlock)) {
                    // 计算方块在面板中的位置
                    int row = blockIndex / 4;
                    int targetScrollOffset = row * (BLOCK_SIZE + ScrollablePanel.BLOCK_SPACING);
                    
                    // 确保目标方块在可见区域内
                    int maxScroll = Math.max(0, (panel.blocks.size() + 3) / 4 - panel.getVisibleRows()) * (BLOCK_SIZE + ScrollablePanel.BLOCK_SPACING);
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
        // 性能优化：使用全局缓存的搜索字符串
        String searchLower = searchText.toLowerCase();
        
        // 从全局缓存获取搜索字符串
        String searchString = globalSearchStrings.get(block);
        if (searchString != null) {
            return searchString.contains(searchLower);
        }
        
        // 如果缓存中没有找到，回退到原始方法
        String blockName = Text.translatable(block.getTranslationKey()).getString().toLowerCase();
        String blockId = Registries.BLOCK.getId(block).getPath().toLowerCase();
        return blockName.contains(searchLower) || blockId.contains(searchLower);
    }

    private void addPageButtons() {
        ButtonWidget prevButton = ButtonWidget.builder(net.minecraft.text.Text.literal("<"), button -> changePage(-1))
                .dimensions(10, height / 2, 20, 20)
                .tooltip(Tooltip.of(net.minecraft.text.Text.translatable("pushdozer.screen.block_selection.previous_page")))
                .build();
        addDrawableChild(prevButton);

        ButtonWidget nextButton = ButtonWidget.builder(net.minecraft.text.Text.literal(">"), button -> changePage(1))
                .dimensions(width - 30, height / 2, 20, 20)
                .tooltip(Tooltip.of(net.minecraft.text.Text.translatable("pushdozer.screen.block_selection.next_page")))
                .build();
        addDrawableChild(nextButton);
    }

    private void changePage(int delta) {
        int totalPages = (blockCategories.size() - 1) / CATEGORIES_PER_PAGE + 1;
        currentPage += delta;
        
        // 实现循环导航
        if (currentPage < 0) {
            currentPage = totalPages - 1; // 从第一页往前翻到最后一页
        } else if (currentPage >= totalPages) {
            currentPage = 0; // 从最后一页往后翻到第一页
        }
        
        refreshCategoryButtons();
    }

    private void refreshCategoryButtons() {
        boolean wasFocused = searchBox != null && searchBox.isFocused();
        
        clearChildren();
        
        // 重新添加搜索框，优化底部布局
        addSearchAndButtons(wasFocused);

        int categorySpacing = 8;
        int blockSpacing = 4;
        int categoryToBlockSpacing = 10;
        int scrollBarWidth = 4;

        int categoryWidth = BLOCK_SIZE * 4 + blockSpacing * 3 + scrollBarWidth;
        int totalWidth2 = CATEGORIES_PER_PAGE * categoryWidth + (CATEGORIES_PER_PAGE - 1) * categorySpacing;
        int startX2 = (width - totalWidth2) / 2;
        int startY = TOP_MARGIN; // 调整起始Y坐标，为顶部显示留出空间

        int startIndex = currentPage * CATEGORIES_PER_PAGE;
        for (int i = 0; i < CATEGORIES_PER_PAGE && startIndex + i < blockCategories.size(); i++) {
            BlockCategory category = blockCategories.get(startIndex + i);
            int columnX = startX2 + i * (categoryWidth + categorySpacing);

            // 获取过滤后的方块数量
            List<Block> filteredBlocks = category.blocks.stream()
                .filter(this::matchesSearch)
                .collect(Collectors.toList());
            
            // 创建带方块总数的标签文本
            Text categoryText = Text.literal(category.getTranslatedName().getString() + 
                " (" + filteredBlocks.size() + ")");
            
            addDrawableChild(ButtonWidget.builder(
                categoryText,
                button -> toggleCategorySelection(category))
                .dimensions(columnX, startY, categoryWidth, CATEGORY_BUTTON_HEIGHT)
                .build());

            // 面板高度：增加2像素，优化底部布局
            int bottomSpace = BUTTON_HEIGHT + BOTTOM_MARGIN + BUTTON_HEIGHT + SEARCH_BOTTOM_SPACING + PAGE_NUMBER_SPACING; // 按钮+底部间距+搜索框+搜索框间距+页码间距
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
        // 按钮位置：距离底部10像素
        int y = height - BUTTON_HEIGHT - BOTTOM_MARGIN;
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

    private void renderCustomBackground(DrawContext context, int mouseX, int mouseY, float delta) {
        // 绘制半透明背景，避免使用renderBackground导致的blur冲突
        context.fill(0, 0, width, height, 0x80000000);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        // 使用自定义背景渲染避免blur冲突
        renderCustomBackground(context, mouseX, mouseY, delta);

        super.render(context, mouseX, mouseY, delta);
        
        // 绘制顶部标题背景
        int titleBackgroundHeight = 35;
        context.fill(0, 0, width, titleBackgroundHeight, 0x80000000);
        
        // 绘制标题
        context.drawCenteredTextWithShadow(textRenderer, getTitle(), width / 2, 8, 0xFFFFFF);

        // 将已选择数量和总数放在同一行显示
        int totalBlocks = blockCategories.stream().mapToInt(cat -> cat.blocks.size()).sum();
        String countText = String.format("已选择: %d / 总计: %d 个方块", selectedBlocks.size(), totalBlocks);
        int countColor = selectedBlocks.isEmpty() ? 0xFFAAAAAA : 0xFFFFFFFF; // 白色表示有选择，灰色表示无选择
        context.drawCenteredTextWithShadow(textRenderer, Text.literal(countText), width / 2, 20, countColor);

        // 页码位置：搜索框上方10像素
        String pageText = getPageText();
        int searchBoxY = height - BUTTON_HEIGHT - BOTTOM_MARGIN - BUTTON_HEIGHT - SEARCH_BOTTOM_SPACING;
        context.drawCenteredTextWithShadow(textRenderer, Text.literal(pageText), width / 2, searchBoxY - PAGE_NUMBER_SPACING, 0xFFFFFF);
    }
    private void toggleBlockSelection(Block block) {
        if (selectedBlocks.contains(block)) {
            selectedBlocks.remove(block);
        } else {
            selectedBlocks.add(block);
        }
    }

    private void toggleCategorySelection(BlockCategory category) {
        boolean allSelected = selectedBlocks.containsAll(category.blocks);
        if (allSelected) {
            category.blocks.forEach(selectedBlocks::remove);
        } else {
            selectedBlocks.addAll(category.blocks);
        }
        refreshCategoryButtons();
    }

    private void selectAll() {
        selectedBlocks.clear();
        for (BlockCategory category : blockCategories) {
            selectedBlocks.addAll(category.blocks);
        }
        refreshCategoryButtons();
    }

    private void deselectAll() {
        selectedBlocks.clear();
        refreshCategoryButtons();
    }

    private void confirmSelection() {
        config.setBreakableBlocks(new ArrayList<>(selectedBlocks));
        MinecraftClient.getInstance().setScreen(parent);
    }

    // 修改 ScrollablePanel 类
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
        private static final long TOOLTIP_DELAY = 500; // 500ms延迟
        private Block lastHoveredBlock = null;

        public ScrollablePanel(int x, int y, int width, int height, List<Block> blocks) {
            super(x, y, width, height, net.minecraft.text.Text.empty(), button -> {}, DEFAULT_NARRATION_SUPPLIER);
            this.blocks = blocks;
        }
        
        // 动态计算可见行数
        private int getVisibleRows() {
            return height / (BLOCK_SIZE + BLOCK_SPACING);
        }

        @Override
        protected void drawIcon(DrawContext context, int mouseX, int mouseY, float delta) {
            int totalRows = (blocks.size() + 3) / 4;
            int visibleRows = getVisibleRows();

            Block hoveredBlock = null;

            // 绘制面板背景
            int backgroundWidth = 4 * BLOCK_SIZE + 3 * BLOCK_SPACING;
            int backgroundHeight = visibleRows * (BLOCK_SIZE + BLOCK_SPACING) - BLOCK_SPACING;
            context.fill(getX(), getY(), getX() + backgroundWidth, getY() + backgroundHeight, PANEL_BACKGROUND_COLOR);

            context.enableScissor(getX(), getY(), getX() + width, getY() + height);
            for (int i = 0; i < blocks.size(); i++) {
                int row = i / 4;
                int col = i % 4;
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

            // 绘制滚动条背景槽
            context.fill(getX() + width - SCROLL_BAR_WIDTH, getY(), getX() + width, getY() + backgroundHeight, SCROLL_BAR_BACKGROUND_COLOR);

            // 只有在需要滚动时才绘制滚动条
            if (totalRows > visibleRows) {
                int scrollBarHeight = Math.max(20, backgroundHeight * visibleRows / totalRows);
                int scrollBarY = getY() + (int) ((backgroundHeight - scrollBarHeight) * (float) scrollOffset / ((totalRows - visibleRows) * (BLOCK_SIZE + BLOCK_SPACING)));
                
                // 绘制滚动条
                drawScrollBar(context, getX() + width - SCROLL_BAR_WIDTH, scrollBarY, scrollBarHeight);
            }

            // 在最后渲染工具提示 - 添加延迟
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
            // 确定边框颜色：如果被选中则使用白色，否则使用默认灰色
            int borderColor = selectedBlocks.contains(block) ? SELECTED_BLOCK_BORDER_COLOR : BLOCK_BORDER_COLOR;
            
            // 绘制边框和背景 - 边框向内扩展
            context.fill(x, y, x + BLOCK_SIZE, y + BLOCK_SIZE, borderColor);
            context.fill(x + 1, y + 1, x + BLOCK_SIZE - 1, y + BLOCK_SIZE - 1, BLOCK_BACKGROUND_COLOR);
            
            // 检查鼠标悬停
            if (mouseX >= x && mouseX < x + BLOCK_SIZE && mouseY >= y && mouseY < y + BLOCK_SIZE) {
                context.fill(x + 1, y + 1, x + BLOCK_SIZE - 1, y + BLOCK_SIZE - 1, BLOCK_HOVER_COLOR);
            }
            
            // 特殊处理没有物品形态的方块
            ItemStack displayStack = getDisplayStack(block);
            if (!displayStack.isEmpty()) {
                context.drawItem(displayStack, x + (BLOCK_SIZE - 16) / 2, y + (BLOCK_SIZE - 16) / 2);
            }
            
            // 检查是否为墙面变体，如果是则绘制下划线
            String blockId = Registries.BLOCK.getId(block).getPath();
            if (blockId.contains("wall_head") || blockId.contains("wall_skull") || blockId.contains("wall_sign")) {
                // 绘制下划线，表示这是墙面变体
                int underlineY = y + BLOCK_SIZE - 3;
                int underlineColor = selectedBlocks.contains(block) ? 0xFFFFFFFF : 0xFF666666;
                context.fill(x + 2, underlineY, x + BLOCK_SIZE - 2, underlineY + 1, underlineColor);
            }
            
            // 如果方块被选中，绘制背景高亮和选中标记
            if (selectedBlocks.contains(block)) {
                context.fill(x + 1, y + 1, x + BLOCK_SIZE - 1, y + BLOCK_SIZE - 1, 0x80FFFFFF);
                context.drawText(textRenderer, net.minecraft.text.Text.literal("☑"), x + BLOCK_SIZE - 8, y + BLOCK_SIZE - 9, 0xFF000000, false);
            }
        }

        private ItemStack getDisplayStack(Block block) {
            return BlockDisplayIcons.getDisplayStack(block);
        }

        @Override
        public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
            if (isMouseOver(mouseX, mouseY)) {
                int totalRows = (blocks.size() + 3) / 4;
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
            int button = click.button();
            if (isMouseOver(mouseX, mouseY)) {
                // 检查是否点击了滚动条区域
                int totalRows = (blocks.size() + 3) / 4;
                int backgroundHeight = getVisibleRows() * (BLOCK_SIZE + BLOCK_SPACING) - BLOCK_SPACING;
                
                // 检查是否点击了滚动条区域
                if (mouseX >= getX() + width - SCROLL_BAR_WIDTH && mouseX < getX() + width && 
                    mouseY >= getY() && mouseY < getY() + backgroundHeight) {
                    if (totalRows > getVisibleRows()) {
                        isDraggingScrollBar = true;
                        dragStartY = (int) mouseY;
                        dragStartScrollOffset = scrollOffset;
                        return true;
                    }
                }
                
                // 检查是否点击了方块
                int startRow = scrollOffset / (BLOCK_SIZE + BLOCK_SPACING);
                for (int i = startRow * 4; i < Math.min(blocks.size(), (startRow + getVisibleRows()) * 4); i++) {
                    int row = i / 4 - startRow;
                    int col = i % 4;
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
                int totalRows = (blocks.size() + 3) / 4;
                int backgroundHeight = getVisibleRows() * (BLOCK_SIZE + BLOCK_SPACING) - BLOCK_SPACING;
                int maxScroll = Math.max(0, (totalRows - getVisibleRows()) * (BLOCK_SIZE + BLOCK_SPACING));
                
                // 计算拖动距离对应的滚动偏移
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
            // 绘制滚动条背景
            context.fill(x, y, x + SCROLL_BAR_WIDTH, y + height, SCROLL_BAR_BACKGROUND_COLOR);
            
            // 绘制3D滚动条
            context.fill(x, y, x + SCROLL_BAR_WIDTH, y + height, SCROLL_BAR_COLOR);
            context.fill(x, y, x + SCROLL_BAR_WIDTH - 1, y + 1, SCROLL_BAR_HIGHLIGHT_COLOR);
            context.fill(x, y, x + 1, y + height - 1, SCROLL_BAR_HIGHLIGHT_COLOR);
            context.fill(x + SCROLL_BAR_WIDTH - 1, y, x + SCROLL_BAR_WIDTH, y + height, SCROLL_BAR_SHADOW_COLOR);
            context.fill(x, y + height - 1, x + SCROLL_BAR_WIDTH, y + height, SCROLL_BAR_SHADOW_COLOR);
        }
    }

    // 修改 BlockCategory 类 - 支持多分类和搜索字符串缓存
    private static class BlockCategory {
        String translationKey;  // 分类翻译键
        List<Block> blocks;
        // 分类优先级，用于排序
        private int priority;
        // 使用Set来跟踪已添加的方块ID，避免重复
        private Set<String> addedBlockIds;

        BlockCategory(String translationKey, int priority) {
            this.translationKey = translationKey;
            this.priority = priority;
            this.blocks = new ArrayList<>();
            this.addedBlockIds = new HashSet<>();
        }

        // 添加获取翻译后文本的方法
        public Text getTranslatedName() {
            return Text.translatable(translationKey);
        }
        
        // 添加方块时同时缓存搜索字符串到全局缓存
        public void addBlock(Block block) {
            String blockId = Registries.BLOCK.getId(block).toString();
            if (!addedBlockIds.contains(blockId)) { // 使用方块ID避免重复添加
                blocks.add(block);
                addedBlockIds.add(blockId);
                // 预计算搜索字符串并存储到全局缓存
                if (!globalSearchStrings.containsKey(block)) {
                    String blockName = Text.translatable(block.getTranslationKey()).getString().toLowerCase();
                    String blockIdPath = Registries.BLOCK.getId(block).getPath().toLowerCase();
                    globalSearchStrings.put(block, blockName + "|" + blockIdPath);
                }
            }
        }
        
        // 获取分类优先级
        public int getPriority() {
            return priority;
        }
    }

    private List<BlockCategory> getAllRelevantBlocks() {
        // 性能优化：使用静态缓存
        if (cachedCategories == null) {
            // 使用 Map 来动态创建和填充分类
            Map<String, BlockCategory> categoryMap = new LinkedHashMap<>();
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
                    BlockCategory category = categoryMap.computeIfAbsent(categoryKey, 
                        key -> new BlockCategory(key, getCategoryPriority(key)));
                    category.addBlock(block);
                }
            }

            // 按优先级排序分类，并对每个分类内的方块进行排序
            cachedCategories = new ArrayList<>(categoryMap.values());
            cachedCategories.sort(Comparator.comparingInt(BlockCategory::getPriority));
            
            // 对每个分类内的方块按ID排序
            for (BlockCategory category : cachedCategories) {
                category.blocks.sort(Comparator.comparing(b -> Registries.BLOCK.getId(b).getPath()));
            }
        }
        return cachedCategories;
    }
    
    // 获取分类优先级
    private int getCategoryPriority(String categoryKey) {
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
    private List<String> getCategoriesForBlock(Block block) {
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
    private boolean isTerrainManual(String blockId) {
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
    
    private boolean isWoodManual(String blockId) {
        return blockId.contains("bamboo_block") || blockId.contains("stripped_bamboo") ||
               blockId.contains("hyphae") || blockId.equals("bamboo_mosaic");
    }
    
    private boolean isPlantManual(String blockId) {
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
    
    private boolean isCropManual(String blockId) {
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
    
    private boolean isFungiManual(String blockId) {
        return blockId.equals("brown_mushroom") || blockId.equals("red_mushroom") ||
               blockId.equals("brown_mushroom_block") || blockId.equals("red_mushroom_block") ||
               blockId.equals("mushroom_stem");
    }
    
    private boolean isCoralManual(String blockId) {
        return blockId.contains("coral") || blockId.contains("coral_block") ||
               blockId.contains("coral_fan") || blockId.contains("coral_wall_fan");
    }
    
    private boolean isNetherManual(String blockId) {
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
    
    private boolean isCopperManual(String blockId) {
        return blockId.contains("copper") && !blockId.contains("copper_ore");
    }
    
    private boolean isAmethystManual(String blockId) {
        return blockId.contains("amethyst");
    }
    
    private boolean isValuableManual(String blockId) {
        return blockId.endsWith("_block") && 
               (blockId.contains("gold") || blockId.contains("iron") || blockId.contains("diamond") || 
                blockId.contains("emerald") || blockId.contains("netherite") || blockId.contains("lapis"));
    }
    
    private boolean isRedstoneManual(String blockId) {
        return blockId.contains("redstone") || blockId.contains("piston") ||
               blockId.contains("dispenser") || blockId.contains("observer") ||
               blockId.contains("hopper") || blockId.contains("comparator") ||
               blockId.contains("repeater") || blockId.equals("tnt") ||
               blockId.contains("target") || blockId.contains("note_block") ||
               blockId.contains("jukebox") || blockId.contains("daylight_detector");
    }
    
    private boolean isDecorativeManual(String blockId) {
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
    
    private boolean isDyedManual(String blockId) {
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
    
    private boolean isFunctionalManual(String blockId) {
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
    
    private boolean isHeadManual(String blockId) {
        // 包含所有头颅方块，包括地面变体和墙面变体
        return blockId.endsWith("_head") || blockId.endsWith("_skull");
    }
    
    private boolean isCaveManual(String blockId) {
        return blockId.contains("dripstone") || 
               (blockId.contains("spore") && !blockId.equals("spore_blossom")) ||
               blockId.contains("lichen") || blockId.contains("moss") ||
               blockId.contains("sculk");
    }
    
    private boolean isFluidManual(String blockId) {
        return blockId.equals("water") || blockId.equals("lava");
    }
    
  
    
    private boolean isBrickManual(String blockId) {
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
    
    private boolean isTechnicalManual(String blockId) {
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