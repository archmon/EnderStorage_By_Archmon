//This class was heavily influenced by the DatabaseHandler class of the original EnderChest mod by 01Kvothe10


package me.archmon.systems;

import com.google.gson.JsonObject;

import java.io.File;
import java.sql.*;
import java.util.UUID;

public class DatabaseHandler {
    private Connection connection;
    private final JsonObject config;
    private String dbType;


    public DatabaseHandler(JsonObject jsonObject) {
        if (jsonObject != null) {
            //if not null, use the jsonObject
            this.config = jsonObject;
        } else {
            //else make a new default copy
            this.config = new JsonObject();
        }
    }

    public synchronized void dbConnect() throws SQLException {
        if (this.connection == null || this.connection.isClosed()) {

            JsonObject jsonObject;

            if (this.config.has("database")) {
                //if "database" key exists, get the sub-object
                jsonObject = this.config.getAsJsonObject("database");
            } else {
                //or use the original config
                jsonObject = this.config;
            }

            if (jsonObject.has("type")){
                this.dbType = jsonObject.get("type").getAsString().toLowerCase();
            }else {
                this.dbType = "sqlite";
            }

            if ("postgresql".equals(this.dbType)){

                String user;
                String password;
                String host;
                int port;
                String databaseFileName; //move this up to the constructor for use with both the Safe and chest. maybe.

                if (jsonObject.has("user")){
                    user = jsonObject.get("user").getAsString();
                } else {
                    user = "";
                }

                if (jsonObject.has("password")){
                    password = jsonObject.get("password").getAsString();
                } else {
                    password = "";
                }

                if (jsonObject.has("host")){
                    host = jsonObject.get("host").getAsString();
                } else {
                    host = "localhost";
                }

                if (jsonObject.has("port")){
                    port = jsonObject.get("port").getAsInt();
                } else {
                    port = 5433;
                }

                if (jsonObject.has("name")) {
                    databaseFileName = jsonObject.get("name").getAsString();
                } else {
                    databaseFileName = "EnderStorage_By_Archmon";
                }

                try {
                    Class.forName("org.postgresql.Driver");
                } catch (ClassNotFoundException errorClassNotFound) {
                    throw new SQLException("PostgreSQL JDBC Driver not found! postgresql is sqlite!", errorClassNotFound);
                }

                String url = String.format("jdbc:postgresql://%s:%d/%s", host, port, databaseFileName);
                this.connection = DriverManager.getConnection(url, user, password);

            } else {

                try {
                    Class.forName("org.sqlite.JDBC");
                } catch (ClassNotFoundException ErrorClassNotFound2) {
                    throw new SQLException("SQLite JDBC Diver not found! postgresql != sqlite! ", ErrorClassNotFound2);
                }

                File fileLocation = new File("mods/archmon_EnderStorage");
                if (!fileLocation.exists()){
                    fileLocation.mkdirs(); //if folder doesn't exist, make it.
                }

                String url2 = "jdbc:sqlite:mods/archmon_EnderStorage/enderStorage_By_Archmon.db";
                this.connection = DriverManager.getConnection(url2);
            }

            this.createTables();
        }
    }

    private void createTables() throws SQLException {

        String sqlStatement;
        if ("postgresql".equals(this.dbType)){
            sqlStatement = "CREATE TABLE IF NOT EXISTS enderSafe (uuid VARCHAR(36) PRIMARY KEY, inventory_data TEXT NOT NULL, last_updated TIMESTAMP DEFAULT CURRENT_TIMESTAMP);";
        }  else {
            sqlStatement = "CREATE TABLE IF NOT EXISTS enderSafe (uuid TEXT PRIMARY KEY, inventory_data TEXT NOT NULL, last_updated TIMESTAMP DEFAULT CURRENT_TIMESTAMP);";
        }

        try (Statement tableCreation = this.connection.createStatement()){
            tableCreation.execute(sqlStatement);
        }

    }

    public synchronized String getInventory(UUID uuid) throws SQLException {

        if (this.connection == null) {
            return null;

        } else {

            String sqlString = "SELECT inventory_data FROM enderSafe WHERE uuid = ?;";

            try (PreparedStatement sqlStatement = this.connection.prepareStatement(sqlString)) {
                sqlStatement.setString(1, uuid.toString());//if wondering why 1, look at setString

                try (ResultSet resultSet = sqlStatement.executeQuery()) {
                    if (resultSet.next()) {
                        return resultSet.getString("inventory_data");
                    }
                }

            }

            return null;

        }
    }

    public synchronized void saveInventory(UUID uuid, String Inventory_Data_String_Json) throws SQLException {

        if (this.connection == null) {
            throw new SQLException(("Not connected to database"));

        } else {

            String sqlStatement;
            if ("postgresql".equals(this.dbType)) {
                sqlStatement = "INSERT INTO enderSafe (uuid, inventory_data, last_updated) VALUES (?, ?, CURRENT_TIMESTAMP) ON CONFLICT (uuid) DO UPDATE SET inventory_data = EXCLUDED.inventory_data, last_updated = CURRENT_TIMESTAMP;";
            } else {
                sqlStatement = "INSERT INTO enderSafe (uuid, inventory_data, last_updated) VALUES (?, ?, CURRENT_TIMESTAMP) ON CONFLICT(uuid) DO UPDATE SET inventory_data = excluded.inventory_data, last_updated = CURRENT_TIMESTAMP;";
            }

            try (PreparedStatement preparedSQLStatement = this.connection.prepareStatement(sqlStatement)){
                preparedSQLStatement.setString(1, uuid.toString());
                preparedSQLStatement.setString(2, Inventory_Data_String_Json);
                preparedSQLStatement.executeUpdate();
            }
        }
    }

    public synchronized void close() {
        try {
            if (this.connection != null && !this.connection.isClosed()) {
                this.connection.close();
            }
        } catch (SQLException errorClose) {
            errorClose.printStackTrace();
        }
    }

    public boolean isConnected() {
        try {
            return this.connection != null && !this.connection.isClosed();
        } catch (SQLException connectionError) {
            return false;
        }
    }
}
