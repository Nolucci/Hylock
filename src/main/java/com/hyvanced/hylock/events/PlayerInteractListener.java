package com.hyvanced.hylock.events;

import java.util.UUID;
import java.util.function.Consumer;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.Entity;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.event.events.player.PlayerInteractEvent;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hyvanced.hylock.HylockPlugin;
import com.hyvanced.hylock.config.HylockConfig;
import com.hyvanced.hylock.lockon.LockOnManager;
import com.hyvanced.hylock.lockon.LockOnState;
import com.hyvanced.hylock.lockon.TargetInfo;

/**
 * Listener for player interactions (attacks) to auto-lock onto targets.
 * When a player attacks an entity, we automatically lock onto it.
 * This provides a natural way to acquire targets without requiring middle mouse button.
 */
@SuppressWarnings("deprecation")
public class PlayerInteractListener implements Consumer<PlayerInteractEvent> {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private final HylockPlugin plugin;

    public PlayerInteractListener(HylockPlugin plugin) {
        this.plugin = plugin;
        LOGGER.atInfo().log("[Hylock] PlayerInteractListener created");
    }

    @Override
    public void accept(PlayerInteractEvent event) {
        LOGGER.atInfo().log("[Hylock] PlayerInteractEvent received! ActionType: %s", event.getActionType());

        // Only process primary (attack) interactions
        if (event.getActionType() != InteractionType.Primary) {
            LOGGER.atInfo().log("[Hylock] Not a primary action, ignoring");
            return;
        }

        Entity targetEntity = event.getTargetEntity();
        if (targetEntity == null) {
            LOGGER.atInfo().log("[Hylock] No target entity in interact event");
            return;
        }

        LOGGER.atInfo().log("[Hylock] Attack detected on entity: %s", targetEntity.getClass().getSimpleName());

        // Get player info
        Player player = event.getPlayer();
        UUID playerId = player.getUuid();
        HylockConfig config = plugin.getConfig();
        LockOnManager lockManager = plugin.getLockOnManager();

        // Check if we should lock this entity
        if (!isValidTarget(targetEntity, playerId, config)) {
            LOGGER.atInfo().log("[Hylock] Target is not valid for locking");
            return;
        }

        // Check current lock state
        LockOnState currentState = lockManager.getState(playerId);

        // If already locked on something, don't auto-switch on attack
        // (user can manually switch with middle mouse or /lockswitch)
        if (currentState == LockOnState.LOCKED) {
            TargetInfo currentTarget = lockManager.getLockedTarget(playerId);
            if (currentTarget != null && !isSameEntity(currentTarget, targetEntity)) {
                LOGGER.atInfo().log("[Hylock] Already locked on different target, not auto-switching");
                return;
            }
            // Already locked on same target, nothing to do
            return;
        }

        // Lock onto the attacked entity
        TargetInfo newTarget = createTargetInfo(targetEntity);
        if (lockManager.lockOnTarget(playerId, newTarget)) {
            LOGGER.atInfo().log("[Hylock] Auto-locked onto %s via attack!", newTarget.getEntityName());
            player.sendMessage(Message.raw("[Hylock] Locked onto " + newTarget.getEntityName()));
        }
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

        // Accept all other entities (mobs, creatures, etc.)
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
        boolean isHostile = !isPlayer; // Simplified - mobs are considered hostile

        // Get entity name
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
