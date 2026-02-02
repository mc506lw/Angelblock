package top.mistmc.angelblock.database;

import top.mistmc.angelblock.Angelblock;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.logging.Level;

public class DatabaseManager {
    private final Angelblock plugin;
    private final String databaseUrl;

    public DatabaseManager(Angelblock plugin) {
        this.plugin = plugin;
        File dataFolder = plugin.getDataFolder();
        if (!dataFolder.exists()) {
            dataFolder.mkdirs();
        }
        File databaseFile = new File(dataFolder, "blocks.db");
        this.databaseUrl = "jdbc:sqlite:" + databaseFile.getAbsolutePath();
    }

    public Connection getConnection() {
        try {
            Class.forName("org.sqlite.JDBC");
            Connection conn = DriverManager.getConnection(databaseUrl);
            return conn;
        } catch (ClassNotFoundException | SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "无法连接到 SQLite 数据库", e);
            return null;
        }
    }

    public void initialize() {
        try {
            Connection conn = getConnection();
            if (conn != null) {
                String sql = "CREATE TABLE IF NOT EXISTS angel_blocks (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                        "world TEXT NOT NULL," +
                        "x INTEGER NOT NULL," +
                        "y INTEGER NOT NULL," +
                        "z INTEGER NOT NULL," +
                        "player_uuid TEXT NOT NULL," +
                        "UNIQUE(world, x, y, z)" +
                        ")";
                try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                    stmt.executeUpdate();
                }
                conn.close();
                plugin.getLogger().info("[AngelBlock] 数据库表初始化成功！");
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "[AngelBlock] 初始化数据库表失败", e);
        }
    }

    public void close() {
        plugin.getLogger().info("[AngelBlock] 数据库连接已关闭！");
    }

    public void vacuum() {
        try {
            Connection conn = getConnection();
            if (conn != null) {
                try (PreparedStatement stmt = conn.prepareStatement("VACUUM")) {
                    stmt.executeUpdate();
                }
                conn.close();
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "数据库优化失败", e);
        }
    }
}
