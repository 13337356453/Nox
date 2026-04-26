package org.nox.tools;

import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * SQLite 数据库工具类。
 * 负责本地 SQLite 数据库的初始化、连接管理以及连接记录的增删改查。
 * 同时实现 CommandLineRunner 接口，确保应用启动时数据库已就绪。
 */
@Component
public final class SQLiteUtil implements CommandLineRunner {
    /** 数据库文件路径 */
    private static final String DB_PATH = "data.db";
    /** SQLite JDBC URL 前缀 */
    private static final String JDBC_PREFIX = "jdbc:sqlite:";
    /** 创建数据表的 SQL 语句 */
    private static final String CREATE_TABLE_SQL = "CREATE TABLE IF NOT EXISTS data (" +
            "id INTEGER PRIMARY KEY, " +
            "url TEXT NOT NULL" +
            ")";
    /** 初始化默认数据的 SQL 语句 */
    private static final String INIT_INSERT_SQL = "INSERT OR IGNORE INTO data(id, url) VALUES(?, ?)";

    // 静态代码块：类加载时即初始化数据库
    static {
        try {
            init();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to initialize SQLite database", e);
        }
    }

    /**
     * Spring Boot 启动回调，确保数据库已初始化。
     */
    @Override
    public void run(String... args) throws Exception {
        init();
    }

    /**
     * 初始化数据库文件和表结构。
     * 如果数据库文件或表不存在，则创建并插入默认记录。
     * @throws SQLException SQL 执行异常
     */
    public static void init() throws SQLException {
        boolean dbFileExisted = Files.exists(Paths.get(DB_PATH).toAbsolutePath());
        ensureDatabaseFile();

        try (Connection connection = getConnection()) {
            boolean tableExisted = tableExists(connection, "data");

            try (Statement statement = connection.createStatement()) {
                statement.execute(CREATE_TABLE_SQL);
            }

            // 如果数据库或表不存在，插入默认测试数据
            if (!dbFileExisted || !tableExisted) {
                try (PreparedStatement ps = connection.prepareStatement(INIT_INSERT_SQL)) {
                    ps.setInt(1, 1);
                    ps.setString(2, "http://127.0.0.1:8080/shell.jsp");
                    ps.executeUpdate();
                }
            }
        }
    }

    /**
     * 获取数据库连接。
     * @return SQLite 连接对象
     * @throws SQLException 连接失败时抛出
     */
    public static Connection getConnection() throws SQLException {
        ensureDatabaseFile();
        return DriverManager.getConnection(JDBC_PREFIX + DB_PATH);
    }

    /**
     * 新增一条连接记录。
     * @param url 目标 URL
     * @return 受影响的行数
     */
    public static int add(String url) {
        String sql = "INSERT INTO data(url) VALUES(?)";
        try (Connection connection = getConnection(); PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, url);
            return ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to add data", e);
        }
    }

    /**
     * 修改指定 ID 的连接记录。
     * @param id  记录 ID
     * @param url 新的目标 URL
     * @return 受影响的行数
     */
    public static int edit(int id, String url) {
        String sql = "UPDATE data SET url = ? WHERE id = ?";
        try (Connection connection = getConnection(); PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, url);
            ps.setInt(2, id);
            return ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to edit data", e);
        }
    }

    /**
     * 删除指定 ID 的连接记录。
     * @param id 记录 ID
     * @return 受影响的行数
     */
    public static int del(int id) {
        String sql = "DELETE FROM data WHERE id = ?";
        try (Connection connection = getConnection(); PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, id);
            return ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to delete data", e);
        }
    }

    /**
     * 根据 ID 查询单条记录。
     * @param id 记录 ID
     * @return 对应的 DataRecord，不存在则返回 null
     */
    public static DataRecord get(int id) {
        String sql = "SELECT id, url FROM data WHERE id = ?";
        try (Connection connection = getConnection(); PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return new DataRecord(rs.getInt("id"), rs.getString("url"));
                }
                return null;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to get data", e);
        }
    }

    /**
     * 查询所有连接记录，按 ID 升序排列。
     * @return 连接记录列表
     */
    public static List<DataRecord> getAll() {
        String sql = "SELECT id, url FROM data ORDER BY id";
        try (Connection connection = getConnection(); PreparedStatement ps = connection.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            List<DataRecord> list = new ArrayList<DataRecord>();
            while (rs.next()) {
                list.add(new DataRecord(rs.getInt("id"), rs.getString("url")));
            }
            return list;
        } catch (SQLException e) {
            throw new RuntimeException("Failed to get all data", e);
        }
    }

    /**
     * 检查指定表是否存在于数据库中。
     * @param connection 数据库连接
     * @param tableName  表名
     * @return 是否存在
     * @throws SQLException SQL 执行异常
     */
    private static boolean tableExists(Connection connection, String tableName) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT name FROM sqlite_master WHERE type='table' AND name=?")) {
            ps.setString(1, tableName);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    /**
     * 确保数据库文件存在。
     * 如果父目录不存在则创建，如果数据库文件不存在则创建空文件。
     */
    private static void ensureDatabaseFile() {
        try {
            Path dbFile = Paths.get(DB_PATH).toAbsolutePath();
            Path parent = dbFile.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            if (!Files.exists(dbFile)) {
                Files.createFile(dbFile);
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to create SQLite database file", e);
        }
    }

    /**
     * 数据记录实体类。
     * 对应 SQLite 中 data 表的一行记录。
     */
    public static class DataRecord {
        private final int id;
        private final String url;

        public DataRecord(int id, String url) {
            this.id = id;
            this.url = url;
        }

        public int getId() {
            return id;
        }

        public String getUrl() {
            return url;
        }
    }
}
