package me.archmon;

import com.hypixel.hytale.server.core.command.system.CommandRegistry;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import me.archmon.commands.EnderStorageModVersion;
import org.jspecify.annotations.NonNull;

public class EnderStoragePlugin extends JavaPlugin {

    public EnderStoragePlugin(@NonNull JavaPluginInit init) {
        super(init);
    }

    protected void setup(){
        //Initialize everything
        CommandRegistry commandRegistry = this.getCommandRegistry();
        commandRegistry.registerCommand(new EnderStorageModVersion(this.getManifest().getVersion().toString()));
    }
}
