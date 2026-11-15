/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.limegroup.gnutella.gui;

import com.limegroup.gnutella.settings.BugSettings;

/**
 * The user's choice to send anonymous crash and freeze reports to Icebase, or not. Every path that
 * talks to Icebase (the crash report spooler and its uploads, the StrictMode and watchdog reports,
 * and the session presence heartbeat) asks {@link #enabled()} before it queues or sends anything.
 * The detectors themselves keep running; only reporting is switched off (frostwire/frostwire#1565).
 */
public final class IcebaseReports {

  private IcebaseReports() {}

  /**
   * True when the user lets FrostWire send reports to Icebase. False when they opted out, and also
   * when the setting can't be read: a report is never sent against the user's choice.
   */
  public static boolean enabled() {
    try {
      return BugSettings.SEND_ICEBASE_REPORTS.getValue();
    } catch (Throwable ignored) {
      return false;
    }
  }

  /**
   * Stores the user's choice. Opting out also deletes the reports already waiting on disk, off the
   * calling thread, so nothing recorded before is sent later. Opting back in needs nothing else:
   * new reports and the heartbeat resume.
   */
  public static void setEnabled(boolean enabled) {
    BugSettings.SEND_ICEBASE_REPORTS.setValue(enabled);
    if (!enabled) {
      CrashReportSpooler.discardPendingReports();
    }
  }
}
