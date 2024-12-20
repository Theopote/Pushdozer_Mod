package com.pushdozer.ui;

import com.pushdozer.PushdozerMod;
import com.pushdozer.config.PushdozerConfig;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import net.minecraft.client.MinecraftClient;

// NotificationScreen 类用于创建一个通知界面
public class NotificationScreen extends Screen {

    // 存储父界面的引用，用于返回上一级界面
    private final Screen parent;
    // 存储要显示的消息
    private final Text message;
    private TextFieldWidget lengthField;
    private TextFieldWidget widthField;
    private TextFieldWidget heightField;
    private TextFieldWidget radiusField;
    private TextFieldWidget distanceField;
    private ButtonWidget shapeButton;
    private ButtonWidget displayModeButton;

    // 构造函数
    protected NotificationScreen(Screen parent, Text message) {
        // 调用父类构造函数，使用翻译键设置界面标题
        super(Text.translatable("pushdozer.config.title"));
        this.parent = parent;
        this.message = message;
    }

    // 初始化方法，用于设置界面组件
    @Override
    protected void init() {
        PushdozerConfig config = PushdozerMod.getConfig();

        // 添加形状选择按钮，使用翻译键
        this.shapeButton = this.addDrawableChild(ButtonWidget.builder(
            Text.translatable("pushdozer.config.geometric_shape").append(": ").append(Text.translatable("pushdozer.config." + config.getShape().toLowerCase())),
            button -> {
                String[] shapes = {"cuboid", "sphere"};
                int currentIndex = java.util.Arrays.asList(shapes).indexOf(config.getShape().toLowerCase());
                String newShape = shapes[(currentIndex + 1) % shapes.length];
                config.setShape(newShape);
                this.shapeButton.setMessage(Text.translatable("pushdozer.config.geometric_shape").append(": ").append(Text.translatable("pushdozer.config." + newShape)));
            })
            .dimensions(this.width / 2 - 100, 200, 200, 20)
            .build()
        );

        // 添加显示模式按钮
        this.displayModeButton = this.addDrawableChild(ButtonWidget.builder(
            Text.translatable("pushdozer.config.display_mode").append(": ").append(Text.translatable("pushdozer.config.display_mode." + config.getDisplayMode().name().toLowerCase())),
            button -> {
                PushdozerConfig.DisplayMode newMode = PushdozerConfig.DisplayMode.values()[(config.getDisplayMode().ordinal() + 1) % PushdozerConfig.DisplayMode.values().length];
                config.setDisplayMode(newMode);
                this.displayModeButton.setMessage(Text.translatable("pushdozer.config.display_mode").append(": ").append(Text.translatable("pushdozer.config.display_mode." + newMode.name().toLowerCase())));
            })
            .dimensions(this.width / 2 - 100, 230, 200, 20)
            .build()
        );

        // 添加保存按钮
        this.addDrawableChild(new ButtonWidget.Builder(
            Text.translatable("pushdozer.config.save_and_close"),
            button -> {
                saveConfig();
                this.client.setScreen(this.parent);
            })
            .dimensions(this.width / 2 - 100, this.height - 40, 200, 20)
            .build()
        );
    }

    private void saveConfig() {
        PushdozerConfig config = PushdozerMod.getConfig();
        // 根据需要实现保存逻辑
        config.save();
        if (this.client != null && this.client.player != null) {
            this.client.player.sendMessage(Text.translatable("pushdozer.config.saved"), false);
        }
    }

    // 渲染方法，用于绘制界面
    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        // 渲染背景
        this.renderBackground(context, mouseX, mouseY, delta);
        
        // 在界面顶部居中绘制标题
        if (this.client != null && this.client.textRenderer != null) {
            context.drawTextWithShadow(this.client.textRenderer, this.title, 
                (this.width - this.client.textRenderer.getWidth(this.title)) / 2, 10, 0xFFFFFF);
        } else {
            // 记录错误
            System.out.println("Error: textRenderer is null in NotificationScreen");
        }
        
        // 在界面中央绘制消息
        if (this.client != null && this.client.textRenderer != null) {
            context.drawTextWithShadow(this.client.textRenderer, this.message, 
                (this.width - this.client.textRenderer.getWidth(this.message)) / 2, 30, 0xFFFFFF);
        } else {
            System.out.println("Error: textRenderer is null in NotificationScreen");
        }

        super.render(context, mouseX, mouseY, delta);
    }
}