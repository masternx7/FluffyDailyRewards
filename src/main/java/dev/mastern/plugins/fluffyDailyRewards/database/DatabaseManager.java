package dev.mastern.plugins.fluffyDailyRewards.database;

import dev.mastern.plugins.fluffyDailyRewards.FluffyDailyRewards;
import dev.mastern.plugins.fluffyDailyRewards.models.PlayerData;

import java.io.File;
import java.sql.*;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class DatabaseManager {
    
    private final FluffyDailyRewards plugin;
    private Connection connection;
    private final String type;
    private final String tableName;
    
    public DatabaseManager(FluffyDailyRewards plugin) {
        this.plugin = plugin;
        this.type = plugin.getConfig().getString("database.type", "sqlite");
        
        if (type.equalsIgnoreCase("mysql")) {
            this.tableName = plugin.getConfig().getString("database.mysql.table", "fdrewards_data");
        } else {
            this.tableName = "player_data";
        }
    }
    
    public void connect() {
        try {
            if (type.equalsIgnoreCase("mysql")) {
                connectMySQL();
            } else {
                connectSQLite();
            }
            createTables();
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to connect to database: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    private void connectSQLite() throws SQLException {
        File dataFolder = plugin.getDataFolder();
        if (!dataFolder.exists()) {
            dataFolder.mkdirs();
        }
        
        String url = "jdbc:sqlite:" + dataFolder + File.separator + "player_data.db";
        connection = DriverManager.getConnection(url);
        plugin.getLogger().info("Connected to SQLite database");
    }
    
    private void connectMySQL() throws SQLException {
        String host = plugin.getConfig().getString("database.mysql.host");
        int port = plugin.getConfig().getInt("database.mysql.port");
        String database = plugin.getConfig().getString("database.mysql.database");
        String username = plugin.getConfig().getString("database.mysql.username");
        String password = plugin.getConfig().getString("database.mysql.password");
        boolean useSSL = plugin.getConfig().getBoolean("database.mysql.properties.useSSL", false);
        boolean autoReconnect = plugin.getConfig().getBoolean("database.mysql.properties.autoReconnect", true);
        
        String url = String.format("jdbc:mysql://%s:%d/%s?useSSL=%s&autoReconnect=%s",
                host, port, database, useSSL, autoReconnect);
        
        connection = DriverManager.getConnection(url, username, password);
        plugin.getLogger().info("Connected to MySQL database");
    }
    
    private void createTables() throws SQLException {
        String sql = "CREATE TABLE IF NOT EXISTS " + tableName + " (" +
                "uuid VARCHAR(36) PRIMARY KEY, " +
                "player_name VARCHAR(16), " +
                "current_day INTEGER DEFAULT 1, " +
                "total_claims INTEGER DEFAULT 0, " +
                "last_claim_time BIGINT DEFAULT 0, " +
                "streak INTEGER DEFAULT 0" +
                ")";
        
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate(sql);
        }
    }
    
    public CompletableFuture<PlayerData> getPlayerData(UUID uuid) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT * FROM " + tableName + " WHERE uuid = ?";
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, uuid.toString());
                ResultSet rs = statement.executeQuery();
                
                if (rs.next()) {
                    return new PlayerData(
                            uuid,
                            rs.getString("player_name"),
                            rs.getInt("current_day"),
                            rs.getInt("total_claims"),
                            rs.getLong("last_claim_time"),
                            rs.getInt("streak")
                    );
                } else {
                    return new PlayerData(uuid, null, 1, 0, 0, 0);
                }
            } catch (SQLException e) {
                plugin.getLogger().severe("Error loading player data: " + e.getMessage());
                return new PlayerData(uuid, null, 1, 0, 0, 0);
            }
        });
    }
    
    public CompletableFuture<Void> savePlayerData(PlayerData data) {
        return CompletableFuture.runAsync(() -> {
            String sql = "INSERT OR REPLACE INTO " + tableName + " (uuid, player_name, current_day, total_claims, last_claim_time, streak) " +
                    "VALUES (?, ?, ?, ?, ?, ?)";
            
            if (type.equalsIgnoreCase("mysql")) {
                sql = "INSERT INTO " + tableName + " (uuid, player_name, current_day, total_claims, last_claim_time, streak) " +
                        "VALUES (?, ?, ?, ?, ?, ?) " +
                        "ON DUPLICATE KEY UPDATE " +
                        "player_name = VALUES(player_name), " +
                        "current_day = VALUES(current_day), " +
                        "total_claims = VALUES(total_claims), " +
                        "last_claim_time = VALUES(last_claim_time), " +
                        "streak = VALUES(streak)";
            }
            
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, data.getUuid().toString());
                statement.setString(2, data.getPlayerName());
                statement.setInt(3, data.getCurrentDay());
                statement.setInt(4, data.getTotalClaims());
                statement.setLong(5, data.getLastClaimTime());
                statement.setInt(6, data.getStreak());
                statement.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().severe("Error saving player data: " + e.getMessage());
            }
        });
    }
    
    public void disconnect() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
                plugin.getLogger().info("Disconnected from database");
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Error closing database connection: " + e.getMessage());
        }
    }
    
    public boolean isConnected() {
        try {
            return connection != null && !connection.isClosed();
        } catch (SQLException e) {
            return false;
        }
    }
}
