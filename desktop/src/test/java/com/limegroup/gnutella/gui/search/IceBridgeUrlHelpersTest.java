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

package com.limegroup.gnutella.gui.search;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
import java.util.Locale;
import org.junit.jupiter.api.Test;

/**
 * The text helpers behind icebridge:// links: hex checks for the peer key, HTML escaping of what a
 * peer sends, and file sizes. They are private to the handler, which needs a window for the rest.
 */
class IceBridgeUrlHelpersTest {

  private static Object call(String name, Class<?> type, Object argument) throws Exception {
    Method method = IceBridgeUrlHandler.class.getDeclaredMethod(name, type);
    method.setAccessible(true);
    return method.invoke(null, argument);
  }

  @Test
  void hexDigitsOfEitherCaseAreHex() throws Exception {
    assertTrue((boolean) call("isHex", String.class, "0123456789abcdefABCDEF"));
    assertTrue((boolean) call("isHex", String.class, ""));
    assertFalse((boolean) call("isHex", String.class, "xyz"));
    assertFalse((boolean) call("isHex", String.class, "12 34"));
    assertFalse((boolean) call("isHex", String.class, "ff-ff"));
  }

  @Test
  void markupFromAPeerIsEscaped() throws Exception {
    assertEquals(
        "&lt;b&gt;&quot;Tom &amp; Jerry&quot;&lt;/b&gt;",
        call("escapeHtml", String.class, "<b>\"Tom & Jerry\"</b>"));
    assertEquals("", call("escapeHtml", String.class, null));
    assertEquals("plain", call("escapeHtml", String.class, "plain"));
    assertEquals("&amp;amp;", call("escapeHtml", String.class, "&amp;"), "escaped exactly once");
  }

  @Test
  void sizesAreShownInTheLargestFittingUnit() throws Exception {
    Locale previous = Locale.getDefault();
    Locale.setDefault(Locale.US);
    try {
      assertEquals("0 B", call("humanSize", long.class, 0L));
      assertEquals("1023 B", call("humanSize", long.class, 1023L));
      assertEquals("1.0 KB", call("humanSize", long.class, 1024L));
      assertEquals("1.5 KB", call("humanSize", long.class, 1536L));
      assertEquals("1.0 MB", call("humanSize", long.class, 1024L * 1024));
      assertEquals("1.0 GB", call("humanSize", long.class, 1024L * 1024 * 1024));
      assertEquals("1.0 TB", call("humanSize", long.class, 1024L * 1024 * 1024 * 1024));
      assertEquals("1024.0 TB", call("humanSize", long.class, 1024L * 1024 * 1024 * 1024 * 1024));
    } finally {
      Locale.setDefault(previous);
    }
  }
}
