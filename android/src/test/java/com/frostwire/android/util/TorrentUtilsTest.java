/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.android.util;

import static org.junit.Assert.assertFalse;

import android.app.Application;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/**
 * A file that can't be seeded is reported as not seeded instead of throwing. (Seeding that works,
 * with a real engine, is in TorrentUtilsSeedingTest.)
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, application = Application.class)
public class TorrentUtilsTest {

  @Rule public TemporaryFolder folder = new TemporaryFolder();

  @Test
  public void aMissingFileIsNotSeeded() {
    assertFalse(
        TorrentUtils.seedFile(new File(folder.getRoot(), "missing.mp3"), "Missing", null, null));
  }

  @Test
  public void aFileThatCannotBeHashedIsReportedAsNotSeeded() throws IOException {
    File file = folder.newFile("song.mp3");
    Files.write(file.toPath(), new byte[] {1, 2, 3});
    // Without a transfer manager, and an engine that is not running, the seed can't be completed;
    // the failure is contained.
    assertFalse(TorrentUtils.seedFile(file, null, null, null));
    assertFalse(TorrentUtils.seedFile(file, "A name", null, null));
  }
}
