package me.archmon.commands;

import com.hypixel.hytale.protocol.GameMode;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.CommandBase;
import org.jspecify.annotations.NonNull;

public class VoidStorageBugReport extends CommandBase {

    public VoidStorageBugReport() {
        super("VoidStorageBugReport", "If there is a bug in the VoidStorage Mod by Archmon, here is the website to report it to.");
        this.setPermissionGroup(GameMode.Adventure); // Allows the command to be used by anyone, not just OP
    }

    @Override
    protected void executeSync(@NonNull CommandContext commandContext) {
        String website = "opps, remind me to add the link";
        commandContext.sendMessage(Message.raw("Bug reports for the VoidStorage Mod by Archmon can be reported here :" + website));
    }
}
