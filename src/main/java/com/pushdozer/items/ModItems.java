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
     * Helper method to register items
     * Uses Items.register method to ensure RegistryKey is set correctly
     */
    private static Item register(Function<Item.Settings, Item> factory, Item.Settings settings) {
        final RegistryKey<Item> registryKey = RegistryKey.of(RegistryKeys.ITEM, Identifier.of(PushdozerMod.MOD_ID, "pushdozer"));
        return Items.register(registryKey, factory, settings);
    }

    // Create item instance - uses new registration method
    public static final Item PUSHDOZER_ITEM = register(PushdozerItem::new, new Item.Settings().maxCount(1));

    /**
     * Register all items
     */
    public static void registerItems() {
        PushdozerMod.LOGGER.info("Registering Pushdozer items...");
        PushdozerMod.LOGGER.info("Pushdozer item registered successfully.");
        PushdozerMod.LOGGER.info("Registered item: pushdozer:pushdozer");
        PushdozerMod.LOGGER.info("Item ID: pushdozer:pushdozer");
    }
} 