package com.tabletopgames.storage;

import cn.nukkit.plugin.PluginBase;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * 插件数据库（SQLite，2026-10-22 引入）。
 *
 * <p>选型与 {@code D:\CodeProject\Territory-main} 一致：<b>SQLite</b> 单文件库，
 * 文件落在 {@code plugins/TabletopGames/tabletopgames.db}。驱动由服务器上的
 * <b>OrmLite 插件</b>提供（{@code plugin.yml} 里 {@code depend: [EconomyAPI, OrmLite]}），
 * 本插件不再把 sqlite-jdbc 打进 jar —— 与 Territory 同一套依赖方式。</p>
 *
 * <h3>为什么不是 OrmLite</h3>
 * Territory 用了 OrmLite（18MB，注解式 ORM）。本插件只有三张表、SQL 都很短，
 * 用 {@link DriverManager} 直连 + 手写 SQL 更小更直白，也少一个依赖要跟着升级。
 *
 * <h3>表</h3>
 * <ul>
 *   <li>{@code player_settings}：玩家个人设置（定位器准星开关、军旗敌方棋子倒置开关）；</li>
 *   <li>{@code ddz_table_config}：斗地主<b>每张牌桌</b>的配置（场次 / 机器人难度），
 *       这类配置属于牌桌自己，不进全局 config.yml；</li>
 *   <li>{@code board_state}：棋盘 / 牌桌的棋子（实体）数据，按 (棋类, 编号) 一行一条。</li>
 *   <li>{@code junqi_formation}：玩家保存的军旗布阵（每人最多 30 个）。</li>
 * </ul>
 *
 * <p>⚠️ 连接只在主线程用（存档读写都在 tick / 事件里），方法加了 {@code synchronized}，
 * 不要拿到别处做长事务。</p>
 */
public final class Database implements AutoCloseable {

    /** 数据库文件名（插件数据目录下）。 */
    private static final String FILE_NAME = "tabletopgames.db";

    private final PluginBase plugin;
    private Connection connection;

    public Database(PluginBase plugin) {
        this.plugin = plugin;
    }

    /**
     * 打开连接并建表。失败时只记日志、返回 {@code false}——插件其余功能照常跑，
     * 不因为数据库打不开就整个不加载（棋盘仍可用 YAML 存档兜底）。
     */
    public boolean open() {
        try {
            // sqlite-jdbc 的驱动类；显式加载，避免 SPI 被某些类加载器挡住
            Class.forName("org.sqlite.JDBC");
            File file = new File(plugin.getDataFolder(), FILE_NAME);
            File parent = file.getParentFile();
            if (parent != null && !parent.isDirectory() && !parent.mkdirs()) {
                plugin.getLogger().warning("无法创建数据库目录：" + parent.getPath());
            }
            connection = DriverManager.getConnection("jdbc:sqlite:" + file.getAbsolutePath());
            connection.setAutoCommit(true);
            createTables();
            plugin.getLogger().info("数据库已就绪：" + file.getPath());
            return true;
        } catch (Exception e) {
            plugin.getLogger().error("数据库打开失败（本次运行不落库，其余功能不受影响）", e);
            connection = null;
            return false;
        }
    }

    private void createTables() throws SQLException {
        try (Statement st = connection.createStatement()) {
            st.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS player_settings (
                        uuid             TEXT PRIMARY KEY,
                        locator_title    INTEGER NOT NULL DEFAULT 1,
                        junqi_flip_enemy INTEGER NOT NULL DEFAULT 0,
                        updated_at       INTEGER NOT NULL DEFAULT 0
                    )""");
            // 旧库补列：军旗「敌方棋子倒置」开关（2026-10-23）
            try {
                st.executeUpdate("ALTER TABLE player_settings ADD COLUMN junqi_flip_enemy INTEGER NOT NULL DEFAULT 0");
            } catch (SQLException ignored) {
                // 列已存在（新库）——正常路径
            }
            st.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS ddz_table_config (
                        table_key  TEXT PRIMARY KEY,
                        tier       TEXT NOT NULL DEFAULT 'free',
                        difficulty TEXT NOT NULL DEFAULT 'advanced',
                        updated_at INTEGER NOT NULL DEFAULT 0
                    )""");
            st.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS board_state (
                        kind       TEXT    NOT NULL,
                        board_id   INTEGER NOT NULL,
                        table_key  TEXT    NOT NULL DEFAULT '',
                        level      TEXT    NOT NULL DEFAULT '',
                        data       TEXT    NOT NULL,
                        updated_at INTEGER NOT NULL DEFAULT 0,
                        PRIMARY KEY (kind, board_id)
                    )""");
            st.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS junqi_formation (
                        uuid       TEXT    NOT NULL,
                        name       TEXT    NOT NULL,
                        layout     TEXT    NOT NULL,
                        updated_at INTEGER NOT NULL DEFAULT 0,
                        PRIMARY KEY (uuid, name)
                    )""");
        }
    }

    /** 是否可用（没打开成功时所有 DAO 都退化成内存态）。 */
    public boolean available() {
        return connection != null;
    }

    public synchronized Connection connection() {
        return connection;
    }

    @Override
    public void close() {
        if (connection == null) {
            return;
        }
        try {
            connection.close();
        } catch (SQLException e) {
            plugin.getLogger().warning("数据库关闭异常：" + e.getMessage());
        } finally {
            connection = null;
        }
    }
}
