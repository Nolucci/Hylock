package com.hyvanced.hylock.events;

import java.util.UUID;
import java.util.function.Consumer;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.protocol.MouseButtonState;
import com.hypixel.hytale.protocol.MouseButtonType;
import com.hypixel.hytale.server.core.entity.Entity;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.event.events.player.PlayerMouseButtonEvent;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hyvanced.hylock.HylockPlugin;
import com.hyvanced.hylock.camera.CameraController;
import com.hyvanced.hylock.camera.LockIndicatorManager;
import com.hyvanced.hylock.config.HylockConfig;
import com.hyvanced.hylock.lockon.LockOnManager;
import com.hyvanced.hylock.lockon.LockOnState;
import com.hyvanced.hylock.lockon.TargetInfo;

/**
 * Event listener for mouse input to handle lock-on targeting.
 * By default, Middle Mouse Button (scroll wheel click) toggles lock-on.
 */
public class LockOnEventListener implements Consumer<PlayerMouseButtonEvent> {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private final HylockPlugin plugin;

    /**
     * Constructs a new LockOnEventListener.
     *
     * @param plugin the Hylock plugin instance
     */
    public LockOnEventListener(HylockPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Handles mouse button events to toggle lock-on targeting.
     *
     * @param event the player mouse button event
     */
    @Override
    public void accept(PlayerMouseButtonEvent event) {
        LOGGER.atInfo().log("[Hylock] DEBUG: Mouse event received! Button: %s, State: %s",
                event.getMouseButton().mouseButtonType,
                event.getMouseButton().state);

        HylockConfig config = plugin.getConfig();

        MouseButtonType configuredButton = config.getLockButton();
        LOGGER.atInfo().log("[Hylock] DEBUG: Configured button: %s, Event button: %s",
                configuredButton, event.getMouseButton().mouseButtonType);

        if (event.getMouseButton().mouseButtonType != configuredButton) {
            LOGGER.atInfo().log("[Hylock] DEBUG: Button mismatch, ignoring event");
            return;
        }

        if (event.getMouseButton().state != MouseButtonState.Pressed) {
            LOGGER.atInfo().log("[Hylock] DEBUG: Not a press event, ignoring");
            return;
        }

        LOGGER.atInfo().log("[Hylock] DEBUG: Middle mouse PRESSED detected!");

        PlayerRef playerRef = event.getPlayerRefComponent();
        UUID playerId = playerRef.getUuid();
        LOGGER.atInfo().log("[Hylock] DEBUG: Player: %s (UUID: %s)", playerRef.getUsername(), playerId);

        LockOnManager lockManager = plugin.getLockOnManager();

        Entity targetEntity = event.getTargetEntity();
        LOGGER.atInfo().log("[Hylock] DEBUG: Target entity from event: %s",
                targetEntity != null ? targetEntity.getClass().getSimpleName() : "NULL");

        LockOnState currentState = lockManager.getState(playerId);
        LOGGER.atInfo().log("[Hylock] DEBUG: Current lock state: %s", currentState);

        if (targetEntity != null && isValidTarget(targetEntity, playerId, config)) {
            LOGGER.atInfo().log("[Hylock] DEBUG: Valid target found, handling entity click");
            handleEntityClick(event, playerRef, playerId, targetEntity, lockManager, currentState);
        } else {
            LOGGER.atInfo().log("[Hylock] DEBUG: No valid target, handling empty click");
            handleEmptyClick(playerRef, playerId, lockManager, currentState);
        }
    }

    /**
     * Handles when a player clicks on a valid entity.
     *
     * @param event        the mouse button event
     * @param playerRef    the player reference
     * @param playerId     the player's UUID
     * @param targetEntity the clicked entity
     * @param lockManager  the lock-on manager
     * @param currentState the current lock state
     */
    private void handleEntityClick(PlayerMouseButtonEvent event, PlayerRef playerRef, UUID playerId,
            Entity targetEntity, LockOnManager lockManager, LockOnState currentState) {

        TargetInfo currentTarget = lockManager.getLockedTarget(playerId);
        CameraController cameraController = plugin.getCameraController();
        LockIndicatorManager indicatorManager = plugin.getLockIndicatorManager();

        if (currentState == LockOnState.LOCKED && currentTarget != null) {
            if (isSameEntity(currentTarget, targetEntity)) {
                lockManager.releaseLock(playerId);
                cameraController.stopCameraLock(playerId);
                indicatorManager.showLockReleased(playerId);
                return;
            }

            cameraController.stopCameraLock(playerId);
            indicatorManager.showLockReleased(playerId);
            lockManager.releaseLock(playerId);
        }

        TargetInfo newTarget = createTargetInfo(targetEntity);
        if (lockManager.lockOnTarget(playerId, newTarget)) {
            cameraController.startCameraLock(playerId, playerRef);

            Player player = event.getPlayer();
            if (player != null) {
                TransformComponent playerTransform = player.getTransformComponent();
                if (playerTransform != null) {
                    Vector3d playerPos = playerTransform.getPosition();
                    cameraController.updatePlayerPosition(playerId,
                        playerPos.getX(), playerPos.getY(), playerPos.getZ());
                }
            }

            indicatorManager.showLockAcquired(playerId, playerRef, newTarget);

            LOGGER.atInfo().log("[Hylock] Player %s locked onto %s with camera tracking",
                    playerRef.getUsername(), newTarget.getEntityName());
        }
    }

    /**
     * Handles when a player clicks on empty space.
     *
     * @param playerRef    the player reference
     * @param playerId     the player's UUID
     * @param lockManager  the lock-on manager
     * @param currentState the current lock state
     */
    private void handleEmptyClick(PlayerRef playerRef, UUID playerId,
            LockOnManager lockManager, LockOnState currentState) {
        if (currentState == LockOnState.LOCKED) {
            CameraController cameraController = plugin.getCameraController();
            LockIndicatorManager indicatorManager = plugin.getLockIndicatorManager();

            lockManager.releaseLock(playerId);
            cameraController.stopCameraLock(playerId);
            indicatorManager.showLockReleased(playerId);
        }
    }

    /**
     * Checks if an entity is a valid lock target.
     *
     * @param entity   the entity to check
     * @param playerId the player's UUID (to prevent self-targeting)
     * @param config   the plugin configuration
     * @return true if the entity is a valid target
     */
    @SuppressWarnings("removal")
    private boolean isValidTarget(Entity entity, UUID playerId, HylockConfig config) {
        if (entity == null) {
            return false;
        }

        UUID entityId = entity.getUuid();
        if (entityId != null && entityId.equals(playerId)) {
            return false;
        }

        if (entity instanceof com.hypixel.hytale.server.core.entity.entities.Player) {
            return config.isLockOnPlayers();
        }

        return true;
    }

    /**
     * Checks if the target info represents the same entity.
     *
     * @param targetInfo the current target info
     * @param entity     the entity to compare
     * @return true if they represent the same entity
     */
    @SuppressWarnings("removal")
    private boolean isSameEntity(TargetInfo targetInfo, Entity entity) {
        if (targetInfo == null || entity == null) {
            return false;
        }
        UUID entityId = entity.getUuid();
        return entityId != null && entityId.equals(targetInfo.getEntityId());
    }

    /**
     * Creates a TargetInfo from an Entity.
     *
     * @param entity the entity to create target info from
     * @return the created TargetInfo
     */
    @SuppressWarnings("removal")
    private TargetInfo createTargetInfo(Entity entity) {
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
