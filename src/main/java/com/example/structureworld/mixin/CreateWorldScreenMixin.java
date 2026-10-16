package com.example.structureworld.mixin;

import com.example.structureworld.client.StructureWorldConfigScreen;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.world.CreateWorldScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 在“创建世界”界面底部注入一个“结构世界设置”按钮，点开自定义配置界面。
 *
 * <p>只做一件最简单的事：在 {@code init()} 末尾往界面里加一个按钮，不触碰世界生成相关逻辑，
 * 因此不会像之前的 WorldPresetsMixin 那样破坏 WorldPresets 类。</p>
 */
@Environment(EnvType.CLIENT)
@Mixin(CreateWorldScreen.class)
public abstract class CreateWorldScreenMixin extends Screen {

    private CreateWorldScreenMixin() {
        super(Text.literal(""));
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void structureworld$addButton(CallbackInfo ci) {
        Screen self = (Screen) this;
        int w = 200;
        int x = this.width - w - 10;
        int y = this.height - 50;
        this.addDrawableChild(ButtonWidget.builder(
                        Text.literal("结构世界设置"),
                        btn -> MinecraftClient.getInstance().setScreen(new StructureWorldConfigScreen(self)))
                .dimensions(x, y, w, 20)
                .build());
    }
}
