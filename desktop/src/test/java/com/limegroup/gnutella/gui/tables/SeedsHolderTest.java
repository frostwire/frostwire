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
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import org.junit.jupiter.api.Test;

/**
 * frostwire/frostwire#1380: SeedsHolder is built for the Transfers table and threw (and caught) a
 * NumberFormatException for every HTTP or metadata-fetching row, each paint.
 */
class SeedsHolderTest {

  @Test
  void unparsableSeedsAreZero() {
    for (String text :
        Arrays.asList("", "3", "a/b", "3/x", "/", " / ", "-1/2", "9999999999/1", null)) {
      assertEquals(new SeedsHolder("0/0"), new SeedsHolder(text), String.valueOf(text));
    }
  }

  @Test
  void countsAreParsedWithoutExceptions() {
    assertEquals(-1, SeedsHolder.parseCount(""));
    assertEquals(-1, SeedsHolder.parseCount("x"));
    assertEquals(-1, SeedsHolder.parseCount("-1"));
    assertEquals(-1, SeedsHolder.parseCount("1234567890"));
    assertEquals(0, SeedsHolder.parseCount("0"));
    assertEquals(42, SeedsHolder.parseCount(" 42 "));
  }

  @Test
  void countsKeepTheirOrderAndText() {
    SeedsHolder few = new SeedsHolder("1/2");
    SeedsHolder many = new SeedsHolder(" 3 / 10 ");
    assertTrue(few.compareTo(many) < 0);
    assertEquals(new SeedsHolder("3/10"), many);
    assertEquals(" 3 / 10 ", many.toString());
  }
}
