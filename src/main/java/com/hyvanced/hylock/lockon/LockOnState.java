package com.hyvanced.hylock.lockon;

/**
 * Represents the current state of a player's lock-on system.
 */
public enum LockOnState {
    /**
     * No target is currently locked
     */
    IDLE,

    /**
     * Searching for a valid target within range
     */
    SEARCHING,

    /**
     * A target is locked and being tracked
     */
    LOCKED,

    /**
     * Target was lost (died, out of range, or line of sight broken)
     */
    TARGET_LOST,

    /**
     * Switching to a new target (after kill or manual switch)
     */
    SWITCHING
}
