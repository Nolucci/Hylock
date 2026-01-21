package com.hyvanced.hylock.commands;

import java.util.UUID;

import javax.annotation.Nonnull;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.GameMode;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hyvanced.hylock.HylockPlugin;
import com.hyvanced.hylock.lockon.LockOnManager;
import com.hyvanced.hylock.lockon.LockOnState;
import com.hyvanced.hylock.lockon.TargetInfo;

/**
 * Command to switch to the next target while locked.
 */
public class LockSwitchCommand extends AbstractPlayerCommand {

    private final HylockPlugin plugin;

    /**
     * Constructs a new LockSwitchCommand.
     *
     * @param plugin the Hylock plugin instance
     */
    public LockSwitchCommand(HylockPlugin plugin) {
        super("lockswitch", "Switch to the next lock-on target (Hylock)");
        this.setPermissionGroup(GameMode.Adventure);
        this.plugin = plugin;
    }

    /**
     * Executes the switch command, changing to the next available target.
     *
     * @param ctx       the command context
     * @param store     the entity store
     * @param ref       the entity reference
     * @param playerRef the player reference
     * @param world     the world instance
     */
    @Override
    protected void execute(@Nonnull CommandContext ctx,
            @Nonnull Store<EntityStore> store,
            @Nonnull Ref<EntityStore> ref,
            @Nonnull PlayerRef playerRef,
            @Nonnull World world) {

        LockOnManager lockManager = plugin.getLockOnManager();
        UUID playerId = playerRef.getUuid();

        if (lockManager.getState(playerId) != LockOnState.LOCKED) {
            ctx.sendMessage(Message.raw("[Hylock] You must be locked onto a target to switch."));
            ctx.sendMessage(Message.raw("Use Middle Mouse to lock onto a target."));
            return;
        }

        boolean switched = lockManager.switchTarget(playerId, 1);

        if (switched) {
            TargetInfo newTarget = lockManager.getLockedTarget(playerId);
            ctx.sendMessage(Message.raw("[Hylock] Switched to " + newTarget.getEntityName()));
        } else {
            ctx.sendMessage(Message.raw("[Hylock] No other targets available."));
        }
    }
}
