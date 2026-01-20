package com.hyvanced.hylock.lockon;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.server.core.entity.LivingEntity;
import com.hypixel.hytale.server.core.modules.entity.AllLegacyLivingEntityTypesQuery;
import com.hypixel.hytale.server.core.modules.entity.EntityModule;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hyvanced.hylock.HylockPlugin;
import com.hyvanced.hylock.config.HylockConfig;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages the lock-on state for all players.
 * This is the core system that handles target acquisition, tracking, and release.
 *
 * The lock-on system works like Zelda's Z-targeting:
 * - Players can lock onto nearby entities
 * - While locked, the camera follows the target
 * - Movement becomes relative to the target (strafing)
 * - Lock is maintained until manually released or target is lost
 */
public class LockOnManager {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private final HylockPlugin plugin;
    private final Map<UUID, PlayerLockOnData> playerLockData;

    public LockOnManager(HylockPlugin plugin) {
        this.plugin = plugin;
        this.playerLockData = new ConcurrentHashMap<>();
        LOGGER.atInfo().log("[Hylock] LockOnManager initialized");
    }

    /**
     * Attempt to lock onto the nearest valid target for a player.
     *
     * @param playerId The UUID of the player attempting to lock
     * @param playerX Player's current X position
     * @param playerY Player's current Y position
     * @param playerZ Player's current Z position
     * @param lookDirX Player's look direction X component
     * @param lookDirY Player's look direction Y component
     * @param lookDirZ Player's look direction Z component
     * @return true if a target was acquired, false otherwise
     */
    public boolean tryLockOn(UUID playerId, double playerX, double playerY, double playerZ,
                             double lookDirX, double lookDirY, double lookDirZ) {
        PlayerLockOnData data = getOrCreatePlayerData(playerId);

        // If already locked, release the current lock first
        if (data.getState() == LockOnState.LOCKED) {
            releaseLock(playerId);
            return false;
        }

        data.setState(LockOnState.SEARCHING);

        LOGGER.atInfo().log("[Hylock] Player %s attempting lock-on from (%.1f, %.1f, %.1f)",
            playerId, playerX, playerY, playerZ);

        // For demonstration, we'll show the state transition
        data.setState(LockOnState.IDLE);
        return false;
    }

    /**
     * Lock onto a specific target entity.
     *
     * @param playerId The player acquiring the lock
     * @param target The target information
     * @return true if lock was successful
     */
    public boolean lockOnTarget(UUID playerId, TargetInfo target) {
        if (target == null || !target.isValid()) {
            return false;
        }

        PlayerLockOnData data = getOrCreatePlayerData(playerId);
        data.setCurrentTarget(target);
        data.setState(LockOnState.LOCKED);

        LOGGER.atInfo().log("[Hylock] Player %s locked onto %s",
            playerId, target.getEntityName());

        return true;
    }

    /**
     * Release the current lock for a player.
     *
     * @param playerId The player releasing the lock
     */
    public void releaseLock(UUID playerId) {
        PlayerLockOnData data = playerLockData.get(playerId);
        if (data == null) {
            return;
        }

        TargetInfo previousTarget = data.getCurrentTarget();
        data.setCurrentTarget(null);
        data.setState(LockOnState.IDLE);

        if (previousTarget != null) {
            LOGGER.atInfo().log("[Hylock] Player %s released lock on %s",
                playerId, previousTarget.getEntityName());
        }
    }

    /**
     * Switch to the next available target.
     * Used when pressing the lock button while already locked, or after killing a target.
     *
     * @param playerId The player switching targets
     * @param direction 1 for next target (clockwise), -1 for previous (counter-clockwise)
     * @return true if switched to a new target
     */
    public boolean switchTarget(UUID playerId, int direction) {
        PlayerLockOnData data = playerLockData.get(playerId);
        if (data == null || data.getState() != LockOnState.LOCKED) {
            return false;
        }

        data.setState(LockOnState.SWITCHING);

        LOGGER.atInfo().log("[Hylock] Player %s attempting to switch target (direction: %d)",
            playerId, direction);

        // If no other target found, stay on current
        data.setState(LockOnState.LOCKED);
        return false;
    }

    /**
     * Update the lock-on system for a player.
     * Should be called each game tick to update tracking.
     *
     * @param playerId The player to update
     * @param playerX Current player X position
     * @param playerY Current player Y position
     * @param playerZ Current player Z position
     */
    public void update(UUID playerId, double playerX, double playerY, double playerZ) {
        PlayerLockOnData data = playerLockData.get(playerId);
        if (data == null || data.getState() != LockOnState.LOCKED) {
            return;
        }

        TargetInfo target = data.getCurrentTarget();
        if (target == null || !target.isValid()) {
            handleTargetLost(playerId, data);
            return;
        }

        HylockConfig config = plugin.getConfig();
        double distance = target.distanceFrom(playerX, playerY, playerZ);

        // Check if target is too far
        if (distance > config.getLockOnRange()) {
            handleTargetLost(playerId, data);
            return;
        }

        // Check if target is too close (optional, prevents awkward camera)
        if (distance < config.getMinLockDistance()) {
            // Don't lose lock, but might want to adjust camera behavior
        }

        // Update camera direction towards target
        // This would interface with Hytale's camera system
    }

    /**
     * Handle when a locked target is lost (died, despawned, out of range, etc.)
     */
    private void handleTargetLost(UUID playerId, PlayerLockOnData data) {
        data.setState(LockOnState.TARGET_LOST);

        HylockConfig config = plugin.getConfig();
        if (config.isAutoSwitchOnKill()) {
            // Attempt to find a new target automatically
            // If found, switch to it; otherwise, return to idle
        }

        // For now, just release the lock
        releaseLock(playerId);
    }

    /**
     * Get the lock-on data for a player, creating it if necessary.
     */
    private PlayerLockOnData getOrCreatePlayerData(UUID playerId) {
        return playerLockData.computeIfAbsent(playerId, id -> new PlayerLockOnData());
    }

    /**
     * Check if a player currently has a target locked.
     */
    public boolean isLocked(UUID playerId) {
        PlayerLockOnData data = playerLockData.get(playerId);
        return data != null && data.getState() == LockOnState.LOCKED;
    }

    /**
     * Get the current lock state for a player.
     */
    public LockOnState getState(UUID playerId) {
        PlayerLockOnData data = playerLockData.get(playerId);
        return data != null ? data.getState() : LockOnState.IDLE;
    }

    /**
     * Get information about the currently locked target.
     */
    public TargetInfo getLockedTarget(UUID playerId) {
        PlayerLockOnData data = playerLockData.get(playerId);
        return data != null ? data.getCurrentTarget() : null;
    }

    /**
     * Calculate the camera yaw angle to look at the target.
     *
     * @return The yaw angle in degrees, or NaN if no target
     */
    public double calculateTargetYaw(UUID playerId, double playerX, double playerZ) {
        TargetInfo target = getLockedTarget(playerId);
        if (target == null) {
            return Double.NaN;
        }

        double dx = target.getLastKnownX() - playerX;
        double dz = target.getLastKnownZ() - playerZ;
        return Math.toDegrees(Math.atan2(-dx, dz));
    }

    /**
     * Calculate the camera pitch angle to look at the target.
     *
     * @return The pitch angle in degrees, or NaN if no target
     */
    public double calculateTargetPitch(UUID playerId, double playerX, double playerY, double playerZ) {
        TargetInfo target = getLockedTarget(playerId);
        if (target == null) {
            return Double.NaN;
        }

        HylockConfig config = plugin.getConfig();
        double dx = target.getLastKnownX() - playerX;
        double dy = (target.getLastKnownY() + config.getCameraHeightOffset()) - (playerY + config.getCameraHeightOffset());
        double dz = target.getLastKnownZ() - playerZ;
        double horizontalDist = Math.sqrt(dx * dx + dz * dz);

        return Math.toDegrees(-Math.atan2(dy, horizontalDist));
    }

    /**
     * Clean up data for a player that disconnected.
     */
    public void removePlayer(UUID playerId) {
        playerLockData.remove(playerId);
        LOGGER.atInfo().log("[Hylock] Cleaned up lock data for player %s", playerId);
    }

    /**
     * Try to lock onto the nearest target from the world.
     * First searches for other players, then for living entities (mobs).
     *
     * @param playerId The player's UUID
     * @param store The entity store
     * @param playerRef The player's entity reference
     * @param world The world to search in
     * @param playerX Player's X position
     * @param playerY Player's Y position
     * @param playerZ Player's Z position
     * @return true if a target was found and locked
     */
    @SuppressWarnings("deprecation")
    public boolean tryLockOnFromWorld(UUID playerId, Store<EntityStore> store, Ref<EntityStore> playerRef,
                                       World world, double playerX, double playerY, double playerZ) {
        LOGGER.atInfo().log("[Hylock] ===== tryLockOnFromWorld START =====");
        LOGGER.atInfo().log("[Hylock] Searching for targets near (%.1f, %.1f, %.1f)", playerX, playerY, playerZ);

        HylockConfig config = plugin.getConfig();
        double maxRange = config.getLockOnRange();
        double maxRangeSquared = maxRange * maxRange;

        LOGGER.atInfo().log("[Hylock] Config: maxRange=%.1f, minRange=%.1f, lockOnPlayers=%s",
                maxRange, config.getMinLockDistance(), config.isLockOnPlayers());

        // Collect potential targets
        List<CandidateTarget> candidates = new ArrayList<>();

        // STEP 1: Search for other players first (simpler API)
        if (config.isLockOnPlayers()) {
            Collection<PlayerRef> allPlayers = world.getPlayerRefs();
            LOGGER.atInfo().log("[Hylock] Found %d players in world", allPlayers.size());

            for (PlayerRef otherPlayer : allPlayers) {
                // Skip self
                if (otherPlayer.getUuid().equals(playerId)) {
                    continue;
                }

                // Get other player's position
                Ref<EntityStore> otherRef = otherPlayer.getReference();
                if (otherRef == null) {
                    continue;
                }

                TransformComponent transform = store.getComponent(otherRef, TransformComponent.getComponentType());
                if (transform == null) {
                    continue;
                }

                Vector3d otherPos = transform.getPosition();
                double dx = otherPos.getX() - playerX;
                double dy = otherPos.getY() - playerY;
                double dz = otherPos.getZ() - playerZ;
                double distSquared = dx * dx + dy * dy + dz * dz;

                if (distSquared <= maxRangeSquared && distSquared >= config.getMinLockDistance() * config.getMinLockDistance()) {
                    LOGGER.atInfo().log("[Hylock] Found player %s at distance %.1f", otherPlayer.getUsername(), Math.sqrt(distSquared));
                    candidates.add(new CandidateTarget(
                        otherPlayer.getUuid(),
                        otherPlayer.getUsername(),
                        otherPos.getX(), otherPos.getY(), otherPos.getZ(),
                        distSquared,
                        false,  // Players are not hostile
                        true    // Is player
                    ));
                }
            }
        }

        // STEP 2: Search for living entities (mobs) using ECS query
        store.forEachChunk(AllLegacyLivingEntityTypesQuery.INSTANCE, (ArchetypeChunk<EntityStore> chunk, CommandBuffer<EntityStore> cmd) -> {
            int size = chunk.size();

            for (int i = 0; i < size; i++) {
                Ref<EntityStore> entityRef = chunk.getReferenceTo(i);
                if (entityRef == null) {
                    continue;
                }

                // Get the LivingEntity component
                LivingEntity entity = store.getComponent(entityRef, EntityModule.get().getComponentType(LivingEntity.class));
                if (entity == null) {
                    continue;
                }

                // Skip players (already handled above)
                if (entity instanceof com.hypixel.hytale.server.core.entity.entities.Player) {
                    continue;
                }

                // Get the TransformComponent
                TransformComponent transform = store.getComponent(entityRef, TransformComponent.getComponentType());
                if (transform == null) {
                    continue;
                }

                // Skip the player themselves
                UUID entityId = entity.getUuid();
                if (entityId != null && entityId.equals(playerId)) {
                    continue;
                }

                // Get entity position
                Vector3d pos = transform.getPosition();
                if (pos == null) {
                    continue;
                }

                double dx = pos.getX() - playerX;
                double dy = pos.getY() - playerY;
                double dz = pos.getZ() - playerZ;
                double distSquared = dx * dx + dy * dy + dz * dz;

                // Check if within range
                if (distSquared <= maxRangeSquared && distSquared >= config.getMinLockDistance() * config.getMinLockDistance()) {
                    String entityName = entity.getLegacyDisplayName();
                    if (entityName == null || entityName.isEmpty()) {
                        entityName = entity.getClass().getSimpleName();
                    }

                    LOGGER.atInfo().log("[Hylock] Found entity %s at distance %.1f", entityName, Math.sqrt(distSquared));
                    candidates.add(new CandidateTarget(
                        entityId,
                        entityName,
                        pos.getX(), pos.getY(), pos.getZ(),
                        distSquared,
                        true,   // Non-player entities are considered hostile
                        false   // Is not player
                    ));
                }
            }
        });

        LOGGER.atInfo().log("[Hylock] Found %d potential targets total", candidates.size());

        if (candidates.isEmpty()) {
            LOGGER.atInfo().log("[Hylock] ===== tryLockOnFromWorld END (no target) =====");
            return false;
        }

        // Sort by distance (closest first), with hostile entities prioritized if configured
        candidates.sort(Comparator.comparingDouble((CandidateTarget c) -> {
            double priority = c.distanceSquared;
            if (config.isPrioritizeHostile() && c.isHostile) {
                priority -= 1000; // Give hostile entities priority
            }
            return priority;
        }));

        // Lock onto the best candidate
        CandidateTarget best = candidates.get(0);
        TargetInfo targetInfo = new TargetInfo(
            best.entityId,
            best.entityName,
            best.isHostile,
            best.isPlayer
        );
        targetInfo.updatePosition(best.x, best.y, best.z);

        LOGGER.atInfo().log("[Hylock] Locking onto %s at distance %.1f",
            best.entityName, Math.sqrt(best.distanceSquared));
        LOGGER.atInfo().log("[Hylock] ===== tryLockOnFromWorld END (found target) =====");

        return lockOnTarget(playerId, targetInfo);
    }

    /**
     * Calculate distance between two 3D points
     */
    private double calculateDistance(double x1, double y1, double z1, double x2, double y2, double z2) {
        double dx = x2 - x1;
        double dy = y2 - y1;
        double dz = z2 - z1;
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    /**
     * Internal class to hold candidate target data during search.
     */
    private static class CandidateTarget {
        final UUID entityId;
        final String entityName;
        final double x, y, z;
        final double distanceSquared;
        final boolean isHostile;
        final boolean isPlayer;

        CandidateTarget(UUID entityId, String entityName, double x, double y, double z,
                       double distanceSquared, boolean isHostile, boolean isPlayer) {
            this.entityId = entityId;
            this.entityName = entityName;
            this.x = x;
            this.y = y;
            this.z = z;
            this.distanceSquared = distanceSquared;
            this.isHostile = isHostile;
            this.isPlayer = isPlayer;
        }
    }

    /**
     * Internal class to store per-player lock-on data.
     */
    private static class PlayerLockOnData {
        private LockOnState state = LockOnState.IDLE;
        private TargetInfo currentTarget = null;

        public LockOnState getState() {
            return state;
        }

        public void setState(LockOnState state) {
            this.state = state;
        }

        public TargetInfo getCurrentTarget() {
            return currentTarget;
        }

        public void setCurrentTarget(TargetInfo target) {
            this.currentTarget = target;
        }
    }
}
