//This class was heavily influenced by the EnderChestMod class of the original EnderChest mod by 01Kvothe10

package me.archmon;

import com.hypixel.hytale.server.core.command.system.CommandRegistry;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import me.archmon.commands.EnderStorageModVersion;
import org.jspecify.annotations.NonNull;

public class EnderStoragePlugin extends JavaPlugin {

    private static EnderStoragePlugin instance;

    public EnderStoragePlugin(@NonNull JavaPluginInit init) {
        super(init);
    }

    protected void setup(){
        //Initialize commands
        CommandRegistry commandRegistry = this.getCommandRegistry();
        commandRegistry.registerCommand(new EnderStorageModVersion(this.getManifest().getVersion().toString()));




    }

    /*The following was heavily influenced by the EnderChestMod class from original EnderChest mod by 01Kvothe10*/
    public static void onFirstTick() {
        if (instance != null) {
            instance.handleCraftingConfig();
        }
    }

    private void handleCraftingConfig() {

    }
}
