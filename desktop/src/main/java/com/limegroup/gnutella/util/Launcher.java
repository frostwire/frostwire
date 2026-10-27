/*
 *     Created by Angel Leon (@gubatron), Alden Torres (aldenml)
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

package com.limegroup.gnutella.util;

import com.frostwire.util.OSUtils;
import com.limegroup.gnutella.gui.util.DesktopParallelExecutor;
import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;
import org.apache.commons.io.FilenameUtils;

/**
 * This class launches files in their associated applications and opens urls in the default browser
 * for different operating systems. This really only works meaningfully for the Mac and Windows.
 *
 * <p>Every launch runs on {@link DesktopParallelExecutor}: asking the OS shell to open something
 * can block for seconds, and the callers are almost always on the EDT. Failures are handed to the
 * caller's {@code onFailure} on the pool thread.
 *
 * <p>Acknowledgement goes to Eric Albert for demonstrating the general technique for loading the
 * MRJ classes in his frequently-used "BrowserLauncher" code.
 *
 * <p>This code is Copyright 1999-2001 by Eric Albert (ejalbert@cs.stanford.edu) and may be
 * redistributed or modified in any form without restrictions as long as the portion of this comment
 * from this paragraph through the end of the comment is not removed. The author requests that he be
 * notified of any application, applet, or other binary that makes use of this code, but that's more
 * out of curiosity than anything and is not required. This software includes no warranty. The
 * author is not repsonsible for any loss of data or functionality or any adverse or unexpected
 * effects of using this software.
 *
 * <p>Credits: <br>
 * Steven Spencer, JavaWorld magazine (<a
 * href="http://www.javaworld.com/javaworld/javatips/jw-javatip66.html">Java Tip 66</a>) <br>
 * Thanks also to Ron B. Yeh, Eric Shapiro, Ben Engber, Paul Teitlebaum, Andrea Cantatore, Larry
 * Barowski, Trevor Bedzek, Frank Miedrich, and Ron Rabakukk
 *
 * @author Eric Albert (<a href="mailto:ejalbert@cs.stanford.edu">ejalbert@cs.stanford.edu</a>)
 * @version 1.4b1 (Released June 20, 2001)
 */
// 2345678|012345678|012345678|012345678|012345678|012345678|012345678|012345678|
public final class Launcher {
  private static final List<String> FORBIDDEN_EXTENSIONS =
      Arrays.asList("exe", "vbs", "lnk", "bat", "sys", "com", "js", "scpt");

  /** This class should be never be instantiated; this just ensures so. */
  private Launcher() {}

  /**
   * Opens the specified url in the default browser, on {@link DesktopParallelExecutor}.
   *
   * <p>{@code Desktop.browse} asks the OS shell to open the url and can block for seconds, so it
   * never runs on the caller's thread. An invalid url, a missing browser or a platform that cannot
   * browse is reported to {@code onFailure}, on the pool thread.
   *
   * @param url The url to open. Should not be null
   * @param onFailure receives whatever the browse call threw, on the pool thread
   * @throws NullPointerException The URL was null.
   */
  public static void openURL(String url, Consumer<Exception> onFailure) {
    if (url == null) {
      throw new NullPointerException("Attempted to open null URL");
    }
    runOffEdt(() -> Desktop.getDesktop().browse(new URI(url)), onFailure);
  }

  /**
   * Launches the file whose abstract path is specified in the `File` parameter, on {@link
   * DesktopParallelExecutor}. This method will not launch any file with .exe, .vbs, .lnk, .bat,
   * .sys, or .com extensions: it reports a {@link SecurityException} to {@code onFailure} instead.
   *
   * <p>The extension check reads the file system and {@code Desktop.open} can stall for hundreds of
   * milliseconds (X11 desktop integration on Linux), so both run on the pool thread. {@code
   * Desktop.open} throws IllegalArgumentException when the file was moved or deleted since the user
   * clicked it; that and every other failure is reported to {@code onFailure}, on the pool thread.
   *
   * @param file the file to launch
   * @param onFailure receives whatever the launch threw, on the pool thread
   */
  public static void launchFile(File file, Consumer<Exception> onFailure) {
    runOffEdt(
        () -> {
          if (file.isFile()
              && FORBIDDEN_EXTENSIONS.contains(FilenameUtils.getExtension(file.getName()))) {
            throw new SecurityException();
          }
          Desktop.getDesktop().open(file);
        },
        onFailure);
  }

  /**
   * Launches the Explorer/Finder and highlights the file, on {@link DesktopParallelExecutor}.
   *
   * @param file the file to show in explorer
   * @param onFailure receives whatever the launch threw, on the pool thread
   * @see #launchExplorer(Supplier, Consumer)
   */
  public static void launchExplorer(File file, Consumer<Exception> onFailure) {
    launchExplorer(() -> file, onFailure);
  }

  /**
   * Launches the Explorer/Finder and highlights the file that {@code target} picks, on {@link
   * DesktopParallelExecutor}. Nothing is launched if {@code target} returns null or a file that
   * does not exist.
   *
   * <p>Picking the file, checking that it exists, resolving its canonical path and starting
   * explorer/osascript all touch the file system or the OS shell and can block for seconds on a
   * slow or disconnected drive, so all of it, {@code target} included, runs on the pool thread.
   *
   * @param target picks the file to show; it runs on the pool thread and may read the file system
   * @param onFailure receives whatever the launch threw, on the pool thread
   */
  public static void launchExplorer(Supplier<File> target, Consumer<Exception> onFailure) {
    runOffEdt(
        () -> {
          File file = target.get();
          if (file != null && file.exists()) {
            explore(file);
          }
        },
        onFailure);
  }

  /** Opens the platform file manager on {@code file}. Blocks on the OS shell. */
  private static void explore(File file) throws IOException {
    if (OSUtils.isWindows()) {
      String explorePath = file.getPath();
      try {
        explorePath = file.getCanonicalPath();
      } catch (IOException ignored) {
      }
      if (file.isDirectory()) {
        // launches explorer in the directory
        FWProcess.exec(new String[] {"explorer", explorePath});
      } else {
        // launches explorer and highlights the file
        FWProcess.exec(new String[] {"explorer", "/select,", explorePath});
      }
    } else if (OSUtils.isMacOSX()) {
      // launches the Finder and highlights the file
      FWProcess.exec(selectFileCommand(file));
    } else if (OSUtils.isLinux()) {
      if (file.isDirectory()) {
        Desktop.getDesktop().open(file);
      } else if (file.isFile()) {
        Desktop.getDesktop().open(file.getParentFile());
      }
    }
  }

  /** A call into the OS shell or desktop integration, which may block for a long time. */
  @FunctionalInterface
  interface ShellCall {
    void run() throws Exception;
  }

  /**
   * Runs {@code call} on {@link DesktopParallelExecutor} and hands any exception it throws to
   * {@code onFailure} on the pool thread, so the caller (usually the EDT) never waits on the OS
   * shell and nothing escapes the pool thread.
   */
  static void runOffEdt(ShellCall call, Consumer<Exception> onFailure) {
    DesktopParallelExecutor.execute(
        () -> {
          try {
            call.run();
          } catch (Exception e) {
            onFailure.accept(e);
          }
        });
  }

  /** Launches the Finder and selects the given File */
  private static String[] selectFileCommand(File file) {
    String path = null;
    try {
      path = file.getCanonicalPath();
    } catch (IOException err) {
      path = file.getAbsolutePath();
    }
    return new String[] {
      "osascript",
      "-e",
      "set unixPath to \"" + path + "\"",
      "-e",
      "set hfsPath to POSIX file unixPath",
      "-e",
      "tell application \"Finder\"",
      "-e",
      "activate",
      "-e",
      "select hfsPath",
      "-e",
      "end tell"
    };
  }
}
