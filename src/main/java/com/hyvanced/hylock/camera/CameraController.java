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
 *
 * The camera system:
 * - Smoothly rotates to face the locked target
 * - Works in both first-person and third-person views
 * - Uses Hytale's native lerp for smooth transitions
 * - Automatically releases when target is out of range
 */
public class CameraController {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private final HylockConfig config;
    private final Map<UUID, CameraState> playerCameraStates;

    public CameraController(HylockConfig config) {
        this.config = config;
        this.playerCameraStates = new ConcurrentHashMap<>();
        LOGGER.atInfo().log("[Hylock] CameraController initialized");
    }

    /**
     * Start camera lock for a player targeting an entity.
     *
     * @param playerId The player's UUID
     * @param playerRef The player reference for sending packets
     */
    public void startCameraLock(UUID playerId, PlayerRef playerRef) {
        CameraState state = new CameraState(playerRef);
        playerCameraStates.put(playerId, state);
        LOGGER.atInfo().log("[Hylock] Camera lock started for player %s", playerId);
    }

    /**
     * Stop camera lock for a player and reset to normal camera.
     *
     * @param playerId The player's UUID
     */
    public void stopCameraLock(UUID playerId) {
        CameraState state = playerCameraStates.remove(playerId);
        if (state != null && state.playerRef != null) {
            // Reset camera to default (player-controlled)
            resetCamera(state.playerRef);
            LOGGER.atInfo().log("[Hylock] Camera lock stopped for player %s", playerId);
        }
    }

    /**
     * Update the camera for a player to look at their locked target.
     * Should be called every tick for smooth camera movement.
     *
     * @param playerId The player's UUID
     * @param playerX Player's current X position
     * @param playerY Player's current Y position
     * @param playerZ Player's current Z position
     * @param target The locked target info
     * @return true if camera was updated, false if player has no camera lock
     */
    public boolean updateCamera(UUID playerId, double playerX, double playerY, double playerZ, TargetInfo target) {
        CameraState state = playerCameraStates.get(playerId);
        if (state == null || state.playerRef == null || target == null) {
            return false;
        }

        // Calculate direction to target
        double targetX = target.getLastKnownX();
        double targetY = target.getLastKnownY() + config.getCameraHeightOffset();
        double targetZ = target.getLastKnownZ();

        double playerEyeY = playerY + config.getCameraHeightOffset();

        // Calculate yaw (horizontal angle)
        double dx = targetX - playerX;
        double dz = targetZ - playerZ;
        float targetYaw = (float) Math.toDegrees(Math.atan2(-dx, dz));

        // Calculate pitch (vertical angle)
        double dy = targetY - playerEyeY;
        double horizontalDist = Math.sqrt(dx * dx + dz * dz);
        float targetPitch = (float) Math.toDegrees(-Math.atan2(dy, horizontalDist));

        // Clamp pitch to reasonable values
        targetPitch = Math.max(-89.0f, Math.min(89.0f, targetPitch));

        // Apply smooth interpolation locally (for tracking state)
        float smoothing = (float) config.getCameraSmoothing();
        float currentYaw = state.currentYaw;
        float currentPitch = state.currentPitch;

        // Smooth yaw (handle wraparound at 180/-180 degrees)
        float yawDiff = normalizeAngle(targetYaw - currentYaw);
        float newYaw = currentYaw + yawDiff * (1.0f - smoothing);

        // Smooth pitch
        float newPitch = currentPitch + (targetPitch - currentPitch) * (1.0f - smoothing);

        // Store the new values
        state.currentYaw = normalizeAngle(newYaw);
        state.currentPitch = newPitch;

        // Apply camera rotation using Hytale's camera packet system
        applyCameraRotation(state.playerRef, state.currentYaw, state.currentPitch);

        return true;
    }

    /**
     * Normalize angle to be within -180 to 180 degrees
     */
    private float normalizeAngle(float angle) {
        while (angle > 180.0f) angle -= 360.0f;
        while (angle < -180.0f) angle += 360.0f;
        return angle;
    }

    /**
     * Apply camera rotation to a player using Hytale's camera packet system.
     */
    private void applyCameraRotation(PlayerRef playerRef, float yawDegrees, float pitchDegrees) {
        try {
            // Convert to radians for Hytale's Direction class
            float yawRadians = (float) Math.toRadians(yawDegrees);
            float pitchRadians = (float) Math.toRadians(pitchDegrees);

            // Create camera settings with custom rotation
            ServerCameraSettings settings = new ServerCameraSettings();

            // Set the rotation using Direction (yaw, pitch, roll) in radians
            settings.rotation = new Direction(yawRadians, pitchRadians, 0.0f);

            // Use custom rotation type to apply our rotation
            settings.rotationType = RotationType.Custom;

            // Apply smooth interpolation using Hytale's native lerp
            // Lower values = smoother but slower response (0.0-1.0)
            float lerpSpeed = 1.0f - (float) config.getCameraSmoothing();
            lerpSpeed = Math.max(0.05f, Math.min(0.95f, lerpSpeed));
            settings.rotationLerpSpeed = lerpSpeed;
            settings.positionLerpSpeed = lerpSpeed;

            // Send camera packet to player
            // ClientCameraView.Custom = full server control
            // true = override player camera input
            SetServerCamera packet = new SetServerCamera(
                ClientCameraView.Custom,
                true,  // Lock camera control
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
     * Reset camera to normal player-controlled mode.
     */
    private void resetCamera(PlayerRef playerRef) {
        try {
            // Return to player-controlled camera
            // Pass false to unlock camera control and null for default settings
            SetServerCamera packet = new SetServerCamera(
                ClientCameraView.Custom,
                false,  // Don't lock camera - return control to player
                null    // Use default settings
            );

            playerRef.getPacketHandler().writeNoCache(packet);

            LOGGER.atInfo().log("[Hylock] Camera reset to player control");
        } catch (Exception e) {
            LOGGER.atWarning().log("[Hylock] Failed to reset camera: %s", e.getMessage());
        }
    }

    /**
     * Check if a player has an active camera lock.
     */
    public boolean hasActiveLock(UUID playerId) {
        return playerCameraStates.containsKey(playerId);
    }

    /**
     * Get current camera yaw for a player.
     */
    public float getCurrentYaw(UUID playerId) {
        CameraState state = playerCameraStates.get(playerId);
        return state != null ? state.currentYaw : 0f;
    }

    /**
     * Get current camera pitch for a player.
     */
    public float getCurrentPitch(UUID playerId) {
        CameraState state = playerCameraStates.get(playerId);
        return state != null ? state.currentPitch : 0f;
    }

    /**
     * Clean up camera state for a disconnected player.
     */
    public void removePlayer(UUID playerId) {
        playerCameraStates.remove(playerId);
    }

    /**
     * Get all player IDs that currently have active camera locks.
     */
    public Set<UUID> getLockedPlayerIds() {
        return Collections.unmodifiableSet(playerCameraStates.keySet());
    }

    /**
     * Get the PlayerRef for a player with an active camera lock.
     */
    public PlayerRef getPlayerRef(UUID playerId) {
        CameraState state = playerCameraStates.get(playerId);
        return state != null ? state.playerRef : null;
    }

    /**
     * Get the last known position for a player.
     * Returns null if no position has been recorded.
     */
    public double[] getLastKnownPosition(UUID playerId) {
        CameraState state = playerCameraStates.get(playerId);
        if (state == null || !state.hasPosition) {
            return null;
        }
        return new double[] { state.lastX, state.lastY, state.lastZ };
    }

    /**
     * Update the last known position for a player.
     * Should be called when position data is available from events.
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
