/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.search.relay.icebridge.client;

import com.code_intelligence.jazzer.api.FuzzedDataProvider;
import com.code_intelligence.jazzer.junit.FuzzTest;
import com.frostwire.search.relay.DistributedSearchTransport;
import com.frostwire.search.relay.IdentityKeys;
import com.frostwire.search.relay.LocalIndexTable;
import com.frostwire.search.relay.LocalSharedTorrent;
import com.frostwire.search.relay.RelaySearchService;
import com.frostwire.search.relay.RemoteSearchRequest;
import com.frostwire.search.relay.RemoteSearchResponse;
import com.frostwire.search.relay.SearchPayloadCodec;
import com.frostwire.search.relay.ShareVisibilityPolicy;
import com.frostwire.search.relay.icebridge.MeshProtocolId;
import com.frostwire.util.Hex;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.GeneralSecurityException;
import java.security.Signature;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

/**
 * Fuzzes a remote peer's search request through {@link IncomingSearchRequestHandler} into the
 * desktop local index: {@link RelaySearchService} turns the peer's keywords and limit into a {@link
 * LocalIndexTable#search} (SQLite FTS5 MATCH on torrent names, tags and file paths) and the handler
 * streams the signed rows back.
 *
 * <p>Who sends it: any IceBridge peer. The keywords (up to the 1 KiB the request builder allows)
 * and the limit (1 to {@link RemoteSearchRequest#MAX_LIMIT}) are the peer's; the harness signs the
 * request with a fixed requester key so the fuzzer gets past the signature check, and delivers it
 * as the transport would, encoded with {@link SearchPayloadCodec}. The index is a real SQLite
 * database in a temp directory, seeded once with a few torrents (ASCII, accented and CJK names,
 * tags, file paths), two of them not publicly shared.
 *
 * <p>The handler and the service document no exception: they log and drop a request they fail to
 * answer. Either log with an exception is a finding (the harness rethrows it), and so is any
 * response that breaks the request's scope: more rows than the requested limit, a row that is not
 * publicly shared or not in the index, the same torrent twice, a response to keywords with no
 * letter or digit (the sanitizer makes those match nothing), or an input running past 10 s.
 */
class IncomingSearchRequestHandlerFuzzTest {

  private static final IdentityKeys HANDLER = identity();
  private static final IdentityKeys REQUESTER = identity();
  private static final Seed[] SEEDS = {
    new Seed("Ubuntu Server 24.04 LTS amd64", "linux iso", "[]", true),
    new Seed(
        "Big Buck Bunny 1080p",
        "movie open blender",
        "[{\"path\":\"Big Buck Bunny/bbb_1080p.mp4\",\"size\":276134947}]",
        true),
    new Seed(
        "Free Music Archive - Jazz Collection",
        null,
        "[{\"path\":\"jazz/Take Five.mp3\",\"size\":5000000},"
            + "{\"path\":\"jazz/So What.flac\",\"size\":30000000}]",
        true),
    new Seed("東京 夜景 写真集", "写真 photo", "[{\"path\":\"東京/夜景_001.jpg\",\"size\":1200}]", true),
    new Seed("Café del Mar – Volumen Uno", "música chill", "[]", true),
    new Seed(
        "Private family videos", "private", "[{\"path\":\"family/2025.mkv\",\"size\":9}]", false),
    new Seed("Ubuntu private notes", "linux", "[]", false),
  };
  private static final Set<String> INDEXED = new HashSet<>();
  private static final Set<String> PUBLIC = new HashSet<>();
  private static final LocalIndexTable INDEX = seededIndex();
  private static final ShareVisibilityPolicy VISIBILITY =
      infoHashHex -> PUBLIC.contains(infoHashHex.toLowerCase(Locale.ROOT));
  private static final CapturedFailure FAILURE = new CapturedFailure();
  // Held so the configured loggers are not collected before the classes under test take them.
  private static final Logger HANDLER_LOG =
      Logger.getLogger(IncomingSearchRequestHandler.class.getSimpleName());
  private static final Logger SERVICE_LOG =
      Logger.getLogger(RelaySearchService.class.getSimpleName());

  static {
    // Both classes log the exception of a request they drop at INFO (debug) or WARNING; the fuzz
    // tasks set logging to SEVERE, so lower only these loggers and keep their records away from
    // the console.
    for (Logger log : new Logger[] {HANDLER_LOG, SERVICE_LOG}) {
      log.setLevel(Level.INFO);
      log.setUseParentHandlers(false);
      log.addHandler(FAILURE);
    }
  }

  @FuzzTest
  void onPayload(FuzzedDataProvider data) throws GeneralSecurityException {
    int limit = data.consumeInt(1, RemoteSearchRequest.MAX_LIMIT);
    String keywords = data.consumeRemainingAsString();
    if (keywords.getBytes(StandardCharsets.UTF_8).length > 1024) {
      // The request builder refuses longer keywords, so no peer can send them.
      return;
    }
    RemoteSearchRequest request = signedRequest(keywords, limit);

    RelaySearchService service = new RelaySearchService(INDEX, HANDLER, VISIBILITY);
    CapturingTransport transport = new CapturingTransport();
    IncomingSearchRequestHandler handler =
        new IncomingSearchRequestHandler(transport, service, null, HANDLER, INDEX);
    FAILURE.thrown = null;
    handler.onPayload(
        REQUESTER.ed25519PubRaw(),
        SearchPayloadCodec.encodeRequest(request),
        System.currentTimeMillis(),
        MeshProtocolId.SEARCH);
    rethrowLogged();

    List<RemoteSearchResponse.Row> rows = new ArrayList<>();
    for (byte[] payload : transport.sent) {
      RemoteSearchResponse response = SearchPayloadCodec.decodeResponse(payload);
      if (response == null) {
        throw new IllegalStateException("handler sent a response its own codec can't decode");
      }
      rows.addAll(response.rows());
    }
    if (rows.size() > limit) {
      throw new IllegalStateException(rows.size() + " rows for limit " + limit);
    }
    if (!rows.isEmpty() && !hasLetterOrDigit(keywords)) {
      throw new IllegalStateException(rows.size() + " rows for keywords with no letter or digit");
    }
    Set<String> seen = new HashSet<>();
    for (RemoteSearchResponse.Row row : rows) {
      String hash = Hex.encode(row.infoHash);
      if (!INDEXED.contains(hash)) {
        throw new IllegalStateException("row " + hash + " is not in the index");
      }
      if (!PUBLIC.contains(hash)) {
        throw new IllegalStateException("row " + hash + " is not publicly shared");
      }
      if (!seen.add(hash)) {
        throw new IllegalStateException("row " + hash + " sent twice");
      }
    }
  }

  private static boolean hasLetterOrDigit(String s) {
    return s.codePoints().anyMatch(Character::isLetterOrDigit);
  }

  private static RemoteSearchRequest signedRequest(String keywords, int limit)
      throws GeneralSecurityException {
    RemoteSearchRequest.Builder builder =
        RemoteSearchRequest.builder()
            .keywords(keywords)
            .limit(limit)
            .nonce(new byte[32])
            .ttl(1)
            .requesterPub(REQUESTER.ed25519PubRaw())
            .path(new byte[0][])
            .timestamp(System.currentTimeMillis() / 1000L)
            .signature(new byte[64]);
    Signature signer = IdentityKeys.softwareSignature("Ed25519");
    signer.initSign(REQUESTER.ed25519().getPrivate());
    signer.update(builder.build().canonicalBytes());
    return builder.signature(signer.sign()).build();
  }

  private static void rethrowLogged() {
    Throwable thrown = FAILURE.thrown;
    if (thrown instanceof RuntimeException e) {
      throw e;
    }
    if (thrown instanceof Error e) {
      throw e;
    }
    if (thrown != null) {
      throw new IllegalStateException("search path logged " + thrown, thrown);
    }
  }

  private static LocalIndexTable seededIndex() {
    try {
      File dir = Files.createTempDirectory("fw-fuzz-local-index").toFile();
      dir.deleteOnExit();
      LocalIndexTable index = LocalIndexTable.open(new File(dir, LocalIndexTable.DEFAULT_DB_NAME));
      long now = System.currentTimeMillis() / 1000L;
      for (int i = 0; i < SEEDS.length; i++) {
        Seed seed = SEEDS[i];
        byte[] infoHash = new byte[LocalSharedTorrent.INFO_HASH_V1_LENGTH];
        Arrays.fill(infoHash, (byte) (0x10 + i));
        byte[] publisher = new byte[LocalSharedTorrent.ED25519_PUB_LENGTH];
        Arrays.fill(publisher, (byte) 0x42);
        index.upsert(
            new LocalSharedTorrent.Builder()
                .infoHash(infoHash)
                .name(seed.name)
                .sizeBytes(1000L * (i + 1))
                .fileCount(1)
                .filesJson(seed.filesJson)
                .tags(seed.tags)
                .publisherNodeId(new byte[20])
                .publisherEd25519Pub(publisher)
                .addedAt(now)
                .lastSeenAt(now)
                .build());
        String hex = Hex.encode(infoHash);
        INDEXED.add(hex);
        if (seed.isPublic) {
          PUBLIC.add(hex);
        }
      }
      return index;
    } catch (IOException e) {
      throw new IllegalStateException(e);
    }
  }

  private static IdentityKeys identity() {
    try {
      // Not fromSeed: it derives the key through jlibtorrent's native Ed25519.
      return IdentityKeys.generate();
    } catch (GeneralSecurityException e) {
      throw new IllegalStateException(e);
    }
  }

  private record Seed(String name, String tags, String filesJson, boolean isPublic) {}

  /** Records what the handler sends; nothing leaves the JVM. */
  private static final class CapturingTransport implements DistributedSearchTransport {
    final List<byte[]> sent = new ArrayList<>();

    @Override
    public boolean send(byte[] targetPub, int protocolId, byte[] payload) {
      if (!Arrays.equals(targetPub, REQUESTER.ed25519PubRaw())) {
        throw new IllegalStateException("response sent to " + Hex.encode(targetPub));
      }
      sent.add(payload);
      return true;
    }

    @Override
    public void addListener(PayloadListener listener) {}

    @Override
    public void removeListener(PayloadListener listener) {}
  }

  /** Remembers the exception of the last record the handler or the service logged. */
  private static final class CapturedFailure extends Handler {
    volatile Throwable thrown;

    @Override
    public void publish(LogRecord record) {
      if (record.getThrown() != null) {
        thrown = record.getThrown();
      }
    }

    @Override
    public void flush() {}

    @Override
    public void close() {}
  }
}
