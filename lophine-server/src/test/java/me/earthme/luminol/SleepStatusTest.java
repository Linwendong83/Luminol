package me.earthme.luminol;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.List;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.SleepStatus;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.support.environment.Normal;
import org.junit.jupiter.api.Test;

@Normal
class SleepStatusTest {
    @Test
    void ignoredAndSpectatorPlayersDoNotChangeTheDenominator() {
        ServerPlayer sleeper = player(false, false, true, true);
        ServerPlayer awake = player(false, false, false, false);
        ServerPlayer hidden = player(false, true, false, false);
        ServerPlayer spectator = player(true, false, false, false);
        List<ServerPlayer> players = List.of(sleeper, awake, hidden, spectator);
        SleepStatus status = new SleepStatus();
        status.update(players);
        assertEquals(2, status.sleepersNeeded(100));
        assertTrue(status.areEnoughSleeping(50));
        assertTrue(status.areEnoughDeepSleeping(50, players));
        assertFalse(status.areEnoughSleeping(100));
        assertFalse(status.areEnoughDeepSleeping(100, players));
    }

    @Test
    void ignoredDeepSleepersCannotTriggerNightSkipping() {
        List<ServerPlayer> players = List.of(
            player(false, true, true, true), player(true, false, true, true), player(false, false, false, false));
        SleepStatus status = new SleepStatus();
        status.update(players);
        assertFalse(status.areEnoughSleeping(100));
        assertFalse(status.areEnoughDeepSleeping(100, players));
    }

    @Test
    void nobodyParticipatingCannotSkipNightEvenAtZeroPercent() {
        List<ServerPlayer> players = List.of(player(false, true, true, true), player(true, false, true, true));
        SleepStatus status = new SleepStatus();
        status.update(players);
        assertEquals(1, status.sleepersNeeded(0));
        assertFalse(status.areEnoughSleeping(0));
        assertFalse(status.areEnoughDeepSleeping(0, players));
    }

    @Test
    void realDeepSleepIsRequired() {
        List<ServerPlayer> players = List.of(player(false, false, true, false), player(false, true, false, false));
        SleepStatus status = new SleepStatus();
        status.update(players);
        assertTrue(status.areEnoughSleeping(100));
        assertFalse(status.areEnoughDeepSleeping(100, players));
    }

    @Test
    void changingIgnoredStateRecalculatesTheThreshold() {
        ServerPlayer sleeper = player(false, false, true, true);
        ServerPlayer hidden = player(false, true, false, false);
        List<ServerPlayer> players = List.of(sleeper, hidden);
        SleepStatus status = new SleepStatus();
        status.update(players);
        assertTrue(status.areEnoughDeepSleeping(100, players));

        when(hidden.getBukkitEntity().isSleepingIgnored()).thenReturn(false);
        hidden.fauxSleeping = false;
        assertTrue(status.update(players));
        assertEquals(2, status.sleepersNeeded(100));
        assertFalse(status.areEnoughDeepSleeping(100, players));

        when(hidden.getBukkitEntity().isSleepingIgnored()).thenReturn(true);
        hidden.fauxSleeping = true;
        assertTrue(status.update(players));
        assertTrue(status.areEnoughDeepSleeping(100, players));
    }

    private static ServerPlayer player(boolean spectator, boolean ignored, boolean sleeping, boolean deepSleeping) {
        ServerPlayer player = mock(ServerPlayer.class);
        CraftPlayer bukkit = mock(CraftPlayer.class);
        when(player.getBukkitEntity()).thenReturn(bukkit);
        when(player.isSpectator()).thenReturn(spectator);
        when(player.isSleeping()).thenReturn(sleeping);
        when(player.isSleepingLongEnough()).thenReturn(deepSleeping);
        when(bukkit.isSleepingIgnored()).thenReturn(ignored);
        player.fauxSleeping = ignored;
        return player;
    }
}
