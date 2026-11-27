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

package com.frostwire.gui.library;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.frostwire.gui.library.SortedListModel.SortOrder;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import javax.swing.DefaultListModel;
import javax.swing.event.ListDataEvent;
import javax.swing.event.ListDataListener;
import org.junit.jupiter.api.Test;

/** A sorted view over an unsorted list model, kept in step as the source changes. */
class SortedListModelTest {

  private static DefaultListModel<String> source(String... items) {
    DefaultListModel<String> m = new DefaultListModel<>();
    for (String s : items) {
      m.addElement(s);
    }
    return m;
  }

  private static List<Object> view(SortedListModel m) {
    List<Object> out = new ArrayList<>();
    for (int i = 0; i < m.getSize(); i++) {
      out.add(m.getElementAt(i));
    }
    return out;
  }

  private static final class Recorder implements ListDataListener {
    final List<String> events = new ArrayList<>();

    @Override
    public void intervalAdded(ListDataEvent e) {
      events.add("added " + e.getIndex0() + "-" + e.getIndex1());
    }

    @Override
    public void intervalRemoved(ListDataEvent e) {
      events.add("removed " + e.getIndex0() + "-" + e.getIndex1());
    }

    @Override
    public void contentsChanged(ListDataEvent e) {
      events.add("changed " + e.getIndex0() + "-" + e.getIndex1());
    }
  }

  @Test
  void theViewIsSortedAscendingByDefault() {
    SortedListModel m = new SortedListModel(source("pear", "apple", "fig"));
    assertEquals(List.of("apple", "fig", "pear"), view(m));
    assertEquals(3, m.getSize());
  }

  @Test
  void descendingReversesTheOrder() {
    SortedListModel m = new SortedListModel(source("pear", "apple", "fig"), SortOrder.DESCENDING);
    assertEquals(List.of("pear", "fig", "apple"), view(m));
  }

  @Test
  void unorderedKeepsTheSourceOrder() {
    SortedListModel m = new SortedListModel(source("pear", "apple", "fig"), SortOrder.UNORDERED);
    assertEquals(List.of("pear", "apple", "fig"), view(m));
  }

  @Test
  void aCustomComparatorDecidesTheOrder() {
    Comparator<Object> byLength = Comparator.comparingInt(o -> o.toString().length());
    SortedListModel m =
        new SortedListModel(source("ccc", "a", "bb"), SortOrder.ASCENDING, byLength);
    assertEquals(List.of("a", "bb", "ccc"), view(m));
  }

  @Test
  void indicesMapBetweenTheViewAndTheSource() {
    SortedListModel m = new SortedListModel(source("pear", "apple", "fig"));
    assertEquals(1, m.toUnsortedModelIndex(0));
    assertEquals(2, m.toUnsortedModelIndex(1));
    assertEquals(0, m.toUnsortedModelIndex(2));
    assertEquals(0, m.toSortedModelIndex(1));
    assertEquals(2, m.toSortedModelIndex(0));
    assertEquals(-1, m.toSortedModelIndex(42));
    assertArrayEquals(new int[] {0, 1}, m.toUnsortedModelIndices(new int[] {2, 0}));
    assertArrayEquals(new int[] {0, 2}, m.toSortedModelIndices(new int[] {0, 1}));
    assertThrows(IndexOutOfBoundsException.class, () -> m.getElementAt(3));
  }

  @Test
  void anElementAddedToTheSourceAppearsInItsSortedPlace() {
    DefaultListModel<String> src = source("pear", "fig");
    SortedListModel m = new SortedListModel(src);
    Recorder r = new Recorder();
    m.addListDataListener(r);
    src.addElement("apple");
    assertEquals(List.of("apple", "fig", "pear"), view(m));
    assertEquals(List.of("added 0-0"), r.events);
    src.add(0, "grape");
    assertEquals(List.of("apple", "fig", "grape", "pear"), view(m));
    assertEquals("grape", m.getElementAt(m.toSortedModelIndex(0)));
  }

  @Test
  void anElementRemovedFromTheSourceDisappearsFromTheView() {
    DefaultListModel<String> src = source("pear", "apple", "fig");
    SortedListModel m = new SortedListModel(src);
    Recorder r = new Recorder();
    m.addListDataListener(r);
    src.removeElement("fig");
    assertEquals(List.of("apple", "pear"), view(m));
    assertEquals(List.of("removed 1-1"), r.events);
    src.remove(0);
    assertEquals(List.of("apple"), view(m));
    assertEquals("apple", m.getElementAt(0));
    assertEquals(0, m.toUnsortedModelIndex(0));
  }

  @Test
  void aRangeRemovalDropsEachOfItsRows() {
    DefaultListModel<String> src = source("d", "c", "b", "a");
    SortedListModel m = new SortedListModel(src);
    src.removeRange(0, 1);
    assertEquals(List.of("a", "b"), view(m));
  }

  @Test
  void changedContentsAreResorted() {
    DefaultListModel<String> src = source("b", "c");
    SortedListModel m = new SortedListModel(src);
    Recorder r = new Recorder();
    m.addListDataListener(r);
    src.set(0, "z");
    assertEquals(List.of("c", "z"), view(m));
    assertEquals(List.of("changed 0-1"), r.events);
  }

  @Test
  void changingTheSortOrderResortsAndNotifies() {
    SortedListModel m = new SortedListModel(source("b", "c", "a"));
    Recorder r = new Recorder();
    m.addListDataListener(r);
    m.setSortOrder(SortOrder.DESCENDING);
    assertEquals(List.of("c", "b", "a"), view(m));
    m.setSortOrder(SortOrder.DESCENDING);
    assertEquals(1, r.events.size(), "same order again changes nothing");
    m.setSortOrder(SortOrder.UNORDERED);
    assertEquals(List.of("b", "c", "a"), view(m));
    assertEquals(2, r.events.size());
  }

  @Test
  void aNewComparatorResortsAndNullGoesBackToTheSourceOrder() {
    SortedListModel m = new SortedListModel(source("bb", "a", "ccc"));
    Recorder r = new Recorder();
    m.addListDataListener(r);
    m.setComparator(Comparator.comparingInt((Object o) -> o.toString().length()).reversed());
    assertEquals(List.of("ccc", "bb", "a"), view(m));
    m.setComparator(null);
    assertEquals(List.of("bb", "a", "ccc"), view(m));
    assertEquals(2, r.events.size());
  }
}
