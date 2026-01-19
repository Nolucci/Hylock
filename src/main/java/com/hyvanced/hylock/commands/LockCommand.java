package com.hyvanced.hylock.commands;

import java.util.UUID;

import javax.annotation.Nonnull;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.protocol.GameMode;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.entity.Entity;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hyvanced.hylock.HylockPlugin;
import com.hyvanced.hylock.events.MouseTargetTracker;
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
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private final HylockPlugin plugin;

    public LockCommand(HylockPlugin plugin) {
        super("lock", "Toggle lock onto the nearest target (Hylock)");
        this.setPermissionGroup(GameMode.Adventure);
        this.plugin = plugin;
        LOGGER.atInfo().log("[Hylock] LockCommand registered");
    }

    @Override
    protected void execute(@Nonnull CommandContext ctx,
            @Nonnull Store<EntityStore> store,
            @Nonnull Ref<EntityStore> ref,
            @Nonnull PlayerRef playerRef,
            @Nonnull World world) {

        LOGGER.atInfo().log("[Hylock] /lock command executed by %s", playerRef.getUsername());

        LockOnManager lockManager = plugin.getLockOnManager();
        UUID playerId = playerRef.getUuid();
        LOGGER.atInfo().log("[Hylock] Player UUID: %s", playerId);

        // Check current state - if locked, toggle off
        LockOnState currentState = lockManager.getState(playerId);
        LOGGER.atInfo().log("[Hylock] Current lock state: %s", currentState);

        if (currentState == LockOnState.LOCKED) {
            LOGGER.atInfo().log("[Hylock] Player is already locked, releasing...");
            TargetInfo currentTarget = lockManager.getLockedTarget(playerId);
            String targetName = currentTarget != null ? currentTarget.getEntityName() : "target";
            lockManager.releaseLock(playerId);
            ctx.sendMessage(Message.raw("[Hylock] Released lock on " + targetName));
            return;
        }

        // First, try to lock onto what the player is looking at (from MouseTargetTracker)
        MouseTargetTracker tracker = plugin.getMouseTargetTracker();
        if (tracker != null) {
            Entity targetedEntity = tracker.getTargetedEntity(playerId);
            if (targetedEntity != null) {
                LOGGER.atInfo().log("[Hylock] Found targeted entity from mouse tracker!");
                TargetInfo target = createTargetInfoFromEntity(targetedEntity);
                if (target != null && lockManager.lockOnTarget(playerId, target)) {
                    LOGGER.atInfo().log("[Hylock] Locked onto targeted entity: %s", target.getEntityName());
                    ctx.sendMessage(Message.raw("[Hylock] Locked onto " + target.getEntityName()));
                    return;
                }
            } else {
                LOGGER.atInfo().log("[Hylock] No entity currently targeted by mouse");
            }
        }

        // Fallback: Get player position and search for nearby players
        LOGGER.atInfo().log("[Hylock] Getting player transform component...");
        TransformComponent transform = store.getComponent(ref, TransformComponent.getComponentType());
        if (transform == null) {
            LOGGER.atWarning().log("[Hylock] TransformComponent is NULL!");
            ctx.sendMessage(Message.raw("[Hylock] Could not get player position."));
            return;
        }

        Vector3d position = transform.getPosition();
        LOGGER.atInfo().log("[Hylock] Player position: (%.2f, %.2f, %.2f)",
                position.getX(), position.getY(), position.getZ());

        ctx.sendMessage(Message.raw("[Hylock] Searching for target..."));

        // Use the LockOnManager to find and lock target (searches players only)
        LOGGER.atInfo().log("[Hylock] Calling tryLockOnFromWorld...");
        boolean found = plugin.getLockOnManager().tryLockOnFromWorld(
                playerId, store, ref, world,
                position.getX(), position.getY(), position.getZ());

        LOGGER.atInfo().log("[Hylock] tryLockOnFromWorld returned: %s", found);

        if (found) {
            TargetInfo target = lockManager.getLockedTarget(playerId);
            if (target != null) {
                LOGGER.atInfo().log("[Hylock] Successfully locked onto: %s", target.getEntityName());
                ctx.sendMessage(Message.raw("[Hylock] Locked onto " + target.getEntityName()));
            }
        } else {
            LOGGER.atInfo().log("[Hylock] No target found");
            ctx.sendMessage(Message.raw("[Hylock] No valid target in range."));
            ctx.sendMessage(Message.raw("Tip: Look at an entity and use /lock to lock onto it."));
        }
    }

    /**
     * Create a TargetInfo from an Entity
     */
    @SuppressWarnings("removal")
    private TargetInfo createTargetInfoFromEntity(Entity entity) {
        if (entity == null) {
            return null;
        }

        boolean isPlayer = entity instanceof com.hypixel.hytale.server.core.entity.entities.Player;
        boolean isHostile = !isPlayer;

        String entityName = entity.getLegacyDisplayName();
        if (entityName == null || entityName.isEmpty()) {
            entityName = entity.getClass().getSimpleName();
        }

        TargetInfo info = new TargetInfo(
                entity.getUuid(),
                entityName,
                isHostile,
                isPlayer);

        // Update position from entity's transform component
        TransformComponent transform = entity.getTransformComponent();
        if (transform != null) {
            Vector3d pos = transform.getPosition();
            info.updatePosition(pos.getX(), pos.getY(), pos.getZ());
        }

        return info;
    }
}
