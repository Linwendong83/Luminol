package me.earthme.luminol.api;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

import me.earthme.luminol.api.entity.PostEntityPortalEvent;
import me.earthme.luminol.api.entity.PreEntityPortalEvent;
import me.earthme.luminol.api.entity.player.PlayerSpectateTeleportEvent;
import me.earthme.luminol.api.entity.player.PostPlayerRespawnEvent;
import org.bukkit.Location;
import org.bukkit.PortalType;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent.TeleportCause;
import org.junit.jupiter.api.Test;

class TeleportEventContractTest {
    @Test
    void portalEventsKeepSnapshotsAndCause() {
        Entity entity = mock(Entity.class);
        World origin = mock(World.class);
        World destination = mock(World.class);
        Location from = new Location(origin, 80, 64, 40);
        Location to = new Location(destination, 10, 70, 5);
        PreEntityPortalEvent pre = new PreEntityPortalEvent(entity, from, destination, PortalType.NETHER, TeleportCause.NETHER_PORTAL);
        PostEntityPortalEvent post = new PostEntityPortalEvent(entity, from, to, PortalType.NETHER, TeleportCause.NETHER_PORTAL);

        from.setX(999);
        to.setY(999);
        pre.getPortalPos().setX(999);
        post.getFrom().setX(999);
        post.getTo().setY(999);

        assertSame(entity, pre.getEntity());
        assertSame(destination, pre.getDestination());
        assertSame(entity, post.getTeleportedEntity());
        assertEquals(80, pre.getPortalPos().getX());
        assertEquals(80, post.getFrom().getX());
        assertEquals(70, post.getTo().getY());
        assertEquals(PortalType.NETHER, pre.getPortalType());
        assertEquals(PortalType.NETHER, post.getPortalType());
        assertEquals(TeleportCause.NETHER_PORTAL, pre.getTeleportCause());
        assertEquals(TeleportCause.NETHER_PORTAL, post.getTeleportCause());
        assertFalse(pre.isCancelled());
        pre.setCancelled(true);
        assertTrue(pre.isCancelled());
    }

    @Test
    void endPortalHasDistinctTypeAndCause() {
        PostEntityPortalEvent event = new PostEntityPortalEvent(mock(Entity.class),
            new Location(null, 0, 64, 0), new Location(null, 100, 49, 0), PortalType.ENDER, TeleportCause.END_PORTAL);
        assertEquals(PortalType.ENDER, event.getPortalType());
        assertEquals(TeleportCause.END_PORTAL, event.getTeleportCause());
    }

    @Test
    void respawnFlagsAndLocationDescribeThisRespawn() {
        Player player = mock(Player.class);
        Location location = new Location(mock(World.class), 10, 70, 20);
        PostPlayerRespawnEvent bed = new PostPlayerRespawnEvent(player, location, true, false, false, PlayerRespawnEvent.RespawnReason.DEATH);
        PostPlayerRespawnEvent anchor = new PostPlayerRespawnEvent(player, location, false, true, false, PlayerRespawnEvent.RespawnReason.DEATH);
        PostPlayerRespawnEvent missing = new PostPlayerRespawnEvent(player, location, false, false, true, PlayerRespawnEvent.RespawnReason.DEATH);
        PostPlayerRespawnEvent end = new PostPlayerRespawnEvent(player, location, false, false, false, PlayerRespawnEvent.RespawnReason.END_PORTAL);

        location.setY(999);
        bed.getRespawnLocation().setY(999);
        assertSame(player, bed.getPlayer());
        assertEquals(70, bed.getRespawnLocation().getY());
        assertTrue(bed.isBedSpawn());
        assertFalse(bed.isAnchorSpawn());
        assertTrue(anchor.isAnchorSpawn());
        assertFalse(anchor.isBedSpawn());
        assertTrue(missing.isMissingRespawnBlock());
        assertFalse(end.isMissingRespawnBlock());
        assertEquals(PlayerRespawnEvent.RespawnReason.END_PORTAL, end.getRespawnReason());
        assertTrue(anchor.getRespawnFlags().contains(PlayerRespawnEvent.RespawnFlag.ANCHOR_SPAWN));
        assertTrue(end.getRespawnFlags().contains(PlayerRespawnEvent.RespawnFlag.END_PORTAL));
    }

    @Test
    void spectatorEventCanBeCancelledWithoutChangingDestinationSnapshot() {
        Player observer = mock(Player.class);
        Entity target = mock(Entity.class);
        Location destination = new Location(mock(World.class), 1, 2, 3);
        PlayerSpectateTeleportEvent event = new PlayerSpectateTeleportEvent(observer, target, destination);
        destination.setX(999);
        event.getDestination().setX(999);
        assertSame(observer, event.getPlayer());
        assertSame(target, event.getTarget());
        assertEquals(1, event.getDestination().getX());
        assertFalse(event.isCancelled());
        event.setCancelled(true);
        assertTrue(event.isCancelled());
    }
}
