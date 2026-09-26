/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */
package com.frostwire.android.search;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

import android.app.Application;
import com.frostwire.android.gui.transfers.UIBittorrentDownload;
import com.frostwire.transfers.BittorrentDownload;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, application = Application.class)
public class AndroidShareVisibilityTest {
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
