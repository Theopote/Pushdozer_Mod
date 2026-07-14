package com.pushdozer.items;

import com.pushdozer.PushdozerMod;
import com.pushdozer.config.PushdozerConfig;
import com.pushdozer.config.PushdozerConfig.WorkMode;
import com.pushdozer.component.PushdozerComponents;
import com.pushdozer.items.handlers.TerrainToolHandler;
import com.pushdozer.services.ConfigService;
import com.mojang.serialization.Codec;
import com.pushdozer.operations.UndoAction;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;
import net.minecraft.util.ActionResult;
import net.minecraft.world.World;

import java.util.Objects;

/**
 * Pushdozer Item Class
 * Provides core functionality for terrain editing tools
 */
public class PushdozerItem extends Item {

    /**
     * Constructor
     * @param settings item settings
     */
    public PushdozerItem(Settings settings) {
        // Set default data component values during item construction
        super(settings.component(PushdozerComponents.DISPLAY_MODE, DisplayMode.NONE));
    }

    /**
     * Get display mode from ItemStack
     * Uses our own type-safe custom data component
     */
    public DisplayMode getDisplayMode(ItemStack stack) {
        return stack.getOrDefault(PushdozerComponents.DISPLAY_MODE, DisplayMode.NONE);
    }

    /**
     * Set display mode to ItemStack
     * Uses our own type-safe custom data component
     */
    public void setDisplayMode(ItemStack stack, DisplayMode mode) {
        stack.set(PushdozerComponents.DISPLAY_MODE, mode);
    }

    /**
     * Handle logic when the item is used
     * Uses HandlerRegistry to obtain the corresponding handler
     */
    @Override
    public ActionResult use(World world, PlayerEntity player, Hand hand) {
        if (!world.isClient()) {
            PushdozerConfig config = ConfigService.getInstance().getConfig(player);
            WorkMode currentMode = config.getWorkMode();

            // Get handler from HandlerRegistry
            TerrainToolHandler handler = PushdozerMod.getHandler(currentMode);
            if (handler != null) {
                // Call appropriate methods based on different WorkModes
                switch (currentMode) {
                    case EXCAVATE -> handler.handleExcavation(player, world, config);
                    case PLACE -> handler.handlePlacement(player, world, config);
                    case SMOOTH -> {
                        PushdozerConfig.SmoothVariant variant = config.getSmoothVariant();
                        switch (variant) {
                            case RAISE -> Objects.requireNonNull(PushdozerMod.getHandler(WorkMode.SMOOTH_RAISE)).handleSmoothRaise(player, world, config);
                            case LOWER -> Objects.requireNonNull(PushdozerMod.getHandler(WorkMode.SMOOTH_LOWER)).handleSmoothLower(player, world, config);
                            default -> Objects.requireNonNull(PushdozerMod.getHandler(WorkMode.ADAPTIVE_SMOOTH)).handleOperation(player, world, UndoAction.ActionType.SMOOTH, config);
                        }
                    }
                    case SMOOTH_RAISE -> handler.handleSmoothRaise(player, world, config);
                    case SMOOTH_LOWER -> handler.handleSmoothLower(player, world, config);
                    case SURFACE_ROUGHEN -> handler.handleSurfaceRoughen(player, world, config);
                    case ADAPTIVE_SMOOTH -> handler.handleOperation(player, world, UndoAction.ActionType.SMOOTH, config);
                    case SURFACE_CONVERT -> handler.handleSurfaceConvert(player, world, config);
                    case BONE_MEAL -> handler.handleBoneMeal(player, world, config);
                    case BATCH_PLANT -> handler.handleBatchPlant(player, world, config);
                    case SHORELINE_PROCESS -> handler.handleShorelineProcess(player, world, config);
                }
            } else {
                PushdozerMod.LOGGER.warn("No handler registered for work mode: {}", currentMode);
            }
        }
        return ActionResult.SUCCESS;
    }

    /**
     * Display mode enumeration
     * Fixed the unused parameter issue in the constructor
     */
    public enum DisplayMode {
        NONE("pushdozer.display_mode.none"),
        WIREFRAME("pushdozer.display_mode.wireframe"),
        POINT_CLOUD("pushdozer.display_mode.point_cloud");

        // Add Codec for enum to enable serialization by the data component system
        // Use custom resolver to handle unknown enum values and provide defaults
        public static final Codec<DisplayMode> CODEC = Codec.stringResolver(
            DisplayMode::name,
            str -> {
                try {
                    return DisplayMode.valueOf(str);
                } catch (IllegalArgumentException e) {
                    // If encountering unknown enum values (like deleted SURFACE), return default value NONE
                    PushdozerMod.LOGGER.warn("Unknown display mode '{}', using NONE as fallback", str);
                    return DisplayMode.NONE;
                }
            }
        );

        DisplayMode(String translationKey) {
        }
    }
}