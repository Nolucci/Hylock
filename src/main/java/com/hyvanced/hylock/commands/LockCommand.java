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
import com.hyvanced.hylock.camera.CameraController;
import com.hyvanced.hylock.camera.LockIndicatorManager;
import com.hyvanced.hylock.events.MouseTargetTracker;
import com.hyvanced.hylock.lockon.LockOnManager;
import com.hyvanced.hylock.lockon.LockOnState;
import com.hyvanced.hylock.lockon.TargetInfo;

/**
 * Command to toggle lock onto the nearest valid target.
 * This is a backup method - prefer using the configured keybind (Middle Mouse by default).
 */
public class LockCommand extends AbstractPlayerCommand {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private final HylockPlugin plugin;

    /**
     * Constructs a new LockCommand.
     *
     * @param plugin the Hylock plugin instance
     */
    public LockCommand(HylockPlugin plugin) {
        super("lock", "Toggle lock onto the nearest target (Hylock)");
        this.setPermissionGroup(GameMode.Adventure);
        this.plugin = plugin;
        LOGGER.atInfo().log("[Hylock] LockCommand registered");
    }

    /**
     * Executes the lock command to toggle targeting on the nearest entity.
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

        LOGGER.atInfo().log("[Hylock] /lock command executed by %s", playerRef.getUsername());

        LockOnManager lockManager = plugin.getLockOnManager();
        UUID playerId = playerRef.getUuid();
        LOGGER.atInfo().log("[Hylock] Player UUID: %s", playerId);

        LockOnState currentState = lockManager.getState(playerId);
        LOGGER.atInfo().log("[Hylock] Current lock state: %s", currentState);

        CameraController cameraController = plugin.getCameraController();
        LockIndicatorManager indicatorManager = plugin.getLockIndicatorManager();

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

        if (currentState == LockOnState.LOCKED) {
            LOGGER.atInfo().log("[Hylock] Player is already locked, releasing...");
            TargetInfo currentTarget = lockManager.getLockedTarget(playerId);
            String targetName = currentTarget != null ? currentTarget.getEntityName() : "target";
            lockManager.releaseLock(playerId);
            cameraController.stopCameraLock(playerId);
            indicatorManager.showLockReleased(playerId);
            ctx.sendMessage(Message.raw("[Hylock] Released lock on " + targetName));
            return;
        }

        MouseTargetTracker tracker = plugin.getMouseTargetTracker();
        if (tracker != null) {
            Entity targetedEntity = tracker.getTargetedEntity(playerId);
            if (targetedEntity != null) {
                LOGGER.atInfo().log("[Hylock] Found targeted entity from mouse tracker!");

                boolean isTargetPlayer = targetedEntity instanceof com.hypixel.hytale.server.core.entity.entities.Player;
                if (isTargetPlayer && !plugin.getConfig().isLockOnPlayers()) {
                    LOGGER.atInfo().log("[Hylock] Player targeting is disabled, skipping player target");
                    ctx.sendMessage(Message.raw("[Hylock] Player targeting is disabled. Use /locktoggle to enable."));
                    return;
                }

                TargetInfo target = createTargetInfoFromEntity(targetedEntity);
                if (target != null && lockManager.lockOnTarget(playerId, target)) {
                    cameraController.startCameraLock(playerId, playerRef);
                    cameraController.updatePlayerPosition(playerId,
                        position.getX(), position.getY(), position.getZ());
                    indicatorManager.showLockAcquired(playerId, playerRef, target);
                    LOGGER.atInfo().log("[Hylock] Locked onto targeted entity: %s", target.getEntityName());
                    ctx.sendMessage(Message.raw("[Hylock] Locked onto " + target.getEntityName()));
                    return;
                }
            } else {
                LOGGER.atInfo().log("[Hylock] No entity currently targeted by mouse");
            }
        }

        ctx.sendMessage(Message.raw("[Hylock] Searching for target..."));

        LOGGER.atInfo().log("[Hylock] Calling tryLockOnFromWorld...");
        boolean found = plugin.getLockOnManager().tryLockOnFromWorld(
                playerId, store, ref, world,
                position.getX(), position.getY(), position.getZ());

        LOGGER.atInfo().log("[Hylock] tryLockOnFromWorld returned: %s", found);

        if (found) {
            TargetInfo target = lockManager.getLockedTarget(playerId);
            if (target != null) {
                cameraController.startCameraLock(playerId, playerRef);
                cameraController.updatePlayerPosition(playerId,
                    position.getX(), position.getY(), position.getZ());
                indicatorManager.showLockAcquired(playerId, playerRef, target);
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
     * Creates a TargetInfo from an Entity.
     *
     * @param entity the entity to create target info from
     * @return the created TargetInfo, or null if entity is null
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

        TransformComponent transform = entity.getTransformComponent();
        if (transform != null) {
            Vector3d pos = transform.getPosition();
            info.updatePosition(pos.getX(), pos.getY(), pos.getZ());
        }

        return info;
    }
}
