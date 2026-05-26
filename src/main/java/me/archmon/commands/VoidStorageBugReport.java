package me.archmon.commands;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.CommandBase;
import com.hypixel.hytale.server.core.permissions.provider.HytalePermissionsProvider;
import org.jspecify.annotations.NonNull;

public class VoidStorageBugReport extends CommandBase {

    public VoidStorageBugReport() {
        super("VoidStorageBugReport", "If there is a bug in the VoidStorage Mod by Archmon, here is the website to report it to.");
        this.setPermissionGroups(HytalePermissionsProvider.GROUP_ADVENTURER); // Allows the command to be used by anyone, not just OP
    }

    @Override
    protected void executeSync(@NonNull CommandContext commandContext) {
        String website = "https://github.com/archmon/VoidStorage_By_Archmon/issues";
        commandContext.sendMessage(Message.raw("Bug reports for the VoidStorage Mod by Archmon can be reported here :" + website));
    }
}
