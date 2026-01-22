package com.hyvanced.hylock.camera;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hyvanced.hylock.HylockPlugin;
import com.hyvanced.hylock.config.HylockConfig;
import com.hyvanced.hylock.lockon.LockOnManager;
import com.hyvanced.hylock.lockon.LockOnState;
import com.hyvanced.hylock.lockon.TargetInfo;

/**
 * Handles tick-based updates for the camera lock-on system.
 * Uses a hybrid approach: position reading on WorldThread, packet sending on
 * scheduler thread.
 */
public class CameraUpdateTask implements Runnable {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final long UPDATE_INTERVAL_MS = 2; // Plus fréquent pour une meilleure fluidité
    private static final long POSITION_UPDATE_INTERVAL_MS = 16; // ~60 times per second for position sync

    private final HylockPlugin plugin;
    private final ScheduledExecutorService scheduler;
    private ScheduledFuture<?> taskHandle;
    private ScheduledFuture<?> positionTaskHandle;
    private volatile boolean running;

    // Cached positions updated by WorldThread
    private final Map<UUID, CachedPositions> positionCache = new ConcurrentHashMap<>();

    public CameraUpdateTask(HylockPlugin plugin) {
        this.plugin = plugin;
        this.scheduler = Executors.newScheduledThreadPool(2, r -> {
            Thread t = new Thread(r, "Hylock-CameraUpdate");
            t.setDaemon(true);
            return t;
        });
        this.running = false;
    }

    public void start() {
        if (running) {
            return;
        }
        running = true;
        // Fast task for sending camera updates
        taskHandle = scheduler.scheduleAtFixedRate(this, 0, UPDATE_INTERVAL_MS, TimeUnit.MILLISECONDS);
        // Slower task for syncing positions from WorldThread
        positionTaskHandle = scheduler.scheduleAtFixedRate(this::syncPositionsFromWorld, 0, POSITION_UPDATE_INTERVAL_MS,
                TimeUnit.MILLISECONDS);
        LOGGER.atInfo().log("[Hylock] Camera update task started (camera: %dms, positions: %dms)",
                UPDATE_INTERVAL_MS, POSITION_UPDATE_INTERVAL_MS);
    }

    public void stop() {
        running = false;
        if (taskHandle != null) {
            taskHandle.cancel(false);
            taskHandle = null;
        }
        if (positionTaskHandle != null) {
            positionTaskHandle.cancel(false);
            positionTaskHandle = null;
        }
        positionCache.clear();
        LOGGER.atInfo().log("[Hylock] Camera update task stopped");
    }

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
     * Syncs positions from WorldThread - runs at 30 FPS.
     */
    private void syncPositionsFromWorld() {
        if (!running)
            return;

        CameraController cameraController = plugin.getCameraController();
        if (cameraController == null)
            return;

        Set<UUID> lockedPlayerIds = new HashSet<>(cameraController.getLockedPlayerIds());

        for (UUID playerId : lockedPlayerIds) {
            try {
                Ref<EntityStore> entityRef = cameraController.getEntityRef(playerId);
                if (entityRef == null)
                    continue;

                Store<EntityStore> store = entityRef.getStore();
                if (store == null || store.getExternalData() == null)
                    continue;

                World world = store.getExternalData().getWorld();
                if (world == null)
                    continue;

                // Schedule position reading on WorldThread
                world.execute(() -> updatePositionCache(playerId, store, entityRef));

            } catch (Exception e) {
                // Silently ignore
            }
        }
    }

    /**
     * Updates the position cache - runs on WorldThread.
     */
    private void updatePositionCache(UUID playerId, Store<EntityStore> store, Ref<EntityStore> entityRef) {
        LockOnManager lockManager = plugin.getLockOnManager();
        CameraController cameraController = plugin.getCameraController();
        LockIndicatorManager indicatorManager = plugin.getLockIndicatorManager();

        if (lockManager == null || cameraController == null)
            return;

        if (lockManager.getState(playerId) != LockOnState.LOCKED) {
            cameraController.stopCameraLock(playerId);
            positionCache.remove(playerId);
            return;
        }

        TargetInfo target = lockManager.getLockedTarget(playerId);
        if (target == null || !target.isValid()) {
            lockManager.releaseLock(playerId);
            cameraController.stopCameraLock(playerId);
            positionCache.remove(playerId);
            return;
        }

        // Valider la référence d'entité du joueur avant d'y accéder
        if (!entityRef.isValid()) {
            lockManager.releaseLock(playerId);
            cameraController.stopCameraLock(playerId);
            if (indicatorManager != null) {
                indicatorManager.showTargetLost(playerId, "Player entity invalid");
            }
            positionCache.remove(playerId);
            return;
        }

        TransformComponent playerTransform = store.getComponent(entityRef, TransformComponent.getComponentType());
        if (playerTransform == null) {
            lockManager.releaseLock(playerId);
            cameraController.stopCameraLock(playerId);
            if (indicatorManager != null) {
                indicatorManager.showTargetLost(playerId, "Player transform missing");
            }
            positionCache.remove(playerId);
            return;
        }

        Vector3d playerPosition = playerTransform.getPosition();
        double playerX = playerPosition.getX();
        double playerY = playerPosition.getY();
        double playerZ = playerPosition.getZ();

        // Update target position
        Ref<EntityStore> targetRef = target.getEntityRef();
        double targetX = target.getLastKnownX();
        double targetY = target.getLastKnownY();
        double targetZ = target.getLastKnownZ();

        if (targetRef != null) {
            // Valider la référence d'entité avant d'y accéder
            if (!targetRef.isValid()) {
                lockManager.releaseLock(playerId);
                cameraController.stopCameraLock(playerId);
                if (indicatorManager != null) {
                    indicatorManager.showTargetLost(playerId, "Target entity invalid");
                }
                positionCache.remove(playerId);
                return;
            }

            TransformComponent targetTransform = store.getComponent(targetRef, TransformComponent.getComponentType());
            if (targetTransform != null) {
                Vector3d targetPos = targetTransform.getPosition();
                if (targetPos != null) {
                    targetX = targetPos.getX();
                    targetY = targetPos.getY();
                    targetZ = targetPos.getZ();
                    target.updatePosition(targetX, targetY, targetZ);
                }
            } else {
                lockManager.releaseLock(playerId);
                cameraController.stopCameraLock(playerId);
                if (indicatorManager != null) {
                    indicatorManager.showTargetLost(playerId, "Target disappeared");
                }
                positionCache.remove(playerId);
                return;
            }
        }

        HylockConfig config = plugin.getConfig();
        double distance = target.distanceFrom(playerX, playerY, playerZ);
        if (distance > config.getLockOnRange()) {
            lockManager.releaseLock(playerId);
            cameraController.stopCameraLock(playerId);
            if (indicatorManager != null) {
                indicatorManager.showTargetLost(playerId, "Out of range");
            }
            positionCache.remove(playerId);
            return;
        }

        // Cache the positions for the fast update loop
        positionCache.put(playerId, new CachedPositions(playerX, playerY, playerZ, targetX, targetY, targetZ));

        cameraController.updatePlayerPosition(playerId, playerX, playerY, playerZ);

        if (indicatorManager != null) {
            indicatorManager.updateIndicator(playerId, playerX, playerY, playerZ, target);
        }

        // Utiliser la méthode avec le contexte du monde pour une meilleure validation
        // des cibles
        World world = store.getExternalData().getWorld();
        lockManager.updateWithWorldContext(playerId, playerX, playerY, playerZ, store, world);
    }

    /**
     * Fast camera update loop - runs at high FPS, only sends packets.
     */
    @Override
    public void run() {
        if (!running)
            return;

        CameraController cameraController = plugin.getCameraController();
        LockOnManager lockManager = plugin.getLockOnManager();
        if (cameraController == null || lockManager == null)
            return;

        for (Map.Entry<UUID, CachedPositions> entry : positionCache.entrySet()) {
            UUID playerId = entry.getKey();
            CachedPositions pos = entry.getValue();

            if (lockManager.getState(playerId) != LockOnState.LOCKED)
                continue;

            TargetInfo target = lockManager.getLockedTarget(playerId);
            if (target == null)
                continue;

            try {
                // Use cached positions for camera calculation and packet sending
                cameraController.updateCameraFromCache(playerId, pos.playerX, pos.playerY, pos.playerZ,
                        pos.targetX, pos.targetY, pos.targetZ);
            } catch (Exception e) {
                // Silently ignore
            }
        }
    }

    public boolean isRunning() {
        return running;
    }

    /**
     * Cached positions for fast access without WorldThread.
     */
    private static class CachedPositions {
        final double playerX, playerY, playerZ;
        final double targetX, targetY, targetZ;

        CachedPositions(double playerX, double playerY, double playerZ,
                double targetX, double targetY, double targetZ) {
            this.playerX = playerX;
            this.playerY = playerY;
            this.playerZ = playerZ;
            this.targetX = targetX;
            this.targetY = targetY;
            this.targetZ = targetZ;
        }
    }
}
