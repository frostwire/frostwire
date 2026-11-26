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

package com.limegroup.gnutella.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * 6 Icebase reports of a ClassFormatError five seconds after start on one Windows machine
 * (#a33293143f1e, frostwire/frostwire#1541): a damaged jar, not a bug in our classes. Users get a
 * plain instruction to download FrostWire again.
 */
class DamagedInstallationTest {

  @Test
  void aMalformedClassMeansADamagedInstallation() {
    assertTrue(DamagedInstallation.isDamaged(new ClassFormatError("Truncated class file")));
  }

  @Test
  void aMissingClassOfFrostWireItselfMeansADamagedInstallation() {
    assertTrue(
        DamagedInstallation.isDamaged(new NoClassDefFoundError("com/limegroup/gnutella/gui/I18n")));
    assertTrue(
        DamagedInstallation.isDamaged(new NoClassDefFoundError("com/frostwire/util/UrlUtils")));
    assertTrue(
        DamagedInstallation.isDamaged(new NoClassDefFoundError("org/limewire/util/OSUtils")));
    assertTrue(
        DamagedInstallation.isDamaged(
            new ExceptionInInitializerError(
                new NoClassDefFoundError("com/limegroup/gnutella/gui/I18n"))));
  }

  /** Icebase #69e248a3e3ca: a public runtime class gone 7 s after start is a damaged runtime. */
  @Test
  void aMissingPublicClassOfTheBundledRuntimeMeansADamagedInstallation() {
    assertTrue(
        DamagedInstallation.isDamaged(
            new NoClassDefFoundError("java/awt/GraphicsEnvironment$LocalGE")));
    assertTrue(DamagedInstallation.isDamaged(new NoClassDefFoundError("javax/swing/JFrame")));
    assertFalse(
        DamagedInstallation.isDamaged(new NoClassDefFoundError("java.awt.GraphicsEnvironment")),
        "a dotted name is the 'Could not initialize class' kind");
  }

  @Test
  void theNativeDialogFallbackIsBuiltPerSystemWithoutAShell() {
    java.util.List<String> windows =
        DamagedInstallation.nativeDialogCommand("Windows 11", "Title", "Text");
    assertEquals("powershell", windows.get(0));
    assertFalse(
        String.join(" ", windows).contains("Text"), "the text goes through the environment");
    java.util.List<String> mac =
        DamagedInstallation.nativeDialogCommand("Mac OS X", "Title", "Text");
    assertEquals("osascript", mac.get(0));
    assertEquals("Text", mac.get(mac.size() - 2));
    java.util.List<String> linux =
        DamagedInstallation.nativeDialogCommand("Linux", "Title", "Text");
    assertEquals("zenity", linux.get(0));
    assertTrue(linux.contains("--text=Text"));
    assertTrue(DamagedInstallation.nativeDialogCommand("Plan 9", "Title", "Text").isEmpty());
    assertTrue(DamagedInstallation.nativeDialogCommand(null, "Title", "Text").isEmpty());
  }

  @Test
  void aMissingJdkClassOrAFailedInitializerIsNotCalledDamage() {
    assertFalse(DamagedInstallation.isDamaged(new NoClassDefFoundError("sun/nio/ch/Net")));
    assertFalse(
        DamagedInstallation.isDamaged(
            new NoClassDefFoundError(
                "Could not initialize class com.limegroup.gnutella.gui.I18n")));
    assertFalse(DamagedInstallation.isDamaged(new NoClassDefFoundError()));
  }

  @Test
  void aDamagedClassBehindOtherErrorsIsStillFound() {
    Throwable wrapped =
        new ExceptionInInitializerError(
            new RuntimeException("startup", new ClassFormatError("Truncated class file")));
    // ExceptionInInitializerError's cause is its exception: the chain is followed
    assertTrue(DamagedInstallation.isDamaged(wrapped));
  }

  @Test
  void otherProblemsAreNotCalledDamage() {
    assertFalse(DamagedInstallation.isDamaged(new NullPointerException()));
    assertFalse(DamagedInstallation.isDamaged(new NoClassDefFoundError("com/foo/Bar")));
    assertFalse(DamagedInstallation.isDamaged(new UnsupportedClassVersionError("too new")));
    assertFalse(DamagedInstallation.isDamaged(null));
  }

  @Test
  void aCauseLoopDoesNotHangTheCheck() {
    RuntimeException a = new RuntimeException("a");
    RuntimeException b = new RuntimeException("b", a);
    a.initCause(b);

    assertFalse(DamagedInstallation.isDamaged(a));
  }

  @Test
  void theMessageSaysWhatToDoAndWhere() {
    String message = DamagedInstallation.message();

    assertTrue(message.contains(DamagedInstallation.DOWNLOAD_URL), message);
    assertTrue(message.toLowerCase().contains("download frostwire again"), message);
    assertFalse(message.contains("Exception") || message.contains("\tat "), "no stack trace");
  }

  @Test
  void theReportNamesTheClassTheJvmRejectedButNothingElse() {
    assertEquals(
        "com.frostwire.util.UrlUtils",
        CrashReportSpooler.missingClass(
            new ClassFormatError(
                "Incompatible magic value 1234 in class file com/frostwire/util/UrlUtils")));
    assertEquals(
        "com.foo.Bar$Inner",
        CrashReportSpooler.missingClass(
            new ClassFormatError("Extra bytes at the end of class file com/foo/Bar$Inner")));
    assertNull(CrashReportSpooler.missingClass(new ClassFormatError("Truncated class file")));
    assertNull(CrashReportSpooler.missingClass(new ClassFormatError()));
    assertNull(
        CrashReportSpooler.missingClass(
            new ClassFormatError("in class file /Users/someone/secret path")));
  }
}
