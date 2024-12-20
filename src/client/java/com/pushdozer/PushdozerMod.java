package com.pushdozer;

import com.pushdozer.config.PushdozerConfig;
import com.pushdozer.items.PlacementHandler;
import com.pushdozer.items.PushdozerItem;
import com.pushdozer.operations.BlockOperation;
import com.pushdozer.operations.UndoAction;
import com.pushdozer.render.ShapeRenderer;
import com.pushdozer.shapes.GeometryShape;
import com.pushdozer.ui.PushdozerConfigScreenHandler;
import com.pushdozer.util.ShapeUtil;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroups;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.resource.featuretoggle.FeatureFlags;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import com.pushdozer.items.SmoothingHandler;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.PacketByteBuf;
import com.pushdozer.validation.ActionValidator;
import com.pushdozer.services.UndoRedoService;
import com.pushdozer.network.UndoRedoPayload;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;


public class PushdozerMod implements ModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger("pushdozer");
    public static final String MOD_ID = "pushdozer";
    public static final Item PUSHDOZER_ITEM = new PushdozerItem(new Item.Settings().maxCount(1));

    public static ScreenHandlerType<PushdozerConfigScreenHandler> CONFIG_SCREEN_HANDLER;

    private static PushdozerConfig config;
    public static PlacementHandler placementHandler;
    private static BlockOperation currentOperation;
    public static SmoothingHandler smoothingHandler;

    public static final Identifier PUSHDOZER_ACTION_PACKET = Identifier.of(MOD_ID, "pushdozer_action");
    public static final Identifier UNDO_PACKET_ID = Identifier.of("pushdozer", "undo");
    public static final Identifier REDO_PACKET_ID = Identifier.of("pushdozer", "redo");

    @Override
    public void onInitialize() {
        Registry.register(Registries.ITEM, Identifier.of(MOD_ID, "pushdozer"), PUSHDOZER_ITEM);
        
        // 将 Pushdozer 添加到工具与实用物品栏
        ItemGroupEvents.modifyEntriesEvent(ItemGroups.TOOLS).register(content -> {
            content.add(PUSHDOZER_ITEM);
        });

        // 注册 ScreenHandlerType
        CONFIG_SCREEN_HANDLER = Registry.register(
            Registries.SCREEN_HANDLER,
            Identifier.of(MOD_ID, "config_screen"),
            new ScreenHandlerType<>((syncId, inventory) -> new PushdozerConfigScreenHandler(syncId, inventory, null), FeatureFlags.VANILLA_FEATURES)
        );

        // 修改配置初始化
        config = PushdozerConfig.getInstance();
        placementHandler = new PlacementHandler(config);
        smoothingHandler = new SmoothingHandler(config);
        LOGGER.info("Pushdozer mod has been initialized！");
        
        // 注册世界渲染事件
        WorldRenderEvents.AFTER_ENTITIES.register((context) -> {
            MinecraftClient client = MinecraftClient.getInstance();
            PlayerEntity player = client.player;
            if (ShapeRenderer.shouldRenderShape() && player != null) {
                // 每次渲染前重新加载配置
                config = PushdozerConfig.getInstance();
                BlockPos playerPos = player.getBlockPos();
                GeometryShape shape = ShapeUtil.createShape(player, getConfig(), playerPos);
                if (shape != null) {
                    ShapeRenderer.renderShape(shape, getConfig());
                }
            }
        });

        // 注册Payload类型
        PayloadTypeRegistry.playC2S().register(
            UndoRedoPayload.ID,
            UndoRedoPayload.CODEC
        );

        // 注册服务器端网络处理
        ServerPlayNetworking.registerGlobalReceiver(
            UndoRedoPayload.ID,
            (payload, context) -> {
                context.server().execute(() -> {
                    if (payload.isUndo()) {
                        LOGGER.info("Server receives the revoke request");
                        UndoRedoService.getInstance().undoLastAction(context.player(), context.player().getWorld());
                    } else {
                        LOGGER.info("Server receives the redo request");
                        UndoRedoService.getInstance().redoLastAction(context.player(), context.player().getWorld());
                    }
                });
            }
        );
    }

    private List<BlockPos> readBlockPosList(PacketByteBuf buf) {
        int size = buf.readVarInt();
        List<BlockPos> positions = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            positions.add(buf.readBlockPos());
        }
        return positions;
    }

    // 添加 placeBlocks 方法
    public static void placeBlocks(PlayerEntity player, World world) {
        if (placementHandler != null) {
            placementHandler.handlePlacement(player, world);
        } else {
            LOGGER.error("PlacementHandler uninitialized！");
        }
    }

    // 修改 breakBlocks 方法，确保记录撤销操作
    public static void breakBlocks(PlayerEntity player, World world, List<BlockPos> positions) {
        if (player instanceof ServerPlayerEntity serverPlayer) {
            if (!ActionValidator.validateBreakAction(serverPlayer, positions)) {
                return;
            }
        }
        
        // 获取配置
        PushdozerConfig config = getConfig();
        
        // 检查标高锁定
        if (config.isHeightLocked()) {
            int lockedHeight = config.getLockedHeight();
            // 过滤掉低于锁定标高的方块位置
            positions = positions.stream()
                .filter(pos -> pos.getY() >= lockedHeight)
                .toList();
                
            // 如果没有可破坏的方块，直接返回
            if (positions.isEmpty()) {
                if (player != null) {
                    player.sendMessage(Text.translatable("pushdozer.message.cannot_break_below", lockedHeight), true);
                }
                return;
            }
        }

        // 过滤掉不在可破坏列表中的方块
        positions = positions.stream()
            .filter(pos -> {
                BlockState state = world.getBlockState(pos);
                return config.isBlockBreakable(state.getBlock());
            })
            .toList();

        // 如果过滤后没有可破坏的方块，直接返回
        if (positions.isEmpty()) {
            return;
        }
        
        List<BlockState> originalStates = new ArrayList<>();
        List<BlockState> newStates = new ArrayList<>();
        
        // 记录原始状态和新状态
        for (BlockPos pos : positions) {
            BlockState originalState = world.getBlockState(pos);
            originalStates.add(originalState);
            newStates.add(Blocks.AIR.getDefaultState());
        }

        // 创建撤销操作
        UndoAction action = new UndoAction(UndoAction.ActionType.DESTROY, positions, originalStates, newStates);
        
        // 执行破坏操作
        for (BlockPos pos : positions) {
            world.setBlockState(pos, Blocks.AIR.getDefaultState(), Block.NOTIFY_ALL | Block.FORCE_STATE);
            world.playSound(null, pos, SoundEvents.BLOCK_STONE_BREAK, SoundCategory.BLOCKS, 1.0F, 1.0F);
        }
        
        // 记录撤销操作
        pushUndoAction(player, action);
    }

    public static PushdozerConfig getConfig() {
        return PushdozerConfig.getInstance();
    }

    // 修改 handlePlacement 方法，确保记录撤销操作
    public static void handlePlacement(PlayerEntity player, World world) {
        if (player instanceof ServerPlayerEntity serverPlayer) {
            if (!ActionValidator.validatePlaceAction(serverPlayer)) {
                return;
            }
        }
        
        if (placementHandler != null) {
            placementHandler.handlePlacement(player, world);
        }
    }

    public static void setOperation(BlockOperation operation) {
        currentOperation = operation;
    }

    public static void executeOperation(PlayerEntity player, World world) {
        if (currentOperation != null) {
            currentOperation.execute(player, world);
        }
    }

    public static void pushUndoAction(PlayerEntity player, UndoAction action) {
        UndoRedoService.getInstance().pushUndoAction(player, action);
    }

    public static void undoLastAction(PlayerEntity player, World world) {
        UndoRedoService.getInstance().undoLastAction(player, world);
    }

    public static void redoLastAction(PlayerEntity player, World world) {
        UndoRedoService.getInstance().redoLastAction(player, world);
    }

    public static void saveConfig() {
        if (config != null) {
            config.save();
        }
    }

    public static void forceBlockUpdate(World world, BlockPos pos) {
        if (world instanceof ServerWorld serverWorld) {
            BlockState state = serverWorld.getBlockState(pos);
            serverWorld.updateListeners(pos, state, state, Block.NOTIFY_ALL);
            serverWorld.updateNeighbors(pos, state.getBlock());
            serverWorld.getChunkManager().markForUpdate(pos);
        }
    }

    // 修改 handleSmoothing 方法，确保记录撤销操作
    public static void handleSmoothing(PlayerEntity player, World world) {
        if (player instanceof ServerPlayerEntity serverPlayer) {
            if (!ActionValidator.validateSmoothAction(serverPlayer)) {
                return;
            }
        }
        
        if (smoothingHandler != null) {
            smoothingHandler.handleSmoothing(player, world);
            // 注意：smoothingHandler 内部已经处理了撤销操作的记录
        }
    }
}