package com.pushdozer.ui;

import com.pushdozer.PushdozerMod;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.network.PacketByteBuf;

/**
 * PushdozerConfigScreenHandler 类
 * 这个类负责处理推土机配置界面的后端逻辑
 */
public class PushdozerConfigScreenHandler extends ScreenHandler {

    // 修改构造函数以匹配 Factory 接口
    public PushdozerConfigScreenHandler(int syncId, PlayerInventory inventory, PacketByteBuf buf) {
        super(PushdozerMod.CONFIG_SCREEN_HANDLER, syncId);
        // 初始化屏幕元素，例如槽位、按钮等
        // 如果需要玩家信息，可以使用 inventory.player
        // 如果 buf 不为 null，可以从中读取额外数据
    }

    @Override
    public boolean canUse(PlayerEntity player) {
        return true;
    }

    @Override
    public ItemStack quickMove(PlayerEntity player, int index) {
        // 由于这是一个配置界面，我们不需要处理物品移动
        return ItemStack.EMPTY;
    }
}