package com.hyvanced.hylock.lockon;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import java.util.UUID;

/**
 * Holds information about a locked target entity.
 * This class stores the target's identification and tracking data.
 */
public class TargetInfo {

    private final UUID entityId;
    private final String entityName;
    private final boolean isHostile;
    private final boolean isPlayer;
    private Ref<EntityStore> entityRef;

    private double lastKnownX;
    private double lastKnownY;
    private double lastKnownZ;
    private long lockStartTime;
    private boolean isValid;

    /**
     * Constructs a new TargetInfo.
     *
     * @param entityId   the unique identifier of the entity
     * @param entityName the display name of the entity
     * @param isHostile  whether the entity is hostile
     * @param isPlayer   whether the entity is a player
     */
    public TargetInfo(UUID entityId, String entityName, boolean isHostile, boolean isPlayer) {
        this.entityId = entityId;
        this.entityName = entityName;
        this.isHostile = isHostile;
        this.isPlayer = isPlayer;
        this.lockStartTime = System.currentTimeMillis();
        this.isValid = true;
    }

    /**
     * Returns the entity's unique identifier.
     *
     * @return the entity UUID
     */
    public UUID getEntityId() {
        return entityId;
    }

    /**
     * Returns the entity's display name.
     *
     * @return the entity name
     */
    public String getEntityName() {
        return entityName;
    }

    /**
     * Checks if the entity is hostile.
     *
     * @return true if the entity is hostile
     */
    public boolean isHostile() {
        return isHostile;
    }

    /**
     * Checks if the entity is a player.
     *
     * @return true if the entity is a player
     */
    public boolean isPlayer() {
        return isPlayer;
    }

    /**
     * Returns the entity reference for direct component access.
     *
     * @return the entity reference, or null if not set
     */
    public Ref<EntityStore> getEntityRef() {
        return entityRef;
    }

    /**
     * Sets the entity reference for direct component access.
     *
     * @param entityRef the entity reference
     */
    public void setEntityRef(Ref<EntityStore> entityRef) {
        this.entityRef = entityRef;
    }

    /**
     * Returns the last known X coordinate of the entity.
     *
     * @return the X coordinate
     */
    public double getLastKnownX() {
        return lastKnownX;
    }

    /**
     * Returns the last known Y coordinate of the entity.
     *
     * @return the Y coordinate
     */
    public double getLastKnownY() {
        return lastKnownY;
    }

    /**
     * Returns the last known Z coordinate of the entity.
     *
     * @return the Z coordinate
     */
    public double getLastKnownZ() {
        return lastKnownZ;
    }

    /**
     * Returns the timestamp when the lock was acquired.
     *
     * @return the lock start time in milliseconds
     */
    public long getLockStartTime() {
        return lockStartTime;
    }

    /**
     * Returns the duration of the lock in milliseconds.
     *
     * @return the lock duration
     */
    public long getLockDuration() {
        return System.currentTimeMillis() - lockStartTime;
    }

    /**
     * Checks if the target is still valid.
     *
     * @return true if the target is valid
     */
    public boolean isValid() {
        return isValid;
    }

    /**
     * Updates the last known position of the entity.
     *
     * @param x the X coordinate
     * @param y the Y coordinate
     * @param z the Z coordinate
     */
    public void updatePosition(double x, double y, double z) {
        this.lastKnownX = x;
        this.lastKnownY = y;
        this.lastKnownZ = z;
    }

    /**
     * Marks this target as invalid.
     */
    public void invalidate() {
        this.isValid = false;
    }

    /**
     * Calculates the distance from a given position to this target.
     *
     * @param x the X coordinate of the position
     * @param y the Y coordinate of the position
     * @param z the Z coordinate of the position
     * @return the distance to the target
     */
    public double distanceFrom(double x, double y, double z) {
        double dx = this.lastKnownX - x;
        double dy = this.lastKnownY - y;
        double dz = this.lastKnownZ - z;
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    /**
     * Calculates the horizontal distance (ignoring Y) from a given position.
     *
     * @param x the X coordinate of the position
     * @param z the Z coordinate of the position
     * @return the horizontal distance to the target
     */
    public double horizontalDistanceFrom(double x, double z) {
        double dx = this.lastKnownX - x;
        double dz = this.lastKnownZ - z;
        return Math.sqrt(dx * dx + dz * dz);
    }

    @Override
    public String toString() {
        return String.format("TargetInfo{entity=%s, name='%s', hostile=%s, player=%s, pos=(%.1f, %.1f, %.1f)}",
            entityId, entityName, isHostile, isPlayer, lastKnownX, lastKnownY, lastKnownZ);
    }
}
