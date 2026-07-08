package com.pushdozer;

import com.pushdozer.config.PushdozerConfig;
import com.pushdozer.items.ModItems;
import com.pushdozer.items.handlers.AdaptiveSmoothHandler;
import com.pushdozer.items.handlers.BatchPlantHandler;
import com.pushdozer.items.handlers.BoneMealHandler;
import com.pushdozer.items.handlers.ExcavationHandler;
import com.pushdozer.items.handlers.PlacementHandler;
import com.pushdozer.items.handlers.ShorelineProcessHandler;
import com.pushdozer.items.handlers.SmoothLowerHandler;
import com.pushdozer.items.handlers.SmoothRaiseHandler;
import com.pushdozer.items.handlers.SmoothingHandler;
import com.pushdozer.items.handlers.SurfaceConvertHandler;
import com.pushdozer.items.handlers.SurfaceRoughenHandler;
import com.pushdozer.operations.UndoAction;
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
    
    // 所有处理器的静态实例，避免频繁创建对象
    public static PlacementHandler placementHandler;
    public static SmoothingHandler smoothingHandler;
    public static ExcavationHandler excavationHandler;
    public static SmoothRaiseHandler smoothRaiseHandler;
    public static SmoothLowerHandler smoothLowerHandler;
    public static SurfaceRoughenHandler surfaceRoughenHandler;
    public static SurfaceConvertHandler surfaceConvertHandler;
    public static BoneMealHandler boneMealHandler;
    public static BatchPlantHandler batchPlantHandler;
    public static ShorelineProcessHandler shorelineProcessHandler;
    public static AdaptiveSmoothHandler adaptiveSmoothHandler;

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

        placementHandler = new PlacementHandler();
        smoothingHandler = new SmoothingHandler();
        excavationHandler = new ExcavationHandler();
        smoothRaiseHandler = new SmoothRaiseHandler();
        smoothLowerHandler = new SmoothLowerHandler();
        surfaceRoughenHandler = new SurfaceRoughenHandler();
        surfaceConvertHandler = new SurfaceConvertHandler();
        boneMealHandler = new BoneMealHandler();
        batchPlantHandler = new BatchPlantHandler();
        shorelineProcessHandler = new ShorelineProcessHandler();
        adaptiveSmoothHandler = new AdaptiveSmoothHandler();

        NetworkManager.registerNetworking();

        LOGGER.info("Pushdozer mod has been initialized！");
    }



    public static PushdozerConfig getConfig() {
        return PushdozerConfig.getInstance();
    }

    public static void pushUndoAction(PlayerEntity player, UndoAction action) {
        UndoRedoService.getInstance().pushUndoAction(player, action);
    }

    /**
     * 调试方法：检查玩家的撤销栈状态
     */
    public static void debugUndoStacks(PlayerEntity player) {
        UndoRedoService.getInstance().debugPlayerStacks(player);
    }

    /**
     * 获取玩家的撤销栈大小
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