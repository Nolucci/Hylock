package com.hyvanced.hylock.commands;

import javax.annotation.Nonnull;

import com.hypixel.hytale.protocol.GameMode;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.CommandBase;
import com.hyvanced.hylock.HylockPlugin;

/**
 * Command to reset all Hylock settings to their defaults.
 */
public class HylockResetCommand extends CommandBase {

    private final HylockPlugin plugin;

    /**
     * Constructs a new HylockResetCommand.
     *
     * @param plugin the Hylock plugin instance
     */
    public HylockResetCommand(HylockPlugin plugin) {
        super("hylockreset", "Reset Hylock settings to defaults");
        this.setPermissionGroup(GameMode.Adventure);
        this.plugin = plugin;
    }

    /**
     * Executes the reset command, restoring all settings to default values.
     *
     * @param ctx the command context
     */
    @Override
    protected void executeSync(@Nonnull CommandContext ctx) {
        plugin.getConfig().resetToDefaults();
        ctx.sendMessage(Message.raw("[Hylock] All settings have been reset to defaults."));
        ctx.sendMessage(Message.raw("Use /hylockstatus to view the current settings."));
    }
}
