package com.pushdozer;

import com.pushdozer.config.PushdozerConfig;
import com.pushdozer.config.domain.WorkMode;
import com.pushdozer.items.ModItems;
import com.pushdozer.items.handlers.*;
import com.pushdozer.operations.UndoAction;
import com.pushdozer.registry.HandlerRegistry;
import com.pushdozer.ui.PushdozerConfigScreenHandler;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemGroups;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.resource.featuretoggle.FeatureFlags;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.pushdozer.services.UndoRedoService;
import com.pushdozer.network.NetworkManager;

public class PushdozerMod implements ModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger("pushdozer");
    public static final String MOD_ID = "pushdozer";

    public static ScreenHandlerType<PushdozerConfigScreenHandler> CONFIG_SCREEN_HANDLER;
    private static PushdozerConfig config;
    private static HandlerRegistry handlerRegistry;

    @Override
    public void onInitialize() {
        LOGGER.info("Initializing Pushdozer mod...");

        ModItems.registerItems();
        PushdozerMod.LOGGER.info("Successfully registered item: {}", Registries.ITEM.getId(ModItems.PUSHDOZER_ITEM));

        config = PushdozerConfig.getInstance();

        ItemGroupEvents.modifyEntriesEvent(ItemGroups.TOOLS).register(content -> {
            content.add(ModItems.PUSHDOZER_ITEM);
            PushdozerMod.LOGGER.info("Added Pushdozer item to tools item group");
        });

        CONFIG_SCREEN_HANDLER = Registry.register(
                Registries.SCREEN_HANDLER,
                Identifier.of(MOD_ID, "config_screen"),
                new ScreenHandlerType<>((syncId, inventory) ->
                        new PushdozerConfigScreenHandler(syncId, inventory, null), FeatureFlags.VANILLA_FEATURES)
        );

        // Initialize handler registry and register all handlers
        handlerRegistry = new HandlerRegistry();
        registerHandlers();

        NetworkManager.registerNetworking();

        LOGGER.info("Pushdozer mod has been initialized！");
    }

    /**
     * Register all terrain tool handlers with the registry.
     */
    private void registerHandlers() {
        handlerRegistry.register(WorkMode.PLACE, new PlacementHandler());
        handlerRegistry.register(WorkMode.SMOOTH, new SmoothingHandler());
        handlerRegistry.register(WorkMode.EXCAVATE, new ExcavationHandler());
        handlerRegistry.register(WorkMode.SMOOTH_RAISE, new SmoothRaiseHandler());
        handlerRegistry.register(WorkMode.SMOOTH_LOWER, new SmoothLowerHandler());
        handlerRegistry.register(WorkMode.SURFACE_ROUGHEN, new SurfaceRoughenHandler());
        handlerRegistry.register(WorkMode.SURFACE_CONVERT, new SurfaceConvertHandler());
        handlerRegistry.register(WorkMode.BONE_MEAL, new BoneMealHandler());
        handlerRegistry.register(WorkMode.BATCH_PLANT, new BatchPlantHandler());
        handlerRegistry.register(WorkMode.SHORELINE_PROCESS, new ShorelineProcessHandler());
        handlerRegistry.register(WorkMode.ADAPTIVE_SMOOTH, new AdaptiveSmoothHandler());

        LOGGER.info("Registered {} terrain tool handlers", handlerRegistry.size());
    }

    /**
     * Get the handler registry.
     *
     * @return the handler registry instance
     */
    public static HandlerRegistry getHandlerRegistry() {
        return handlerRegistry;
    }

    /**
     * Get a handler for a specific work mode.
     * For backward compatibility with existing code.
     *
     * @param mode the work mode
     * @return the handler, or null if not registered
     */
    public static TerrainToolHandler getHandler(WorkMode mode) {
        return handlerRegistry != null ? handlerRegistry.get(mode) : null;
    }



    public static PushdozerConfig getConfig() {
        return PushdozerConfig.getInstance();
    }

    public static void pushUndoAction(PlayerEntity player, UndoAction action) {
        UndoRedoService.getInstance().pushUndoAction(player, action);
    }

    /**
     * Debug method: Check player's undo stack status
     */
    public static void debugUndoStacks(PlayerEntity player) {
        UndoRedoService.getInstance().debugPlayerStacks(player);
    }

    /**
     * Get player's undo stack size
     */
    public static int getUndoStackSize(PlayerEntity player) {
        return UndoRedoService.getInstance().getUndoStackSize(player);
    }

    public static void saveConfig() {
        if (config != null) {
            config.save();
        }
    }
}