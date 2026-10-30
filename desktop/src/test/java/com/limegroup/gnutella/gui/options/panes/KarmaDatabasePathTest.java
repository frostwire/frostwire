/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.limegroup.gnutella.gui.options.panes;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.frostwire.search.relay.LocalIndexTable;
import com.frostwire.search.relay.RelayConstants;
import java.io.File;
import org.junit.jupiter.api.Test;

/**
 * The Options panes must read the karma chain from the database the writer appends to ({@code
 * Initializer.startRelayStack}: {@code settingsDir/libtorrent/frostwire-shared-torrents.db}), or
 * they show "(no karma database)" or a score of 0 (frostwire/frostwire#1412).
 */
public class KarmaDatabasePathTest {

  private static final File SETTINGS_DIR = new File("settings-dir");

  /** The writer's path, spelled out as Initializer builds it. */
  private static File writerDbFile() {
    return new File(RelayConstants.relayHomeDir(SETTINGS_DIR), LocalIndexTable.DEFAULT_DB_NAME);
  }

  @Test
  public void writerDatabaseIsUnderTheRelayHomeDir() {
    assertEquals(
        new File(new File(SETTINGS_DIR, "libtorrent"), "frostwire-shared-torrents.db"),
        writerDbFile());
  }

  @Test
  public void karmaPaneReadsTheWriterDatabase() {
    assertEquals(writerDbFile(), KarmaSettingsPaneItem.karmaDbFile(SETTINGS_DIR));
  }

  @Test
  public void identityPaneReadsTheWriterDatabase() {
    assertEquals(writerDbFile(), IdentitySettingsPaneItem.karmaDbFile(SETTINGS_DIR));
  }
}
