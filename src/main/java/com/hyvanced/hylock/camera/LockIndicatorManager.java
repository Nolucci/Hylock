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
 */
public class LockIndicatorManager {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final long INDICATOR_UPDATE_INTERVAL_MS = 500;

    private final HylockConfig config;
    private final Map<UUID, IndicatorState> playerIndicators;

    /**
     * Constructs a new LockIndicatorManager.
     *
     * @param config the Hylock configuration
     */
    public LockIndicatorManager(HylockConfig config) {
        this.config = config;
        this.playerIndicators = new ConcurrentHashMap<>();
        LOGGER.atInfo().log("[Hylock] LockIndicatorManager initialized");
    }

    /**
     * Shows the lock acquired indicator to a player.
     *
     * @param playerId  the player's UUID
     * @param playerRef the player reference for sending messages
     * @param target    the locked target info
     */
    public void showLockAcquired(UUID playerId, PlayerRef playerRef, TargetInfo target) {
        if (!config.isShowLockIndicator()) {
            return;
        }

        IndicatorState state = new IndicatorState(playerRef, target);
        playerIndicators.put(playerId, state);

        String targetType = target.isPlayer() ? "Player" : (target.isHostile() ? "Enemy" : "Entity");
        String message = String.format("§a§l⊕ LOCKED §r§7[%s] §f%s", targetType, target.getEntityName());
        sendIndicator(playerRef, message);

        if (config.isPlayLockSound()) {
            playLockSound(playerRef, true);
        }

        LOGGER.atInfo().log("[Hylock] Lock indicator shown for player %s -> %s", playerId, target.getEntityName());
    }

    /**
     * Shows the lock released indicator to a player.
     *
     * @param playerId the player's UUID
     */
    public void showLockReleased(UUID playerId) {
        IndicatorState state = playerIndicators.remove(playerId);
        if (state == null || !config.isShowLockIndicator()) {
            return;
        }

        String message = "§c§l⊗ UNLOCKED";
        sendIndicator(state.playerRef, message);

        if (config.isPlayLockSound()) {
            playLockSound(state.playerRef, false);
        }

        LOGGER.atInfo().log("[Hylock] Lock released indicator shown for player %s", playerId);
    }

    /**
     * Updates the indicator with current target info.
     *
     * @param playerId the player's UUID
     * @param playerX  player's current X position
     * @param playerY  player's current Y position
     * @param playerZ  player's current Z position
     * @param target   the current locked target
     */
    public void updateIndicator(UUID playerId, double playerX, double playerY, double playerZ, TargetInfo target) {
        IndicatorState state = playerIndicators.get(playerId);
        if (state == null || !config.isShowLockIndicator()) {
            return;
        }

        long now = System.currentTimeMillis();
        if (now - state.lastUpdateTime < INDICATOR_UPDATE_INTERVAL_MS) {
            return;
        }
        state.lastUpdateTime = now;

        double distance = target.distanceFrom(playerX, playerY, playerZ);
        String distanceColor = getDistanceColor(distance);

        String targetType = target.isPlayer() ? "§b" : (target.isHostile() ? "§c" : "§e");
        String message = String.format("§7⊕ %s%s §7| %s%.1fm",
            targetType, target.getEntityName(),
            distanceColor, distance);

        sendIndicator(state.playerRef, message);
    }

    /**
     * Shows the target lost indicator to a player.
     *
     * @param playerId the player's UUID
     * @param reason   the reason the target was lost
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
     * Gets the color code based on distance to target.
     *
     * @param distance the distance to the target
     * @return the color code string
     */
    private String getDistanceColor(double distance) {
        double maxRange = config.getLockOnRange();
        double ratio = distance / maxRange;

        if (ratio < 0.5) {
            return "§a";
        } else if (ratio < 0.75) {
            return "§e";
        } else {
            return "§c";
        }
    }

    /**
     * Sends an indicator message to a player.
     *
     * @param playerRef the player reference
     * @param message   the message to send
     */
    private void sendIndicator(PlayerRef playerRef, String message) {
        try {
            playerRef.sendMessage(Message.raw(message));
        } catch (Exception e) {
            LOGGER.atWarning().log("[Hylock] Failed to send indicator: %s", e.getMessage());
        }
    }

    /**
     * Plays the lock/unlock sound effect.
     *
     * @param playerRef the player reference
     * @param isLock    true for lock sound, false for unlock sound
     */
    private void playLockSound(PlayerRef playerRef, boolean isLock) {
    }

    /**
     * Cleans up indicator state for a disconnected player.
     *
     * @param playerId the player's UUID
     */
    public void removePlayer(UUID playerId) {
        playerIndicators.remove(playerId);
    }

    /**
     * Checks if a player has an active indicator.
     *
     * @param playerId the player's UUID
     * @return true if the player has an active indicator
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

        /**
         * Constructs a new IndicatorState.
         *
         * @param playerRef the player reference
         * @param target    the locked target info
         */
        IndicatorState(PlayerRef playerRef, TargetInfo target) {
            this.playerRef = playerRef;
            this.target = target;
            this.lastUpdateTime = 0;
        }
    }
}
