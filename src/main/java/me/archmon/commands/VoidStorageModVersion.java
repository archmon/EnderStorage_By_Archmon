package me.archmon.commands;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.CommandBase;
import com.hypixel.hytale.server.core.permissions.provider.HytalePermissionsProvider;
import org.jspecify.annotations.NonNull;

public class VoidStorageModVersion extends CommandBase {

    private final String pluginVersion;

    public VoidStorageModVersion(String pluginVersion) {
        super("VoidStorageVersion","Version number of the VoidStorage Mod by Archmon");
        this.setPermissionGroups(HytalePermissionsProvider.GROUP_ADVENTURER); // Allows the command to be used by anyone, not just OP
        this.pluginVersion = pluginVersion;
    }

    @Override
    protected void executeSync(@NonNull CommandContext ctx) {
        ctx.sendMessage(Message.raw("VoidStorage_By_Archmon mod version is v" + pluginVersion + " Mod!"));
    }
}
