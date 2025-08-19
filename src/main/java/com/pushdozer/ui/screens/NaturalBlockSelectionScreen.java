package com.pushdozer.ui.screens;

import java.util.List;
import java.util.function.Consumer;
import java.util.ArrayList;

import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.registry.Registries;
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

/**
 * 自然方块选择屏幕
 * 专门用于表层转换配置，包含自然方块和水体
 */
public class NaturalBlockSelectionScreen extends Screen {
    private final Screen parent;
    private final Consumer<Block> onBlockSelected;
    private final List<Block> naturalBlocks;
    private List<Block> filteredBlocksCache;
    private String currentSearchText = "";
    private ScrollableBlockPanel scrollPanel;
    private Block selectedBlock = null; // 当前选中的方块

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

    public NaturalBlockSelectionScreen(Screen parent, Consumer<Block> onBlockSelected) {
        super(Text.translatable("pushdozer.screen.natural_block_selection.title"));
        this.parent = parent;
        this.onBlockSelected = onBlockSelected;
        // 注意：getNaturalBlocks现在依赖于client，所以在构造函数中调用可能太早
        // 最好在 init() 中调用，因为那时 client 肯定存在。
        this.naturalBlocks = new ArrayList<>(); 
        this.filteredBlocksCache = this.naturalBlocks;
    }

    @Override
    protected void init() {
        super.init();

        // 在init中填充方块列表，确保client可用
        if (this.naturalBlocks.isEmpty()) {
            this.naturalBlocks.addAll(getNaturalBlocks());
            this.filteredBlocksCache = this.naturalBlocks;
        }

        // --- 改进的健壮布局 ---
        
        // 1. 定义页脚元素 - 改进搜索框宽度计算
        int buttonWidth = 80;
        int buttonSpacing = 10;
        int totalButtonWidth = buttonWidth * 2 + buttonSpacing;
        int buttonStartX = (width - totalButtonWidth) / 2;
        
        // 搜索框宽度更灵活，设置为屏幕宽度的固定比例，并设置最小和最大宽度
        int searchBoxWidth = Math.min(Math.max((int)(width * 0.4), 200), 400);
        int searchBoxX = (width - searchBoxWidth) / 2;

        // 2. 从下往上放置页脚元素
        int buttonsY = this.height - 20 - BUTTON_HEIGHT; // 距离底部20px
        int searchBoxY = buttonsY - ELEMENT_SPACING - BUTTON_HEIGHT; // 在按钮上方

        // 3. 定义顶部和底部边距
        int topMargin = 40; // 标题下方留白
        int bottomMargin = this.height - searchBoxY + ELEMENT_SPACING; // 搜索框到屏幕底部的总高度

        // 4. 计算滚动面板的尺寸和位置 - 添加最小高度检查
        int panelContentWidth = BLOCKS_PER_ROW * (BLOCK_SIZE + BLOCK_SPACING) - BLOCK_SPACING;
        int panelWidth = panelContentWidth + SCROLL_BAR_WIDTH + PANEL_PADDING * 2;
        int panelX = (this.width - panelWidth) / 2;
        int panelHeight = Math.max(100, this.height - topMargin - bottomMargin); // 确保最小高度

        // 5. 添加所有UI元素
        scrollPanel = new ScrollableBlockPanel(panelX, topMargin, panelWidth, panelHeight, filteredBlocksCache);
        addDrawableChild(scrollPanel);

        TextFieldWidget searchBox = new TextFieldWidget(textRenderer, searchBoxX, searchBoxY, searchBoxWidth, BUTTON_HEIGHT, Text.empty());
        searchBox.setMaxLength(50);
        searchBox.setDrawsBackground(true);
        searchBox.setVisible(true);
        searchBox.setEditable(true);
        searchBox.setText(""); // 清空初始文本
        searchBox.setChangedListener(this::onSearchTextChanged);
        searchBox.setPlaceholder(Text.translatable("pushdozer.screen.block_selection.search_hint"));
        addDrawableChild(searchBox);

        addDrawableChild(ButtonWidget.builder(Text.translatable("gui.ok"), button -> onConfirm())
                .dimensions(buttonStartX, buttonsY, buttonWidth, BUTTON_HEIGHT).build());
        
        addDrawableChild(ButtonWidget.builder(Text.translatable("gui.cancel"), button -> close())
                .dimensions(buttonStartX + buttonWidth + buttonSpacing, buttonsY, buttonWidth, BUTTON_HEIGHT).build());
    }

    @Override
    public void tick() {
        super.tick();
    }
    
    private void onConfirm() {
        // 如果有选中的方块，应用选择并关闭窗口
        if (selectedBlock != null) {
            onBlockSelected.accept(selectedBlock);
        }
        close();
    }

    private void onSearchTextChanged(String text) {
        if (!text.equals(currentSearchText)) {
            currentSearchText = text.toLowerCase();
            updateFilteredBlocks();
            if (scrollPanel != null) {
                scrollPanel.updateBlocks(filteredBlocksCache);
            }
        }
    }
    
    private void updateFilteredBlocks() {
        if (currentSearchText.isEmpty()) {
            filteredBlocksCache = naturalBlocks;
        } else {
            filteredBlocksCache = naturalBlocks.stream()
                .filter(block -> Text.translatable(block.getTranslationKey()).getString().toLowerCase().contains(currentSearchText) || 
                                 block.getTranslationKey().toLowerCase().contains(currentSearchText))
                .toList();
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        this.renderBackground(context, mouseX, mouseY, delta);
        
        super.render(context, mouseX, mouseY, delta);
        context.drawCenteredTextWithShadow(textRenderer, title, width / 2, 10, 0xFFFFFF);
        
        // 在顶部显示选中的方块名称
        if (selectedBlock != null) {
            String selectedBlockName = Text.translatable(selectedBlock.getTranslationKey()).getString();
            context.drawCenteredTextWithShadow(textRenderer, Text.literal("选中: " + selectedBlockName), width / 2, 25, 0xFFFFFF);
        }
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
        private Block lastClickedBlock = null;
        private long lastClickTime = 0;
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
            
            // 循环绘制方块
            for (int i = 0; i < blocks.size(); i++) {
                int row = i / BLOCKS_PER_ROW;
                int col = i % BLOCKS_PER_ROW;
                
                int blockX = contentX + offsetX + col * (BLOCK_SIZE + BLOCK_SPACING);
                int blockY = contentY + row * (BLOCK_SIZE + BLOCK_SPACING) - scrollOffset;

                // 只渲染可见的方块 - 边框向内收缩1像素
                if (blockY + BLOCK_SIZE >= contentY && blockY < contentY + contentHeight) {
                    // 确定边框颜色：如果被选中则使用白色，否则使用默认灰色
                    int borderColor = (blocks.get(i) == selectedBlock) ? SELECTED_BLOCK_BORDER_COLOR : BLOCK_BORDER_COLOR;
                    
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
                    ItemStack itemStack = new ItemStack(blocks.get(i));
                    if (blocks.get(i) == Blocks.WATER) itemStack = new ItemStack(Items.WATER_BUCKET);
                    else if (blocks.get(i) == Blocks.LAVA) itemStack = new ItemStack(Items.LAVA_BUCKET);
                    else if (blocks.get(i) == Blocks.FROSTED_ICE) itemStack = new ItemStack(Blocks.ICE);
                    
                    if (!itemStack.isEmpty()) {
                        context.drawItem(itemStack, blockX + (BLOCK_SIZE - 16) / 2, blockY + (BLOCK_SIZE - 16) / 2);
                    }

                    // 如果方块被选中，绘制背景高亮和选中标记
                    if (blocks.get(i) == selectedBlock) {
                        context.fill(blockX + 1, blockY + 1, blockX + BLOCK_SIZE - 1, blockY + BLOCK_SIZE - 1, 0x80FFFFFF);
                        context.drawText(textRenderer, Text.literal("☑"), blockX + BLOCK_SIZE - 8, blockY + BLOCK_SIZE - 9, 0xFF000000, false);
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
                int maxScroll = Math.max(0, (totalRows - visibleRows) * (BLOCK_SIZE + BLOCK_SPACING));
                scrollOffset = Math.max(0, Math.min(scrollOffset - (int)(verticalAmount * 10), maxScroll));
                return true;
            }
            return false;
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
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
                            long time = System.currentTimeMillis();

                            // 检查双击
                            if (clickedBlock == lastClickedBlock && (time - lastClickTime) < 500) {
                                selectedBlock = clickedBlock;
                                onConfirm(); // 双击直接确认
                            } else {
                                selectedBlock = clickedBlock;
                                this.lastClickedBlock = clickedBlock;
                                this.lastClickTime = time;
                            }
                            return true;
                        }
                    }
                }
            }
            return false;
        }
        
        @Override
        public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
            if (isDraggingScrollBar && button == 0) {
                int contentHeight = height - PANEL_PADDING * 2;
                int visibleRows = contentHeight / (BLOCK_SIZE + BLOCK_SPACING);
                int totalRows = (blocks.size() + BLOCKS_PER_ROW - 1) / BLOCKS_PER_ROW;
                int maxScroll = Math.max(0, (totalRows - visibleRows) * (BLOCK_SIZE + BLOCK_SPACING));
                
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
        public boolean mouseReleased(double mouseX, double mouseY, int button) {
            if (button == 0) {
                isDraggingScrollBar = false;
            }
            return false;
        }
        
        @Override
        protected void appendClickableNarrations(NarrationMessageBuilder builder) {
            builder.put(NarrationPart.TITLE, Text.translatable("pushdozer.narrator.natural_block_panel"));
        }
    }
    
    private List<Block> getNaturalBlocks() {
        if (this.client == null || this.client.world == null) {
            return new ArrayList<>();
        }

        List<Block> result = new ArrayList<>();

        for (Block block : Registries.BLOCK) {
            String id = Registries.BLOCK.getId(block).getPath().toLowerCase();
            String key = block.getTranslationKey().toLowerCase();

            if (block == Blocks.AIR) continue;

            // 仅保留自然/地形类方块，不含植物、珊瑚、装饰与结构类
            if (isNaturalBaseBlock(id, key)) {
                result.add(block);
            }
        }

        return result;
    }

    private boolean isNaturalBaseBlock(String id, String key) {
        return id.equals("grass_block") || id.equals("dirt") || id.equals("coarse_dirt") ||
               id.equals("rooted_dirt") || id.equals("podzol") || id.equals("mycelium") ||
               id.equals("dirt_path") || id.equals("mud") || id.equals("muddy_mangrove_roots") ||
               id.equals("packed_mud") ||
               id.equals("stone") || id.equals("granite") || id.equals("diorite") ||
               id.equals("andesite") || id.equals("deepslate") || id.equals("tuff") ||
               id.equals("calcite") || id.equals("dripstone_block") ||
               id.equals("sand") || id.equals("red_sand") || id.equals("gravel") ||
               id.equals("soul_sand") || id.equals("soul_soil") ||
               key.contains("snow") || key.contains("ice") ||
               id.equals("clay") || key.contains("terracotta") ||
               id.equals("end_stone") || id.equals("netherrack") ||
               id.equals("basalt") || id.equals("blackstone") ||
               id.equals("water") || id.equals("lava") || id.equals("obsidian") ||
               key.contains("sandstone");
    }

} 