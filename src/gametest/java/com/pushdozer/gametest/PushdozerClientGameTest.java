package com.pushdozer.gametest;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;

/**
 * 客户端 Game Test 骨架：启动单人世界并截图，便于后续扩展 UI/预览类回归。
 * <p>
 * CI 默认不跑客户端 Game Test；本地可执行 {@code ./gradlew runClientGameTest}（若已配置）。
 */
@SuppressWarnings("UnstableApiUsage")
public class PushdozerClientGameTest implements FabricClientGameTest {

    @Override
    public void runTest(ClientGameTestContext context) {
        try (TestSingleplayerContext singleplayer = context.worldBuilder().create()) {
            singleplayer.getClientWorld().waitForChunksRender();
            context.takeScreenshot("pushdozer-singleplayer-smoke");
        }
    }
}
