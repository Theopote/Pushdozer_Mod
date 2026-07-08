package com.pushdozer;

import net.minecraft.Bootstrap;
import net.minecraft.SharedConstants;
import org.junit.jupiter.api.BeforeAll;

/**
 * 初始化 Minecraft 引导环境，使注册表相关类（BlockState、Blocks 等）可在单元测试中使用。
 */
public abstract class PushdozerTestBase {
    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.createGameVersion();
        Bootstrap.initialize();
    }
}
