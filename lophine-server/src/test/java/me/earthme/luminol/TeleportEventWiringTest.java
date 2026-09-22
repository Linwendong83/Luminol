package me.earthme.luminol;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Handle;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InvokeDynamicInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

/** Checks compiled call sites as well as the API, so an unused event class cannot pass migration checks. */
class TeleportEventWiringTest {
    private static final String API = "me/earthme/luminol/api/";

    @Test
    void portalCompletionIsWiredIntoThePlacementCallback() throws IOException {
        ClassNode entity = read("net/minecraft/world/entity/Entity");
        MethodNode start = entity.methods.stream().filter(method -> method.name.equals("portalToAsync")).findFirst().orElseThrow();
        List<MethodInsnNode> directCalls = calls(List.of(start));
        int preEvent = indexOf(directCalls, API + "entity/PreEntityPortalEvent", "callEvent");
        int detach = indexOf(directCalls, entity.name, "detachPassengers");
        assertTrue(preEvent >= 0 && detach > preEvent, "Cancellation must happen before removing the entity");
        assertEquals(-1, indexOf(directCalls, API + "entity/PostEntityPortalEvent", "callEvent"), "Do not report completion when merely queueing a portal");

        List<MethodNode> portalPath = withLambdas(entity, "portalToAsync");
        assertTrue(indexOf(calls(portalPath), entity.name, "placeInAsync") >= 0);
        List<MethodNode> postCallbacks = portalPath.stream()
            .filter(method -> indexOf(calls(List.of(method)), API + "entity/PostEntityPortalEvent", "callEvent") >= 0).toList();
        assertEquals(1, postCallbacks.size(), "There must be one portal completion call site");
        List<MethodInsnNode> completion = calls(postCallbacks);
        assertTrue(indexOf(completion, "java/util/function/Consumer", "accept")
            > indexOf(completion, API + "entity/PostEntityPortalEvent", "callEvent"), "Preserve the caller's completion after notifying plugins");

        assertEquals(-1, indexOf(calls(withLambdas(entity, "teleportAsync")), API + "entity/PostEntityPortalEvent", "callEvent"));
    }

    @Test
    void endPlatformRemainsInPortalTransition() throws IOException {
        ClassNode entity = read("net/minecraft/world/entity/Entity");
        assertTrue(indexOf(calls(withLambdas(entity, "findOrCreatePortalAsync")),
            "net/minecraft/world/level/levelgen/feature/EndPlatformFeature", "createEndPlatform") >= 0);
    }

    @Test
    void respawnUsesResolvedFlagsAndFiresAfterPlacement() throws IOException {
        ClassNode player = read("net/minecraft/server/level/ServerPlayer");
        List<MethodNode> respawnPath = withLambdas(player, "respawn");
        List<MethodInsnNode> all = calls(respawnPath);
        assertTrue(all.stream().anyMatch(call -> call.name.equals("isBedSpawn") && call.owner.endsWith("RespawnPosAngle")));
        assertTrue(all.stream().anyMatch(call -> call.name.equals("isAnchorSpawn") && call.owner.endsWith("RespawnPosAngle")));
        assertTrue(all.stream().anyMatch(call -> call.name.equals("placeInAsync")));
        long events = all.stream().filter(call -> call.owner.equals(API + "entity/player/PostPlayerRespawnEvent") && call.name.equals("callEvent")).count();
        assertEquals(1, events);
        for (MethodNode method : respawnPath) {
            if (method.name.equals("respawn")) {
                assertEquals(-1, indexOf(calls(List.of(method)), API + "entity/player/PostPlayerRespawnEvent", "callEvent"));
            }
        }
    }

    @Test
    void endGatewaysDoNotPublishCrossDimensionPortalEvents() throws IOException {
        ClassNode gateway = read("net/minecraft/world/level/block/entity/TheEndGatewayBlockEntity");
        List<MethodInsnNode> calls = calls(gateway.methods);
        assertEquals(-1, indexOf(calls, API + "entity/PreEntityPortalEvent", "callEvent"));
        assertEquals(-1, indexOf(calls, API + "entity/PostEntityPortalEvent", "callEvent"));
    }

    private static ClassNode read(String name) throws IOException {
        try (var stream = TeleportEventWiringTest.class.getClassLoader().getResourceAsStream(name + ".class")) {
            assertNotNull(stream, name);
            ClassNode node = new ClassNode();
            new ClassReader(stream).accept(node, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
            return node;
        }
    }

    private static List<MethodNode> withLambdas(ClassNode owner, String entry) {
        List<MethodNode> result = new ArrayList<>(owner.methods.stream().filter(method -> method.name.equals(entry)).toList());
        Set<MethodNode> seen = new HashSet<>(result);
        for (int i = 0; i < result.size(); i++) {
            for (var instruction : result.get(i).instructions) {
                if (instruction instanceof InvokeDynamicInsnNode dynamic) {
                    for (Object argument : dynamic.bsmArgs) {
                        if (argument instanceof Handle handle && handle.getOwner().equals(owner.name)) {
                            owner.methods.stream().filter(method -> method.name.equals(handle.getName()) && method.desc.equals(handle.getDesc()))
                                .filter(seen::add).forEach(result::add);
                        }
                    }
                }
            }
        }
        return result;
    }

    private static List<MethodInsnNode> calls(List<MethodNode> methods) {
        List<MethodInsnNode> calls = new ArrayList<>();
        for (MethodNode method : methods) {
            for (var instruction : method.instructions) {
                if (instruction instanceof MethodInsnNode call) calls.add(call);
            }
        }
        return calls;
    }

    private static int indexOf(List<MethodInsnNode> calls, String owner, String method) {
        for (int i = 0; i < calls.size(); i++) {
            if (calls.get(i).owner.equals(owner) && calls.get(i).name.equals(method)) return i;
        }
        return -1;
    }
}
