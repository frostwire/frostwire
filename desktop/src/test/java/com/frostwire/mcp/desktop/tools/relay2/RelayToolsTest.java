/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.mcp.desktop.tools.relay2;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.frostwire.mcp.desktop.tools.DiscoveredPeersTool;
import com.frostwire.mcp.desktop.tools.RelayStatusTool;
import com.frostwire.mcp.desktop.tools.obs.RelayMetricsTool;
import com.frostwire.search.relay.DistributedSearchTransport;
import com.frostwire.search.relay.IdentityKeys;
import com.frostwire.search.relay.IndexDigest;
import com.frostwire.search.relay.KarmaChainSource;
import com.frostwire.search.relay.LocalIndex;
import com.frostwire.search.relay.LocalSharedTorrent;
import com.frostwire.search.relay.PeerDirectory;
import com.frostwire.search.relay.PeerKarmaCache;
import com.frostwire.search.relay.PowTestIdentities;
import com.frostwire.search.relay.RelaySearchWiring;
import com.frostwire.search.relay.RemoteKarmaChainFetcher;
import com.frostwire.util.Hex;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.limegroup.gnutella.gui.search.DesktopSearchEngines;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * The IceBridge relay tools read and change the distributed engine's peer directory and local
 * index. A directory, an index, an identity and a transport that delivers nothing are installed on
 * the distributed engine for each test, and whatever it had is put back afterwards.
 */
class RelayToolsTest {

  private static final byte[] ALICE = pub(1);
  private static final byte[] BOB = pub(2);

  private RelaySearchWiring distributed;
  private PeerDirectory previousDirectory;
  private LocalIndex previousIndex;
  private IdentityKeys previousIdentity;
  private DistributedSearchTransport previousTransport;

  private PeerDirectory directory;
  private final InMemoryIndex index = new InMemoryIndex();
  private IdentityKeys identity;

  @BeforeEach
  void wireTheDistributedEngine() throws Exception {
    distributed = DesktopSearchEngines.distributed();
    previousDirectory = DesktopSearchEngines.distributed().peerDirectory();
    previousIndex = DesktopSearchEngines.distributed().localIndex();
    previousIdentity = DesktopSearchEngines.distributed().identity();
    previousTransport = DesktopSearchEngines.distributed().searchTransport();

    KarmaChainSource noChains = peerPub -> null;
    directory = new PeerDirectory(new PeerKarmaCache(new RemoteKarmaChainFetcher(noChains)));
    directory.upsertVerified(ALICE, "alice.example", 6881, 6888);
    directory.upsertVerified(BOB, "bob.example", 6882, 6889);
    directory.setIndexDigest(ALICE, IndexDigest.build(List.of("ubuntu linux")).toBytes());
    identity = PowTestIdentities.next();
    distributed
        .peerDirectory(directory)
        .localIndex(index)
        .identity(identity)
        .searchTransport(new SilentTransport());
  }

  @AfterEach
  void restoreTheDistributedEngine() {
    distributed
        .peerDirectory(previousDirectory)
        .localIndex(previousIndex)
        .identity(previousIdentity)
        .searchTransport(previousTransport);
  }

  @Test
  void peersAreListedWithTheirDetails() {
    JsonObject out = new PeersListTool().execute(new JsonObject());

    assertEquals(2, out.get("total").getAsInt());
    assertEquals(2, out.get("live").getAsInt());
    assertEquals(1, out.get("with_digest").getAsInt());
    JsonObject alice = peer(out, Hex.encode(ALICE));
    assertEquals("alice.example", alice.get("host").getAsString());
    assertEquals(6881, alice.get("utp_port").getAsInt());
    assertEquals(6888, alice.get("rudp_port").getAsInt());
    assertTrue(alice.get("verified").getAsBoolean());
    assertFalse(alice.get("spam").getAsBoolean());
    assertTrue(alice.get("has_digest").getAsBoolean());
    assertFalse(peer(out, Hex.encode(BOB)).get("has_digest").getAsBoolean());

    JsonObject one = new JsonObject();
    one.addProperty("limit", 0);
    assertEquals(
        1,
        new PeersListTool().execute(one).getAsJsonArray("peers").size(),
        "the limit is at least one");

    JsonObject onlyDigest = new JsonObject();
    onlyDigest.addProperty("with_digest_only", true);
    JsonArray withDigest;
    onlyDigest.addProperty("limit", 9999);
    withDigest = new PeersListTool().execute(onlyDigest).getAsJsonArray("peers");
    assertEquals(1, withDigest.size());
    assertEquals(Hex.encode(ALICE), withDigest.get(0).getAsJsonObject().get("pub").getAsString());
  }

  @Test
  void peersCanBeBlockedAndRemoved() {
    JsonObject blocked = new PeerBlockTool().execute(pubArg(Hex.encode(BOB)));
    assertTrue(blocked.get("blocked").getAsBoolean());
    assertEquals(Hex.encode(BOB), blocked.get("pub").getAsString());
    assertTrue(directory.get(BOB).orElseThrow().isSpam());

    JsonObject removed = new PeerRemoveTool().execute(pubArg(Hex.encode(ALICE)));
    assertTrue(removed.get("removed").getAsBoolean());
    assertTrue(directory.get(ALICE).isEmpty());
    assertFalse(
        new PeerRemoveTool().execute(pubArg(Hex.encode(ALICE))).get("removed").getAsBoolean(),
        "already gone");
  }

  @Test
  void peerToolsRejectBadKeys() {
    for (var tool : List.of(new PeerBlockTool(), new PeerRemoveTool())) {
      assertEquals(
          "Missing required parameter: pub",
          tool.execute(new JsonObject()).get("error").getAsString());
      assertEquals(
          "pub must be a 64-character hex string",
          tool.execute(pubArg("abcd")).get("error").getAsString());
    }
    assertThrows(IllegalArgumentException.class, () -> RelayToolSupport.parsePub32(null));
  }

  @Test
  void catalogAcceptsHexOrBase64KeysAndIsEmptyWhenThePeerDoesNotAnswer() {
    JsonObject hex = new JsonObject();
    hex.addProperty("peer", Hex.encode(ALICE).toUpperCase(Locale.ROOT));
    hex.addProperty("timeout_ms", 1);
    JsonObject out = new PeerCatalogTool().execute(hex);
    assertEquals(Hex.encode(ALICE), out.get("peer").getAsString());
    assertEquals(0, out.get("count").getAsInt());
    assertEquals(0, out.getAsJsonArray("catalog").size());

    JsonObject base64 = new JsonObject();
    base64.addProperty("peer", Base64.getUrlEncoder().withoutPadding().encodeToString(BOB));
    base64.addProperty("timeout_ms", 100);
    assertEquals(Hex.encode(BOB), new PeerCatalogTool().execute(base64).get("peer").getAsString());

    JsonObject bad = new JsonObject();
    bad.addProperty("peer", "not*base64");
    assertEquals(
        "peer must be 64-char hex or base64url of 32 bytes",
        new PeerCatalogTool().execute(bad).get("error").getAsString());
    bad.addProperty("peer", Base64.getUrlEncoder().encodeToString(new byte[5]));
    assertEquals(
        "peer must decode to 32 bytes",
        new PeerCatalogTool().execute(bad).get("error").getAsString());
    assertEquals(
        "Missing required parameter: peer",
        new PeerCatalogTool().execute(new JsonObject()).get("error").getAsString());
  }

  @Test
  void theLocalIndexIsListedAndSearched() {
    index.torrents.add(torrent("Ubuntu 24.04 ISO", 1));
    index.torrents.add(torrent("Debian netinst", 2));

    JsonObject list = new LocalIndexListTool().execute(new JsonObject());
    assertEquals(2, list.get("count").getAsInt());
    JsonObject first = list.getAsJsonArray("torrents").get(0).getAsJsonObject();
    assertEquals("Ubuntu 24.04 ISO", first.get("name").getAsString());
    assertEquals(Hex.encode(infoHash(1)), first.get("ih").getAsString());
    assertEquals(1_000, first.get("size").getAsLong());
    assertEquals(3, first.get("files").getAsInt());
    assertEquals(Hex.encode(ALICE), first.get("publisher_pub").getAsString());
    assertEquals("disc.iso", first.get("matched_file").getAsString());

    JsonObject one = new JsonObject();
    one.addProperty("limit", 1);
    assertEquals(1, new LocalIndexListTool().execute(one).get("count").getAsInt());

    JsonObject query = new JsonObject();
    query.addProperty("q", "debian");
    JsonObject found = new LocalIndexSearchTool().execute(query);
    assertEquals("debian", found.get("query").getAsString());
    assertEquals(1, found.get("count").getAsInt());
    assertEquals(
        "Debian netinst",
        found.getAsJsonArray("results").get(0).getAsJsonObject().get("name").getAsString());

    query.addProperty("q", "  ");
    assertEquals(
        "Missing required parameter: q",
        new LocalIndexSearchTool().execute(query).get("error").getAsString());

    index.nullSearch = true;
    query.addProperty("q", "anything");
    assertEquals(0, new LocalIndexSearchTool().execute(query).get("count").getAsInt());
  }

  @Test
  void statusToolsReportTheWiredRelay() {
    index.torrents.add(torrent("Ubuntu", 1));

    JsonObject digest = new DigestStatusTool().execute(new JsonObject());
    assertEquals(2, digest.get("peer_directory_size").getAsInt());
    assertEquals(2, digest.get("live").getAsInt());
    assertEquals(1, digest.get("peers_with_digest").getAsInt());
    assertTrue(digest.get("has_local_index").getAsBoolean());
    assertEquals(1, digest.get("local_index_size").getAsInt());

    JsonObject status = new RelayStatusTool().execute(new JsonObject());
    assertTrue(status.get("ready").getAsBoolean());
    assertEquals(2, status.get("peer_count").getAsInt());
    assertTrue(status.get("has_identity").getAsBoolean());
    assertEquals(Hex.encode(identity.ed25519PubRaw()), status.get("pubkey_hex").getAsString());
    assertTrue(status.get("has_local_index").getAsBoolean());

    JsonObject metrics = new RelayMetricsTool().execute(new JsonObject());
    assertTrue(metrics.get("distributed_ready").getAsBoolean());
    assertEquals(2, metrics.get("peer_directory_size").getAsInt());
    assertTrue(metrics.get("has_identity").getAsBoolean());
    assertEquals(Hex.encode(identity.ed25519PubRaw()), metrics.get("pubkey_hex").getAsString());
    assertTrue(metrics.get("has_local_index").getAsBoolean());

    JsonObject discovered = new DiscoveredPeersTool().execute(new JsonObject());
    assertTrue(discovered.get("distributed_ready").getAsBoolean());
    assertEquals(2, discovered.get("total_peers").getAsInt());
    JsonArray top = discovered.getAsJsonArray("top_verified_peers");
    assertEquals(2, top.size());
    assertTrue(top.get(0).getAsJsonObject().get("verified").getAsBoolean());
  }

  @Test
  void toolsReportARelayThatIsNotWired() {
    distributed.peerDirectory(null).localIndex(null).identity(null).searchTransport(null);

    String noDirectory = "PeerDirectory is not available (relay stack not wired)";
    assertEquals(noDirectory, new PeersListTool().execute(null).get("error").getAsString());
    assertEquals(
        noDirectory,
        new PeerBlockTool().execute(pubArg(Hex.encode(ALICE))).get("error").getAsString());
    assertEquals(
        noDirectory,
        new PeerRemoveTool().execute(pubArg(Hex.encode(ALICE))).get("error").getAsString());
    String noIndex = "LocalIndex is not available (relay stack not wired)";
    assertEquals(
        noIndex, new LocalIndexListTool().execute(new JsonObject()).get("error").getAsString());
    JsonObject query = new JsonObject();
    query.addProperty("q", "x");
    assertEquals(noIndex, new LocalIndexSearchTool().execute(query).get("error").getAsString());
    JsonObject peer = new JsonObject();
    peer.addProperty("peer", Hex.encode(ALICE));
    assertEquals(
        "distributed relay not wired",
        new PeerCatalogTool().execute(peer).get("error").getAsString());

    JsonObject digest = new DigestStatusTool().execute(new JsonObject());
    assertEquals(0, digest.get("peer_directory_size").getAsInt());
    assertFalse(digest.get("has_local_index").getAsBoolean());
    assertFalse(new RelayStatusTool().execute(new JsonObject()).get("ready").getAsBoolean());
    JsonObject discovered = new DiscoveredPeersTool().execute(new JsonObject());
    assertFalse(discovered.get("distributed_ready").getAsBoolean());
    assertFalse(discovered.has("top_verified_peers"));
  }

  @Test
  void hostCacheAddValidatesTheHostAndPortBeforeTouchingTheCache() {
    HostCacheAddTool tool = new HostCacheAddTool();
    assertEquals("Missing required parameter: host", tool.execute(null).get("error").getAsString());
    assertEquals(
        "Missing required parameter: host",
        tool.execute(host(" ", null)).get("error").getAsString());
    assertEquals(
        "Missing required parameter: port",
        tool.execute(host("relay.example", null)).get("error").getAsString());
    assertEquals(
        "port must be an integer",
        tool.execute(host("relay.example", "http")).get("error").getAsString());
    assertEquals(
        "port must be between 1 and 65535",
        tool.execute(host("relay.example", 0)).get("error").getAsString());
    assertEquals(
        "port must be between 1 and 65535",
        tool.execute(host("relay.example", 65536)).get("error").getAsString());
  }

  private static JsonObject host(String host, Object port) {
    JsonObject args = new JsonObject();
    args.addProperty("host", host);
    if (port instanceof Number number) {
      args.addProperty("port", number);
    } else if (port != null) {
      args.addProperty("port", port.toString());
    }
    return args;
  }

  @Test
  void argumentHelpersFallBackToDefaults() {
    JsonObject args = new JsonObject();
    args.addProperty("n", "many");
    args.add("nil", null);
    args.addProperty("flag", "yes");
    args.add("obj", new JsonObject());
    assertEquals(7, RelayToolSupport.clampInt(args, "n", 7, 1, 10));
    assertEquals(7, RelayToolSupport.clampInt(args, "nil", 7, 1, 10));
    assertEquals(7, RelayToolSupport.clampInt(null, "n", 7, 1, 10));
    assertFalse(RelayToolSupport.boolArg(args, "flag", false), "not a boolean");
    assertTrue(RelayToolSupport.boolArg(args, "obj", true));
    assertTrue(RelayToolSupport.boolArg(null, "flag", true));
    assertNull(RelayToolSupport.stringArg(args, "obj"));
    assertNull(RelayToolSupport.stringArg(args, "nil"));
    assertArrayEquals(ALICE, RelayToolSupport.parsePub(Hex.encode(ALICE)));
  }

  private static JsonObject peer(JsonObject listing, String pub) {
    for (var element : listing.getAsJsonArray("peers")) {
      if (element.getAsJsonObject().get("pub").getAsString().equals(pub)) {
        return element.getAsJsonObject();
      }
    }
    throw new AssertionError("no peer " + pub + " in " + listing);
  }

  private static JsonObject pubArg(String pub) {
    JsonObject args = new JsonObject();
    args.addProperty("pub", pub);
    return args;
  }

  /** A distinct peer key per {@code seed} that may be verified (it carries the identity PoW). */
  private static byte[] pub(int seed) {
    return PowTestIdentities.get(seed).ed25519PubRaw();
  }

  private static byte[] infoHash(int seed) {
    byte[] hash = new byte[20];
    Arrays.fill(hash, (byte) (seed + 100));
    return hash;
  }

  private static LocalSharedTorrent torrent(String name, int seed) {
    return new LocalSharedTorrent.Builder()
        .infoHash(infoHash(seed))
        .name(name)
        .sizeBytes(1_000L * seed)
        .fileCount(3)
        .publisherNodeId(new byte[20])
        .publisherEd25519Pub(ALICE)
        .addedAt(1)
        .lastSeenAt(2)
        .matchedFile("disc.iso")
        .build();
  }

  /** A local index kept in a list; search matches names ignoring case. */
  private static final class InMemoryIndex implements LocalIndex {
    final List<LocalSharedTorrent> torrents = new ArrayList<>();
    boolean nullSearch;

    @Override
    public void upsert(LocalSharedTorrent torrent) {
      torrents.add(torrent);
    }

    @Override
    public void delete(String infoHashHex) {}

    @Override
    public Optional<LocalSharedTorrent> get(String infoHashHex) {
      return Optional.empty();
    }

    @Override
    public List<LocalSharedTorrent> search(String query, int limit) {
      if (nullSearch) {
        return null;
      }
      return torrents.stream()
          .filter(t -> t.name().toLowerCase(Locale.ROOT).contains(query.toLowerCase(Locale.ROOT)))
          .limit(limit)
          .toList();
    }

    @Override
    public void markPublished(String infoHashHex, long timestamp) {}

    @Override
    public List<String> needsRepublish(long nowSec, long thresholdSec) {
      return List.of();
    }

    @Override
    public void updateLastSeen(String infoHashHex, long ts) {}

    @Override
    public int size() {
      return torrents.size();
    }

    @Override
    public List<LocalSharedTorrent> listAll() {
      return torrents;
    }
  }

  /** A transport that reaches no one, so a catalog request gets no answer. */
  private static final class SilentTransport implements DistributedSearchTransport {
    @Override
    public boolean send(byte[] targetPub, int protocolId, byte[] payload) {
      return false;
    }

    @Override
    public void addListener(PayloadListener listener) {}

    @Override
    public void removeListener(PayloadListener listener) {}
  }
}
