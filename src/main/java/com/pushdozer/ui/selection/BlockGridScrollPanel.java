package com.pushdozer.ui.selection;

import net.minecraft.block.Block;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * 可滚动方块网格面板，封装滚动条、点击选择与单元格渲染。
 */
public class BlockGridScrollPanel extends ButtonWidget {
    private List<Block> blocks = new ArrayList<>();
    private final int columnsPerRow;
    private final SelectionStrategy<Block> selectionStrategy;
    private final BlockCellRenderer.DisplayMode displayMode;
    private final BlockCellRenderer.BlockCellDecorator cellDecorator;
    private final Consumer<Block> onSelectionChanged;

    private int scrollOffset;
    private boolean isDraggingScrollBar;
    private int dragStartY;
    private int dragStartScrollOffset;
    private long hoverStartTime;
    private Block lastHoveredBlock;
    private int hoveredX;
    private int hoveredY;

    public BlockGridScrollPanel(int x,
                                int y,
                                int width,
                                int height,
                                List<Block> blocks,
                                int columnsPerRow,
                                SelectionStrategy<Block> selectionStrategy,
                                BlockCellRenderer.DisplayMode displayMode,
                                BlockCellRenderer.BlockCellDecorator cellDecorator,
                                Consumer<Block> onSelectionChanged) {
        super(x, y, width, height, Text.empty(), button -> {}, DEFAULT_NARRATION_SUPPLIER);
        this.blocks = new ArrayList<>(blocks);
        this.columnsPerRow = columnsPerRow;
        this.selectionStrategy = selectionStrategy;
        this.displayMode = displayMode;
        this.cellDecorator = cellDecorator;
        this.onSelectionChanged = onSelectionChanged != null ? onSelectionChanged : block -> {};
    }

    public void setBlocks(List<Block> blocks) {
        this.blocks = new ArrayList<>(blocks);
        this.scrollOffset = 0;
    }

    public List<Block> getBlocks() {
        return blocks;
    }

    public void updateBlocks(List<Block> newBlocks) {
        this.blocks = new ArrayList<>(newBlocks);
        this.scrollOffset = 0;
    }

    public int getScrollOffset() {
        return scrollOffset;
    }

    public void setScrollOffset(int scrollOffset) {
        this.scrollOffset = Math.max(0, Math.min(scrollOffset, maxScroll()));
    }

    private int rowStride() {
        return SelectionScreenStyle.BLOCK_SIZE + SelectionScreenStyle.BLOCK_SPACING;
    }

    private int visibleRows() {
        return height / rowStride();
    }

    private int totalRows() {
        return (blocks.size() + columnsPerRow - 1) / columnsPerRow;
    }

    private int maxScroll() {
        return Math.max(0, (totalRows() - visibleRows()) * rowStride());
    }

    private int gridWidth() {
        return columnsPerRow * SelectionScreenStyle.BLOCK_SIZE + (columnsPerRow - 1) * SelectionScreenStyle.BLOCK_SPACING;
    }

    @Override
    protected void drawIcon(DrawContext context, int mouseX, int mouseY, float delta) {
        int visibleRows = visibleRows();
        int backgroundWidth = gridWidth();
        int backgroundHeight = visibleRows * rowStride() - SelectionScreenStyle.BLOCK_SPACING;
        context.fill(getX(), getY(), getX() + backgroundWidth, getY() + backgroundHeight, SelectionScreenStyle.PANEL_BACKGROUND_COLOR);

        Block hoveredBlock = null;
        TextRenderer textRenderer = net.minecraft.client.MinecraftClient.getInstance().textRenderer;

        context.enableScissor(getX(), getY(), getX() + width, getY() + height);
        int startRow = scrollOffset / rowStride();
        for (int i = startRow * columnsPerRow; i < blocks.size(); i++) {
            int row = i / columnsPerRow;
            int col = i % columnsPerRow;
            int visibleRow = row - startRow;
            if (visibleRow < 0 || visibleRow >= visibleRows) {
                continue;
            }
            int blockX = getX() + col * rowStride();
            int blockY = getY() + visibleRow * rowStride();
            Block block = blocks.get(i);
            boolean selected = selectionStrategy.isSelected(block);
            BlockCellRenderer.renderCell(context, textRenderer, block, blockX, blockY, mouseX, mouseY, selected, displayMode, cellDecorator);
            if (mouseX >= blockX && mouseX < blockX + SelectionScreenStyle.BLOCK_SIZE
                && mouseY >= blockY && mouseY < blockY + SelectionScreenStyle.BLOCK_SIZE) {
                hoveredBlock = block;
                hoveredX = mouseX;
                hoveredY = mouseY;
            }
        }
        context.disableScissor();

        context.fill(getX() + width - SelectionScreenStyle.SCROLL_BAR_WIDTH, getY(),
            getX() + width, getY() + backgroundHeight, SelectionScreenStyle.SCROLL_BAR_BACKGROUND_COLOR);

        if (totalRows() > visibleRows) {
            int scrollBarHeight = Math.max(20, backgroundHeight * visibleRows / totalRows());
            int scrollBarY = getY() + (int) ((backgroundHeight - scrollBarHeight) * (float) scrollOffset / maxScroll());
            drawScrollBar(context, getX() + width - SelectionScreenStyle.SCROLL_BAR_WIDTH, scrollBarY, scrollBarHeight);
        }

        if (hoveredBlock != null) {
            long currentTime = System.currentTimeMillis();
            if (lastHoveredBlock != hoveredBlock) {
                hoverStartTime = currentTime;
                lastHoveredBlock = hoveredBlock;
            }
            if (currentTime - hoverStartTime >= SelectionScreenStyle.TOOLTIP_DELAY_MS) {
                context.drawTooltip(textRenderer, hoveredBlock.getName(), hoveredX, hoveredY);
            }
        } else {
            lastHoveredBlock = null;
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (isMouseOver(mouseX, mouseY)) {
            scrollOffset = Math.max(0, Math.min(scrollOffset - (int) (verticalAmount * 10), maxScroll()));
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubleClick) {
        double mouseX = click.x();
        double mouseY = click.y();
        if (!isMouseOver(mouseX, mouseY)) {
            return false;
        }

        int backgroundHeight = visibleRows() * rowStride() - SelectionScreenStyle.BLOCK_SPACING;
        if (mouseX >= getX() + width - SelectionScreenStyle.SCROLL_BAR_WIDTH && mouseX < getX() + width
            && mouseY >= getY() && mouseY < getY() + backgroundHeight && totalRows() > visibleRows()) {
            isDraggingScrollBar = true;
            dragStartY = (int) mouseY;
            dragStartScrollOffset = scrollOffset;
            return true;
        }

        int startRow = scrollOffset / rowStride();
        for (int i = startRow * columnsPerRow; i < Math.min(blocks.size(), (startRow + visibleRows()) * columnsPerRow); i++) {
            int row = i / columnsPerRow - startRow;
            int col = i % columnsPerRow;
            int blockX = getX() + col * rowStride();
            int blockY = getY() + row * rowStride();
            if (mouseX >= blockX && mouseX < blockX + SelectionScreenStyle.BLOCK_SIZE
                && mouseY >= blockY && mouseY < blockY + SelectionScreenStyle.BLOCK_SIZE) {
                Block block = blocks.get(i);
                selectionStrategy.toggle(block);
                onSelectionChanged.accept(block);
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean mouseDragged(Click click, double deltaX, double deltaY) {
        if (!isDraggingScrollBar) {
            return false;
        }
        int backgroundHeight = visibleRows() * rowStride() - SelectionScreenStyle.BLOCK_SPACING;
        int maxScroll = maxScroll();
        int dragDistance = (int) click.y() - dragStartY;
        int scrollBarHeight = Math.max(20, backgroundHeight * visibleRows() / Math.max(1, totalRows()));
        int scrollableHeight = backgroundHeight - scrollBarHeight;
        if (scrollableHeight > 0) {
            double scrollRatio = (double) dragDistance / scrollableHeight;
            scrollOffset = Math.max(0, Math.min(dragStartScrollOffset + (int) (scrollRatio * maxScroll), maxScroll));
        }
        return true;
    }

    @Override
    public boolean mouseReleased(Click click) {
        if (isDraggingScrollBar) {
            isDraggingScrollBar = false;
            return true;
        }
        return false;
    }

    private void drawScrollBar(DrawContext context, int x, int y, int barHeight) {
        int w = SelectionScreenStyle.SCROLL_BAR_WIDTH;
        context.fill(x, y, x + w, y + barHeight, SelectionScreenStyle.SCROLL_BAR_BACKGROUND_COLOR);
        context.fill(x, y, x + w, y + barHeight, SelectionScreenStyle.SCROLL_BAR_COLOR);
        context.fill(x, y, x + w - 1, y + 1, SelectionScreenStyle.SCROLL_BAR_HIGHLIGHT_COLOR);
        context.fill(x, y, x + 1, y + barHeight - 1, SelectionScreenStyle.SCROLL_BAR_HIGHLIGHT_COLOR);
        context.fill(x + w - 1, y, x + w, y + barHeight, SelectionScreenStyle.SCROLL_BAR_SHADOW_COLOR);
        context.fill(x, y + barHeight - 1, x + w, y + barHeight, SelectionScreenStyle.SCROLL_BAR_SHADOW_COLOR);
    }
}
