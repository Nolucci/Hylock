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
 */
public class CameraUpdateTask implements Runnable {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final long UPDATE_INTERVAL_MS = 50;

    private final HylockPlugin plugin;
    private final ScheduledExecutorService scheduler;
    private ScheduledFuture<?> taskHandle;
    private volatile boolean running;

    /**
     * Constructs a new CameraUpdateTask.
     *
     * @param plugin the Hylock plugin instance
     */
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
     * Starts the camera update task.
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
     * Stops the camera update task.
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
     * Shuts down the scheduler completely.
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

    /**
     * Runs the camera update task.
     */
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
     * Updates the camera for all players with active locks.
     */
    private void updateAllLockedPlayers() {
        LockOnManager lockManager = plugin.getLockOnManager();
        CameraController cameraController = plugin.getCameraController();
        LockIndicatorManager indicatorManager = plugin.getLockIndicatorManager();

        if (lockManager == null || cameraController == null) {
            return;
        }

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
     * Updates the camera for a single player.
     *
     * @param playerId         the player's UUID
     * @param lockManager      the lock-on manager
     * @param cameraController the camera controller
     * @param indicatorManager the indicator manager
     */
    private void updatePlayerCamera(UUID playerId, LockOnManager lockManager,
                                     CameraController cameraController, LockIndicatorManager indicatorManager) {
        PlayerRef playerRef = cameraController.getPlayerRef(playerId);
        if (playerRef == null) {
            cameraController.stopCameraLock(playerId);
            return;
        }

        if (lockManager.getState(playerId) != LockOnState.LOCKED) {
            cameraController.stopCameraLock(playerId);
            if (indicatorManager != null) {
                indicatorManager.showLockReleased(playerId);
            }
            return;
        }

        TargetInfo target = lockManager.getLockedTarget(playerId);
        if (target == null || !target.isValid()) {
            lockManager.releaseLock(playerId);
            cameraController.stopCameraLock(playerId);
            if (indicatorManager != null) {
                indicatorManager.showTargetLost(playerId, "Target lost");
            }
            return;
        }

        double[] playerPos = cameraController.getLastKnownPosition(playerId);
        if (playerPos == null) {
            return;
        }

        HylockConfig config = plugin.getConfig();
        double distance = target.distanceFrom(playerPos[0], playerPos[1], playerPos[2]);
        if (distance > config.getLockOnRange()) {
            lockManager.releaseLock(playerId);
            cameraController.stopCameraLock(playerId);
            if (indicatorManager != null) {
                indicatorManager.showTargetLost(playerId, "Out of range");
            }

            LOGGER.atInfo().log("[Hylock] Target out of range (%.1f blocks), releasing lock for %s",
                distance, playerId);
            return;
        }

        cameraController.updateCamera(playerId, playerPos[0], playerPos[1], playerPos[2], target);

        if (indicatorManager != null) {
            indicatorManager.updateIndicator(playerId, playerPos[0], playerPos[1], playerPos[2], target);
        }

        lockManager.update(playerId, playerPos[0], playerPos[1], playerPos[2]);
    }

    /**
     * Checks if the task is currently running.
     *
     * @return true if running
     */
    public boolean isRunning() {
        return running;
    }
}
