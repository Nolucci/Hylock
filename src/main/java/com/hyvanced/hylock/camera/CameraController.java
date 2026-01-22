package com.hyvanced.hylock.camera;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.protocol.Direction;
import com.hypixel.hytale.protocol.ModelTransform;
import com.hypixel.hytale.protocol.packets.player.ClientTeleport;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hyvanced.hylock.config.HylockConfig;
import com.hyvanced.hylock.lockon.TargetInfo;

/**
 * Controls the camera for players with locked targets.
 * Uses ClientTeleport packet with lookOrientation to smoothly follow locked
 * entities
 * while preserving player movement controls.
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
     * Starts camera lock for a player targeting an entity.
     * Initializes with the player's current look direction.
     */
    public void startCameraLock(UUID playerId, PlayerRef playerRef) {
        float initialYaw = 0f;
        float initialPitch = 0f;

        Vector3f headRotation = playerRef.getHeadRotation();
        if (headRotation != null) {
            initialYaw = (float) Math.toDegrees(headRotation.getYaw());
            initialPitch = (float) Math.toDegrees(headRotation.getPitch());
        }

        CameraState state = new CameraState(playerRef, initialYaw, initialPitch);
        playerCameraStates.put(playerId, state);
        LOGGER.atInfo().log("[Hylock] Camera lock started for player %s (yaw=%.1f, pitch=%.1f)",
                playerId, initialYaw, initialPitch);
    }

    /**
     * Stops camera lock for a player.
     */
    public void stopCameraLock(UUID playerId) {
        CameraState state = playerCameraStates.remove(playerId);
        if (state != null) {
            LOGGER.atInfo().log("[Hylock] Camera lock stopped for player %s", playerId);
        }
    }

    /**
     * Updates the camera for a player to look at their locked target.
     * Uses ClientTeleport packet with lookOrientation to rotate the player.
     */
    public boolean updateCamera(UUID playerId, double playerX, double playerY, double playerZ,
            TargetInfo target, Store<EntityStore> store, Ref<EntityStore> entityRef) {
        CameraState state = playerCameraStates.get(playerId);
        if (state == null || state.playerRef == null || target == null) {
            return false;
        }

        double targetX = target.getLastKnownX();
        // Centrer la caméra sur le milieu du modèle de l'entité au lieu du haut
        double targetY = target.getLastKnownY() + (config.getCameraHeightOffset() * 0.5); // Ajustement pour centrer sur
                                                                                          // le corps
        double targetZ = target.getLastKnownZ();

        double playerEyeY = playerY + config.getCameraHeightOffset();

        double dx = targetX - playerX;
        double dz = targetZ - playerZ;
        // Hytale yaw: 0 = looking at +Z, 90 = looking at -X (standard Minecraft-like
        // convention)
        // atan2(dx, dz) gives angle from +Z axis, rotating clockwise
        float targetYaw = (float) Math.toDegrees(Math.atan2(-dx, -dz));

        double dy = targetY - playerEyeY;
        double horizontalDist = Math.sqrt(dx * dx + dz * dz);
        // Pitch: if target is above (dy > 0), we look up (negative pitch in Hytale)
        // if target is below (dy < 0), we look down (positive pitch in Hytale)
        float targetPitch = (float) Math.toDegrees(Math.atan2(dy, horizontalDist));

        targetPitch = Math.max(-89.0f, Math.min(89.0f, targetPitch));

        // Smoothing: 1.0 = instant snap to target (no interpolation)
        float lerpFactor = 1.0f;

        float yawDiff = normalizeAngle(targetYaw - state.currentYaw);
        float newYaw = state.currentYaw + yawDiff * lerpFactor;

        float newPitch = state.currentPitch + (targetPitch - state.currentPitch) * lerpFactor;

        state.currentYaw = normalizeAngle(newYaw);
        state.currentPitch = newPitch;

        applyLookDirection(state.playerRef, state.currentYaw, state.currentPitch);

        return true;
    }

    /**
     * Normalizes an angle to be within -180 to 180 degrees.
     */
    private float normalizeAngle(float angle) {
        while (angle > 180.0f)
            angle -= 360.0f;
        while (angle < -180.0f)
            angle += 360.0f;
        return angle;
    }

    /**
     * Applies look direction to the player using ClientTeleport packet.
     * Only sets lookOrientation, leaving position unchanged.
     */
    private void applyLookDirection(PlayerRef playerRef, float yawDegrees, float pitchDegrees) {
        try {
            float yawRadians = (float) Math.toRadians(yawDegrees);
            float pitchRadians = (float) Math.toRadians(pitchDegrees);

            Direction lookDirection = new Direction(yawRadians, pitchRadians, 0f);

            ModelTransform transform = new ModelTransform(null, null, lookDirection);

            ClientTeleport packet = new ClientTeleport((byte) 0, transform, false);

            playerRef.getPacketHandler().writeNoCache(packet);

            LOGGER.atFine().log("[Hylock] Look direction sent: yaw=%.2f°, pitch=%.2f°",
                    yawDegrees, pitchDegrees);

        } catch (Exception e) {
            LOGGER.atWarning().log("[Hylock] Failed to apply look direction: %s", e.getMessage());
        }
    }

    /**
     * Updates the camera using cached positions - can be called from any thread.
     * This is the fast path that only calculates angles and sends packets.
     */
    public void updateCameraFromCache(UUID playerId, double playerX, double playerY, double playerZ,
            double targetX, double targetY, double targetZ) {
        CameraState state = playerCameraStates.get(playerId);
        if (state == null || state.playerRef == null) {
            return;
        }

        // Centrer la caméra sur le milieu du modèle de l'entité au lieu du haut
        double adjustedTargetY = targetY + (config.getCameraHeightOffset() * 0.5); // Ajustement pour centrer sur le
                                                                                   // corps
        double playerEyeY = playerY + config.getCameraHeightOffset();

        double dx = targetX - playerX;
        double dz = targetZ - playerZ;
        float targetYaw = (float) Math.toDegrees(Math.atan2(-dx, -dz));

        double dy = adjustedTargetY - playerEyeY;
        double horizontalDist = Math.sqrt(dx * dx + dz * dz);
        float targetPitch = (float) Math.toDegrees(Math.atan2(dy, horizontalDist));

        targetPitch = Math.max(-89.0f, Math.min(89.0f, targetPitch));

        // Direct assignment for maximum responsiveness
        state.currentYaw = normalizeAngle(targetYaw);
        state.currentPitch = targetPitch;

        applyLookDirection(state.playerRef, state.currentYaw, state.currentPitch);
    }

    public boolean hasActiveLock(UUID playerId) {
        return playerCameraStates.containsKey(playerId);
    }

    public float getCurrentYaw(UUID playerId) {
        CameraState state = playerCameraStates.get(playerId);
        return state != null ? state.currentYaw : 0f;
    }

    public float getCurrentPitch(UUID playerId) {
        CameraState state = playerCameraStates.get(playerId);
        return state != null ? state.currentPitch : 0f;
    }

    public void removePlayer(UUID playerId) {
        playerCameraStates.remove(playerId);
    }

    public Set<UUID> getLockedPlayerIds() {
        return Collections.unmodifiableSet(playerCameraStates.keySet());
    }

    public PlayerRef getPlayerRef(UUID playerId) {
        CameraState state = playerCameraStates.get(playerId);
        return state != null ? state.playerRef : null;
    }

    public double[] getLastKnownPosition(UUID playerId) {
        CameraState state = playerCameraStates.get(playerId);
        if (state == null || !state.hasPosition) {
            return null;
        }
        return new double[] { state.lastX, state.lastY, state.lastZ };
    }

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
     * Gets the entity reference for a player.
     */
    public Ref<EntityStore> getEntityRef(UUID playerId) {
        CameraState state = playerCameraStates.get(playerId);
        if (state != null && state.playerRef != null) {
            return state.playerRef.getReference();
        }
        return null;
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

        CameraState(PlayerRef playerRef, float initialYaw, float initialPitch) {
            this.playerRef = playerRef;
            this.currentYaw = initialYaw;
            this.currentPitch = initialPitch;
            this.lastX = 0;
            this.lastY = 0;
            this.lastZ = 0;
            this.hasPosition = false;
        }
    }
}
