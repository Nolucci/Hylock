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
 * When a player attacks an entity, the system automatically locks onto it.
 */
@SuppressWarnings("deprecation")
public class PlayerInteractListener implements Consumer<PlayerInteractEvent> {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private final HylockPlugin plugin;

    /**
     * Constructs a new PlayerInteractListener.
     *
     * @param plugin the Hylock plugin instance
     */
    public PlayerInteractListener(HylockPlugin plugin) {
        this.plugin = plugin;
        LOGGER.atInfo().log("[Hylock] PlayerInteractListener created");
    }

    /**
     * Handles player interaction events to auto-lock onto attacked targets.
     *
     * @param event the player interact event
     */
    @Override
    public void accept(PlayerInteractEvent event) {
        LOGGER.atInfo().log("[Hylock] PlayerInteractEvent received! ActionType: %s", event.getActionType());

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

        Player player = event.getPlayer();
        UUID playerId = player.getUuid();
        HylockConfig config = plugin.getConfig();
        LockOnManager lockManager = plugin.getLockOnManager();

        if (!isValidTarget(targetEntity, playerId, config)) {
            LOGGER.atInfo().log("[Hylock] Target is not valid for locking");
            return;
        }

        LockOnState currentState = lockManager.getState(playerId);

        if (currentState == LockOnState.LOCKED) {
            TargetInfo currentTarget = lockManager.getLockedTarget(playerId);
            if (currentTarget != null && !isSameEntity(currentTarget, targetEntity)) {
                LOGGER.atInfo().log("[Hylock] Already locked on different target, not auto-switching");
                return;
            }
            return;
        }

        TargetInfo newTarget = createTargetInfo(targetEntity);
        if (lockManager.lockOnTarget(playerId, newTarget)) {
            LOGGER.atInfo().log("[Hylock] Auto-locked onto %s via attack!", newTarget.getEntityName());
            player.sendMessage(Message.raw("[Hylock] Locked onto " + newTarget.getEntityName()));
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
