package com.hyvanced.hylock.commands;

import javax.annotation.Nonnull;

import com.hypixel.hytale.protocol.GameMode;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.CommandBase;
import com.hyvanced.hylock.HylockPlugin;
import com.hyvanced.hylock.config.HylockConfig;

/**
 * Main command for the Hylock plugin.
 * Provides help information and displays current lock-on system status.
 */
public class HylockCommand extends CommandBase {

    private final HylockPlugin plugin;

    /**
     * Constructs a new HylockCommand.
     *
     * @param plugin the Hylock plugin instance used to access configuration and manifest
     */
    public HylockCommand(HylockPlugin plugin) {
        super("hylock", "Hylock lock-on system - Zelda-style targeting for Hytale combat");
        this.setPermissionGroup(GameMode.Adventure);
        this.plugin = plugin;
    }

    /**
     * Executes the hylock command synchronously.
     * Displays plugin information and current settings to the command sender.
     *
     * @param ctx the command context containing sender information and utilities
     */
    @Override
    protected void executeSync(@Nonnull CommandContext ctx) {
        showInfo(ctx);
    }

    /**
     * Displays comprehensive plugin information including available commands,
     * usage instructions, and current configuration settings.
     *
     * @param ctx the command context used to send messages to the player
     */
    private void showInfo(CommandContext ctx) {
        HylockConfig config = plugin.getConfig();

        ctx.sendMessage(Message.raw("═══════════════════════════════════════"));
        ctx.sendMessage(Message.raw("  HYLOCK - Zelda-style Lock-On System"));
        ctx.sendMessage(Message.raw("  Part of Hyvanced by Nolucci"));
        ctx.sendMessage(Message.raw("═══════════════════════════════════════"));
        ctx.sendMessage(Message.raw(""));
        ctx.sendMessage(Message.raw("Commands:"));
        ctx.sendMessage(Message.raw("  /lock - Toggle lock (fallback method)"));
        ctx.sendMessage(Message.raw("  /unlock - Release current lock"));
        ctx.sendMessage(Message.raw("  /lockswitch - Switch to next target"));
        ctx.sendMessage(Message.raw("  /locktoggle - Toggle player targeting"));
        ctx.sendMessage(Message.raw("  /hylockstatus - View current settings"));
        ctx.sendMessage(Message.raw("  /hylockreset - Reset to defaults"));
        ctx.sendMessage(Message.raw(""));
        ctx.sendMessage(Message.raw("How to use (Recommended):"));
        ctx.sendMessage(Message.raw("  1. Look at an enemy"));
        ctx.sendMessage(Message.raw("  2. Click Middle Mouse (scroll wheel) to lock"));
        ctx.sendMessage(Message.raw("  3. Your camera will follow the target"));
        ctx.sendMessage(Message.raw("  4. Click Middle Mouse again to release"));
        ctx.sendMessage(Message.raw(""));
        ctx.sendMessage(Message.raw("Current Settings:"));
        ctx.sendMessage(Message.raw("  Lock Button: " + config.getLockButton().name() + " Mouse"));
        ctx.sendMessage(Message.raw("  Lock Range: " + config.getLockOnRange() + " blocks"));
        ctx.sendMessage(Message.raw("  Detection Angle: " + config.getLockOnAngle() + "°"));
        ctx.sendMessage(Message.raw("  Auto-Switch: " + formatBoolean(config.isAutoSwitchOnKill())));
        ctx.sendMessage(Message.raw("  Lock Players: " + formatBoolean(config.isLockOnPlayers())));
        ctx.sendMessage(Message.raw(""));
        ctx.sendMessage(Message.raw("Version: " + plugin.getManifest().getVersion().toString()));
    }

    /**
     * Formats a boolean value as a human-readable string.
     *
     * @param value the boolean value to format
     * @return "Enabled" if true, "Disabled" if false
     */
    private String formatBoolean(boolean value) {
        return value ? "Enabled" : "Disabled";
    }
}
