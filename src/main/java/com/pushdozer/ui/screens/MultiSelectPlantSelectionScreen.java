package com.pushdozer.ui.screens;

import com.pushdozer.util.BlockDisplayIcons;

import java.util.List;
import java.util.function.Consumer;
import java.util.ArrayList;
import java.util.Set;
import java.util.HashSet;
import java.util.Map;
import java.util.HashMap;

import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.registry.Registries;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.narration.NarrationMessageBuilder;
import net.minecraft.client.gui.screen.narration.NarrationPart;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.Text;
import org.jetbrains.annotations.NotNull;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.block.BlockState;
import net.minecraft.block.PlantBlock;
import net.minecraft.block.TallPlantBlock;
import net.minecraft.block.CropBlock;
import net.minecraft.block.SaplingBlock;
import net.minecraft.block.VineBlock;

/**
 * 多选植物选择屏幕
 * 专门用于批量种植配置的自定义植物选择，支持多选和分类
 */
public class MultiSelectPlantSelectionScreen extends Screen {
    private final Screen parent;
    private final Consumer<List<Block>> onBlocksSelected;
    private final List<Block> plantBlocks;
    private List<Block> filteredBlocksCache;
    private String currentSearchText = "";
    private ScrollableBlockPanel scrollPanel;
    private final Set<Block> selectedBlocks = new HashSet<>(); // 多选的方块集合
    
    // 分类相关
    private final Map<String, List<Block>> categorizedBlocks = new HashMap<>();
    private String currentCategory = "all"; // 当前选中的分类
    private final List<ButtonWidget> categoryButtons = new ArrayList<>();
    
    // 静态缓存优化
    private static List<Block> cachedPlantBlocks = null;
    private static Map<String, List<Block>> cachedCategorizedBlocks = null;
    private static long lastCacheTime = 0;
    private static final long CACHE_DURATION = 30000; // 30秒缓存
    
    // 搜索防抖优化
    private long lastSearchTime = 0;
    private static final long SEARCH_DEBOUNCE_DELAY = 300; // 300ms防抖延迟
    private final List<Block> reusableFilteredList = new ArrayList<>(); // 可重用的过滤列表

    // 常量定义
    private static final int BLOCKS_PER_ROW = 8;
    private static final int BLOCK_SIZE = 20;
    private static final int BLOCK_SPACING = 4;
    private static final int BUTTON_HEIGHT = 20;
    private static final int SCROLL_BAR_WIDTH = 4;
    private static final int PANEL_PADDING = 5; // 面板内边距
    private static final int ELEMENT_SPACING = 5; // 元素间距
    private static final int PANEL_BACKGROUND_COLOR = 0x40000000;
    private static final int BLOCK_BORDER_COLOR = 0xFF8B8B8B;
    private static final int BLOCK_BACKGROUND_COLOR = 0xFF373737;
    private static final int BLOCK_HOVER_COLOR = 0xFF555555;

    private static final int SCROLL_BAR_BACKGROUND_COLOR = 0x33FFFFFF;
    private static final int SCROLL_BAR_COLOR = 0xFFAAAAAA;
    private static final int SCROLL_BAR_HIGHLIGHT_COLOR = 0xFFFFFFFF;
    private static final int SCROLL_BAR_SHADOW_COLOR = 0xFF555555;
    private static final int SELECTED_BLOCK_BORDER_COLOR = 0xFFFFFFFF; // 选中方块的白色边框

    public MultiSelectPlantSelectionScreen(Screen parent, Consumer<List<Block>> onBlocksSelected, List<Block> initialSelectedBlocks) {
        super(Text.translatable("pushdozer.screen.plant_selection.title"));
        this.parent = parent;
        this.onBlocksSelected = onBlocksSelected;
        this.plantBlocks = new ArrayList<>(); 
        this.filteredBlocksCache = this.plantBlocks;
        if (initialSelectedBlocks != null) {
            this.selectedBlocks.addAll(initialSelectedBlocks);
        }
    }

    @Override
    protected void init() {
        super.init();

        // 使用静态缓存优化
        if (this.plantBlocks.isEmpty()) {
            // 添加 null 检查
            if (this.client == null) {
                // 如果 client 为空，延迟到下次调用
                return;
            }
            
            // 检查缓存是否有效
            long currentTime = System.currentTimeMillis();
            if (cachedPlantBlocks == null || cachedCategorizedBlocks == null || 
                (currentTime - lastCacheTime) > CACHE_DURATION) {
                // 缓存失效，重新计算
                cachedPlantBlocks = getPlantBlocks();
                categorizeBlocksStatic(cachedPlantBlocks);
                lastCacheTime = currentTime;
            }
            
            // 强制清除缓存以确保新逻辑生效（临时措施）
            cachedPlantBlocks = null;
            cachedCategorizedBlocks = null;
            cachedPlantBlocks = getPlantBlocks();
            cachedCategorizedBlocks = categorizeBlocksStatic(cachedPlantBlocks);
            lastCacheTime = currentTime;
            
            // 使用缓存数据
            this.plantBlocks.addAll(cachedPlantBlocks);
            this.categorizedBlocks.putAll(cachedCategorizedBlocks);
            this.filteredBlocksCache = this.plantBlocks;
        }

        // --- 改进的健壮布局 ---
        
        // 1. 定义页脚元素 - 改进搜索框宽度计算
        int buttonWidth = 60;
        int buttonSpacing = 10;
        int totalButtonWidth = buttonWidth * 3 + buttonSpacing * 2; // 三个按钮
        int buttonStartX = (width - totalButtonWidth) / 2;
        
        // 搜索框宽度更灵活，设置为屏幕宽度的固定比例，并设置最小和最大宽度
        int searchBoxWidth = Math.min(Math.max((int)(width * 0.4), 200), 400);
        int searchBoxX = (width - searchBoxWidth) / 2;

        // 2. 从下往上放置页脚元素
        int buttonsY = this.height - 20 - BUTTON_HEIGHT; // 距离底部20px
        int searchBoxY = buttonsY - ELEMENT_SPACING - BUTTON_HEIGHT; // 在按钮上方

        // 3. 定义顶部和底部边距
        int topMargin = 70; // 减少顶部边距，为方块展示界面腾出更多空间
        int bottomMargin = this.height - searchBoxY + ELEMENT_SPACING; // 搜索框到屏幕底部的总高度

        // 4. 计算滚动面板的尺寸和位置 - 添加最小高度检查
        int panelContentWidth = BLOCKS_PER_ROW * (BLOCK_SIZE + BLOCK_SPACING) - BLOCK_SPACING;
        int panelWidth = panelContentWidth + SCROLL_BAR_WIDTH + PANEL_PADDING * 2;
        int panelX = (this.width - panelWidth) / 2;
        int panelHeight = Math.max(100, this.height - topMargin - bottomMargin); // 确保最小高度

        // 5. 添加分类按钮
        addCategoryButtons(); // 在y=50位置添加分类按钮
        updateCategoryButtonStates(); // 初始化按钮状态

        // 6. 添加所有UI元素
        scrollPanel = new ScrollableBlockPanel(panelX, topMargin, panelWidth, panelHeight, filteredBlocksCache);
        addDrawableChild(scrollPanel);

        TextFieldWidget searchBox = new TextFieldWidget(textRenderer, searchBoxX, searchBoxY, searchBoxWidth, BUTTON_HEIGHT, Text.empty());
        searchBox.setMaxLength(50);
        searchBox.setDrawsBackground(true);
        searchBox.setVisible(true);
        searchBox.setEditable(true);
        searchBox.setText(""); // 清空初始文本
        searchBox.setChangedListener(this::onSearchTextChanged);
        searchBox.setPlaceholder(Text.translatable("pushdozer.screen.plant_selection.search_hint"));
        addDrawableChild(searchBox);

        // 三个按钮：全选、清空、确定、取消
        addDrawableChild(ButtonWidget.builder(Text.translatable("pushdozer.button.select_all"), button -> selectAll())
                .dimensions(buttonStartX, buttonsY, buttonWidth, BUTTON_HEIGHT).build());
        
        addDrawableChild(ButtonWidget.builder(Text.translatable("pushdozer.button.clear_all"), button -> clearAll())
                .dimensions(buttonStartX + buttonWidth + buttonSpacing, buttonsY, buttonWidth, BUTTON_HEIGHT).build());
        
        addDrawableChild(ButtonWidget.builder(Text.translatable("gui.ok"), button -> onConfirm())
                .dimensions(buttonStartX + 2 * (buttonWidth + buttonSpacing), buttonsY, buttonWidth, BUTTON_HEIGHT).build());
    }

    /**
     * 添加分类按钮
     */
    private void addCategoryButtons() {
        categoryButtons.clear();
        
        // 定义分类按钮（移除未实现的 seeds 类别）
        String[] categories = {
            "all", "saplings", "small_flowers", "large_flowers", "grass_ferns",
            "mushrooms", "crops", "aquatic_plants", "coral", "nether_plants",
            "potted", "other"
        };
        
        // 使用更小的按钮宽度以适应屏幕
        int buttonWidth = 60;
        int buttonSpacing = 3;
        int buttonsPerRow = 7; // 每行7个按钮
        
        for (int i = 0; i < categories.length; i++) {
            String category = categories[i];
            int row = i / buttonsPerRow;
            int col = i % buttonsPerRow;
            
            int totalRowWidth = buttonsPerRow * buttonWidth + (buttonsPerRow - 1) * buttonSpacing;
            int startX = (width - totalRowWidth) / 2;
            
            int x = startX + col * (buttonWidth + buttonSpacing);
            int buttonY = 25 + row * (BUTTON_HEIGHT + 2); // 将按钮向上移动25像素
            
            ButtonWidget button = ButtonWidget.builder(
                Text.translatable("pushdozer.category.plant." + category), 
                buttonWidget -> selectCategory(category)
            ).dimensions(x, buttonY, buttonWidth, BUTTON_HEIGHT).build();
            
            categoryButtons.add(button);
            addDrawableChild(button);
        }
    }
    
    /**
     * 选择分类
     */
    private void selectCategory(String category) {
        this.currentCategory = category;
        updateFilteredBlocks();
        if (scrollPanel != null) {
            scrollPanel.updateBlocks(filteredBlocksCache);
        }
        
        // 更新按钮状态
        updateCategoryButtonStates();
    }
    
    /**
     * 更新分类按钮状态
     */
    private void updateCategoryButtonStates() {
        String[] categories = {
            "all", "saplings", "small_flowers", "large_flowers", "grass_ferns",
            "mushrooms", "crops", "aquatic_plants", "coral", "nether_plants",
            "potted", "other"
        };
        
        for (int i = 0; i < categoryButtons.size() && i < categories.length; i++) {
            ButtonWidget button = categoryButtons.get(i);
            String category = categories[i];
            boolean isSelected = currentCategory.equals(category);
            
            // 更新按钮文本颜色
            if (isSelected) {
                button.setMessage(Text.translatable("pushdozer.category.plant." + category).copy().formatted(net.minecraft.util.Formatting.YELLOW));
            } else {
                button.setMessage(Text.translatable("pushdozer.category.plant." + category));
            }
        }
    }

    @Override
    public void tick() {
        super.tick();
    }
    
    private void selectAll() {
        selectedBlocks.clear();
        selectedBlocks.addAll(filteredBlocksCache);
        // 提示用户全选仅限当前视图
        if (client != null && client.player != null) {
            client.player.sendMessage(Text.literal("已全选当前视图中的 " + filteredBlocksCache.size() + " 种植物"), false);
        }
    }
    
    private void clearAll() {
        selectedBlocks.clear();
    }
    
    private void onConfirm() {
        // 返回选中的方块列表
        onBlocksSelected.accept(new ArrayList<>(selectedBlocks));
        close();
    }

    private void onSearchTextChanged(String text) {
        if (!text.equals(currentSearchText)) {
            currentSearchText = text.toLowerCase();
            
            // 搜索防抖：延迟更新以避免频繁过滤
            long currentTime = System.currentTimeMillis();
            if (currentTime - lastSearchTime < SEARCH_DEBOUNCE_DELAY) {
                // 如果距离上次搜索时间太短，延迟执行
                return;
            }
            lastSearchTime = currentTime;
            
            updateFilteredBlocks();
            if (scrollPanel != null) {
                scrollPanel.updateBlocks(filteredBlocksCache);
            }
        }
    }
    
    private void updateFilteredBlocks() {
        List<Block> baseBlocks;
        
        // 根据当前分类选择基础方块列表
        if ("all".equals(currentCategory)) {
            baseBlocks = plantBlocks;
        } else {
            baseBlocks = categorizedBlocks.getOrDefault(currentCategory, new ArrayList<>());
        }
        
        // 性能优化：使用可重用列表避免频繁创建新列表
        reusableFilteredList.clear();
        
        // 应用搜索过滤
        if (currentSearchText.isEmpty()) {
            reusableFilteredList.addAll(baseBlocks);
        } else {
            for (Block block : baseBlocks) {
                String translation = Text.translatable(block.getTranslationKey()).getString().toLowerCase();
                String key = block.getTranslationKey().toLowerCase();
                if (translation.contains(currentSearchText) || key.contains(currentSearchText)) {
                    reusableFilteredList.add(block);
                }
            }
        }
        
        filteredBlocksCache = new ArrayList<>(reusableFilteredList);
        
        // 强制刷新面板以更新选中状态显示
        if (scrollPanel != null) {
            scrollPanel.updateBlocks(filteredBlocksCache);
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        // 使用自定义背景渲染避免blur冲突
        context.fill(0, 0, width, height, 0x80000000);
        
        super.render(context, mouseX, mouseY, delta);
        
        // 绘制顶部标题背景
        int titleBackgroundHeight = 25;
        context.fill(0, 0, width, titleBackgroundHeight, 0x80000000);
        
        // 绘制标题
        context.drawCenteredTextWithShadow(textRenderer, title, width / 2, 5, 0xFFFFFF);
        
        // 将已选择数量和总数放在同一行显示
        int totalPlants = plantBlocks.size();
        String countText = String.format("已选择: %d / 总计: %d 种植物", selectedBlocks.size(), totalPlants);
        int countColor = selectedBlocks.isEmpty() ? 0xFFAAAAAA : 0xFFFFFFFF; // 白色表示有选择，灰色表示无选择
        context.drawCenteredTextWithShadow(textRenderer, Text.literal(countText), width / 2, 15, countColor);
        
        // 在左侧中部显示当前分类信息
        int leftMargin = 20;
        int infoY = height / 2 - 30; // 屏幕中部偏上
        
        // 显示当前分类信息
        String categoryText = "当前分类: " + Text.translatable("pushdozer.category.plant." + currentCategory).getString();
        context.drawTextWithShadow(textRenderer, Text.literal(categoryText), leftMargin, infoY, 0xCCCCCC);
    }

    @Override
    public void close() {
        if (client != null) {
            client.setScreen(parent);
        }
    }

    private class ScrollableBlockPanel extends ClickableWidget {
        private List<Block> blocks;
        private int scrollOffset = 0;
        private boolean isDraggingScrollBar = false;
        private int dragStartY = 0;
        private int dragStartScrollOffset = 0;

        public ScrollableBlockPanel(int x, int y, int width, int height, List<Block> blocks) {
            super(x, y, width, height, Text.empty());
            this.blocks = blocks;
        }

        public void updateBlocks(List<Block> newBlocks) {
            this.blocks = newBlocks;
            this.scrollOffset = 0;
        }

        @Override
        protected void renderWidget(DrawContext context, int mouseX, int mouseY, float delta) {
            Block hoveredBlock = null;
            int hoveredX = 0, hoveredY = 0;

            // 1. 定义面板的实际内容区域边界
            int contentX = getX() + PANEL_PADDING;
            int contentY = getY() + PANEL_PADDING;
            int contentWidth = width - SCROLL_BAR_WIDTH - PANEL_PADDING * 2;
            int contentHeight = height - PANEL_PADDING * 2;

            // 2. 绘制面板背景
            context.fill(getX(), getY(), getX() + width - SCROLL_BAR_WIDTH, getY() + height, PANEL_BACKGROUND_COLOR);

            // 3. 启用裁剪
            context.enableScissor(contentX - 1, contentY - 1, contentX + contentWidth + 1, contentY + contentHeight + 1);

            // 计算方块网格的总尺寸
            int gridWidth = BLOCKS_PER_ROW * (BLOCK_SIZE + BLOCK_SPACING) - BLOCK_SPACING;
            int offsetX = (contentWidth - gridWidth) / 2;
            
            // 性能优化：只渲染可见的方块
            int startRow = Math.max(0, scrollOffset / (BLOCK_SIZE + BLOCK_SPACING));
            int endRow = Math.min((blocks.size() + BLOCKS_PER_ROW - 1) / BLOCKS_PER_ROW, 
                                 startRow + (contentHeight / (BLOCK_SIZE + BLOCK_SPACING)) + 1);
            
            for (int row = startRow; row <= endRow; row++) {
                for (int col = 0; col < BLOCKS_PER_ROW; col++) {
                    int i = row * BLOCKS_PER_ROW + col;
                    if (i >= blocks.size()) break;
                    
                    int blockX = contentX + offsetX + col * (BLOCK_SIZE + BLOCK_SPACING);
                    int blockY = contentY + row * (BLOCK_SIZE + BLOCK_SPACING) - scrollOffset;

                    // 只渲染可见的方块 - 边框向内收缩1像素
                    if (blockY + BLOCK_SIZE >= contentY && blockY < contentY + contentHeight) {
                    // 确定边框颜色：如果被选中则使用白色，否则使用默认灰色
                    boolean isSelected = selectedBlocks.contains(blocks.get(i));
                    int borderColor = isSelected ? SELECTED_BLOCK_BORDER_COLOR : BLOCK_BORDER_COLOR;
                    
                    // 绘制边框和背景 - 边框向内扩展
                    context.fill(blockX, blockY, blockX + BLOCK_SIZE, blockY + BLOCK_SIZE, borderColor);
                    context.fill(blockX + 1, blockY + 1, blockX + BLOCK_SIZE - 1, blockY + BLOCK_SIZE - 1, BLOCK_BACKGROUND_COLOR);

                    // 检查鼠标悬停
                    if (isMouseOver(mouseX, mouseY) && mouseX >= blockX && mouseX < blockX + BLOCK_SIZE && mouseY >= blockY && mouseY < blockY + BLOCK_SIZE) {
                        context.fill(blockX + 1, blockY + 1, blockX + BLOCK_SIZE - 1, blockY + BLOCK_SIZE - 1, BLOCK_HOVER_COLOR);
                        hoveredBlock = blocks.get(i);
                        hoveredX = mouseX;
                        hoveredY = mouseY;
                    }

                    // 绘制方块图标
                    ItemStack itemStack = getItemStack(i);
                    if (!itemStack.isEmpty()) {
                        context.drawItem(itemStack, blockX + (BLOCK_SIZE - 16) / 2, blockY + (BLOCK_SIZE - 16) / 2);
                    }

                    // 盆栽：在图标下方绘制大圆环作为标识（保持植物本体图标不变）
                    if (isPottedBlock(blocks.get(i))) {
                        int cx = blockX + BLOCK_SIZE / 2;
                        int cy = blockY + BLOCK_SIZE / 2;
                        int outerR = Math.max(7, (BLOCK_SIZE - 4) / 2); // 稍小于边框
                        int thickness = 3;
                        int ringColor = 0x88FFFFFF; // 半透明白色
                        drawRing(context, blockX, blockY, cx, cy, outerR, thickness, ringColor);
                    }

                    // 如果方块被选中，绘制背景高亮和选中标记
                    if (isSelected) {
                        context.fill(blockX + 1, blockY + 1, blockX + BLOCK_SIZE - 1, blockY + BLOCK_SIZE - 1, 0x80FFFFFF);
                        context.drawText(textRenderer, Text.literal("☑"), blockX + BLOCK_SIZE - 8, blockY + BLOCK_SIZE - 9, 0xFF000000, false);
                    }
                }
            }
        }

            // 绘制完所有方块后，关闭裁剪
            context.disableScissor();

            // 在裁剪区域外绘制滚动条和提示文本
            drawScrollBar(context);

            if (hoveredBlock != null) {
                context.drawTooltip(textRenderer, Text.translatable(hoveredBlock.getTranslationKey()), hoveredX, hoveredY);
            }
        }

        private boolean isPottedBlock(Block b) {
            String id = Registries.BLOCK.getId(b).getPath().toLowerCase();
            String key = b.getTranslationKey().toLowerCase();
            return id.startsWith("potted_") || key.contains("potted");
        }

        private void drawRing(DrawContext ctx,
                              int clipX, int clipY,
                              int cx, int cy, int outerR, int thickness, int color) {
            int innerR = Math.max(0, outerR - thickness);
            int minY = Math.max(clipY, cy - outerR);
            int maxY = Math.min(clipY + MultiSelectPlantSelectionScreen.BLOCK_SIZE - 1, cy + outerR);
            for (int y = minY; y <= maxY; y++) {
                int dy = y - cy;
                int y2 = dy * dy;
                int xOuter = (int) Math.floor(Math.sqrt(Math.max(0, outerR * outerR - y2)));
                int xInner = innerR > 0 ? (int) Math.floor(Math.sqrt(Math.max(0, innerR * innerR - y2))) : -1;

                int leftOuter = cx - xOuter;
                int rightOuter = cx + xOuter;
                int leftInner = cx - xInner;
                int rightInner = cx + xInner;

                // 左半环
                int lx1 = Math.max(clipX, leftOuter);
                int lx2 = Math.min(clipX + MultiSelectPlantSelectionScreen.BLOCK_SIZE - 1, leftInner - 1);
                if (lx1 <= lx2) ctx.fill(lx1, y, lx2 + 1, y + 1, color);
                // 右半环
                int rx1 = Math.max(clipX, rightInner + 1);
                int rx2 = Math.min(clipX + MultiSelectPlantSelectionScreen.BLOCK_SIZE - 1, rightOuter);
                if (rx1 <= rx2) ctx.fill(rx1, y, rx2 + 1, y + 1, color);
            }
        }



        private @NotNull ItemStack getItemStack(int i) {
            return BlockDisplayIcons.getPlantDisplayStack(blocks.get(i));
        }

        private void drawScrollBar(DrawContext context) {
            int scrollBarX = getX() + width - SCROLL_BAR_WIDTH;
            int contentHeight = height - PANEL_PADDING * 2;
            context.fill(scrollBarX, getY(), scrollBarX + SCROLL_BAR_WIDTH, getY() + height, SCROLL_BAR_BACKGROUND_COLOR);

            int visibleRows = contentHeight / (BLOCK_SIZE + BLOCK_SPACING);
            int totalRows = (blocks.size() + BLOCKS_PER_ROW - 1) / BLOCKS_PER_ROW;
            
            // 只有在需要滚动时才绘制滚动条
            if (totalRows > visibleRows) {
                int scrollBarHeight = Math.max(20, contentHeight * visibleRows / totalRows);
                int scrollBarY = getY() + (int) ((contentHeight - scrollBarHeight) * (float) scrollOffset / ((totalRows - visibleRows) * (BLOCK_SIZE + BLOCK_SPACING)));
                
                // 绘制3D滚动条
                context.fill(scrollBarX, scrollBarY, scrollBarX + SCROLL_BAR_WIDTH, scrollBarY + scrollBarHeight, SCROLL_BAR_COLOR);
                context.fill(scrollBarX, scrollBarY, scrollBarX + SCROLL_BAR_WIDTH - 1, scrollBarY + 1, SCROLL_BAR_HIGHLIGHT_COLOR);
                context.fill(scrollBarX, scrollBarY, scrollBarX + 1, scrollBarY + scrollBarHeight - 1, SCROLL_BAR_HIGHLIGHT_COLOR);
                context.fill(scrollBarX + SCROLL_BAR_WIDTH - 1, scrollBarY, scrollBarX + SCROLL_BAR_WIDTH, scrollBarY + scrollBarHeight, SCROLL_BAR_SHADOW_COLOR);
                context.fill(scrollBarX, scrollBarY + scrollBarHeight - 1, scrollBarX + SCROLL_BAR_WIDTH, scrollBarY + scrollBarHeight, SCROLL_BAR_SHADOW_COLOR);
            }
        }

        @Override
        public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
            if (isMouseOver(mouseX, mouseY)) {
                int contentHeight = height - PANEL_PADDING * 2;
                int visibleRows = contentHeight / (BLOCK_SIZE + BLOCK_SPACING);
                int totalRows = (blocks.size() + BLOCKS_PER_ROW - 1) / BLOCKS_PER_ROW;
                // 使用 long 避免整数溢出
                long maxScrollLong = Math.max(0L, (long)(totalRows - visibleRows) * (BLOCK_SIZE + BLOCK_SPACING));
                int maxScroll = maxScrollLong > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) maxScrollLong;
                scrollOffset = Math.max(0, Math.min(scrollOffset - (int)(verticalAmount * 10), maxScroll));
                return true;
            }
            return false;
        }

        @Override
        public boolean mouseClicked(Click click, boolean doubled) {
            double mouseX = click.x();
            double mouseY = click.y();
            int button = click.button();
            if (isMouseOver(mouseX, mouseY) && button == 0) {
                // 检查是否点击了滚动条
                int scrollBarX = getX() + width - SCROLL_BAR_WIDTH;
                if (mouseX >= scrollBarX && mouseX < scrollBarX + SCROLL_BAR_WIDTH) {
                    // 开始拖动滚动条
                    isDraggingScrollBar = true;
                    dragStartY = (int) mouseY;
                    dragStartScrollOffset = scrollOffset;
                    return true;
                }
                
                // 计算内容区域边界
                int contentX = getX() + PANEL_PADDING;
                int contentY = getY() + PANEL_PADDING;
                int contentWidth = width - SCROLL_BAR_WIDTH - PANEL_PADDING * 2;
                int contentHeight = height - PANEL_PADDING * 2;
                
                // 计算居中偏移
                int gridWidth = BLOCKS_PER_ROW * (BLOCK_SIZE + BLOCK_SPACING) - BLOCK_SPACING;
                int offsetX = (contentWidth - gridWidth) / 2;
                
                for (int i = 0; i < blocks.size(); i++) {
                    int row = i / BLOCKS_PER_ROW;
                    int col = i % BLOCKS_PER_ROW;
                    
                    int blockX = contentX + offsetX + col * (BLOCK_SIZE + BLOCK_SPACING);
                    int blockY = contentY + row * (BLOCK_SIZE + BLOCK_SPACING) - scrollOffset;
                    
                    // 只检查可见的方块 - 考虑边框向内收缩
                    if (blockY + BLOCK_SIZE >= contentY && blockY < contentY + contentHeight) {
                        if (mouseX >= blockX && mouseX < blockX + BLOCK_SIZE && mouseY >= blockY && mouseY < blockY + BLOCK_SIZE) {
                            Block clickedBlock = blocks.get(i);
                            
                            // 多选逻辑：切换选中状态
                            if (selectedBlocks.contains(clickedBlock)) {
                                selectedBlocks.remove(clickedBlock);
                            } else {
                                selectedBlocks.add(clickedBlock);
                            }
                            
                            return true;
                        }
                    }
                }
            }
            return false;
        }
        
        @Override
        public boolean mouseDragged(Click click, double offsetX, double offsetY) {
            double mouseY = click.y();
            int button = click.button();
            if (isDraggingScrollBar && button == 0) {
                int contentHeight = height - PANEL_PADDING * 2;
                int visibleRows = contentHeight / (BLOCK_SIZE + BLOCK_SPACING);
                int totalRows = (blocks.size() + BLOCKS_PER_ROW - 1) / BLOCKS_PER_ROW;
                // 使用 long 避免整数溢出
                long maxScrollLong = Math.max(0L, (long)(totalRows - visibleRows) * (BLOCK_SIZE + BLOCK_SPACING));
                int maxScroll = maxScrollLong > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) maxScrollLong;
                
                if (maxScroll > 0) {
                    int dragDistance = (int) mouseY - dragStartY;
                    int scrollBarHeight = Math.max(20, contentHeight * visibleRows / totalRows);
                    int scrollableHeight = contentHeight - scrollBarHeight;
                    
                    float scrollRatio = (float) dragDistance / scrollableHeight;
                    int newScrollOffset = dragStartScrollOffset + (int) (scrollRatio * maxScroll);
                    scrollOffset = Math.max(0, Math.min(newScrollOffset, maxScroll));
                }
                return true;
            }
            return false;
        }
        
        @Override
        public boolean mouseReleased(Click click) {
            if (click.button() == 0) {
                isDraggingScrollBar = false;
            }
            return false;
        }
        
        @Override
        protected void appendClickableNarrations(NarrationMessageBuilder builder) {
            builder.put(NarrationPart.TITLE, Text.translatable("pushdozer.narrator.plant_panel"));
        }
    }
    
    /**
     * 获取所有可种植的植物方块
     * 包括树木、树苗、蘑菇、花草、农作物、仙人掌、珊瑚、海草等
     */
    private List<Block> getPlantBlocks() {
        if (this.client == null || this.client.world == null) {
            return new ArrayList<>();
        }

        List<Block> list = new ArrayList<>();
        for (Block block : Registries.BLOCK) {
            // 排除明显非植物的内容
            if (block == Blocks.AIR) continue;
            if (isPlantCandidate(block)) {
                list.add(block);
            }
        }
        return list;
    }
    private boolean isPlantCandidate(Block block) {
        String id = Registries.BLOCK.getId(block).getPath().toLowerCase();
        String key = block.getTranslationKey().toLowerCase();
        BlockState state = block.getDefaultState();

        // 显式排除：结果态茎、不应纳入的树叶、大蘑菇块
        switch (id) {
            case "attached_melon_stem", "attached_pumpkin_stem", "brown_mushroom_block", "red_mushroom_block",
                 "mushroom_stem", "cherry_leaves", "flowering_azalea_leaves" -> {
                return false;
            }
        }

        // 排除：所有珊瑚块（*_coral_block，包含失活与正常）与干海带块（不是植物）
        if (id.endsWith("_coral_block") || id.equals("dried_kelp_block")) {
            return false;
        }

        // 标签优先：鲜花/树苗/农作物
        if (state.isIn(BlockTags.FLOWERS)) return true;
        if (state.isIn(BlockTags.SAPLINGS)) return true;
        if (state.isIn(BlockTags.CROPS)) return true;

        // 典型植物类
        if (block instanceof PlantBlock || block instanceof TallPlantBlock || block instanceof CropBlock || block instanceof SaplingBlock || block instanceof VineBlock) {
            return true;
        }

        // 垂滴叶（大小型都纳入，但排除茎）
        if (id.contains("dripleaf") && !id.contains("big_dripleaf_stem")) return true;

        // 下界藤蔓：显式包含垂泪藤和缠怨藤
        if (id.equals("weeping_vines") || id.equals("weeping_vines_plant") || 
            id.equals("twisting_vines") || id.equals("twisting_vines_plant")) {
            // 调试信息：确认下界藤蔓被识别
            if (client != null && client.player != null) {
                client.player.sendMessage(Text.literal("识别到下界藤蔓: " + id), false);
            }
            return true;
        }

        // 重复与不可直接种植的避免
        // 移除 pitcher_plant 排除，因为它应该被包含在植物列表中

        // 其他可摆放/可种植植物
        if (id.startsWith("potted_")) return true; // 盆栽
        if (id.contains("chorus_")) return true;   // 末地植物
        if (key.contains("seagrass") || key.contains("kelp") || id.equals("sea_pickle") || id.equals("lily_pad")) return true; // 水生
        if (key.contains("mushroom") || key.contains("fungus")) return true; // 蘑菇
        if (id.contains("coral")) return true; // 珊瑚（可在水中放置）
        if (id.equals("moss_block") || id.equals("moss_carpet") || id.equals("moss")) return true; // 覆地苔藓
        if ((key.contains("grass") && !id.equals("grass_block")) || key.contains("fern")) return true; // 草/蕨

        // 作物补齐
        switch (id) {
            case "wheat", "carrots", "potatoes", "beetroots", "sweet_berry_bush", "cocoa", "sugar_cane", "bamboo",
                 "cactus" -> {
                return true;
            }
            case "melon_stem", "pumpkin_stem" -> {
                return true; // 种子对应茎（不含 attached_*）
            }
        }

        // 枯/干/凋零 叶子：允许作为植被摆放
        return (key.contains("leaves") || id.endsWith("_leaves")) && (key.contains("dead") || key.contains("withered") || key.contains("dry"));

        // 显式排除：不可“种植/盆栽”的杂项根土 & 大蘑菇块
    }

    /**
     * 静态分类方块方法（用于缓存）
     */
    private static Map<String, List<Block>> categorizeBlocksStatic(List<Block> plantBlocks) {
        Map<String, List<Block>> categorizedBlocks = new HashMap<>();
        
        // 基于启发式将动态收集的植物按类别划分
        List<Block> saplings = new ArrayList<>();
        List<Block> smallFlowers = new ArrayList<>();
        List<Block> largeFlowers = new ArrayList<>();
        List<Block> grassFerns = new ArrayList<>();
        List<Block> mushrooms = new ArrayList<>();
        List<Block> crops = new ArrayList<>();
        List<Block> aquatic = new ArrayList<>();
        List<Block> coral = new ArrayList<>();
        List<Block> nether = new ArrayList<>();
        List<Block> potted = new ArrayList<>();
        List<Block> other = new ArrayList<>();

        for (Block b : plantBlocks) {
            String id = Registries.BLOCK.getId(b).getPath().toLowerCase();
            String key = b.getTranslationKey().toLowerCase();

            // 显式大型植物：向日葵、丁香、玫瑰丛、牡丹（避免被归入"其他"）
            if (id.equals("sunflower") || id.equals("lilac") || id.equals("rose_bush") || id.equals("peony")) { largeFlowers.add(b); continue; }

            // 优先将所有带⭕标识（盆栽）的方块归入盆栽分类
            if (id.startsWith("potted_") || key.contains("potted")) { potted.add(b); continue; }

            if (id.endsWith("_sapling") || id.equals("mangrove_propagule")) { saplings.add(b); continue; }

            // 珊瑚及变体均纳入珊瑚分类
            if (key.contains("coral") || id.contains("coral")) { coral.add(b); continue; }
            if (key.contains("seagrass") || key.contains("kelp") || id.equals("sea_pickle") || id.equals("lily_pad")) { aquatic.add(b); continue; }

            if (key.contains("mushroom") || key.contains("fungus")) { mushrooms.add(b); continue; }

            if (id.contains("_flower_cluster") || key.contains("cluster") || id.contains("cluster")) { smallFlowers.add(b); continue; }
            if (key.contains("flower") || id.contains("flowers") || id.contains("flower") || id.contains("petal")
                || key.contains("blossom") || id.contains("blossom") || key.contains("bloom") || id.contains("bloom")) {
                // 按名称区分大小型植物
                if (key.contains("sunflower") || key.contains("lilac") || key.contains("rose_bush") || key.contains("peony") || key.contains("tall")
                    || id.contains("big_dripleaf")) {
                    largeFlowers.add(b);
                } else {
                    smallFlowers.add(b);
                }
                continue;
            }

            // 杜鹃灌木归入小型
            if (id.equals("azalea") || id.equals("flowering_azalea")) { smallFlowers.add(b); continue; }

            // 显式大型植物（不含 flower 关键词）：大垂滴叶、小垂滴叶、瓶子草（按需求归为大型）
            if (id.contains("big_dripleaf") || id.contains("small_dripleaf") || id.contains("pitcher_plant")) { largeFlowers.add(b); continue; }

            // 显式小型花卉：常见小花（防止遗漏）
            if (id.equals("dandelion") || id.equals("poppy") || id.contains("orchid") || id.equals("allium")
                || id.contains("azure_bluet") || id.contains("tulip") || id.contains("oxeye_daisy") || id.contains("cornflower")
                || id.contains("lily_of_the_valley") || id.equals("torchflower") || id.equals("wither_rose")) {
                smallFlowers.add(b); continue;
            }

            if ((key.contains("grass") && !id.equals("grass_block")) || key.contains("fern")) { grassFerns.add(b); continue; }

            if (key.contains("crop") || id.endsWith("_crop")
                || id.equals("wheat") || id.equals("carrots") || id.equals("potatoes") || id.equals("beetroots")
                || id.equals("melon_stem") || id.equals("pumpkin_stem")
                || id.equals("sweet_berry_bush") || id.equals("cocoa") || id.equals("sugar_cane")) { crops.add(b); continue; }
            // 仙人掌与竹子不归入农作物
            if (id.equals("cactus") || id.equals("bamboo") || id.equals("bamboo_sapling")) { other.add(b); continue; }

            // 下界藤蔓归入下界植物分类
            if (id.equals("weeping_vines") || id.equals("weeping_vines_plant") || id.equals("twisting_vines") || id.equals("twisting_vines_plant")) { nether.add(b); continue; }
            
            // 其他藤蔓类归并到草蕨类，便于统一查看
            if (key.contains("vine") || key.contains("vines") || id.equals("glow_lichen")) { grassFerns.add(b); continue; }

            if (key.contains("nether") || key.contains("crimson") || key.contains("warped")) { nether.add(b); continue; }
            // 紫颂植株归入其他类别
            if (id.equals("chorus_plant")) { other.add(b); continue; }
            // 紫颂花归入小型花朵
            if (id.equals("chorus_flower")) { smallFlowers.add(b); continue; }

            other.add(b);
        }

        categorizedBlocks.put("saplings", saplings);
        categorizedBlocks.put("small_flowers", smallFlowers);
        categorizedBlocks.put("large_flowers", largeFlowers);
        categorizedBlocks.put("grass_ferns", grassFerns);
        categorizedBlocks.put("mushrooms", mushrooms);
        categorizedBlocks.put("crops", crops);
        categorizedBlocks.put("aquatic_plants", aquatic);
        categorizedBlocks.put("coral", coral);
        categorizedBlocks.put("nether_plants", nether);
        categorizedBlocks.put("potted", potted);
        categorizedBlocks.put("other", other);
        
        return categorizedBlocks;
    }
}