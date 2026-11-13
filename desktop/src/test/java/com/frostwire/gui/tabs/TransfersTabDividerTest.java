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

package com.frostwire.gui.tabs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import javax.swing.JPanel;
import javax.swing.JSplitPane;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;

/**
 * The Transfers tab is added to the window the first time it is shown, and the details of a
 * pre-selected transfer opened before the tab was laid out: the parent's height was 0, so the
 * divider went to 0 and the details covered the transfers (frostwire/frostwire#1548).
 */
class TransfersTabDividerTest {

  @Test
  void theDividerWaitsForTheParentToHaveAHeight() throws Exception {
    List<Integer> placed = new ArrayList<>();
    JSplitPane[] splitter = new JSplitPane[1];
    JPanel[] parent = new JPanel[1];
    SwingUtilities.invokeAndWait(
        () -> {
          parent[0] = new JPanel(null);
          splitter[0] = new JSplitPane(JSplitPane.VERTICAL_SPLIT, new JPanel(), new JPanel());
          parent[0].add(splitter[0]);
          TransfersTab.placeDetailsDivider(splitter[0], parent[0], placed::add);
        });

    assertTrue(placed.isEmpty(), "placed before layout at " + placed);
    assertTrue(splitter[0].getDividerLocation() != 0, "the divider went to 0 before layout");

    SwingUtilities.invokeAndWait(() -> parent[0].setSize(1200, 900));
    SwingUtilities.invokeAndWait(() -> {}); // let the resize event run

    assertEquals(List.of(400), placed, "900 px tall: all but 500 px for the transfers");
    assertEquals(400, splitter[0].getDividerLocation());
  }

  @Test
  void aLaidOutParentPlacesTheDividerAtOnce() throws Exception {
    List<Integer> placed = new ArrayList<>();
    SwingUtilities.invokeAndWait(
        () -> {
          JPanel parent = new JPanel(null);
          parent.setSize(800, 600);
          JSplitPane splitter =
              new JSplitPane(JSplitPane.VERTICAL_SPLIT, new JPanel(), new JPanel());
          parent.add(splitter);
          TransfersTab.placeDetailsDivider(splitter, parent, placed::add);
        });

    assertEquals(List.of(400), placed, "600 px tall: two thirds for the transfers");
  }

  @Test
  void detailsGetAtMost500PixelsOnTallWindows() {
    assertEquals(400, TransfersTab.detailsDividerLocation(600));
    assertEquals(533, TransfersTab.detailsDividerLocation(800));
    assertEquals(700, TransfersTab.detailsDividerLocation(1200));
  }
}
