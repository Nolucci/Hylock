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
 */
public class UnlockCommand extends AbstractPlayerCommand {

    private final HylockPlugin plugin;

    /**
     * Constructs a new UnlockCommand.
     *
     * @param plugin the Hylock plugin instance
     */
    public UnlockCommand(HylockPlugin plugin) {
        super("unlock", "Release the current lock-on target (Hylock)");
        this.setPermissionGroup(GameMode.Adventure);
        this.plugin = plugin;
    }

    /**
     * Executes the unlock command to release the current lock.
     *
     * @param ctx       the command context
     * @param store     the entity store
     * @param ref       the entity reference
     * @param playerRef the player reference
     * @param world     the world instance
     */
    @Override
    protected void execute(@Nonnull CommandContext ctx,
            @Nonnull Store<EntityStore> store,
            @Nonnull Ref<EntityStore> ref,
            @Nonnull PlayerRef playerRef,
            @Nonnull World world) {

        LockOnManager lockManager = plugin.getLockOnManager();
        UUID playerId = playerRef.getUuid();

        LockOnState currentState = lockManager.getState(playerId);

        if (currentState != LockOnState.LOCKED) {
            ctx.sendMessage(Message.raw("[Hylock] No target currently locked."));
            return;
        }

        TargetInfo target = lockManager.getLockedTarget(playerId);
        String targetName = target != null ? target.getEntityName() : "Unknown";

        lockManager.releaseLock(playerId);

        ctx.sendMessage(Message.raw("[Hylock] Released lock on " + targetName));
    }
}
