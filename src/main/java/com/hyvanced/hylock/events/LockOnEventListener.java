package com.hyvanced.hylock.events;

import java.util.UUID;
import java.util.function.Consumer;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.protocol.MouseButtonState;
import com.hypixel.hytale.protocol.MouseButtonType;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.Entity;
import com.hypixel.hytale.server.core.event.events.player.PlayerMouseButtonEvent;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hyvanced.hylock.HylockPlugin;
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

        // If already locked on this same entity, release
        if (currentState == LockOnState.LOCKED && currentTarget != null) {
            // Check if clicking on same target
            if (isSameEntity(currentTarget, targetEntity)) {
                lockManager.releaseLock(playerId);
                sendMessage(playerRef, "[Hylock] Lock released.");
                return;
            }

            // Different entity - switch target
            lockManager.releaseLock(playerId);
        }

        // Lock onto the new target
        TargetInfo newTarget = createTargetInfo(targetEntity);
        if (lockManager.lockOnTarget(playerId, newTarget)) {
            sendMessage(playerRef, "[Hylock] Locked onto " + newTarget.getEntityName());
            LOGGER.atInfo().log("[Hylock] Player %s locked onto %s", playerRef.getUsername(),
                    newTarget.getEntityName());
        }
    }

    /**
     * Handle when player clicks on empty space
     */
    private void handleEmptyClick(PlayerRef playerRef, UUID playerId,
            LockOnManager lockManager, LockOnState currentState) {
        if (currentState == LockOnState.LOCKED) {
            // Release current lock
            TargetInfo currentTarget = lockManager.getLockedTarget(playerId);
            String targetName = currentTarget != null ? currentTarget.getEntityName() : "target";
            lockManager.releaseLock(playerId);
            sendMessage(playerRef, "[Hylock] Released lock on " + targetName);
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

    /**
     * Send a message to the player
     */
    private void sendMessage(PlayerRef playerRef, String message) {
        playerRef.sendMessage(Message.raw(message));
    }
}
