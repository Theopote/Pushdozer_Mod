package com.pushdozer.config;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.pushdozer.PushdozerMod;

import com.pushdozer.items.PushdozerItem;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.block.Block;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

public class PushdozerConfig {
    private static final String CONFIG_FILE_NAME = "pushdozer_config.json";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    public static final int MAX_OPERATION_DISTANCE = 99;

    public enum WorkMode {
        DESTROY("pushdozer.mode.destroy"),
        PLACE("pushdozer.mode.place"),
        SMOOTH("pushdozer.mode.smooth");

        private final String translationKey;

        WorkMode(String translationKey) {
            this.translationKey = translationKey;
        }

        public Text getDisplayText() {
            return Text.translatable(translationKey);
        }

        public static WorkMode fromDisplayName(String displayName) {
            for (WorkMode mode : values()) {
                if (mode.getDisplayText().getString().equals(displayName)) {
                    return mode;
                }
            }
            throw new IllegalArgumentException("No WorkMode found for display name: " + displayName);
        }
    }

    public enum DisplayMode {
        NONE("pushdozer.display_mode.none"),
        WIREFRAME("pushdozer.display_mode.wireframe"),
        SURFACE("pushdozer.display_mode.surface");

        private final String translationKey;

        DisplayMode(String translationKey) {
            this.translationKey = translationKey;
        }

        public Text getDisplayText() {
            return Text.translatable(translationKey);
        }

        public static DisplayMode fromDisplayName(String displayName) {
            for (DisplayMode mode : values()) {
                if (mode.getDisplayText().getString().equals(displayName)) {
                    return mode;
                }
            }
            throw new IllegalArgumentException("No DisplayMode found for display name: " + displayName);
        }
    }

    private WorkMode workMode = WorkMode.DESTROY;
    private DisplayMode displayMode = DisplayMode.WIREFRAME; // 确保有一个默认值
    private int maxOperationDistance = 20; // 默认值
    private Set<String> breakableBlockIds = new HashSet<>();
    private List<String> ignoredBlockIds = new ArrayList<>(List.of(
        "minecraft:grass",
        "minecraft:fern",
        "minecraft:dead_bush",
        "minecraft:sapling",
        "minecraft:seagrass",
        "minecraft:tall_seagrass",
        "minecraft:tall_grass"
    ));
    private String placeableBlockId = "minecraft:stone";
    private String shape = "Box";
    private int radius = 5;
    private int length = 5;
    private int width = 5;
    private int height = 5;
    private Set<String> placeableBlockIds = new HashSet<>();
    private boolean heightLocked = false;
    private int lockedHeight = 0;

    public PushdozerConfig() {
        placeableBlockIds.add("minecraft:stone");
        placeableBlockIds.add("minecraft:dirt");
        // 确保在构造函数中设置默认值
        if (displayMode == null) {
            displayMode = DisplayMode.WIREFRAME;
        }
    }

    // Getter 和 Setter 方法

    public WorkMode getWorkMode() {
        return workMode;
    }

    public void setWorkMode(WorkMode workMode) {
        this.workMode = workMode;
        notifyListeners();
    }

    public DisplayMode getDisplayMode() {
        System.out.println("Current display mode: " + displayMode);
        return displayMode;
    }

    public void setDisplayMode(DisplayMode displayMode) {
        this.displayMode = displayMode;
        save(); // 确保在更改后保存配置
        notifyListeners();
    }

    public int getMaxOperationDistance() {
        return maxOperationDistance;
    }

    public void setMaxOperationDistance(int distance) {
        this.maxOperationDistance = distance;
        notifyListeners();
    }

    public Set<String> getBreakableBlockIds() {
        return new HashSet<>(breakableBlockIds);
    }

    public void setBreakableBlockIds(Set<String> blockIds) {
        this.breakableBlockIds = new HashSet<>(blockIds);
        notifyListeners();
    }

    public boolean isBlockBreakable(Block block) {
        String blockId = Registries.BLOCK.getId(block).toString();
        return breakableBlockIds.contains(blockId);
    }

    public String getPlaceableBlockId() {
        return placeableBlockId;
    }

    public void setPlaceableBlockId(String id) {
        this.placeableBlockId = id;
        notifyListeners();
    }

    public boolean isBlockPlaceable(String blockId) {
        return placeableBlockIds.contains(blockId);
    }

    public String getShape() {
        return shape;
    }

    public void setShape(String shape) {
        this.shape = shape;
        notifyListeners();
    }

    public int getRadius() {
        return radius;
    }

    public void setRadius(int radius) {
        this.radius = radius;
        notifyListeners();
    }

    public int getLength() {
        return length;
    }

    public void setLength(int length) {
        this.length = length;
        notifyListeners();
    }

    public int getWidth() {
        return width;
    }

    public void setWidth(int width) {
        this.width = width;
        notifyListeners();
    }

    public int getHeight() {
        return height;
    }

    public void setHeight(int height) {
        this.height = height;
        notifyListeners();
    }

    public boolean isHeightLocked() {
        return heightLocked;
    }

    public void setHeightLocked(boolean locked) {
        this.heightLocked = locked;
    }

    public int getLockedHeight() {
        return lockedHeight;
    }

    public void setLockedHeight(int height) {
        this.lockedHeight = height;
    }

    private void notifyListeners() {
        // 实现配置变更通知逻辑
    }

    public void save() {
        Path configDir = FabricLoader.getInstance().getConfigDir();
        File configFile = configDir.resolve(CONFIG_FILE_NAME).toFile();

        try (FileWriter writer = new FileWriter(configFile)) {
            GSON.toJson(this, writer);
            PushdozerMod.LOGGER.info("Pushdozer configuration is saved");
        } catch (IOException e) {
            PushdozerMod.LOGGER.error("An error occurred while saving the Pushdozer configuration", e);
        }
    }

    public static PushdozerConfig load() {
        Path configDir = FabricLoader.getInstance().getConfigDir();
        File configFile = configDir.resolve(CONFIG_FILE_NAME).toFile();

        if (configFile.exists()) {
            try (FileReader reader = new FileReader(configFile)) {
                PushdozerConfig config = GSON.fromJson(reader, PushdozerConfig.class);
                PushdozerMod.LOGGER.info("configuration has been loaded");
                if (config.getWorkMode() == null) {
                    config.setWorkMode(WorkMode.DESTROY);
                }
                if (config.getDisplayMode() == null) {
                    config.setDisplayMode(DisplayMode.WIREFRAME);
                }
                return config;
            } catch (IOException e) {
                PushdozerMod.LOGGER.error("An error occurred while loading the Pushdozer configuration", e);
            }
        }

        PushdozerMod.LOGGER.info("Pushdozer configuration file not found, default configuration used");
        return new PushdozerConfig();
    }

    public List<String> getIgnoredBlockIds() {
        return ignoredBlockIds;
    }

    public void setIgnoredBlockIds(List<String> ignoredBlockIds) {
        this.ignoredBlockIds = ignoredBlockIds;
        notifyListeners();
    }

    public List<Block> getBreakableBlocks() {
        return breakableBlockIds.stream()
            .map(Identifier::tryParse)
            .filter(id -> id != null)
            .map(Registries.BLOCK::get)
            .collect(Collectors.toList());
    }

    public void setBreakableBlocks(List<Block> blocks) {
        this.breakableBlockIds = blocks.stream()
            .map(block -> Registries.BLOCK.getId(block).toString())
            .collect(Collectors.toSet());
        notifyListeners();
    }

    // 添加 writeToPacket 方法
    public void writeToPacket(PacketByteBuf buf) {
        buf.writeString(shape);
        buf.writeInt(maxOperationDistance);
        buf.writeEnumConstant(workMode);
        buf.writeEnumConstant(displayMode);
        buf.writeInt(radius);
        buf.writeInt(length);
        buf.writeInt(width);
        buf.writeInt(height);
        buf.writeString(placeableBlockId);
        
        // 写入 breakableBlockIds
        buf.writeVarInt(breakableBlockIds.size());
        for (String blockId : breakableBlockIds) {
            buf.writeString(blockId);
        }
        
        // 写入 ignoredBlockIds
        buf.writeVarInt(ignoredBlockIds.size());
        for (String blockId : ignoredBlockIds) {
            buf.writeString(blockId);
        }
        
        // 写入 placeableBlockIds
        buf.writeVarInt(placeableBlockIds.size());
        for (String blockId : placeableBlockIds) {
            buf.writeString(blockId);
        }
    }

    // 添加 fromPacket 方法
    public static PushdozerConfig fromPacket(PacketByteBuf buf) {
        PushdozerConfig config = new PushdozerConfig();
        config.shape = buf.readString();
        config.maxOperationDistance = buf.readInt();
        config.workMode = buf.readEnumConstant(WorkMode.class);
        config.displayMode = buf.readEnumConstant(DisplayMode.class);
        config.radius = buf.readInt();
        config.length = buf.readInt();
        config.width = buf.readInt();
        config.height = buf.readInt();
        config.placeableBlockId = buf.readString();
        
        // 读取 breakableBlockIds
        int breakableSize = buf.readVarInt();
        config.breakableBlockIds = new HashSet<>();
        for (int i = 0; i < breakableSize; i++) {
            config.breakableBlockIds.add(buf.readString());
        }
        
        // 读取 ignoredBlockIds
        int ignoredSize = buf.readVarInt();
        config.ignoredBlockIds = new ArrayList<>();
        for (int i = 0; i < ignoredSize; i++) {
            config.ignoredBlockIds.add(buf.readString());
        }
        
        // 读取 placeableBlockIds
        int placeableSize = buf.readVarInt();
        config.placeableBlockIds = new HashSet<>();
        for (int i = 0; i < placeableSize; i++) {
            config.placeableBlockIds.add(buf.readString());
        }
        
        return config;
    }

    private static PushdozerConfig instance = null;

    // 添加获取单例实例的方法
    public static PushdozerConfig getInstance() {
        if (instance == null) {
            instance = load();
        }
        return instance;
    }

    // 添加更新实例的方法
    public static void updateInstance(PushdozerConfig newConfig) {
        instance = newConfig;
        // 可能需要在这里触发一些UI更新
    }

    // 从PushdozerMod移动的方法
    public void toggleMode(PlayerEntity player) {
        if (isHoldingPushdozer(player)) {
            WorkMode[] modes = WorkMode.values();
            int nextIndex = (this.workMode.ordinal() + 1) % modes.length;
            WorkMode newMode = modes[nextIndex];
            this.setWorkMode(newMode);
            this.save();
            player.sendMessage(Text.literal("working Mode changed to: " + newMode.getDisplayText()), false);
        } else {
            player.sendMessage(Text.literal("Please hold the pushdozer tool to switch modes"), false);
        }
    }

    private boolean isHoldingPushdozer(PlayerEntity player) {
        return player.getMainHandStack().getItem() instanceof PushdozerItem ||
               player.getOffHandStack().getItem() instanceof PushdozerItem;
    }

    public boolean validateConfig() {
        // 实现配置验证逻辑
        return true;
    }

    // 添加玩家特定配置的支持
    private static final Map<UUID, PushdozerConfig> playerConfigs = new HashMap<>();

    public static void updatePlayerConfig(ServerPlayerEntity player, PushdozerConfig newConfig) {
        playerConfigs.put(player.getUuid(), newConfig);
    }

    public static PushdozerConfig getPlayerConfig(ServerPlayerEntity player) {
        return playerConfigs.getOrDefault(player.getUuid(), getInstance());
    }

    public void clearPlayerConfigs() {
        playerConfigs.clear();
    }
}