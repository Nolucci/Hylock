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
import com.hyvanced.hylock.lockon.LockOnManager;
import com.hyvanced.hylock.lockon.LockOnState;
import com.hyvanced.hylock.lockon.TargetInfo;

/**
 * Quick command to release the current lock-on target.
 *
 * Usage:
 * /unlock - Release current lock
 *
 * This command is designed to be bound to a key for quick access.
 */
public class UnlockCommand extends AbstractPlayerCommand {

    private final HylockPlugin plugin;

    public UnlockCommand(HylockPlugin plugin) {
        super("unlock", "Release the current lock-on target (Hylock)");
        this.setPermissionGroup(GameMode.Adventure);
        this.plugin = plugin;
    }

    @Override
    protected void execute(@Nonnull CommandContext ctx,
            @Nonnull Store<EntityStore> store,
            @Nonnull Ref<EntityStore> ref,
            @Nonnull PlayerRef playerRef,
            @Nonnull World world) {

        LockOnManager lockManager = plugin.getLockOnManager();
        UUID playerId = playerRef.getUuid();

        // Check if player has an active lock
        LockOnState currentState = lockManager.getState(playerId);

        if (currentState != LockOnState.LOCKED) {
            ctx.sendMessage(Message.raw("[Hylock] No target currently locked."));
            return;
        }

        // Get target name before releasing for feedback
        TargetInfo target = lockManager.getLockedTarget(playerId);
        String targetName = target != null ? target.getEntityName() : "Unknown";

        // Release the lock
        lockManager.releaseLock(playerId);

        ctx.sendMessage(Message.raw("[Hylock] Released lock on " + targetName));
    }
}
