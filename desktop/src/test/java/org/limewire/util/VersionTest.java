/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package org.limewire.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class VersionTest {

  @Test
  void parsesAndOrdersDottedVersions() throws Exception {
    assertEquals("7.1.5", new Version("7.1.5").toString());
    assertTrue(new Version("7.1.5").compareTo(new Version("7.1.6")) < 0);
    assertTrue(new Version("7.2.0").compareTo(new Version("7.1.9")) > 0);
    assertTrue(new Version("8").compareTo(new Version("7.9.9")) > 0);
    assertTrue(new Version("1.10").compareTo(new Version("1.9")) > 0);
    assertEquals(0, new Version("3.4").compareTo(new Version("3.4.0")));
    assertEquals(new Version("3"), new Version("3.0.0"));
    assertEquals(new Version("3").hashCode(), new Version("3.0.0.0").hashCode());
    assertNotEquals(new Version("3.0.1"), new Version("3.0.2"));
    assertNotEquals(new Version("3"), "3");
  }

  @Test
  void parsesRevisionSuffixes() throws Exception {
    Version beta = new Version("1.2.3_04");
    Version release = new Version("1.2.3");
    assertTrue(beta.compareTo(release) > 0);
    assertTrue(new Version("1.2.3_05").compareTo(beta) > 0);
    assertEquals(0, new Version("1.2.3b").compareTo(release));
  }

  @Test
  void rejectsMalformedVersions() {
    assertThrows(VersionFormatException.class, () -> new Version("x.y.z"));
    assertThrows(VersionFormatException.class, () -> new Version(""));
    assertThrows(VersionFormatException.class, () -> new Version("1.x"));
    assertThrows(VersionFormatException.class, () -> new Version("1.2."));
    assertThrows(VersionFormatException.class, () -> new Version("1.2.x"));
  }

  @Test
  void javaVersionHelpers() {
    assertEquals(System.getProperty("java.version"), VersionUtils.getJavaVersion());
    assertTrue(VersionUtils.isJava16OrAbove());
  }
}
