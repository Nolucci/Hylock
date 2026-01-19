package com.hyvanced.hylock.commands;

import java.util.UUID;

import javax.annotation.Nonnull;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.GameMode;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hyvanced.hylock.HylockPlugin;
import com.hyvanced.hylock.config.HylockConfig;
import com.hyvanced.hylock.lockon.LockOnManager;
import com.hyvanced.hylock.lockon.LockOnState;
import com.hyvanced.hylock.lockon.TargetInfo;

/**
 * Command to display detailed Hylock status and settings.
 *
 * Usage:
 * /hylockstatus - Show all current settings
 */
public class HylockStatusCommand extends AbstractPlayerCommand {

    private final HylockPlugin plugin;

    public HylockStatusCommand(HylockPlugin plugin) {
        super("hylockstatus", "Show Hylock lock-on system settings");
        this.setPermissionGroup(GameMode.Adventure);
        this.plugin = plugin;
    }

    @Override
    protected void execute(@Nonnull CommandContext ctx,
            @Nonnull Store<EntityStore> store,
            @Nonnull Ref<EntityStore> ref,
            @Nonnull PlayerRef playerRef,
            @Nonnull World world) {

        HylockConfig config = plugin.getConfig();
        LockOnManager lockManager = plugin.getLockOnManager();
        UUID playerId = playerRef.getUuid();

        ctx.sendMessage(Message.raw("═══════════════════════════════════════"));
        ctx.sendMessage(Message.raw("  HYLOCK STATUS"));
        ctx.sendMessage(Message.raw("═══════════════════════════════════════"));
        ctx.sendMessage(Message.raw(""));

        // Current lock status
        LockOnState currentState = lockManager.getState(playerId);
        ctx.sendMessage(Message.raw("Current Lock Status:"));
        ctx.sendMessage(Message.raw("  State: " + currentState.name()));
        if (currentState == LockOnState.LOCKED) {
            TargetInfo target = lockManager.getLockedTarget(playerId);
            if (target != null) {
                ctx.sendMessage(Message.raw("  Target: " + target.getEntityName()));
            }
        }
        ctx.sendMessage(Message.raw(""));

        ctx.sendMessage(Message.raw("Keybind:"));
        ctx.sendMessage(Message.raw("  Lock Button: " + config.getLockButton().name() + " Mouse"));
        ctx.sendMessage(Message.raw(""));

        ctx.sendMessage(Message.raw("Detection Settings:"));
        ctx.sendMessage(Message.raw("  Lock Range: " + config.getLockOnRange() + " blocks"));
        ctx.sendMessage(Message.raw("  Detection Angle: " + config.getLockOnAngle() + "°"));
        ctx.sendMessage(Message.raw("  Min Distance: " + config.getMinLockDistance() + " blocks"));
        ctx.sendMessage(Message.raw(""));
        ctx.sendMessage(Message.raw("Behavior Settings:"));
        ctx.sendMessage(Message.raw("  Auto-Switch on Kill: " + formatBoolean(config.isAutoSwitchOnKill())));
        ctx.sendMessage(Message.raw("  Prioritize Hostile: " + formatBoolean(config.isPrioritizeHostile())));
        ctx.sendMessage(Message.raw("  Lock on Players: " + formatBoolean(config.isLockOnPlayers())));
        ctx.sendMessage(Message.raw(""));
        ctx.sendMessage(Message.raw("Camera Settings:"));
        ctx.sendMessage(Message.raw("  Camera Smoothing: " + config.getCameraSmoothing()));
        ctx.sendMessage(Message.raw("  Height Offset: " + config.getCameraHeightOffset()));
        ctx.sendMessage(Message.raw(""));
        ctx.sendMessage(Message.raw("Movement Settings:"));
        ctx.sendMessage(Message.raw("  Strafe Enabled: " + formatBoolean(config.isEnableStrafe())));
        ctx.sendMessage(Message.raw("  Strafe Speed: " + (config.getStrafeSpeedMultiplier() * 100) + "%"));
        ctx.sendMessage(Message.raw(""));
        ctx.sendMessage(Message.raw("Feedback Settings:"));
        ctx.sendMessage(Message.raw("  Show Lock Indicator: " + formatBoolean(config.isShowLockIndicator())));
        ctx.sendMessage(Message.raw("  Play Lock Sound: " + formatBoolean(config.isPlayLockSound())));
    }

    private String formatBoolean(boolean value) {
        return value ? "Enabled" : "Disabled";
    }
}
