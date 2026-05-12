package me.archmon.systems;

import com.google.gson.JsonObject;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

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
                String databaseFileName; //move this up to the constructor for use with both the Safe and chest.

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
                    port = 5432;
                }

                if (jsonObject.has("name")) {
                    databaseFileName = jsonObject.get("name").getAsString();
                } else {
                    databaseFileName = "enderSafe";
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

                File fileLocation = new File("mods/archmon_EnderSafe-Chest");
                if (!fileLocation.exists()){
                    fileLocation.mkdirs(); //if folder doesn't exist, make it.
                }

                String url2 = "jdbc:sqlite:mods/archmon_EnderSafe-Chest/endersafe.db"; //will have to rework this for use with both endersafe and enderchest
                this.connection = DriverManager.getConnection(url2);
            }

            this.createTables();
        }
    }

    private void createTables() throws SQLException {

    }




}
