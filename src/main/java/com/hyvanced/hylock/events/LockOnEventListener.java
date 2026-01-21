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
 *
 * This provides a Zelda-style experience where you can:
 * - Click middle mouse on an entity to lock onto it
 * - Click middle mouse again (or on empty space) to release lock
 * - Click middle mouse on a different entity to switch targets
 */
public class LockOnEventListener implements Consumer<PlayerMouseButtonEvent> {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private final HylockPlugin plugin;

    public LockOnEventListener(HylockPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void accept(PlayerMouseButtonEvent event) {
        // DEBUG: Log every mouse event received
        LOGGER.atInfo().log("[Hylock] DEBUG: Mouse event received! Button: %s, State: %s",
                event.getMouseButton().mouseButtonType,
                event.getMouseButton().state);

        HylockConfig config = plugin.getConfig();

        // Check if the configured lock button was pressed
        MouseButtonType configuredButton = config.getLockButton();
        LOGGER.atInfo().log("[Hylock] DEBUG: Configured button: %s, Event button: %s",
                configuredButton, event.getMouseButton().mouseButtonType);

        if (event.getMouseButton().mouseButtonType != configuredButton) {
            LOGGER.atInfo().log("[Hylock] DEBUG: Button mismatch, ignoring event");
            return;
        }

        // Only trigger on button press, not release
        if (event.getMouseButton().state != MouseButtonState.Pressed) {
            LOGGER.atInfo().log("[Hylock] DEBUG: Not a press event, ignoring");
            return;
        }

        LOGGER.atInfo().log("[Hylock] DEBUG: Middle mouse PRESSED detected!");

        // Get player info
        PlayerRef playerRef = event.getPlayerRefComponent();
        UUID playerId = playerRef.getUuid();
        LOGGER.atInfo().log("[Hylock] DEBUG: Player: %s (UUID: %s)", playerRef.getUsername(), playerId);

        LockOnManager lockManager = plugin.getLockOnManager();

        // Get the entity the player is looking at (if any)
        Entity targetEntity = event.getTargetEntity();
        LOGGER.atInfo().log("[Hylock] DEBUG: Target entity from event: %s",
                targetEntity != null ? targetEntity.getClass().getSimpleName() : "NULL");

        // Get current lock state
        LockOnState currentState = lockManager.getState(playerId);
        LOGGER.atInfo().log("[Hylock] DEBUG: Current lock state: %s", currentState);

        if (targetEntity != null && isValidTarget(targetEntity, playerId, config)) {
            // Player clicked on a valid entity
            LOGGER.atInfo().log("[Hylock] DEBUG: Valid target found, handling entity click");
            handleEntityClick(event, playerRef, playerId, targetEntity, lockManager, currentState);
        } else {
            // Player clicked on nothing or invalid target
            LOGGER.atInfo().log("[Hylock] DEBUG: No valid target, handling empty click");
            handleEmptyClick(playerRef, playerId, lockManager, currentState);
        }
    }

    /**
     * Handle when player clicks on a valid entity
     */
    private void handleEntityClick(PlayerMouseButtonEvent event, PlayerRef playerRef, UUID playerId,
            Entity targetEntity, LockOnManager lockManager, LockOnState currentState) {

        TargetInfo currentTarget = lockManager.getLockedTarget(playerId);
        CameraController cameraController = plugin.getCameraController();
        LockIndicatorManager indicatorManager = plugin.getLockIndicatorManager();

        // If already locked on this same entity, release
        if (currentState == LockOnState.LOCKED && currentTarget != null) {
            // Check if clicking on same target
            if (isSameEntity(currentTarget, targetEntity)) {
                // Release lock and stop camera
                lockManager.releaseLock(playerId);
                cameraController.stopCameraLock(playerId);
                indicatorManager.showLockReleased(playerId);
                return;
            }

            // Different entity - switch target (release old first)
            cameraController.stopCameraLock(playerId);
            indicatorManager.showLockReleased(playerId);
            lockManager.releaseLock(playerId);
        }

        // Lock onto the new target
        TargetInfo newTarget = createTargetInfo(targetEntity);
        if (lockManager.lockOnTarget(playerId, newTarget)) {
            // Start camera lock - the CameraController tracks the PlayerRef
            cameraController.startCameraLock(playerId, playerRef);

            // Try to get and store player position for camera updates
            Player player = event.getPlayer();
            if (player != null) {
                TransformComponent playerTransform = player.getTransformComponent();
                if (playerTransform != null) {
                    Vector3d playerPos = playerTransform.getPosition();
                    cameraController.updatePlayerPosition(playerId,
                        playerPos.getX(), playerPos.getY(), playerPos.getZ());
                }
            }

            // Show lock indicator
            indicatorManager.showLockAcquired(playerId, playerRef, newTarget);

            LOGGER.atInfo().log("[Hylock] Player %s locked onto %s with camera tracking",
                    playerRef.getUsername(), newTarget.getEntityName());
        }
    }

    /**
     * Handle when player clicks on empty space
     */
    private void handleEmptyClick(PlayerRef playerRef, UUID playerId,
            LockOnManager lockManager, LockOnState currentState) {
        if (currentState == LockOnState.LOCKED) {
            // Release current lock
            CameraController cameraController = plugin.getCameraController();
            LockIndicatorManager indicatorManager = plugin.getLockIndicatorManager();

            lockManager.releaseLock(playerId);
            cameraController.stopCameraLock(playerId);
            indicatorManager.showLockReleased(playerId);
        }
        // If not locked, clicking on nothing does nothing
    }

    /**
     * Check if an entity is a valid lock target
     */
    @SuppressWarnings("removal")
    private boolean isValidTarget(Entity entity, UUID playerId, HylockConfig config) {
        if (entity == null) {
            return false;
        }

        // Can't lock onto yourself
        UUID entityId = entity.getUuid();
        if (entityId != null && entityId.equals(playerId)) {
            return false;
        }

        // Check if we should lock onto players
        if (entity instanceof com.hypixel.hytale.server.core.entity.entities.Player) {
            return config.isLockOnPlayers();
        }

        // For now, accept all other entities
        return true;
    }

    /**
     * Check if the target info represents the same entity
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
     * Create a TargetInfo from an Entity
     */
    @SuppressWarnings("removal")
    private TargetInfo createTargetInfo(Entity entity) {
        boolean isPlayer = entity instanceof com.hypixel.hytale.server.core.entity.entities.Player;
        boolean isHostile = !isPlayer; // Simplified - could check actual hostility

        // Get entity name - use display name or class name as fallback
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
