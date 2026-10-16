package com.example.structureworld;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * 模组配置：决定“结构世界”里哪些结构被铺满、以及铺满的密度。
 *
 * <p>配置文件位置：游戏目录下的 {@code config/structureworld.json}（首次启动会自动生成带注释的默认文件）。
 * 改完保存后，下次“创建世界”即生效（无需重启游戏；但已生成的世界不会变）。</p>
 *
 * <p>字段说明：</p>
 * <ul>
 *   <li>{@code spacing}：每隔多少区块尝试一次结构放置。{@code 1} = 最密；数字越大越稀。</li>
 *   <li>{@code separation}：相邻结构最小间隔区块数。{@code 0} = 允许紧挨。</li>
 *   <li>{@code spreadType}：{@code LINEAR} 或 {@code TRIANGULAR}。</li>
 *   <li>{@code mode}：{@code ALL}（全部铺满）/ {@code WHITELIST}（只铺 listed 里的）/ {@code BLACKLIST}（除 listed 外都铺）。</li>
 *   <li>{@code structureSets}：WHITELIST/BLACKLIST 模式下的结构集 ID 列表（填 {@code minecraft:xxx}）。</li>
 * </ul>
 */
public class StructureWorldConfig {

    /** 默认配置：只铺 11 个地表结构、间距 6 —— 密度约为原版村庄的 5 倍，且不会拖慢世界加载。 */
    public int spacing = 6;
    public int separation = 1;
    public String spreadType = "LINEAR";
    public String mode = "WHITELIST"; // ALL / WHITELIST / BLACKLIST
    public List<String> structureSets = defaultStructureSets();

    /** 默认铺满名单：只含地表结构，避开废弃矿井/要塞/远古城市这类纯地下高开销结构。 */
    public static List<String> defaultStructureSets() {
        return new ArrayList<>(List.of(
                "minecraft:villages",
                "minecraft:desert_pyramids",
                "minecraft:jungle_temples",
                "minecraft:igloo",
                "minecraft:swamp_huts",
                "minecraft:pillager_outposts",
                "minecraft:woodland_mansions",
                "minecraft:ocean_monuments",
                "minecraft:ocean_ruins",
                "minecraft:shipwrecks",
                "minecraft:buried_treasure"
        ));
    }

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    /** 默认文件内容（含注释，便于用户修改；注释字段在读取时会被忽略）。 */
    private static final String DEFAULT_JSON = """
            {
              "_comment": "结构世界配置：改完保存即可，下次创建世界生效。mode=ALL 全部铺满；WHITELIST 只铺 listed；BLACKLIST 除 listed 外都铺。spacing 越小越密(1=最密但最卡)，想更流畅就调大(6~12)。",
              "spacing": 6,
              "separation": 1,
              "spreadType": "LINEAR",
              "mode": "WHITELIST",
              "structureSets": [
                "minecraft:villages",
                "minecraft:desert_pyramids",
                "minecraft:jungle_temples",
                "minecraft:igloo",
                "minecraft:swamp_huts",
                "minecraft:pillager_outposts",
                "minecraft:woodland_mansions",
                "minecraft:ocean_monuments",
                "minecraft:ocean_ruins",
                "minecraft:shipwrecks",
                "minecraft:buried_treasure"
              ]
            }
            """;

    /** 首次启动时生成默认配置文件（如果还不存在），并返回其所在目录路径。 */
    public static Path ensureDefaultFile() {
        Path dir = FabricLoader.getInstance().getConfigDir();
        Path file = dir.resolve("structureworld.json");
        try {
            if (!Files.exists(file)) {
                Files.createDirectories(dir);
                Files.writeString(file, DEFAULT_JSON);
            }
        } catch (Exception e) {
            // 配置写不出来不影响游戏运行，仅记录。
            System.err.println("[StructureWorld] 无法写入默认配置: " + e.getMessage());
        }
        return file;
    }

    /** 从配置文件读取；文件缺失/损坏时回退到默认值（间距 6 + 白名单地表结构）。 */
    public static StructureWorldConfig load() {
        Path file = FabricLoader.getInstance().getConfigDir().resolve("structureworld.json");
        try {
            if (!Files.exists(file)) {
                return new StructureWorldConfig();
            }
            StructureWorldConfig cfg = GSON.fromJson(Files.readString(file), StructureWorldConfig.class);
            if (cfg == null) {
                cfg = new StructureWorldConfig();
            }
            if (cfg.structureSets == null) {
                cfg.structureSets = new ArrayList<>();
            }
            if (cfg.spreadType == null) {
                cfg.spreadType = "LINEAR";
            }
            if (cfg.mode == null || cfg.mode.isBlank()) {
                cfg.mode = "WHITELIST";
            }
            // 白名单模式下名单为空 → 视为“用默认名单”，否则会一个结构都不铺。
            if (cfg.structureSets.isEmpty() && "WHITELIST".equalsIgnoreCase(cfg.mode)) {
                cfg.structureSets = defaultStructureSets();
            }
            return cfg;
        } catch (Exception e) {
            System.err.println("[StructureWorld] 读取配置失败，使用默认: " + e.getMessage());
            return new StructureWorldConfig();
        }
    }

    /** 把当前配置写回 config/structureworld.json。 */
    public void save() {
        Path file = FabricLoader.getInstance().getConfigDir().resolve("structureworld.json");
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, GSON.toJson(this));
        } catch (Exception e) {
            System.err.println("[StructureWorld] 保存配置失败: " + e.getMessage());
        }
    }
}
