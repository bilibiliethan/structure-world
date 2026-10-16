# Structure World（结构世界）

[![Build](https://github.com/bilibiliethan/structure-world/actions/workflows/build.yml/badge.svg)](https://github.com/bilibiliethan/structure-world/actions/workflows/build.yml)
[![Release](https://img.shields.io/github/v/release/bilibiliethan/structure-world?label=release)](https://github.com/bilibiliethan/structure-world/releases/latest)

一个 **Minecraft Fabric** 模组（当前编译验证版本 **1.21.11**；目标版本 1.26.3，见文末核对清单）。

在“创建世界”界面提供一个名为 **结构世界（铺满结构）** 的世界预设。选用后，**世界就是一个标准、正常的主世界**（地形、洞穴、生物群系、出生点全部与原版一致），只是原版结构被高密度地铺在地表上（村庄、神庙、海底神殿、沉船……）。

---

## 工作原理（v2）

**关键点：世界用的是原版 `minecraft:noise` 生成器，不是自定义生成器。**

原因是原版 `ServerChunkLoadingManager` 的构造器里写死了这样一段逻辑：

```java
if (chunkGenerator instanceof NoiseChunkGenerator ncg) {
    this.noiseConfig = NoiseConfig.create(ncg.getSettings().value(), ...);   // 真实设置
} else {
    this.noiseConfig = NoiseConfig.create(
        ChunkGeneratorSettings.createMissingSettings(), ...);                // 空壳设置
}
```

也就是说：任何**不是** `NoiseChunkGenerator` 实例的自定义区块生成器，都会拿到一个空壳设置，于是

- 设置里没有 `spawn_target` → 出生点搜索恒返回**世界原点** → 原点若是海洋就「出生在大海上」；
- 多重噪声采样器退化 → **全图生物群系恒定同一个**。

（v1 用的“委托式自定义生成器”正是踩了这个坑，已废弃。）

v2 的做法：

| 文件 | 作用 |
| --- | --- |
| `data/structureworld/worldgen/noise_settings/dense_overworld.json` | 与原版 `minecraft:overworld` 噪声设置**逐字节相同**的副本（含 `spawn_target`）。这一步保证 NoiseConfig 完全正常。 |
| `data/structureworld/worldgen/world_preset/dense_structures.json` | 世界预设：主世界 = `minecraft:noise` + `structureworld:dense_overworld`；下界/末地保持原版。 |
| `data/minecraft/worldgen/world_preset/normal.json` | 覆盖原版 normal 预设，使“默认”世界类型也是结构世界。 |
| `data/minecraft/tags/worldgen/world_preset/normal.json` | 把预设加入“创建世界”界面的下拉列表。 |
| `mixin/ChunkGeneratorMixin` | **唯一的生成改动**：注入 `ChunkGenerator#createStructurePlacementCalculator`，仅当生成器是 `NoiseChunkGenerator` 且 settings 恰好为 `structureworld:dense_overworld` 时，把结构集换成极密放置。其他任何世界（原版/下界/末地）完全不受影响。 |
| `DenseStructurePlacements` | 把结构集注册表流“加密”：命中的 `StructureSet` 换成 `spacing/separation` 极小的 `RandomSpreadStructurePlacement`。 |
| `StructureWorldConfig` + `client/StructureWorldConfigScreen` | 配置文件 `config/structureworld.json` 与游戏内设置界面（滑块 + 勾选）。 |
| `mixin/CreateWorldScreenMixin` | 在“创建世界”界面加一个“结构世界设置”按钮。 |
| `DenseStructureChunkGenerator` | **已废弃**，仅为让 v1 时代创建的旧存档仍能加载而保留注册。 |

> 结构是否真正生成仍受结构自身的生物群系条件限制（村庄只会在平原等生物群系生成），所以“铺满”指的是在合适生物群系里高密度出现。

---

## 构建与运行

**直接下载现成 jar**（推荐，不用自己编译）：
👉 [Releases 页面](https://github.com/bilibiliethan/structure-world/releases/latest) → 下载 `structureworld-x.y.z.jar`

自己编译：

```bash
cd structure-world
./gradlew build        # 产物：build/libs/structureworld-1.0.0.jar
```

> 需要 **JDK 25**（fabric-loom 1.18 的硬性要求）。没装 wrapper 的机器可直接用本地 Gradle 9.7+：`gradle build`。
> `gradle.properties` 里已设 `org.gradle.jvmargs=-Xmx4G`（loom 反编译 Minecraft 时吃内存）。

把 jar 放进 `.minecraft/mods/`，启动游戏即可。本机 PCL 实例路径：
`D:\pcl\.minecraft\versions\1.21.11-Fabric 0.19.3\mods\`

### 自动构建（GitHub Actions）

工作流：`.github/workflows/build.yml`

| 触发时机 | 行为 |
| --- | --- |
| 推送到 `main` / 向 `main` 提 PR | 编译验证，产物上传为 Actions artifact（保留 30 天） |
| 推送 `v*` 标签（如 `v1.0.0`） | 额外**自动创建 Release 并附上模组 jar** |
| Actions 页面手动 Run workflow | 同上，仅编译 |

产物永远只含可用的模组 jar（`-dev.jar` 开发包与 `-sources.jar` 源码包会被排除）。
要发版：

```bash
git tag v1.0.0
git push origin v1.0.0     # 去 Actions 页面等 Release 自动出现
```

### 在游戏里使用
1. 单人游戏 → 创建世界
2. 世界类型选 **结构世界（铺满结构）**（英文端 *Structure World (dense structures)*；默认类型也已指向它）
3. **必须新建世界**——旧存档的区块已经生成过，不会变化

### 调参
- 游戏内：“创建世界”界面 →「结构世界设置」：拖动间距/间隔滑块、勾选要铺满的结构集。
- 或直接改 `config/structureworld.json`（改完下次创建世界生效）：
  - `spacing`：越小越密。`1` 最密但世界加载极慢；推荐 `6~12`。
  - `separation`：必须小于 `spacing`，代码里已强制夹取，不会崩。
  - `mode`：`ALL` / `WHITELIST` / `BLACKLIST`；`structureSets` 填 `minecraft:villages` 这类 ID。
- 默认配置：只铺 11 个**地表**结构 + `spacing=6`，密度约原版村庄的 5 倍，不拖慢加载。

---

## ⚠️ 版本核对清单（迁移到 1.26.3 时）

1. **`gradle.properties` 四个版本号**：`minecraft_version` / `yarn_mappings` / `loader_version` / `loom_version`。
   核对：`https://maven.fabricmc.net/net/fabricmc/yarn/`、`.../fabric-loader/`、`.../fabric-loom/`
2. **`dense_overworld.json` 必须重新从新版 jar 的 `data/minecraft/worldgen/noise_settings/overworld.json` 复制**（否则地形会与同版本原版不一致）。
3. **Mixin 目标方法**：`ChunkGenerator#createStructurePlacementCalculator` 是否仍是该签名；若不是，改 `ChunkGeneratorMixin` 的 `method`/参数列表。
4. **UI 翻译键**：`generator.structureworld.dense_structures`（`zh_cn.json` / `en_us.json`）。
5. **预设列表**：界面依赖 `minecraft:worldgen/world_preset/normal` 标签。
