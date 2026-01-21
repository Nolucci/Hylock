package com.hyvanced.hylock.lockon;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.vector.Vector3d;
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
 * The lock-on system works like Zelda's Z-targeting where players can lock onto
 * nearby entities and the camera follows the target.
 */
public class LockOnManager {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private final HylockPlugin plugin;
    private final Map<UUID, PlayerLockOnData> playerLockData;

    /**
     * Constructs a new LockOnManager.
     *
     * @param plugin the Hylock plugin instance
     */
    public LockOnManager(HylockPlugin plugin) {
        this.plugin = plugin;
        this.playerLockData = new ConcurrentHashMap<>();
        LOGGER.atInfo().log("[Hylock] LockOnManager initialized");
    }

    /**
     * Attempts to lock onto the nearest valid target for a player.
     *
     * @param playerId the UUID of the player attempting to lock
     * @param playerX  player's current X position
     * @param playerY  player's current Y position
     * @param playerZ  player's current Z position
     * @param lookDirX player's look direction X component
     * @param lookDirY player's look direction Y component
     * @param lookDirZ player's look direction Z component
     * @return true if a target was acquired, false otherwise
     */
    public boolean tryLockOn(UUID playerId, double playerX, double playerY, double playerZ,
                             double lookDirX, double lookDirY, double lookDirZ) {
        PlayerLockOnData data = getOrCreatePlayerData(playerId);

        if (data.getState() == LockOnState.LOCKED) {
            releaseLock(playerId);
            return false;
        }

        data.setState(LockOnState.SEARCHING);

        LOGGER.atInfo().log("[Hylock] Player %s attempting lock-on from (%.1f, %.1f, %.1f)",
            playerId, playerX, playerY, playerZ);

        data.setState(LockOnState.IDLE);
        return false;
    }

    /**
     * Locks onto a specific target entity.
     *
     * @param playerId the player acquiring the lock
     * @param target   the target information
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
     * Releases the current lock for a player.
     *
     * @param playerId the player releasing the lock
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
     * Switches to the next available target.
     *
     * @param playerId  the player switching targets
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

        data.setState(LockOnState.LOCKED);
        return false;
    }

    /**
     * Updates the lock-on system for a player.
     * Should be called each game tick to update tracking.
     *
     * @param playerId the player to update
     * @param playerX  current player X position
     * @param playerY  current player Y position
     * @param playerZ  current player Z position
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

        if (distance > config.getLockOnRange()) {
            handleTargetLost(playerId, data);
            return;
        }

        if (distance < config.getMinLockDistance()) {
        }
    }

    /**
     * Handles when a locked target is lost.
     *
     * @param playerId the player who lost the target
     * @param data     the player's lock-on data
     */
    private void handleTargetLost(UUID playerId, PlayerLockOnData data) {
        data.setState(LockOnState.TARGET_LOST);

        HylockConfig config = plugin.getConfig();
        if (config.isAutoSwitchOnKill()) {
        }

        releaseLock(playerId);
    }

    /**
     * Gets the lock-on data for a player, creating it if necessary.
     *
     * @param playerId the player's UUID
     * @return the player's lock-on data
     */
    private PlayerLockOnData getOrCreatePlayerData(UUID playerId) {
        return playerLockData.computeIfAbsent(playerId, id -> new PlayerLockOnData());
    }

    /**
     * Checks if a player currently has a target locked.
     *
     * @param playerId the player's UUID
     * @return true if the player has a locked target
     */
    public boolean isLocked(UUID playerId) {
        PlayerLockOnData data = playerLockData.get(playerId);
        return data != null && data.getState() == LockOnState.LOCKED;
    }

    /**
     * Gets the current lock state for a player.
     *
     * @param playerId the player's UUID
     * @return the current lock-on state
     */
    public LockOnState getState(UUID playerId) {
        PlayerLockOnData data = playerLockData.get(playerId);
        return data != null ? data.getState() : LockOnState.IDLE;
    }

    /**
     * Gets information about the currently locked target.
     *
     * @param playerId the player's UUID
     * @return the target info, or null if no target is locked
     */
    public TargetInfo getLockedTarget(UUID playerId) {
        PlayerLockOnData data = playerLockData.get(playerId);
        return data != null ? data.getCurrentTarget() : null;
    }

    /**
     * Calculates the camera yaw angle to look at the target.
     *
     * @param playerId the player's UUID
     * @param playerX  the player's X position
     * @param playerZ  the player's Z position
     * @return the yaw angle in degrees, or NaN if no target
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
     * Calculates the camera pitch angle to look at the target.
     *
     * @param playerId the player's UUID
     * @param playerX  the player's X position
     * @param playerY  the player's Y position
     * @param playerZ  the player's Z position
     * @return the pitch angle in degrees, or NaN if no target
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
     * Cleans up data for a player that disconnected.
     *
     * @param playerId the player's UUID
     */
    public void removePlayer(UUID playerId) {
        playerLockData.remove(playerId);
        LOGGER.atInfo().log("[Hylock] Cleaned up lock data for player %s", playerId);
    }

    /**
     * Tries to lock onto the nearest target from the world.
     * Searches for other players first, then for living entities (mobs).
     *
     * @param playerId  the player's UUID
     * @param store     the entity store
     * @param playerRef the player's entity reference
     * @param world     the world to search in
     * @param playerX   player's X position
     * @param playerY   player's Y position
     * @param playerZ   player's Z position
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

        List<CandidateTarget> candidates = new ArrayList<>();

        if (config.isLockOnPlayers()) {
            Collection<PlayerRef> allPlayers = world.getPlayerRefs();
            LOGGER.atInfo().log("[Hylock] Found %d players in world", allPlayers.size());

            for (PlayerRef otherPlayer : allPlayers) {
                if (otherPlayer.getUuid().equals(playerId)) {
                    continue;
                }

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
                        false,
                        true
                    ));
                }
            }
        }

        LOGGER.atInfo().log("[Hylock] Searching for entities with TransformComponent...");

        final java.util.Set<String> playerPositions = new java.util.HashSet<>();
        for (PlayerRef p : world.getPlayerRefs()) {
            Ref<EntityStore> pRef = p.getReference();
            if (pRef != null) {
                TransformComponent pTransform = store.getComponent(pRef, TransformComponent.getComponentType());
                if (pTransform != null) {
                    Vector3d pPos = pTransform.getPosition();
                    if (pPos != null) {
                        playerPositions.add(String.format("%.1f,%.1f,%.1f", pPos.getX(), pPos.getY(), pPos.getZ()));
                    }
                }
            }
        }

        final int[] entityCounter = {0};

        store.forEachChunk(TransformComponent.getComponentType(), (ArchetypeChunk<EntityStore> chunk, CommandBuffer<EntityStore> cmd) -> {
            int size = chunk.size();
            LOGGER.atInfo().log("[Hylock] Processing chunk with %d entities", size);

            for (int i = 0; i < size; i++) {
                Ref<EntityStore> entityRef = chunk.getReferenceTo(i);
                if (entityRef == null) {
                    continue;
                }

                TransformComponent transform = store.getComponent(entityRef, TransformComponent.getComponentType());
                if (transform == null) {
                    continue;
                }

                Vector3d pos = transform.getPosition();
                if (pos == null) {
                    continue;
                }

                String posKey = String.format("%.1f,%.1f,%.1f", pos.getX(), pos.getY(), pos.getZ());
                if (playerPositions.contains(posKey)) {
                    continue;
                }

                double dx = pos.getX() - playerX;
                double dy = pos.getY() - playerY;
                double dz = pos.getZ() - playerZ;
                double distSquared = dx * dx + dy * dy + dz * dz;

                if (distSquared <= maxRangeSquared && distSquared >= config.getMinLockDistance() * config.getMinLockDistance()) {
                    entityCounter[0]++;
                    UUID entityId = UUID.nameUUIDFromBytes(posKey.getBytes());

                    String entityName = "Entity_" + entityCounter[0];

                    LOGGER.atInfo().log("[Hylock] Found entity %s at distance %.1f (pos: %s)",
                        entityName, Math.sqrt(distSquared), posKey);
                    candidates.add(new CandidateTarget(
                        entityId,
                        entityName,
                        pos.getX(), pos.getY(), pos.getZ(),
                        distSquared,
                        true,
                        false
                    ));
                }
            }
        });

        LOGGER.atInfo().log("[Hylock] Found %d potential targets total", candidates.size());

        if (candidates.isEmpty()) {
            LOGGER.atInfo().log("[Hylock] ===== tryLockOnFromWorld END (no target) =====");
            return false;
        }

        candidates.sort(Comparator.comparingDouble((CandidateTarget c) -> {
            double priority = c.distanceSquared;
            if (config.isPrioritizeHostile() && c.isHostile) {
                priority -= 1000;
            }
            return priority;
        }));

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
     * Calculates distance between two 3D points.
     *
     * @param x1 first point X
     * @param y1 first point Y
     * @param z1 first point Z
     * @param x2 second point X
     * @param y2 second point Y
     * @param z2 second point Z
     * @return the distance between the two points
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

        /**
         * Constructs a new CandidateTarget.
         *
         * @param entityId        the entity's UUID
         * @param entityName      the entity's name
         * @param x               the X position
         * @param y               the Y position
         * @param z               the Z position
         * @param distanceSquared the squared distance from player
         * @param isHostile       whether the entity is hostile
         * @param isPlayer        whether the entity is a player
         */
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

        /**
         * Gets the current lock-on state.
         *
         * @return the lock-on state
         */
        public LockOnState getState() {
            return state;
        }

        /**
         * Sets the lock-on state.
         *
         * @param state the new state
         */
        public void setState(LockOnState state) {
            this.state = state;
        }

        /**
         * Gets the current target info.
         *
         * @return the target info
         */
        public TargetInfo getCurrentTarget() {
            return currentTarget;
        }

        /**
         * Sets the current target.
         *
         * @param target the target info
         */
        public void setCurrentTarget(TargetInfo target) {
            this.currentTarget = target;
        }
    }
}
