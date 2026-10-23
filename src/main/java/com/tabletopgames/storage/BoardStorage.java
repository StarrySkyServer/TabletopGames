package com.tabletopgames.storage;

import cn.nukkit.plugin.PluginBase;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;

/**
 * 棋盘存档读写助手：一个棋盘一个 YAML 文件，按棋类分目录存放。
 * <p>
 * 目录结构：{@code <插件数据目录>/data/<棋类>/<4 位编号>.yml}
 * （如 {@code data/gomoku/0001.yml}、{@code data/uno/0002.yml}），文件内第一行 {@code id: 0001}
 * 与文件名编号一致。
 * <p>
 * {@link Yaml} 实例非线程安全，因此每次调用都新建一个（存档很小，开销可忽略）。
 * 运行期的 snakeyaml 由 Nukkit-MOT 内置提供（见 pom 中 okaeri-configs-yaml-snakeyaml 依赖注释）。
 */
public final class BoardStorage {

    private BoardStorage() {
    }

    // ------------------------------------------------------------------
    // 目录与文件
    // ------------------------------------------------------------------

    /** 某棋类的存档目录（{@code <插件数据目录>/data/<棋类>/}，不存在时创建）。 */
    public static File folder(PluginBase plugin, String kind) {
        File dir = new File(new File(plugin.getDataFolder(), "data"), kind);
        if (!dir.isDirectory() && !dir.mkdirs()) {
            plugin.getLogger().warning("无法创建存档目录：" + dir.getPath());
        }
        return dir;
    }

    /** 指定编号的存档文件（编号补零到 4 位，如 {@code 0001.yml}）。 */
    public static File file(File dir, int id) {
        return new File(dir, String.format(Locale.ROOT, "%04d.yml", id));
    }

    /** 目录下的全部存档文件，按文件名（即编号）升序。 */
    public static List<File> files(File dir) {
        File[] found = dir.listFiles((d, name) -> name.endsWith(".yml"));
        if (found == null || found.length == 0) {
            return new ArrayList<>();
        }
        List<File> list = new ArrayList<>(List.of(found));
        list.sort(Comparator.comparing(File::getName));
        return list;
    }

    /** 从文件名解析编号；不是「编号.yml」形式时返回 0。 */
    public static int idOf(File file) {
        String name = file.getName();
        if (!name.endsWith(".yml")) {
            return 0;
        }
        try {
            return Integer.parseInt(name.substring(0, name.length() - 4));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /** 删除存档文件；文件不存在返回 true。 */
    public static boolean delete(File file) {
        return !file.exists() || file.delete();
    }

    /** 以 UTF-8 写入文本（自动建父目录）。失败返回 false，由调用方记录日志。 */
    public static boolean write(File file, String text) {
        File parent = file.getParentFile();
        if (parent != null && !parent.isDirectory() && !parent.mkdirs()) {
            return false;
        }
        try {
            Files.writeString(file.toPath(), text, StandardCharsets.UTF_8);
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    // ------------------------------------------------------------------
    // YAML 文本
    // ------------------------------------------------------------------

    /** 把数据结构序列化为 YAML 文本（块状风格，UTF-8 由调用方写盘时指定）。 */
    public static String dump(Object data) {
        DumperOptions options = new DumperOptions();
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        options.setIndent(2);
        options.setPrettyFlow(true);
        return new Yaml(options).dump(data);
    }

    /** 读取 YAML 文件；文件不存在或解析失败返回 null。 */
    public static Object load(File file) {
        if (!file.isFile()) {
            return null;
        }
        try {
            return new Yaml().load(Files.readString(file.toPath(), StandardCharsets.UTF_8));
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    /**
     * 单个棋盘存档文件的文本拼装器。
     * <p>
     * snakeyaml 无法在映射内部插入注释，因此这里按「小节」分别 dump 再拼接：
     * 头部一段说明注释，之后每一节先写小节注释，再写「键: 值」。
     */
    public static final class Document {

        private final StringBuilder text = new StringBuilder();

        public Document(String title, String... notes) {
            appendComment(title);
            for (String note : notes) {
                appendComment(note);
            }
            text.append('\n');
        }

        /** 追加一节：{@code 小节注释} + {@code 键:} 下挂的对象（dump 成块状 YAML，自动缩进 2 格）。 */
        public Document section(String comment, String key, Object value) {
            appendComment(comment);
            text.append(dump(Collections.singletonMap(key, value))).append('\n');
            return this;
        }

        /** 追加一个标量节（值原样写出、不加引号，如 {@code id: 0001}）。 */
        public Document scalar(String comment, String key, String raw) {
            appendComment(comment);
            text.append(key).append(": ").append(raw).append("\n\n");
            return this;
        }

        public String build() {
            return text.toString();
        }

        /** 注释可多行（用 {@code \n} 分隔）；每行加 {@code "# "} 前缀。 */
        private void appendComment(String comment) {
            if (comment == null || comment.isEmpty()) {
                return;
            }
            try (Stream<String> lines = comment.lines()) {
                lines.forEach(line -> text.append("# ").append(line).append('\n'));
            }
        }
    }

    // ------------------------------------------------------------------
    // 取值工具
    // ------------------------------------------------------------------

    /** 从 YAML 映射读子映射；缺失或类型不符返回空映射。 */
    public static Map<?, ?> mapValue(Map<?, ?> map, String key) {
        Object value = map.get(key);
        return value instanceof Map<?, ?> sub ? sub : Collections.emptyMap();
    }

    /** 从 YAML 映射读字符串，缺失或类型不符返回默认值。 */
    public static String stringValue(Map<?, ?> map, String key, String fallback) {
        Object value = map.get(key);
        return value == null ? fallback : String.valueOf(value);
    }

    /** 从 YAML 映射读整数（Number 与数字字符串均可），缺失或无法解析返回默认值。 */
    public static int intValue(Map<?, ?> map, String key, int fallback) {
        Object value = map.get(key);
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    /** 从 YAML 映射读小数（Number 与数字字符串均可），缺失或无法解析返回默认值。 */
    public static double doubleValue(Map<?, ?> map, String key, double fallback) {
        Object value = map.get(key);
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        try {
            return Double.parseDouble(String.valueOf(value));
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    /** 从 YAML 映射读布尔（仅接受 Boolean），缺失返回默认值。 */
    public static boolean boolValue(Map<?, ?> map, String key, boolean fallback) {
        Object value = map.get(key);
        return value instanceof Boolean flag ? flag : fallback;
    }
}
