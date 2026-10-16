package com.example.structureworld;

import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.structure.StructureSet;
import net.minecraft.world.gen.chunk.placement.RandomSpreadStructurePlacement;
import net.minecraft.world.gen.chunk.placement.SpreadType;

import java.util.stream.Stream;

/**
 * 把「结构集注册表流」加密：配置里点名的结构集会被换成一个 spacing/separation 极小的
 * {@link RandomSpreadStructurePlacement}，从而在合适的生物群系里高密度生成。
 *
 * <p>这是整个模组唯一真正改动原版世界生成的地方 —— 地形、生物群系、出生点全部由原版
 * {@code NoiseChunkGenerator} 负责，因此世界就是标准的正常主世界。</p>
 */
public final class DenseStructurePlacements {

    private DenseStructurePlacements() {
    }

    /**
     * @param registry 原版结构集注册表包装器
     * @param cfg      模组配置
     * @return 加密后的结构集流（未命中的条目原样保留）
     */
    public static Stream<RegistryEntry<StructureSet>> densify(RegistryWrapper<StructureSet> registry,
                                                             StructureWorldConfig cfg) {
        final int spacing = Math.max(1, cfg.spacing);
        // 关键：随机散布要求 separation < spacing（否则内部 nextInt(0) 抛 "Bound must be positive"）。
        // 这里强制把间隔限制在 [0, spacing-1]，任何配置都不会崩。
        final int separation = Math.max(0, Math.min(cfg.separation, spacing - 1));
        final SpreadType spreadType = parseSpread(cfg.spreadType);

        System.out.println("[StructureWorld] 生成结构放置计算: spacing=" + spacing
                + ", separation=" + separation + ", spread=" + spreadType + ", mode=" + cfg.mode
                + ", 勾选=" + cfg.structureSets);
        if (spacing <= 2 && "ALL".equalsIgnoreCase(cfg.mode)) {
            System.out.println("[StructureWorld] 警告: spacing=" + spacing + " + mode=ALL 属于「极限满铺」，"
                    + "区块生成会非常慢，世界加载可能卡住。建议把 spacing 调到 6 以上、或改用 WHITELIST 只铺地表结构。");
        }

        return registry.streamEntries().map(entry -> {
            if (!shouldDensify(cfg, entry.getKey().orElse(null))) {
                return entry;
            }
            StructureSet original = entry.value();
            // 用结构集的注册表键派生一个稳定的 salt，避免所有结构完全对齐到同一网格。
            // （不读取原 placement 的 salt：该方法是 protected，跨包不可访问。）
            int salt = entry.getKey().map(key -> key.getValue().hashCode()).orElse(0);
            RandomSpreadStructurePlacement densePlacement =
                    new RandomSpreadStructurePlacement(spacing, separation, spreadType, salt);
            return RegistryEntry.of(new StructureSet(original.structures(), densePlacement));
        });
    }

    /** 解析配置的 spreadType 字符串为枚举，非法/空值回退到 LINEAR。 */
    private static SpreadType parseSpread(String s) {
        if (s == null) {
            return SpreadType.LINEAR;
        }
        try {
            return SpreadType.valueOf(s.toUpperCase());
        } catch (IllegalArgumentException e) {
            return SpreadType.LINEAR;
        }
    }

    /** 根据配置判断某个结构集是否应该被「铺满」。 */
    private static boolean shouldDensify(StructureWorldConfig cfg, RegistryKey<StructureSet> key) {
        if (key == null) {
            return true;
        }
        String id = key.getValue().toString(); // 形如 minecraft:villages
        return switch (cfg.mode.toUpperCase()) {
            case "WHITELIST" -> cfg.structureSets.contains(id);
            case "BLACKLIST" -> !cfg.structureSets.contains(id);
            default -> true; // ALL
        };
    }
}
