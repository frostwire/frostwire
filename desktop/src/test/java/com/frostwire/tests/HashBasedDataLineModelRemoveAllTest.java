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

package com.frostwire.tests;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.limegroup.gnutella.gui.tables.AbstractDataLine;
import com.limegroup.gnutella.gui.tables.HashBasedDataLineModel;
import com.limegroup.gnutella.gui.tables.LimeTableColumn;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Removing a big selection from the Library's table looped remove(int), which remaps every row
 * after the removed one each time: seconds on the EDT and gigabytes of boxed row numbers (Icebase
 * #cc692a1a7966, macOS, EDT allocated 2119 MB in 2.5 s).
 */
class HashBasedDataLineModelRemoveAllTest {

  static final class Line extends AbstractDataLine<String> {
    @Override
    public int getColumnCount() {
      return 1;
    }

    @Override
    public LimeTableColumn getColumn(int col) {
      return new LimeTableColumn(col, "COL" + col, "col" + col, 50, true, String.class);
    }

    @Override
    public boolean isDynamic(int col) {
      return false;
    }

    @Override
    public boolean isClippable(int col) {
      return false;
    }

    @Override
    public Object getValueAt(int col) {
      return getInitializeObject();
    }

    @Override
    public int getTypeAheadColumn() {
      return 0;
    }

    @Override
    public void update() {}
  }

  static final class Model extends HashBasedDataLineModel<Line, String> {
    int reads;

    Model() {
      super(Line.class);
    }

    @Override
    public Line get(int row) {
      reads++;
      return super.get(row);
    }
  }

  private static Model modelWith(int rows) {
    Model model = new Model();
    for (int i = 0; i < rows; i++) {
      model.add("row" + i, i);
    }
    return model;
  }

  @Test
  void removesTheRowsAndKeepsEveryOtherRowFindable() {
    Model model = modelWith(100);
    List<String> gone = new ArrayList<>();
    for (int i = 0; i < 100; i += 3) {
      gone.add("row" + i);
    }

    int removed = model.removeAll(gone);

    assertEquals(gone.size(), removed);
    assertEquals(100 - gone.size(), model.getRowCount());
    for (int row = 0; row < model.getRowCount(); row++) {
      String init = model.get(row).getInitializeObject();
      assertEquals(row, model.getRow(init), "stale index for " + init);
    }
    for (String init : gone) {
      assertEquals(-1, model.getRow(init), init + " is still indexed");
    }
  }

  @Test
  void unknownObjectsAndDuplicatesAreIgnored() {
    Model model = modelWith(5);

    int removed = model.removeAll(List.of("row1", "row1", "nobody"));

    assertEquals(1, removed);
    assertEquals(4, model.getRowCount());
    assertEquals(0, model.removeAll(List.of()));
  }

  @Test
  void removingManyRowsRemapsOnceNotOncePerRow() {
    Model model = modelWith(2000);
    List<String> gone = new ArrayList<>();
    for (int i = 0; i < 2000; i += 2) {
      gone.add("row" + i);
    }
    model.reads = 0;

    model.removeAll(gone);

    // one remap walks the remaining rows once; a remap per removed row walks them 1000 times
    assertTrue(model.reads < 10 * 2000, "model rows read " + model.reads + " times");
  }
}
