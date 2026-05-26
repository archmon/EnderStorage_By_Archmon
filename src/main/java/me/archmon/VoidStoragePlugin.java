package me.archmon;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.hypixel.hytale.server.core.command.system.CommandRegistry;
import com.hypixel.hytale.server.core.permissions.PermissionsModule;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import me.archmon.commands.VoidStorageBugReport;
import me.archmon.commands.VoidStorageModVersion;
import org.jspecify.annotations.NonNull;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.*;
import java.util.Map;


public class VoidStoragePlugin extends JavaPlugin {

    private static VoidStoragePlugin instance;
    private static VoidStorageManager manager;

    public VoidStoragePlugin(@NonNull JavaPluginInit init) {
        super(init);
    }

    protected void setup(){
        super.setup();
        instance = this;
        this.registerPermissions();
        this.extractReadme();
        Path modFolder = this.findModFolder();
        if (!Files.exists(modFolder, new LinkOption[0])) {
            try {
                Files.createDirectories(modFolder);
            } catch (Exception _){
            }
        }

        //Initialize commands
        CommandRegistry commandRegistry = this.getCommandRegistry();
        commandRegistry.registerCommand(new VoidStorageModVersion(this.getManifest().getVersion().toString()));
        commandRegistry.registerCommand(new VoidStorageBugReport());

        //initialize the Void storage systems
        JsonObject configFile = this.loadConfig();
        manager = new VoidStorageManager(configFile);
        VoidStorageTickSystem voidStorageTickSystem = new VoidStorageTickSystem(manager);
        manager.setTickSystem(voidStorageTickSystem);
        this.getEntityStoreRegistry().registerSystem(voidStorageTickSystem);
        this.getEntityStoreRegistry().registerSystem(new VoidStorageUseBlockSystem(manager));
        this.getEntityStoreRegistry().registerSystem(new VoidStoragePlaceBlockSystem(manager));
        this.getEntityStoreRegistry().registerSystem(new VoidStorageDamageBlockSystem(manager));
        this.getEntityStoreRegistry().registerSystem(new VoidStorageBreakBlockSystem(manager));

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            if (manager != null) {
                manager.saveAll();
            }
        }));
    }

    private void registerPermissions() {
        try {
            PermissionsModule.registerPermission("voidstorage.admin", "hytale:Admin");
            PermissionsModule.registerPermission("voidstorage.safe.bypass", "hytale:Admin");

            PermissionsModule permissionsModule = PermissionsModule.get();

            if (permissionsModule != null) {
                permissionsModule.refreshVirtualGroups();
            }
        } catch (Exception errCatch) {
            System.err.println("[VoidStorage] Failed to register permissions: " + errCatch.getMessage());
        }
    }



    private void extractReadme() {
        try {
            Path modFolder = this.findModFolder();

            Path readMeFile = modFolder.resolve("README.txt");
            if (Files.exists(readMeFile, new LinkOption[0]) && Files.size(readMeFile) > 0L) {
                return;
            }

            try (InputStream inputStream = this.getClass().getResourceAsStream("/README.txt")) {
                if (inputStream != null){
                    if (!Files.exists(modFolder, new LinkOption[0])) {
                        Files.createDirectories(modFolder);
                    }

                    Files.copy(inputStream, readMeFile, new CopyOption[0]);
                }
            }
        } catch (Exception err){
            System.err.println("[VoidStorage] Failed to extract README.txt: " + err.getMessage());
        }
    }

    // Allows other plugins to access only the supported VoidStorage API surface.
    /*public static VoidStorageApi getVoidStorageManager() {
        return manager;
    }*/

    private JsonObject loadConfig() {
        try {
            Path modFolder = this.findModFolder();
            Path configFile = modFolder.resolve("config.json");
            if (!Files.exists(configFile, new LinkOption[0])){
                return this.createDefaultConfig(configFile);
            } else {
                String readConfigFile = Files.readString(configFile);
                JsonObject configJson = JsonParser.parseString(readConfigFile).getAsJsonObject();
                boolean configInput = false;
                if (!configJson.has("enableCrafting_pocket_DimensionSafe")){
                    configJson.addProperty("enableCrafting_pocket_DimensionSafe", true);
                    configInput = true;
                }

                if (!configJson.has("enableCrafting_VoidChest")){
                    configJson.addProperty("enableCrafting_VoidChest", true);
                    configInput = true;
                }

                if (!configJson.has("enableCrafting_VoidWrench")){
                    configJson.addProperty("enableCrafting_VoidWrench", true);
                    configInput = true;
                }

                if (!configJson.has("database")){
                    configJson.add("database", this.createDefaultDbConfig());
                    configInput = true;
                }

                if(configInput) {
                    Files.writeString(configFile, (new GsonBuilder()).setPrettyPrinting().create().toJson(configJson));
                }

                return configJson;
            }
        }catch (Exception err){
            return this.createDefaultDbConfig();
        }

    }

    private JsonObject createDefaultConfig(Path configFile) throws IOException {
        JsonObject configJson = new JsonObject();
        configJson.addProperty("enableCrafting_pocket_DimensionSafe", true);
        configJson.addProperty("enableCrafting_VoidChest", true);
        configJson.addProperty("enableCrafting_VoidWrench", true);
        configJson.add("database", this.createDefaultDbConfig());
        Files.createDirectories(configFile.getParent());
        Files.writeString(configFile, (new GsonBuilder()).setPrettyPrinting().create().toJson(configJson));
        return configJson;
    }

    private JsonObject createDefaultDbConfig() {
        JsonObject configJson = new JsonObject();
        configJson.addProperty("type", "sqlite");
        configJson.addProperty("host", "localhost");
        configJson.addProperty("port", 5433);
        configJson.addProperty("name", "VoidStorage_By_Archmon");
        configJson.addProperty("user", "postgres");
        configJson.addProperty("password","password");
        return configJson;
    }

    //There is a method of the same name above that has an input of a path variable
    private JsonObject createDefaultConfig() {
        JsonObject configJson = new JsonObject();
        configJson.addProperty("_comment", "VoidStorage Configuration");
        configJson.addProperty("enableCrafting_pocket_DimensionSafe", true);
        configJson.addProperty("enableCrafting_VoidChest", true);
        configJson.addProperty("enableCrafting_VoidWrench", true);
        configJson.add("database", this.createDefaultDbConfig());
        return configJson;
    }

    //Called from onFirstTick() so crafting registries are available before recipes are modified.
    private void handleCraftingConfig() {
        JsonObject configJson = this.loadConfig();
        boolean craftingBoolean;
        boolean craftingBoolean2;
        boolean craftingBoolean3;
        if (configJson.has("enableCrafting_pocket_DimensionSafe")) {
            craftingBoolean = configJson.get("enableCrafting_pocket_DimensionSafe").getAsBoolean();
        } else {
            craftingBoolean = true;
        }
        if (configJson.has("enableCrafting_VoidChest")) {
            craftingBoolean2 = configJson.get("enableCrafting_VoidChest").getAsBoolean();
        } else {
            craftingBoolean2 = true;
        }
        if (configJson.has("enableCrafting_VoidWrench")) {
            craftingBoolean3 = configJson.get("enableCrafting_VoidWrench").getAsBoolean();
        } else {
            craftingBoolean3 = true;
        }

        if (!craftingBoolean){
            this.removeRecipe("pocket_DimensionSafe");
        }
        if (!craftingBoolean2){
            this.removeRecipe("VoidChest");
        }
        if (!craftingBoolean3){
            this.removeRecipe("VoidWrench");
        }
    }

    public static void onFirstTick() {
        if (instance != null) {
            instance.handleCraftingConfig();
        }
    }


    private void removeRecipe(String blockName) {
        try {
            @SuppressWarnings("rawtypes") Class craftingPluginClass = Class.forName("com.hypixel.hytale.builtin.crafting.CraftingPlugin");
            @SuppressWarnings("unchecked") Method getCrafting = craftingPluginClass.getMethod("get");
            Object craftingObject = getCrafting.invoke((Object)null);
            Field registriesField = craftingPluginClass.getDeclaredField("registries");
            registriesField.setAccessible(true);
            @SuppressWarnings("rawtypes") Map craftingMap = (Map)registriesField.get(craftingObject);
            if (craftingMap == null || !craftingMap.containsKey("Furniture_Bench")){//Bench_WorkBench or Bench_Furniture
                return;
            }

            Object workbenchObject = craftingMap.get("Furniture_Bench");
            @SuppressWarnings("rawtypes") Class benchRecipeRegistryClass = Class.forName("com.hypixel.hytale.builtin.crafting.BenchRecipeRegistry");
            @SuppressWarnings("unchecked") Method getAllRecipesMethod = benchRecipeRegistryClass.getMethod("getAllRecipes");
            Object[] recipesArray = (Object[]) getAllRecipesMethod.invoke(workbenchObject);
            String removedRecipe = null;

            for (Object recipe : recipesArray) {
                Field primaryOutputField = recipe.getClass().getDeclaredField("primaryOutput");
                primaryOutputField.setAccessible(true);
                Object recipe2 = primaryOutputField.get(recipe);
                if (recipe2 != null) {
                    Field itemIdField = recipe2.getClass().getDeclaredField("itemId");
                    itemIdField.setAccessible(true);
                    String recipeString = (String)itemIdField.get(recipe2);
                    if (blockName.equals(recipeString)){
                        Field idField = recipe.getClass().getDeclaredField("id");
                        idField.setAccessible(true);
                        removedRecipe = (String)idField.get(recipe);
                        break;
                    }
                }
            }

            if (removedRecipe != null) {
                @SuppressWarnings("unchecked") Method removedRecipeMethod = benchRecipeRegistryClass.getMethod("removeRecipe", String.class);
                removedRecipeMethod.invoke(workbenchObject, removedRecipe);
            }
        } catch (Exception err) {
            System.err.println("[VoidStorage] Failed to remove recipe for " + blockName + ": " + err.getMessage());
        }
    }

    private Path findModFolder() {
        return Paths.get("mods/archmon_VoidStorage");
    }


}
