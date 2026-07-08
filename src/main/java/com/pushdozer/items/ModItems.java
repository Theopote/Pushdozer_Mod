package com.pushdozer.items;

import com.pushdozer.PushdozerMod;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;
import java.util.function.Function;

public class ModItems {
    
    /**
     * 注册物品的辅助方法
     * 使用 Items.register 方法，确保 RegistryKey 正确设置
     */
    private static Item register(Function<Item.Settings, Item> factory, Item.Settings settings) {
        final RegistryKey<Item> registryKey = RegistryKey.of(RegistryKeys.ITEM, Identifier.of(PushdozerMod.MOD_ID, "pushdozer"));
        return Items.register(registryKey, factory, settings);
    }
    
    // 创建物品实例 - 使用新的注册方法
    public static final Item PUSHDOZER_ITEM = register(PushdozerItem::new, new Item.Settings().maxCount(1));
    
    /**
     * 注册所有物品
     */
    public static void registerItems() {
        PushdozerMod.LOGGER.info("Registering Pushdozer items...");
        PushdozerMod.LOGGER.info("Pushdozer item registered successfully.");
        PushdozerMod.LOGGER.info("Registered item: pushdozer:pushdozer");
        PushdozerMod.LOGGER.info("Item ID: pushdozer:pushdozer");
    }
} 