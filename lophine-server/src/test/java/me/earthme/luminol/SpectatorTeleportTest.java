package me.earthme.luminol;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import io.papermc.paper.threadedregions.EntityScheduler;
import io.papermc.paper.threadedregions.TeleportUtils;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import me.earthme.luminol.api.entity.player.PlayerSpectateTeleportEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.craftbukkit.entity.CraftEntity;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.event.player.PlayerTeleportEvent.TeleportCause;
import org.bukkit.plugin.PluginManager;
import org.bukkit.support.environment.Normal;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

@Normal
class SpectatorTeleportTest {
    @Test
    void eventRunsAfterTargetSnapshotAndOnObserverTask() throws Exception {
        try (Harness h = new Harness()) {
            h.start(TeleportCause.SPECTATE);
            assertTrue(h.events.isEmpty());
            h.targetTasks.getFirst().accept(h.target);
            assertTrue(h.events.isEmpty());
            when(h.targetBukkit.getLocation()).thenReturn(new Location(h.world, 999, 80, 999));
            h.observerTasks.getFirst().accept(h.observer);

            assertEquals(1, h.events.size());
            assertEquals(10, h.events.getFirst().getDestination().getX());
            assertSame(h.observerBukkit, h.events.getFirst().getPlayer());
            assertSame(h.targetBukkit, h.events.getFirst().getTarget());
            assertEquals(List.of(h.observer), h.completions);
            verify(h.targetBukkit, times(1)).getLocation();
            h.verifyTransferred();
        }
    }

    @Test
    void cancellationPreventsTransferAndCompletesOnce() throws Exception {
        try (Harness h = new Harness()) {
            h.cancel = true;
            h.start(TeleportCause.SPECTATE);
            h.targetTasks.getFirst().accept(h.target);
            h.observerTasks.getFirst().accept(h.observer);
            assertEquals(1, h.events.size());
            h.assertFailedOnce();
            h.verifyNotTransferred();
        }
    }

    @Test
    void retiredTargetFailsWithoutFiringEvent() throws Exception {
        try (Harness h = new Harness()) {
            h.start(TeleportCause.SPECTATE);
            h.targetRetired.getFirst().accept(h.target);
            h.assertFailedOnce();
            assertTrue(h.events.isEmpty());
            assertTrue(h.observerTasks.isEmpty());
        }
    }

    @Test
    void observerRetiringAfterSnapshotFailsWithoutFiringEvent() throws Exception {
        try (Harness h = new Harness()) {
            h.start(TeleportCause.SPECTATE);
            h.targetTasks.getFirst().accept(h.target);
            h.observerRetired.getFirst().accept(h.observer);
            h.assertFailedOnce();
            assertTrue(h.events.isEmpty());
            h.verifyNotTransferred();
        }
    }

    @Test
    void alreadyRetiredTargetFailsOnce() throws Exception {
        try (Harness h = new Harness()) {
            when(h.targetScheduler.schedule(any(), any(), anyLong())).thenReturn(false);
            h.start(TeleportCause.SPECTATE);
            h.assertFailedOnce();
            assertTrue(h.events.isEmpty());
        }
    }

    @Test
    void ordinaryTeleportDoesNotFireSpectatorEvent() throws Exception {
        try (Harness h = new Harness()) {
            h.start(TeleportCause.COMMAND);
            h.targetTasks.getFirst().accept(h.target);
            h.observerTasks.getFirst().accept(h.observer);
            assertTrue(h.events.isEmpty());
            assertEquals(List.of(h.observer), h.completions);
        }
    }

    private static final class Harness implements AutoCloseable {
        final ServerPlayer observer = mock(ServerPlayer.class);
        final Entity target = mock(Entity.class);
        final CraftPlayer observerBukkit = mock(CraftPlayer.class);
        final CraftEntity targetBukkit = mock(CraftEntity.class);
        final CraftWorld world = mock(CraftWorld.class);
        final ServerLevel level = mock(ServerLevel.class);
        final EntityScheduler observerScheduler = mock(EntityScheduler.class);
        final EntityScheduler targetScheduler = mock(EntityScheduler.class);
        final List<Consumer<Entity>> observerTasks = new ArrayList<>();
        final List<Consumer<Entity>> targetTasks = new ArrayList<>();
        final List<Consumer<Entity>> observerRetired = new ArrayList<>();
        final List<Consumer<Entity>> targetRetired = new ArrayList<>();
        final List<Entity> completions = new ArrayList<>();
        final List<PlayerSpectateTeleportEvent> events = new ArrayList<>();
        final MockedStatic<Bukkit> bukkit;
        boolean cancel;

        Harness() throws Exception {
            when(observer.getBukkitEntity()).thenReturn(observerBukkit);
            when(target.getBukkitEntity()).thenReturn(targetBukkit);
            when(observer.isSpectator()).thenReturn(true);
            when(world.getHandle()).thenReturn(level);
            when(targetBukkit.getLocation()).thenReturn(new Location(world, 10, 70, 20));
            // Mockito bypasses constructors, so inject the schedulers normally created by CraftEntity.
            var field = CraftEntity.class.getField("taskScheduler");
            field.setAccessible(true);
            field.set(observerBukkit, observerScheduler);
            field.set(targetBukkit, targetScheduler);
            captureTasks(observerScheduler, observerTasks, observerRetired);
            captureTasks(targetScheduler, targetTasks, targetRetired);
            doAnswer(invocation -> {
                Consumer<Entity> completion = invocation.getArgument(7);
                if (completion != null) completion.accept(observer);
                return true;
            }).when(observer).teleportAsync(eq(level), any(Vec3.class), eq(90F), eq(30F), isNull(), any(), eq(0L), any());
            PluginManager plugins = mock(PluginManager.class);
            doAnswer(invocation -> {
                PlayerSpectateTeleportEvent event = invocation.getArgument(0);
                events.add(event);
                event.setCancelled(cancel);
                return null;
            }).when(plugins).callEvent(any(PlayerSpectateTeleportEvent.class));
            bukkit = mockStatic(Bukkit.class);
            bukkit.when(Bukkit::getPluginManager).thenReturn(plugins);
        }

        private static void captureTasks(EntityScheduler scheduler, List<Consumer<Entity>> tasks, List<Consumer<Entity>> retired) {
            when(scheduler.schedule(any(), any(), eq(1L))).thenAnswer(invocation -> {
                tasks.add(invocation.getArgument(0));
                retired.add(invocation.getArgument(1));
                return true;
            });
        }

        void start(TeleportCause cause) {
            TeleportUtils.teleport(observer, false, target, 90F, 30F, 0L, cause, completions::add);
        }

        void assertFailedOnce() {
            assertEquals(1, completions.size());
            assertNull(completions.getFirst());
        }

        void verifyTransferred() {
            verify(observer).teleportAsync(eq(level), eq(new Vec3(10, 70, 20)), eq(90F), eq(30F), isNull(), eq(TeleportCause.SPECTATE), eq(0L), any());
        }

        void verifyNotTransferred() {
            verify(observer, never()).teleportAsync(any(), any(), any(), any(), any(), any(), anyLong(), any());
        }

        @Override
        public void close() {
            bukkit.close();
        }
    }
}
