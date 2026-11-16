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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * frostwire/frostwire#1570: a table's background resort threw a ClassCastException. {@link
 * AbstractTableMediator#compare} must be a total order over any sort keys, nulls and keys of
 * different classes included, so one odd row can't break a sort.
 */
class AbstractTableMediatorCompareTest {

  private static final Object[] KEYS = {
    null,
    "beta",
    "Alpha",
    "",
    7,
    -3,
    7.5,
    new SizeHolder(10),
    new SizeHolder(2),
    new NameHolder("name"),
    new Date(5),
    new Object(),
    new Object(),
    Boolean.TRUE,
  };

  @Test
  void keysOfDifferentClassesCompareWithoutThrowing() {
    // Each of these threw a ClassCastException before (frostwire/frostwire#1570).
    assertTrue(AbstractTableMediator.compare("text", 1) != 0);
    assertTrue(AbstractTableMediator.compare(1, "text") != 0);
    assertTrue(AbstractTableMediator.compare(new SizeHolder(1), "text") != 0);
    assertTrue(AbstractTableMediator.compare(new NameHolder("n"), new SizeHolder(1)) != 0);
    assertTrue(AbstractTableMediator.compare(1, 1L) != 0);
  }

  @Test
  void compareIsATotalOrderOverMixedAndNullKeys() {
    for (Object a : KEYS) {
      assertEquals(0, AbstractTableMediator.compare(a, a), "reflexive: " + a);
      for (Object b : KEYS) {
        int ab = Integer.signum(AbstractTableMediator.compare(a, b));
        int ba = Integer.signum(AbstractTableMediator.compare(b, a));
        assertEquals(-ab, ba, "antisymmetric: " + a + " / " + b);
        for (Object c : KEYS) {
          if (ab <= 0 && AbstractTableMediator.compare(b, c) <= 0) {
            assertTrue(
                AbstractTableMediator.compare(a, c) <= 0,
                "transitive: " + a + " <= " + b + " <= " + c);
          }
        }
      }
    }
  }

  @Test
  void keysOfOneClassKeepTheirNaturalOrderAndNullsSortFirst() {
    assertTrue(AbstractTableMediator.compare(null, "a") < 0);
    assertTrue(AbstractTableMediator.compare("a", null) > 0);
    assertTrue(AbstractTableMediator.compare("alpha", "Beta") < 0);
    assertEquals(0, AbstractTableMediator.compare("Alpha", "alpha"));
    assertTrue(AbstractTableMediator.compare(2, 10) < 0);
    assertTrue(AbstractTableMediator.compare(new SizeHolder(2), new SizeHolder(10)) < 0);
    assertEquals(0, AbstractTableMediator.compare(new Object(), new Object()));

    List<Object> keys = new ArrayList<>(Arrays.asList(KEYS));
    keys.sort(AbstractTableMediator::compare);
    assertEquals(null, keys.get(0));
  }
}
