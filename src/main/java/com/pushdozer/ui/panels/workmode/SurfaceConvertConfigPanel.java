package com.pushdozer.ui.panels.workmode;

import com.pushdozer.config.PushdozerConfig;
import com.pushdozer.ui.screens.NaturalBlockSelectionScreen;
import com.pushdozer.ui.screens.PushdozerConfigScreen;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;

/**
 * 表层转换配置面板
 * 支持最多5种方块的配置，每种方块有对应的占比滑动条
 */
public class SurfaceConvertConfigPanel extends WorkModeConfigPanel {
    
    private List<BlockConfigRow> blockRows = new ArrayList<>();
    private int panelHeight;
    private static final int MAX_BLOCKS = 5;
    private static final int ROW_HEIGHT = 20;
    private static final int BLOCK_ICON_SIZE = 20;

    public SurfaceConvertConfigPanel(PushdozerConfigScreen parent, PushdozerConfig config) {
        super(parent, config);
    }

    @Override
    protected Text getTitleText() {
        return Text.translatable("pushdozer.mode.surface_convert");
    }

    @Override
    protected int getPanelHeight() {
        return panelHeight;
    }

    @Override
    protected void initializeWidgets() {
        widgets.clear();
        if (blockRows == null) {
            blockRows = new ArrayList<>();
        } else {
            blockRows.clear();
        }

        // 第一步：预计算面板高度
        int tempPanelTop = 0; // 先假设为0
        int contentLeft;
        int contentTop = tempPanelTop + TITLE_HEIGHT + WIDGET_MARGIN;
        int contentWidth;
        List<PushdozerConfig.SurfaceConvertBlock> surfaceBlocks = config.getSurfaceConvertBlocks();
        int currentY = contentTop;
        for (int i = 0; i < surfaceBlocks.size() && i < MAX_BLOCKS; i++) {
            currentY += ROW_HEIGHT + WIDGET_MARGIN;
        }
        if (surfaceBlocks.size() == 1) {
            currentY += WIDGET_HEIGHT + WIDGET_MARGIN; // addBlockButton
            currentY += WIDGET_HEIGHT; // confirmButton
            currentY += 5;
        } else if (surfaceBlocks.size() >= 2 && surfaceBlocks.size() < MAX_BLOCKS) {
            currentY += WIDGET_HEIGHT + WIDGET_MARGIN; // addBlockButton + normalizeButton
            currentY += WIDGET_HEIGHT; // confirmButton
            currentY += 5;
        } else if (surfaceBlocks.size() == MAX_BLOCKS) {
            currentY += WIDGET_HEIGHT; // normalizeButton + confirmButton
            currentY += 5;
        }
        int calculatedPanelHeight = currentY - tempPanelTop;

        // 第二步：设置panelTop和panelLeft为居中
        if (parent != null) {
            int screenWidth = parent.getScreenWidth();
            int screenHeight = parent.getScreenHeight();
            this.panelLeft = (screenWidth - PANEL_WIDTH) / 2;
            this.panelTop = (screenHeight - calculatedPanelHeight) / 2;
        }
        this.panelHeight = calculatedPanelHeight;
        if (parent != null) {
            initializeTitlePosition();
        }

        // 第三步：用新panelTop重新布局控件
        contentLeft = panelLeft + WIDGET_MARGIN;
        contentTop = panelTop + TITLE_HEIGHT + WIDGET_MARGIN;
        contentWidth = PANEL_WIDTH - (WIDGET_MARGIN * 2);
        currentY = contentTop;
        for (int i = 0; i < surfaceBlocks.size() && i < MAX_BLOCKS; i++) {
            PushdozerConfig.SurfaceConvertBlock block = surfaceBlocks.get(i);
            BlockConfigRow row = new BlockConfigRow(contentLeft, currentY, contentWidth, block, i);
            blockRows.add(row);
            widgets.addAll(row.getWidgets());
            currentY += ROW_HEIGHT + WIDGET_MARGIN;
        }
        if (surfaceBlocks.size() == 1) {
            ButtonWidget addBlockButton = ButtonWidget.builder(
                            Text.translatable("pushdozer.config.add_block"),
                            button -> addNewBlock())
                    .dimensions(contentLeft, currentY, contentWidth, WIDGET_HEIGHT)
                    .build();
            widgets.add(addBlockButton);
            currentY += WIDGET_HEIGHT + WIDGET_MARGIN;

            confirmButton = ButtonWidget.builder(
                            Text.translatable("pushdozer.button.done"),
                            button -> {
                                saveConfig();
                                closeSubPanel();
                            })
                    .dimensions(contentLeft, currentY, contentWidth, WIDGET_HEIGHT)
                    .build();
            widgets.add(confirmButton);
        } else if (surfaceBlocks.size() >= 2 && surfaceBlocks.size() < MAX_BLOCKS) {
            int halfWidth = (contentWidth - WIDGET_MARGIN) / 2;
            ButtonWidget addBlockButton = ButtonWidget.builder(
                            Text.translatable("pushdozer.config.add_block"),
                            button -> addNewBlock())
                    .dimensions(contentLeft, currentY, halfWidth, WIDGET_HEIGHT)
                    .build();
            widgets.add(addBlockButton);

            ButtonWidget normalizeButton = ButtonWidget.builder(
                            Text.translatable("pushdozer.config.normalize_percentages"),
                            button -> normalizePercentages())
                    .dimensions(contentLeft + halfWidth + WIDGET_MARGIN, currentY, halfWidth, WIDGET_HEIGHT)
                    .build();
            widgets.add(normalizeButton);
            currentY += WIDGET_HEIGHT + WIDGET_MARGIN;

            confirmButton = ButtonWidget.builder(
                            Text.translatable("pushdozer.button.done"),
                            button -> {
                                saveConfig();
                                closeSubPanel();
                            })
                    .dimensions(contentLeft, currentY, contentWidth, WIDGET_HEIGHT)
                    .build();
            widgets.add(confirmButton);
        } else if (surfaceBlocks.size() == MAX_BLOCKS) {
            int halfWidth = (contentWidth - WIDGET_MARGIN) / 2;
            ButtonWidget normalizeButton = ButtonWidget.builder(
                            Text.translatable("pushdozer.config.normalize_percentages"),
                            button -> normalizePercentages())
                    .dimensions(contentLeft, currentY, halfWidth, WIDGET_HEIGHT)
                    .build();
            widgets.add(normalizeButton);

            confirmButton = ButtonWidget.builder(
                            Text.translatable("pushdozer.button.done"),
                            button -> {
                                saveConfig();
                                closeSubPanel();
                            })
                    .dimensions(contentLeft + halfWidth + WIDGET_MARGIN, currentY, halfWidth, WIDGET_HEIGHT)
                    .build();
            widgets.add(confirmButton);
        }
    }

    private void addSelectedBlock(Block block) {
        List<PushdozerConfig.SurfaceConvertBlock> surfaceBlocks = config.getSurfaceConvertBlocks();
        
        // 检查是否已经存在该方块
        String blockId = Registries.BLOCK.getId(block).toString();
        boolean exists = surfaceBlocks.stream().anyMatch(b -> b.getBlockId().equals(blockId));
        
        if (!exists && surfaceBlocks.size() < MAX_BLOCKS) {
            surfaceBlocks.add(new PushdozerConfig.SurfaceConvertBlock(blockId, 0f));
            normalizePercentages(); // 自动分配百分比
        }
    }

    private void addNewBlock() {
        if (parent.getClient() != null) {
            parent.getClient().setScreen(new NaturalBlockSelectionScreen(parent, this::addSelectedBlock));
        }
    }

    private void removeBlock(int index) {
        List<PushdozerConfig.SurfaceConvertBlock> surfaceBlocks = config.getSurfaceConvertBlocks();
        if (index >= 0 && index < surfaceBlocks.size() && surfaceBlocks.size() > 1) {
            surfaceBlocks.remove(index);
            initializeWidgets(); // 重新初始化界面
        }
    }

    private void normalizePercentages() {
        List<PushdozerConfig.SurfaceConvertBlock> surfaceBlocks = config.getSurfaceConvertBlocks();
        if (surfaceBlocks.size() == 1) {
            // 单个方块时，强制设为100%
            surfaceBlocks.getFirst().setPercentage(100.0f);
        } else if (surfaceBlocks.size() > 1) {
            // 多个方块时，平均分配
            float equalPercentage = 100.0f / surfaceBlocks.size();
            for (PushdozerConfig.SurfaceConvertBlock block : surfaceBlocks) {
                block.setPercentage(equalPercentage);
            }
        }
        
        initializeWidgets(); // 重新初始化界面
    }

    @Override
    protected void renderBackground(DrawContext context) {
        // 直接使用 panelHeight
        context.fill(panelLeft, panelTop, panelLeft + PANEL_WIDTH, panelTop + this.panelHeight, COLOR_PANEL_BG);

        // 绘制标题栏背景
        context.fill(panelLeft, panelTop, panelLeft + PANEL_WIDTH, panelTop + TITLE_HEIGHT, COLOR_TITLE_BG);

        // 绘制面板边框
        context.drawBorder(panelLeft, panelTop, PANEL_WIDTH, this.panelHeight, COLOR_PANEL_BORDER);
    }

    @Override
    protected void renderWidgets(DrawContext context, int mouseX, int mouseY, float delta) {
        super.renderWidgets(context, mouseX, mouseY, delta);
        
        // 渲染方块图标
        if (blockRows != null) {
            for (BlockConfigRow row : blockRows) {
                row.renderBlockIcon(context, mouseX, mouseY);
            }
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!visible) return false;

        // 先让父类/控件处理点击
        for (net.minecraft.client.gui.Element widget : widgets) {
            if (widget.mouseClicked(mouseX, mouseY, button)) {
                return true;
            }
        }
        
        // 然后检查点击是否在面板内（用于捕获面板背景的点击）
        if (mouseX >= panelLeft && mouseX <= panelLeft + PANEL_WIDTH &&
            mouseY >= panelTop && mouseY <= panelTop + this.panelHeight) {
            // 点击在面板内，但没有点到任何控件上，消费掉这个事件，防止穿透
            return true; 
        }

        // 点击在面板外部，关闭面板
        hide();
        parent.showMainPanel();
        return true; // 消费掉这个事件
    }

    @Override
    public void saveConfig() {
        try {
            // 保存配置
            config.save();
            
            // 显示保存成功消息
            parent.showErrorMessage(Text.translatable("pushdozer.config.saved").getString());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void show() {
        visible = true;
        
        // 先计算面板位置
        this.panelLeft = (parent.getScreenWidth() - PANEL_WIDTH) / 2;
        
        // 清空控件列表，确保高度计算准确
        widgets.clear();
        confirmButton = null;
        
        // 初始化控件（包括确认按钮）
        initializeWidgets();
        
        // 现在可以正确计算面板高度和位置
        this.panelTop = (parent.getScreenHeight() - getPanelHeight()) / 2;
        
        // 重新计算所有控件的位置，因为panelTop已经确定
        recalculateAllWidgetPositions();
    }

    @Override
    protected void recalculateAllWidgetPositions() {
        // 重新计算标题位置
        initializeTitlePosition();
        
        // 重新计算所有内容控件的位置（包括确认按钮）
        recalculateContentWidgetPositions();
    }
    
    /**
     * 重新计算内容控件的位置
     */
    protected void recalculateContentWidgetPositions() {
        int contentLeft = panelLeft + WIDGET_MARGIN;
        int contentTop = panelTop + TITLE_HEIGHT + WIDGET_MARGIN;
        int contentWidth = PANEL_WIDTH - (WIDGET_MARGIN * 2);
        int currentY = contentTop;
        
        // 重新计算方块行的位置
        for (BlockConfigRow row : blockRows) {
            row.recalculatePosition(contentLeft, currentY, contentWidth);
            currentY += ROW_HEIGHT + WIDGET_MARGIN;
        }
        
        // 重新计算按钮位置（包括确认按钮）
        List<PushdozerConfig.SurfaceConvertBlock> surfaceBlocks = config.getSurfaceConvertBlocks();
        if (surfaceBlocks.size() == 1) {
            // 重新定位添加方块按钮和确认按钮
            if (widgets.size() >= 2) {
                if (widgets.get(widgets.size() - 2) instanceof net.minecraft.client.gui.widget.ClickableWidget clickableWidget) {
                    clickableWidget.setPosition(contentLeft, currentY);
                }
                currentY += WIDGET_HEIGHT + WIDGET_MARGIN;
                if (widgets.getLast() instanceof net.minecraft.client.gui.widget.ClickableWidget clickableWidget) {
                    clickableWidget.setPosition(contentLeft, currentY);
                }
            }
        } else if (surfaceBlocks.size() >= 2 && surfaceBlocks.size() < MAX_BLOCKS) {
            // 重新定位添加方块按钮、均匀分布按钮和确认按钮
            int halfWidth = (contentWidth - WIDGET_MARGIN) / 2;
            if (widgets.size() >= 3) {
                if (widgets.get(widgets.size() - 3) instanceof net.minecraft.client.gui.widget.ClickableWidget clickableWidget) {
                    clickableWidget.setPosition(contentLeft, currentY);
                }
                if (widgets.get(widgets.size() - 2) instanceof net.minecraft.client.gui.widget.ClickableWidget clickableWidget) {
                    clickableWidget.setPosition(contentLeft + halfWidth + WIDGET_MARGIN, currentY);
                }
                currentY += WIDGET_HEIGHT + WIDGET_MARGIN;
                if (widgets.getLast() instanceof net.minecraft.client.gui.widget.ClickableWidget clickableWidget) {
                    clickableWidget.setPosition(contentLeft, currentY);
                }
            }
        } else if (surfaceBlocks.size() == MAX_BLOCKS) {
            // 重新定位均匀分布按钮和确认按钮
            int halfWidth = (contentWidth - WIDGET_MARGIN) / 2;
            if (widgets.size() >= 2) {
                if (widgets.get(widgets.size() - 2) instanceof net.minecraft.client.gui.widget.ClickableWidget clickableWidget) {
                    clickableWidget.setPosition(contentLeft, currentY);
                }
                if (widgets.getLast() instanceof net.minecraft.client.gui.widget.ClickableWidget clickableWidget) {
                    clickableWidget.setPosition(contentLeft + halfWidth + WIDGET_MARGIN, currentY);
                }
            }
        }
    }

    /**
     * 方块配置行类
     * 包含方块图标、删除按钮和占比滑动条
     */
    private class BlockConfigRow {
        private final PushdozerConfig.SurfaceConvertBlock block;
        private final int index;
        private final int x, y, width;
        private ButtonWidget removeButton;
        private PercentageSlider percentageSlider;
        private List<net.minecraft.client.gui.Element> rowWidgets = new ArrayList<>();

        public BlockConfigRow(int x, int y, int width, PushdozerConfig.SurfaceConvertBlock block, int index) {
            this.block = block;
            this.index = index;
            this.x = x;
            this.y = y;
            this.width = width;
            
            initializeRowWidgets();
        }

        private void initializeRowWidgets() {
            // 只有当方块数量大于1时才显示删除按钮
            List<PushdozerConfig.SurfaceConvertBlock> surfaceBlocks = config.getSurfaceConvertBlocks();
            int sliderX = x + BLOCK_ICON_SIZE + 5; // 基础位置
            int sliderWidth = width - BLOCK_ICON_SIZE - 5; // 基础宽度
            
            if (surfaceBlocks.size() > 1) {
                removeButton = ButtonWidget.builder(
                    Text.literal("×"),
                    button -> removeBlock(index))
                    .dimensions(x + BLOCK_ICON_SIZE + 5, y, 20, 20)
                    .build();
                rowWidgets.add(removeButton);
                
                // 有删除按钮时，滑动条需要向右移动并调整宽度
                sliderX = x + BLOCK_ICON_SIZE + 30;
                sliderWidth = width - BLOCK_ICON_SIZE - 30;
            }

            // 占比滑动条
            percentageSlider = new PercentageSlider(
                sliderX, y, sliderWidth, 20, 
                block.getPercentage(), block);
            
            // 立即更新滑动条的值，确保单个方块时显示为100%
            percentageSlider.updateValueFromConfig();
            
            rowWidgets.add(percentageSlider);
        }

        public List<net.minecraft.client.gui.Element> getWidgets() {
            return rowWidgets;
        }

        public void renderBlockIcon(DrawContext context, int mouseX, int mouseY) {
            try {
                Block blockType = Registries.BLOCK.get(Identifier.of(block.getBlockId()));
                ItemStack itemStack = getDisplayStack(blockType);
                
                // 绘制背景
                context.fill(x, y, x + BLOCK_ICON_SIZE, y + BLOCK_ICON_SIZE, 0xFF373737);
                context.drawBorder(x, y, BLOCK_ICON_SIZE, BLOCK_ICON_SIZE, 0xFF8B8B8B);
                
                // 绘制方块图标
                if (!itemStack.isEmpty()) {
                    context.drawItem(itemStack, x + 2, y + 2);
                }
                
                // 检查鼠标悬停并显示工具提示
                if (mouseX >= x && mouseX < x + BLOCK_ICON_SIZE && mouseY >= y && mouseY < y + BLOCK_ICON_SIZE) {
                    Text tooltipText = blockType.getName();
                    context.drawTooltip(parent.getTextRenderer(), tooltipText, mouseX, mouseY);
                }
            } catch (Exception e) {
                // 如果无法获取方块，绘制错误图标
                context.fill(x, y, x + BLOCK_ICON_SIZE, y + BLOCK_ICON_SIZE, 0xFFFF0000);
            }
        }
        
        private ItemStack getDisplayStack(Block block) {
            if (block == Blocks.WATER) {
                return Items.WATER_BUCKET.getDefaultStack();
            } else if (block == Blocks.LAVA) {
                return Items.LAVA_BUCKET.getDefaultStack();
            } else if (block == Blocks.FROSTED_ICE) {
                return new ItemStack(Blocks.ICE);
            }
            
            if (block == Blocks.TALL_SEAGRASS) {
                return Items.SEAGRASS.getDefaultStack();
            } else if (block == Blocks.KELP_PLANT) {
                return Items.KELP.getDefaultStack();
            } else if (block == Blocks.WEEPING_VINES_PLANT) {
                return Items.WEEPING_VINES.getDefaultStack();
            } else if (block == Blocks.TWISTING_VINES_PLANT) {
                return Items.TWISTING_VINES.getDefaultStack();
            }
            
            String translationKey = block.getTranslationKey();
            if (translationKey.contains("wall_sign")) {
                String baseSignKey = translationKey.replace("wall_sign", "sign");
                try {
                    Block baseSign = Registries.BLOCK.get(Registries.BLOCK.getId(block).withPath(baseSignKey));
                    if (baseSign != Blocks.AIR) {
                        return baseSign.asItem().getDefaultStack();
                    }
                } catch (Exception e) {
                    System.err.println("Failed to find base sign for wall sign: " + block.getTranslationKey() + ", using original block");
                }
            }
            
            if (block == Blocks.WHEAT) {
                return Items.WHEAT_SEEDS.getDefaultStack();
            } else if (block == Blocks.CARROTS) {
                return Items.CARROT.getDefaultStack();
            } else if (block == Blocks.POTATOES) {
                return Items.POTATO.getDefaultStack();
            } else if (block == Blocks.BEETROOTS) {
                return Items.BEETROOT.getDefaultStack();
            }
            
            if (block == Blocks.FIRE) {
                return Items.FLINT_AND_STEEL.getDefaultStack();
            }
            
            String blockId = Registries.BLOCK.getId(block).getPath();
            if (blockId.contains("wall_head") || blockId.contains("wall_skull")) {
                String baseHeadId = blockId.replace("wall_", "");
                try {
                    Block baseHead = Registries.BLOCK.get(Registries.BLOCK.getId(block).withPath(baseHeadId));
                    if (baseHead != Blocks.AIR) {
                        return baseHead.asItem().getDefaultStack();
                    }
                } catch (Exception e) {
                    System.err.println("Failed to find base head for wall head: " + block.getTranslationKey() + ", using original block");
                }
            }
            
            // 处理一些特殊方块
            if (block == Blocks.BEDROCK) {
                return Items.BEDROCK.getDefaultStack();
            } else if (block == Blocks.COMMAND_BLOCK) {
                return Items.COMMAND_BLOCK.getDefaultStack();
            } else if (block == Blocks.STRUCTURE_BLOCK) {
                return Items.STRUCTURE_BLOCK.getDefaultStack();
            } else if (block == Blocks.JIGSAW) {
                return Items.JIGSAW.getDefaultStack();
            } else if (block == Blocks.BARRIER) {
                return Items.BARRIER.getDefaultStack();
            } else if (block == Blocks.LIGHT) {
                return Items.LIGHT.getDefaultStack();
            } else if (block == Blocks.SPAWNER) {
                return Items.SPAWNER.getDefaultStack();
            } else if (block == Blocks.TRIAL_SPAWNER) {
                return Items.TRIAL_SPAWNER.getDefaultStack();
            } else if (block == Blocks.DRAGON_EGG) {
                return Items.DRAGON_EGG.getDefaultStack();
            } else if (block == Blocks.END_PORTAL) {
                return Items.END_PORTAL_FRAME.getDefaultStack();
            } else if (block == Blocks.END_GATEWAY) {
                return Items.END_PORTAL_FRAME.getDefaultStack();
            } else if (block == Blocks.NETHER_PORTAL) {
                return Items.OBSIDIAN.getDefaultStack();
            } else if (block == Blocks.END_PORTAL_FRAME) {
                return Items.END_PORTAL_FRAME.getDefaultStack();
            } else if (block == Blocks.NETHER_WART) {
                return Items.NETHER_WART.getDefaultStack();
            } else if (block == Blocks.CRIMSON_FUNGUS) {
                return Items.CRIMSON_FUNGUS.getDefaultStack();
            } else if (block == Blocks.WARPED_FUNGUS) {
                return Items.WARPED_FUNGUS.getDefaultStack();
            } else if (block == Blocks.CRIMSON_ROOTS) {
                return Items.CRIMSON_ROOTS.getDefaultStack();
            } else if (block == Blocks.WARPED_ROOTS) {
                return Items.WARPED_ROOTS.getDefaultStack();
            } else if (block == Blocks.NETHER_SPROUTS) {
                return Items.NETHER_SPROUTS.getDefaultStack();
            } else if (block == Blocks.WEEPING_VINES) {
                return Items.WEEPING_VINES.getDefaultStack();
            } else if (block == Blocks.TWISTING_VINES) {
                return Items.TWISTING_VINES.getDefaultStack();
            } else if (block == Blocks.SHROOMLIGHT) {
                return Items.SHROOMLIGHT.getDefaultStack();
            } else if (block == Blocks.GLOW_LICHEN) {
                return Items.GLOW_LICHEN.getDefaultStack();
            } else if (block == Blocks.SCULK_VEIN) {
                return Items.SCULK_VEIN.getDefaultStack();
            } else if (block == Blocks.SCULK_CATALYST) {
                return Items.SCULK_CATALYST.getDefaultStack();
            } else if (block == Blocks.SCULK_SHRIEKER) {
                return Items.SCULK_SHRIEKER.getDefaultStack();
            } else if (block == Blocks.SCULK_SENSOR) {
                return Items.SCULK_SENSOR.getDefaultStack();
            } else if (block == Blocks.CALIBRATED_SCULK_SENSOR) {
                return Items.CALIBRATED_SCULK_SENSOR.getDefaultStack();
            } else if (block == Blocks.SCULK) {
                return Items.SCULK.getDefaultStack();
            }
            
            // 处理一些没有物品形式的方块
            ItemStack itemStack = block.asItem().getDefaultStack();
            if (itemStack.isEmpty()) {
                // 如果方块没有对应的物品，尝试使用替代物品
                if (block == Blocks.AIR) {
                    return ItemStack.EMPTY;
                } else if (block == Blocks.CAVE_AIR) {
                    return ItemStack.EMPTY;
                } else if (block == Blocks.VOID_AIR) {
                    return ItemStack.EMPTY;
                } else if (block == Blocks.STRUCTURE_VOID) {
                    return ItemStack.EMPTY;
                } else if (block == Blocks.END_PORTAL) {
                    return ItemStack.EMPTY;
                } else if (block == Blocks.END_GATEWAY) {
                    return ItemStack.EMPTY;
                } else if (block == Blocks.NETHER_PORTAL) {
                    return ItemStack.EMPTY;
                } else if (block == Blocks.LIGHT) {
                    return ItemStack.EMPTY;
                } else {
                    // 对于其他没有物品形式的方块，根据方块类型使用合适的替代物品
                    String blockIdPath = Registries.BLOCK.getId(block).getPath().toLowerCase();
                    if (blockIdPath.contains("portal")) {
                        return Items.OBSIDIAN.getDefaultStack();
                    } else if (blockIdPath.contains("light")) {
                        return Items.GLOWSTONE.getDefaultStack();
                    } else if (blockIdPath.contains("air")) {
                        return ItemStack.EMPTY;
                    } else if (blockIdPath.contains("void")) {
                        return ItemStack.EMPTY;
                    } else {
                        // 默认使用石头作为替代
                        return Items.STONE.getDefaultStack();
                    }
                }
            }
            
            return itemStack;
        }

        public void recalculatePosition(int newX, int newY, int newWidth) {
            // 重新计算控件位置
            List<PushdozerConfig.SurfaceConvertBlock> surfaceBlocks = config.getSurfaceConvertBlocks();
            int sliderX = newX + BLOCK_ICON_SIZE + 5; // 基础位置
            
            if (surfaceBlocks.size() > 1) {
                if (removeButton != null) {
                    removeButton.setPosition(newX + BLOCK_ICON_SIZE + 5, newY);
                }
                sliderX = newX + BLOCK_ICON_SIZE + 30;
            }

            if (percentageSlider != null) {
                percentageSlider.setPosition(sliderX, newY);
            }
        }
    }

    /**
     * 占比滑动条 - 优化的更新策略
     */
    private class PercentageSlider extends SliderWidget {
        private final PushdozerConfig.SurfaceConvertBlock block;

        public PercentageSlider(int x, int y, int width, int height, float percentage, PushdozerConfig.SurfaceConvertBlock block) {
            super(x, y, width, height, 
                Text.translatable("pushdozer.config.percentage", String.format("%.1f", percentage)), 
                getInitialValue(percentage));
            this.block = block;
        }
        
        /**
         * 获取初始值 - 如果只有一个方块，强制为100%
         */
        private static double getInitialValue(float percentage) {
            // 由于这是静态方法，我们需要在创建滑动条时检查方块数量
            // 这里先使用传入的percentage，然后在updateValueFromConfig中修正
            return percentage / 100.0;
        }

        @Override
        protected void updateMessage() {
            float percentage = (float)(this.value * 100.0);
            setMessage(Text.translatable("pushdozer.config.percentage", String.format("%.1f", percentage)));
        }

        @Override
        protected void applyValue() {
            List<PushdozerConfig.SurfaceConvertBlock> surfaceBlocks = config.getSurfaceConvertBlocks();
            
            // 如果只有一个方块，强制设为100%并且不允许调整
            if (surfaceBlocks.size() == 1) {
                block.setPercentage(100.0f);
                this.value = 1.0; // 强制滑动条位置为100%
                updateMessage();
                return; // 直接返回，不执行后续逻辑
            }
            
            // 多个方块时的正常逻辑
            float percentage = (float)(this.value * 100.0);
            block.setPercentage(percentage);
            
            // 调整其他方块的百分比
            adjustOtherPercentages(block, percentage);
            
            // 使用轻量级更新，只更新其他滑动条的显示
            updateOtherSliders();
        }
        
        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            // 如果只有一个方块，禁用滑动条交互
            List<PushdozerConfig.SurfaceConvertBlock> surfaceBlocks = config.getSurfaceConvertBlocks();
            if (surfaceBlocks.size() == 1) {
                return false; // 不处理点击事件
            }
            return super.mouseClicked(mouseX, mouseY, button);
        }
        
        @Override
        public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
            // 如果只有一个方块，禁用滑动条拖拽
            List<PushdozerConfig.SurfaceConvertBlock> surfaceBlocks = config.getSurfaceConvertBlocks();
            if (surfaceBlocks.size() == 1) {
                return false; // 不处理拖拽事件
            }
            return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
        }
        
        @Override
        public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
            // 如果只有一个方块，禁用滑动条滚轮
            List<PushdozerConfig.SurfaceConvertBlock> surfaceBlocks = config.getSurfaceConvertBlocks();
            if (surfaceBlocks.size() == 1) {
                return false; // 不处理滚轮事件
            }
            return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
        }
        
        /**
         * 轻量级更新其他滑动条 - 避免重建整个UI
         */
        private void updateOtherSliders() {
            for (BlockConfigRow row : blockRows) {
                if (row.percentageSlider != this) {
                    row.percentageSlider.updateValueFromConfig();
                }
            }
        }
        
        /**
         * 从配置数据更新滑块状态 - 避免触发applyValue造成无限循环
         */
        public void updateValueFromConfig() {
            List<PushdozerConfig.SurfaceConvertBlock> surfaceBlocks = config.getSurfaceConvertBlocks();
            
            // 如果只有一个方块，强制显示为100%
            if (surfaceBlocks.size() == 1) {
                this.value = 1.0;
                block.setPercentage(100.0f);
            } else {
                this.value = block.getPercentage() / 100.0;
            }
            
            updateMessage(); // 只更新显示文本
        }
        
        private void adjustOtherPercentages(PushdozerConfig.SurfaceConvertBlock currentBlock, float currentPercentage) {
            List<PushdozerConfig.SurfaceConvertBlock> surfaceBlocks = config.getSurfaceConvertBlocks();
            
            // 计算其他方块的总百分比
            float otherTotal = 0f;
            int otherCount = 0;
            for (PushdozerConfig.SurfaceConvertBlock block : surfaceBlocks) {
                if (block != currentBlock) {
                    otherTotal += block.getPercentage();
                    otherCount++;
                }
            }
            
            // 计算剩余百分比
            float remainingPercentage = 100.0f - currentPercentage;
            
            if (otherCount > 0 && remainingPercentage >= 0) {
                // 按比例分配剩余百分比给其他方块
                for (PushdozerConfig.SurfaceConvertBlock block : surfaceBlocks) {
                    if (block != currentBlock) {
                        if (otherTotal > 0) {
                            // 按原比例分配
                            float ratio = block.getPercentage() / otherTotal;
                            block.setPercentage(remainingPercentage * ratio);
                        } else {
                            // 如果其他方块都是0，则平均分配
                            block.setPercentage(remainingPercentage / otherCount);
                        }
                    }
                }
            }
            
            // *** 新增：浮点数精度校准 ***
            float totalPercentage = 0f;
            PushdozerConfig.SurfaceConvertBlock largestBlock = null;
            float maxPercentage = -1f;

            for (PushdozerConfig.SurfaceConvertBlock b : surfaceBlocks) {
                totalPercentage += b.getPercentage();
                if (b != currentBlock && b.getPercentage() > maxPercentage) {
                    maxPercentage = b.getPercentage();
                    largestBlock = b;
                }
            }
            
            if (largestBlock == null && !surfaceBlocks.isEmpty()) {
                // 如果只有一个方块，或者当前方块是唯一的其他方块
                largestBlock = surfaceBlocks.stream().filter(b -> b != currentBlock).findFirst().orElse(null);
            }

            if (largestBlock != null) {
                float difference = 100.0f - totalPercentage;
                // 将误差加到最大的那个"其他"方块上
                largestBlock.setPercentage(largestBlock.getPercentage() + difference);
            }
        }
    }
} 