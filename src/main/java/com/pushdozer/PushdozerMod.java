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

        try {
            // 注册物品
            ModItems.registerItems();
            
            // 验证物品注册
            PushdozerMod.LOGGER.info("Successfully registered item: {}", Registries.ITEM.getId(ModItems.PUSHDOZER_ITEM));
            
            // 首先初始化配置
            config = PushdozerConfig.getInstance();

            // 将 Pushdozer 添加到工具与实用物品栏
            ItemGroupEvents.modifyEntriesEvent(ItemGroups.TOOLS).register(content -> {
                content.add(ModItems.PUSHDOZER_ITEM);
                PushdozerMod.LOGGER.info("Added Pushdozer item to tools item group");
            });
            
            // 注册 ScreenHandlerType
            CONFIG_SCREEN_HANDLER = Registry.register(
                    Registries.SCREEN_HANDLER,
                    Identifier.of(MOD_ID, "config_screen"),
                    new ScreenHandlerType<>((syncId, inventory) ->
                            new PushdozerConfigScreenHandler(syncId, inventory, null), FeatureFlags.VANILLA_FEATURES)
            );

            // 初始化所有处理器实例，避免频繁创建对象
            placementHandler = new PlacementHandler(config);
            smoothingHandler = new SmoothingHandler(config);
            excavationHandler = new ExcavationHandler(config);
            smoothRaiseHandler = new SmoothRaiseHandler(config);
            smoothLowerHandler = new SmoothLowerHandler(config);
            surfaceRoughenHandler = new SurfaceRoughenHandler(config);
            surfaceConvertHandler = new SurfaceConvertHandler(config);
            boneMealHandler = new BoneMealHandler(config);
            batchPlantHandler = new BatchPlantHandler(config);
            shorelineProcessHandler = new ShorelineProcessHandler(config);
            adaptiveSmoothHandler = new AdaptiveSmoothHandler(config);

            // 注册网络系统
            NetworkManager.registerNetworking();

            LOGGER.info("Pushdozer mod has been initialized！");
        } catch (Exception e) {
            LOGGER.error("Failed to initialize Pushdozer mod", e);
            throw new RuntimeException("Failed to initialize Pushdozer mod", e);
        }
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