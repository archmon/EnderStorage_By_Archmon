package me.archmon.commands;

import com.hypixel.hytale.protocol.GameMode;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.CommandBase;
import org.jspecify.annotations.NonNull;

public class EnderStorageDebug extends CommandBase {

    public EnderStorageDebug() {
        super("EnderStorageDebug", "Shows EnderStorage debug status.");
        this.setPermissionGroup(GameMode.Adventure);
    }

    @Override
    protected void executeSync(@NonNull CommandContext ctx) {
        ctx.sendMessage(Message.raw("EnderStorage debug status:"));
        ctx.sendMessage(Message.raw("- pocket_DimensionSafe uses coordinate-keyed database storage."));
        ctx.sendMessage(Message.raw("- Ender_Chest instances use coordinate-keyed color/private network config."));
        ctx.sendMessage(Message.raw("- Ender_Chest default public network is public:0:0:0."));
        ctx.sendMessage(Message.raw("- EnderWrench crouch-use opens the current one-slot private lock window."));
        ctx.sendMessage(Message.raw("- pocket_DimensionSafe owner checks support enderstorage.admin and enderstorage.safe.bypass."));
    }
}
