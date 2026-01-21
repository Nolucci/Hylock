package com.hyvanced.hylock.events;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.entity.Entity;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.event.events.player.PlayerMouseMotionEvent;

/**
 * Tracks the entity each player is currently looking at (hovering over with mouse).
 * This information is used by the /lock command to lock onto the targeted entity.
 */
public class MouseTargetTracker implements Consumer<PlayerMouseMotionEvent> {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private final Map<UUID, Entity> playerTargets = new ConcurrentHashMap<>();
    private int eventCounter = 0;

    /**
     * Constructs a new MouseTargetTracker.
     */
    public MouseTargetTracker() {
        LOGGER.atInfo().log("[Hylock] MouseTargetTracker created");
    }

    /**
     * Handles mouse motion events to track the entity under the player's cursor.
     *
     * @param event the player mouse motion event
     */
    @Override
    @SuppressWarnings("removal")
    public void accept(PlayerMouseMotionEvent event) {
        eventCounter++;
        if (eventCounter % 50 == 1) {
            Entity target = event.getTargetEntity();
            String targetInfo = target != null ? target.getClass().getSimpleName() : "null";
            LOGGER.atInfo().log("[Hylock] MouseMotionEvent #%d - targetEntity: %s", eventCounter, targetInfo);
        }

        Player player = event.getPlayer();
        UUID playerId = player.getUuid();
        Entity targetEntity = event.getTargetEntity();

        if (targetEntity != null && !targetEntity.getUuid().equals(playerId)) {
            Entity previousTarget = playerTargets.get(playerId);
            if (previousTarget == null || !previousTarget.getUuid().equals(targetEntity.getUuid())) {
                String entityName = targetEntity.getLegacyDisplayName();
                if (entityName == null || entityName.isEmpty()) {
                    entityName = targetEntity.getClass().getSimpleName();
                }
                LOGGER.atInfo().log("[Hylock] Player %s now targeting: %s",
                    player.getLegacyDisplayName(), entityName);
            }
            playerTargets.put(playerId, targetEntity);
        } else {
            if (playerTargets.containsKey(playerId)) {
                playerTargets.remove(playerId);
            }
        }
    }

    /**
     * Gets the entity that a player is currently looking at.
     *
     * @param playerId the player's UUID
     * @return the targeted entity, or null if not targeting anything
     */
    public Entity getTargetedEntity(UUID playerId) {
        return playerTargets.get(playerId);
    }

    /**
     * Clears the tracked target for a player.
     *
     * @param playerId the player's UUID
     */
    public void clearTarget(UUID playerId) {
        playerTargets.remove(playerId);
    }
}
