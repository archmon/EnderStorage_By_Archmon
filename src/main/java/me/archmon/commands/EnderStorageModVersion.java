package me.archmon.commands;

import com.hypixel.hytale.protocol.GameMode;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.CommandBase;
import org.jspecify.annotations.NonNull;

public class EnderStorageModVersion extends CommandBase {

    private final String pluginVersion;

    public EnderStorageModVersion(String pluginVersion) {
        super("EnderStorageVersion","Version number of the EnderStorage Mod by Archmon");
        this.setPermissionGroup(GameMode.Adventure); // Allows the command to be used by anyone, not just OP
        this.pluginVersion = pluginVersion;
    }

    @Override
    protected void executeSync(@NonNull CommandContext ctx) {
        ctx.sendMessage(Message.raw("Hello from the EnderStorage_By_Archmon v" + pluginVersion + " Mod!"));
    }
}
