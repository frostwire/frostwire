/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.tests;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.limegroup.gnutella.gui.tables.AbstractDataLine;
import com.limegroup.gnutella.gui.tables.BasicDataLineModel;
import com.limegroup.gnutella.gui.tables.LimeTableColumn;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * frostwire/frostwire#1570: a background resort threw a ClassCastException out of TimSort when one
 * column's sort keys were not all of one class. Rows whose key is a String, a number or missing (a
 * row not initialized yet) must sort without throwing, each kind together, in natural order within
 * a kind.
 */
class BasicDataLineModelMixedSortKeysTest {

  static final class Line extends AbstractDataLine<Object> {
    @Override
    public int getColumnCount() {
      return 1;
    }

    @Override
    public LimeTableColumn getColumn(int col) {
      return new LimeTableColumn(col, "COL" + col, "col" + col, 50, true, Object.class);
    }

    @Override
    public boolean isDynamic(int col) {
      return true;
    }

    @Override
    public boolean isClippable(int col) {
      return false;
    }

    @Override
    public Object getValueAt(int col) {
      // An uninitialized row has no key yet.
      return getInitializeObject();
    }

    @Override
    public int getTypeAheadColumn() {
      return 0;
    }

    @Override
    public void update() {}
  }

  static final class Model extends BasicDataLineModel<Line, Object> {
    Model() {
      super(Line.class);
    }

    void append(Object key) {
      Line line = new Line();
      if (key != null) {
        line.initialize(key);
      }
      add(line, getRowCount());
    }

    void sortNow(int ascending) {
      _activeColumn = 0;
      _ascending = ascending;
      doResort();
    }

    List<Object> keys() {
      List<Object> keys = new ArrayList<>();
      for (int i = 0; i < getRowCount(); i++) {
        keys.add(get(i).getInitializeObject());
      }
      return keys;
    }
  }

  @Test
  void rowsWithMixedAndMissingKeysSortWithoutThrowing() {
    Model model = new Model();
    // More than TimSort's 32-element threshold, as in the report (TimSort.sort's merge path).
    for (int i = 40; i > 0; i--) {
      model.append(i % 3 == 0 ? (Object) i : (i % 3 == 1 ? "s" + (char) ('a' + i % 26) : null));
    }

    model.sortNow(1);

    List<Object> keys = model.keys();
    assertEquals(40, keys.size());
    int nulls = 0;
    while (nulls < keys.size() && keys.get(nulls) == null) {
      nulls++;
    }
    assertEquals(13, nulls, "rows without a key sort first: " + keys);
    // java.lang.Integer before java.lang.String, each in its natural order.
    List<Object> rest = keys.subList(nulls, keys.size());
    for (int i = 1; i < rest.size(); i++) {
      Object a = rest.get(i - 1);
      Object b = rest.get(i);
      if (a instanceof Integer && b instanceof Integer) {
        assertEquals(true, (Integer) a <= (Integer) b, keys.toString());
      } else if (a instanceof String && b instanceof String) {
        assertEquals(true, ((String) a).compareTo((String) b) <= 0, keys.toString());
      } else {
        assertEquals(true, a instanceof Integer && b instanceof String, keys.toString());
      }
    }

    model.sortNow(-1);

    assertEquals(null, model.keys().get(39), "descending puts rows without a key last");
  }
}
