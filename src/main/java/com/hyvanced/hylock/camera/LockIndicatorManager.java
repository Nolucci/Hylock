package com.hyvanced.hylock.camera;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hyvanced.hylock.config.HylockConfig;
import com.hyvanced.hylock.lockon.TargetInfo;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages visual indicators for locked targets.
 * Shows the player which entity is currently locked and provides feedback.
 *
 * Features:
 * - Display target name and distance in action bar or chat
 * - Show lock/unlock notifications
 * - Track indicator state per player
 *
 * Note: Full visual effects (glow, particles) require Hytale's
 * effect system APIs when they become available.
 */
public class LockIndicatorManager {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    // Update interval for indicator refresh (ms)
    private static final long INDICATOR_UPDATE_INTERVAL_MS = 500;

    private final HylockConfig config;
    private final Map<UUID, IndicatorState> playerIndicators;

    public LockIndicatorManager(HylockConfig config) {
        this.config = config;
        this.playerIndicators = new ConcurrentHashMap<>();
        LOGGER.atInfo().log("[Hylock] LockIndicatorManager initialized");
    }

    /**
     * Show lock acquired indicator to player.
     *
     * @param playerId The player's UUID
     * @param playerRef The player reference for sending messages
     * @param target The locked target info
     */
    public void showLockAcquired(UUID playerId, PlayerRef playerRef, TargetInfo target) {
        if (!config.isShowLockIndicator()) {
            return;
        }

        IndicatorState state = new IndicatorState(playerRef, target);
        playerIndicators.put(playerId, state);

        // Send lock notification
        String targetType = target.isPlayer() ? "Player" : (target.isHostile() ? "Enemy" : "Entity");
        String message = String.format("§a§l⊕ LOCKED §r§7[%s] §f%s", targetType, target.getEntityName());
        sendIndicator(playerRef, message);

        // Play lock sound if enabled
        if (config.isPlayLockSound()) {
            playLockSound(playerRef, true);
        }

        LOGGER.atInfo().log("[Hylock] Lock indicator shown for player %s -> %s", playerId, target.getEntityName());
    }

    /**
     * Show lock released indicator to player.
     *
     * @param playerId The player's UUID
     */
    public void showLockReleased(UUID playerId) {
        IndicatorState state = playerIndicators.remove(playerId);
        if (state == null || !config.isShowLockIndicator()) {
            return;
        }

        String message = "§c§l⊗ UNLOCKED";
        sendIndicator(state.playerRef, message);

        // Play unlock sound if enabled
        if (config.isPlayLockSound()) {
            playLockSound(state.playerRef, false);
        }

        LOGGER.atInfo().log("[Hylock] Lock released indicator shown for player %s", playerId);
    }

    /**
     * Update the indicator with current target info (distance, health, etc.)
     *
     * @param playerId The player's UUID
     * @param playerX Player's current X position
     * @param playerY Player's current Y position
     * @param playerZ Player's current Z position
     * @param target The current locked target
     */
    public void updateIndicator(UUID playerId, double playerX, double playerY, double playerZ, TargetInfo target) {
        IndicatorState state = playerIndicators.get(playerId);
        if (state == null || !config.isShowLockIndicator()) {
            return;
        }

        // Check if enough time has passed since last update
        long now = System.currentTimeMillis();
        if (now - state.lastUpdateTime < INDICATOR_UPDATE_INTERVAL_MS) {
            return;
        }
        state.lastUpdateTime = now;

        // Calculate distance
        double distance = target.distanceFrom(playerX, playerY, playerZ);

        // Format distance color based on range
        String distanceColor = getDistanceColor(distance);

        // Build indicator message
        String targetType = target.isPlayer() ? "§b" : (target.isHostile() ? "§c" : "§e");
        String message = String.format("§7⊕ %s%s §7| %s%.1fm",
            targetType, target.getEntityName(),
            distanceColor, distance);

        sendIndicator(state.playerRef, message);
    }

    /**
     * Show target lost indicator (target died, despawned, etc.)
     *
     * @param playerId The player's UUID
     * @param reason The reason the target was lost
     */
    public void showTargetLost(UUID playerId, String reason) {
        IndicatorState state = playerIndicators.remove(playerId);
        if (state == null || !config.isShowLockIndicator()) {
            return;
        }

        String message = String.format("§e§l⊘ TARGET LOST §r§7(%s)", reason);
        sendIndicator(state.playerRef, message);

        LOGGER.atInfo().log("[Hylock] Target lost indicator shown for player %s: %s", playerId, reason);
    }

    /**
     * Get color code based on distance to target.
     */
    private String getDistanceColor(double distance) {
        double maxRange = config.getLockOnRange();
        double ratio = distance / maxRange;

        if (ratio < 0.5) {
            return "§a"; // Green - close
        } else if (ratio < 0.75) {
            return "§e"; // Yellow - medium
        } else {
            return "§c"; // Red - far (about to lose lock)
        }
    }

    /**
     * Send indicator message to player.
     * Uses action bar for non-intrusive display.
     */
    private void sendIndicator(PlayerRef playerRef, String message) {
        try {
            // Send as regular message for now
            // TODO: Use action bar when API is available
            playerRef.sendMessage(Message.raw(message));
        } catch (Exception e) {
            LOGGER.atWarning().log("[Hylock] Failed to send indicator: %s", e.getMessage());
        }
    }

    /**
     * Play lock/unlock sound effect.
     */
    private void playLockSound(PlayerRef playerRef, boolean isLock) {
        // TODO: Implement sound playback when Hytale sound API is available
        // For now, this is a placeholder
        // String soundId = isLock ? "hylock:lock_on" : "hylock:lock_off";
        // playerRef.playSound(soundId);
    }

    /**
     * Clean up indicator state for a disconnected player.
     */
    public void removePlayer(UUID playerId) {
        playerIndicators.remove(playerId);
    }

    /**
     * Check if a player has an active indicator.
     */
    public boolean hasIndicator(UUID playerId) {
        return playerIndicators.containsKey(playerId);
    }

    /**
     * Internal class to track indicator state per player.
     */
    private static class IndicatorState {
        final PlayerRef playerRef;
        final TargetInfo target;
        long lastUpdateTime;

        IndicatorState(PlayerRef playerRef, TargetInfo target) {
            this.playerRef = playerRef;
            this.target = target;
            this.lastUpdateTime = 0;
        }
    }
}
