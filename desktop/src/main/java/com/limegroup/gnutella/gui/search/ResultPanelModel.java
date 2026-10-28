/*
 *     Created by Angel Leon (@gubatron), Alden Torres (aldenml)
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

package com.limegroup.gnutella.gui.search;

import com.limegroup.gnutella.gui.tables.BasicDataLineModel;
import com.limegroup.gnutella.gui.tables.LimeTableColumn;
import java.util.HashMap;
import java.util.Map;

/**
 * Model for search results.
 *
 * <p>Ensures that if new lines are added and they are similar to old lines, that the new lines are
 * added as extra information to the existing lines, instead of as brand new lines.
 */
class ResultPanelModel extends BasicDataLineModel<SearchResultDataLine, UISearchResult> {
  /** */
  /** The columns. */
  private final SearchTableColumns COLUMNS = new SearchTableColumns();

  /** HashMap for quick access to indexes based on SHA1 info. */
  private final Map<String, Integer> _indexes = new HashMap<>();

  private int _numResults;

  /** Constructs a new ResultPanelModel with the given MetadataModel. */
  ResultPanelModel() {
    super(SearchResultDataLine.class);
  }

  /** Gets the columns used by this model. */
  SearchTableColumns getColumns() {
    return COLUMNS;
  }

  /** Creates a new TableLine. */
  public SearchResultDataLine createDataLine() {
    return new SearchResultDataLine(COLUMNS);
  }

  /** Gets the column at the specified index. */
  public LimeTableColumn getTableColumn(int idx) {
    return COLUMNS.getColumn(idx);
  }

  /**
   * Sorts the Type column by file extension (its value is an icon), the count column by seeds, and
   * the size column by the raw byte count.
   */
  @Override
  protected Object sortKey(SearchResultDataLine line, int column) {
    if (column == SearchTableColumns.TYPE_IDX) {
      return line.getExtension();
    }
    if (column == SearchTableColumns.COUNT_IDX) {
      Integer seeds = line.getSeedsAsInteger();
      return seeds == null ? 0 : seeds;
    }
    if (column == SearchTableColumns.SIZE_IDX) {
      return line.getSize();
    }
    return super.sortKey(line, column);
  }

  /**
   * Overrides the default remove to remove the index from the HashMap.
   *
   * @param row the index of the row to remove.
   */
  public void remove(int row) {
    String sha1 = getHash(row);
    if (sha1 != null) {
      synchronized (getListLock()) {
        _indexes.remove(sha1);
      }
    }
    super.remove(row);
    _numResults -= 1;
    synchronized (getListLock()) {
      remapIndexes(row);
    }
  }

  /** Override default so new ones get added to the end */
  public int add(UISearchResult o) {
    return add(o, getRowCount());
  }

  /** Override to fix compile error on OSX. */
  public int add(SearchResultDataLine dl) {
    return super.add(dl);
  }

  /**
   * Override to not iterate through each result. Repaints only rows whose displayed values changed
   * since unaffected rows cost a full-table paint.
   */
  public Object refresh() {
    if (getRowCount() == 0) {
      return null;
    }
    fireChangedRowsSinceLastRefresh();
    return null;
  }

  /** Maintains the indexes HashMap and MetadataModel. */
  public int add(SearchResultDataLine tl, int row) {
    _numResults += 1;
    String sha1 = tl.getHash();
    if (sha1 != null) {
      synchronized (getListLock()) {
        _indexes.put(sha1, row);
      }
    }
    int addedAt = super.add(tl, row);
    synchronized (getListLock()) {
      remapIndexes(addedAt + 1);
    }
    return addedAt;
  }

  /** Gets the row this DataLine is at. */
  public int getRow(SearchResultDataLine tl) {
    String sha1 = tl.getHash();
    if (sha1 != null) return fastMatch(sha1);
    else return super.getRow(tl);
  }

  /**
   * Overrides the default sort to maintain the indexes HashMap, according to the current sort
   * column and order.
   */
  protected void doResort() {
    super.doResort();
    synchronized (getListLock()) {
      _indexes.clear(); // it's easier & quicker to just clear & re-input
      remapIndexes(0);
    }
  }

  /** Every row was replaced at once: count them and index them in one pass. */
  @Override
  protected void rowsReplaced() {
    _numResults = getRowCount();
    _indexes.clear();
    remapIndexes(0);
  }

  /** Does nothing -- lines need no cleanup. */
  protected void cleanup() {}

  /**
   * Simple clear -- clears the number of sources & cached SHA1 indexes. Calls super.clear to erase
   * the stored lines.
   */
  void simpleClear() {
    _numResults = 0;
    synchronized (getListLock()) {
      _indexes.clear();
    }
    super.clear();
  }

  /**
   * Remaps the indexes, starting at 'start' and going to the end of the list. This is needed for
   * when rows are added to the middle of the list to maintain the correct rows per objects.
   */
  private void remapIndexes(int start) {
    remapIndexes(start, getRowCount());
  }

  /**
   * Remaps the indexes, starting at 'start' and going to 'end'. This is useful for when we move a
   * row from end to start or vice versa.
   */
  private void remapIndexes(int start, int end) {
    for (int i = start; i < end; i++) {
      String sha1 = getHash(i);
      if (sha1 != null) _indexes.put(sha1, i);
    }
  }

  /** Gets the SHA1 URN for a row. */
  private String getHash(int idx) {
    if (idx >= getRowCount()) return null;
    return get(idx).getHash();
  }

  /** Fast match -- lookup in the table. */
  private int fastMatch(String sha1) {
    synchronized (getListLock()) {
      Integer idx = _indexes.get(sha1);
      //noinspection ReplaceNullCheck
      if (idx == null) return -1;
      else return idx;
    }
  }

  int getTotalResults() {
    return _numResults;
  }

  @Override
  public boolean isCellEditable(int rowIndex, int columnIndex) {
    return columnIndex == SearchTableColumns.NAME_IDX
        || columnIndex == SearchTableColumns.SOURCE_IDX
        || columnIndex == SearchTableColumns.ACTIONS_IDX;
  }
}
