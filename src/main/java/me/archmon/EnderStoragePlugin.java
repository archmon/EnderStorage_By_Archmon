//This class was heavily influenced by the EnderChestMod class of the original EnderChest mod by 01Kvothe10

package me.archmon;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.hypixel.hytale.server.core.command.system.CommandRegistry;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import me.archmon.commands.EnderStorageBugReport;
import me.archmon.commands.EnderStorageModVersion;
import org.jspecify.annotations.NonNull;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.*;
import java.util.Map;


public class EnderStoragePlugin extends JavaPlugin {

    private static EnderStoragePlugin instance;
    private static EnderStorageManager manager;

    public EnderStoragePlugin(@NonNull JavaPluginInit init) {
        super(init);
    }

    protected void setup(){
        super.setup();
        instance = this;
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
        commandRegistry.registerCommand(new EnderStorageModVersion(this.getManifest().getVersion().toString()));
        commandRegistry.registerCommand(new EnderStorageBugReport());

        //initialize the Ender storage systems
        JsonObject configFile = this.loadConfig();
        manager = new EnderStorageManager(configFile);
        EnderStorageTickSystem enderStorageTickSystem = new EnderStorageTickSystem(manager);
        manager.setTickSystem(enderStorageTickSystem);
        this.getEntityStoreRegistry().registerSystem(enderStorageTickSystem);
        this.getEntityStoreRegistry().registerSystem(new EnderStorageUseBlockSystem(manager));

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            if (manager != null) {
                manager.saveAll();
            }
        }));
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
            //noinspection CallToPrintStackTrace
            err.printStackTrace();
        }
    }

    //Allows other plugins to access the EnderStorageManager, which is needed for the EnderStorage API.
    public static EnderStorageManager getEnderStorageManager() {
        return manager;
    }

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
                if (!configJson.has("enableCrafting_EnderSafe")){
                    configJson.addProperty("enableCrafting_EnderSafe", true);
                    configInput = true;
                }

                if (!configJson.has("enableCrafting_Ender_Chest")){
                    configJson.addProperty("enableCrafting_Ender_Chest", true);
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
        configJson.addProperty("enableCrafting_EnderSafe", true);
        configJson.addProperty("enableCrafting_Ender_Chest", true);
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
        configJson.addProperty("name", "EnderStorage_By_Archmon");
        configJson.addProperty("user", "postgres");
        configJson.addProperty("password","password");
        return configJson;
    }

    //There is a method of the same name above that has an input of a path variable
    private JsonObject createDefaultConfig() {
        JsonObject configJson = new JsonObject();
        configJson.addProperty("_comment", "EnderStorage Configuration");
        configJson.addProperty("enableCrafting_EnderSafe", true);
        configJson.addProperty("enableCrafting_Ender_Chest", true);
        configJson.add("database", this.createDefaultDbConfig());
        return configJson;
    }

    //Called from onFirstTick() so crafting registries are available before recipes are modified.
    private void handleCraftingConfig() {
        JsonObject configJson = this.loadConfig();
        boolean craftingBoolean;
        boolean craftingBoolean2;
        if (configJson.has("enableCrafting_EnderSafe")) {
            craftingBoolean = configJson.get("enableCrafting_EnderSafe").getAsBoolean();
        } else {
            craftingBoolean = true;
        }
        if (configJson.has("enableCrafting_Ender_Chest")) {
            craftingBoolean2 = configJson.get("enableCrafting_Ender_Chest").getAsBoolean();
        } else {
            craftingBoolean2 = true;
        }

        if (!craftingBoolean){
            this.removeEnderSafeRecipe("EnderSafe");
        }
        if (!craftingBoolean2){
            this.removeEnderSafeRecipe("Ender_Chest");
        }
    }

    public static void onFirstTick() {
        if (instance != null) {
            instance.handleCraftingConfig();
        }
    }


    private void removeEnderSafeRecipe(String blockName) {
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
            //noinspection CallToPrintStackTrace
            err.printStackTrace();
        }
    }

    private Path findModFolder() {
        return Paths.get("mods/archmon_EnderStorage");
    }


}
