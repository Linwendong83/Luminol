package me.earthme.luminol.api.entity;

import org.apache.commons.lang3.Validate;
import org.bukkit.Location;
import org.bukkit.PortalType;
import org.bukkit.entity.Entity;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.jetbrains.annotations.NotNull;

/**
 * Fired after the vanilla portal process has placed an entity in the destination world.
 */
public class PostEntityPortalEvent extends Event {
    private static final HandlerList HANDLER_LIST = new HandlerList();

    private final Entity teleportedEntity;
    private final Location from;
    private final Location to;
    private final PortalType portalType;
    private final PlayerTeleportEvent.TeleportCause teleportCause;

    public PostEntityPortalEvent(
        @NotNull Entity teleportedEntity,
        @NotNull Location from,
        @NotNull Location to,
        @NotNull PortalType portalType,
        @NotNull PlayerTeleportEvent.TeleportCause teleportCause
    ) {
        Validate.notNull(teleportedEntity, "teleportedEntity cannot be null!");
        Validate.notNull(from, "from cannot be null!");
        Validate.notNull(to, "to cannot be null!");
        Validate.notNull(portalType, "portalType cannot be null!");
        Validate.notNull(teleportCause, "teleportCause cannot be null!");

        this.teleportedEntity = teleportedEntity;
        this.from = from.clone();
        this.to = to.clone();
        this.portalType = portalType;
        this.teleportCause = teleportCause;
    }

    /**
     * Get the entity which was teleported
     *
     * @return the entity which was teleported
     */
    public @NotNull Entity getTeleportedEntity() {
        return this.teleportedEntity;
    }

    /**
     * Get the portal entry location.
     *
     * @return the portal entry location
     */
    public @NotNull Location getFrom() {
        return this.from.clone();
    }

    /**
     * Get the final entity location after the portal process completed.
     *
     * @return the final entity location
     */
    public @NotNull Location getTo() {
        return this.to.clone();
    }

    /**
     * Get the vanilla portal type that was processed.
     *
     * @return the portal type
     */
    public @NotNull PortalType getPortalType() {
        return this.portalType;
    }

    /**
     * Get the Bukkit teleport cause used by this portal process.
     *
     * @return the teleport cause
     */
    public @NotNull PlayerTeleportEvent.TeleportCause getTeleportCause() {
        return this.teleportCause;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLER_LIST;
    }

    @NotNull
    public static HandlerList getHandlerList() {
        return HANDLER_LIST;
    }
}
