/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.gui.components.transfers;

import com.limegroup.gnutella.gui.tables.BasicDataLineModel;
import com.limegroup.gnutella.gui.tables.DataLine;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/** Brings a table model in line with a fresh snapshot of items, matching rows by a stable key. */
final class KeyedRowSync {
  private KeyedRowSync() {}

  /**
   * Makes {@code model} show exactly {@code items}: rows whose key is still present get the fresh
   * item (so their values change), new keys are added, keys that disappeared are removed, and an
   * active sort is reapplied. Matching by key keeps each row tied to the same entity when the
   * snapshot's order or size changes. Call on the EDT.
   */
  static <T extends DataLine<E>, E, K> void sync(
      BasicDataLineModel<T, E> model, List<E> items, Function<E, K> key) {
    Map<K, E> incoming = new LinkedHashMap<>();
    for (E item : items) {
      incoming.put(key.apply(item), item);
    }
    for (int row = model.getRowCount() - 1; row >= 0; row--) {
      T line = model.get(row);
      E current = line.getInitializeObject();
      E fresh = current == null ? null : incoming.remove(key.apply(current));
      if (fresh == null) {
        model.remove(row);
      } else {
        line.initialize(fresh);
        model.fireTableRowsUpdated(row, row);
      }
    }
    for (E item : incoming.values()) {
      model.add(item);
    }
    if (model.isSorted()) {
      model.resort();
    }
  }
}
