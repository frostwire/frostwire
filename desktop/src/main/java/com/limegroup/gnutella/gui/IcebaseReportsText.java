/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.limegroup.gnutella.gui;

/**
 * Wording of the Icebase reports choice, shared by the setup wizard and the Options pane.
 *
 * <p>Every claim here must stay true of the code: the report fields are the ones {@link
 * CrashReportSpooler} builds (stack frames are class, method and line only; exception messages are
 * never sent; thread names are reduced to their prefix), the heartbeat is {@link
 * LivePresenceHeartbeat}'s, and Icebase (frostwire-cloud {@code icebase.frostwire.com}) stores no
 * IP address.
 */
public final class IcebaseReportsText {

  private IcebaseReportsText() {}

  /** The Options tree node. */
  public static String optionsNodeTitle() {
    return I18n.tr("Crash Reports");
  }

  /** The Options pane's title. */
  public static String paneTitle() {
    return I18n.tr("Anonymous Crash and Freeze Reports");
  }

  /** The checkbox, in the wizard and in the Options pane. */
  public static String label() {
    return I18n.tr("Send anonymous crash and freeze reports to Icebase, our bug tracker");
  }

  /** What a report is, what it holds and what it never holds, and why it matters. */
  public static String note() {
    return I18n.tr(
        "A report is made when FrostWire crashes or freezes. It holds version numbers (FrostWire, Java, your system), memory, CPU and thread statistics, uptime, and where in the code it happened (class, method and line). Never error messages, your user name, file names, searches, downloads or anything that identifies you. Our server sees your IP address, as any server does, but does not store it. FrostWire also tells Icebase once a minute that it is running and whether it crashed or froze, so we can count sessions without problems. Most crashes and freezes we fix are found this way, often before anyone writes in.");
  }

  /** Where the choice lives after the wizard. */
  public static String whereToChangeIt() {
    return I18n.tr("You can change this later in Tools > Options > Advanced > Crash Reports.");
  }
}
