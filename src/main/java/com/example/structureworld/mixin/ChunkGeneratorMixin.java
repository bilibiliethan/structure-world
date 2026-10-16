package com.example.structureworld.mixin;

import com.example.structureworld.DenseStructurePlacements;
import com.example.structureworld.StructureWorldConfig;
import com.example.structureworld.StructureWorldMod;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.structure.StructureSet;
import net.minecraft.world.gen.chunk.ChunkGenerator;
import net.minecraft.world.gen.chunk.NoiseChunkGenerator;
import net.minecraft.world.gen.chunk.placement.StructurePlacementCalculator;
import net.minecraft.world.gen.noise.NoiseConfig;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 唯一的世界生成改动点：结构放置计算。
 *
 * <p>为什么用这种方式（而不是自定义 ChunkGenerator）：<br>
 * 原版 {@code ServerChunkLoadingManager} 构造器里写死了
 * {@code if (chunkGenerator instanceof NoiseChunkGenerator ncg) { 用 ncg.getSettings() 建 NoiseConfig }
 * else { 用 ChunkGeneratorSettings.createMissingSettings() 建 NoiseConfig }}。<br>
 * 也就是说，任何<b>不是</b> {@code NoiseChunkGenerator} 实例的自定义生成器，都会拿到一个
 * <b>空壳设置</b>：没有 spawn_target（出生点恒定落在世界原点）、多重噪声采样器退化
 * （全图生物群系恒定同一个）。这正是「出生在大海上 + 生物群系全是河流」的根因。</p>
 *
 * <p>因此本模组改为：世界直接用原版 {@code minecraft:noise} 生成器，只把 settings 换成
 * {@code structureworld:dense_overworld}（内容与原版 overworld 设置逐字节相同）。这样
 * NoiseConfig 完全正常（地形、生物群系、出生点全部与原版一致），我们再在这里把结构集加密。</p>
 *
 * <p>判断依据：生成器是 {@link NoiseChunkGenerator} 且 settings 恰好是
 * {@code structureworld:dense_overworld}。其他任何世界（含原版世界、下界、末地）都不受影响。</p>
 */
@Mixin(ChunkGenerator.class)
public class ChunkGeneratorMixin {

    @Inject(method = "createStructurePlacementCalculator", at = @At("HEAD"), cancellable = true)
    private void structureworld$denseStructurePlacements(RegistryWrapper<StructureSet> structureSetRegistry,
                                                         NoiseConfig noiseConfig,
                                                         long seed,
                                                         CallbackInfoReturnable<StructurePlacementCalculator> cir) {
        // 注意：Mixin 类在编译期与目标类型无继承关系，所以要先经 Object 中转再判断/转换。
        Object self = this;
        if (!(self instanceof NoiseChunkGenerator noiseGenerator)) {
            return;
        }
        boolean isDenseWorld = noiseGenerator.getSettings().getKey()
                .map(key -> key.getValue().equals(StructureWorldMod.DENSE_OVERWORLD_SETTINGS))
                .orElse(false);
        if (!isDenseWorld) {
            return; // 普通世界 → 完全走原版逻辑
        }

        StructureWorldConfig cfg = StructureWorldConfig.load();
        cir.setReturnValue(StructurePlacementCalculator.create(
                noiseConfig,
                seed,
                ((ChunkGenerator) self).getBiomeSource(),
                DenseStructurePlacements.densify(structureSetRegistry, cfg)
        ));
    }
}
