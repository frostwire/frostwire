/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.tests;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.limegroup.gnutella.gui.tables.AbstractDataLine;
import com.limegroup.gnutella.gui.tables.BasicDataLineModel;
import com.limegroup.gnutella.gui.tables.LimeTableColumn;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/**
 * The background resort must not hold the list lock while it reads sort keys or compares them: the
 * EDT takes that lock to paint, and a key can be costly (the Transfers FILE column builds an icon).
 * The result must stay what an in-place stable sort gives.
 */
class BasicDataLineModelSortLockTest {

  /** Sort key that records whether it is compared while the model's list lock is held. */
  static final class Key implements Comparable<Key> {
    final String value;
    final Model model;

    Key(String value, Model model) {
      this.value = value;
      this.model = model;
    }

    @Override
    public int compareTo(Key other) {
      model.compares.incrementAndGet();
      if (Thread.holdsLock(model.lock())) {
        model.comparedUnderLock.set(true);
      }
      Runnable hook = model.onCompare;
      if (hook != null) {
        model.onCompare = null;
        hook.run();
      }
      return value.compareTo(other.value);
    }
  }

  static final class Line extends AbstractDataLine<String> {
    Model model;

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
      return true;
    }

    @Override
    public boolean isClippable(int col) {
      return false;
    }

    @Override
    public Object getValueAt(int col) {
      if (Thread.holdsLock(model.lock())) {
        model.keyReadUnderLock.set(true);
      }
      // Only the first character sorts, so equal keys exercise stability.
      return new Key(getInitializeObject().substring(0, 1), model);
    }

    @Override
    public int getTypeAheadColumn() {
      return 0;
    }

    @Override
    public void update() {}
  }

  static final class Model extends BasicDataLineModel<Line, String> {
    final AtomicBoolean comparedUnderLock = new AtomicBoolean();
    final AtomicBoolean keyReadUnderLock = new AtomicBoolean();
    final AtomicInteger compares = new AtomicInteger();
    volatile Runnable onCompare;

    Model() {
      super(Line.class);
    }

    Object lock() {
      return getListLock();
    }

    void append(String value) {
      Line line = new Line();
      line.model = this;
      line.initialize(value);
      add(line, getRowCount());
    }

    void sortNow(int ascending) {
      _activeColumn = 0;
      _ascending = ascending;
      doResort();
    }

    List<String> rows() {
      List<String> rows = new ArrayList<>();
      for (int i = 0; i < getRowCount(); i++) {
        rows.add(get(i).getInitializeObject());
      }
      return rows;
    }
  }

  @Test
  void sortReadsAndComparesKeysWithoutHoldingTheListLock() {
    Model model = new Model();
    for (String value : new String[] {"c1", "a1", "d1", "b1", "a2", "c2"}) {
      model.append(value);
    }

    model.sortNow(1);

    assertTrue(model.compares.get() > 0);
    assertEquals(false, model.comparedUnderLock.get(), "compared keys under the list lock");
    assertEquals(false, model.keyReadUnderLock.get(), "read sort keys under the list lock");
    assertEquals(List.of("a1", "a2", "b1", "c1", "c2", "d1"), model.rows());
  }

  @Test
  void anotherThreadCanAddAndRemoveRowsWhileTheSortRuns() throws Exception {
    Model model = new Model();
    for (String value : new String[] {"d1", "b1", "e1", "a1", "c1"}) {
      model.append(value);
    }
    model.onCompare =
        () ->
            CompletableFuture.runAsync(
                    () -> {
                      model.remove(2); // e1
                      model.append("c2");
                    })
                .orTimeout(5, TimeUnit.SECONDS)
                .join();

    model.sortNow(1);

    assertEquals(List.of("a1", "b1", "c1", "c2", "d1"), model.rows());
  }

  @Test
  void resultMatchesAStableInPlaceSort() {
    Random random = new Random(1337);
    for (int ascending : new int[] {1, -1}) {
      Model model = new Model();
      List<String> values = new ArrayList<>();
      for (int i = 0; i < 500; i++) {
        String value = (char) ('a' + random.nextInt(10)) + Integer.toString(i);
        values.add(value);
        model.append(value);
      }
      List<String> expected = new ArrayList<>(values);
      Comparator<String> firstChar = Comparator.comparing(v -> v.substring(0, 1));
      expected.sort(ascending == 1 ? firstChar : firstChar.reversed());

      model.sortNow(ascending);

      assertEquals(expected, model.rows());
    }
  }
}
