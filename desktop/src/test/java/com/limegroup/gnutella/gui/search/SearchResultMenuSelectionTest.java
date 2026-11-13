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
 *     along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.limegroup.gnutella.gui.search;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.function.BiPredicate;
import org.junit.jupiter.api.Test;

/**
 * The right-click menu of a mixed selection of search results acts on the leading results of one
 * kind. Right-clicking a selection whose first two results differed kept none of them and threw
 * ArrayIndexOutOfBoundsException (Icebase #48baeb90936e, frostwire/frostwire#1532).
 */
class SearchResultMenuSelectionTest {
  /** Same kind: same first letter. */
  private static final BiPredicate<String, String> SAME_KIND = (a, b) -> a.charAt(0) == b.charAt(0);

  @Test
  void aSelectionWhoseFirstTwoResultsDifferKeepsTheFirst() {
    String[] kept =
        SearchResultMenu.leadingSameKind(new String[] {"torrent1", "stream1"}, SAME_KIND);

    assertArrayEquals(new String[] {"torrent1"}, kept);
  }

  @Test
  void aSelectionThatChangesKindLaterKeepsEveryResultBeforeTheChange() {
    String[] kept =
        SearchResultMenu.leadingSameKind(
            new String[] {"torrent1", "torrent2", "torrent3", "stream1", "torrent4"}, SAME_KIND);

    assertArrayEquals(new String[] {"torrent1", "torrent2", "torrent3"}, kept);
  }

  @Test
  void aSelectionOfOneKindIsKeptWhole() {
    String[] all = {"torrent1", "torrent2"};

    assertSame(all, SearchResultMenu.leadingSameKind(all, SAME_KIND));
    String[] one = {"stream1"};
    assertSame(one, SearchResultMenu.leadingSameKind(one, SAME_KIND));
  }
}
