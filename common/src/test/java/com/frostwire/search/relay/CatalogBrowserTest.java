/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.search.relay;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.frostwire.search.relay.icebridge.MeshProtocolId;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.charset.StandardCharsets;
import java.security.Signature;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;

/**
 * Exercises {@link CatalogBrowser} against a fake in-process
 * {@link DistributedSearchTransport} that delivers a peer-signed manifest
 * (or a corrupted one) off-thread.
 */
class CatalogBrowserTest {

  private static final List<RemoteIndexFetcher.RemoteTorrentEntry> CATALOG = List.of(
      new RemoteIndexFetcher.RemoteTorrentEntry(
          "a1b2c3d4e5f6", "ubuntu-24.04.iso", 5_000_000_000L, 1),
      new RemoteIndexFetcher.RemoteTorrentEntry(
          "f0e1d2c3b4a5", "debian-12.iso", 3_000_000_000L, 3));

  @Test
  void constructorRejectsNulls() throws Exception {
    IdentityKeys identity = IdentityKeys.generate(0);
    FakeTransport transport = new FakeTransport(new byte[32], (Supplier<byte[]>) null);
    assertThrows(IllegalArgumentException.class, () -> new CatalogBrowser(null, transport));
    assertThrows(IllegalArgumentException.class, () -> new CatalogBrowser(identity, null));
  }

  @Test
  void invalidPeerPubOrTimeoutReturnsEmpty() throws Exception {
    IdentityKeys identity = IdentityKeys.generate(0);
    byte[] peerPub = new byte[32];
    FakeTransport transport = new FakeTransport(peerPub, (Supplier<byte[]>) null);
    CatalogBrowser browser = new CatalogBrowser(identity, transport);

    assertTrue(browser.fetchCatalog(null, 1000).isEmpty());
    assertTrue(browser.fetchCatalog(new byte[31], 1000).isEmpty());
    assertTrue(browser.fetchCatalog(peerPub, 0).isEmpty());
    assertTrue(browser.fetchCatalog(peerPub, -1).isEmpty());
    assertTrue(transport.listeners().isEmpty(), "validation failures must not register listeners");
  }

  @Test
  void happyPathReturnsVerifiedRowsAndCleansUp() throws Exception {
    IdentityKeys holder = IdentityKeys.generate(0);
    byte[] peerPub = holder.ed25519PubRaw();
    byte[] manifest = signedManifest(holder, peerPub, CATALOG, false, null);
    FakeTransport transport = new FakeTransport(peerPub, () -> manifest);
    CatalogBrowser browser = new CatalogBrowser(IdentityKeys.generate(0), transport);

    List<RemoteIndexFetcher.RemoteTorrentEntry> entries = browser.fetchCatalog(peerPub, 3000);

    assertNotNull(entries);
    assertEquals(2, entries.size());
    assertEquals("a1b2c3d4e5f6", entries.get(0).infoHashHex());
    assertEquals("ubuntu-24.04.iso", entries.get(0).name());
    assertEquals(5_000_000_000L, entries.get(0).sizeBytes());
    assertEquals(1, entries.get(0).fileCount());
    assertEquals("f0e1d2c3b4a5", entries.get(1).infoHashHex());
    assertEquals(3, entries.get(1).fileCount());

    assertEquals(1, transport.sent.size(), "exactly one send is issued");
    assertTrue(transport.sent.get(0).executed, "send operation should have executed");
    assertTrue(transport.sent.get(0).cancelled, "send operation must be cancelled in finally");
    assertTrue(transport.listeners().isEmpty(), "listener must be removed in finally");
  }

  @Test
  void deliveredRequestIsSignedAndTargetsPeer() throws Exception {
    IdentityKeys holder = IdentityKeys.generate(0);
    IdentityKeys requester = IdentityKeys.generate(0);
    byte[] peerPub = holder.ed25519PubRaw();
    byte[] manifest = signedManifest(holder, peerPub, CATALOG, false, null);
    FakeTransport transport = new FakeTransport(peerPub, () -> manifest);
    CatalogBrowser browser = new CatalogBrowser(requester, transport);

    browser.fetchCatalog(peerPub, 3000);

    FakeTransport.SentOperation sent = transport.sent.get(0);
    assertEquals(MeshProtocolId.CATALOG, sent.protocolId);
    assertTrue(Arrays.equals(peerPub, sent.targetPub));

    RemoteCatalogBrowseRequest request =
        SearchPayloadCodec.decodeCatalogBrowseRequest(sent.payload);
    assertNotNull(request, "sent payload must decode as a catalog browse request");
    assertTrue(Arrays.equals(peerPub, request.targetPub()));
    assertTrue(Arrays.equals(requester.ed25519PubRaw(), request.requesterPub()));

    Signature verifier = IdentityKeys.softwareSignature("Ed25519");
    verifier.initVerify(SearchResponseVerifier.rawEd25519ToPublicKey(requester.ed25519PubRaw()));
    verifier.update(request.canonicalBytes());
    assertTrue(verifier.verify(request.signature()),
        "request must carry a valid requester signature");
  }

  @Test
  void badSignatureManifestIsRejected() throws Exception {
    IdentityKeys holder = IdentityKeys.generate(0);
    byte[] peerPub = holder.ed25519PubRaw();
    byte[] manifest = signedManifest(holder, peerPub, CATALOG, true, null);
    FakeTransport transport = new FakeTransport(peerPub, () -> manifest);
    CatalogBrowser browser = new CatalogBrowser(IdentityKeys.generate(0), transport);

    assertTrue(browser.fetchCatalog(peerPub, 500).isEmpty());
    assertTrue(transport.sent.get(0).cancelled);
    assertTrue(transport.listeners().isEmpty());
  }

  @Test
  void wrongNonceManifestIsRejected() throws Exception {
    IdentityKeys holder = IdentityKeys.generate(0);
    byte[] peerPub = holder.ed25519PubRaw();
    byte[] wrongNonce = new byte[32];
    Arrays.fill(wrongNonce, (byte) 0x5a);
    byte[] manifest = signedManifest(holder, peerPub, CATALOG, false, wrongNonce);
    FakeTransport transport = new FakeTransport(peerPub, () -> manifest);
    CatalogBrowser browser = new CatalogBrowser(IdentityKeys.generate(0), transport);

    assertTrue(browser.fetchCatalog(peerPub, 500).isEmpty());
    assertTrue(transport.sent.get(0).cancelled);
    assertTrue(transport.listeners().isEmpty());
  }

  @Test
  void noResponseTimesOutEmptyAndCleansUp() throws Exception {
    byte[] peerPub = new byte[32];
    Arrays.fill(peerPub, (byte) 0x11);
    FakeTransport transport = new FakeTransport(peerPub, (Supplier<byte[]>) null);
    CatalogBrowser browser = new CatalogBrowser(IdentityKeys.generate(0), transport);

    long start = System.nanoTime();
    List<RemoteIndexFetcher.RemoteTorrentEntry> entries = browser.fetchCatalog(peerPub, 200);
    long elapsedMs = (System.nanoTime() - start) / 1_000_000L;

    assertTrue(entries.isEmpty());
    assertTrue(elapsedMs >= 150, "should wait out the timeout budget, was " + elapsedMs + "ms");
    assertEquals(1, transport.sent.size());
    assertTrue(transport.sent.get(0).cancelled, "send operation must be cancelled on timeout");
    assertTrue(transport.listeners().isEmpty(), "listener must be removed on timeout");
  }

  @Test
  void largeCatalogArrivesAsSignedPagesThatEachFitOneRelayedDatagram() throws Exception {
    IdentityKeys holder = IdentityKeys.generate(0);
    byte[] peerPub = holder.ed25519PubRaw();
    List<RemoteIndexFetcher.RemoteTorrentEntry> big = bigCatalog(30);
    List<byte[]> delivered = new CopyOnWriteArrayList<>();
    FakeTransport transport = new FakeTransport(peerPub, request -> {
      List<byte[]> pages = signedPages(holder, big, nonceOf(request));
      delivered.addAll(pages);
      List<byte[]> reversed = new java.util.ArrayList<>(pages);
      java.util.Collections.reverse(reversed); // relays do not preserve order
      return reversed;
    });

    List<RemoteIndexFetcher.RemoteTorrentEntry> entries =
        new CatalogBrowser(IdentityKeys.generate(0), transport).fetchCatalog(peerPub, 3000);

    assertTrue(delivered.size() > 1, "a 30-torrent catalog cannot fit a single relayed frame");
    for (byte[] page : delivered) {
      assertTrue(page.length + com.frostwire.search.relay.icebridge.MeshEnvelope.HEADER_LENGTH
              <= com.frostwire.search.relay.icebridge.udp.RelayFrame.MAX_APP_PAYLOAD,
          "page of " + page.length + " bytes would be dropped by the relay");
    }
    assertEquals(big.size(), entries.size());
    for (int i = 0; i < big.size(); i++) {
      assertEquals(big.get(i).infoHashHex(), entries.get(i).infoHashHex(), "page order restored");
    }
  }

  @Test
  void pagesSignedForAnotherRequestAreRejected() throws Exception {
    IdentityKeys holder = IdentityKeys.generate(0);
    byte[] peerPub = holder.ed25519PubRaw();
    byte[] otherNonce = new byte[32];
    Arrays.fill(otherNonce, (byte) 7);
    FakeTransport transport = new FakeTransport(peerPub,
        request -> signedPages(holder, bigCatalog(30), otherNonce));

    assertTrue(new CatalogBrowser(IdentityKeys.generate(0), transport)
        .fetchCatalog(peerPub, 500).isEmpty());
  }

  @Test
  void relabeledPageFailsVerificationAndOnlyVerifiedPagesAreReturned() throws Exception {
    IdentityKeys holder = IdentityKeys.generate(0);
    byte[] peerPub = holder.ed25519PubRaw();
    List<RemoteIndexFetcher.RemoteTorrentEntry> big = bigCatalog(30);
    int[] total = {0};
    FakeTransport transport = new FakeTransport(peerPub, request -> {
      List<byte[]> pages = new java.util.ArrayList<>(signedPages(holder, big, nonceOf(request)));
      total[0] = pages.size();
      // A relay relabels the last page as page 0 and drops the real page 0.
      JsonObject last = JsonParser.parseString(
          new String(pages.get(pages.size() - 1), StandardCharsets.UTF_8)).getAsJsonObject();
      last.addProperty("page", 0);
      pages.set(0, last.toString().getBytes(StandardCharsets.UTF_8));
      pages.remove(pages.size() - 1);
      return pages;
    });

    List<RemoteIndexFetcher.RemoteTorrentEntry> entries =
        new CatalogBrowser(IdentityKeys.generate(0), transport).fetchCatalog(peerPub, 500);

    assertTrue(total[0] > 2);
    assertTrue(!entries.isEmpty() && entries.size() < big.size(),
        "verified pages are shown; the relabeled one is not");
    assertTrue(entries.stream().noneMatch(e -> e.infoHashHex().equals(big.get(0).infoHashHex())),
        "page 0 rows were dropped and the forged page 0 must not verify");
  }

  // --- helpers ---

  /**
   * Build a manifest signed by {@code signer} over the canonical bytes while
   * claiming {@code claimedPub}. When {@code tamperSignature} is true the
   * signature is corrupted; when {@code nonce} is non-null it is injected into
   * the JSON (the canonical domain excludes the nonce, so the signature stays
   * valid for a nonce-correlation test).
   */
  private static byte[] signedManifest(IdentityKeys signer, byte[] claimedPub,
                                       List<RemoteIndexFetcher.RemoteTorrentEntry> entries,
                                       boolean tamperSignature, byte[] nonce) throws Exception {
    String pubB64 = Base64.getEncoder().withoutPadding().encodeToString(claimedPub);
    long timestamp = System.currentTimeMillis() / 1000L;
    byte[] canonical = RemoteIndexFetcher.manifestCanonicalBytes(
        RemoteIndexFetcher.MANIFEST_VERSION, pubB64, timestamp, entries);
    Signature signerSignature = IdentityKeys.softwareSignature("Ed25519");
    signerSignature.initSign(signer.ed25519().getPrivate());
    signerSignature.update(canonical);
    byte[] signature = signerSignature.sign();
    if (tamperSignature) {
      signature[0] ^= 0x01;
    }
    byte[] json = RemoteIndexFetcher.buildManifestJson(
        RemoteIndexFetcher.MANIFEST_VERSION, pubB64, timestamp, entries, signature);
    if (nonce == null) {
      return json;
    }
    JsonObject root = JsonParser.parseString(new String(json, StandardCharsets.UTF_8))
        .getAsJsonObject();
    root.addProperty("nonce", Base64.getEncoder().withoutPadding().encodeToString(nonce));
    return root.toString().getBytes(StandardCharsets.UTF_8);
  }

  private static List<RemoteIndexFetcher.RemoteTorrentEntry> bigCatalog(int n) {
    List<RemoteIndexFetcher.RemoteTorrentEntry> out = new java.util.ArrayList<>();
    for (int i = 0; i < n; i++) {
      out.add(new RemoteIndexFetcher.RemoteTorrentEntry(
          String.format("%040x", i + 1),
          "Chip Stocks Crash, 20B Fund Margin Called, Frontier Labs SLOW DOWN AI #" + i
              + " (640x360).mp4",
          193_318_977L + i, 1));
    }
    return out;
  }

  private static byte[] nonceOf(byte[] requestPayload) {
    return SearchPayloadCodec.decodeCatalogBrowseRequest(requestPayload).nonce();
  }

  private static List<byte[]> signedPages(IdentityKeys signer,
      List<RemoteIndexFetcher.RemoteTorrentEntry> entries, byte[] nonce) {
    try {
      String pubB64 = Base64.getEncoder().withoutPadding().encodeToString(signer.ed25519PubRaw());
      long ts = System.currentTimeMillis() / 1000L;
      List<List<RemoteIndexFetcher.RemoteTorrentEntry>> split =
          CatalogManifestPages.paginate(pubB64, ts, entries);
      List<byte[]> out = new java.util.ArrayList<>();
      for (int page = 0; page < split.size(); page++) {
        Signature sig = IdentityKeys.softwareSignature("Ed25519");
        sig.initSign(signer.ed25519().getPrivate());
        sig.update(CatalogManifestPages.canonicalBytes(
            pubB64, ts, split.get(page), nonce, page, split.size()));
        out.add(CatalogManifestPages.buildJson(
            pubB64, ts, split.get(page), nonce, page, split.size(), sig.sign()));
      }
      return out;
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  private static final class FakeTransport implements DistributedSearchTransport {

    private final List<PayloadListener> listeners = new CopyOnWriteArrayList<>();
    final List<SentOperation> sent = new CopyOnWriteArrayList<>();
    private final byte[] responderPub;
    private final java.util.function.Function<byte[], List<byte[]>> responder;

    FakeTransport(byte[] responderPub, Supplier<byte[]> responder) {
      this.responderPub = responderPub == null ? new byte[32] : responderPub.clone();
      this.responder = responder == null ? null : request -> {
        byte[] one = responder.get();
        return one == null ? null : List.of(one);
      };
    }

    FakeTransport(byte[] responderPub,
        java.util.function.Function<byte[], List<byte[]>> pagedResponder) {
      this.responderPub = responderPub.clone();
      this.responder = pagedResponder;
    }

    List<PayloadListener> listeners() {
      return listeners;
    }

    @Override
    public boolean send(byte[] targetPub, int protocolId, byte[] payload) {
      return true;
    }

    @Override
    public SendOperation createSend(byte[] targetPub, int protocolId, byte[] payload,
                                    long deadlineNanos) {
      SentOperation operation =
          new SentOperation(targetPub.clone(), protocolId, payload.clone());
      sent.add(operation);
      return operation;
    }

    @Override
    public void addListener(PayloadListener listener) {
      listeners.add(listener);
    }

    @Override
    public void removeListener(PayloadListener listener) {
      listeners.remove(listener);
    }

    final class SentOperation implements SendOperation {
      final byte[] targetPub;
      final int protocolId;
      final byte[] payload;
      volatile boolean executed;
      volatile boolean cancelled;

      SentOperation(byte[] targetPub, int protocolId, byte[] payload) {
        this.targetPub = targetPub;
        this.protocolId = protocolId;
        this.payload = payload;
      }

      @Override
      public boolean execute() {
        executed = true;
        List<byte[]> responses = responder == null ? null : responder.apply(payload);
        if (responses == null) {
          return true;
        }
        Thread deliver = new Thread(() -> {
          for (byte[] response : responses) {
            for (PayloadListener listener : listeners) {
              listener.onPayload(responderPub, response, System.currentTimeMillis(),
                  MeshProtocolId.CATALOG);
            }
          }
        }, "catalog-browser-fake-deliver");
        deliver.setDaemon(true);
        deliver.start();
        return true;
      }

      @Override
      public void cancel() {
        cancelled = true;
      }
    }
  }
}
