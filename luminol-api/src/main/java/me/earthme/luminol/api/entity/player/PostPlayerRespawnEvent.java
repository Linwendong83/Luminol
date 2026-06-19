package me.earthme.luminol.api.entity.player;

import io.papermc.paper.event.player.AbstractRespawnEvent;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.jetbrains.annotations.NotNull;

/**
 * Fired after the Folia async respawn process has placed a player in the respawn world.
 */
public class PostPlayerRespawnEvent extends AbstractRespawnEvent {
    private static final HandlerList HANDLERS = new HandlerList();

    public PostPlayerRespawnEvent(
        @NotNull Player player,
        @NotNull Location respawnLocation,
        boolean bedSpawn,
        boolean anchorSpawn,
        boolean missingRespawnBlock,
        @NotNull PlayerRespawnEvent.RespawnReason respawnReason
    ) {
        super(player, respawnLocation, bedSpawn, anchorSpawn, missingRespawnBlock, respawnReason);
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
