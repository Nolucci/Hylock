package com.hyvanced.hylock.config;

import com.hypixel.hytale.protocol.MouseButtonType;

/**
 * Configuration settings for the Hylock lock-on system.
 * These values control how the targeting system behaves.
 */
public class HylockConfig {

    // Keybind settings
    private MouseButtonType lockButton = MouseButtonType.Middle;  // Mouse button to toggle lock (Middle = scroll wheel click)

    // Lock-on detection settings
    private double lockOnRange = 20.0;          // Maximum distance to detect targets (in blocks)
    private double lockOnAngle = 60.0;          // Cone angle for target detection (degrees from center)
    private double minLockDistance = 2.0;       // Minimum distance to maintain lock (prevents lock at point-blank)

    // Lock behavior settings
    private boolean autoSwitchOnKill = true;    // Automatically switch to next target when current dies
    private boolean prioritizeHostile = true;   // Prioritize hostile mobs over neutral ones
    private boolean lockOnPlayers = false;      // Allow locking onto other players (PvP)

    // Camera settings
    private double cameraSmoothing = 0.15;      // Camera follow smoothness (0.0 = instant, 1.0 = very slow)
    private double cameraHeightOffset = 1.5;    // Camera height offset when locked (eye level adjustment)

    // Visual feedback
    private boolean showLockIndicator = true;   // Show visual indicator on locked target
    private boolean playLockSound = true;       // Play sound when locking/unlocking

    // Movement settings
    private boolean enableStrafe = true;        // Enable strafing movement while locked
    private double strafeSpeedMultiplier = 0.9; // Movement speed multiplier while locked (1.0 = normal)

    // === Getters ===

    public MouseButtonType getLockButton() {
        return lockButton;
    }

    public double getLockOnRange() {
        return lockOnRange;
    }

    public double getLockOnAngle() {
        return lockOnAngle;
    }

    public double getMinLockDistance() {
        return minLockDistance;
    }

    public boolean isAutoSwitchOnKill() {
        return autoSwitchOnKill;
    }

    public boolean isPrioritizeHostile() {
        return prioritizeHostile;
    }

    public boolean isLockOnPlayers() {
        return lockOnPlayers;
    }

    public double getCameraSmoothing() {
        return cameraSmoothing;
    }

    public double getCameraHeightOffset() {
        return cameraHeightOffset;
    }

    public boolean isShowLockIndicator() {
        return showLockIndicator;
    }

    public boolean isPlayLockSound() {
        return playLockSound;
    }

    public boolean isEnableStrafe() {
        return enableStrafe;
    }

    public double getStrafeSpeedMultiplier() {
        return strafeSpeedMultiplier;
    }

    // === Setters ===

    public void setLockButton(MouseButtonType button) {
        this.lockButton = button;
    }

    public void setLockOnRange(double range) {
        this.lockOnRange = Math.max(5.0, Math.min(50.0, range));
    }

    public void setLockOnAngle(double angle) {
        this.lockOnAngle = Math.max(15.0, Math.min(180.0, angle));
    }

    public void setMinLockDistance(double distance) {
        this.minLockDistance = Math.max(0.5, Math.min(5.0, distance));
    }

    public void setAutoSwitchOnKill(boolean autoSwitch) {
        this.autoSwitchOnKill = autoSwitch;
    }

    public void setPrioritizeHostile(boolean prioritize) {
        this.prioritizeHostile = prioritize;
    }

    public void setLockOnPlayers(boolean lockPlayers) {
        this.lockOnPlayers = lockPlayers;
    }

    public void setCameraSmoothing(double smoothing) {
        this.cameraSmoothing = Math.max(0.0, Math.min(1.0, smoothing));
    }

    public void setCameraHeightOffset(double offset) {
        this.cameraHeightOffset = Math.max(0.0, Math.min(3.0, offset));
    }

    public void setShowLockIndicator(boolean show) {
        this.showLockIndicator = show;
    }

    public void setPlayLockSound(boolean play) {
        this.playLockSound = play;
    }

    public void setEnableStrafe(boolean enable) {
        this.enableStrafe = enable;
    }

    public void setStrafeSpeedMultiplier(double multiplier) {
        this.strafeSpeedMultiplier = Math.max(0.5, Math.min(1.5, multiplier));
    }

    /**
     * Reset all settings to their default values
     */
    public void resetToDefaults() {
        this.lockButton = MouseButtonType.Middle;
        this.lockOnRange = 20.0;
        this.lockOnAngle = 60.0;
        this.minLockDistance = 2.0;
        this.autoSwitchOnKill = true;
        this.prioritizeHostile = true;
        this.lockOnPlayers = false;
        this.cameraSmoothing = 0.15;
        this.cameraHeightOffset = 1.5;
        this.showLockIndicator = true;
        this.playLockSound = true;
        this.enableStrafe = true;
        this.strafeSpeedMultiplier = 0.9;
    }
}
