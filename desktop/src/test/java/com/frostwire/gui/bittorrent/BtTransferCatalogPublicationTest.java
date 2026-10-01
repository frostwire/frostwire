/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */
package com.frostwire.gui.bittorrent;

import static org.junit.jupiter.api.Assertions.*;

import com.frostwire.gui.bittorrent.BtTransferShareVisibility.TransferStatus;
import com.frostwire.jlibtorrent.Entry;
import com.frostwire.jlibtorrent.SessionManager;
import com.frostwire.search.relay.IdentityKeys;
import com.frostwire.search.relay.IndexAnnouncementPublisher;
import com.frostwire.search.relay.LocalIndex;
import com.frostwire.search.relay.LocalSharedTorrent;
import com.frostwire.search.relay.RelaySearchService;
import com.frostwire.search.relay.RemoteSearchRequest;
import com.frostwire.search.relay.RemoteSearchResponse;
import com.frostwire.transfers.TransferState;
import java.security.Signature;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class BtTransferCatalogPublicationTest {
  @Test
  void startupPublisherRequiresOptInAndWithdrawsWithoutDeletingIndexRows() throws Exception {
    Fixture fixture = new Fixture();
    fixture.catalog.set(false);
    IndexAnnouncementPublisher publisher = fixture.publisher();
    RecordingSession session = new RecordingSession();

    assertEquals(0, publisher.publishIfNeeded(session));
    assertEquals(1, session.manifests.size(), "startup clears an old process advertisement");
    assertTrue(session.rows().isEmpty(), "opt-out must never publish a catalog row");

    fixture.catalog.set(true);
    assertEquals(1, publisher.publishIfNeeded(session));
    assertEquals(fixture.row.infoHashHex(), session.rows().get(0).dictionary().get("ih").string());
    fixture.catalog.set(false);
    assertEquals(0, publisher.publishIfNeeded(session));
    assertEquals(3, session.manifests.size(), "opt-out replaces the prior nonempty manifest");
    assertTrue(session.rows().isEmpty());
    assertEquals(1, fixture.index.size(), "withdrawal must not depend on removing local history");
    publisher.publishIfNeeded(session);
    assertEquals(3, session.manifests.size(), "unchanged opt-out need not publish repeatedly");
  }

  @Test
  void catalogRejectsPausedPrivateDownloadingAndUnavailableTransfers() throws Exception {
    List<TransferStatus> denied =
        List.of(
            new TransferStatus(TransferState.SEEDING, true, true, false),
            new TransferStatus(TransferState.SEEDING, false, true, true),
            new TransferStatus(TransferState.DOWNLOADING, false, true, false),
            new TransferStatus(TransferState.SEEDING, false, false, false));
    for (TransferStatus status : denied) {
      Fixture fixture = new Fixture();
      IndexAnnouncementPublisher publisher = fixture.publisher();
      RecordingSession session = new RecordingSession();
      assertEquals(1, publisher.publishIfNeeded(session));
      fixture.transfer.set(status);
      publisher.publishIfNeeded(session);
      assertEquals(2, session.manifests.size(), "must publish a withdrawal, not just skip a tick");
      assertTrue(session.rows().isEmpty(), "catalog must deny " + status);
    }
    Fixture fixture = new Fixture();
    IndexAnnouncementPublisher publisher = fixture.publisher();
    RecordingSession session = new RecordingSession();
    assertEquals(1, publisher.publishIfNeeded(session));
    fixture.transfer.set(null);
    publisher.publishIfNeeded(session);
    assertTrue(session.rows().isEmpty(), "removed transfer must be withdrawn");
    fixture = new Fixture();
    publisher = fixture.publisher();
    assertEquals(1, publisher.publishIfNeeded(session));
    fixture.enabled.set(false);
    publisher.publishIfNeeded(session);
    assertTrue(session.rows().isEmpty(), "disabled participation must withdraw the catalog");
  }

  @Test
  void ordinarySignedSearchStillReturnsActiveDownloadWhenCatalogIsDisabled() throws Exception {
    Fixture fixture = new Fixture();
    fixture.catalog.set(false);
    fixture.transfer.set(new TransferStatus(TransferState.DOWNLOADING, false, true, false));
    RelaySearchService search =
        new RelaySearchService(fixture.index, fixture.identity, fixture.visibility);
    IdentityKeys requester = IdentityKeys.generate();
    RemoteSearchRequest.Builder builder =
        RemoteSearchRequest.builder()
            .keywords("catalog seed")
            .limit(10)
            .ttl(1)
            .path(new byte[0][])
            .requesterPub(requester.ed25519PubRaw())
            .nonce(new byte[32])
            .timestamp(System.currentTimeMillis() / 1000)
            .signature(new byte[64]);
    Signature signer = Signature.getInstance("Ed25519");
    signer.initSign(requester.ed25519().getPrivate());
    signer.update(builder.build().canonicalBytes());
    RemoteSearchResponse response =
        search.handle(builder.signature(signer.sign()).build()).orElseThrow();
    assertEquals(1, response.rows().size());
    assertTrue(
        fixture.visibility.isVisible(fixture.row.infoHashHex()),
        "METADATA keeps general visibility");
    assertFalse(fixture.visibility.isCatalogVisible(fixture.row.infoHashHex()));
    RecordingSession session = new RecordingSession();
    assertEquals(0, fixture.publisher().publishIfNeeded(session));
    assertTrue(session.rows().isEmpty());
  }

  private static TransferStatus seed() {
    return new TransferStatus(TransferState.SEEDING, false, true, false);
  }

  private static final class Fixture {
    final AtomicBoolean enabled = new AtomicBoolean(true);
    final AtomicBoolean catalog = new AtomicBoolean(true);
    final AtomicReference<TransferStatus> transfer = new AtomicReference<>(seed());
    final IdentityKeys identity = IdentityKeys.generate();
    final MemoryIndex index = new MemoryIndex();
    final LocalSharedTorrent row;
    final BtTransferShareVisibility visibility;

    Fixture() throws Exception {
      byte[] hash = new byte[20];
      hash[0] = 1;
      row =
          new LocalSharedTorrent.Builder()
              .infoHash(hash)
              .name("catalog seed")
              .sizeBytes(100)
              .fileCount(1)
              .filesJson("[]")
              .publisherNodeId(new byte[20])
              .publisherEd25519Pub(identity.ed25519PubRaw())
              .publisherUtpPort(0)
              .addedAt(1)
              .lastSeenAt(1)
              .build();
      index.upsert(row);
      visibility =
          new BtTransferShareVisibility(
              enabled::get,
              catalog::get,
              value -> row.infoHashHex().equals(value) ? transfer.get() : null);
    }

    IndexAnnouncementPublisher publisher() {
      // This is the factory called by Initializer.startDhtAdvertiser, not a test-selected policy.
      return visibility.createCatalogPublisher(index, identity);
    }
  }

  private static final class RecordingSession extends SessionManager {
    final List<Entry> manifests = new ArrayList<>();

    @Override
    public void dhtPutItem(byte[] publicKey, byte[] privateKey, Entry entry, byte[] salt) {
      manifests.add(entry);
    }

    List<Entry> rows() {
      return manifests.get(manifests.size() - 1).dictionary().get("rows").list();
    }
  }

  private static final class MemoryIndex implements LocalIndex {
    LocalSharedTorrent row;
    long publishedAt;

    @Override
    public void upsert(LocalSharedTorrent value) {
      row = value;
    }

    @Override
    public void delete(String hash) {
      row = null;
    }

    @Override
    public Optional<LocalSharedTorrent> get(String hash) {
      return row != null && row.infoHashHex().equals(hash) ? Optional.of(row) : Optional.empty();
    }

    @Override
    public List<LocalSharedTorrent> search(String query, int limit) {
      return row != null && row.name().contains(query) ? List.of(row) : List.of();
    }

    @Override
    public void markPublished(String hash, long timestamp) {
      publishedAt = timestamp;
    }

    @Override
    public List<String> needsRepublish(long now, long threshold) {
      return row != null && (publishedAt == 0 || now - publishedAt >= threshold)
          ? List.of(row.infoHashHex())
          : List.of();
    }

    @Override
    public void updateLastSeen(String hash, long timestamp) {}

    @Override
    public int size() {
      return row == null ? 0 : 1;
    }
  }
}
