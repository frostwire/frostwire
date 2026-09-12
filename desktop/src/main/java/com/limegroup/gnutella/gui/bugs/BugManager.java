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

package com.limegroup.gnutella.gui.bugs;

import com.frostwire.util.Logger;
import com.frostwire.util.OSUtils;
import com.limegroup.gnutella.gui.*;
import com.limegroup.gnutella.settings.BugSettings;
import java.io.*;
import java.util.Date;
import java.util.EnumMap;
import org.apache.commons.io.IOUtils;
import org.limewire.util.StringUtils;

/**
 * Dispatches errors to friendly handlers and the local bug log. Crash reports themselves go out
 * silently through CrashReportSpooler (Icebase); this class shows no dialogs and contacts no
 * servlet.
 */
public final class BugManager {
  private static final Logger LOG = Logger.getLogger(BugManager.class);
  private static final EnumMap<ErrorType, EnumMap<DetailErrorType, String>> errorDescs;

  /** The instance of BugManager -- follows a singleton pattern. */
  private static BugManager INSTANCE;

  static {
    errorDescs = new EnumMap<>(ErrorType.class);
    for (ErrorType type : ErrorType.values())
      errorDescs.put(type, new EnumMap<>(DetailErrorType.class));
    errorDescs
        .get(ErrorType.GENERIC)
        .put(
            DetailErrorType.DISK_FULL,
            I18n.tr(
                "FrostWire was unable to write a necessary file because your hard drive is full. To continue using FrostWire you must free up space on your hard drive."));
    errorDescs
        .get(ErrorType.GENERIC)
        .put(
            DetailErrorType.FILE_LOCKED,
            I18n.tr(
                "FrostWire was unable to open a necessary file because another program has locked the file. FrostWire may act unexpectedly until this file is released."));
    errorDescs
        .get(ErrorType.GENERIC)
        .put(
            DetailErrorType.NO_PRIVS,
            I18n.tr(
                "FrostWire was unable to write a necessary file because you do not have the necessary permissions. Your preferences may not be maintained the next time you start FrostWire, or FrostWire may behave in unexpected ways."));
    errorDescs
        .get(ErrorType.GENERIC)
        .put(
            DetailErrorType.BAD_CHARS,
            I18n.tr(
                "FrostWire cannot open a necessary file because the filename contains characters which are not supported by your operating system. FrostWire may behave in unexpected ways."));
    errorDescs
        .get(ErrorType.DOWNLOAD)
        .put(
            DetailErrorType.DISK_FULL,
            I18n.tr(
                "FrostWire cannot download the selected file because your hard drive is full. To download more files, you must free up space on your hard drive."));
    errorDescs
        .get(ErrorType.DOWNLOAD)
        .put(
            DetailErrorType.FILE_LOCKED,
            I18n.tr(
                "FrostWire was unable to download the selected file because another program is using the file. Please close the other program and retry the download."));
    errorDescs
        .get(ErrorType.DOWNLOAD)
        .put(
            DetailErrorType.NO_PRIVS,
            I18n.tr(
                "FrostWire was unable to create or continue writing an incomplete file for the selected download because you do not have permission to write files to the incomplete folder. To continue using FrostWire, please choose a different Save Folder."));
    errorDescs
        .get(ErrorType.DOWNLOAD)
        .put(
            DetailErrorType.BAD_CHARS,
            I18n.tr(
                "FrostWire was unable to open the incomplete file for the selected download because the filename contains characters which are not supported by your operating system."));
    // just verify it was all setup right.
    for (ErrorType type : ErrorType.values()) {
      assert errorDescs.get(type) != null;
      assert errorDescs.get(type).size() == DetailErrorType.values().length;
    }
  }

  private final LocalClientInfoFactory localClientInfoFactory;

  /** A lock to be used when writing to the logfile, if the log is to be recorded locally. */
  private final Object WRITE_LOCK = new Object();

  /** A separator between bug reports. */
  private final byte[] SEPARATOR = "-----------------\n".getBytes();

  /**
   * Private to ensure that only this class can construct a `BugManager`, thereby ensuring that only
   * one instance is created.
   */
  private BugManager() {
    localClientInfoFactory =
        LimeWireModule.instance()
            .getLimeWireGUIModule()
            .getLimeWireGUI()
            .getLocalClientInfoFactory();
  }

  public static synchronized BugManager instance() {
    if (INSTANCE == null) INSTANCE = new BugManager();
    return INSTANCE;
  }

  /**
   * Attempts to handle an IOException. If we know expect the problem, we can either ignore it or
   * display a friendly error (both returning true, for handled) or expect the outer-world to handle
   * it (and return false).
   *
   * @return true if we could handle the error.
   */
  private static boolean handleException(IOException ioe) {
    Throwable e = ioe;
    while (e != null) {
      String msg = e.getMessage();
      if (msg != null) {
        msg = msg.toLowerCase();
        DetailErrorType detailType = null;
        // If the user's disk is full, let them know.
        if (StringUtils.contains(msg, "no space left")
            || StringUtils.contains(msg, "not enough space")) {
          detailType = DetailErrorType.DISK_FULL;
        }
        // If the file is locked, let them know.
        else if (StringUtils.contains(msg, "being used by another process")
            || StringUtils.contains(msg, "with a user-mapped section open")) {
          detailType = DetailErrorType.FILE_LOCKED;
        }
        // If we don't have permissions to write, let them know.
        else if (StringUtils.contains(msg, "access is denied")
            || StringUtils.contains(msg, "permission denied")) {
          detailType = DetailErrorType.NO_PRIVS;
        }
        // If character set is faulty...
        else if (StringUtils.contains(msg, "invalid argument")) {
          detailType = DetailErrorType.BAD_CHARS;
        }
        if (detailType != null) {
          MessageService.instance().showError(errorDescs.get(ErrorType.GENERIC).get(detailType));
          return true;
        }
      }
      e = e.getCause();
    }
    // don't know what to do, let the outer world handle it.
    return false;
  }

  /**
   * Handles a single bug report. If bug is a ThreadDeath, rethrows it. If the user wants to ignore
   * all bugs, this effectively does nothing. The server told us to stop reporting this (or any)
   * bug(s) for awhile, this effectively does nothing. Otherwise, it will either send the bug
   * directly to the servlet or ask the user to review it before sending.
   */
  public void handleBug(Throwable bug, String threadName, String detail) {
    // Try to dispatch the bug to a friendly handler.
    if (bug instanceof IOException && handleException((IOException) bug)) {
      return; // handled already.
    }
    // Get the classpath
    StringBuilder classPath = new StringBuilder();
    String classPathSeparator = OSUtils.isWindows() ? ";" : ":";
    String[] classpaths = System.getProperty("java.class.path").split(classPathSeparator);
    for (String classpath : classpaths) {
      classPath.append("  ").append(classpath).append("\n");
    }
    // Add CLASSPATH to the report
    detail = detail + "\nCLASSPATH:\n" + classPath + "\n";
    bug.printStackTrace();
    // Build the LocalClientInfo out of the info ...
    final LocalClientInfo info =
        localClientInfoFactory.createLocalClientInfo(bug, threadName, detail, false);
    if (BugSettings.LOG_BUGS_LOCALLY.getValue()) {
      logBugToDisk(info);
    }
    // Silent Icebase reporting already happened in ErrorHandler ->
    // CrashReportSpooler. The legacy review dialog and bug servlet are gone.
  }

  /** Logs the bug report to a local file. If the file reaches a certain size it is erased. */
  private void logBugToDisk(LocalClientInfo info) {
    File f = BugSettings.BUG_LOG_FILE.getValue();
    f.setWritable(true);
    OutputStream os = null;
    try {
      synchronized (WRITE_LOCK) {
        if (f.length() > BugSettings.MAX_BUGFILE_SIZE.getValue()) {
          //noinspection ResultOfMethodCallIgnored
          f.delete();
        }
        os = new BufferedOutputStream(new FileOutputStream(f.getPath(), true));
        os.write((new Date() + "\n").getBytes());
        os.write(info.toBugReport().getBytes());
        os.write(SEPARATOR);
        os.flush();
      }
    } catch (IOException ignored) {
    } finally {
      IOUtils.closeQuietly(os);
    }
  }

  public enum ErrorType {
    GENERIC,
    DOWNLOAD
  }

  private enum DetailErrorType {
    DISK_FULL,
    FILE_LOCKED,
    NO_PRIVS,
    BAD_CHARS
  }
}
