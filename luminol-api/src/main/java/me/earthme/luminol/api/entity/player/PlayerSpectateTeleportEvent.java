package me.earthme.luminol.api.entity.player;

import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerEvent;
import org.jetbrains.annotations.NotNull;

/**
 * Fired before a spectator is teleported to an entity by the spectator menu
 * or by Folia's cross-region spectator camera handling.
 */
public final class PlayerSpectateTeleportEvent extends PlayerEvent implements Cancellable {
    private static final HandlerList HANDLERS = new HandlerList();

    private final Entity target;
    private final Location destination;
    private boolean cancelled;

    public PlayerSpectateTeleportEvent(
            @NotNull Player player,
            @NotNull Entity target,
            @NotNull Location destination
    ) {
        super(player);
        this.target = target;
        this.destination = destination.clone();
    }

    /**
     * Gets the entity the spectator is about to be teleported to.
     */
    public @NotNull Entity getTarget() {
        return this.target;
    }

    /**
     * Gets the target entity's location at the time the teleport was queued.
     */
    public @NotNull Location getDestination() {
        return this.destination.clone();
    }

    @Override
    public boolean isCancelled() {
        return this.cancelled;
    }

    @Override
    public void setCancelled(boolean cancel) {
        this.cancelled = cancel;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }

    public static @NotNull HandlerList getHandlerList() {
        return HANDLERS;
    }
}
