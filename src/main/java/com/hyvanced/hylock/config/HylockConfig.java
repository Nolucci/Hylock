package com.hyvanced.hylock.config;

import com.hypixel.hytale.protocol.MouseButtonType;

/**
 * Configuration settings for the Hylock lock-on system.
 * Controls targeting behavior, camera settings, and visual feedback.
 */
public class HylockConfig {

    private MouseButtonType lockButton = MouseButtonType.Middle;
    private double lockOnRange = 20.0;
    private double lockOnAngle = 60.0;
    private double minLockDistance = 2.0;
    private boolean autoSwitchOnKill = true;
    private boolean prioritizeHostile = true;
    private boolean lockOnPlayers = true;
    private double cameraSmoothing = 0.15;
    private double cameraHeightOffset = 1.5;
    private boolean showLockIndicator = true;
    private boolean playLockSound = true;
    private boolean enableStrafe = true;
    private double strafeSpeedMultiplier = 0.9;

    /**
     * Returns the mouse button used to toggle lock-on.
     *
     * @return the configured mouse button type
     */
    public MouseButtonType getLockButton() {
        return lockButton;
    }

    /**
     * Returns the maximum distance for target detection.
     *
     * @return the lock-on range in blocks
     */
    public double getLockOnRange() {
        return lockOnRange;
    }

    /**
     * Returns the cone angle for target detection.
     *
     * @return the detection angle in degrees from center
     */
    public double getLockOnAngle() {
        return lockOnAngle;
    }

    /**
     * Returns the minimum distance to maintain a lock.
     *
     * @return the minimum lock distance in blocks
     */
    public double getMinLockDistance() {
        return minLockDistance;
    }

    /**
     * Checks if auto-switch to next target on kill is enabled.
     *
     * @return true if auto-switch is enabled
     */
    public boolean isAutoSwitchOnKill() {
        return autoSwitchOnKill;
    }

    /**
     * Checks if hostile mobs are prioritized over neutral ones.
     *
     * @return true if hostile entities are prioritized
     */
    public boolean isPrioritizeHostile() {
        return prioritizeHostile;
    }

    /**
     * Checks if locking onto players is allowed.
     *
     * @return true if player targeting is enabled
     */
    public boolean isLockOnPlayers() {
        return lockOnPlayers;
    }

    /**
     * Returns the camera follow smoothness factor.
     *
     * @return the smoothing value (0.0 = instant, 1.0 = very slow)
     */
    public double getCameraSmoothing() {
        return cameraSmoothing;
    }

    /**
     * Returns the camera height offset when locked.
     *
     * @return the height offset for eye level adjustment
     */
    public double getCameraHeightOffset() {
        return cameraHeightOffset;
    }

    /**
     * Checks if the visual lock indicator is enabled.
     *
     * @return true if the indicator should be shown
     */
    public boolean isShowLockIndicator() {
        return showLockIndicator;
    }

    /**
     * Checks if lock/unlock sounds are enabled.
     *
     * @return true if sounds should be played
     */
    public boolean isPlayLockSound() {
        return playLockSound;
    }

    /**
     * Checks if strafing movement while locked is enabled.
     *
     * @return true if strafe is enabled
     */
    public boolean isEnableStrafe() {
        return enableStrafe;
    }

    /**
     * Returns the movement speed multiplier while locked.
     *
     * @return the strafe speed multiplier (1.0 = normal speed)
     */
    public double getStrafeSpeedMultiplier() {
        return strafeSpeedMultiplier;
    }

    /**
     * Sets the mouse button used to toggle lock-on.
     *
     * @param button the mouse button type to use
     */
    public void setLockButton(MouseButtonType button) {
        this.lockButton = button;
    }

    /**
     * Sets the maximum distance for target detection.
     *
     * @param range the lock-on range in blocks (clamped between 5.0 and 50.0)
     */
    public void setLockOnRange(double range) {
        this.lockOnRange = Math.max(5.0, Math.min(50.0, range));
    }

    /**
     * Sets the cone angle for target detection.
     *
     * @param angle the detection angle in degrees (clamped between 15.0 and 180.0)
     */
    public void setLockOnAngle(double angle) {
        this.lockOnAngle = Math.max(15.0, Math.min(180.0, angle));
    }

    /**
     * Sets the minimum distance to maintain a lock.
     *
     * @param distance the minimum lock distance in blocks (clamped between 0.5 and 5.0)
     */
    public void setMinLockDistance(double distance) {
        this.minLockDistance = Math.max(0.5, Math.min(5.0, distance));
    }

    /**
     * Sets whether to automatically switch to next target on kill.
     *
     * @param autoSwitch true to enable auto-switch
     */
    public void setAutoSwitchOnKill(boolean autoSwitch) {
        this.autoSwitchOnKill = autoSwitch;
    }

    /**
     * Sets whether to prioritize hostile mobs over neutral ones.
     *
     * @param prioritize true to prioritize hostile entities
     */
    public void setPrioritizeHostile(boolean prioritize) {
        this.prioritizeHostile = prioritize;
    }

    /**
     * Sets whether locking onto players is allowed.
     *
     * @param lockPlayers true to enable player targeting
     */
    public void setLockOnPlayers(boolean lockPlayers) {
        this.lockOnPlayers = lockPlayers;
    }

    /**
     * Sets the camera follow smoothness factor.
     *
     * @param smoothing the smoothing value (clamped between 0.0 and 1.0)
     */
    public void setCameraSmoothing(double smoothing) {
        this.cameraSmoothing = Math.max(0.0, Math.min(1.0, smoothing));
    }

    /**
     * Sets the camera height offset when locked.
     *
     * @param offset the height offset (clamped between 0.0 and 3.0)
     */
    public void setCameraHeightOffset(double offset) {
        this.cameraHeightOffset = Math.max(0.0, Math.min(3.0, offset));
    }

    /**
     * Sets whether to show the visual lock indicator.
     *
     * @param show true to show the indicator
     */
    public void setShowLockIndicator(boolean show) {
        this.showLockIndicator = show;
    }

    /**
     * Sets whether to play lock/unlock sounds.
     *
     * @param play true to play sounds
     */
    public void setPlayLockSound(boolean play) {
        this.playLockSound = play;
    }

    /**
     * Sets whether strafing movement while locked is enabled.
     *
     * @param enable true to enable strafe
     */
    public void setEnableStrafe(boolean enable) {
        this.enableStrafe = enable;
    }

    /**
     * Sets the movement speed multiplier while locked.
     *
     * @param multiplier the strafe speed multiplier (clamped between 0.5 and 1.5)
     */
    public void setStrafeSpeedMultiplier(double multiplier) {
        this.strafeSpeedMultiplier = Math.max(0.5, Math.min(1.5, multiplier));
    }

    /**
     * Resets all settings to their default values.
     */
    public void resetToDefaults() {
        this.lockButton = MouseButtonType.Middle;
        this.lockOnRange = 20.0;
        this.lockOnAngle = 60.0;
        this.minLockDistance = 2.0;
        this.autoSwitchOnKill = true;
        this.prioritizeHostile = true;
        this.lockOnPlayers = true;
        this.cameraSmoothing = 0.15;
        this.cameraHeightOffset = 1.5;
        this.showLockIndicator = true;
        this.playLockSound = true;
        this.enableStrafe = true;
        this.strafeSpeedMultiplier = 0.9;
    }
}
