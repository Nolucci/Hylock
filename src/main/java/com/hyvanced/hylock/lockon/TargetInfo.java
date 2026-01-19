package com.hyvanced.hylock.lockon;

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

    private double lastKnownX;
    private double lastKnownY;
    private double lastKnownZ;
    private long lockStartTime;
    private boolean isValid;

    public TargetInfo(UUID entityId, String entityName, boolean isHostile, boolean isPlayer) {
        this.entityId = entityId;
        this.entityName = entityName;
        this.isHostile = isHostile;
        this.isPlayer = isPlayer;
        this.lockStartTime = System.currentTimeMillis();
        this.isValid = true;
    }

    // === Getters ===

    public UUID getEntityId() {
        return entityId;
    }

    public String getEntityName() {
        return entityName;
    }

    public boolean isHostile() {
        return isHostile;
    }

    public boolean isPlayer() {
        return isPlayer;
    }

    public double getLastKnownX() {
        return lastKnownX;
    }

    public double getLastKnownY() {
        return lastKnownY;
    }

    public double getLastKnownZ() {
        return lastKnownZ;
    }

    public long getLockStartTime() {
        return lockStartTime;
    }

    public long getLockDuration() {
        return System.currentTimeMillis() - lockStartTime;
    }

    public boolean isValid() {
        return isValid;
    }

    // === Setters ===

    public void updatePosition(double x, double y, double z) {
        this.lastKnownX = x;
        this.lastKnownY = y;
        this.lastKnownZ = z;
    }

    public void invalidate() {
        this.isValid = false;
    }

    /**
     * Calculate the distance from a given position to this target
     */
    public double distanceFrom(double x, double y, double z) {
        double dx = this.lastKnownX - x;
        double dy = this.lastKnownY - y;
        double dz = this.lastKnownZ - z;
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    /**
     * Calculate the horizontal distance (ignoring Y) from a given position
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
