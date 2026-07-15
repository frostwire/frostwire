/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.search.relay.icebridge;

import com.frostwire.search.relay.DistributedSearchTransport;
import com.frostwire.search.relay.IdentityKeys;
import com.frostwire.search.relay.icebridge.client.IceBridgeClient;
import com.frostwire.search.relay.icebridge.client.IceBridgeSearchTransport;
import com.frostwire.search.relay.icebridge.control.PeerInfo;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Stress / load tests for the IceBridge mesh and control plane.
 *
 * <p><b>No hardcoded lab IPs.</b> All endpoints bind loopback with ephemeral
 * ports ({@code ServerSocket(0)}). Scale knobs (optional env):
 * <ul>
 *   <li>{@code ICEBRIDGE_STRESS_MESSAGES} — messages per blast (default 200)</li>
 *   <li>{@code ICEBRIDGE_STRESS_CLIENTS} — concurrent USE_REMOTE clients (default 8)</li>
 *   <li>{@code ICEBRIDGE_STRESS_THREADS} — sender threads (default 8)</li>
 * </ul>
 *
 * <p>Tagged {@code stress} so suites can include/exclude:
 * {@code ./gradlew test --tests '*IceBridgeStressTest*'}
 *
 * <p>Also asserts the <em>protocol-agnostic</em> contract: a pure FORWARDER
 * delivers opaque bytes for non-SEARCH protocol IDs without interpreting them.
 */
@Tag("stress")
class IceBridgeStressTest {

    private static final String LOOPBACK = "127.0.0.1";

    private final List<AutoCloseable> resources = new ArrayList<>();

    @AfterEach
    void tearDown() {
        Collections.reverse(resources);
        for (AutoCloseable r : resources) {
            try {
                r.close();
            } catch (Throwable ignored) {
            }
        }
    }

    private static int stressMessages() {
        return envInt("ICEBRIDGE_STRESS_MESSAGES", 200, 20, 50_000);
    }

    private static int stressClients() {
        return envInt("ICEBRIDGE_STRESS_CLIENTS", 8, 2, 64);
    }

    private static int stressThreads() {
        return envInt("ICEBRIDGE_STRESS_THREADS", 8, 2, 64);
    }

    private static int envInt(String key, int def, int min, int max) {
        String raw = System.getenv(key);
        if (raw == null || raw.isBlank()) {
            return def;
        }
        try {
            int v = Integer.parseInt(raw.trim());
            return Math.max(min, Math.min(max, v));
        } catch (NumberFormatException e) {
            return def;
        }
    }

    /**
     * Pure FORWARDER relays opaque CHAT payloads A→B under concurrent senders.
     * Forwarder never needs to parse application JSON — only IBP1 framing.
     */
    @Test
    @Timeout(value = 90, unit = TimeUnit.SECONDS)
    void concurrentOpaqueBlastThroughForwarder() throws Exception {
        int n = stressMessages();
        int threads = Math.min(stressThreads(), n);

        Forwarder fw = startForwarder("fw");
        ClientEndpoint alice = startRemoteClient("alice", fw);
        ClientEndpoint bob = startRemoteClient("bob", fw);

        // Cross-route so deliver() can find both USE_REMOTE endpoints.
        assertTrue(alice.client.route(
                bob.identity.ed25519PubRaw(), LOOPBACK, fw.server.rudpPort(),
                IceBridgeConfig.Role.BOTH));
        assertTrue(bob.client.route(
                alice.identity.ed25519PubRaw(), LOOPBACK, fw.server.rudpPort(),
                IceBridgeConfig.Role.BOTH));

        ConcurrentHashMap<String, byte[]> received = new ConcurrentHashMap<>();
        CountDownLatch done = new CountDownLatch(n);
        bob.transport.addListener(new DistributedSearchTransport.PayloadListener() {
            @Override
            public void onPayload(byte[] source, byte[] payload, long receivedMs) {
                onPayload(source, payload, receivedMs, MeshProtocolId.SEARCH);
            }

            @Override
            public void onPayload(byte[] source, byte[] payload, long receivedMs, int protocolId) {
                if (protocolId != MeshProtocolId.CHAT) {
                    return;
                }
                String key = new String(payload, StandardCharsets.UTF_8);
                received.put(key, payload);
                done.countDown();
            }
        });

        AtomicInteger sendOk = new AtomicInteger();
        AtomicInteger sendFail = new AtomicInteger();
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        resources.add(() -> {
            pool.shutdownNow();
            pool.awaitTermination(5, TimeUnit.SECONDS);
        });

        long t0 = System.nanoTime();
        CountDownLatch started = new CountDownLatch(n);
        for (int i = 0; i < n; i++) {
            final int idx = i;
            pool.execute(() -> {
                try {
                    byte[] body = ("opaque-chat-" + idx).getBytes(StandardCharsets.UTF_8);
                    boolean ok = alice.client.send(
                            bob.identity.ed25519PubRaw(), MeshProtocolId.CHAT, body);
                    if (ok) {
                        sendOk.incrementAndGet();
                    } else {
                        sendFail.incrementAndGet();
                    }
                } finally {
                    started.countDown();
                }
            });
        }
        assertTrue(started.await(30, TimeUnit.SECONDS), "all send tasks should start/finish");
        assertEquals(0, sendFail.get(), "sends should succeed; failed=" + sendFail.get());
        assertEquals(n, sendOk.get());

        assertTrue(done.await(60, TimeUnit.SECONDS),
                "bob should receive all messages; got=" + received.size() + "/" + n
                        + " elapsedMs=" + msSince(t0));

        for (int i = 0; i < n; i++) {
            String key = "opaque-chat-" + i;
            assertTrue(received.containsKey(key), "missing " + key);
        }

        long elapsedMs = msSince(t0);
        double rate = n * 1000.0 / Math.max(1, elapsedMs);
        System.out.printf(
                "IceBridgeStress concurrentOpaqueBlast: n=%d threads=%d elapsedMs=%d rate=%.1f msg/s%n",
                n, threads, elapsedMs, rate);
        assertTrue(rate > 5.0, "throughput floor (lab): expected >5 msg/s, got " + rate);
    }

    /**
     * Many concurrent USE_REMOTE clients register + lookup on one forwarder.
     * Uses only loopback + ephemeral ports.
     */
    @Test
    @Timeout(value = 90, unit = TimeUnit.SECONDS)
    void manyRemoteClientsRegisterAndLookup() throws Exception {
        int clients = stressClients();
        Forwarder fw = startForwarder("fw-multi");

        java.util.concurrent.ConcurrentLinkedQueue<ClientEndpoint> endpoints =
                new java.util.concurrent.ConcurrentLinkedQueue<>();
        ExecutorService pool = Executors.newFixedThreadPool(Math.min(clients, 16));
        resources.add(() -> {
            pool.shutdownNow();
            pool.awaitTermination(5, TimeUnit.SECONDS);
        });

        CountDownLatch registered = new CountDownLatch(clients);
        AtomicInteger regFail = new AtomicInteger();
        for (int i = 0; i < clients; i++) {
            final int idx = i;
            pool.execute(() -> {
                try {
                    ClientEndpoint c = startRemoteClient("c" + idx, fw);
                    endpoints.add(c);
                    // startRemoteClient already registers; re-register is a refresh under load.
                    boolean ok = c.client.register(
                            c.identity, LOOPBACK, fw.server.rudpPort(),
                            IceBridgeConfig.Role.BOTH);
                    if (!ok) {
                        regFail.incrementAndGet();
                    }
                } catch (Throwable t) {
                    regFail.incrementAndGet();
                } finally {
                    registered.countDown();
                }
            });
        }
        assertTrue(registered.await(60, TimeUnit.SECONDS));
        assertEquals(0, regFail.get(), "all clients should register");

        List<ClientEndpoint> endpointList = new ArrayList<>(endpoints);
        assertEquals(clients, endpointList.size());

        // Give PeerRegistry a moment; then lookup should see multiple peers.
        Thread.sleep(200);
        List<PeerInfo> peers = fw.client.lookup(Math.max(50, clients + 5));
        assertTrue(peers.size() >= clients,
                "registry should hold all clients; got=" + peers.size()
                        + " expected>=" + clients);

        // Concurrent lookups under load.
        int lookups = clients * 10;
        CountDownLatch lookupDone = new CountDownLatch(lookups);
        AtomicInteger lookupFail = new AtomicInteger();
        long t0 = System.nanoTime();
        for (int i = 0; i < lookups; i++) {
            ClientEndpoint c = endpointList.get(i % endpointList.size());
            pool.execute(() -> {
                try {
                    List<PeerInfo> got = c.client.lookup(20);
                    if (got == null || got.isEmpty()) {
                        lookupFail.incrementAndGet();
                    }
                } catch (Throwable t) {
                    lookupFail.incrementAndGet();
                } finally {
                    lookupDone.countDown();
                }
            });
        }
        assertTrue(lookupDone.await(60, TimeUnit.SECONDS));
        assertEquals(0, lookupFail.get(), "lookups should succeed");
        System.out.printf(
                "IceBridgeStress manyRemoteClients: clients=%d lookups=%d elapsedMs=%d%n",
                clients, lookups, msSince(t0));
    }

    /**
     * Large payloads exercise fragmentation on direct rUDP between two BOTH nodes.
     */
    @Test
    @Timeout(value = 60, unit = TimeUnit.SECONDS)
    void largePayloadFragmentationBlast() throws Exception {
        int rounds = Math.min(40, stressMessages() / 5);
        // ~12 KiB each — well into multi-fragment territory.
        int payloadBytes = 12 * 1024;

        BothNode a = startBoth("largeA");
        BothNode b = startBoth("largeB");

        assertTrue(a.client.route(
                b.identity.ed25519PubRaw(), LOOPBACK, b.server.rudpPort(),
                IceBridgeConfig.Role.BOTH));
        assertTrue(b.client.route(
                a.identity.ed25519PubRaw(), LOOPBACK, a.server.rudpPort(),
                IceBridgeConfig.Role.BOTH));

        CountDownLatch done = new CountDownLatch(rounds);
        AtomicInteger okBytes = new AtomicInteger();
        b.transport.addListener(new DistributedSearchTransport.PayloadListener() {
            @Override
            public void onPayload(byte[] source, byte[] payload, long receivedMs) {
                onPayload(source, payload, receivedMs, MeshProtocolId.SEARCH);
            }

            @Override
            public void onPayload(byte[] source, byte[] payload, long receivedMs, int protocolId) {
                if (protocolId != MeshProtocolId.FILESYNC) {
                    return;
                }
                if (payload != null && payload.length == payloadBytes
                        && payload[0] == (byte) 0xAB
                        && payload[payload.length - 1] == (byte) 0xCD) {
                    okBytes.incrementAndGet();
                }
                done.countDown();
            }
        });

        long t0 = System.nanoTime();
        for (int i = 0; i < rounds; i++) {
            byte[] body = new byte[payloadBytes];
            Arrays.fill(body, (byte) 0x11);
            body[0] = (byte) 0xAB;
            body[body.length - 1] = (byte) 0xCD;
            body[4] = (byte) (i & 0xFF);
            assertTrue(a.client.send(
                    b.identity.ed25519PubRaw(), MeshProtocolId.FILESYNC, body),
                    "send round " + i);
        }

        assertTrue(done.await(45, TimeUnit.SECONDS),
                "all large payloads should arrive; ok=" + okBytes.get() + "/" + rounds);
        assertEquals(rounds, okBytes.get(), "all payloads must match sentinel bytes");
        System.out.printf(
                "IceBridgeStress largePayload: rounds=%d size=%d elapsedMs=%d%n",
                rounds, payloadBytes, msSince(t0));
    }

    /**
     * Multi-hop: searcher on R1, seeder on R2, opaque METADATA blast across
     * two pure forwarders (protocol-agnostic path, not SEARCH schema).
     */
    /**
     * Multi-hop RELAY path is rate-limited (~20 QPS per key on intermediate
     * hops). Pace sends so stress measures sustained mesh delivery, not
     * token-bucket drops.
     */
    @Test
    @Timeout(value = 90, unit = TimeUnit.SECONDS)
    void multiHopOpaqueMetadataUnderLoad() throws Exception {
        // Stay under RELAY_MAX_QPS (~20) with headroom; still enough for load.
        int n = Math.min(40, Math.max(20, stressMessages() / 5));

        Forwarder r1 = startForwarder("r1");
        Forwarder r2 = startForwarder("r2");
        int linked = RelayMesh.linkFully(RelayMesh.MeshNode.of(
                new RelayMesh.MeshNode("R1", r1.client, r1.server.identity().ed25519PubRaw(),
                        LOOPBACK, r1.server.rudpPort()),
                new RelayMesh.MeshNode("R2", r2.client, r2.server.identity().ed25519PubRaw(),
                        LOOPBACK, r2.server.rudpPort())));
        assertTrue(linked >= 2, "relays should route each other");

        // Warm HELLO/ACK between relays so RELAY can hop.
        for (int w = 0; w < 3; w++) {
            r1.client.send(r2.server.identity().ed25519PubRaw(),
                    MeshProtocolId.TELEMETRY, new byte[]{0x01});
            r2.client.send(r1.server.identity().ed25519PubRaw(),
                    MeshProtocolId.TELEMETRY, new byte[]{0x01});
            Thread.sleep(80);
        }

        ClientEndpoint searcher = startRemoteClient("searcher", r1);
        ClientEndpoint seeder = startRemoteClient("seeder", r2);

        // Critical multi-hop topology:
        //  - seeder is ONLY registered on R2 (local /poll client)
        //  - R1 does NOT know seeder's host — only knows R2 as FORWARDER
        //  - deliver(seederPub) on R1 → RELAY fanout to R2 → local poll demux
        assertTrue(r1.client.lookup(100).stream().noneMatch(p ->
                        pubEquals(p.pub, seeder.identity.ed25519PubRaw())),
                "R1 must not have a direct route to seeder");

        CountDownLatch done = new CountDownLatch(n);
        ConcurrentHashMap<Integer, Boolean> got = new ConcurrentHashMap<>();
        seeder.transport.addListener(new DistributedSearchTransport.PayloadListener() {
            @Override
            public void onPayload(byte[] source, byte[] payload, long receivedMs) {
                onPayload(source, payload, receivedMs, MeshProtocolId.SEARCH);
            }

            @Override
            public void onPayload(byte[] source, byte[] payload, long receivedMs, int protocolId) {
                if (protocolId != MeshProtocolId.METADATA) {
                    return;
                }
                if (payload != null && payload.length >= 4) {
                    int id = ((payload[0] & 0xFF) << 24)
                            | ((payload[1] & 0xFF) << 16)
                            | ((payload[2] & 0xFF) << 8)
                            | (payload[3] & 0xFF);
                    if (got.putIfAbsent(id, Boolean.TRUE) == null) {
                        done.countDown();
                    }
                }
            }
        });

        long t0 = System.nanoTime();
        int sendOk = 0;
        // ~12 msg/s — below intermediate RELAY rate limit so packets are not dropped.
        final long paceNanos = 80_000_000L; // 80ms
        for (int i = 0; i < n; i++) {
            long tick = System.nanoTime();
            byte[] body = new byte[64];
            body[0] = (byte) ((i >>> 24) & 0xFF);
            body[1] = (byte) ((i >>> 16) & 0xFF);
            body[2] = (byte) ((i >>> 8) & 0xFF);
            body[3] = (byte) (i & 0xFF);
            Arrays.fill(body, 4, body.length, (byte) 0x5A);
            if (searcher.client.send(
                    seeder.identity.ed25519PubRaw(), MeshProtocolId.METADATA, body)) {
                sendOk++;
            }
            long spent = System.nanoTime() - tick;
            if (spent < paceNanos) {
                Thread.sleep((paceNanos - spent) / 1_000_000L,
                        (int) ((paceNanos - spent) % 1_000_000L));
            }
        }
        assertEquals(n, sendOk, "control /send should accept all");

        assertTrue(done.await(45, TimeUnit.SECONDS),
                "seeder should get multi-hop METADATA; got=" + got.size() + "/" + n);
        assertEquals(n, got.size());
        System.out.printf(
                "IceBridgeStress multiHopOpaque: n=%d elapsedMs=%d (paced for RELAY QPS)%n",
                n, msSince(t0));
    }

    private static boolean pubEquals(String b64url, byte[] raw) {
        if (b64url == null || raw == null) {
            return false;
        }
        try {
            byte[] decoded = java.util.Base64.getUrlDecoder().decode(b64url);
            return Arrays.equals(decoded, raw);
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    /**
     * Contract: MeshEnvelope is the only demux; application bytes stay opaque.
     * This is a fast unit-style guardrail (not a crypto E2E claim).
     */
    @Test
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    void meshEnvelopeDoesNotRequireSearchSchema() {
        byte[] secret = "not-a-search-request-just-bytes".getBytes(StandardCharsets.UTF_8);
        for (int proto : new int[]{
                MeshProtocolId.CHAT,
                MeshProtocolId.METADATA,
                MeshProtocolId.AI,
                MeshProtocolId.FILESYNC,
                99 // unknown id still framed opaquely
        }) {
            byte[] wire = MeshEnvelope.encodeForWire(proto, secret);
            MeshEnvelope env = MeshEnvelope.unwrap(wire);
            assertEquals(proto, env.protocolId(), "protocolId must round-trip");
            assertTrue(Arrays.equals(secret, env.payload()),
                    "payload must round-trip for protocolId=" + proto);
            // Framing is binary IBP1 — not FrostWire search JSON.
            assertTrue(wire[0] == 'I' && wire[1] == 'B' && wire[2] == 'P' && wire[3] == '1',
                    "wire must start with IBP1 magic");
        }
    }

    // ---- fixtures ----

    private Forwarder startForwarder(String label) throws Exception {
        Path tmp = Files.createTempDirectory("icebridge-stress-fw-" + label + "-");
        Path idFile = tmp.resolve("identity.dat");
        IdentityKeys.save(IdentityKeys.generate(0), idFile.toFile());

        IceBridgeConfig config = IceBridgeConfig.newBuilder()
                .host(LOOPBACK)
                .rudpPort(freePort())
                .relayPort(0)
                .controlHttpPort(freePort())
                .role(IceBridgeConfig.Role.FORWARDER)
                .maxPeers(500)
                .peerTtlSec(180)
                .maxQpsPerKey(10_000.0)
                .identityFile(idFile.toFile())
                .build();

        IceBridgeServer server = new IceBridgeServer(config);
        server.start();
        resources.add(server);

        IceBridgeClient client = new IceBridgeClient(server.controlPort());
        client.setAuthToken(server.authToken());
        waitHealthy(client, label);
        return new Forwarder(server, client);
    }

    private BothNode startBoth(String label) throws Exception {
        Path tmp = Files.createTempDirectory("icebridge-stress-both-" + label + "-");
        Path idFile = tmp.resolve("identity.dat");
        IdentityKeys.save(IdentityKeys.generate(0), idFile.toFile());

        IceBridgeConfig config = IceBridgeConfig.newBuilder()
                .host(LOOPBACK)
                .rudpPort(freePort())
                .relayPort(0)
                .controlHttpPort(freePort())
                .role(IceBridgeConfig.Role.BOTH)
                .maxPeers(200)
                .peerTtlSec(180)
                .maxQpsPerKey(10_000.0)
                .identityFile(idFile.toFile())
                .build();

        IceBridgeServer server = new IceBridgeServer(config);
        server.start();
        resources.add(server);

        IceBridgeClient client = new IceBridgeClient(server.controlPort());
        client.setAuthToken(server.authToken());
        waitHealthy(client, label);

        IceBridgeSearchTransport transport = new IceBridgeSearchTransport(client);
        transport.start();
        resources.add(transport);

        return new BothNode(server, client, transport, server.identity());
    }

    private ClientEndpoint startRemoteClient(String label, Forwarder fw) throws Exception {
        IdentityKeys identity = IdentityKeys.generate(0);
        IceBridgeClient client = new IceBridgeClient(fw.server.controlPort());
        client.setAuthToken(fw.server.authToken());
        client.setOwnPub(identity.ed25519PubRaw());
        waitHealthy(client, label);

        assertTrue(client.register(
                identity, LOOPBACK, fw.server.rudpPort(), IceBridgeConfig.Role.BOTH),
                label + " register");

        IceBridgeSearchTransport transport = new IceBridgeSearchTransport(client);
        transport.start();
        resources.add(transport);
        return new ClientEndpoint(identity, client, transport);
    }

    private static void waitHealthy(IceBridgeClient client, String label) throws Exception {
        for (int i = 0; i < 100; i++) {
            if (client.health()) {
                return;
            }
            Thread.sleep(50);
        }
        fail("IceBridge " + label + " did not become healthy");
    }

    private static int freePort() throws Exception {
        try (ServerSocket s = new ServerSocket(0)) {
            return s.getLocalPort();
        }
    }

    private static long msSince(long t0Nanos) {
        return (System.nanoTime() - t0Nanos) / 1_000_000L;
    }

    private static final class Forwarder {
        final IceBridgeServer server;
        final IceBridgeClient client;

        Forwarder(IceBridgeServer server, IceBridgeClient client) {
            this.server = server;
            this.client = client;
        }
    }

    private static final class BothNode {
        final IceBridgeServer server;
        final IceBridgeClient client;
        final IceBridgeSearchTransport transport;
        final IdentityKeys identity;

        BothNode(IceBridgeServer server, IceBridgeClient client,
                 IceBridgeSearchTransport transport, IdentityKeys identity) {
            this.server = server;
            this.client = client;
            this.transport = transport;
            this.identity = identity;
        }
    }

    private static final class ClientEndpoint {
        final IdentityKeys identity;
        final IceBridgeClient client;
        final IceBridgeSearchTransport transport;

        ClientEndpoint(IdentityKeys identity, IceBridgeClient client,
                       IceBridgeSearchTransport transport) {
            this.identity = identity;
            this.client = client;
            this.transport = transport;
        }
    }
}
