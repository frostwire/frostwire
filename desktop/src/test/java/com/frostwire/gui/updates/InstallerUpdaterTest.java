/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.gui.updates;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.limegroup.gnutella.settings.UpdateSettings;
import java.io.File;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * The part of the silent installer download that runs without libtorrent or a network: whether it
 * runs at all, and which files in the updates folder it throws away. The folder is under the test
 * JVM's own home, never the developer's.
 */
class InstallerUpdaterTest {
  private boolean automatic;

  @BeforeEach
  void prepare() throws Exception {
    Assumptions.assumeTrue(
        UpdateSettings.UPDATES_DIR.getAbsolutePath().contains("test-home"),
        "the updates folder must be the test JVM's own: " + UpdateSettings.UPDATES_DIR);
    automatic = UpdateSettings.AUTOMATIC_INSTALLER_DOWNLOAD.getValue();
    emptyTheUpdatesFolder();
    Files.createDirectories(UpdateSettings.UPDATES_DIR.toPath());
  }

  @AfterEach
  void restore() throws Exception {
    UpdateSettings.AUTOMATIC_INSTALLER_DOWNLOAD.setValue(automatic);
    emptyTheUpdatesFolder();
  }

  private static void emptyTheUpdatesFolder() {
    File[] files = UpdateSettings.UPDATES_DIR.listFiles();
    if (files != null) {
      for (File f : files) {
        f.delete();
      }
    }
  }

  private static String md5(String content) throws Exception {
    byte[] digest =
        MessageDigest.getInstance("MD5").digest(content.getBytes(StandardCharsets.UTF_8));
    return String.format("%032x", new BigInteger(1, digest));
  }

  private static File write(String name, String content) throws Exception {
    File f = new File(UpdateSettings.UPDATES_DIR, name);
    Files.writeString(f.toPath(), content);
    return f;
  }

  private static UpdateMessage updateWithMd5(String md5) {
    UpdateMessage m = new UpdateMessage("update", "text");
    m.setVersion("9.9.9");
    m.setRemoteMD5(md5);
    return m;
  }

  @Test
  void withAutomaticDownloadsOffAndNoForceNothingHappens() throws Exception {
    UpdateSettings.AUTOMATIC_INSTALLER_DOWNLOAD.setValue(false);
    File stale = write("stale.bin", "old installer");
    new InstallerUpdater(updateWithMd5(md5("new installer")), false).run();
    assertTrue(stale.exists(), "the updates folder was left alone");
    assertFalse(InstallerUpdater.isDownloadingUpdate());
  }

  @Test
  void aForcedCheckRunsEvenWithAutomaticDownloadsOff() throws Exception {
    UpdateSettings.AUTOMATIC_INSTALLER_DOWNLOAD.setValue(false);
    File stale = write("stale.bin", "old installer");
    new InstallerUpdater(updateWithMd5(md5("new installer")), true).run();
    assertFalse(stale.exists());
  }

  @Test
  void filesThatAreNotTheAnnouncedInstallerAreRemovedAndTheRightOneKept() throws Exception {
    UpdateSettings.AUTOMATIC_INSTALLER_DOWNLOAD.setValue(true);
    File stale = write("stale.bin", "old installer");
    File half = write("half.part", "new inst");
    File installer = write("installer.bin", "new installer");
    new InstallerUpdater(updateWithMd5(md5("new installer")), false).run();
    assertFalse(stale.exists());
    assertFalse(half.exists());
    assertTrue(installer.exists(), "the file with the announced MD5 is the update");
    assertEquals("new installer", Files.readString(installer.toPath()));
  }

  @Test
  void contentLengthIsReadWhateverTheCaseOfTheHeaderName() {
    assertEquals(1234, InstallerUpdater.contentLength(Map.of("Content-Length", List.of("1234"))));
    assertEquals(1234, InstallerUpdater.contentLength(Map.of("content-length", List.of("1234"))));
    assertEquals(1234, InstallerUpdater.contentLength(Map.of("CONTENT-LENGTH", List.of(" 1234 "))));
  }

  @Test
  void anAbsentOrBrokenContentLengthIsZero() {
    assertEquals(0, InstallerUpdater.contentLength(null));
    assertEquals(0, InstallerUpdater.contentLength(Map.of()));
    assertEquals(0, InstallerUpdater.contentLength(Map.of("Content-Type", List.of("text/html"))));
    assertEquals(0, InstallerUpdater.contentLength(Map.of("Content-Length", List.of())));
    assertEquals(0, InstallerUpdater.contentLength(Map.of("Content-Length", List.of("many"))));
    assertEquals(0, InstallerUpdater.contentLength(Map.of("Content-Length", List.of("-5"))));
  }

  @Test
  void progressIsAPercentageThatNeverDividesByZero() {
    assertEquals(0, InstallerUpdater.progressPercent(0, 200));
    assertEquals(50, InstallerUpdater.progressPercent(100, 200));
    assertEquals(100, InstallerUpdater.progressPercent(200, 200));
    assertEquals(100, InstallerUpdater.progressPercent(300, 200), "never above 100");
    assertEquals(0, InstallerUpdater.progressPercent(4096, 0), "unknown size");
    assertEquals(
        99, InstallerUpdater.progressPercent(Long.MAX_VALUE / 100 - 1, Long.MAX_VALUE / 100));
  }

  @Test
  void nothingIsDownloadingWhenTheMessageNamesNoSource() throws Exception {
    UpdateSettings.AUTOMATIC_INSTALLER_DOWNLOAD.setValue(true);
    new InstallerUpdater(updateWithMd5(md5("x")), false).run();
    assertFalse(InstallerUpdater.isDownloadingUpdate());
  }

  @Test
  void anUpdatesFolderThatDoesNotExistIsFine() throws Exception {
    UpdateSettings.AUTOMATIC_INSTALLER_DOWNLOAD.setValue(true);
    Files.delete(UpdateSettings.UPDATES_DIR.toPath());
    new InstallerUpdater(updateWithMd5(md5("x")), false).run();
    assertFalse(InstallerUpdater.isDownloadingUpdate());
  }
}
