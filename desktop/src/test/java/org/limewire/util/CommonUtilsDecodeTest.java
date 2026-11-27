/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package org.limewire.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

/** decode() reports every malformed escape as IllegalArgumentException, like URLDecoder. */
class CommonUtilsDecodeTest {
  @Test
  void truncatedEscapeIsAnIllegalArgument() {
    assertThrows(IllegalArgumentException.class, () -> CommonUtils.decode("abc%4"));
    assertThrows(IllegalArgumentException.class, () -> CommonUtils.decode("abc%"));
    assertThrows(IllegalArgumentException.class, () -> CommonUtils.decode("%"));
  }

  @Test
  void nonHexEscapeIsAnIllegalArgument() {
    assertThrows(IllegalArgumentException.class, () -> CommonUtils.decode("a%zzb"));
  }

  @Test
  void wellFormedEscapesAndPlusDecode() {
    assertEquals("a b c", CommonUtils.decode("a%20b+c"));
    assertEquals("100%", CommonUtils.decode("100%25"));
    assertEquals("plain", CommonUtils.decode("plain"));
    assertEquals("", CommonUtils.decode(""));
  }
}
