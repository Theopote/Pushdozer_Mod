package com.pushdozer.component;

import com.pushdozer.PushdozerMod;
import com.pushdozer.items.PushdozerItem;
import net.minecraft.component.ComponentType;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;
import java.util.function.UnaryOperator;

public class PushdozerComponents {

    /**
     * 自定义的DisplayMode数据组件类型。
     * 它存储一个DisplayMode枚举值。
     */
    public static final ComponentType<PushdozerItem.DisplayMode> DISPLAY_MODE = register(builder ->
        builder
            .codec(PushdozerItem.DisplayMode.CODEC) // 使用枚举自带的Codec进行序列化
            .packetCodec(PacketCodecs.STRING.xmap(
                str -> {
                    try {
                        return PushdozerItem.DisplayMode.valueOf(str);
                    } catch (IllegalArgumentException e) {
                        // 如果遇到未知的枚举值（如已删除的SURFACE），返回默认值NONE
                        PushdozerMod.LOGGER.warn("Unknown display mode '{}' in network packet, using NONE as fallback", str);
                        return PushdozerItem.DisplayMode.NONE;
                    }
                },
                PushdozerItem.DisplayMode::name
            )) // 定义网络同步方式
    );

    /**
     * 注册辅助方法
     */
    private static <T> ComponentType<T> register(UnaryOperator<ComponentType.Builder<T>> builderOperator) {
        return Registry.register(
                Registries.DATA_COMPONENT_TYPE,
                Identifier.of(PushdozerMod.MOD_ID, "display_mode"),
                builderOperator.apply(ComponentType.builder()).build()
        );
    }

    /**
     * 主注册方法，确保这个类被加载
     */
    public static void registerComponents() {
        PushdozerMod.LOGGER.info("Registering Pushdozer's custom data components...");
    }
} 