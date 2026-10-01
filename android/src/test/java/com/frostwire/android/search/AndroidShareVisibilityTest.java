/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */
package com.frostwire.android.search;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

import android.app.Application;
import com.frostwire.android.gui.transfers.UIBittorrentDownload;
import com.frostwire.search.relay.ShareVisibilityPolicy;
import com.frostwire.transfers.BittorrentDownload;
import com.frostwire.transfers.TransferState;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, application = Application.class)
public class AndroidShareVisibilityTest {
  @Test
  public void catalogRequiresSeedingWhileOrdinarySharingSurvivesCatalogOptOut() {
    AtomicBoolean participating = new AtomicBoolean(true);
    AtomicBoolean consent = new AtomicBoolean(true);
    AtomicBoolean live = new AtomicBoolean(true);
    AtomicReference<TransferState> transfer = new AtomicReference<>(TransferState.SEEDING);
    Function<String, AtomicReference<TransferState>> lookup = hash -> live.get() ? transfer : null;
    ShareVisibilityPolicy catalog =
        AndroidShareVisibility.catalogPolicy(
            participating::get, consent::get, lookup, AtomicReference::get);
    AndroidShareVisibility ordinary =
        new AndroidShareVisibility(participating::get, hash -> lookup.apply(hash) != null);

    assertTrue(catalog.isVisible("abcd"));
    assertTrue(ordinary.isVisible("abcd"));
    transfer.set(TransferState.DOWNLOADING);
    assertFalse("an active download is not browsable", catalog.isVisible("abcd"));
    assertTrue("downloads remain eligible for ordinary sharing", ordinary.isVisible("abcd"));
    transfer.set(TransferState.SEEDING);
    consent.set(false);
    assertFalse(catalog.isVisible("abcd"));
    assertTrue("catalog opt-out does not disable SEARCH/METADATA", ordinary.isVisible("abcd"));
    consent.set(true);
    assertTrue("consent is live, not captured at construction", catalog.isVisible("abcd"));
    live.set(false);
    assertFalse("a removed transfer is not browsable", catalog.isVisible("abcd"));
    assertFalse(ordinary.isVisible("abcd"));
    live.set(true);
    participating.set(false);
    assertFalse(catalog.isVisible("abcd"));
    assertFalse(ordinary.isVisible("abcd"));
  }

  @Test
  public void catalogOptOutDoesNotPerformLiveOrNativeStateLookup() {
    AtomicBoolean participating = new AtomicBoolean(true);
    AtomicBoolean consent = new AtomicBoolean(false);
    AtomicInteger lookups = new AtomicInteger();
    AtomicInteger stateReads = new AtomicInteger();
    ShareVisibilityPolicy catalog =
        AndroidShareVisibility.catalogPolicy(
            participating::get,
            consent::get,
            hash -> {
              lookups.incrementAndGet();
              return new Object();
            },
            transfer -> {
              stateReads.incrementAndGet();
              return TransferState.SEEDING;
            });

    assertFalse(catalog.isVisible("abcd"));
    assertEquals(0, lookups.get());
    assertEquals(0, stateReads.get());
    consent.set(true);
    participating.set(false);
    assertFalse(catalog.isVisible("abcd"));
    assertEquals(0, lookups.get());
    assertEquals(0, stateReads.get());
    participating.set(true);
    assertTrue(catalog.isVisible("ABCD"));
    assertEquals(1, lookups.get());
    assertEquals(1, stateReads.get());
  }

  @Test
  public void catalogConsentAndParticipationRevokedDuringLookupFailClosed() {
    for (boolean revokeConsent : new boolean[] {true, false}) {
      AtomicBoolean participating = new AtomicBoolean(true);
      AtomicBoolean consent = new AtomicBoolean(true);
      ShareVisibilityPolicy catalog =
          AndroidShareVisibility.catalogPolicy(
              participating::get,
              consent::get,
              hash -> {
                (revokeConsent ? consent : participating).set(false);
                return new Object();
              },
              transfer -> TransferState.SEEDING);
      assertFalse("permission must be rechecked after live lookup", catalog.isVisible("abcd"));
    }
  }

  @Test
  public void catalogConsentRevokedDuringStateReadFailsClosed() {
    AtomicBoolean consent = new AtomicBoolean(true);
    AtomicInteger reads = new AtomicInteger();
    ShareVisibilityPolicy catalog =
        AndroidShareVisibility.catalogPolicy(
            () -> true,
            consent::get,
            hash -> new Object(),
            transfer -> {
              reads.incrementAndGet();
              consent.set(false);
              return TransferState.SEEDING;
            });
    assertFalse(catalog.isVisible("abcd"));
    assertEquals(1, reads.get());
    assertFalse(catalog.isVisible("abcd"));
    assertEquals("subsequent opt-out must not read state", 1, reads.get());
  }

  @Test
  public void catalogMissingTransferOrUnavailableStateFailsClosed() {
    AtomicInteger reads = new AtomicInteger();
    ShareVisibilityPolicy missing =
        AndroidShareVisibility.catalogPolicy(
            () -> true,
            () -> true,
            hash -> null,
            transfer -> {
              reads.incrementAndGet();
              return TransferState.SEEDING;
            });
    assertFalse(missing.isVisible("abcd"));
    assertEquals(0, reads.get());
    ShareVisibilityPolicy unavailable =
        AndroidShareVisibility.catalogPolicy(
            () -> true,
            () -> true,
            hash -> new Object(),
            transfer -> {
              throw new IllegalStateException("native handle removed");
            });
    assertFalse(unavailable.isVisible("abcd"));
    ShareVisibilityPolicy unknown =
        AndroidShareVisibility.catalogPolicy(
            () -> true, () -> true, hash -> new Object(), transfer -> null);
    assertFalse(unknown.isVisible("abcd"));
  }

  @Test
  public void hybridTorrentCanBeLookedUpByItsIndexedV1Hash() {
    String v1 = "512e9d91e069d560df9225017543f17e29973cd0";
    UIBittorrentDownload ui = mock(UIBittorrentDownload.class);
    assertSame(
        ui,
        AndroidShareVisibility.resolve(
            v1, key -> null, List.of(ui), candidate -> v1.toUpperCase()));
    assertNull(
        AndroidShareVisibility.resolve(
            "0000000000000000000000000000000000000000", key -> null, List.of(ui), candidate -> v1));
  }

  @Test
  public void distinctWrappersOfTheSameNativeTorrentAreTheSameTransfer() {
    // BTEngine callbacks and ensureUiDownload() each build their own BTDownload wrapper for one
    // torrent. A freshly seeded YouTube/HTTP file was never indexed because the callback wrapper
    // was not the UI row's object; only the native torrent identity matters.
    String[] callback = {"native-torrent-1"};
    String[] uiRow = {new String("native-torrent-1")};
    String[] readded = {"native-torrent-2"};
    java.util.function.Function<String[], String> handleOf = w -> w[0];
    java.util.function.BiPredicate<String, String> sameNative = String::equals;

    assertTrue(AndroidShareVisibility.sameTorrent(callback, uiRow, handleOf, sameNative));
    assertTrue(AndroidShareVisibility.sameTorrent(uiRow, uiRow, handleOf, sameNative));
    assertFalse(
        "a removed and re-added torrent is a different native object",
        AndroidShareVisibility.sameTorrent(callback, readded, handleOf, sameNative));
    assertFalse(AndroidShareVisibility.sameTorrent(callback, null, handleOf, sameNative));
    assertFalse(
        "missing native handle fails closed",
        AndroidShareVisibility.sameTorrent(callback, new String[] {null}, handleOf, sameNative));
    assertFalse(
        "a removed native handle fails closed",
        AndroidShareVisibility.sameTorrent(
            callback,
            uiRow,
            handleOf,
            (a, b) -> {
              throw new IllegalStateException("handle removed");
            }));
  }

  // Visibility matrix without native handles: eligibility is decided by the
  // live-transfer predicate and participation flag, never by parsing metadata.
  @Test
  public void onlyLiveActivePublicTransfersAreEligible() {
    UIBittorrentDownload ui = mock(UIBittorrentDownload.class, withSettings().stubOnly().lenient());
    when(ui.getDl()).thenReturn(null);
    when(ui.isSharingPaused()).thenReturn(false);
    when(ui.isRemovedFromSharing()).thenReturn(false);
    // Null download fails closed without touching native handles.
    assertFalse(AndroidShareVisibility.isLiveTransfer("abcd", null, hash -> ui));
    assertFalse(AndroidShareVisibility.isLiveTransfer("abcd", null, hash -> null));
    BittorrentDownload metadataOnly = mock(BittorrentDownload.class);
    assertFalse(AndroidShareVisibility.isLiveTransfer("abcd", null, hash -> metadataOnly));
  }

  @Test
  public void optOutAndWithdrawalAffectEverySubsequentCheckWithoutRestart() {
    AtomicBoolean participating = new AtomicBoolean(true);
    AtomicBoolean shared = new AtomicBoolean(true);
    AndroidShareVisibility policy =
        new AndroidShareVisibility(participating::get, hash -> shared.get());
    assertTrue(policy.isVisible("abcd"));
    shared.set(false);
    assertFalse(policy.isVisible("abcd"));
    shared.set(true);
    participating.set(false);
    assertFalse(policy.isVisible("abcd"));
  }

  @Test
  public void revocationDuringNativeLookupFailsClosed() {
    AtomicBoolean participating = new AtomicBoolean(true);
    AndroidShareVisibility policy =
        new AndroidShareVisibility(
            participating::get,
            hash -> {
              participating.set(false);
              return true;
            });
    assertFalse(policy.isVisible("abcd"));
  }

  @Test
  public void missingAndUnavailableMetadataFailClosed() {
    AndroidShareVisibility policy =
        new AndroidShareVisibility(
            () -> true,
            hash -> {
              throw new IllegalStateException("Native handle removed");
            });
    assertFalse(policy.isVisible("abcd"));
    assertFalse(policy.isVisible(null));
  }

  @Test
  public void replacementDuringMetadataLookupRevokesOldTransfer() {
    // A lookup that resolves to a different UI object on recheck revokes the old transfer,
    // even without dereferencing native handles.
    UIBittorrentDownload first =
        mock(UIBittorrentDownload.class, withSettings().stubOnly().lenient());
    UIBittorrentDownload second =
        mock(UIBittorrentDownload.class, withSettings().stubOnly().lenient());
    when(first.getDl()).thenReturn(null);
    when(second.getDl()).thenReturn(null);
    java.util.concurrent.atomic.AtomicInteger lookups =
        new java.util.concurrent.atomic.AtomicInteger();
    assertFalse(
        AndroidShareVisibility.isLiveTransfer(
            "abcd", null, hash -> lookups.getAndIncrement() == 0 ? first : second));
    assertFalse(AndroidShareVisibility.isLiveTransfer("abcd", null, hash -> first));
  }
}
