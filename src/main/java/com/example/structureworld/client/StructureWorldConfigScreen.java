package com.example.structureworld.client;

import com.example.structureworld.StructureWorldConfig;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.IntConsumer;

/**
 * 结构世界配置界面（客户端）。
 *
 * <p>提供：间距、间隔、散布类型、模式的调整，以及每个结构集的勾选。
 * 保存即写入 {@code config/structureworld.json}，下次创建世界生效。</p>
 */
@Environment(EnvType.CLIENT)
public class StructureWorldConfigScreen extends Screen {

    /** 常用原版结构集 ID（用于勾选列表）。 */
    private static final List<String> STRUCTURE_SETS = List.of(
            "minecraft:villages", "minecraft:desert_pyramids", "minecraft:jungle_temples",
            "minecraft:igloo", "minecraft:swamp_huts", "minecraft:ocean_ruins",
            "minecraft:shipwrecks", "minecraft:ocean_monuments", "minecraft:woodland_mansions",
            "minecraft:buried_treasure", "minecraft:pillager_outposts", "minecraft:bastion_remnants",
            "minecraft:nether_fossils", "minecraft:fortresses", "minecraft:end_cities",
            "minecraft:trail_ruins", "minecraft:ancient_cities", "minecraft:mineshafts",
            "minecraft:ruined_portals"
    );

    /** 结构集 ID 的中文显示名。 */
    private static final Map<String, String> STRUCTURE_NAMES = Map.ofEntries(
            Map.entry("minecraft:villages", "村庄"),
            Map.entry("minecraft:desert_pyramids", "沙漠神庙"),
            Map.entry("minecraft:jungle_temples", "丛林神庙"),
            Map.entry("minecraft:igloo", "雪屋"),
            Map.entry("minecraft:swamp_huts", "女巫小屋"),
            Map.entry("minecraft:ocean_ruins", "海底废墟"),
            Map.entry("minecraft:shipwrecks", "沉船"),
            Map.entry("minecraft:ocean_monuments", "海底神殿"),
            Map.entry("minecraft:woodland_mansions", "林地府邸"),
            Map.entry("minecraft:buried_treasure", "埋藏的宝藏"),
            Map.entry("minecraft:pillager_outposts", "掠夺者前哨站"),
            Map.entry("minecraft:bastion_remnants", "堡垒遗迹"),
            Map.entry("minecraft:nether_fossils", "下界化石"),
            Map.entry("minecraft:fortresses", "下界要塞"),
            Map.entry("minecraft:end_cities", "末地城"),
            Map.entry("minecraft:trail_ruins", "古迹废墟"),
            Map.entry("minecraft:ancient_cities", "远古城市"),
            Map.entry("minecraft:mineshafts", "废弃矿井"),
            Map.entry("minecraft:ruined_portals", "废弃传送门")
    );

    private final Screen parent;
    private final StructureWorldConfig config;

    // 顶部控件
    private SliderWidget spacingSlider;
    private SliderWidget separationSlider;
    private ButtonWidget spreadButton;
    private ButtonWidget modeButton;

    // 结构集勾选状态（仅对 WHITELIST/BLACKLIST 模式生效）
    private final Map<String, Boolean> selected = new LinkedHashMap<>();

    // 列表滚动
    private int scroll = 0;
    private static final int ROW_H = 22;
    private static final int LIST_X = 20;
    private static final int BOX = 14;

    public StructureWorldConfigScreen(Screen parent) {
        super(Text.literal("结构世界设置"));
        this.parent = parent;
        this.config = StructureWorldConfig.load();
        for (String id : STRUCTURE_SETS) {
            boolean on = config.structureSets != null && config.structureSets.contains(id);
            selected.put(id, on);
        }
    }

    @Override
    protected void init() {
        super.init();
        int y = 50;
        int w = (this.width - 60) / 4;

        // 间距 / 间隔：可拖动滑块
        spacingSlider = new IntSlider(20, y, w, 20, "间距", 1, 16, config.spacing,
                v -> config.spacing = v);
        separationSlider = new IntSlider(20 + (w + 8) * 1, y, w, 20, "间隔", 0, 16, config.separation,
                v -> config.separation = v);

        spreadButton = ButtonWidget.builder(Text.literal(""), b -> {
            config.spreadType = "LINEAR".equals(config.spreadType) ? "TRIANGULAR" : "LINEAR";
            refreshTop();
        }).dimensions(20 + (w + 8) * 2, y, w, 20).build();

        modeButton = ButtonWidget.builder(Text.literal(""), b -> {
            config.mode = switch (config.mode.toUpperCase()) {
                case "WHITELIST" -> "BLACKLIST";
                case "BLACKLIST" -> "ALL";
                default -> "WHITELIST";
            };
            refreshTop();
        }).dimensions(20 + (w + 8) * 3, y, w, 20).build();

        this.addDrawableChild(spacingSlider);
        this.addDrawableChild(separationSlider);
        this.addDrawableChild(spreadButton);
        this.addDrawableChild(modeButton);
        refreshTop();

        // 底部按钮
        this.addDrawableChild(ButtonWidget.builder(Text.literal("保存并退出"), b -> saveAndClose())
                .dimensions(this.width / 2 - 150, this.height - 30, 140, 20).build());
        this.addDrawableChild(ButtonWidget.builder(Text.literal("取消"), b -> this.client.setScreen(parent))
                .dimensions(this.width / 2 + 12, this.height - 30, 140, 20).build());
    }

    private void refreshTop() {
        spreadButton.setMessage(Text.literal("散布: " + ("TRIANGULAR".equals(config.spreadType) ? "三角" : "线性")));
        modeButton.setMessage(Text.literal("模式: " + modeLabel(config.mode)));
    }

    private static String modeLabel(String mode) {
        return switch (mode == null ? "ALL" : mode.toUpperCase()) {
            case "WHITELIST" -> "白名单";
            case "BLACKLIST" -> "黑名单";
            default -> "全部";
        };
    }

    private int listTop() {
        return 90;
    }

    private int listBottom() {
        return this.height - 50;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        // 注意：框架的 renderWithTooltip 已经调用过 renderBackground（做了一次背景模糊），
        // 这里绝不能再调一次，否则会触发 "Can only blur once per frame" 崩溃。
        super.render(context, mouseX, mouseY, delta);

        context.drawText(this.textRenderer, Text.literal("结构世界 —— 自定义铺满哪些结构、以及密度"), 20, 28, 0xFFFFFFFF, true);
        if (config.spacing <= 2) {
            context.drawText(this.textRenderer,
                    Text.literal("间距 " + config.spacing + " 太密，世界加载会非常慢甚至卡死，建议 6 以上"),
                    20, 40, 0xFFFF5555, true);
        }
        context.drawText(this.textRenderer,
                Text.literal("模式：全部=都铺满；白名单=只铺勾选的；黑名单=除勾选外都铺。点下方结构名可勾选"),
                20, listTop() - 16, 0xFFC0C0C0, true);

        // 可见区域
        int top = listTop();
        int bottom = listBottom();
        int startRow = Math.max(0, scroll / ROW_H);
        int visible = (bottom - top) / ROW_H + 1;
        for (int i = startRow; i < STRUCTURE_SETS.size() && i <= startRow + visible; i++) {
            int rowY = top + i * ROW_H - scroll;
            if (rowY + ROW_H < top || rowY > bottom) continue;
            String id = STRUCTURE_SETS.get(i);
            boolean on = Boolean.TRUE.equals(selected.get(id));
            // 勾选框
            context.fill(LIST_X, rowY, LIST_X + BOX, rowY + BOX, 0xFF3A3A3A);
            context.drawText(this.textRenderer, Text.literal(on ? "[x]" : "[ ]"), LIST_X - 2, rowY + 2,
                    on ? 0xFF55FF55 : 0xFF999999, true);
            // 中文名标签
            int color = "ALL".equals(config.mode) ? 0xFFBBBBBB : 0xFFFFFFFF;
            String label = STRUCTURE_NAMES.getOrDefault(id, id);
            context.drawText(this.textRenderer, Text.literal(label), LIST_X + BOX + 8, rowY + 2, color, true);
        }

        // 滚动条
        int totalH = STRUCTURE_SETS.size() * ROW_H;
        int viewH = bottom - top;
        if (totalH > viewH) {
            int barH = Math.max(20, viewH * viewH / totalH);
            int barY = top + (scroll * (viewH - barH) / (totalH - viewH));
            context.fill(this.width - 8, barY, this.width - 4, barY + barH, 0xFF888888);
        }
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        double mouseX = click.x();
        double mouseY = click.y();
        // 先让顶部/底部按钮正常响应
        if (super.mouseClicked(click, doubled)) {
            return true;
        }
        // 列表区勾选
        int top = listTop();
        int bottom = listBottom();
        if (mouseX >= LIST_X && mouseX <= LIST_X + BOX + 300 && mouseY >= top && mouseY <= bottom) {
            int rowY = (int) mouseY - top + scroll;
            int idx = rowY / ROW_H;
            if (idx >= 0 && idx < STRUCTURE_SETS.size()) {
                String id = STRUCTURE_SETS.get(idx);
                selected.put(id, !Boolean.TRUE.equals(selected.get(id)));
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        int totalH = STRUCTURE_SETS.size() * ROW_H;
        int viewH = listBottom() - listTop();
        if (totalH <= viewH) return false;
        int maxScroll = totalH - viewH;
        scroll = (int) Math.max(0, Math.min(maxScroll, scroll - verticalAmount * ROW_H));
        return true;
    }

    private void saveAndClose() {
        // 把勾选状态写回 structureSets。
        // 注意：先保留配置里"不在本界面列表中"的条目，避免保存时把它们悄悄丢掉。
        List<String> list = new ArrayList<>();
        if (config.structureSets != null) {
            for (String s : config.structureSets) {
                if (!STRUCTURE_SETS.contains(s)) {
                    list.add(s);
                }
            }
        }
        for (Map.Entry<String, Boolean> e : selected.entrySet()) {
            if (Boolean.TRUE.equals(e.getValue())) {
                list.add(e.getKey());
            }
        }
        config.structureSets = list;
        // 保证合法：间隔必须小于间距
        config.spacing = Math.max(1, config.spacing);
        if (config.separation >= config.spacing) {
            config.separation = config.spacing - 1;
        }
        config.save();
        MinecraftClient.getInstance().setScreen(parent);
    }

    /**
     * 整数滑块：把 SliderWidget 的 0..1 比例映射到 [min, max] 整数区间。
     * 拖动时实时回调 onChange，并自动更新显示的数值。
     */
    @Environment(EnvType.CLIENT)
    private static class IntSlider extends SliderWidget {
        private final String label;
        private final int min;
        private final int max;
        private final IntConsumer onChange;

        IntSlider(int x, int y, int width, int height, String label, int min, int max, int current,
                  IntConsumer onChange) {
            super(x, y, width, height, Text.literal(""), ratio(current, min, max));
            this.label = label;
            this.min = min;
            this.max = max;
            this.onChange = onChange;
            this.updateMessage();
        }

        private static double ratio(int v, int min, int max) {
            if (max <= min) {
                return 0.0;
            }
            return (double) (Math.max(min, Math.min(max, v)) - min) / (max - min);
        }

        private int currentValue() {
            return (int) Math.round(min + this.value * (max - min));
        }

        @Override
        protected void updateMessage() {
            this.setMessage(Text.literal(label + ": " + currentValue()));
        }

        @Override
        protected void applyValue() {
            onChange.accept(currentValue());
        }
    }
}
