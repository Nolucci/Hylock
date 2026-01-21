package com.hyvanced.hylock.commands;

import javax.annotation.Nonnull;

import com.hypixel.hytale.protocol.GameMode;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.CommandBase;
import com.hyvanced.hylock.HylockPlugin;
import com.hyvanced.hylock.config.HylockConfig;

/**
 * Command to toggle player targeting on or off.
 */
public class LockToggleCommand extends CommandBase {

    private final HylockPlugin plugin;

    /**
     * Constructs a new LockToggleCommand.
     *
     * @param plugin the Hylock plugin instance
     */
    public LockToggleCommand(HylockPlugin plugin) {
        super("locktoggle", "Toggle player targeting for lock-on");
        this.setPermissionGroup(GameMode.Adventure);
        this.plugin = plugin;
    }

    /**
     * Executes the toggle command, switching player targeting on or off.
     *
     * @param ctx the command context
     */
    @Override
    protected void executeSync(@Nonnull CommandContext ctx) {
        HylockConfig config = plugin.getConfig();

        boolean newValue = !config.isLockOnPlayers();
        config.setLockOnPlayers(newValue);

        if (newValue) {
            ctx.sendMessage(Message.raw("[Hylock] Player targeting: ENABLED"));
            ctx.sendMessage(Message.raw("  You can now lock onto other players."));
        } else {
            ctx.sendMessage(Message.raw("[Hylock] Player targeting: DISABLED"));
            ctx.sendMessage(Message.raw("  You can only lock onto mobs/entities."));
        }
    }
}
