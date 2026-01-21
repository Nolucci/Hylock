package com.hyvanced.hylock;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import com.hyvanced.hylock.commands.HylockCommand;
import com.hyvanced.hylock.commands.HylockResetCommand;
import com.hyvanced.hylock.commands.HylockStatusCommand;
import com.hyvanced.hylock.commands.LockCommand;
import com.hyvanced.hylock.commands.LockSwitchCommand;
import com.hyvanced.hylock.commands.LockToggleCommand;
import com.hyvanced.hylock.commands.UnlockCommand;
import com.hyvanced.hylock.camera.CameraController;
import com.hyvanced.hylock.camera.CameraUpdateTask;
import com.hyvanced.hylock.camera.LockIndicatorManager;
import com.hyvanced.hylock.config.HylockConfig;
import com.hyvanced.hylock.events.LockOnEventListener;
import com.hyvanced.hylock.events.MouseTargetTracker;
import com.hyvanced.hylock.events.PlayerInteractListener;
import com.hyvanced.hylock.lockon.LockOnManager;
import com.hypixel.hytale.server.core.event.events.player.PlayerInteractEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerMouseButtonEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerMouseMotionEvent;

/**
 * Hylock - Zelda-style target lock-on system for Hytale
 * Part of the Hyvanced plugin suite by Nolucci
 *
 * Features:
 * - Lock onto nearby hostile entities
 * - Camera follows locked target
 * - Strafe movement while locked
 * - Visual indicator on locked target
 * - Configurable lock range and behavior
 */
public class HylockPlugin extends JavaPlugin {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private static HylockPlugin instance;
    private LockOnManager lockOnManager;
    private HylockConfig config;
    private MouseTargetTracker mouseTargetTracker;
    private CameraController cameraController;
    private CameraUpdateTask cameraUpdateTask;
    private LockIndicatorManager lockIndicatorManager;

    public HylockPlugin(JavaPluginInit init) {
        super(init);
        instance = this;
        LOGGER.atInfo().log("[Hylock] Initializing %s v%s by Nolucci (Hyvanced)",
            this.getName(),
            this.getManifest().getVersion().toString());
    }

    @Override
    protected void setup() {
        // Initialize configuration
        this.config = new HylockConfig();
        LOGGER.atInfo().log("[Hylock] Configuration loaded");

        // Initialize lock-on manager
        this.lockOnManager = new LockOnManager(this);
        LOGGER.atInfo().log("[Hylock] Lock-on manager initialized");

        // Initialize camera system
        this.cameraController = new CameraController(this.config);
        LOGGER.atInfo().log("[Hylock] Camera controller initialized");

        // Initialize lock indicator manager
        this.lockIndicatorManager = new LockIndicatorManager(this.config);
        LOGGER.atInfo().log("[Hylock] Lock indicator manager initialized");

        // Initialize and start camera update task
        this.cameraUpdateTask = new CameraUpdateTask(this);
        this.cameraUpdateTask.start();
        LOGGER.atInfo().log("[Hylock] Camera update task started");

        // Register commands
        registerCommands();
        LOGGER.atInfo().log("[Hylock] Commands registered");

        // Register event listeners
        registerEvents();
        LOGGER.atInfo().log("[Hylock] Event listeners registered");

        LOGGER.atInfo().log("[Hylock] Plugin setup complete! Use /hylock for help or Middle Mouse to lock.");
    }

    private void registerCommands() {
        // Main hylock command for info and help
        this.getCommandRegistry().registerCommand(new HylockCommand(this));

        // Status and reset commands
        this.getCommandRegistry().registerCommand(new HylockStatusCommand(this));
        this.getCommandRegistry().registerCommand(new HylockResetCommand(this));

        // Quick lock/unlock commands
        this.getCommandRegistry().registerCommand(new LockCommand(this));
        this.getCommandRegistry().registerCommand(new UnlockCommand(this));
        this.getCommandRegistry().registerCommand(new LockSwitchCommand(this));
        this.getCommandRegistry().registerCommand(new LockToggleCommand(this));
    }

    @SuppressWarnings("deprecation")
    private void registerEvents() {
        LOGGER.atInfo().log("[Hylock] Registering event listeners...");

        // Register mouse button event listener for lock-on toggle
        this.getEventRegistry().register(PlayerMouseButtonEvent.class, new LockOnEventListener(this));
        LOGGER.atInfo().log("[Hylock] Registered PlayerMouseButtonEvent listener");

        // Register interact event listener for auto-lock on attack
        // PlayerInteractEvent has KeyType=String, so must use registerGlobal
        this.getEventRegistry().registerGlobal(PlayerInteractEvent.class, new PlayerInteractListener(this));
        LOGGER.atInfo().log("[Hylock] Registered PlayerInteractEvent listener (global)");

        // Register mouse motion tracker - PlayerMouseMotionEvent has KeyType=Void, use register
        this.mouseTargetTracker = new MouseTargetTracker();
        this.getEventRegistry().register(PlayerMouseMotionEvent.class, this.mouseTargetTracker);
        LOGGER.atInfo().log("[Hylock] Registered PlayerMouseMotionEvent listener");

        LOGGER.atInfo().log("[Hylock] All event listeners registered successfully");
    }

    /**
     * Get the singleton instance of the plugin
     */
    public static HylockPlugin getInstance() {
        return instance;
    }

    /**
     * Get the lock-on manager
     */
    public LockOnManager getLockOnManager() {
        return lockOnManager;
    }

    /**
     * Get the plugin configuration
     */
    public HylockConfig getConfig() {
        return config;
    }

    /**
     * Get the mouse target tracker
     */
    public MouseTargetTracker getMouseTargetTracker() {
        return mouseTargetTracker;
    }

    /**
     * Get the plugin logger
     */
    public static HytaleLogger getPluginLogger() {
        return LOGGER;
    }

    /**
     * Get the camera controller
     */
    public CameraController getCameraController() {
        return cameraController;
    }

    /**
     * Get the camera update task
     */
    public CameraUpdateTask getCameraUpdateTask() {
        return cameraUpdateTask;
    }

    /**
     * Get the lock indicator manager
     */
    public LockIndicatorManager getLockIndicatorManager() {
        return lockIndicatorManager;
    }
}
