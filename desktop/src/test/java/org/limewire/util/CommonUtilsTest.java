/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package org.limewire.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.frostwire.util.OSUtils;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class CommonUtilsTest {

  @Test
  void decodeHandlesPlusPercentAndPlainText() {
    assertEquals("hello world", CommonUtils.decode("hello+world"));
    assertEquals("a b/c", CommonUtils.decode("a%20b%2Fc"));
    assertEquals("plain", CommonUtils.decode("plain"));
    assertEquals("", CommonUtils.decode(""));
    assertThrows(IllegalArgumentException.class, () -> CommonUtils.decode("bad%zz"));
  }

  @Test
  void seconds2timeFormatsClockStyle() {
    assertEquals("0:00", CommonUtils.seconds2time(0));
    assertEquals("0:09", CommonUtils.seconds2time(9));
    assertEquals("1:05", CommonUtils.seconds2time(65));
    assertEquals("59:59", CommonUtils.seconds2time(3599));
    assertEquals("1:00:00", CommonUtils.seconds2time(3600));
    assertEquals("1:02:05", CommonUtils.seconds2time(3725));
    assertEquals("1:01:01:01", CommonUtils.seconds2time(90061));
    assertEquals("2:00:00:00", CommonUtils.seconds2time(2 * 86400));
  }

  @Test
  void convertFileNameReplacesIllegalCharacters() {
    assertEquals("a_b_c", CommonUtils.convertFileName("a/b\nc"));
    assertEquals("tab_here", CommonUtils.convertFileName("tab\there"));
    assertEquals("plain name.mp3", CommonUtils.convertFileName("plain name.mp3"));
    if (OSUtils.isMacOSX()) {
      assertEquals("a_b", CommonUtils.convertFileName("a:b"));
    }
  }

  @Test
  void convertFileNameTruncatesLongNamesKeepingTheExtension() {
    String longName = "x".repeat(400) + ".mp3";
    String converted = CommonUtils.convertFileName(longName);
    assertTrue(converted.getBytes().length <= 180);
    assertTrue(converted.endsWith(".mp3"));
    assertTrue(converted.startsWith("xxxx"));

    String noExtension = CommonUtils.convertFileName("y".repeat(400));
    assertEquals(180, noExtension.length());

    // a huge extension is cut so the base name survives
    String hugeExt = CommonUtils.convertFileName("name." + "e".repeat(300));
    assertTrue(hugeExt.getBytes().length <= 180);
    assertTrue(hugeExt.startsWith("name."));
  }

  @Test
  void directoriesComeFromSystemProperties() {
    assertEquals(new File(System.getProperty("user.home")), CommonUtils.getUserHomeDir());
    assertEquals(new File(System.getProperty("user.dir")), CommonUtils.getCurrentDirectory());
    assertNotNull(CommonUtils.getExecutableDirectory());
    assertFalse(CommonUtils.isPortable());
  }

  @Test
  void resourceStreamsLoadFromTheClasspath() throws IOException {
    try (InputStream in =
        CommonUtils.getResourceStream("org/limewire/util/CommonUtilsTest.class")) {
      assertTrue(in.read() >= 0);
    }
    assertThrows(IOException.class, () -> CommonUtils.getResourceStream("no/such/resource.bin"));
  }

  @Test
  void portableDirectoriesAreReadFromMetaProperties(@TempDir File tmp) {
    File settings = new File(tmp, "settings");
    Properties meta = new Properties();
    for (String os : new String[] {"windows", "mac", "posix"}) {
      meta.setProperty("user.settings.dir." + os, settings.getPath());
    }
    assertNull(CommonUtils.getPortableSettingsDir(new Properties()));
    File resolved = CommonUtils.getPortableSettingsDir(meta);
    if (OSUtils.isWindows() || OSUtils.isMacOSX() || OSUtils.isLinux()) {
      assertEquals(settings, resolved);
      assertTrue(settings.isDirectory(), "the portable directory is created on demand");
    } else {
      assertNull(resolved);
    }
  }
}
