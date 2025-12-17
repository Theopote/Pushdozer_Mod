package com.pushdozer.client;

import com.pushdozer.config.PushdozerConfig;
import com.pushdozer.items.PushdozerItem;
import com.pushdozer.ui.screens.PushdozerConfigScreen;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;
import com.pushdozer.network.ClientNetworkHandler;

/**
 * KeyBindings 类负责注册和处理 Pushdozer 模组的按键绑定
 */
public class KeyBindings {

    // 定义按键类和各个按键的标识符
    /**
     * 1.21.11 起，按键分类使用 {@link KeyBinding.Category}（基于 Identifier），其本地化键为：
     * {@code key.category.<namespace>.<path>}
     */
    public static final KeyBinding.Category KEY_CATEGORY_PUSHDOZER =
            KeyBinding.Category.create(Identifier.of("pushdozer", "pushdozer"));
    public static final String KEY_TOGGLE_MODE = "key.pushdozer.toggle_mode";
    public static final String KEY_OPEN_CONFIG = "key.pushdozer.open_config";
    public static final String KEY_TOGGLE_DISPLAY_MODE = "key.pushdozer.toggle_display_mode";
    public static final String KEY_UNDO = "key.pushdozer.undo";
    public static final String KEY_REDO = "key.pushdozer.redo";
    public static final String KEY_INCREASE_DISTANCE = "key.pushdozer.increase_distance";
    public static final String KEY_DECREASE_DISTANCE = "key.pushdozer.decrease_distance";
    public static final String KEY_TOGGLE_SHAPE = "key.pushdozer.toggle_shape";

    // 定义按键绑定对象
    public static KeyBinding toggleModeKey;
    public static KeyBinding openConfigKey;
    public static KeyBinding toggleDisplayModeKey;
    public static KeyBinding undoKey;
    public static KeyBinding redoKey;
    public static KeyBinding increaseDistanceKey;
    public static KeyBinding decreaseDistanceKey;
    public static KeyBinding toggleShapeKey;


    /**
     * 注册所有按键绑定
     */
    public static void register() {
        // 注册切换模式按键
        toggleModeKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            KEY_TOGGLE_MODE,
            InputUtil.Type.KEYSYM,
            GLFW.GLFW_KEY_G, // 默认按键为 G
            KEY_CATEGORY_PUSHDOZER
        ));

        // 注册打开配置界面按键
        openConfigKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            KEY_OPEN_CONFIG,
            InputUtil.Type.KEYSYM,
            GLFW.GLFW_KEY_K, // 默认按键为 K
            KEY_CATEGORY_PUSHDOZER
        ));

        // 注册切换显示模式按键
        toggleDisplayModeKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            KEY_TOGGLE_DISPLAY_MODE,
            InputUtil.Type.KEYSYM,
            GLFW.GLFW_KEY_V, // 默认按键为 V
            KEY_CATEGORY_PUSHDOZER
        ));

        // 注册增加操作距离按键
        increaseDistanceKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            KEY_INCREASE_DISTANCE,
            InputUtil.Type.KEYSYM,
            GLFW.GLFW_KEY_UP, // 默认按键为上箭头
            KEY_CATEGORY_PUSHDOZER
        ));

        // 注册减少操作距离按键
        decreaseDistanceKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            KEY_DECREASE_DISTANCE,
            InputUtil.Type.KEYSYM,
            GLFW.GLFW_KEY_DOWN, // 默认按键为下箭头
            KEY_CATEGORY_PUSHDOZER
        ));

        // 注册切换形状按键
        toggleShapeKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            KEY_TOGGLE_SHAPE,
            InputUtil.Type.KEYSYM,
            GLFW.GLFW_KEY_U, // 默认按键为 U
            KEY_CATEGORY_PUSHDOZER
        ));

        // 注册撤销操作按键
        undoKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            KEY_UNDO,
            InputUtil.Type.KEYSYM,
            GLFW.GLFW_KEY_Z, // 默认按键为 Z
            KEY_CATEGORY_PUSHDOZER
        ));

        // 注册重做操作按键
        redoKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            KEY_REDO,
            InputUtil.Type.KEYSYM,
            GLFW.GLFW_KEY_Y, // 默认按键为 Y
            KEY_CATEGORY_PUSHDOZER
        ));

        // 注册按键输入事件
        registerKeyInputs();
    }

    /**
     * 注册按键输入事件处理
     */
    private static void registerKeyInputs() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.world != null && client.player != null) {
                // 处理切换模式按键
                if (toggleModeKey.wasPressed() && isHoldingPushdozer(client)) {
                    toggleWorkMode(client);
                }

                // 处理打开配置界面按键
                if (openConfigKey.wasPressed() && isHoldingPushdozer(client)) {
                    toggleConfigScreen(client);
                }

                // 处理切换显示模式按键
                if (toggleDisplayModeKey.wasPressed() && isHoldingPushdozer(client)) {
                    toggleDisplayMode(client);
                }

                // 处理切换形状按键
                if (toggleShapeKey.wasPressed() && isHoldingPushdozer(client)) {
                    toggleShape(client);
                }

                // 处理增加操作距离按键
                if (increaseDistanceKey.wasPressed() && isHoldingPushdozer(client)) {
                    adjustOperationDistance(client, true);
                }

                // 处理减少操作距离按键
                if (decreaseDistanceKey.wasPressed() && isHoldingPushdozer(client)) {
                    adjustOperationDistance(client, false);
                }

                // 处理撤销操作的检测逻辑
                if (undoKey.wasPressed() && isControlDown(client) && isHoldingPushdozer(client)) {
                    ClientNetworkHandler.sendUndoRedoRequest(true);
                }

                // 处理重做操作的检测逻辑
                if (redoKey.wasPressed() && isControlDown(client) && isHoldingPushdozer(client)) {
                    ClientNetworkHandler.sendUndoRedoRequest(false);
                }
            }
        });
    }

    private static boolean isControlDown(MinecraftClient client) {
        if (client == null || client.getWindow() == null) return false;
        return InputUtil.isKeyPressed(client.getWindow(), GLFW.GLFW_KEY_LEFT_CONTROL)
                || InputUtil.isKeyPressed(client.getWindow(), GLFW.GLFW_KEY_RIGHT_CONTROL);
    }

    /**
     * 检查玩家是否手持 Pushdozer 工具
     * @param client Minecraft 客户端实例
     * @return 如果玩手持 Pushdozer 工具返回 true
     */
    private static boolean isHoldingPushdozer(MinecraftClient client) {
        if (client.player == null) return false;
        ItemStack mainHand = client.player.getMainHandStack();
        ItemStack offHand = client.player.getOffHandStack();
        return mainHand.getItem() instanceof PushdozerItem ||
               offHand.getItem() instanceof PushdozerItem;
    }

    /**
     * 切换配置界面显示状态
     * @param client Minecraft 客户端实例
     */
    private static void toggleConfigScreen(MinecraftClient client) {
        if (client.currentScreen instanceof PushdozerConfigScreen) {
            client.setScreen(null); // 关闭配置界面
        } else {
            client.setScreen(new PushdozerConfigScreen(PushdozerConfig.getInstance()));
        }
    }

    /**
     * 切换显示模式
     * @param client Minecraft 客户端实例
     */
    private static void toggleDisplayMode(MinecraftClient client) {
        if (client.player == null) return;
        
        // 更新配置级别的显示模式
        PushdozerConfig config = PushdozerConfig.getInstance();
        // 确保显示模式不为null
        if (config.getDisplayMode() == null) {
            config.setDisplayMode(PushdozerConfig.DisplayMode.WIREFRAME);
        }
        PushdozerConfig.DisplayMode[] modes = PushdozerConfig.DisplayMode.values();
        int nextIndex = (config.getDisplayMode().ordinal() + 1) % modes.length;
        PushdozerConfig.DisplayMode newConfigMode = modes[nextIndex];
        config.setDisplayMode(newConfigMode);
        config.save();
        
        // 同时更新物品级别的显示模式，确保同步
        ItemStack mainHand = client.player.getMainHandStack();
        ItemStack offHand = client.player.getOffHandStack();
        
        PushdozerItem pushdozerItem = null;
        ItemStack targetStack = null;
        
        if (mainHand.getItem() instanceof PushdozerItem) {
            pushdozerItem = (PushdozerItem) mainHand.getItem();
            targetStack = mainHand;
        } else if (offHand.getItem() instanceof PushdozerItem) {
            pushdozerItem = (PushdozerItem) offHand.getItem();
            targetStack = offHand;
        }
        
        if (pushdozerItem != null) {
            // 将配置级别的显示模式同步到物品级别
            PushdozerItem.DisplayMode newItemMode = PushdozerItem.DisplayMode.values()[newConfigMode.ordinal()];
            pushdozerItem.setDisplayMode(targetStack, newItemMode);
            
            // 使用配置级别的显示模式来显示提示消息
            client.player.sendMessage(
                Text.translatable("pushdozer.message.display_mode_switch", newConfigMode.getDisplayText().getString()),
                true
            );
        } else {
            // 如果没有手持推土机，只显示配置级别的提示
            client.player.sendMessage(
                Text.translatable("pushdozer.message.display_mode_switch", newConfigMode.getDisplayText().getString()),
                true
            );
        }
    }

    private static void toggleWorkMode(MinecraftClient client) {
        if (client.player == null) return;

        if (!isHoldingPushdozer(client)) {
            client.player.sendMessage(Text.translatable("pushdozer.message.hold_tool"), true);
            return;
        }

        PushdozerConfig config = PushdozerConfig.getInstance();
        // 仅循环可见工作模式（隐藏旧平滑三项）
        java.util.List<PushdozerConfig.WorkMode> visibleModes = new java.util.ArrayList<>();
        for (PushdozerConfig.WorkMode m : PushdozerConfig.WorkMode.values()) {
            switch (m) {
                case SMOOTH_RAISE, SMOOTH_LOWER, ADAPTIVE_SMOOTH -> {}
                default -> visibleModes.add(m);
            }
        }
        if (!visibleModes.contains(PushdozerConfig.WorkMode.SMOOTH)) {
            visibleModes.add(PushdozerConfig.WorkMode.SMOOTH);
        }
        int currentIdx = visibleModes.indexOf(config.getWorkMode());
        int nextIndex = (currentIdx + 1 + visibleModes.size()) % visibleModes.size();
        PushdozerConfig.WorkMode newMode = visibleModes.get(nextIndex);
        config.setWorkMode(newMode);
        PushdozerConfig.getInstance().save();
        
        // 每个玩家的配置是独立的，不需要同步到服务器

        Text modeText = Text.translatable("pushdozer.mode." + newMode.name().toLowerCase());
        client.player.sendMessage(
            Text.translatable("pushdozer.message.working_mode_switch", modeText),
            true
        );
    }

    /**
     * 切换笔刷形状 - 循环切换所有6种几何体形状
     * @param client Minecraft 客户端实例
     */
    private static void toggleShape(MinecraftClient client) {
        PushdozerConfig config = PushdozerConfig.getInstance();
        PushdozerConfig.GeometryType[] geometryTypes = PushdozerConfig.GeometryType.values();
        int currentIndex = config.getGeometryType().ordinal();
        int nextIndex = (currentIndex + 1) % geometryTypes.length;
        PushdozerConfig.GeometryType newGeometryType = geometryTypes[nextIndex];
        
        config.setGeometryType(newGeometryType);
        config.save();

        // 显示切换消息
        Text shapeText = newGeometryType.getDisplayText();
        if (client.player != null) {
            client.player.sendMessage(
                Text.translatable("pushdozer.message.shape_switch", shapeText),
                true
            );
        }
    }

    /**
     * 调整操作距离
     * @param client Minecraft 客户端实例
     * @param increase 如果为 true 则增加距离，否则减少距离
     */
    private static void adjustOperationDistance(MinecraftClient client, boolean increase) {
        PushdozerConfig config = PushdozerConfig.getInstance();
        int currentDistance = config.getMaxOperationDistance();
        int newDistance;

        if (increase) {
            newDistance = Math.min(currentDistance + 1, PushdozerConfig.MAX_OPERATION_DISTANCE);
        } else {
            newDistance = Math.max(currentDistance - 1, 1);
        }

        if (newDistance != currentDistance) {
            config.setMaxOperationDistance(newDistance);
            config.save();
            if (client.player != null) {
                client.player.sendMessage(
                    Text.translatable("pushdozer.message.maximum_distance", newDistance),
                    true
                );
            }
        }
    }
}