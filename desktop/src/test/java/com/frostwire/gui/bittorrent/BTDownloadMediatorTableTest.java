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

package com.frostwire.gui.bittorrent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import javax.swing.LookAndFeel;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.plaf.metal.MetalLookAndFeel;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** The Transfers window's table, built for real without a display. */
class BTDownloadMediatorTableTest {

  private static LookAndFeel previous;

  @BeforeAll
  static void metal() throws Exception {
    System.setProperty("java.awt.headless", "true");
    previous = UIManager.getLookAndFeel();
    UIManager.setLookAndFeel(new MetalLookAndFeel());
  }

  @AfterAll
  static void restore() throws Exception {
    UIManager.setLookAndFeel(previous);
  }

  private static BTDownloadMediator mediator() throws Exception {
    BTDownloadMediator[] holder = new BTDownloadMediator[1];
    SwingUtilities.invokeAndWait(() -> holder[0] = BTDownloadMediator.instance());
    return holder[0];
  }

  @Test
  void theTransfersTableIsASingletonWithItsActions() throws Exception {
    BTDownloadMediator m = mediator();
    assertSame(m, mediator());
    assertNotNull(m.getComponent());
    assertEquals(7, m.getActions().length);
    assertEquals(0, m.getDownloads().size());
    assertEquals(0, m.getSelectedDownloaders().length);
  }

  @Test
  void aTransferThatIsNotInTheTableIsNotBeingDownloaded() throws Exception {
    BTDownloadMediator m = mediator();
    assertFalse(m.isDownloading("deadbeef"));
    assertFalse(m.isActiveTorrentDownload(null));
    assertFalse(m.isActiveTorrentDownload(new java.io.File("/no/such/file")));
    assertTrue(m.getTotalDownloads() >= 0);
  }
}
