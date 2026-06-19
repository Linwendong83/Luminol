package me.earthme.luminol.api.portal;

import org.apache.commons.lang3.Validate;
import org.bukkit.Location;
import org.bukkit.PortalType;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.jetbrains.annotations.NotNull;

/**
 * Fired when the vanilla portal process has calculated the destination search or target position.
 */
public class PortalLocateEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();

    private final Entity entity;
    private final Location original;
    private final World destinationWorld;
    private final PortalType portalType;
    private final PlayerTeleportEvent.TeleportCause teleportCause;
    private Location destination;

    public PortalLocateEvent(
        @NotNull Entity entity,
        @NotNull Location original,
        @NotNull Location destination,
        @NotNull World destinationWorld,
        @NotNull PortalType portalType,
        @NotNull PlayerTeleportEvent.TeleportCause teleportCause
    ) {
        Validate.notNull(entity, "entity cannot be null!");
        Validate.notNull(original, "original couldn't be null!");
        Validate.notNull(destination, "destination couldn't be null!");
        Validate.notNull(destinationWorld, "destinationWorld couldn't be null!");
        Validate.notNull(portalType, "portalType couldn't be null!");
        Validate.notNull(teleportCause, "teleportCause couldn't be null!");

        this.entity = entity;
        this.original = original.clone();
        this.destination = destination.clone();
        this.destinationWorld = destinationWorld;
        this.portalType = portalType;
        this.teleportCause = teleportCause;
    }

    /**
     * Get the entity being teleported by the portal process.
     *
     * @return the entity
     */
    public @NotNull Entity getEntity() {
        return this.entity;
    }

    /**
     * Get the destination position of this teleportation
     *
     * @return the destination position
     */
    public @NotNull Location getDestination() {
        return this.destination.clone();
    }

    /**
     * Set the destination search or target position used by the portal process.
     *
     * @param destination the new destination position
     */
    public void setDestination(@NotNull Location destination) {
        Validate.notNull(destination, "destination couldn't be null!");
        Validate.notNull(destination.getWorld(), "destination world couldn't be null!");
        Validate.isTrue(destination.getWorld().equals(this.destinationWorld), "destination world cannot be changed");
        destination.checkFinite();
        this.destination = destination.clone();
    }

    /**
     * Get the original portal position of this teleportation
     *
     * @return the original portal position
     */
    public @NotNull Location getOriginal() {
        return this.original.clone();
    }

    /**
     * Get the destination world for this portal process.
     *
     * @return the destination world
     */
    public @NotNull World getDestinationWorld() {
        return this.destinationWorld;
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
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }

    @NotNull
    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
