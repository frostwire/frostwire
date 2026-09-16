/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.search.relay;

import com.frostwire.search.relay.icebridge.MeshProtocolId;
import com.frostwire.util.Hex;
import com.frostwire.util.Logger;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.Signature;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Fetches a peer's full shared-torrent catalog over the IceBridge mesh.
 *
 * <p>Counterpart to the DHT-backed {@link RemoteIndexFetcher.DhtIndexSource}:
 * instead of resolving a BEP 46 mutable item, this client sends a signed
 * {@link RemoteCatalogBrowseRequest} directly to the target peer over
 * Protocol #1 ({@link MeshProtocolId#SEARCH}) and waits for the peer-signed
 * JSON manifest that
 * {@code com.frostwire.search.relay.icebridge.client.IncomingSearchRequestHandler}
 * builds.
 *
 * <p><b>Flow (fail-closed):</b>
 * <ol>
 *   <li>register a {@link DistributedSearchTransport.PayloadListener}
 *       <em>before</em> sending, so no response can be missed;</li>
 *   <li>build, sign, and encode a {@link RemoteCatalogBrowseRequest}
 *       ({@code targetPub == peerPub}, fresh 32-byte nonce, current epoch
 *       seconds);</li>
 *   <li>{@code createSend(peerPub, SEARCH, bytes, deadline)} and execute the
 *       send off-thread;</li>
 *   <li>block on a latch up to a single monotonic {@link System#nanoTime()}
 *       deadline while the listener decodes, bounds, and verifies the
 *       manifest;</li>
 *   <li>always remove the listener and cancel the send in {@code finally}.</li>
 * </ol>
 *
 * <p><b>Correlation:</b> the shipped manifest
 * ({@link RemoteIndexFetcher#buildManifestJson(int, String, long, List, byte[])})
 * carries no nonce field, so when an inbound manifest has no {@code nonce}
 * the first manifest that verifies against {@code peerPub} within the timeout
 * window is accepted. If a future manifest version includes a {@code nonce}
 * field, it must equal the request nonce or the payload is ignored. The
 * Ed25519 signature over
 * {@link RemoteIndexFetcher#manifestCanonicalBytes(int, String, long, List)}
 * is always the authoritative check; the transport {@code sourcePub} is not
 * trusted (relayed manifests still authenticate via the signature).
 *
 * <p><b>Bounds:</b> inbound payloads larger than
 * {@link RemoteSearchResponse#MAX_STREAM_BYTES} are dropped, and manifests
 * with more than {@link RemoteSearchRequest#MAX_LIMIT} rows are rejected.
 *
 * <p><b>Threading / units:</b> a {@code CatalogBrowser} is <b>not
 * thread-safe</b> — callers must serialize {@link #fetchCatalog(byte[], int)}
 * calls. Only one fetch is expected per call. All timeouts are milliseconds;
 * the internal deadline is monotonic nanoseconds. Never throws out: any
 * validation, transport, decode, verification, or timeout failure yields an
 * empty list.
 */
public final class CatalogBrowser {

  private static final Logger LOG = Logger.getLogger(CatalogBrowser.class);

  /**
   * Maximum accepted encoded manifest size. Mirrors the search-response stream
   * bound so no single catalog frame can exceed the mesh payload budget.
   */
  private static final int MAX_MANIFEST_BYTES = RemoteSearchResponse.MAX_STREAM_BYTES;

  /** Maximum accepted manifest rows. Mirrors the search request result limit. */
  private static final int MAX_MANIFEST_ENTRIES = RemoteSearchRequest.MAX_LIMIT;

  private static final int PUBLIC_KEY_LENGTH = 32;
  private static final int ED25519_SIGNATURE_LENGTH = 64;
  private static final int NONCE_LENGTH = 32;

  private static final ThreadPoolExecutor SENDERS = new ThreadPoolExecutor(
      2, 2, 30, TimeUnit.SECONDS, new ArrayBlockingQueue<>(16), r -> {
        Thread thread = new Thread(r, "catalog-browser-send");
        thread.setDaemon(true);
        return thread;
      });

  private final IdentityKeys identity;
  private final DistributedSearchTransport transport;

  public CatalogBrowser(IdentityKeys identity, DistributedSearchTransport transport) {
    if (identity == null) {
      throw new IllegalArgumentException("identity is null");
    }
    if (transport == null) {
      throw new IllegalArgumentException("transport is null");
    }
    this.identity = identity;
    this.transport = transport;
  }

  /**
   * Fetch {@code peerPub}'s shared-torrent catalog.
   *
   * @param peerPub   raw 32-byte Ed25519 public key of the peer to browse
   * @param timeoutMs total wall-clock budget in milliseconds ({@code > 0})
   * @return the verified catalog rows, or an empty list on any failure. Never
   *     throws.
   */
  public List<RemoteIndexFetcher.RemoteTorrentEntry> fetchCatalog(byte[] peerPub, int timeoutMs) {
    if (peerPub == null || peerPub.length != PUBLIC_KEY_LENGTH || timeoutMs <= 0) {
      return Collections.emptyList();
    }
    final byte[] target = peerPub.clone();
    final long deadlineNanos = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMs);
    final byte[] nonce = new byte[NONCE_LENGTH];
    try {
      new SecureRandom().nextBytes(nonce);
    } catch (Throwable t) {
      LOG.warn("CatalogBrowser: no SecureRandom available for nonce", t);
      return Collections.emptyList();
    }
    RemoteCatalogBrowseRequest request = buildSignedRequest(target, nonce);
    if (request == null) {
      return Collections.emptyList();
    }
    final byte[] encoded;
    try {
      encoded = SearchPayloadCodec.encodeCatalogBrowseRequest(request);
    } catch (Throwable t) {
      LOG.debug("CatalogBrowser: could not encode catalog browse request", t);
      return Collections.emptyList();
    }
    final byte[] expectedNonce = request.nonce();
    final CountDownLatch done = new CountDownLatch(1);
    final AtomicReference<List<RemoteIndexFetcher.RemoteTorrentEntry>> result =
        new AtomicReference<>();

    DistributedSearchTransport.PayloadListener listener =
        new DistributedSearchTransport.PayloadListener() {
          @Override
          public void onPayload(byte[] sourcePub, byte[] payload, long receivedMs) {
            accept(payload);
          }

          @Override
          public void onPayload(byte[] sourcePub, byte[] payload, long receivedMs, int protocolId) {
            if (MeshProtocolId.effective(protocolId) == MeshProtocolId.SEARCH) {
              accept(payload);
            }
          }

          private void accept(byte[] payload) {
            if (done.getCount() == 0 || System.nanoTime() >= deadlineNanos) {
              return;
            }
            List<RemoteIndexFetcher.RemoteTorrentEntry> entries =
                decodeAndVerifyManifest(payload, expectedNonce, target);
            if (entries == null) {
              return;
            }
            result.compareAndSet(null, entries);
            done.countDown();
          }
        };

    transport.addListener(listener);
    DistributedSearchTransport.SendOperation operation = null;
    Future<?> sendFuture = null;
    try {
      operation = transport.createSend(target, MeshProtocolId.SEARCH, encoded, deadlineNanos);
      if (operation == null) {
        return Collections.emptyList();
      }
      final DistributedSearchTransport.SendOperation op = operation;
      sendFuture = SENDERS.submit(op::execute);
      long remaining = deadlineNanos - System.nanoTime();
      if (remaining > 0) {
        done.await(remaining, TimeUnit.NANOSECONDS);
      }
      List<RemoteIndexFetcher.RemoteTorrentEntry> entries = result.get();
      return entries == null ? Collections.emptyList() : entries;
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return Collections.emptyList();
    } catch (Throwable t) {
      LOG.debug("CatalogBrowser.fetchCatalog failed for peer " + Hex.encode(target), t);
      return Collections.emptyList();
    } finally {
      if (sendFuture != null) {
        sendFuture.cancel(true);
      }
      if (operation != null) {
        operation.cancel();
      }
      SENDERS.purge();
      transport.removeListener(listener);
    }
  }

  private RemoteCatalogBrowseRequest buildSignedRequest(byte[] targetPub, byte[] nonce) {
    try {
      long timestamp = System.currentTimeMillis() / 1000L;
      RemoteCatalogBrowseRequest.Builder builder = RemoteCatalogBrowseRequest.builder()
          .requesterPub(identity.ed25519PubRaw())
          .targetPub(targetPub)
          .nonce(nonce)
          .timestamp(timestamp);
      RemoteCatalogBrowseRequest unsigned =
          builder.signature(new byte[ED25519_SIGNATURE_LENGTH]).build();
      Signature signer = IdentityKeys.softwareSignature("Ed25519");
      signer.initSign(identity.ed25519().getPrivate());
      signer.update(unsigned.canonicalBytes());
      return builder.signature(signer.sign()).build();
    } catch (Throwable t) {
      LOG.warn("CatalogBrowser: could not sign catalog browse request", t);
      return null;
    }
  }

  /**
   * Decode, bound, and verify an inbound manifest. Returns {@code null} when
   * the payload is not a valid, peer-signed manifest for this fetch.
   */
  private static List<RemoteIndexFetcher.RemoteTorrentEntry> decodeAndVerifyManifest(
      byte[] payload, byte[] expectedNonce, byte[] peerPub) {
    if (payload == null || payload.length == 0 || payload.length > MAX_MANIFEST_BYTES) {
      return null;
    }
    try {
      JsonObject root = JsonParser.parseString(new String(payload, StandardCharsets.UTF_8))
          .getAsJsonObject();
      if (root == null) {
        return null;
      }
      JsonElement nonceElement = root.get("nonce");
      if (nonceElement != null && nonceElement.isJsonPrimitive()) {
        byte[] manifestNonce = Base64.getDecoder().decode(nonceElement.getAsString());
        if (!Arrays.equals(manifestNonce, expectedNonce)) {
          return null;
        }
      }
      JsonElement versionElement = root.get("v");
      JsonElement pubElement = root.get("pub");
      JsonElement tsElement = root.get("ts");
      JsonElement sigElement = root.get("sig");
      if (versionElement == null || pubElement == null
          || tsElement == null || sigElement == null) {
        return null;
      }
      int version = versionElement.getAsInt();
      String pubB64 = pubElement.getAsString();
      long timestamp = tsElement.getAsLong();
      byte[] signature = Base64.getDecoder().decode(sigElement.getAsString());
      if (signature.length != ED25519_SIGNATURE_LENGTH) {
        return null;
      }
      List<RemoteIndexFetcher.RemoteTorrentEntry> entries =
          RemoteIndexFetcher.parseManifest(payload);
      if (entries == null || entries.size() > MAX_MANIFEST_ENTRIES) {
        return null;
      }
      byte[] manifestPub = Base64.getDecoder().decode(pubB64);
      if (manifestPub.length != PUBLIC_KEY_LENGTH || !Arrays.equals(manifestPub, peerPub)) {
        return null;
      }
      byte[] canonical =
          RemoteIndexFetcher.manifestCanonicalBytes(version, pubB64, timestamp, entries);
      if (!verifyManifestSignature(peerPub, canonical, signature)) {
        LOG.debug("CatalogBrowser: manifest signature verification failed");
        return null;
      }
      return entries;
    } catch (Throwable t) {
      LOG.debug("CatalogBrowser: rejected malformed catalog manifest", t);
      return null;
    }
  }

  private static boolean verifyManifestSignature(
      byte[] peerPub, byte[] canonical, byte[] signature) {
    try {
      PublicKey publicKey = SearchResponseVerifier.rawEd25519ToPublicKey(peerPub);
      Signature verifier = IdentityKeys.softwareSignature("Ed25519");
      verifier.initVerify(publicKey);
      verifier.update(canonical);
      return verifier.verify(signature);
    } catch (GeneralSecurityException e) {
      LOG.debug("CatalogBrowser: manifest signature verification threw", e);
      return false;
    }
  }
}
