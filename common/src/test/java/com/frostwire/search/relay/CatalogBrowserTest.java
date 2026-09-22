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
    FakeTransport transport = new FakeTransport(new byte[32], null);
    assertThrows(IllegalArgumentException.class, () -> new CatalogBrowser(null, transport));
    assertThrows(IllegalArgumentException.class, () -> new CatalogBrowser(identity, null));
  }

  @Test
  void invalidPeerPubOrTimeoutReturnsEmpty() throws Exception {
    IdentityKeys identity = IdentityKeys.generate(0);
    byte[] peerPub = new byte[32];
    FakeTransport transport = new FakeTransport(peerPub, null);
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
    FakeTransport transport = new FakeTransport(peerPub, null);
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

  private static final class FakeTransport implements DistributedSearchTransport {

    private final List<PayloadListener> listeners = new CopyOnWriteArrayList<>();
    final List<SentOperation> sent = new CopyOnWriteArrayList<>();
    private final byte[] responderPub;
    private final Supplier<byte[]> responder;

    FakeTransport(byte[] responderPub, Supplier<byte[]> responder) {
      this.responderPub = responderPub == null ? new byte[32] : responderPub.clone();
      this.responder = responder;
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
        byte[] response = responder == null ? null : responder.get();
        if (response == null) {
          return true;
        }
        Thread deliver = new Thread(() -> {
          for (PayloadListener listener : listeners) {
            listener.onPayload(responderPub, response, System.currentTimeMillis(),
                MeshProtocolId.CATALOG);
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
