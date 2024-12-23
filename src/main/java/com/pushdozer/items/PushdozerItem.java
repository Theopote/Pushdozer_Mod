package com.pushdozer.items;

import java.util.ArrayList;
import java.util.List;

import com.pushdozer.PushdozerMod;
import com.pushdozer.config.PushdozerConfig;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public class PushdozerItem extends Item {
    public enum DisplayMode {
        NONE("None"),
        WIREFRAME("Wireframe"),
        SURFACE("Surface");

        private final String displayName;

        DisplayMode(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }

        public static DisplayMode fromDisplayName(String displayName) {
            for (DisplayMode mode : values()) {
                if (mode.getDisplayName().equals(displayName)) {
                    return mode;
                }
            }
            throw new IllegalArgumentException("No DisplayMode found for display name: " + displayName);
        }
    }

    private DisplayMode currentDisplayMode = DisplayMode.NONE;

    public PushdozerItem(Settings settings) {
        super(settings);
    }

    public DisplayMode getDisplayMode() {
        return currentDisplayMode;
    }

    public void setDisplayMode(DisplayMode mode) {
        this.currentDisplayMode = mode;
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity player, Hand hand) {
        ItemStack itemStack = player.getStackInHand(hand);
        PushdozerConfig config = PushdozerMod.getConfig();

        List<BlockPos> positions = new ArrayList<>();

        switch (config.getWorkMode()) {
            case DESTROY:
                positions = GeometryDestroyer.getBlocksToBreak(player, world, config);
                if (!world.isClient) {
                    PushdozerMod.breakBlocks(player, world, positions);
                }
                break;
            case PLACE:
                if (!world.isClient) {
                    PushdozerMod.handlePlacement(player, world);
                }
                break;
            case SMOOTH:
                if (!world.isClient) {
                    PushdozerMod.handleSmoothing(player, world);
                }
                break;
            default:
                break;
        }

        return TypedActionResult.success(itemStack);
    }
}