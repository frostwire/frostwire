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

package com.frostwire.gui.components.transfers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.limegroup.gnutella.settings.UISettings;
import java.awt.Component;
import java.awt.Container;
import java.awt.event.MouseAdapter;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JToggleButton;
import javax.swing.LookAndFeel;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.plaf.metal.MetalLookAndFeel;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** The transfer details strip under the Transfers table: its five tabs and the remembered one. */
class TransferDetailComponentTest {

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

  private static void collect(Container parent, List<JToggleButton> out) {
    for (Component c : parent.getComponents()) {
      if (c instanceof JToggleButton button) {
        out.add(button);
      }
      if (c instanceof Container container) {
        collect(container, out);
      }
    }
  }

  @Test
  void theFiveTabsSwitchTheDetailAndRememberTheLastOne() throws Exception {
    String remembered = UISettings.LAST_SELECTED_TRANSFER_DETAIL_JPANEL.getValue();
    try {
      TransferDetailComponent[] holder = new TransferDetailComponent[1];
      SwingUtilities.invokeAndWait(
          () -> holder[0] = new TransferDetailComponent(new MouseAdapter() {}));
      // the panels are built by a task queued on the EDT: let it run
      SwingUtilities.invokeAndWait(() -> {});
      List<JToggleButton> buttons = new ArrayList<>();
      collect(holder[0], buttons);
      assertTrue(buttons.size() >= 5, "the five tabs, and toggles inside the panels");
      String[] names = {"General", "Files", "Pieces", "Trackers", "Peers"};
      String[] cards = {"G", "F", "P", "T", "p"};
      for (int i = 0; i < names.length; i++) {
        int index = i;
        JToggleButton button =
            buttons.stream()
                .filter(b -> b.getText().equals(names[index]))
                .findFirst()
                .orElseThrow();
        SwingUtilities.invokeAndWait(button::doClick);
        assertEquals(cards[i], UISettings.LAST_SELECTED_TRANSFER_DETAIL_JPANEL.getValue());
      }
      assertNotNull(holder[0].getLayout());
    } finally {
      UISettings.LAST_SELECTED_TRANSFER_DETAIL_JPANEL.setValue(remembered);
    }
  }

  @Test
  void aPanelWithoutASelectedTransferIgnoresUpdates() throws Exception {
    TransferDetailComponent[] holder = new TransferDetailComponent[1];
    SwingUtilities.invokeAndWait(
        () -> holder[0] = new TransferDetailComponent(new MouseAdapter() {}));
    SwingUtilities.invokeAndWait(() -> {});
    holder[0].updateData(null);
    assertTrue(holder[0].getComponentCount() > 0);
  }
}
