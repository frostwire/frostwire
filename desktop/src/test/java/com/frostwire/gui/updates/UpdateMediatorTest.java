/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.gui.updates;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.limegroup.gnutella.util.FrostWireUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * What the update popup and the Library's update button ask: is there a newer build, which file.
 */
class UpdateMediatorTest {

  @AfterEach
  void forgetTheMessage() {
    UpdateMediator.instance().setUpdateMessage(null);
  }

  private static UpdateMessage update(String version, String build) {
    UpdateMessage m = new UpdateMessage("update", "text");
    m.setVersion(version);
    m.setBuild(build);
    return m;
  }

  /** The current version with the service number raised by {@code delta}. */
  private static String versionAhead(int delta) {
    String[] parts = FrostWireUtils.getFrostWireVersion().split("\\.");
    return parts[0] + "." + parts[1] + "." + (Integer.parseInt(parts[2]) + delta);
  }

  @Test
  void theInstallerNameIsTheSaveAsNameFirst() {
    UpdateMessage m = update("9.9.9", null);
    m.setSaveAs("FrostWire-9.9.9.dmg");
    m.setTorrent("http://example.com/other.torrent");
    m.setInstallerUrl("https://example.com/path/another.dmg");
    assertEquals("FrostWire-9.9.9.dmg", UpdateMediator.getInstallerFilename(m));
  }

  @Test
  void theInstallerNameIsTheTorrentNameWithoutTheTorrentExtension() {
    UpdateMessage m = update("9.9.9", null);
    m.setTorrent("http://update.example.com/dl/frostwire-9.9.9.amd64.deb.torrent");
    assertEquals("frostwire-9.9.9.amd64.deb", UpdateMediator.getInstallerFilename(m));
  }

  @Test
  void theInstallerNameIsTheLastSegmentOfTheInstallerUrl() {
    UpdateMessage m = update("9.9.9", null);
    m.setInstallerUrl("https://dl.example.com/path/FrostWire-9.9.9.dmg");
    assertEquals("FrostWire-9.9.9.dmg", UpdateMediator.getInstallerFilename(m));
  }

  @Test
  void anEmptySaveAsFallsBackToTheTorrentAndThenTheInstallerUrl() {
    UpdateMessage m = update("9.9.9", null);
    // A parsed saveAs="" is a new String, not the interned "".
    m.setSaveAs(new String(""));
    m.setTorrent("http://example.com/dl/FrostWire.dmg.torrent");
    assertEquals("FrostWire.dmg", UpdateMediator.getInstallerFilename(m));
    m.setTorrent(null);
    m.setInstallerUrl("https://example.com/dl/FrostWire.dmg");
    assertEquals("FrostWire.dmg", UpdateMediator.getInstallerFilename(m));
  }

  @Test
  void aTorrentUrlWithoutTheTorrentExtensionStillHasAName() {
    UpdateMessage m = update("9.9.9", null);
    m.setTorrent("http://example.com/dl?id=3");
    assertEquals("dl", UpdateMediator.getInstallerFilename(m));
    m.setTorrent("http://example.com/dl/FrostWire-9.9.9.dmg");
    assertEquals("FrostWire-9.9.9.dmg", UpdateMediator.getInstallerFilename(m));
    m.setTorrent("http://example.com/dl/FrostWire-9.9.9.dmg.torrent?token=1#x");
    assertEquals("FrostWire-9.9.9.dmg", UpdateMediator.getInstallerFilename(m));
  }

  @Test
  void aQueryOrFragmentIsNotPartOfTheInstallerUrlName() {
    UpdateMessage m = update("9.9.9", null);
    m.setInstallerUrl("https://example.com/dl/FrostWire.dmg?mirror=2");
    assertEquals("FrostWire.dmg", UpdateMediator.getInstallerFilename(m));
  }

  @Test
  void aTorrentUrlThatEndsInASlashFallsBackToTheInstallerUrl() {
    UpdateMessage m = update("9.9.9", null);
    m.setTorrent("http://example.com/dl/");
    m.setInstallerUrl("https://example.com/dl/FrostWire.dmg");
    assertNull(UpdateMediator.getInstallerFilename(update("9.9.9", null)));
    assertEquals("FrostWire.dmg", UpdateMediator.getInstallerFilename(m));
  }

  @Test
  void aMessageWithNothingToDownloadHasNoInstallerName() {
    assertNull(UpdateMediator.getInstallerFilename(update("9.9.9", null)));
  }

  @Test
  void withoutAMessageNothingIsUpdatedOrDownloaded() {
    UpdateMediator mediator = UpdateMediator.instance();
    mediator.setUpdateMessage(null);
    assertFalse(mediator.isUpdated());
    assertFalse(mediator.isUpdateDownloaded());
    assertEquals("", mediator.getLatestVersion());
  }

  @Test
  void frostWireIsUpdatedWhenTheLatestVersionIsNotNewerThanThisOne() {
    UpdateMediator mediator = UpdateMediator.instance();
    mediator.setUpdateMessage(update(FrostWireUtils.getFrostWireVersion(), null));
    assertTrue(mediator.isUpdated());
    assertEquals(FrostWireUtils.getFrostWireVersion(), mediator.getLatestVersion());

    mediator.setUpdateMessage(update(versionAhead(1), null));
    assertFalse(mediator.isUpdated(), "a newer version is out");
    assertEquals(versionAhead(1), mediator.getLatestVersion());
  }

  @Test
  void aMessageWithoutAVersionNeverCountsAsUpdated() {
    UpdateMediator.instance().setUpdateMessage(update(null, null));
    assertFalse(UpdateMediator.instance().isUpdated());
    assertNull(UpdateMediator.instance().getLatestVersion());
  }

  @Test
  void anUpdateIsNotDownloadedUntilTheInstallerMd5WasSeen() {
    UpdateMessage m = update("9.9.9", null);
    m.setRemoteMD5("0123456789abcdef0123456789abcdef");
    UpdateMediator.instance().setUpdateMessage(m);
    assertFalse(
        UpdateMediator.instance().isUpdateDownloaded(),
        "no installer has been checked on this run");
  }

  @Test
  void checkingBuildsAndVersionsDecidesIfFrostWireIsOld() {
    assertTrue(
        UpdateManager.isFrostWireOld(update(null, "" + (FrostWireUtils.getBuildNumber() + 1))));
    assertFalse(UpdateManager.isFrostWireOld(update(null, "" + FrostWireUtils.getBuildNumber())));
    assertFalse(
        UpdateManager.isFrostWireOld(update(null, "" + (FrostWireUtils.getBuildNumber() - 1))));
    assertTrue(UpdateManager.isFrostWireOld(update("999.0.0", null)));
    assertTrue(UpdateManager.isFrostWireOld(update(versionAhead(1), null)));
    assertFalse(UpdateManager.isFrostWireOld(update(FrostWireUtils.getFrostWireVersion(), null)));
    assertFalse(UpdateManager.isFrostWireOld(update("0.0.1", null)));
    assertFalse(UpdateManager.isFrostWireOld(update(null, null)));
    assertFalse(UpdateManager.isFrostWireOld(update("seven", null)), "unreadable means not old");
    assertFalse(UpdateManager.isFrostWireOld(update("7.1", null)), "too few parts");
  }

  @Test
  void aNewerMajorOrReleaseNumberIsNewerEvenWithALowerServiceNumber() {
    String[] parts = FrostWireUtils.getFrostWireVersion().split("\\.");
    int major = Integer.parseInt(parts[0]);
    int release = Integer.parseInt(parts[1]);
    assertTrue(UpdateManager.isFrostWireOld(update((major + 1) + ".0.0", null)));
    assertTrue(UpdateManager.isFrostWireOld(update(major + "." + (release + 1) + ".0", null)));
    assertFalse(UpdateManager.isFrostWireOld(update((major - 1) + ".99.99", null)));
  }

  @Test
  void anUnreadableBuildFallsBackToTheVersion() {
    assertTrue(UpdateManager.isFrostWireOld(update("999.0.0", "abc")));
    assertFalse(UpdateManager.isFrostWireOld(update("0.0.1", "abc")));
  }
}
