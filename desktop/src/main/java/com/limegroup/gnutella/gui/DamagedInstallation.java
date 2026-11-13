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

import java.awt.Desktop;
import java.awt.GraphicsEnvironment;
import java.net.URI;
import javax.swing.JOptionPane;

/**
 * What FrostWire says when its own files cannot be read: a plain instruction to download and
 * install it again, instead of a stack trace. A class the JVM rejects as malformed (a
 * ClassFormatError), or a jar it cannot read (a ZipError), is not a bug in FrostWire's code: the
 * bytes on disk are not the ones we shipped (an interrupted install or update, a disk error, a
 * security product that rewrote or locked the file). Icebase #a33293143f1e,
 * frostwire/frostwire#1541.
 *
 * <p>Uses nothing of FrostWire's own, only the JDK: the files that would load it may be the damaged
 * ones.
 */
public final class DamagedInstallation {
  static final String DOWNLOAD_URL = "https://www.frostwire.com/download";

  private DamagedInstallation() {}

  /** Whether the problem, or something it was caused by, says FrostWire's files are damaged. */
  public static boolean isDamaged(Throwable problem) {
    for (int depth = 0; problem != null && depth < 8; depth++) {
      if (isMalformedClass(problem) || isZipError(problem)) {
        return true;
      }
      problem = problem.getCause();
    }
    return false;
  }

  /**
   * A ClassFormatError, but not its subclass UnsupportedClassVersionError: that one means a Java
   * too old for the class, which reinstalling FrostWire would not fix.
   */
  private static boolean isMalformedClass(Throwable problem) {
    return problem instanceof ClassFormatError
        && !(problem instanceof UnsupportedClassVersionError);
  }

  /** ZipError is deprecated for removal: named, not referenced, so it compiles on any JDK. */
  private static boolean isZipError(Throwable problem) {
    return problem instanceof Error
        && "java.util.zip.ZipError".equals(problem.getClass().getName());
  }

  /** The plain message users see. */
  static String message() {
    return "FrostWire's files are damaged, so it can't start.\n\n"
        + "This can happen when an installation or an update was interrupted, "
        + "or when a security program changed the files.\n\n"
        + "Please download FrostWire again from\n"
        + DOWNLOAD_URL
        + "\nand install it, even if FrostWire is already installed. "
        + "Your settings are kept in your user folder, not in FrostWire's program folder.";
  }

  /**
   * Shows the message with a button that opens the download page. Never throws and does nothing
   * without a display.
   */
  public static void show() {
    System.err.println(message());
    if (GraphicsEnvironment.isHeadless()) {
      return;
    }
    try {
      Object[] options = {"Download FrostWire", "Close"};
      int choice =
          JOptionPane.showOptionDialog(
              null,
              message(),
              "FrostWire can't start",
              JOptionPane.DEFAULT_OPTION,
              JOptionPane.ERROR_MESSAGE,
              null,
              options,
              options[0]);
      if (choice == 0 && Desktop.isDesktopSupported()) {
        Desktop.getDesktop().browse(new URI(DOWNLOAD_URL));
      }
    } catch (Throwable ignored) {
      // The message is on stderr; nothing else can be done.
    }
  }
}
