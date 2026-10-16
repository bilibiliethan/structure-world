package com.example.structureworld;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.structure.StructureSet;
import net.minecraft.util.Identifier;
import net.minecraft.world.gen.StructureAccessor;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.HeightLimitView;
import net.minecraft.world.Heightmap;
import net.minecraft.world.biome.source.BiomeAccess;
import net.minecraft.world.biome.source.BiomeSource;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.ChunkRegion;
import net.minecraft.world.gen.chunk.Blender;
import net.minecraft.world.gen.chunk.ChunkGenerator;
import net.minecraft.world.gen.chunk.ChunkGeneratorSettings;
import net.minecraft.world.gen.chunk.NoiseChunkGenerator;
import net.minecraft.world.gen.chunk.VerticalBlockSample;
import net.minecraft.world.gen.chunk.placement.RandomSpreadStructurePlacement;
import net.minecraft.world.gen.chunk.placement.SpreadType;
import net.minecraft.world.gen.chunk.placement.StructurePlacementCalculator;
import net.minecraft.world.gen.noise.NoiseConfig;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

/**
 * 自定义区块生成器：在地形上“铺满”原版结构。
 *
 * <p>实现思路（委托模式）：</p>
 * <ul>
 *   <li>{@link NoiseChunkGenerator} 是 {@code final} 类，无法直接继承，因此我们在内部持有
 *       一个 {@code NoiseChunkGenerator} 实例，把地形/洞穴/生物群系等所有“形状”相关工作全部委托给它，
 *       从而得到一个与原版完全相同的地形。</li>
 *   <li>唯一被重写的是 {@link #createStructurePlacementCalculator}，在这里把注册表里的每一个
 *       {@link StructureSet} 复制一份，并替换成“极密”的 {@link RandomSpreadStructurePlacement}
 *       （spacing=1, separation=0），让结构几乎在每一块合适的区块都尝试生成 —— 即“铺满”。</li>
 * </ul>
 *
 * <p>注意：结构是否真正生成仍受自身生物群系条件限制（例如村庄只会在平原等生物群系生成），
 * 所以“铺满”指的是在合适的生物群系里高密度出现，而不是浮在海里或空中。</p>
 */
public class DenseStructureChunkGenerator extends ChunkGenerator {

    /** 注册到 CHUNK_GENERATOR 注册表时使用的键，与 fabric.mod.json / 数据包中的 type 对应。 */
    public static final RegistryKey<MapCodec<? extends ChunkGenerator>> KEY =
            RegistryKey.of(RegistryKeys.CHUNK_GENERATOR, Identifier.of("structureworld", "dense_structure"));

    /** 序列化用的 MapCodec（biome_source + settings），与原版 NoiseChunkGenerator 保持一致。 */
    public static final MapCodec<DenseStructureChunkGenerator> CODEC = RecordCodecBuilder.mapCodec(
            instance -> instance.group(
                            BiomeSource.CODEC.fieldOf("biome_source")
                                    .forGetter(DenseStructureChunkGenerator::getBiomeSource),
                            ChunkGeneratorSettings.REGISTRY_CODEC.fieldOf("settings")
                                    .forGetter(DenseStructureChunkGenerator::getSettings)
                    )
                    .apply(instance, DenseStructureChunkGenerator::new)
    );

    private final NoiseChunkGenerator delegate;
    private final RegistryEntry<ChunkGeneratorSettings> settings;

    public DenseStructureChunkGenerator(BiomeSource biomeSource, RegistryEntry<ChunkGeneratorSettings> settings) {
        super(biomeSource);
        this.settings = settings;
        this.delegate = new NoiseChunkGenerator(biomeSource, settings);
        System.out.println("[StructureWorld] DenseStructureChunkGenerator 已实例化（世界将铺满结构）");
    }

    public RegistryEntry<ChunkGeneratorSettings> getSettings() {
        return this.settings;
    }

    @Override
    protected MapCodec<? extends ChunkGenerator> getCodec() {
        return CODEC;
    }

    @Override
    public Optional<RegistryKey<MapCodec<? extends ChunkGenerator>>> getCodecKey() {
        return Optional.of(KEY);
    }

    /**
     * 关键重写：根据模组配置，把“需要铺满”的结构集替换为“极密”随机散布，
     * 其余结构保持原版放置，从而实现可自定义的结构铺满。
     *
     * <p>注意：v2 起新世界不再使用本类（原因见 {@code StructureWorldMod} 注释：
     * 自定义生成器拿不到真实 NoiseConfig，会导致出生点/生物群系异常）。本类仅保留，
     * 用于让 v1 创建的旧存档仍然能加载。真正的实现与新版共用 {@link DenseStructurePlacements}。</p>
     */
    @Override
    public StructurePlacementCalculator createStructurePlacementCalculator(
            RegistryWrapper<StructureSet> structureSetRegistry,
            NoiseConfig noiseConfig,
            long seed) {

        StructureWorldConfig cfg = StructureWorldConfig.load();
        return StructurePlacementCalculator.create(
                noiseConfig, seed, this.getBiomeSource(),
                DenseStructurePlacements.densify(structureSetRegistry, cfg));
    }

    // ===== 以下方法全部委托给内部的 NoiseChunkGenerator，保证地形与原版一致 =====

    @Override
    public int getWorldHeight() {
        return delegate.getWorldHeight();
    }

    @Override
    public int getSeaLevel() {
        return delegate.getSeaLevel();
    }

    @Override
    public int getMinimumY() {
        return delegate.getMinimumY();
    }

    @Override
    public int getHeight(int x, int z, Heightmap.Type heightmap, HeightLimitView world, NoiseConfig noiseConfig) {
        return delegate.getHeight(x, z, heightmap, world, noiseConfig);
    }

    @Override
    public VerticalBlockSample getColumnSample(int x, int z, HeightLimitView world, NoiseConfig noiseConfig) {
        return delegate.getColumnSample(x, z, world, noiseConfig);
    }

    @Override
    public void buildSurface(ChunkRegion region, StructureAccessor structures, NoiseConfig noiseConfig, Chunk chunk) {
        delegate.buildSurface(region, structures, noiseConfig, chunk);
    }

    @Override
    public void carve(ChunkRegion chunkRegion, long seed, NoiseConfig noiseConfig, BiomeAccess biomeAccess,
                      StructureAccessor structureAccessor, Chunk chunk) {
        delegate.carve(chunkRegion, seed, noiseConfig, biomeAccess, structureAccessor, chunk);
    }

    @Override
    public CompletableFuture<Chunk> populateBiomes(NoiseConfig noiseConfig, Blender blender,
                                                  StructureAccessor structureAccessor, Chunk chunk) {
        return delegate.populateBiomes(noiseConfig, blender, structureAccessor, chunk);
    }

    @Override
    public CompletableFuture<Chunk> populateNoise(Blender blender, NoiseConfig noiseConfig,
                                                 StructureAccessor structureAccessor, Chunk chunk) {
        return delegate.populateNoise(blender, noiseConfig, structureAccessor, chunk);
    }

    @Override
    public void populateEntities(ChunkRegion region) {
        delegate.populateEntities(region);
    }

    @Override
    public void appendDebugHudText(List<String> text, NoiseConfig noiseConfig, BlockPos pos) {
        delegate.appendDebugHudText(text, noiseConfig, pos);
    }
}
