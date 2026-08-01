package com.example.dbmanager.service;

import com.example.dbmanager.dto.TestResultResponse;
import com.example.dbmanager.entity.DbConnection;
import com.example.dbmanager.exception.NotFoundException;
import com.example.dbmanager.repository.ConnectionRepository;
import com.example.dbmanager.util.AesEncryptor;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 目标数据库动态连接管理：按连接配置创建 HikariCP 连接池并缓存（key = connectionId），
 * 连接配置变更 / 删除时需调用 {@link #evict(Long)} 销毁对应连接池。
 * 目标库一律通过 JdbcTemplate 访问，不使用 ORM。
 */
@Service
public class TargetJdbcService {

    private static final Logger log = LoggerFactory.getLogger(TargetJdbcService.class);

    private static final String URL_PARAMS =
            "?useUnicode=true&characterEncoding=utf8mb4&useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true";

    private final Map<Long, HikariDataSource> pools = new ConcurrentHashMap<>();

    private final ConnectionRepository connectionRepository;
    private final AesEncryptor aesEncryptor;

    public TargetJdbcService(ConnectionRepository connectionRepository, AesEncryptor aesEncryptor) {
        this.connectionRepository = connectionRepository;
        this.aesEncryptor = aesEncryptor;
    }

    /**
     * 获取指定连接的 JdbcTemplate（连接池懒创建并缓存）。
     */
    public JdbcTemplate getJdbcTemplate(Long connectionId) {
        return new JdbcTemplate(getDataSource(connectionId));
    }

    /**
     * 获取指定连接的连接池（需要独占物理连接时使用，如多语句执行）。
     */
    public HikariDataSource getDataSource(Long connectionId) {
        return pools.computeIfAbsent(connectionId, this::createPool);
    }

    /**
     * 销毁并移除指定连接的连接池（配置变更 / 删除时调用）。
     */
    public void evict(Long connectionId) {
        HikariDataSource ds = pools.remove(connectionId);
        if (ds != null) {
            ds.close();
            log.info("已销毁连接池 target-conn-{}", connectionId);
        }
    }

    @PreDestroy
    public void shutdown() {
        pools.values().forEach(HikariDataSource::close);
        pools.clear();
    }

    private HikariDataSource createPool(Long connectionId) {
        DbConnection conn = connectionRepository.findById(connectionId)
                .orElseThrow(() -> new NotFoundException("连接不存在: " + connectionId));
        String plainPassword = aesEncryptor.decrypt(conn.getPasswordEnc());

        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(buildUrl(conn.getHost(), conn.getPort(), conn.getDatabaseName()));
        config.setUsername(conn.getUsername() == null ? "" : conn.getUsername());
        config.setPassword(plainPassword == null ? "" : plainPassword);
        config.setMaximumPoolSize(4);
        config.setMinimumIdle(1);
        config.setConnectionTimeout(5000);
        config.setValidationTimeout(3000);
        config.setPoolName("target-conn-" + connectionId);
        log.info("创建目标库连接池 target-conn-{} ({}:{})", connectionId, conn.getHost(), conn.getPort());
        return new HikariDataSource(config);
    }

    /**
     * 读取指定连接的查询超时配置（秒），连接不存在或字段为空时回落 60。
     */
    public int getQueryTimeoutSeconds(Long connectionId) {
        return connectionRepository.findById(connectionId)
                .map(DbConnection::getQueryTimeoutSeconds)
                .filter(timeout -> timeout != null && timeout > 0)
                .orElse(60);
    }

    /**
     * 用解密的连接配置创建一个不走连接池的裸 JDBC 连接（用于 KILL 取消等独立操作），
     * 调用方负责关闭返回的连接。
     */
    public java.sql.Connection createBareConnection(Long connectionId) throws SQLException {
        DbConnection conn = connectionRepository.findById(connectionId)
                .orElseThrow(() -> new NotFoundException("连接不存在: " + connectionId));
        String plainPassword = aesEncryptor.decrypt(conn.getPasswordEnc());
        Properties props = new Properties();
        props.setProperty("user", conn.getUsername() == null ? "" : conn.getUsername());
        props.setProperty("password", plainPassword == null ? "" : plainPassword);
        return DriverManager.getConnection(
                buildUrl(conn.getHost(), conn.getPort(), conn.getDatabaseName()), props);
    }

    /**
     * 使用给定配置直接测试连接（不经过连接池缓存），返回耗时与结果。
     */
    public TestResultResponse testConnection(String host, Integer port, String username,
                                             String plainPassword, String database) {
        long start = System.currentTimeMillis();
        String url = buildUrl(host, port, database) + "&connectTimeout=5000&socketTimeout=10000";
        Properties props = new Properties();
        props.setProperty("user", username == null ? "" : username);
        props.setProperty("password", plainPassword == null ? "" : plainPassword);
        try (java.sql.Connection con = DriverManager.getConnection(url, props);
             Statement stmt = con.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT 1")) {
            rs.next();
            return new TestResultResponse(true, "连接成功", System.currentTimeMillis() - start);
        } catch (Exception e) {
            return new TestResultResponse(false, rootMessage(e), System.currentTimeMillis() - start);
        }
    }

    public static String buildUrl(String host, Integer port, String database) {
        StringBuilder sb = new StringBuilder("jdbc:mysql://")
                .append(host).append(':').append(port).append('/');
        if (database != null && !database.isBlank()) {
            sb.append(database);
        }
        return sb.append(URL_PARAMS).toString();
    }

    private static String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        String message = current.getMessage();
        return message != null ? message : throwable.toString();
    }
}
