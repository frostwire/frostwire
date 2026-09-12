package com.limegroup.gnutella.gui.bugs;

import com.limegroup.gnutella.gui.CrashReportSpooler;
import com.limegroup.gnutella.settings.BugSettings;

public class DeadlockBugManager {
  private DeadlockBugManager() {}

  /**
   * Handles a deadlock bug. Reports go out silently through CrashReportSpooler (Icebase) when the
   * user kept deadlock reports enabled.
   */
  public static void handleDeadlock(DeadlockException bug, String threadName, String message) {
    bug.printStackTrace();
    System.err.println("Detail: " + message);
    if (BugSettings.SEND_DEADLOCK_BUGS.getValue()) {
      CrashReportSpooler.record(bug);
    }
  }
}
