package com.example.structureworld;

import net.fabricmc.api.ModInitializer;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;

import java.nio.file.Path;

/**
 * 模组入口。
 *
 * <p>结构世界的实现方式（v2）：</p>
 * <ul>
 *   <li>世界本身 = <b>原版 {@code minecraft:noise} 生成器</b>，只是把 settings 换成
 *       {@code structureworld:dense_overworld}（内容与原版 overworld 设置逐字节相同，
 *       包括 spawn_target）。这样原版创建 NoiseConfig 时能拿到真实设置，
 *       地形 / 生物群系 / 出生点全部与原版一致。</li>
 *   <li>唯一的改动点：{@code ChunkGenerator#createStructurePlacementCalculator} 的 Mixin，
 *       见 {@code mixin/ChunkGeneratorMixin}，仅在 settings 命中 dense_overworld 时生效。</li>
 * </ul>
 *
 * <p>为什么不再自定义 ChunkGenerator：原版 {@code ServerChunkLoadingManager} 用
 * {@code instanceof NoiseChunkGenerator} 判断要不要读取真实 settings，自定义生成器会拿到
 * 空壳设置，导致出生点恒定在世界原点、全图生物群系恒定同一个。详见
 * {@code mixin/ChunkGeneratorMixin} 的注释。</p>
 *
 * <p>世界预设以数据包 JSON 形式提供，见
 * {@code data/structureworld/worldgen/world_preset/dense_structures.json}。</p>
 */
public class StructureWorldMod implements ModInitializer {
    public static final String MOD_ID = "structureworld";

    /** 结构世界使用的噪声设置条目（数据包：data/structureworld/worldgen/noise_settings/dense_overworld.json）。 */
    public static final Identifier DENSE_OVERWORLD_SETTINGS =
            Identifier.of(MOD_ID, "dense_overworld");

    @Override
    public void onInitialize() {
        // 注册旧版自定义生成器的编解码器。
        // v2 起新世界不再使用它，但保留注册可以让「v1 时代创建的旧存档」仍能加载（不至于报未知生成器）。
        Registry.register(
                Registries.CHUNK_GENERATOR,
                RegistryKey.of(RegistryKeys.CHUNK_GENERATOR, Identifier.of(MOD_ID, "dense_structure")),
                DenseStructureChunkGenerator.CODEC
        );

        // 首次启动生成默认配置文件（config/structureworld.json），方便玩家自定义铺满的结构与密度。
        Path configFile = StructureWorldConfig.ensureDefaultFile();
        System.out.println("[StructureWorld] 配置文件位置: " + configFile.toAbsolutePath());
    }
}
