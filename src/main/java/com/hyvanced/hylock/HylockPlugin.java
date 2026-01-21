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
 * Hylock - Zelda-style target lock-on system for Hytale.
 * Part of the Hyvanced plugin suite by Nolucci.
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

    /**
     * Constructs a new HylockPlugin.
     *
     * @param init the plugin initialization data
     */
    public HylockPlugin(JavaPluginInit init) {
        super(init);
        instance = this;
        LOGGER.atInfo().log("[Hylock] Initializing %s v%s by Nolucci (Hyvanced)",
            this.getName(),
            this.getManifest().getVersion().toString());
    }

    /**
     * Sets up the plugin by initializing all components and registering commands and events.
     */
    @Override
    protected void setup() {
        this.config = new HylockConfig();
        LOGGER.atInfo().log("[Hylock] Configuration loaded");

        this.lockOnManager = new LockOnManager(this);
        LOGGER.atInfo().log("[Hylock] Lock-on manager initialized");

        this.cameraController = new CameraController(this.config);
        LOGGER.atInfo().log("[Hylock] Camera controller initialized");

        this.lockIndicatorManager = new LockIndicatorManager(this.config);
        LOGGER.atInfo().log("[Hylock] Lock indicator manager initialized");

        this.cameraUpdateTask = new CameraUpdateTask(this);
        this.cameraUpdateTask.start();
        LOGGER.atInfo().log("[Hylock] Camera update task started");

        registerCommands();
        LOGGER.atInfo().log("[Hylock] Commands registered");

        registerEvents();
        LOGGER.atInfo().log("[Hylock] Event listeners registered");

        LOGGER.atInfo().log("[Hylock] Plugin setup complete! Use /hylock for help or Middle Mouse to lock.");
    }

    /**
     * Registers all plugin commands.
     */
    private void registerCommands() {
        this.getCommandRegistry().registerCommand(new HylockCommand(this));
        this.getCommandRegistry().registerCommand(new HylockStatusCommand(this));
        this.getCommandRegistry().registerCommand(new HylockResetCommand(this));
        this.getCommandRegistry().registerCommand(new LockCommand(this));
        this.getCommandRegistry().registerCommand(new UnlockCommand(this));
        this.getCommandRegistry().registerCommand(new LockSwitchCommand(this));
        this.getCommandRegistry().registerCommand(new LockToggleCommand(this));
    }

    /**
     * Registers all event listeners.
     */
    @SuppressWarnings("deprecation")
    private void registerEvents() {
        LOGGER.atInfo().log("[Hylock] Registering event listeners...");

        this.getEventRegistry().register(PlayerMouseButtonEvent.class, new LockOnEventListener(this));
        LOGGER.atInfo().log("[Hylock] Registered PlayerMouseButtonEvent listener");

        this.getEventRegistry().registerGlobal(PlayerInteractEvent.class, new PlayerInteractListener(this));
        LOGGER.atInfo().log("[Hylock] Registered PlayerInteractEvent listener (global)");

        this.mouseTargetTracker = new MouseTargetTracker();
        this.getEventRegistry().register(PlayerMouseMotionEvent.class, this.mouseTargetTracker);
        LOGGER.atInfo().log("[Hylock] Registered PlayerMouseMotionEvent listener");

        LOGGER.atInfo().log("[Hylock] All event listeners registered successfully");
    }

    /**
     * Returns the singleton instance of the plugin.
     *
     * @return the plugin instance
     */
    public static HylockPlugin getInstance() {
        return instance;
    }

    /**
     * Returns the lock-on manager.
     *
     * @return the lock-on manager
     */
    public LockOnManager getLockOnManager() {
        return lockOnManager;
    }

    /**
     * Returns the plugin configuration.
     *
     * @return the configuration
     */
    public HylockConfig getConfig() {
        return config;
    }

    /**
     * Returns the mouse target tracker.
     *
     * @return the mouse target tracker
     */
    public MouseTargetTracker getMouseTargetTracker() {
        return mouseTargetTracker;
    }

    /**
     * Returns the plugin logger.
     *
     * @return the logger
     */
    public static HytaleLogger getPluginLogger() {
        return LOGGER;
    }

    /**
     * Returns the camera controller.
     *
     * @return the camera controller
     */
    public CameraController getCameraController() {
        return cameraController;
    }

    /**
     * Returns the camera update task.
     *
     * @return the camera update task
     */
    public CameraUpdateTask getCameraUpdateTask() {
        return cameraUpdateTask;
    }

    /**
     * Returns the lock indicator manager.
     *
     * @return the lock indicator manager
     */
    public LockIndicatorManager getLockIndicatorManager() {
        return lockIndicatorManager;
    }
}
