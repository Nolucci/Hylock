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

    // Stores the last targeted entity for each player
    private final Map<UUID, Entity> playerTargets = new ConcurrentHashMap<>();

    // Counter for debug logging (to avoid spam)
    private int eventCounter = 0;

    public MouseTargetTracker() {
        LOGGER.atInfo().log("[Hylock] MouseTargetTracker created");
    }

    @Override
    @SuppressWarnings("removal")
    public void accept(PlayerMouseMotionEvent event) {
        // Log every 100th event to avoid spam but confirm events are being received
        eventCounter++;
        if (eventCounter % 100 == 1) {
            LOGGER.atInfo().log("[Hylock] MouseMotionEvent received (count: %d)", eventCounter);
        }

        Player player = event.getPlayer();
        UUID playerId = player.getUuid();
        Entity targetEntity = event.getTargetEntity();

        // Update the tracked target for this player
        if (targetEntity != null && !targetEntity.getUuid().equals(playerId)) {
            // Only log when target changes
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
            // Clear target if looking at nothing or self
            if (playerTargets.containsKey(playerId)) {
                playerTargets.remove(playerId);
            }
        }
    }

    /**
     * Get the entity that a player is currently looking at.
     * @param playerId The player's UUID
     * @return The targeted entity, or null if not targeting anything
     */
    public Entity getTargetedEntity(UUID playerId) {
        return playerTargets.get(playerId);
    }

    /**
     * Clear tracked target for a player (e.g., when they disconnect).
     */
    public void clearTarget(UUID playerId) {
        playerTargets.remove(playerId);
    }
}
