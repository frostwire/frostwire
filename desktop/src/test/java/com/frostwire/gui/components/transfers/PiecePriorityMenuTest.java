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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.frostwire.bittorrent.PiecePriorities;
import com.frostwire.jlibtorrent.Priority;
import java.awt.Component;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.JRadioButtonMenuItem;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;

/** The Pieces tab's priority menu, the priority logic is tested in common's PiecePrioritiesTest. */
class PiecePriorityMenuTest {

  @Test
  void menuOffersEveryPriorityWithTheCurrentOneSelected() throws Exception {
    List<Priority> chosen = new ArrayList<>();
    Priority[] choices = PiecePriorities.choices();
    SwingUtilities.invokeAndWait(
        () -> {
          JPopupMenu menu =
              PiecePriorityMenu.createMenu(
                  new PiecePriorities.PieceState(7, false, Priority.FOUR), chosen::add);
          List<JRadioButtonMenuItem> items = priorityItems(menu);
          assertEquals(choices.length, items.size());
          for (int i = 0; i < items.size(); i++) {
            JRadioButtonMenuItem item = items.get(i);
            assertTrue(item.isEnabled());
            assertEquals(choices[i] == Priority.FOUR, item.isSelected());
          }
          items.get(0).doClick();
          items.get(3).doClick();
        });
    assertEquals(List.of(Priority.IGNORE, Priority.SEVEN), chosen);
  }

  @Test
  void aDownloadedPieceOffersNoPriorityToChange() throws Exception {
    List<Priority> chosen = new ArrayList<>();
    SwingUtilities.invokeAndWait(
        () -> {
          JPopupMenu menu =
              PiecePriorityMenu.createMenu(
                  new PiecePriorities.PieceState(7, true, Priority.FOUR), chosen::add);
          List<JRadioButtonMenuItem> items = priorityItems(menu);
          assertEquals(PiecePriorities.choices().length, items.size());
          for (JRadioButtonMenuItem item : items) {
            assertFalse(item.isEnabled(), item.getText() + " is enabled on a downloaded piece");
            item.doClick();
          }
        });
    assertTrue(chosen.isEmpty());
  }

  @Test
  void labelsFollowLibtorrentsLevels() {
    // 4 is libtorrent's default piece priority, 1 (jlibtorrent's NORMAL) its lowest
    assertEquals(PiecePriorityMenu.label(Priority.FOUR), PiecePriorityMenu.describe(Priority.FOUR));
    assertEquals("Normal", PiecePriorityMenu.label(Priority.FOUR));
    assertEquals("Low", PiecePriorityMenu.label(Priority.NORMAL));
    assertEquals("High", PiecePriorityMenu.label(Priority.SEVEN));
    assertEquals("Don't Download", PiecePriorityMenu.label(Priority.IGNORE));
    assertEquals("Unknown", PiecePriorityMenu.describe(null));
    assertEquals("High (5)", PiecePriorityMenu.describe(Priority.FIVE));
    assertEquals("Low (2)", PiecePriorityMenu.describe(Priority.TWO));
  }

  private static List<JRadioButtonMenuItem> priorityItems(JPopupMenu menu) {
    List<JRadioButtonMenuItem> items = new ArrayList<>();
    JMenuItem title = null;
    for (Component component : menu.getComponents()) {
      if (component instanceof JRadioButtonMenuItem) {
        items.add((JRadioButtonMenuItem) component);
      } else if (component instanceof JMenuItem && title == null) {
        title = (JMenuItem) component;
      }
    }
    assertNotNull(title, "the menu names the piece");
    assertFalse(title.isEnabled());
    return items;
  }
}
