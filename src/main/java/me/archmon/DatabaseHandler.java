package me.archmon;

import com.google.gson.JsonObject;

import java.io.File;
import java.sql.*;
import java.util.UUID;

class DatabaseHandler {
    private static final String VOID_CHEST_TABLE = "void_chest";
    private static final String VOID_CHEST_BLOCK_TABLE = "void_chest_block";
    private static final String POCKET_DIMENSION_SAFE_TABLE = "pocket_dimension_safe";

    private Connection connection;
    private final JsonObject config;
    private String dbType;

    DatabaseHandler(JsonObject jsonObject) {
        if (jsonObject != null) {
            this.config = jsonObject;
        } else {
            this.config = new JsonObject();
        }
    }

    synchronized void dbConnect() throws SQLException {
        if (this.connection == null || this.connection.isClosed()) {
            JsonObject jsonObject;

            if (this.config.has("database")) {
                jsonObject = this.config.getAsJsonObject("database");
            } else {
                jsonObject = this.config;
            }

            if (jsonObject.has("type")) {
                this.dbType = jsonObject.get("type").getAsString().toLowerCase();
            } else {
                this.dbType = "sqlite";
            }

            if ("postgresql".equals(this.dbType)) {
                String user = jsonObject.has("user") ? jsonObject.get("user").getAsString() : "";
                String password = jsonObject.has("password") ? jsonObject.get("password").getAsString() : "";
                String host = jsonObject.has("host") ? jsonObject.get("host").getAsString() : "localhost";
                int port = jsonObject.has("port") ? jsonObject.get("port").getAsInt() : 5433;
                String databaseName = jsonObject.has("name") ? jsonObject.get("name").getAsString() : "VoidStorage_By_Archmon";

                try {
                    Class.forName("org.postgresql.Driver");
                } catch (ClassNotFoundException errorClassNotFound) {
                    throw new SQLException("PostgreSQL JDBC Driver not found.", errorClassNotFound);
                }

                String url = String.format("jdbc:postgresql://%s:%d/%s", host, port, databaseName);
                this.connection = DriverManager.getConnection(url, user, password);
            } else {
                try {
                    Class.forName("org.sqlite.JDBC");
                } catch (ClassNotFoundException errorClassNotFound) {
                    throw new SQLException("SQLite JDBC Driver not found.", errorClassNotFound);
                }

                File fileLocation = new File("mods/archmon_voidStorage");
                if (!fileLocation.exists()) {
                    //noinspection ResultOfMethodCallIgnored
                    fileLocation.mkdirs();
                }

                String url = "jdbc:sqlite:mods/archmon_voidStorage/voidStorage_By_Archmon.db";
                this.connection = DriverManager.getConnection(url);
            }

            this.createTables();
        }
    }

    private void createTables() throws SQLException {
        String createVoidChestTable;
        String createVoidChestBlockTable;
        String createPocketDimensionSafeTable;

        if ("postgresql".equals(this.dbType)) {
            createVoidChestTable = """
                    CREATE TABLE IF NOT EXISTS void_chest (
                        network_key VARCHAR(192) PRIMARY KEY,
                        color_code VARCHAR(16) NOT NULL,
                        player_uuid VARCHAR(36) NULL,
                        inventory_data TEXT NOT NULL,
                        last_accessed TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                    );
                    """;

            createVoidChestBlockTable = """
                    CREATE TABLE IF NOT EXISTS void_chest_block (
                        location_key VARCHAR(128) PRIMARY KEY,
                        color_code VARCHAR(16) NOT NULL,
                        owner_uuid VARCHAR(36) NULL,
                        owner_name VARCHAR(64) NULL,
                        last_updated TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                    );
                    """;

            createPocketDimensionSafeTable = """
                    CREATE TABLE IF NOT EXISTS pocket_dimension_safe (
                        location_key VARCHAR(128) PRIMARY KEY,
                        owner_uuid VARCHAR(36) NOT NULL,
                        inventory_data TEXT NOT NULL,
                        last_accessed TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                    );
                    """;
        } else {
            createVoidChestTable = """
                    CREATE TABLE IF NOT EXISTS void_chest (
                        network_key TEXT PRIMARY KEY,
                        color_code TEXT NOT NULL,
                        player_uuid TEXT NULL,
                        inventory_data TEXT NOT NULL,
                        last_accessed TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                    );
                    """;

            createVoidChestBlockTable = """
                    CREATE TABLE IF NOT EXISTS void_chest_block (
                        location_key TEXT PRIMARY KEY,
                        color_code TEXT NOT NULL,
                        owner_uuid TEXT NULL,
                        owner_name TEXT NULL,
                        last_updated TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                    );
                    """;

            createPocketDimensionSafeTable = """
                    CREATE TABLE IF NOT EXISTS pocket_dimension_safe (
                        location_key TEXT PRIMARY KEY,
                        owner_uuid TEXT NOT NULL,
                        inventory_data TEXT NOT NULL,
                        last_accessed TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                    );
                    """;
        }

        try (Statement statement = this.connection.createStatement()) {
            statement.execute(createVoidChestTable);
            statement.execute(createVoidChestBlockTable);
            statement.execute(createPocketDimensionSafeTable);
        }

        this.ensureVoidChestBlockOwnerNameColumn();
    }

    synchronized String getVoidChestInventory(String colorCode, UUID playerUuid) throws SQLException {
        this.requireConnection();

        String networkKey = this.createVoidChestNetworkKey(colorCode, playerUuid);
        String sql = "SELECT inventory_data FROM " + VOID_CHEST_TABLE + " WHERE network_key = ?;";

        try (PreparedStatement statement = this.connection.prepareStatement(sql)) {
            statement.setString(1, networkKey);

            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    this.touchVoidChest(networkKey);
                    return resultSet.getString("inventory_data");
                }
            }
        }

        return null;
    }

    synchronized void saveVoidChestInventory(String colorCode, UUID playerUuid, String inventoryData) throws SQLException {
        this.requireConnection();

        String networkKey = this.createVoidChestNetworkKey(colorCode, playerUuid);

        String sql;
        if ("postgresql".equals(this.dbType)) {
            sql = """
                    INSERT INTO void_chest (network_key, color_code, player_uuid, inventory_data, last_accessed)
                    VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP)
                    ON CONFLICT (network_key)
                    DO UPDATE SET
                        color_code = EXCLUDED.color_code,
                        player_uuid = EXCLUDED.player_uuid,
                        inventory_data = EXCLUDED.inventory_data,
                        last_accessed = CURRENT_TIMESTAMP;
                    """;
        } else {
            sql = """
                    INSERT INTO void_chest (network_key, color_code, player_uuid, inventory_data, last_accessed)
                    VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP)
                    ON CONFLICT(network_key)
                    DO UPDATE SET
                        color_code = excluded.color_code,
                        player_uuid = excluded.player_uuid,
                        inventory_data = excluded.inventory_data,
                        last_accessed = CURRENT_TIMESTAMP;
                    """;
        }

        try (PreparedStatement statement = this.connection.prepareStatement(sql)) {
            statement.setString(1, networkKey);
            statement.setString(2, colorCode);

            if (playerUuid == null) {
                statement.setNull(3, Types.VARCHAR);
            } else {
                statement.setString(3, playerUuid.toString());
            }

            statement.setString(4, inventoryData);
            statement.executeUpdate();
        }
    }

    synchronized VoidChestBlockConfig getVoidChestBlockConfig(String locationKey) throws SQLException {
        this.requireConnection();

        String sql = "SELECT color_code, owner_uuid, owner_name FROM " + VOID_CHEST_BLOCK_TABLE + " WHERE location_key = ?;";

        try (PreparedStatement statement = this.connection.prepareStatement(sql)) {
            statement.setString(1, locationKey);

            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    String ownerUuidValue = resultSet.getString("owner_uuid");
                    UUID ownerUuid = ownerUuidValue == null ? null : UUID.fromString(ownerUuidValue);
                    return new VoidChestBlockConfig(resultSet.getString("color_code"), ownerUuid, resultSet.getString("owner_name"));
                }
            }
        }

        return null;
    }

    synchronized void saveVoidChestBlockConfig(String locationKey, String colorCode, UUID ownerUuid, String ownerName) throws SQLException {
        this.requireConnection();

        String sql;
        if ("postgresql".equals(this.dbType)) {
            sql = """
                    INSERT INTO void_chest_block (location_key, color_code, owner_uuid, owner_name, last_updated)
                    VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP)
                    ON CONFLICT (location_key)
                    DO UPDATE SET
                        color_code = EXCLUDED.color_code,
                        owner_uuid = EXCLUDED.owner_uuid,
                        owner_name = EXCLUDED.owner_name,
                        last_updated = CURRENT_TIMESTAMP;
                    """;
        } else {
            sql = """
                    INSERT INTO void_chest_block (location_key, color_code, owner_uuid, owner_name, last_updated)
                    VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP)
                    ON CONFLICT(location_key)
                    DO UPDATE SET
                        color_code = excluded.color_code,
                        owner_uuid = excluded.owner_uuid,
                        owner_name = excluded.owner_name,
                        last_updated = CURRENT_TIMESTAMP;
                    """;
        }

        try (PreparedStatement statement = this.connection.prepareStatement(sql)) {
            statement.setString(1, locationKey);
            statement.setString(2, colorCode);

            if (ownerUuid == null) {
                statement.setNull(3, Types.VARCHAR);
            } else {
                statement.setString(3, ownerUuid.toString());
            }

            if (ownerName == null || ownerName.isBlank()) {
                statement.setNull(4, Types.VARCHAR);
            } else {
                statement.setString(4, ownerName);
            }

            statement.executeUpdate();
        }
    }

    synchronized void deleteVoidChestBlockConfig(String locationKey) throws SQLException {
        this.requireConnection();

        String sql = "DELETE FROM " + VOID_CHEST_BLOCK_TABLE + " WHERE location_key = ?;";

        try (PreparedStatement statement = this.connection.prepareStatement(sql)) {
            statement.setString(1, locationKey);
            statement.executeUpdate();
        }
    }

    synchronized String getPocketDimensionSafeInventory(String locationKey) throws SQLException {
        this.requireConnection();

        String sql = "SELECT inventory_data FROM " + POCKET_DIMENSION_SAFE_TABLE + " WHERE location_key = ?;";

        try (PreparedStatement statement = this.connection.prepareStatement(sql)) {
            statement.setString(1, locationKey);

            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    this.touchPocketDimensionSafe(locationKey);
                    return resultSet.getString("inventory_data");
                }
            }
        }

        return null;
    }

    synchronized UUID getPocketDimensionSafeOwner(String locationKey) throws SQLException {
        this.requireConnection();

        String sql = "SELECT owner_uuid FROM " + POCKET_DIMENSION_SAFE_TABLE + " WHERE location_key = ?;";

        try (PreparedStatement statement = this.connection.prepareStatement(sql)) {
            statement.setString(1, locationKey);

            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return UUID.fromString(resultSet.getString("owner_uuid"));
                }
            }
        }

        return null;
    }

    synchronized void savePocketDimensionSafeInventory(String locationKey, UUID ownerUuid, String inventoryData) throws SQLException {
        this.requireConnection();

        String sql;
        if ("postgresql".equals(this.dbType)) {
            sql = """
                    INSERT INTO pocket_dimension_safe (location_key, owner_uuid, inventory_data, last_accessed)
                    VALUES (?, ?, ?, CURRENT_TIMESTAMP)
                    ON CONFLICT (location_key)
                    DO UPDATE SET
                        owner_uuid = EXCLUDED.owner_uuid,
                        inventory_data = EXCLUDED.inventory_data,
                        last_accessed = CURRENT_TIMESTAMP;
                    """;
        } else {
            sql = """
                    INSERT INTO pocket_dimension_safe (location_key, owner_uuid, inventory_data, last_accessed)
                    VALUES (?, ?, ?, CURRENT_TIMESTAMP)
                    ON CONFLICT(location_key)
                    DO UPDATE SET
                        owner_uuid = excluded.owner_uuid,
                        inventory_data = excluded.inventory_data,
                        last_accessed = CURRENT_TIMESTAMP;
                    """;
        }

        try (PreparedStatement statement = this.connection.prepareStatement(sql)) {
            statement.setString(1, locationKey);
            statement.setString(2, ownerUuid.toString());
            statement.setString(3, inventoryData);
            statement.executeUpdate();
        }
    }

    synchronized void deletePocketDimensionSafe(String locationKey) throws SQLException {
        this.requireConnection();

        String sql = "DELETE FROM " + POCKET_DIMENSION_SAFE_TABLE + " WHERE location_key = ?;";

        try (PreparedStatement statement = this.connection.prepareStatement(sql)) {
            statement.setString(1, locationKey);
            statement.executeUpdate();
        }
    }

    private synchronized void touchVoidChest(String networkKey) throws SQLException {
        String sql = "UPDATE " + VOID_CHEST_TABLE + " SET last_accessed = CURRENT_TIMESTAMP WHERE network_key = ?;";

        try (PreparedStatement statement = this.connection.prepareStatement(sql)) {
            statement.setString(1, networkKey);
            statement.executeUpdate();
        }
    }

    private synchronized void touchPocketDimensionSafe(String locationKey) throws SQLException {
        String sql = "UPDATE " + POCKET_DIMENSION_SAFE_TABLE + " SET last_accessed = CURRENT_TIMESTAMP WHERE location_key = ?;";

        try (PreparedStatement statement = this.connection.prepareStatement(sql)) {
            statement.setString(1, locationKey);
            statement.executeUpdate();
        }
    }

    private void ensureVoidChestBlockOwnerNameColumn() throws SQLException {
        if ("postgresql".equals(this.dbType)) {
            try (Statement statement = this.connection.createStatement()) {
                statement.execute("ALTER TABLE " + VOID_CHEST_BLOCK_TABLE + " ADD COLUMN IF NOT EXISTS owner_name VARCHAR(64) NULL;");
            }
            return;
        }

        String pragmaSql = "PRAGMA table_info(" + VOID_CHEST_BLOCK_TABLE + ");";

        try (Statement statement = this.connection.createStatement();
             ResultSet resultSet = statement.executeQuery(pragmaSql)) {
            while (resultSet.next()) {
                if ("owner_name".equalsIgnoreCase(resultSet.getString("name"))) {
                    return;
                }
            }
        }

        try (Statement statement = this.connection.createStatement()) {
            statement.execute("ALTER TABLE " + VOID_CHEST_BLOCK_TABLE + " ADD COLUMN owner_name TEXT NULL;");
        }
    }

    private void requireConnection() throws SQLException {
        if (this.connection == null || this.connection.isClosed()) {
            throw new SQLException("Not connected to database.");
        }
    }

    synchronized void close() {
        try {
            if (this.connection != null && !this.connection.isClosed()) {
                this.connection.close();
            }
        } catch (SQLException errorClose) {
            System.err.println("[VoidStorage] Failed to close database connection: " + errorClose.getMessage());
        }
    }

    boolean isConnected() {
        try {
            return this.connection != null && !this.connection.isClosed();
        } catch (SQLException connectionError) {
            return false;
        }
    }

    private String createVoidChestNetworkKey(String colorCode, UUID playerUuid) {
        if (playerUuid == null) {
            return "public:" + colorCode;
        }

        return "player:" + playerUuid + ":" + colorCode;
    }

    static class VoidChestBlockConfig {
        final String colorCode;
        final UUID ownerUuid;
        final String ownerName;

        VoidChestBlockConfig(String colorCode, UUID ownerUuid, String ownerName) {
            this.colorCode = colorCode;
            this.ownerUuid = ownerUuid;
            this.ownerName = ownerName;
        }
    }
}
