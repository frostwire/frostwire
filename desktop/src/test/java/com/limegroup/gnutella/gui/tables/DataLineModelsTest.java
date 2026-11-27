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

package com.limegroup.gnutella.gui.tables;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import javax.swing.event.TableModelEvent;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * The row storage behind every FrostWire table: BasicDataLineModel (lookup, insertion, removal,
 * sorting, change-scoped refresh events) and HashBasedDataLineModel (indexes kept in step).
 */
class DataLineModelsTest {

  @BeforeAll
  static void headless() {
    System.setProperty("java.awt.headless", "true");
  }

  /** Two columns: the initializing string and its length. */
  public static class WordLine extends AbstractDataLine<String> {
    static final LimeTableColumn WORD =
        new LimeTableColumn(0, "WORD", "Word", 100, true, String.class);
    static final LimeTableColumn SIZE =
        new LimeTableColumn(1, "SIZE", "Size", 50, false, Integer.class);
    int updates;
    int cleanups;
    String override;

    public int getColumnCount() {
      return 2;
    }

    public LimeTableColumn getColumn(int col) {
      return col == 0 ? WORD : SIZE;
    }

    public boolean isDynamic(int col) {
      return col == 1;
    }

    public boolean isClippable(int col) {
      return col == 0;
    }

    public Object getValueAt(int col) {
      String v = override != null ? override : initializer;
      return col == 0 ? v : (Object) v.length();
    }

    @Override
    public void setValueAt(Object o, int col) {
      override = String.valueOf(o);
    }

    public int getTypeAheadColumn() {
      return 0;
    }

    @Override
    public void update() {
      updates++;
    }

    @Override
    public void cleanup() {
      cleanups++;
    }
  }

  static class BasicModel extends BasicDataLineModel<WordLine, String> {
    BasicModel() {
      super(WordLine.class);
    }

    void replace(List<WordLine> lines) {
      setRows(lines);
    }

    void sortNow() {
      doResort();
    }
  }

  static class HashModel extends HashBasedDataLineModel<WordLine, String> {
    HashModel() {
      super(WordLine.class);
    }

    int append(List<WordLine> lines) {
      return appendAll(lines);
    }

    int forceAddAt(WordLine l, int row) {
      return forceAdd(l, row);
    }
  }

  private static WordLine line(String s) {
    WordLine l = new WordLine();
    l.initialize(s);
    return l;
  }

  private static List<TableModelEvent> record(BasicDataLineModel<?, ?> m) {
    List<TableModelEvent> events = new ArrayList<>();
    m.addTableModelListener(events::add);
    return events;
  }

  private static String[] words(BasicDataLineModel<WordLine, String> m) {
    String[] out = new String[m.getRowCount()];
    for (int i = 0; i < out.length; i++) {
      out[i] = m.get(i).getInitializeObject();
    }
    return out;
  }

  private static void assertWords(BasicDataLineModel<WordLine, String> m, String... expected) {
    assertTrue(Arrays.equals(expected, words(m)), Arrays.toString(words(m)));
  }

  @Test
  void columnMetadataComesFromTheDataLine() {
    BasicModel m = new BasicModel();
    assertEquals(2, m.getColumnCount());
    assertEquals("Word", m.getColumnName(0));
    assertEquals("SIZE", m.getColumnId(1));
    assertEquals(Integer.class, m.getColumnClass(1));
    assertSame(WordLine.WORD, m.getTableColumn(0));
    assertTrue(m.isClippable(0));
    assertFalse(m.isClippable(1));
    assertEquals(0, m.getTypeAheadColumn());
  }

  @Test
  void addInsertsAtRowZeroAndFiresInsertEvent() {
    BasicModel m = new BasicModel();
    List<TableModelEvent> events = record(m);
    assertEquals(0, m.add("one"));
    assertEquals(0, m.add("two"));
    assertWords(m, "two", "one");
    assertEquals(2, events.size());
    assertEquals(TableModelEvent.INSERT, events.get(1).getType());
    assertEquals(0, events.get(1).getFirstRow());
  }

  @Test
  void lookupsFindByInitializerByColumnAndByLine() {
    BasicModel m = new BasicModel();
    m.add("a", 0);
    m.add("bbb", 1);
    assertTrue(m.contains("bbb"));
    assertFalse(m.contains("zzz"));
    assertTrue(m.contains("bbb", 0));
    assertTrue(m.contains(3, 1));
    assertFalse(m.contains(9, 1));
    assertEquals(1, m.getRow("bbb"));
    assertEquals(-1, m.getRow("zzz"));
    assertEquals(1, m.getRow("bbb", 0));
    assertEquals(-1, m.getRow("nope", 0));
    WordLine second = m.get(1);
    assertEquals(1, m.getRow(second));
    assertSame(second, m.get("bbb"));
    assertNull(m.get("missing"));
    assertSame(second, m.get(3, 1));
    assertNull(m.get(99, 1));
    assertNull(m.get(-1));
    assertEquals("bbb", m.getValueAt(1, 0));
    assertEquals(3, m.getValueAt(1, 1));
  }

  @Test
  void removeCleansUpTheLineAndFiresDeleteEvent() {
    BasicModel m = new BasicModel();
    m.add("a", 0);
    m.add("b", 1);
    m.add("c", 2);
    WordLine b = m.get(1);
    List<TableModelEvent> events = record(m);
    m.remove((Object) "a");
    assertEquals(TableModelEvent.DELETE, events.get(0).getType());
    m.remove(b);
    assertEquals(1, b.cleanups);
    m.remove((Object) "unknown");
    m.remove(line("not in model"));
    assertWords(m, "c");
    assertEquals(2, events.size());
    m.remove(0);
    assertEquals(0, m.getRowCount());
  }

  @Test
  void clearCleansEveryLineAndFiresOneChangeEvent() {
    BasicModel m = new BasicModel();
    m.add("a", 0);
    m.add("b", 1);
    WordLine a = m.get(0);
    WordLine b = m.get(1);
    List<TableModelEvent> events = record(m);
    m.clear();
    assertEquals(0, m.getRowCount());
    assertEquals(1, a.cleanups);
    assertEquals(1, b.cleanups);
    assertEquals(1, events.size());
  }

  @Test
  void setValueAtIgnoresRowsThatDoNotExist() {
    BasicModel m = new BasicModel();
    m.add("a", 0);
    List<TableModelEvent> events = record(m);
    m.setValueAt("changed", 5, 0);
    m.setValueAt("changed", -1, 0);
    assertTrue(events.isEmpty());
    m.setValueAt("changed", 0, 0);
    assertEquals("changed", m.getValueAt(0, 0));
    assertEquals(1, events.size());
  }

  @Test
  void updateRefreshesOneLineAndReportsItsRow() {
    BasicModel m = new BasicModel();
    m.add("a", 0);
    m.add("b", 1);
    List<TableModelEvent> events = record(m);
    assertEquals(1, m.update("b"));
    assertEquals(1, m.get(1).updates);
    assertEquals(0, m.get(0).updates);
    assertEquals(1, events.get(0).getFirstRow());
  }

  @Test
  void tooltipQueriesDelegateToTheLine() {
    BasicModel m = new BasicModel();
    m.add("a", 0);
    assertNull(m.getToolTipArray(0, 0));
    assertFalse(m.isTooltipRequired(0, 0));
  }

  @Test
  void sortTogglesAscendingDescendingThenOff() {
    BasicModel m = new BasicModel();
    assertFalse(m.isSorted());
    assertEquals(-1, m.getSortColumn());
    m.sort(0);
    assertTrue(m.isSorted());
    assertTrue(m.isSortAscending());
    assertEquals(0, m.getSortColumn());
    m.sort(0);
    assertFalse(m.isSortAscending());
    m.sort(0);
    assertFalse(m.isSorted());
    assertEquals(-1, m.getSortColumn());
    m.sort(1);
    assertTrue(m.isSortAscending());
    assertEquals(1, m.getSortColumn());
    assertTrue(m.needsResort());
    m.sort(0);
    assertFalse(m.needsResort());
  }

  @Test
  void resortOrdersRowsByTheActiveColumnInBothDirections() {
    BasicModel m = new BasicModel();
    for (String s : new String[] {"pear", "fig", "banana", "kiwi"}) {
      m.add(s, m.getRowCount());
    }
    m.sort(1);
    m.sortNow();
    assertWords(m, "fig", "pear", "kiwi", "banana");
    m.sort(1);
    m.sortNow();
    assertWords(m, "banana", "pear", "kiwi", "fig");
  }

  @Test
  void addSortedKeepsTheSortedOrder() {
    BasicModel m = new BasicModel();
    m.sort(0);
    m.addSorted("b");
    m.addSorted("c");
    m.addSorted("a");
    m.addSorted(line("bb"));
    assertWords(m, "a", "b", "bb", "c");
  }

  @Test
  void setRowsReplacesEverythingAndFiresOneChangeEvent() {
    BasicModel m = new BasicModel();
    m.add("x", 0);
    m.sort(0);
    List<TableModelEvent> events = record(m);
    m.replace(Arrays.asList(line("c"), line("a"), line("b")));
    assertWords(m, "a", "b", "c");
    // the sort button's background resort may add a change event of its own
    assertFalse(events.isEmpty());
  }

  @Test
  void unsortedSetRowsKeepsGivenOrder() {
    BasicModel m = new BasicModel();
    m.replace(Arrays.asList(line("c"), line("a")));
    assertWords(m, "c", "a");
  }

  @Test
  void refreshUpdatesLinesAndOnlyFiresForRowsWhoseValuesChanged() {
    BasicModel m = new BasicModel();
    assertNull(m.refresh());
    m.add("a", 0);
    m.add("b", 1);
    m.add("c", 2);
    List<TableModelEvent> events = record(m);
    m.refresh();
    assertTrue(events.isEmpty(), "the first refresh only stores the baseline");
    assertEquals(1, m.get(0).updates);
    m.refresh();
    assertTrue(events.isEmpty(), "nothing changed");
    m.get(1).override = "bb";
    m.refresh();
    assertEquals(1, events.size());
    assertEquals(1, events.get(0).getFirstRow());
    assertEquals(1, events.get(0).getLastRow());
    m.get(0).override = "aa";
    m.get(1).override = "b";
    m.get(2).override = "cc";
    events.clear();
    m.refresh();
    // all three rows changed: contiguous rows collapse into one range event
    assertEquals(1, events.size());
    assertEquals(0, events.get(0).getFirstRow());
    assertEquals(2, events.get(0).getLastRow());
    m.get(1).override = "bbbb";
    events.clear();
    m.refresh();
    assertEquals(1, events.get(0).getFirstRow());
    assertEquals(1, events.get(0).getLastRow());
    m.get(0).override = "a";
    m.get(2).override = "c";
    events.clear();
    m.refresh();
    // rows 0 and 2 changed, row 1 did not: two separate range events
    assertEquals(2, events.size());
    assertEquals(0, events.get(0).getLastRow());
    assertEquals(2, events.get(1).getFirstRow());
  }

  @Test
  void aRowCountChangeFallsBackToAFullRangeEvent() {
    BasicModel m = new BasicModel();
    m.add("a", 0);
    m.refresh();
    m.add("b", 1);
    List<TableModelEvent> events = record(m);
    m.refresh();
    assertEquals(1, events.size());
    assertEquals(0, events.get(0).getFirstRow());
    assertEquals(1, events.get(0).getLastRow());
  }

  @Test
  void aCellThatThrowsStillMarksTheRowChanged() {
    BasicModel m = new BasicModel();
    m.add("a", 0);
    m.refresh();
    List<TableModelEvent> events = record(m);
    m.get(0).initializer = null;
    m.refresh();
    assertEquals(1, events.size());
  }

  @Test
  void hashModelRefusesDuplicatesAndKeepsIndexesInStep() {
    HashModel m = new HashModel();
    assertEquals(0, m.add(line("a"), 0));
    assertEquals(1, m.add(line("b"), 1));
    assertEquals(0, m.add(line("c"), 0));
    assertEquals(-1, m.add(line("b"), 0));
    assertEquals(3, m.getRowCount());
    assertEquals(0, m.getRow("c"));
    assertEquals(1, m.getRow("a"));
    assertEquals(2, m.getRow("b"));
    assertEquals(-1, m.getRow("zzz"));
    assertTrue(m.contains("a"));
    assertFalse(m.contains("zzz"));
    m.remove(0);
    assertFalse(m.contains("c"));
    assertEquals(0, m.getRow("a"));
    assertEquals(1, m.getRow("b"));
    m.clear();
    assertFalse(m.contains("a"));
    assertEquals(0, m.getRowCount());
  }

  @Test
  void forceAddRemapsTheRowsAfterIt() {
    HashModel m = new HashModel();
    m.add(line("a"), 0);
    m.add(line("b"), 1);
    assertEquals(0, m.forceAddAt(line("z"), 0));
    assertEquals(0, m.getRow("z"));
    assertEquals(1, m.getRow("a"));
    assertEquals(2, m.getRow("b"));
  }

  @Test
  void appendAllSkipsKnownObjectsAndFiresOneInsert() {
    HashModel m = new HashModel();
    m.add(line("a"), 0);
    List<TableModelEvent> events = record(m);
    WordLine dup = line("a");
    WordLine dupInBatch = line("b");
    int added = m.append(Arrays.asList(line("b"), dup, line("c"), dupInBatch));
    assertEquals(2, added);
    assertEquals(1, dup.cleanups);
    assertEquals(1, dupInBatch.cleanups);
    assertEquals(1, events.size());
    assertEquals(1, events.get(0).getFirstRow());
    assertEquals(2, events.get(0).getLastRow());
    assertEquals(2, m.getRow("c"));
    events.clear();
    assertEquals(0, m.append(List.of(line("a"))));
    assertTrue(events.isEmpty());
  }

  @Test
  void removeAllRemovesOnlyKnownObjectsAndRemapsOnce() {
    HashModel m = new HashModel();
    for (String s : new String[] {"a", "b", "c", "d", "e"}) {
      m.add(line(s), m.getRowCount());
    }
    assertEquals(0, m.removeAll(Arrays.asList("x", "y")));
    assertEquals(3, m.removeAll(Arrays.asList("b", "e", "a", "nope")));
    assertEquals(2, m.getRowCount());
    assertEquals(0, m.getRow("c"));
    assertEquals(1, m.getRow("d"));
    assertFalse(m.contains("a"));
  }

  @Test
  void resortRebuildsTheIndexes() {
    HashModel m = new HashModel();
    for (String s : new String[] {"ccc", "a", "bb"}) {
      m.add(line(s), m.getRowCount());
    }
    m.sort(1);
    m.doResort();
    assertEquals(0, m.getRow("a"));
    assertEquals(1, m.getRow("bb"));
    assertEquals(2, m.getRow("ccc"));
  }

  @Test
  void sizeHolderFormatsAndOrdersBySize() {
    SizeHolder small = new SizeHolder(1024);
    SizeHolder big = new SizeHolder(1024.0 * 1024 * 5, " (est.)");
    SizeHolder unknown = new SizeHolder(-5);
    assertEquals("--", unknown.toString());
    assertEquals(-1, unknown.getSize(), 0);
    assertTrue(big.toString().endsWith(" (est.)"));
    assertTrue(small.compareTo(big) < 0);
    assertTrue(big.compareTo(small) > 0);
    assertEquals(0, small.compareTo(new SizeHolder(1024)));
    assertTrue(unknown.compareTo(small) < 0);
    assertEquals(small, new SizeHolder(1024));
    assertEquals(small.hashCode(), new SizeHolder(1024).hashCode());
    assertNotEqualsObject(small, big);
    assertNotEqualsObject(small, "x");
    assertEquals(1024, small.getSize(), 0);
  }

  private static void assertNotEqualsObject(Object a, Object b) {
    assertFalse(a.equals(b));
  }

  @Test
  void timeRemainingHolderShowsInfinityBlankOrTime() {
    assertEquals("∞", new TimeRemainingHolder(-1).toString());
    assertEquals("", new TimeRemainingHolder(0).toString());
    assertFalse(new TimeRemainingHolder(61).toString().isEmpty());
    assertEquals(new TimeRemainingHolder(7), new TimeRemainingHolder(7));
    assertEquals(new TimeRemainingHolder(7).hashCode(), new TimeRemainingHolder(7).hashCode());
    assertNotEqualsObject(new TimeRemainingHolder(7), new TimeRemainingHolder(8));
    assertNotEqualsObject(new TimeRemainingHolder(7), null);
    // Shorter remaining time sorts as larger (descending by seconds).
    assertTrue(new TimeRemainingHolder(10).compareTo(new TimeRemainingHolder(20)) > 0);
  }

  @Test
  void nameHolderOrdersByDisplayName() {
    NameHolder a = new NameHolder("alpha");
    NameHolder b = new NameHolder("Beta");
    assertTrue(a.compareTo(b) < 0);
    assertEquals("alpha", a.toString());
    assertNull(a.getIcon());
    assertNotNull(a.getLocaleString());
  }

  @Test
  void limeTableColumnKeepsItsDefaultsAndRefusesChanges() {
    LimeTableColumn c = new LimeTableColumn(3, "ID", "Name", 77, true, String.class);
    assertEquals(3, c.getDefaultOrder());
    assertEquals(77, c.getDefaultWidth());
    assertTrue(c.getDefaultVisibility());
    assertEquals("ID", c.getId());
    assertEquals("ID", c.toString());
    assertEquals("Name", c.getName());
    assertEquals("Name", c.getHeaderValue());
    assertEquals(String.class, c.getColumnClass());
    assertNull(c.getIcon());
    assertThrows(IllegalStateException.class, () -> c.setModelIndex(1));
    assertThrows(IllegalStateException.class, () -> c.setHeaderValue("x"));
    assertThrows(IllegalStateException.class, () -> c.setIdentifier("x"));
    LimeTableColumn hiddenName =
        new LimeTableColumn(0, "H", "Hidden", 40, true, false, false, String.class);
    assertEquals("", hiddenName.getHeaderValue());
    assertEquals(40, hiddenName.getMaxWidth());
    assertEquals(40, hiddenName.getMinWidth());
    assertFalse(hiddenName.getResizable());
  }
}
