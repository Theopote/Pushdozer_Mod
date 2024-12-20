package com.pushdozer.client;

import com.pushdozer.config.PushdozerConfig;
import com.pushdozer.items.PushdozerItem;
import com.pushdozer.ui.screens.PushdozerConfigScreen;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.pushdozer.network.UndoRedoPayload;

/**
 * KeyBindings 类负责注册和处理 Pushdozer 模组的按键绑定
 */
public class KeyBindings {
    private static final Logger LOGGER = LoggerFactory.getLogger(KeyBindings.class);

    // 定义按键类和各个按键的标识符
    public static final String KEY_CATEGORY_PUSHDOZER = "key.category.pushdozer";
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
            GLFW.GLFW_KEY_DOWN, // 默认按���为下箭头
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
        ) {
            @Override
            public boolean matchesKey(int keyCode, int scanCode) {
                return super.matchesKey(keyCode, scanCode) && Screen.hasControlDown();
            }
        });

        // 注册重做操作按键
        redoKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            KEY_REDO,
            InputUtil.Type.KEYSYM,
            GLFW.GLFW_KEY_Y, // 默认按键为 Y
            KEY_CATEGORY_PUSHDOZER
        ) {
            @Override
            public boolean matchesKey(int keyCode, int scanCode) {
                return super.matchesKey(keyCode, scanCode) && Screen.hasControlDown();
            }
        });

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
                if (undoKey.wasPressed() && Screen.hasControlDown() && isHoldingPushdozer(client)) {
                    ClientPlayNetworking.send(new UndoRedoPayload(true));
                }

                // 处理重做操作的检测逻辑
                if (redoKey.wasPressed() && Screen.hasControlDown() && isHoldingPushdozer(client)) {
                    ClientPlayNetworking.send(new UndoRedoPayload(false));
                }
            }
        });
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
        PushdozerConfig config = PushdozerConfig.getInstance();
        PushdozerConfig.DisplayMode[] modes = PushdozerConfig.DisplayMode.values();
        int nextIndex = (config.getDisplayMode().ordinal() + 1) % modes.length;
        PushdozerConfig.DisplayMode newMode = modes[nextIndex];
        config.setDisplayMode(newMode);
        PushdozerConfig.getInstance().save();
        client.player.sendMessage(
            Text.translatable("pushdozer.message.display_mode_switch", newMode.getDisplayText()),
            true
        );
    }

    private static void toggleWorkMode(MinecraftClient client) {
        if (client.player == null) return;

        if (!isHoldingPushdozer(client)) {
            client.player.sendMessage(Text.translatable("pushdozer.message.hold_tool"), true);
            return;
        }

        PushdozerConfig config = PushdozerConfig.getInstance();
        PushdozerConfig.WorkMode[] modes = PushdozerConfig.WorkMode.values();
        int nextIndex = (config.getWorkMode().ordinal() + 1) % modes.length;
        PushdozerConfig.WorkMode newMode = modes[nextIndex];
        config.setWorkMode(newMode);
        PushdozerConfig.getInstance().save();

        Text modeText = Text.translatable("pushdozer.mode." + newMode.name().toLowerCase());
        client.player.sendMessage(
            Text.translatable("pushdozer.message.working_mode_switch", modeText),
            true
        );
    }

    /**
     * 切换笔刷形状
     * @param client Minecraft 客户端实例
     */
    private static void toggleShape(MinecraftClient client) {
        PushdozerConfig config = PushdozerConfig.getInstance();
        String currentShape = config.getShape();
        String newShape = currentShape.equals("Box") ? "Sphere" : "Box";

        config.setShape(newShape);
        config.save();

        // 显示切换消息
        Text shapeText = Text.translatable("pushdozer.shape." + newShape.toLowerCase());
        client.player.sendMessage(
            Text.translatable("pushdozer.message.shape_switch", shapeText),
            true
        );
    }

    /**
     * 调整操作距离
     * @param client Minecraft 客户端实例
     * @param increase 如果为 true 则增加距离，否则减少距离
     */
    private static void adjustOperationDistance(MinecraftClient client, boolean increase) {
        PushdozerConfig config = PushdozerConfig.getInstance();
        int currentDistance = config.getMaxOperationDistance();
        int newDistance = currentDistance;

        if (increase) {
            newDistance = Math.min(currentDistance + 1, PushdozerConfig.MAX_OPERATION_DISTANCE);
        } else {
            newDistance = Math.max(currentDistance - 1, 1);
        }

        if (newDistance != currentDistance) {
            config.setMaxOperationDistance(newDistance);
            config.save();
            client.player.sendMessage(
                Text.translatable("pushdozer.message.maximum_distance", newDistance),
                true
            );
        }
    }
}