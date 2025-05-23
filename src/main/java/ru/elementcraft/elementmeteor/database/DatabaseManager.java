package ru.elementcraft.elementmeteor.database;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import ru.elementcraft.elementmeteor.ElementMeteor;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

public class DatabaseManager {

    private final ElementMeteor plugin;
    private final Logger logger;
    private HikariDataSource dataSource;

    public DatabaseManager(ElementMeteor plugin) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
    }

    public void initialize() {
        String host = plugin.getConfig().getString("database.host", "localhost");
        int port = plugin.getConfig().getInt("database.port", 3306);
        String database = plugin.getConfig().getString("database.name", "elementmeteor");
        String username = plugin.getConfig().getString("database.username", "root");
        String password = plugin.getConfig().getString("database.password", "");

        if (plugin.getSecurityManager() != null) {
            password = plugin.getSecurityManager().decrypt(password);
        }

        // hikariCP - требование тз
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:mariadb://" + host + ":" + port + "/" + database);
        config.setUsername(username);
        config.setPassword(password);
        config.setDriverClassName("org.mariadb.jdbc.Driver");

        config.setMaximumPoolSize(10);
        config.setMinimumIdle(5);
        config.setIdleTimeout(300000);
        config.setConnectionTimeout(10000);
        config.setLeakDetectionThreshold(300000);
        config.addDataSourceProperty("cachePrepStmts", "true");
        config.addDataSourceProperty("prepStmtCacheSize", "250");
        config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");

        try {
            dataSource = new HikariDataSource(config);
            createTables();
        } catch (Exception e) {
        }
    }

    private void createTables() {
        CompletableFuture.runAsync(() -> {
            try (Connection connection = getConnection();
                 Statement statement = connection.createStatement()) {

                // тз: таблица для аспекта прокачки
                statement.executeUpdate(
                    "CREATE TABLE IF NOT EXISTS ability_usage (" +
                    "id INT AUTO_INCREMENT PRIMARY KEY, " +
                    "player_uuid VARCHAR(36) NOT NULL, " +
                    "player_name VARCHAR(16) NOT NULL, " +
                    "uses INT NOT NULL DEFAULT 0, " +
                    "last_used TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP, " +
                    "UNIQUE KEY unique_player (player_uuid)" +
                    ");"
                );

                statement.executeUpdate(
                    "CREATE TABLE IF NOT EXISTS meteor_statistics (" +
                    "id INT AUTO_INCREMENT PRIMARY KEY, " +
                    "player_uuid VARCHAR(36) NOT NULL, " +
                    "usage_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
                    "radius DOUBLE NOT NULL, " +
                    "blocks_affected INT NOT NULL, " +
                    "entities_hit INT NOT NULL, " +
                    "world VARCHAR(64) NOT NULL, " +
                    "x DOUBLE NOT NULL, " +
                    "y DOUBLE NOT NULL, " +
                    "z DOUBLE NOT NULL, " +
                    "INDEX idx_player (player_uuid), " +
                    "INDEX idx_time (usage_time)" +
                    ");"
                );
            } catch (SQLException e) {
            }
        }).exceptionally(ex -> {
            return null;
        });
    }

    public Connection getConnection() throws SQLException {
        if (dataSource == null) {
            throw new SQLException("Data source is not initialized!");
        }
        return dataSource.getConnection();
    }

    public int getPlayerUses(Player player) {
        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT uses FROM ability_usage WHERE player_uuid = ?")) {

            statement.setString(1, player.getUniqueId().toString());

            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return resultSet.getInt("uses");
                }
            }

            try (PreparedStatement insertStatement = connection.prepareStatement(
                    "INSERT INTO ability_usage (player_uuid, player_name, uses) VALUES (?, ?, 0)")) {
                insertStatement.setString(1, player.getUniqueId().toString());
                insertStatement.setString(2, player.getName());
                insertStatement.executeUpdate();
            }

        } catch (SQLException e) {
        }

        return 0;
    }

    public CompletableFuture<Integer> getPlayerUsageCountAsync(UUID playerUuid) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection connection = getConnection();
                 PreparedStatement statement = connection.prepareStatement(
                         "SELECT uses FROM ability_usage WHERE player_uuid = ?")) {

                statement.setString(1, playerUuid.toString());

                try (ResultSet resultSet = statement.executeQuery()) {
                    if (resultSet.next()) {
                        return resultSet.getInt("uses");
                    }
                }

                try (PreparedStatement insertStatement = connection.prepareStatement(
                        "INSERT INTO ability_usage (player_uuid, player_name, uses) VALUES (?, ?, 0)")) {
                    insertStatement.setString(1, playerUuid.toString());
                    insertStatement.setString(2, "Unknown");
                    insertStatement.executeUpdate();
                }

            } catch (SQLException e) {
            }

            return 0;
        });
    }

    public CompletableFuture<Void> updatePlayerUsageCountAsync(UUID playerUuid, int count) {
        return CompletableFuture.runAsync(() -> {
            try (Connection connection = getConnection();
                 PreparedStatement statement = connection.prepareStatement(
                         "UPDATE ability_usage SET uses = ? WHERE player_uuid = ?")) {

                statement.setInt(1, count);
                statement.setString(2, playerUuid.toString());
                int updated = statement.executeUpdate();

                if (updated == 0) {
                    try (PreparedStatement insertStatement = connection.prepareStatement(
                            "INSERT INTO ability_usage (player_uuid, player_name, uses) VALUES (?, ?, ?)")) {
                        insertStatement.setString(1, playerUuid.toString());
                        insertStatement.setString(2, "Unknown");
                        insertStatement.setInt(3, count);
                        insertStatement.executeUpdate();
                    }
                }

            } catch (SQLException e) {
            }
        });
    }

    // главная функция прокачки - увеличение использований
    public int incrementPlayerUses(Player player) {
        try (Connection connection = getConnection()) {
            try (PreparedStatement updateStatement = connection.prepareStatement(
                    "UPDATE ability_usage SET uses = uses + 1 WHERE player_uuid = ?")) {
                updateStatement.setString(1, player.getUniqueId().toString());
                int updated = updateStatement.executeUpdate();

                if (updated == 0) {
                    try (PreparedStatement insertStatement = connection.prepareStatement(
                            "INSERT INTO ability_usage (player_uuid, player_name, uses) VALUES (?, ?, 1)")) {
                        insertStatement.setString(1, player.getUniqueId().toString());
                        insertStatement.setString(2, player.getName());
                        insertStatement.executeUpdate();
                    }
                    return 1;
                }
            }

            try (PreparedStatement getStatement = connection.prepareStatement(
                    "SELECT uses FROM ability_usage WHERE player_uuid = ?")) {
                getStatement.setString(1, player.getUniqueId().toString());
                try (ResultSet resultSet = getStatement.executeQuery()) {
                    if (resultSet.next()) {
                        return resultSet.getInt("uses");
                    }
                }
            }

        } catch (SQLException e) {
        }

        return 1;
    }

    public CompletableFuture<Integer> incrementPlayerUsesAsync(UUID playerUuid) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection connection = getConnection()) {
                try (PreparedStatement updateStatement = connection.prepareStatement(
                        "UPDATE ability_usage SET uses = uses + 1 WHERE player_uuid = ?")) {
                    updateStatement.setString(1, playerUuid.toString());
                    int updated = updateStatement.executeUpdate();

                    if (updated == 0) {
                        try (PreparedStatement insertStatement = connection.prepareStatement(
                                "INSERT INTO ability_usage (player_uuid, player_name, uses) VALUES (?, ?, 1)")) {
                            insertStatement.setString(1, playerUuid.toString());
                            insertStatement.setString(2, "Unknown");
                            insertStatement.executeUpdate();
                        }
                        return 1;
                    }
                }

                try (PreparedStatement getStatement = connection.prepareStatement(
                        "SELECT uses FROM ability_usage WHERE player_uuid = ?")) {
                    getStatement.setString(1, playerUuid.toString());
                    try (ResultSet resultSet = getStatement.executeQuery()) {
                        if (resultSet.next()) {
                            return resultSet.getInt("uses");
                        }
                    }
                }

            } catch (SQLException e) {
            }

            return 1;
        });
    }

    public void shutdown() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
        }
    }

    public void saveMeteorStatistics(Player player, double radius, int blocksAffected, int entitiesHit, Location location) {
        CompletableFuture.runAsync(() -> {
            try (Connection connection = getConnection();
                 PreparedStatement statement = connection.prepareStatement(
                         "INSERT INTO meteor_statistics (player_uuid, radius, blocks_affected, entities_hit, world, x, y, z) " +
                         "VALUES (?, ?, ?, ?, ?, ?, ?, ?)")) {

                statement.setString(1, player.getUniqueId().toString());
                statement.setDouble(2, radius);
                statement.setInt(3, blocksAffected);
                statement.setInt(4, entitiesHit);
                statement.setString(5, location.getWorld().getName());
                statement.setDouble(6, location.getX());
                statement.setDouble(7, location.getY());
                statement.setDouble(8, location.getZ());

                statement.executeUpdate();

            } catch (SQLException e) {
            }
        });
    }

    public CompletableFuture<Map<String, Object>> getServerStatisticsAsync() {
        return CompletableFuture.supplyAsync(() -> {
            Map<String, Object> stats = new HashMap<>();

            try (Connection connection = getConnection()) {
                try (PreparedStatement stmt = connection.prepareStatement(
                        "SELECT SUM(uses) as total_uses FROM ability_usage")) {
                    try (ResultSet rs = stmt.executeQuery()) {
                        if (rs.next()) {
                            stats.put("totalUses", rs.getInt("total_uses"));
                        }
                    }
                }

                try (PreparedStatement stmt = connection.prepareStatement(
                        "SELECT AVG(uses) as avg_uses FROM ability_usage")) {
                    try (ResultSet rs = stmt.executeQuery()) {
                        if (rs.next()) {
                            stats.put("avgUsesPerPlayer", rs.getDouble("avg_uses"));
                        }
                    }
                }

                try (PreparedStatement stmt = connection.prepareStatement(
                        "SELECT player_name, uses FROM ability_usage ORDER BY uses DESC LIMIT 1")) {
                    try (ResultSet rs = stmt.executeQuery()) {
                        if (rs.next()) {
                            stats.put("topPlayerName", rs.getString("player_name"));
                            stats.put("topPlayerUses", rs.getInt("uses"));
                        }
                    }
                }
            } catch (SQLException e) {
            }

            return stats;
        });
    }

    public CompletableFuture<Map<String, Object>> getPlayerStatisticsAsync(Player player) {
        return CompletableFuture.supplyAsync(() -> {
            Map<String, Object> stats = new HashMap<>();

            try (Connection connection = getConnection()) {
                try (PreparedStatement stmt = connection.prepareStatement(
                        "SELECT uses FROM ability_usage WHERE player_uuid = ?")) {
                    stmt.setString(1, player.getUniqueId().toString());
                    try (ResultSet rs = stmt.executeQuery()) {
                        if (rs.next()) {
                            stats.put("uses", rs.getInt("uses"));
                        } else {
                            stats.put("uses", 0);
                        }
                    }
                }

                try (PreparedStatement stmt = connection.prepareStatement(
                        "SELECT last_used FROM ability_usage WHERE player_uuid = ?")) {
                    stmt.setString(1, player.getUniqueId().toString());
                    try (ResultSet rs = stmt.executeQuery()) {
                        if (rs.next()) {
                            stats.put("lastUsed", rs.getTimestamp("last_used"));
                        }
                    }
                }

                try (PreparedStatement stmt = connection.prepareStatement(
                        "SELECT AVG(radius) as avg_radius, MAX(radius) as max_radius, " +
                        "COUNT(*) as total_meteors, SUM(blocks_affected) as total_blocks, " +
                        "SUM(entities_hit) as total_entities " +
                        "FROM meteor_statistics WHERE player_uuid = ?")) {
                    stmt.setString(1, player.getUniqueId().toString());
                    try (ResultSet rs = stmt.executeQuery()) {
                        if (rs.next()) {
                            stats.put("avgRadius", rs.getDouble("avg_radius"));
                            stats.put("maxRadius", rs.getDouble("max_radius"));
                            stats.put("totalMeteors", rs.getInt("total_meteors"));
                            stats.put("totalBlocksAffected", rs.getInt("total_blocks"));
                            stats.put("totalEntitiesHit", rs.getInt("total_entities"));
                        }
                    }
                }
            } catch (SQLException e) {
            }

            return stats;
        });
    }
}
