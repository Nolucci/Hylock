package com.hyvanced.hylock.camera;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hyvanced.hylock.HylockPlugin;
import com.hyvanced.hylock.config.HylockConfig;
import com.hyvanced.hylock.lockon.LockOnManager;
import com.hyvanced.hylock.lockon.LockOnState;
import com.hyvanced.hylock.lockon.TargetInfo;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Handles tick-based updates for the camera lock-on system.
 * Runs at a fixed rate to smoothly update camera positions for all locked players.
 *
 * The task:
 * - Updates camera rotation for each player with an active lock
 * - Checks for out-of-range targets and releases locks
 * - Updates indicators with current target distance
 *
 * Note: This task uses the last known positions stored in TargetInfo and CameraController.
 * For real-time position tracking, the positions should be updated from the main game thread.
 */
public class CameraUpdateTask implements Runnable {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    // Update rate: 20 times per second (50ms interval) for smooth camera
    private static final long UPDATE_INTERVAL_MS = 50;

    private final HylockPlugin plugin;
    private final ScheduledExecutorService scheduler;
    private ScheduledFuture<?> taskHandle;
    private volatile boolean running;

    public CameraUpdateTask(HylockPlugin plugin) {
        this.plugin = plugin;
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "Hylock-CameraUpdate");
            t.setDaemon(true);
            return t;
        });
        this.running = false;
    }

    /**
     * Start the camera update task.
     */
    public void start() {
        if (running) {
            return;
        }
        running = true;
        taskHandle = scheduler.scheduleAtFixedRate(this, 0, UPDATE_INTERVAL_MS, TimeUnit.MILLISECONDS);
        LOGGER.atInfo().log("[Hylock] Camera update task started (interval: %dms)", UPDATE_INTERVAL_MS);
    }

    /**
     * Stop the camera update task.
     */
    public void stop() {
        running = false;
        if (taskHandle != null) {
            taskHandle.cancel(false);
            taskHandle = null;
        }
        LOGGER.atInfo().log("[Hylock] Camera update task stopped");
    }

    /**
     * Shutdown the scheduler completely.
     */
    public void shutdown() {
        stop();
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(1, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
        LOGGER.atInfo().log("[Hylock] Camera update scheduler shutdown complete");
    }

    @Override
    public void run() {
        if (!running) {
            return;
        }

        try {
            updateAllLockedPlayers();
        } catch (Exception e) {
            LOGGER.atWarning().log("[Hylock] Error in camera update task: %s", e.getMessage());
        }
    }

    /**
     * Update camera for all players with active locks.
     */
    private void updateAllLockedPlayers() {
        LockOnManager lockManager = plugin.getLockOnManager();
        CameraController cameraController = plugin.getCameraController();
        LockIndicatorManager indicatorManager = plugin.getLockIndicatorManager();

        if (lockManager == null || cameraController == null) {
            return;
        }

        // Get all players with active camera locks from the CameraController
        Set<UUID> lockedPlayerIds = new HashSet<>(cameraController.getLockedPlayerIds());

        for (UUID playerId : lockedPlayerIds) {
            try {
                updatePlayerCamera(playerId, lockManager, cameraController, indicatorManager);
            } catch (Exception e) {
                LOGGER.atWarning().log("[Hylock] Failed to update camera for player %s: %s",
                    playerId, e.getMessage());
            }
        }
    }

    /**
     * Update camera for a single player.
     */
    private void updatePlayerCamera(UUID playerId, LockOnManager lockManager,
                                     CameraController cameraController, LockIndicatorManager indicatorManager) {
        // Get the PlayerRef from CameraController
        PlayerRef playerRef = cameraController.getPlayerRef(playerId);
        if (playerRef == null) {
            cameraController.stopCameraLock(playerId);
            return;
        }

        // Check if player still has a lock in the LockOnManager
        if (lockManager.getState(playerId) != LockOnState.LOCKED) {
            // Player no longer locked, stop their camera lock
            cameraController.stopCameraLock(playerId);
            if (indicatorManager != null) {
                indicatorManager.showLockReleased(playerId);
            }
            return;
        }

        // Get target info
        TargetInfo target = lockManager.getLockedTarget(playerId);
        if (target == null || !target.isValid()) {
            // Target invalid, release lock
            lockManager.releaseLock(playerId);
            cameraController.stopCameraLock(playerId);
            if (indicatorManager != null) {
                indicatorManager.showTargetLost(playerId, "Target lost");
            }
            return;
        }

        // Get player's last known position from CameraController
        // The position is updated when events occur (lock acquired, attacks, etc.)
        double[] playerPos = cameraController.getLastKnownPosition(playerId);
        if (playerPos == null) {
            // No position available yet, use target's position as estimate
            // This will be corrected when next event updates the position
            return;
        }

        // Check distance and release if too far
        HylockConfig config = plugin.getConfig();
        double distance = target.distanceFrom(playerPos[0], playerPos[1], playerPos[2]);
        if (distance > config.getLockOnRange()) {
            // Target out of range, release lock
            lockManager.releaseLock(playerId);
            cameraController.stopCameraLock(playerId);
            if (indicatorManager != null) {
                indicatorManager.showTargetLost(playerId, "Out of range");
            }

            LOGGER.atInfo().log("[Hylock] Target out of range (%.1f blocks), releasing lock for %s",
                distance, playerId);
            return;
        }

        // Update camera to face target
        cameraController.updateCamera(playerId, playerPos[0], playerPos[1], playerPos[2], target);

        // Update indicator with current distance
        if (indicatorManager != null) {
            indicatorManager.updateIndicator(playerId, playerPos[0], playerPos[1], playerPos[2], target);
        }

        // Also call lock manager update for any additional logic
        lockManager.update(playerId, playerPos[0], playerPos[1], playerPos[2]);
    }

    /**
     * Check if the task is currently running.
     */
    public boolean isRunning() {
        return running;
    }
}
