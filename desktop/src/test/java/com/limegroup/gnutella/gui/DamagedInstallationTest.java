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
