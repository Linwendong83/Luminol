package me.earthme.luminol.api.entity;

import org.apache.commons.lang3.Validate;
import org.bukkit.Location;
import org.bukkit.PortalType;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.jetbrains.annotations.NotNull;

/**
 * Fired before the vanilla portal process removes an entity from its current world.
 */
public class PreEntityPortalEvent extends Event implements Cancellable {
    private static final HandlerList HANDLERS = new HandlerList();

    private final Entity entity;
    private final Location portalPos;
    private final World destination;
    private final PortalType portalType;
    private final PlayerTeleportEvent.TeleportCause teleportCause;

    private boolean cancelled = false;

    public PreEntityPortalEvent(
        @NotNull Entity entity,
        @NotNull Location portalPos,
        @NotNull World destination,
        @NotNull PortalType portalType,
        @NotNull PlayerTeleportEvent.TeleportCause teleportCause
    ) {
        Validate.notNull(entity, "entity cannot be null!");
        Validate.notNull(portalPos, "portalPos cannot be null!");
        Validate.notNull(destination, "destination cannot be null!");
        Validate.notNull(portalType, "portalType cannot be null!");
        Validate.notNull(teleportCause, "teleportCause cannot be null!");

        this.entity = entity;
        this.portalPos = portalPos.clone();
        this.destination = destination;
        this.portalType = portalType;
        this.teleportCause = teleportCause;
    }

    /**
     * Get the entity that is about to teleport
     *
     * @return the entity
     */
    public @NotNull Entity getEntity() {
        return this.entity;
    }

    /**
     * Get the location of the portal
     *
     * @return the portal location
     */
    public @NotNull Location getPortalPos() {
        return this.portalPos.clone();
    }

    /**
     * Get the destination world
     *
     * @return the destination world
     */
    public @NotNull World getDestination() {
        return this.destination;
    }

    /**
     * Get the vanilla portal type being processed.
     *
     * @return the portal type
     */
    public @NotNull PortalType getPortalType() {
        return this.portalType;
    }

    /**
     * Get the Bukkit teleport cause that will be used for this portal process.
     *
     * @return the teleport cause
     */
    public @NotNull PlayerTeleportEvent.TeleportCause getTeleportCause() {
        return this.teleportCause;
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

    @NotNull
    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
