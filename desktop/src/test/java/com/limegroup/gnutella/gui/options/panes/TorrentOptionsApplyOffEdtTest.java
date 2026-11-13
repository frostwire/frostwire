/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     This program is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     This program is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.limegroup.gnutella.gui.options.panes;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/**
 * The Options window's OK button applied the BitTorrent pages on the EDT, and every engine call
 * goes to libtorrent's session, which can take seconds to answer on a starved machine (Icebase
 * #f9e9e1f3a2e0: is_dht_running; frostwire/frostwire#1546). Applying now only reads what the user
 * chose on the EDT and hands the engine's work to a background queue.
 *
 * <p>A source test, as the pane items cannot be built headless (ThemeMediator reads the screen
 * size).
 */
class TorrentOptionsApplyOffEdtTest {

  /** The text of applyOptions(), without whitespace. */
  private static String applyOptions(String file) throws IOException {
    String source =
        Files.readString(
                Path.of("src/main/java/com/limegroup/gnutella/gui/options/panes/" + file + ".java"))
            .replaceAll("\\s+", "");
    int start = source.indexOf("publicbooleanapplyOptions(){");
    assertTrue(start >= 0, "applyOptions() not found in " + file);
    int end = source.indexOf("returnfalse;}", start);
    assertTrue(end > start, "end of applyOptions() not found in " + file);
    return source.substring(start, end);
  }

  private static void assertEngineOnlyInTheBackground(String file) throws IOException {
    String body = applyOptions(file);
    int queued = body.indexOf("background.accept(");
    assertTrue(queued >= 0, file + ".applyOptions() hands nothing to the background queue");
    assertFalse(
        body.substring(0, queued).contains("BTEngine"),
        file + ".applyOptions() calls the engine on the EDT before queueing");
    assertFalse(body.contains("isDhtRunning"), file + " asks the session if DHT runs on the EDT");
  }

  @Test
  void theConnectionPageAppliesItsEngineSettingsInTheBackground() throws IOException {
    assertEngineOnlyInTheBackground("TorrentConnectionPaneItem");
  }

  @Test
  void theSpeedPageAppliesItsEngineSettingsInTheBackground() throws IOException {
    assertEngineOnlyInTheBackground("TorrentGlobalSpeedPaneItem");
  }
}
