package com.pushdozer.ui.selection;

import com.pushdozer.util.BlockDisplayIcons;
import net.minecraft.block.Block;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;

import java.util.function.Function;

public final class BlockCellRenderer {
    public enum DisplayMode {
        DEFAULT(BlockDisplayIcons::getDisplayStack),
        PLANT(BlockDisplayIcons::getPlantDisplayStack);

        private final Function<Block, ItemStack> stackProvider;

        DisplayMode(Function<Block, ItemStack> stackProvider) {
            this.stackProvider = stackProvider;
        }

        public ItemStack stackFor(Block block) {
            return stackProvider.apply(block);
        }
    }

    private BlockCellRenderer() {
    }

    public static void renderCell(DrawContext context,
                                  TextRenderer textRenderer,
                                  Block block,
                                  int x,
                                  int y,
                                  int mouseX,
                                  int mouseY,
                                  boolean selected,
                                  DisplayMode displayMode,
                                  BlockCellDecorator decorator) {
        int borderColor = selected ? SelectionScreenStyle.SELECTED_BLOCK_BORDER_COLOR : SelectionScreenStyle.BLOCK_BORDER_COLOR;
        context.fill(x, y, x + SelectionScreenStyle.BLOCK_SIZE, y + SelectionScreenStyle.BLOCK_SIZE, borderColor);
        context.fill(x + 1, y + 1, x + SelectionScreenStyle.BLOCK_SIZE - 1, y + SelectionScreenStyle.BLOCK_SIZE - 1,
            SelectionScreenStyle.BLOCK_BACKGROUND_COLOR);

        if (mouseX >= x && mouseX < x + SelectionScreenStyle.BLOCK_SIZE
            && mouseY >= y && mouseY < y + SelectionScreenStyle.BLOCK_SIZE) {
            context.fill(x + 1, y + 1, x + SelectionScreenStyle.BLOCK_SIZE - 1, y + SelectionScreenStyle.BLOCK_SIZE - 1,
                SelectionScreenStyle.BLOCK_HOVER_COLOR);
        }

        ItemStack displayStack = displayMode.stackFor(block);
        if (!displayStack.isEmpty()) {
            int iconOffset = (SelectionScreenStyle.BLOCK_SIZE - 16) / 2;
            context.drawItem(displayStack, x + iconOffset, y + iconOffset);
        }

        String blockId = Registries.BLOCK.getId(block).getPath();
        if (blockId.contains("wall_head") || blockId.contains("wall_skull") || blockId.contains("wall_sign")) {
            int underlineY = y + SelectionScreenStyle.BLOCK_SIZE - 3;
            int underlineColor = selected ? 0xFFFFFFFF : 0xFF666666;
            context.fill(x + 2, underlineY, x + SelectionScreenStyle.BLOCK_SIZE - 2, underlineY + 1, underlineColor);
        }

        if (decorator != null) {
            decorator.decorate(context, block, x, y);
        }

        if (selected) {
            context.fill(x + 1, y + 1, x + SelectionScreenStyle.BLOCK_SIZE - 1, y + SelectionScreenStyle.BLOCK_SIZE - 1, 0x80FFFFFF);
            context.drawText(textRenderer, Text.literal("☑"),
                x + SelectionScreenStyle.BLOCK_SIZE - 8, y + SelectionScreenStyle.BLOCK_SIZE - 9, 0xFF000000, false);
        }
    }

    @FunctionalInterface
    public interface BlockCellDecorator {
        void decorate(DrawContext context, Block block, int x, int y);
    }

    public static final BlockCellDecorator PottedCellDecorator = (context, block, x, y) -> {
        if (isPottedBlock(block)) {
            drawPottedRing(context, x, y);
        }
    };

    private static boolean isPottedBlock(Block block) {
        String id = Registries.BLOCK.getId(block).getPath().toLowerCase();
        String key = block.getTranslationKey().toLowerCase();
        return id.startsWith("potted_") || key.contains("potted");
    }

    private static void drawPottedRing(DrawContext context, int clipX, int clipY) {
        int cx = clipX + SelectionScreenStyle.BLOCK_SIZE / 2;
        int cy = clipY + SelectionScreenStyle.BLOCK_SIZE / 2;
        int outerR = Math.max(7, (SelectionScreenStyle.BLOCK_SIZE - 4) / 2);
        int thickness = 3;
        int ringColor = 0x88FFFFFF;
        int innerR = Math.max(0, outerR - thickness);
        int minY = Math.max(clipY, cy - outerR);
        int maxY = Math.min(clipY + SelectionScreenStyle.BLOCK_SIZE - 1, cy + outerR);
        for (int y = minY; y <= maxY; y++) {
            int dy = y - cy;
            int y2 = dy * dy;
            int xOuter = (int) Math.floor(Math.sqrt(Math.max(0, outerR * outerR - y2)));
            int xInner = innerR > 0 ? (int) Math.floor(Math.sqrt(Math.max(0, innerR * innerR - y2))) : -1;

            int leftOuter = cx - xOuter;
            int rightOuter = cx + xOuter;
            int leftInner = cx - xInner;
            int rightInner = cx + xInner;

            int lx1 = Math.max(clipX, leftOuter);
            int lx2 = Math.min(clipX + SelectionScreenStyle.BLOCK_SIZE - 1, leftInner - 1);
            if (lx1 <= lx2) {
                context.fill(lx1, y, lx2 + 1, y + 1, ringColor);
            }
            int rx1 = Math.max(clipX, rightInner + 1);
            int rx2 = Math.min(clipX + SelectionScreenStyle.BLOCK_SIZE - 1, rightOuter);
            if (rx1 <= rx2) {
                context.fill(rx1, y, rx2 + 1, y + 1, ringColor);
            }
        }
    }
}
