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

import javax.swing.*;

/** The search result menu. */
final class SearchResultMenu {
  private final SearchResultMediator PANEL;

  /** Private constructor to ensure that this class can never be created. */
  SearchResultMenu(SearchResultMediator rp) {
    PANEL = rp;
  }

  /** Adds search-result specific items to the JPopupMenu. */
  JPopupMenu addToMenu(JPopupMenu popupMenu, SearchResultDataLine[] lines) {
    // Check if there are lines
    if (lines.length == 0) {
      return popupMenu;
    }
    // A menu acts on results of one kind: keep the first results that are the same kind as the
    // first one.
    lines = leadingSameKind(lines, SearchResultDataLine::isSameKindAs);
    return lines[0].getSearchResult().createMenu(popupMenu, lines, PANEL);
  }

  /**
   * The items from the first one up to, not including, the first item that is not of the same kind
   * as the one before it. Never empty for a non-empty array: the first item is always kept (the
   * code this replaces kept one item too few, and none when the first two differed, #1532).
   */
  static <T> T[] leadingSameKind(T[] items, java.util.function.BiPredicate<T, T> sameKind) {
    for (int i = 1; i < items.length; i++) {
      if (!sameKind.test(items[i - 1], items[i])) {
        return java.util.Arrays.copyOf(items, i);
      }
    }
    return items;
  }
}
