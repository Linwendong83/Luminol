package me.earthme.luminol;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import ca.spottedleaf.concurrentutil.completable.CallbackCompletable;
import ca.spottedleaf.moonrise.common.util.TickThread;
import ca.spottedleaf.moonrise.patches.chunk_system.scheduling.ChunkTaskScheduler;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;
import me.earthme.luminol.api.entity.PostEntityPortalEvent;
import me.earthme.luminol.api.entity.PreEntityPortalEvent;
import me.earthme.luminol.config.modules.function.PortalRateLimiterConfig;
import me.earthme.luminol.utils.thread.SimpleReferenceRWLock;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.Vec3;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.PortalType;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.craftbukkit.entity.CraftEntity;
import org.bukkit.event.Event;
import org.bukkit.event.player.PlayerTeleportEvent.TeleportCause;
import org.bukkit.plugin.PluginManager;
import org.bukkit.support.environment.Normal;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

@Normal
class PortalTransferTest {
    @Test
    void completionWaitsForPlacementAndPreservesTransitionAndCallbackOrder() throws Exception {
        try (Harness h = new Harness()) {
            assertTrue(h.start());
            assertEquals(1, h.pre.size());
            assertTrue(h.post.isEmpty());
            TeleportTransition info = mock(TeleportTransition.class);
            when(info.postTeleportTransition()).thenReturn(entity -> h.order.add("transition"));
            h.searches.getFirst().complete(info);
            assertTrue(h.post.isEmpty(), "Finding a portal is not completing a transfer");
            assertTrue(h.completions.isEmpty());
            h.placements.getFirst().accept(h.entity);

            assertEquals(1, h.post.size());
            PostEntityPortalEvent event = h.post.getFirst();
            assertEquals(new Location(h.originWorld, 80, 64, 40), event.getFrom());
            assertEquals(new Location(h.destinationWorld, 10, 70, 5), event.getTo());
            assertEquals(PortalType.NETHER, event.getPortalType());
            assertEquals(TeleportCause.NETHER_PORTAL, event.getTeleportCause());
            assertEquals(List.of("transition", "post", "callback"), h.order);
            assertEquals(List.of(h.entity), h.completions);
            assertTrue(h.lock.acquireWrite(System.nanoTime() + 1_000_000_000L), "The level read reference must be released");
            h.lock.releaseWrite();
        }
    }

    @Test
    void cancelledPortalDoesNotRemoveEntityOrPublishCompletion() throws Exception {
        try (Harness h = new Harness()) {
            h.cancel = true;
            assertFalse(h.start());
            assertEquals(1, h.pre.size());
            h.assertNoTransfer();
        }
    }

    @Test
    void ineligibleEntityDoesNotPublishPortalEvents() throws Exception {
        try (Harness h = new Harness()) {
            when(h.entity.canPortalAsync(h.destination, true)).thenReturn(false);
            assertFalse(h.start());
            assertTrue(h.pre.isEmpty());
            h.assertNoTransfer();
        }
    }

    @Test
    void unloadingDestinationDoesNotPublishCompletion() throws Exception {
        try (Harness h = new Harness()) {
            h.lock.blockReadingReferencing();
            assertFalse(h.start());
            h.assertNoTransfer();
        }
    }

    @Test
    void shutdownAbandonmentDoesNotPublishSuccessfulCompletion() throws Exception {
        try (Harness h = new Harness()) {
            when(h.origin.removePendingTeleport(any())).thenReturn(false);
            assertTrue(h.start());
            h.searches.getFirst().complete(mock(TeleportTransition.class));
            assertTrue(h.placements.isEmpty());
            assertTrue(h.post.isEmpty());
            assertTrue(h.completions.isEmpty());
        }
    }

    private static final class Harness implements AutoCloseable {
        final ServerLevel origin = mock(ServerLevel.class);
        final ServerLevel destination = mock(ServerLevel.class);
        final CraftWorld originWorld = mock(CraftWorld.class);
        final CraftWorld destinationWorld = mock(CraftWorld.class);
        final CraftEntity bukkitEntity = mock(CraftEntity.class);
        final SimpleReferenceRWLock lock = new SimpleReferenceRWLock();
        final List<CallbackCompletable<TeleportTransition>> searches = new ArrayList<>();
        final List<Consumer<Entity>> placements = new ArrayList<>();
        final List<Entity> completions = new ArrayList<>();
        final List<PreEntityPortalEvent> pre = new ArrayList<>();
        final List<PostEntityPortalEvent> post = new ArrayList<>();
        final List<String> order = new ArrayList<>();
        final Entity entity;
        final Method portal;
        final MockedStatic<Bukkit> bukkit;
        final MockedStatic<TickThread> threads;
        final boolean previousBackpressure = PortalRateLimiterConfig.destinationBackpressureEnabled;
        boolean cancel;
        boolean detached;

        Harness() throws Exception {
            // Run the real portal entrypoint and completion lambdas with controlled chunk search/placement.
            entity = mock(Entity.class, invocation -> switch (invocation.getMethod().getName()) {
                case "portalToAsync" -> invocation.callRealMethod();
                case "detachPassengers" -> {
                    detached = true;
                    yield new Entity.EntityTreeNode(null, (Entity) invocation.getMock());
                }
                case "transformForAsyncTeleport" -> invocation.getMock();
                case "findOrCreatePortalAsync" -> {
                    searches.add(invocation.getArgument(4));
                    yield null;
                }
                case "placeInAsync" -> {
                    placements.add(invocation.getArgument(4));
                    yield null;
                }
                default -> RETURNS_DEFAULTS.answer(invocation);
            });
            portal = Arrays.stream(Entity.class.getDeclaredMethods()).filter(method -> method.getName().equals("portalToAsync")).findFirst().orElseThrow();
            portal.setAccessible(true);
            var level = Entity.class.getDeclaredField("level");
            level.setAccessible(true);
            level.set(entity, origin);
            var unloadLock = ServerLevel.class.getField("levelUnloadStateLock");
            unloadLock.setAccessible(true);
            unloadLock.set(destination, lock);
            when(entity.canPortalAsync(destination, true)).thenReturn(true);
            when(entity.position()).thenReturn(new Vec3(80, 64, 40));
            when(entity.getBukkitEntity()).thenReturn(bukkitEntity);
            when(origin.getWorld()).thenReturn(originWorld);
            when(destination.getWorld()).thenReturn(destinationWorld);
            when(bukkitEntity.getLocation()).thenReturn(new Location(destinationWorld, 10, 70, 5));
            when(origin.removePendingTeleport(any())).thenReturn(true);
            ChunkTaskScheduler scheduler = mock(ChunkTaskScheduler.class);
            var manager = ChunkTaskScheduler.class.getField("chunkHolderManager");
            manager.setAccessible(true);
            manager.set(scheduler, mock(manager.getType()));
            when(origin.moonrise$getChunkTaskScheduler()).thenReturn(scheduler);

            PluginManager plugins = mock(PluginManager.class);
            doAnswer(invocation -> {
                Event event = invocation.getArgument(0);
                if (event instanceof PreEntityPortalEvent before) {
                    pre.add(before);
                    before.setCancelled(cancel);
                } else if (event instanceof PostEntityPortalEvent after) {
                    post.add(after);
                    order.add("post");
                }
                return null;
            }).when(plugins).callEvent(any());
            bukkit = mockStatic(Bukkit.class);
            bukkit.when(Bukkit::getPluginManager).thenReturn(plugins);
            threads = mockStatic(TickThread.class);
            PortalRateLimiterConfig.destinationBackpressureEnabled = false;
        }

        boolean start() throws Exception {
            Object nether = Arrays.stream(portal.getParameterTypes()[3].getEnumConstants())
                .filter(value -> value.toString().equals("NETHER")).findFirst().orElseThrow();
            Consumer<Entity> complete = result -> {
                completions.add(result);
                order.add("callback");
            };
            return (boolean) portal.invoke(entity, destination, new BlockPos(80, 64, 40), true, nether, complete);
        }

        void assertNoTransfer() {
            assertFalse(detached);
            assertTrue(searches.isEmpty());
            assertTrue(placements.isEmpty());
            assertTrue(post.isEmpty());
            assertTrue(completions.isEmpty());
        }

        @Override
        public void close() {
            PortalRateLimiterConfig.destinationBackpressureEnabled = previousBackpressure;
            threads.close();
            bukkit.close();
        }
    }
}
