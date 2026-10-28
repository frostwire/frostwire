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

import com.frostwire.gui.filters.TableLineFilter;
import com.frostwire.util.Logger;
import com.limegroup.gnutella.settings.SearchSettings;
import java.util.ArrayList;
import java.util.List;

/**
 * Filters out certain rows from the data model.
 *
 * @author Sumeet Thadani, Sam Berlin
 */
public class TableRowFilteredModel extends ResultPanelModel {
  private static final Logger LOG = Logger.getLogger(TableRowFilteredModel.class);

  /** A list of all filtered results. */
  private final List<SearchResultDataLine> HIDDEN;

  /** The filter to use in this row filter. */
  private final TableLineFilter<SearchResultDataLine> FILTER;

  /** The Junk Filter */
  private TableLineFilter<SearchResultDataLine> junkFilter = AllowFilter.instance();

  private int _numResults;

  /** Constructs a TableRowFilter with the specified TableLineFilter. */
  public TableRowFilteredModel(TableLineFilter<SearchResultDataLine> f) {
    super();
    if (f == null) {
      throw new NullPointerException("null filter");
    }
    FILTER = f;
    HIDDEN = new ArrayList<>();
    _numResults = 0;
  }

  /**
   * Returns true if Table is sorted which means either it is really sorted OR 'move junk to bottom'
   * is selected which is also some kind of sorting!
   */
  public boolean isSorted() {
    return super.isSorted() || SearchSettings.moveJunkToBottom();
  }

  /** Determines whether this line should be added. */
  public int add(SearchResultDataLine tl, int row) {
    if (isShown(tl)) {
      return super.add(tl, row);
    }
    hide(tl);
    return -1;
  }

  /** Whether the line passes the junk setting and the filter. */
  private boolean isShown(SearchResultDataLine line) {
    return (junkFilter.allow(line) || !SearchSettings.hideJunk()) && allow(line);
  }

  /**
   * Counts a line that isn't shown. Junk hidden by the junk setting is dropped, lines the filter
   * rejects are kept so a later filter can show them again.
   */
  private void hide(SearchResultDataLine line) {
    if (junkFilter.allow(line) || !SearchSettings.hideJunk()) {
      HIDDEN.add(line);
    }
    _numResults += 1;
  }

  /** Intercepts to clear the hidden map. */
  protected void simpleClear() {
    _numResults = 0;
    HIDDEN.clear();
    super.simpleClear();
  }

  @Override
  public void clear() {
    _numResults = 0;
    HIDDEN.clear();
    super.clear();
  }

  /** Notification that the filters have changed. Fires one table-changed event. */
  void filtersChanged() {
    rebuild();
  }

  /** Sets the Junk Filter. Pass null as argument to disable the filter */
  void setJunkFilter(TableLineFilter<SearchResultDataLine> junkFilter) {
    if (junkFilter != null) {
      this.junkFilter = junkFilter;
    } else {
      this.junkFilter = AllowFilter.instance();
    }
  }

  /** Determines whether or not the specified line is allowed by the filter. */
  private boolean allow(SearchResultDataLine line) {
    return FILTER.allow(line);
  }

  /**
   * Re-applies the filters to every line, shown or hidden. The shown lines replace the rows in one
   * step, sorted once and indexed once, instead of being re-added one by one: that sorted insert
   * and re-index per line was O(N²) on the EDT, plus one table event per line.
   */
  private void rebuild() {
    List<SearchResultDataLine> lines;
    synchronized (getListLock()) {
      lines = new ArrayList<>(_list.size() + HIDDEN.size());
      lines.addAll(_list);
    }
    lines.addAll(HIDDEN);
    HIDDEN.clear();
    _numResults = 0;
    List<SearchResultDataLine> shown = new ArrayList<>(lines.size());
    for (SearchResultDataLine line : lines) {
      if (isShown(line)) {
        shown.add(line);
      } else {
        hide(line);
      }
    }
    setRows(shown);
  }

  private int getFilteredResults() {
    return super.getTotalResults();
  }

  public int getTotalResults() {
    return getFilteredResults() + _numResults;
  }
}
