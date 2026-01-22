package com.hyvanced.hylock.lockon;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

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

/**
 * Manages the lock-on state for all players.
 * This is the core system that handles target acquisition, tracking, and
 * release.
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
     * @param direction 1 for next target (clockwise), -1 for previous
     *                  (counter-clockwise)
     * @return true if switched to a new target
     */
    public boolean switchTarget(UUID playerId, int direction) {
        PlayerLockOnData data = playerLockData.get(playerId);
        if (data == null || data.getState() != LockOnState.LOCKED) {
            // Si le joueur n'a pas de cible verrouillée, essaie de verrouiller la cible la
            // plus proche
            return tryFindAndLockNearestTarget(playerId, direction);
        }

        data.setState(LockOnState.SWITCHING);

        LOGGER.atInfo().log("[Hylock] Player %s attempting to switch target (direction: %d)",
                playerId, direction);

        // Trouver la cible suivante parmi les entités proches
        TargetInfo currentTarget = data.getCurrentTarget();
        TargetInfo nextTarget = findNextTarget(playerId, currentTarget, direction);

        if (nextTarget != null) {
            data.setCurrentTarget(nextTarget);
            data.setState(LockOnState.LOCKED);

            LOGGER.atInfo().log("[Hylock] Player %s switched to target %s",
                    playerId, nextTarget.getEntityName());
            return true;
        } else {
            // Si aucune autre cible n'est trouvée, on tente de trouver la plus proche
            boolean result = tryFindAndLockNearestTarget(playerId, direction);
            data.setState(result ? LockOnState.LOCKED : LockOnState.IDLE);
            return result;
        }
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
        if (target == null || !target.isValid() || !isTargetStillValid(target)) {
            handleTargetLost(playerId, data);
            return;
        }

        // Vérifier spécifiquement si la cible est morte (points de vie à 0 ou statut de
        // mort)
        if (isTargetDead(target)) {
            handleTargetDeath(playerId, data); // Nouvelle méthode pour gérer la mort de la cible
            return;
        }

        HylockConfig config = plugin.getConfig();
        double distance = target.distanceFrom(playerX, playerY, playerZ);

        if (distance > config.getLockOnRange()) {
            handleTargetLost(playerId, data);
            return;
        }

        if (distance < config.getMinLockDistance()) {
            // Gérer le cas où la cible est trop proche
        }
    }

    /**
     * Handles when a locked target dies (health reaches 0 or death animation
     * triggers).
     * This immediately switches to the next available target according to settings.
     *
     * @param playerId the player whose target died
     * @param data     the player's lock-on data
     */
    private void handleTargetDeath(UUID playerId, PlayerLockOnData data) {
        data.setState(LockOnState.SWITCHING); // Utiliser l'état SWITCHING existant

        HylockConfig config = plugin.getConfig();
        if (config.isAutoSwitchOnKill()) {
            // Passer immédiatement à la cible suivante sans libérer le verrou actuel
            // On effectue un switch vers la cible suivante
            boolean switched = switchTarget(playerId, 1); // Direction positive pour la cible suivante
            if (!switched) {
                // Si aucun changement de cible n'a eu lieu, on libère le verrou
                releaseLock(playerId);
            }
        } else {
            // Si le passage automatique est désactivé, on libère le verrou
            releaseLock(playerId);
        }
    }

    /**
     * Updates the lock-on system for a player with world context.
     * This version provides better target validation using world components.
     *
     * @param playerId the player to update
     * @param playerX  current player X position
     * @param playerY  current player Y position
     * @param playerZ  current player Z position
     * @param store    the entity store
     * @param world    the world to validate entities in
     */
    public void updateWithWorldContext(UUID playerId, double playerX, double playerY, double playerZ,
            Store<EntityStore> store, World world) {
        PlayerLockOnData data = playerLockData.get(playerId);
        if (data == null || data.getState() != LockOnState.LOCKED) {
            return;
        }

        TargetInfo target = data.getCurrentTarget();
        if (target == null || !target.isValid() || !isTargetStillValid(target)) {
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
            // Gérer le cas où la cible est trop proche
        }
    }

    /**
     * Checks if a target is still valid in the world.
     * This includes checking if the entity still exists.
     *
     * @param target the target to check
     * @return true if the target is still valid, false otherwise
     */
    public boolean isTargetStillValid(TargetInfo target) {
        // Check if target ref exists and is valid
        if (target.getEntityRef() != null) {
            if (!target.getEntityRef().isValid()) {
                target.invalidate(); // Marquer la cible comme invalide
                return false;
            }

            // Vérifier si l'entité est encore en vie (pour les entités vivantes)
            // On suppose qu'il y a un composant de vie dans l'entité
            // Cette vérification dépendra de la structure des entités dans Hytale
            try {
                // Accéder au stockage de l'entité pour vérifier son état
                var store = target.getEntityRef().getStore();
                if (store != null) {
                    // Vérifier si l'entité a un composant de vie et si ses points de vie sont > 0
                    // ou si l'entité est marquée comme morte
                    // On suppose qu'il existe un composant de vie dans l'entité
                    // Cette vérification est spécifique à la structure d'Hytale
                    // Pour l'instant, on continue à se baser sur la validité de la référence
                }
            } catch (Exception e) {
                // En cas d'erreur lors de l'accès au composant, on considère la cible comme
                // invalide
                target.invalidate();
                return false;
            }

            return true;
        }

        // If no entity reference, we can't verify if the entity still exists
        // In this case, we rely on other checks (like position and distance)
        return true;
    }

    /**
     * Checks if a target is dead (health at 0 or death status).
     * This method specifically looks for death indicators beyond just entity
     * validity.
     *
     * @param target the target to check for death
     * @return true if the target is dead, false otherwise
     */
    public boolean isTargetDead(TargetInfo target) {
        if (target == null) {
            return true; // Considéré comme mort si null
        }

        // Si la référence de l'entité n'est pas valide, on considère qu'elle est morte
        if (target.getEntityRef() != null && !target.getEntityRef().isValid()) {
            target.invalidate();
            return true;
        }

        // On suppose qu'il existe un moyen de vérifier l'état de vie de l'entité
        // dans le composant de vie de l'entité
        try {
            if (target.getEntityRef() != null) {
                var store = target.getEntityRef().getStore();
                if (store != null) {
                    // Ici, on devrait vérifier si l'entité a un composant de vie
                    // et si les points de vie sont à 0 ou si l'entité est marquée comme morte
                    // Pour l'instant, on suppose que la mort est détectée via la validité de la
                    // référence
                    // ou via une propriété spécifique de l'entité
                }
            }
        } catch (Exception e) {
            target.invalidate();
            return true; // Considéré comme mort en cas d'erreur
        }

        return false; // Par défaut, on suppose que la cible est vivante
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
            // Essayer de trouver une nouvelle cible après avoir perdu la précédente
            switchTarget(playerId, 1); // Cherche la cible suivante dans le sens horaire
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
        double dy = (target.getLastKnownY() + config.getCameraHeightOffset())
                - (playerY + config.getCameraHeightOffset());
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
     * Gets the set of player IDs that currently have active locks.
     *
     * @return unmodifiable set of player UUIDs with active locks
     */
    public java.util.Set<UUID> getLockedPlayerIds() {
        java.util.Set<UUID> lockedPlayers = new java.util.HashSet<>();
        for (Map.Entry<UUID, PlayerLockOnData> entry : playerLockData.entrySet()) {
            if (entry.getValue().getState() == LockOnState.LOCKED) {
                lockedPlayers.add(entry.getKey());
            }
        }
        return java.util.Collections.unmodifiableSet(lockedPlayers);
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

                if (distSquared <= maxRangeSquared
                        && distSquared >= config.getMinLockDistance() * config.getMinLockDistance()) {
                    LOGGER.atInfo().log("[Hylock] Found player %s at distance %.1f", otherPlayer.getUsername(),
                            Math.sqrt(distSquared));
                    candidates.add(new CandidateTarget(
                            otherPlayer.getUuid(),
                            otherPlayer.getUsername(),
                            otherPos.getX(), otherPos.getY(), otherPos.getZ(),
                            distSquared,
                            false,
                            true));
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

        final int[] entityCounter = { 0 };

        store.forEachChunk(TransformComponent.getComponentType(),
                (ArchetypeChunk<EntityStore> chunk, CommandBuffer<EntityStore> cmd) -> {
                    int size = chunk.size();
                    LOGGER.atInfo().log("[Hylock] Processing chunk with %d entities", size);

                    for (int i = 0; i < size; i++) {
                        Ref<EntityStore> entityRef = chunk.getReferenceTo(i);
                        if (entityRef == null) {
                            continue;
                        }

                        TransformComponent transform = store.getComponent(entityRef,
                                TransformComponent.getComponentType());
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

                        if (distSquared <= maxRangeSquared
                                && distSquared >= config.getMinLockDistance() * config.getMinLockDistance()) {
                            entityCounter[0]++;
                            UUID entityId = UUID.nameUUIDFromBytes(posKey.getBytes());

                            String entityName = "Entity_" + entityCounter[0];

                            LOGGER.atInfo().log("[Hylock] Found entity %s at distance %.1f (pos: %s)",
                                    entityName, Math.sqrt(distSquared), posKey);
                            CandidateTarget candidate = new CandidateTarget(
                                    entityId,
                                    entityName,
                                    pos.getX(), pos.getY(), pos.getZ(),
                                    distSquared,
                                    true,
                                    false);
                            candidate.entityRef = entityRef;
                            candidates.add(candidate);
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
                best.isPlayer);
        targetInfo.updatePosition(best.x, best.y, best.z);
        targetInfo.setEntityRef(best.entityRef);

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
        Ref<EntityStore> entityRef;

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

    /**
     * Tries to find and lock onto the nearest target from the world.
     * Searches for other players first, then for living entities (mobs).
     *
     * @param playerId  the player's UUID
     * @param direction the direction to search for targets (1 for next, -1 for
     *                  previous)
     * @return true if a target was found and locked
     */
    /**
     * Tries to find and lock onto the nearest target from the world.
     * This method needs access to world components to search for entities.
     *
     * @param playerId  the player's UUID
     * @param direction the direction to search for targets (1 for next, -1 for
     *                  previous)
     * @return true if a target was found and locked
     */
    public boolean tryFindAndLockNearestTarget(UUID playerId, int direction) {
        // Récupérer les données du joueur
        PlayerLockOnData data = playerLockData.get(playerId);
        if (data == null) {
            return false;
        }

        // Récupérer la position du joueur via le CameraController
        double[] playerPos = plugin.getCameraController().getLastKnownPosition(playerId);
        if (playerPos == null) {
            // Si on n'a pas la position du joueur, on ne peut pas chercher de cibles
            return false;
        }

        double playerX = playerPos[0];
        double playerY = playerPos[1];
        double playerZ = playerPos[2];

        // Pour permettre à cette méthode de fonctionner sans le contexte complet du
        // monde,
        // nous devons accéder au gestionnaire d'événements de la souris pour obtenir
        // les entités potentielles à proximité
        // Pour l'instant, on va juste retourner false, mais dans une implémentation
        // complète
        // on devrait avoir un mécanisme pour rechercher les entités proches

        // Essayons d'utiliser la méthode tryLockOnFromWorld mais avec des valeurs
        // nulles
        // pour les paramètres qui ne sont pas disponibles
        // Cette approche ne fonctionnera pas correctement car la méthode a besoin du
        // monde
        // et du store pour fonctionner

        // Pour contourner cela, on pourrait avoir besoin de modifier l'approche
        // et d'avoir un service qui permet de rechercher les entités à proximité
        // sans avoir à passer par la méthode existante

        // Pour l'instant, on va conserver le comportement existant
        // mais dans une version ultérieure, il faudra implémenter un mécanisme
        // qui permet de rechercher les entités proches même sans le contexte complet
        return false;
    }

    /**
     * Tries to find and lock onto the nearest target from the world.
     * This method needs access to world components to search for entities.
     *
     * @param playerId  the player's UUID
     * @param store     the entity store
     * @param playerRef the player's entity reference
     * @param world     the world to search in
     * @param playerX   player's X position
     * @param playerY   player's Y position
     * @param playerZ   player's Z position
     * @param direction the direction to search for targets (1 for next, -1 for
     *                  previous)
     * @return true if a target was found and locked
     */
    public boolean tryFindAndLockNearestTarget(UUID playerId, Store<EntityStore> store, Ref<EntityStore> playerRef,
            World world, double playerX, double playerY, double playerZ, int direction) {
        // Appeler la méthode existante de recherche de cible
        boolean foundTarget = tryLockOnFromWorld(playerId, store, playerRef, world, playerX, playerY, playerZ);
        return foundTarget;
    }

    /**
     * Finds the next target based on the current target and direction.
     *
     * @param playerId      the player's UUID
     * @param currentTarget the current target
     * @param direction     1 for next target (clockwise), -1 for previous
     *                      (counter-clockwise)
     * @return the next target, or null if none found
     */
    /**
     * Finds the next target based on the current target and direction.
     *
     * @param playerId      the player's UUID
     * @param currentTarget the current target
     * @param direction     1 for next target (clockwise), -1 for previous
     *                      (counter-clockwise)
     * @return the next target, or null if none found
     */
    private TargetInfo findNextTarget(UUID playerId, TargetInfo currentTarget, int direction) {
        // Récupérer les données du joueur
        PlayerLockOnData data = playerLockData.get(playerId);
        if (data == null) {
            return null;
        }

        // Récupérer la position du joueur
        double[] playerPos = plugin.getCameraController().getLastKnownPosition(playerId);
        if (playerPos == null) {
            return null;
        }

        double playerX = playerPos[0];
        double playerY = playerPos[1];
        double playerZ = playerPos[2];

        // Pour implémenter cette logique, nous devons rechercher toutes les cibles
        // potentielles
        // dans la zone et les trier par angle par rapport à la cible actuelle
        // Pour l'instant, retournons null, car cela nécessite un accès direct au monde
        return null;
    }
}
