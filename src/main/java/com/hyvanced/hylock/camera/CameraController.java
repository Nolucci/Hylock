package com.hyvanced.hylock.camera;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.protocol.ClientCameraView;
import com.hypixel.hytale.protocol.Direction;
import com.hypixel.hytale.protocol.RotationType;
import com.hypixel.hytale.protocol.ServerCameraSettings;
import com.hypixel.hytale.protocol.packets.camera.SetServerCamera;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hyvanced.hylock.config.HylockConfig;
import com.hyvanced.hylock.lockon.TargetInfo;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Controls the camera for players with locked targets.
 * Handles smooth camera interpolation to follow locked entities.
 */
public class CameraController {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private final HylockConfig config;
    private final Map<UUID, CameraState> playerCameraStates;

    /**
     * Constructs a new CameraController.
     *
     * @param config the Hylock configuration
     */
    public CameraController(HylockConfig config) {
        this.config = config;
        this.playerCameraStates = new ConcurrentHashMap<>();
        LOGGER.atInfo().log("[Hylock] CameraController initialized");
    }

    /**
     * Starts camera lock for a player targeting an entity.
     *
     * @param playerId  the player's UUID
     * @param playerRef the player reference for sending packets
     */
    public void startCameraLock(UUID playerId, PlayerRef playerRef) {
        CameraState state = new CameraState(playerRef);
        playerCameraStates.put(playerId, state);
        LOGGER.atInfo().log("[Hylock] Camera lock started for player %s", playerId);
    }

    /**
     * Stops camera lock for a player and resets to normal camera.
     *
     * @param playerId the player's UUID
     */
    public void stopCameraLock(UUID playerId) {
        CameraState state = playerCameraStates.remove(playerId);
        if (state != null && state.playerRef != null) {
            resetCamera(state.playerRef);
            LOGGER.atInfo().log("[Hylock] Camera lock stopped for player %s", playerId);
        }
    }

    /**
     * Updates the camera for a player to look at their locked target.
     *
     * @param playerId the player's UUID
     * @param playerX  player's current X position
     * @param playerY  player's current Y position
     * @param playerZ  player's current Z position
     * @param target   the locked target info
     * @return true if camera was updated, false if player has no camera lock
     */
    public boolean updateCamera(UUID playerId, double playerX, double playerY, double playerZ, TargetInfo target) {
        CameraState state = playerCameraStates.get(playerId);
        if (state == null || state.playerRef == null || target == null) {
            return false;
        }

        double targetX = target.getLastKnownX();
        double targetY = target.getLastKnownY() + config.getCameraHeightOffset();
        double targetZ = target.getLastKnownZ();

        double playerEyeY = playerY + config.getCameraHeightOffset();

        double dx = targetX - playerX;
        double dz = targetZ - playerZ;
        float targetYaw = (float) Math.toDegrees(Math.atan2(-dx, dz));

        double dy = targetY - playerEyeY;
        double horizontalDist = Math.sqrt(dx * dx + dz * dz);
        float targetPitch = (float) Math.toDegrees(-Math.atan2(dy, horizontalDist));

        targetPitch = Math.max(-89.0f, Math.min(89.0f, targetPitch));

        float smoothing = (float) config.getCameraSmoothing();
        float currentYaw = state.currentYaw;
        float currentPitch = state.currentPitch;

        float yawDiff = normalizeAngle(targetYaw - currentYaw);
        float newYaw = currentYaw + yawDiff * (1.0f - smoothing);

        float newPitch = currentPitch + (targetPitch - currentPitch) * (1.0f - smoothing);

        state.currentYaw = normalizeAngle(newYaw);
        state.currentPitch = newPitch;

        applyCameraRotation(state.playerRef, state.currentYaw, state.currentPitch);

        return true;
    }

    /**
     * Normalizes an angle to be within -180 to 180 degrees.
     *
     * @param angle the angle to normalize
     * @return the normalized angle
     */
    private float normalizeAngle(float angle) {
        while (angle > 180.0f) angle -= 360.0f;
        while (angle < -180.0f) angle += 360.0f;
        return angle;
    }

    /**
     * Applies camera rotation to a player using Hytale's camera packet system.
     *
     * @param playerRef   the player reference
     * @param yawDegrees  the yaw angle in degrees
     * @param pitchDegrees the pitch angle in degrees
     */
    private void applyCameraRotation(PlayerRef playerRef, float yawDegrees, float pitchDegrees) {
        try {
            float yawRadians = (float) Math.toRadians(yawDegrees);
            float pitchRadians = (float) Math.toRadians(pitchDegrees);

            ServerCameraSettings settings = new ServerCameraSettings();

            settings.rotation = new Direction(yawRadians, pitchRadians, 0.0f);
            settings.rotationType = RotationType.Custom;

            float lerpSpeed = 1.0f - (float) config.getCameraSmoothing();
            lerpSpeed = Math.max(0.05f, Math.min(0.95f, lerpSpeed));
            settings.rotationLerpSpeed = lerpSpeed;
            settings.positionLerpSpeed = lerpSpeed;

            SetServerCamera packet = new SetServerCamera(
                ClientCameraView.Custom,
                true,
                settings
            );

            playerRef.getPacketHandler().writeNoCache(packet);

            LOGGER.atFine().log("[Hylock] Camera packet sent: yaw=%.2f°, pitch=%.2f°",
                yawDegrees, pitchDegrees);

        } catch (Exception e) {
            LOGGER.atWarning().log("[Hylock] Failed to apply camera rotation: %s", e.getMessage());
        }
    }

    /**
     * Resets the camera to normal player-controlled mode.
     *
     * @param playerRef the player reference
     */
    private void resetCamera(PlayerRef playerRef) {
        try {
            SetServerCamera packet = new SetServerCamera(
                ClientCameraView.Custom,
                false,
                null
            );

            playerRef.getPacketHandler().writeNoCache(packet);

            LOGGER.atInfo().log("[Hylock] Camera reset to player control");
        } catch (Exception e) {
            LOGGER.atWarning().log("[Hylock] Failed to reset camera: %s", e.getMessage());
        }
    }

    /**
     * Checks if a player has an active camera lock.
     *
     * @param playerId the player's UUID
     * @return true if the player has an active lock
     */
    public boolean hasActiveLock(UUID playerId) {
        return playerCameraStates.containsKey(playerId);
    }

    /**
     * Gets the current camera yaw for a player.
     *
     * @param playerId the player's UUID
     * @return the current yaw in degrees
     */
    public float getCurrentYaw(UUID playerId) {
        CameraState state = playerCameraStates.get(playerId);
        return state != null ? state.currentYaw : 0f;
    }

    /**
     * Gets the current camera pitch for a player.
     *
     * @param playerId the player's UUID
     * @return the current pitch in degrees
     */
    public float getCurrentPitch(UUID playerId) {
        CameraState state = playerCameraStates.get(playerId);
        return state != null ? state.currentPitch : 0f;
    }

    /**
     * Cleans up camera state for a disconnected player.
     *
     * @param playerId the player's UUID
     */
    public void removePlayer(UUID playerId) {
        playerCameraStates.remove(playerId);
    }

    /**
     * Gets all player IDs that currently have active camera locks.
     *
     * @return an unmodifiable set of player UUIDs
     */
    public Set<UUID> getLockedPlayerIds() {
        return Collections.unmodifiableSet(playerCameraStates.keySet());
    }

    /**
     * Gets the PlayerRef for a player with an active camera lock.
     *
     * @param playerId the player's UUID
     * @return the player reference, or null if no active lock
     */
    public PlayerRef getPlayerRef(UUID playerId) {
        CameraState state = playerCameraStates.get(playerId);
        return state != null ? state.playerRef : null;
    }

    /**
     * Gets the last known position for a player.
     *
     * @param playerId the player's UUID
     * @return an array of [x, y, z] coordinates, or null if no position recorded
     */
    public double[] getLastKnownPosition(UUID playerId) {
        CameraState state = playerCameraStates.get(playerId);
        if (state == null || !state.hasPosition) {
            return null;
        }
        return new double[] { state.lastX, state.lastY, state.lastZ };
    }

    /**
     * Updates the last known position for a player.
     *
     * @param playerId the player's UUID
     * @param x        the X coordinate
     * @param y        the Y coordinate
     * @param z        the Z coordinate
     */
    public void updatePlayerPosition(UUID playerId, double x, double y, double z) {
        CameraState state = playerCameraStates.get(playerId);
        if (state != null) {
            state.lastX = x;
            state.lastY = y;
            state.lastZ = z;
            state.hasPosition = true;
        }
    }

    /**
     * Internal class to track camera state per player.
     */
    private static class CameraState {
        final PlayerRef playerRef;
        float currentYaw;
        float currentPitch;
        double lastX;
        double lastY;
        double lastZ;
        boolean hasPosition;

        /**
         * Constructs a new CameraState.
         *
         * @param playerRef the player reference
         */
        CameraState(PlayerRef playerRef) {
            this.playerRef = playerRef;
            this.currentYaw = 0f;
            this.currentPitch = 0f;
            this.lastX = 0;
            this.lastY = 0;
            this.lastZ = 0;
            this.hasPosition = false;
        }
    }
}
