package com.pushdozer.items;

import java.util.Map;

import com.google.common.collect.ImmutableMap;
import com.pushdozer.PushdozerMod;
import com.pushdozer.config.PushdozerConfig;
import com.pushdozer.component.PushdozerComponents;
import com.mojang.serialization.Codec;
import com.pushdozer.operations.UndoAction;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.ActionResult;
import net.minecraft.world.World;

/**
 * Pushdozer 物品类
 * 提供地形编辑工具的核心功能
 */
public class PushdozerItem extends Item {

    /**
     * 操作处理器接口
     * 定义所有地形操作处理器的通用行为
     */
    @FunctionalInterface
    public interface IOperationHandler {
        void handle(PlayerEntity player, World world, PushdozerConfig config);
    }
    
    /**
     * 工作模式到操作处理器的映射
     * 使用预创建的处理器实例，避免频繁创建对象导致的性能问题
     */
    private static final Map<PushdozerConfig.WorkMode, IOperationHandler> OPERATION_HANDLERS = 
        ImmutableMap.<PushdozerConfig.WorkMode, IOperationHandler>builder()
            .put(PushdozerConfig.WorkMode.EXCAVATE, (p, w, c) -> PushdozerMod.excavationHandler.handleExcavation(p, w))
            .put(PushdozerConfig.WorkMode.PLACE, (p, w, c) -> PushdozerMod.placementHandler.handlePlacement(p, w))
            // 统一平滑模式，根据变体转发到具体处理器
            .put(PushdozerConfig.WorkMode.SMOOTH, (p, w, c) -> {
                PushdozerConfig.SmoothVariant variant = c.getSmoothVariant();
                switch (variant) {
                    case RAISE -> PushdozerMod.smoothRaiseHandler.handleSmoothRaise(p, w);
                    case LOWER -> PushdozerMod.smoothLowerHandler.handleSmoothLower(p, w);
                    case ADAPTIVE -> PushdozerMod.adaptiveSmoothHandler.handleOperation(p, w, UndoAction.ActionType.SMOOTH);
                    default -> PushdozerMod.adaptiveSmoothHandler.handleOperation(p, w, UndoAction.ActionType.SMOOTH);
                }
            })
            .put(PushdozerConfig.WorkMode.SMOOTH_RAISE, (p, w, c) -> PushdozerMod.smoothRaiseHandler.handleSmoothRaise(p, w))
            .put(PushdozerConfig.WorkMode.SMOOTH_LOWER, (p, w, c) -> PushdozerMod.smoothLowerHandler.handleSmoothLower(p, w))
            .put(PushdozerConfig.WorkMode.SURFACE_ROUGHEN, (p, w, c) -> PushdozerMod.surfaceRoughenHandler.handleSurfaceRoughen(p, w))
            .put(PushdozerConfig.WorkMode.ADAPTIVE_SMOOTH, (p, w, c) -> PushdozerMod.adaptiveSmoothHandler.handleOperation(p, w, UndoAction.ActionType.SMOOTH))
            .put(PushdozerConfig.WorkMode.SURFACE_CONVERT, (p, w, c) -> PushdozerMod.surfaceConvertHandler.handleSurfaceConvert(p, w))
            .put(PushdozerConfig.WorkMode.BONE_MEAL, (p, w, c) -> PushdozerMod.boneMealHandler.handleBoneMeal(p, w))
            .put(PushdozerConfig.WorkMode.BATCH_PLANT, (p, w, c) -> PushdozerMod.batchPlantHandler.handleBatchPlant(p, w))
            .put(PushdozerConfig.WorkMode.SHORELINE_PROCESS, (p, w, c) -> PushdozerMod.shorelineProcessHandler.handleShorelineProcess(p, w))
            .build();

    /**
     * 构造函数
     * @param settings 物品设置
     */
    public PushdozerItem(Settings settings) {
        // 在物品构建时设置默认的数据组件值
        super(settings.component(PushdozerComponents.DISPLAY_MODE, DisplayMode.NONE));
    }

    /**
     * 从 ItemStack 获取显示模式
     * 使用我们自己的、类型安全的自定义数据组件
     */
    public DisplayMode getDisplayMode(ItemStack stack) {
        return stack.getOrDefault(PushdozerComponents.DISPLAY_MODE, DisplayMode.NONE);
    }

    /**
     * 设置显示模式到 ItemStack
     * 使用我们自己的、类型安全的自定义数据组件
     */
    public void setDisplayMode(ItemStack stack, DisplayMode mode) {
        stack.set(PushdozerComponents.DISPLAY_MODE, mode);
    }

    /**
     * 切换显示模式
     * @param stack 物品堆叠
     * @return 新的显示模式
     */
    public DisplayMode toggleDisplayMode(ItemStack stack) {
        DisplayMode[] modes = DisplayMode.values();
        int nextIndex = (getDisplayMode(stack).ordinal() + 1) % modes.length;
        DisplayMode newMode = modes[nextIndex];
        setDisplayMode(stack, newMode);
        return newMode;
    }

    /**
     * 物品使用时的处理逻辑
     * 使用策略模式消除重复代码，现在使用预创建的处理器实例
     */
    @Override
    public ActionResult use(World world, PlayerEntity player, Hand hand) {
        if (!world.isClient) {
            PushdozerConfig config = PushdozerMod.getConfig();
            PushdozerConfig.WorkMode currentMode = config.getWorkMode();

            // 从Map中获取操作并执行，使用预创建的处理器实例
            IOperationHandler handler = OPERATION_HANDLERS.get(currentMode);
            if (handler != null) {
                handler.handle(player, world, config);
            }
        }
        return ActionResult.SUCCESS;
    }

    /**
     * 显示模式枚举
     * 修复了构造函数中未使用的参数问题
     */
    public enum DisplayMode {
        NONE("pushdozer.display_mode.none"),
        WIREFRAME("pushdozer.display_mode.wireframe"),
        POINT_CLOUD("pushdozer.display_mode.point_cloud");

        // 为枚举添加Codec，使其能被数据组件系统序列化
        // 使用自定义的解析器来处理未知的枚举值，提供默认值
        public static final Codec<DisplayMode> CODEC = Codec.stringResolver(
            DisplayMode::name,
            str -> {
                try {
                    return DisplayMode.valueOf(str);
                } catch (IllegalArgumentException e) {
                    // 如果遇到未知的枚举值（如已删除的SURFACE），返回默认值NONE
                    PushdozerMod.LOGGER.warn("Unknown display mode '{}', using NONE as fallback", str);
                    return DisplayMode.NONE;
                }
            }
        );

        private final String translationKey;

        DisplayMode(String translationKey) {
            this.translationKey = translationKey;
        }

        /**
         * 获取显示名称（翻译后的文本）
         * @return 翻译后的显示名称
         */
        public String getDisplayName() {
            return Text.translatable(translationKey).getString();
        }

        /**
         * 获取翻译文本对象
         * @return 翻译文本对象
         */
        public Text getDisplayText() {
            return Text.translatable(translationKey);
        }
    }
}