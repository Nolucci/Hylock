package com.hyvanced.hylock.commands;

import java.util.UUID;

import javax.annotation.Nonnull;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.protocol.GameMode;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hyvanced.hylock.HylockPlugin;
import com.hyvanced.hylock.lockon.LockOnManager;
import com.hyvanced.hylock.lockon.LockOnState;
import com.hyvanced.hylock.lockon.TargetInfo;

/**
 * Command to lock onto the nearest valid target.
 * This is a backup method - prefer using the configured keybind (Middle Mouse
 * by default).
 *
 * Usage:
 * /lock - Toggle lock on nearest target in view
 */
public class LockCommand extends AbstractPlayerCommand {

    private final HylockPlugin plugin;

    public LockCommand(HylockPlugin plugin) {
        super("lock", "Toggle lock onto the nearest target (Hylock)");
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

        // Check current state - if locked, toggle off
        LockOnState currentState = lockManager.getState(playerId);

        if (currentState == LockOnState.LOCKED) {
            TargetInfo currentTarget = lockManager.getLockedTarget(playerId);
            String targetName = currentTarget != null ? currentTarget.getEntityName() : "target";
            lockManager.releaseLock(playerId);
            ctx.sendMessage(Message.raw("[Hylock] Released lock on " + targetName));
            return;
        }

        // Get player position
        TransformComponent transform = store.getComponent(ref, TransformComponent.getComponentType());
        if (transform == null) {
            ctx.sendMessage(Message.raw("[Hylock] Could not get player position."));
            return;
        }

        Vector3d position = transform.getPosition();
        ctx.sendMessage(Message.raw("[Hylock] Searching for target..."));

        // Use the LockOnManager to find and lock target
        boolean found = plugin.getLockOnManager().tryLockOnFromWorld(
                playerId, store, ref, world,
                position.getX(), position.getY(), position.getZ());

        if (found) {
            TargetInfo target = lockManager.getLockedTarget(playerId);
            if (target != null) {
                ctx.sendMessage(Message.raw("[Hylock] Locked onto " + target.getEntityName()));
            }
        } else {
            ctx.sendMessage(Message.raw("[Hylock] No valid target in range."));
            ctx.sendMessage(Message.raw("Tip: Use Middle Mouse to lock onto entity you're looking at."));
        }
    }
}
