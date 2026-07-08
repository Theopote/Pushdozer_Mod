package com.pushdozer.ui.selection;

import net.minecraft.block.Block;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.input.CharInput;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 分页分类选择界面基类：搜索、分页、滚动网格、页脚按钮。
 */
public abstract class AbstractPagedCategorySelectionScreen extends Screen {
    protected final Screen parent;
    protected final List<SelectionCategory<Block>> categories;
    protected final SelectionStrategy<Block> selectionStrategy;
    protected final int columnsPerRow;
    protected final int footerButtonCount;

    protected int currentPage;
    protected String searchText = "";
    protected TextFieldWidget searchBox;

    protected AbstractPagedCategorySelectionScreen(Screen parent,
                                                   Text title,
                                                   List<SelectionCategory<Block>> categories,
                                                   SelectionStrategy<Block> selectionStrategy,
                                                   int columnsPerRow,
                                                   int footerButtonCount) {
        super(title);
        this.parent = parent;
        this.categories = categories;
        this.selectionStrategy = selectionStrategy;
        this.columnsPerRow = columnsPerRow;
        this.footerButtonCount = footerButtonCount;
    }

    protected abstract Text searchFieldLabel();

    protected abstract Text previousPageTooltip();

    protected abstract Text nextPageTooltip();

    protected abstract void addFooterButtons(int startX, int buttonY);

    protected abstract String formatStatusLine(int selectedCount, int totalCount);

    protected void onSelectionChanged(Block block) {
    }

    protected void onConfirm() {
    }

    protected int totalItemCount() {
        return categories.stream().mapToInt(category -> category.getItems().size()).sum();
    }

    @Override
    protected void init() {
        super.init();
        addSearchAndButtons(false);
        refreshCategoryButtons();
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubleClick) {
        boolean result = super.mouseClicked(click, doubleClick);
        if (searchBox != null) {
            if (searchBox.isMouseOver(click.x(), click.y())) {
                searchBox.setFocused(true);
                return true;
            }
            searchBox.setFocused(false);
        }
        return result;
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        if (searchBox != null && searchBox.isFocused()) {
            if (input.key() == GLFW.GLFW_KEY_ESCAPE) {
                searchBox.setFocused(false);
                return true;
            }
            if (input.key() == GLFW.GLFW_KEY_ENTER) {
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
        if (searchBox != null && searchBox.isFocused() && searchBox.charTyped(input)) {
            return true;
        }
        return super.charTyped(input);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, width, height, SelectionScreenStyle.SCREEN_BACKGROUND_COLOR);
        super.render(context, mouseX, mouseY, delta);

        context.fill(0, 0, width, SelectionScreenStyle.TOP_MARGIN, SelectionScreenStyle.SCREEN_BACKGROUND_COLOR);
        context.drawCenteredTextWithShadow(textRenderer, getTitle(), width / 2, 8, 0xFFFFFF);

        int selectedCount = selectionStrategy.selectedCount();
        int totalCount = totalItemCount();
        int countColor = selectedCount == 0 ? 0xFFAAAAAA : 0xFFFFFFFF;
        context.drawCenteredTextWithShadow(textRenderer,
            Text.literal(formatStatusLine(selectedCount, totalCount)), width / 2, 20, countColor);

        int searchBoxY = footerSearchBoxY();
        context.drawCenteredTextWithShadow(textRenderer, Text.literal(getPageText()),
            width / 2, searchBoxY - SelectionScreenStyle.PAGE_NUMBER_SPACING, 0xFFFFFF);
    }

    protected void addSearchAndButtons(boolean restoreFocus) {
        int totalWidth = SelectionScreenStyle.searchWidth(footerButtonCount);
        int startX = (width - totalWidth) / 2;
        int buttonY = height - SelectionScreenStyle.BUTTON_HEIGHT - SelectionScreenStyle.BOTTOM_MARGIN;
        int searchBoxY = buttonY - SelectionScreenStyle.BUTTON_HEIGHT - SelectionScreenStyle.SEARCH_BOTTOM_SPACING;

        searchBox = new TextFieldWidget(textRenderer, startX, searchBoxY, totalWidth, SelectionScreenStyle.BUTTON_HEIGHT, searchFieldLabel());
        searchBox.setMaxLength(50);
        searchBox.setDrawsBackground(true);
        searchBox.setVisible(true);
        searchBox.setEditable(true);
        searchBox.setText(searchText);
        searchBox.setFocused(restoreFocus);
        searchBox.setChangedListener(this::onSearchTextChanged);
        addDrawableChild(searchBox);

        addPageButtons();
        addFooterButtons(startX, buttonY);
    }

    protected void addPageButtons() {
        addDrawableChild(ButtonWidget.builder(Text.literal("<"), button -> changePage(-1))
            .dimensions(10, height / 2, 20, 20)
            .tooltip(Tooltip.of(previousPageTooltip()))
            .build());
        addDrawableChild(ButtonWidget.builder(Text.literal(">"), button -> changePage(1))
            .dimensions(width - 30, height / 2, 20, 20)
            .tooltip(Tooltip.of(nextPageTooltip()))
            .build());
    }

    protected void changePage(int delta) {
        int totalPages = Math.max(1, (categories.size() - 1) / SelectionScreenStyle.CATEGORIES_PER_PAGE + 1);
        currentPage += delta;
        if (currentPage < 0) {
            currentPage = totalPages - 1;
        } else if (currentPage >= totalPages) {
            currentPage = 0;
        }
        refreshCategoryButtons();
    }

    protected void refreshCategoryButtons() {
        boolean wasFocused = searchBox != null && searchBox.isFocused();
        clearChildren();
        addSearchAndButtons(wasFocused);

        int categoryWidth = SelectionScreenStyle.categoryColumnWidth(columnsPerRow);
        int totalWidth = SelectionScreenStyle.CATEGORIES_PER_PAGE * categoryWidth
            + (SelectionScreenStyle.CATEGORIES_PER_PAGE - 1) * SelectionScreenStyle.CATEGORY_SPACING;
        int startX = (width - totalWidth) / 2;
        int startY = SelectionScreenStyle.TOP_MARGIN;

        int startIndex = currentPage * SelectionScreenStyle.CATEGORIES_PER_PAGE;
        for (int i = 0; i < SelectionScreenStyle.CATEGORIES_PER_PAGE && startIndex + i < categories.size(); i++) {
            SelectionCategory<Block> category = categories.get(startIndex + i);
            int columnX = startX + i * (categoryWidth + SelectionScreenStyle.CATEGORY_SPACING);

            List<Block> filteredBlocks = category.getItems().stream()
                .filter(block -> BlockSearchIndex.matches(block, searchText))
                .collect(Collectors.toList());

            Text categoryText = Text.literal(category.getTranslatedName().getString() + " (" + filteredBlocks.size() + ")");
            addDrawableChild(ButtonWidget.builder(categoryText, button -> onCategoryHeaderClick(category))
                .dimensions(columnX, startY, categoryWidth, SelectionScreenStyle.CATEGORY_BUTTON_HEIGHT)
                .build());

            int bottomSpace = SelectionScreenStyle.BUTTON_HEIGHT + SelectionScreenStyle.BOTTOM_MARGIN
                + SelectionScreenStyle.BUTTON_HEIGHT + SelectionScreenStyle.SEARCH_BOTTOM_SPACING
                + SelectionScreenStyle.PAGE_NUMBER_SPACING;
            int panelHeight = height - startY - SelectionScreenStyle.CATEGORY_BUTTON_HEIGHT
                - SelectionScreenStyle.CATEGORY_TO_BLOCK_SPACING - bottomSpace + 2;

            BlockGridScrollPanel panel = new BlockGridScrollPanel(
                columnX,
                startY + SelectionScreenStyle.CATEGORY_BUTTON_HEIGHT + SelectionScreenStyle.CATEGORY_TO_BLOCK_SPACING,
                categoryWidth,
                panelHeight,
                filteredBlocks,
                columnsPerRow,
                selectionStrategy,
                BlockCellRenderer.DisplayMode.DEFAULT,
                null,
                this::onSelectionChanged
            );
            addDrawableChild(panel);
        }
    }

    protected void onCategoryHeaderClick(SelectionCategory<Block> category) {
        selectionStrategy.toggleCategory(category.getItems());
        refreshCategoryButtons();
    }

    protected void onSearchTextChanged(String text) {
        if (!text.equals(searchText)) {
            searchText = text;
            refreshCategoryButtons();
        }
    }

    protected void focusOnMatch() {
        if (searchText.isEmpty()) {
            return;
        }
        for (int i = 0; i < categories.size(); i++) {
            SelectionCategory<Block> category = categories.get(i);
            List<Block> items = category.getItems();
            for (int j = 0; j < items.size(); j++) {
                Block block = items.get(j);
                if (BlockSearchIndex.matches(block, searchText)) {
                    int targetPage = i / SelectionScreenStyle.CATEGORIES_PER_PAGE;
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

    protected void scrollToBlock(Block targetBlock, int blockIndex) {
        for (Element child : children()) {
            if (child instanceof BlockGridScrollPanel panel && panel.getBlocks().contains(targetBlock)) {
                int row = blockIndex / columnsPerRow;
                int targetScrollOffset = row * (SelectionScreenStyle.BLOCK_SIZE + SelectionScreenStyle.BLOCK_SPACING);
                panel.setScrollOffset(targetScrollOffset);
                break;
            }
        }
    }

    protected String getPageText() {
        int totalPages = Math.max(1, (categories.size() - 1) / SelectionScreenStyle.CATEGORIES_PER_PAGE + 1);
        return (currentPage + 1) + "/" + totalPages;
    }

    protected int footerSearchBoxY() {
        int buttonY = height - SelectionScreenStyle.BUTTON_HEIGHT - SelectionScreenStyle.BOTTOM_MARGIN;
        return buttonY - SelectionScreenStyle.BUTTON_HEIGHT - SelectionScreenStyle.SEARCH_BOTTOM_SPACING;
    }

    protected void selectAllVisibleItems() {
        List<Block> all = new ArrayList<>();
        for (SelectionCategory<Block> category : categories) {
            all.addAll(category.getItems());
        }
        selectionStrategy.selectAll(all);
        refreshCategoryButtons();
    }

    protected void clearSelection() {
        selectionStrategy.clearAll();
        refreshCategoryButtons();
    }

    protected void returnToParent() {
        if (client != null) {
            client.setScreen(parent);
        }
    }
}
