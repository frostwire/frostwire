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

import java.util.List;

/**
 * A sample of the text the first windows show, in the user's language: the splash window's status,
 * the first-run setup wizard and the main window's menus, header tabs and Transfers tab. The
 * startup warm-up measures it in each UI font before the splash, so it loads the font files
 * (composite fonts' fallback slots) this text needs, those of the user's script, and no others:
 * loading every slot of every UI font then held the JDK's global font lock while the EDT built the
 * splash, the wizard and the main window, and the EDT waited for it (Icebase #7d83b84c37c5,
 * #0068ec7873a2, frostwire/frostwire#1544).
 */
public final class EarlyWindowText {

  private EarlyWindowText() {}

  /** The strings, translated to the user's language. */
  public static List<String> strings() {
    // Initializing ResourceManager sets I18n's locale to the user's language; the warm-up asks
    // before startup does.
    ResourceManager.getLocale();
    return List.of(
        // The splash window's status (SplashWindow, Initializer).
        I18n.tr("Loading FrostWire..."),
        I18n.tr("Loading HTML Engine..."),
        I18n.tr("Loading User Interface..."),
        I18n.tr("Loading Core Components..."),
        // The setup wizard (SetupManager, SetupWindow and its pages). Its title is a 16 pt bold
        // Dialog, whose slots the EDT loaded measuring it (Icebase #d5a31f6c0160,
        // frostwire/frostwire#1329).
        I18n.tr("FrostWire Setup Wizard"),
        I18n.tr("Welcome"),
        I18n.tr("Language:"),
        I18n.tr("<< Back"),
        I18n.tr("Next >>"),
        I18n.tr("Finish"),
        I18n.tr("Cancel"),
        I18n.tr("Learn more about this option..."),
        I18n.tr("BitTorrent Sharing Settings"),
        I18n.tr("Seeding Settings"),
        I18n.tr("Seed finished downloads."),
        I18n.tr("Don't seed finished downloads."),
        I18n.tr("Seeding & Publishing"),
        I18n.tr("State Your Intent"),
        // The main window: its menus (the menu package), header tabs (SearchTab, TransfersTab,
        // LibraryTab) and the Transfers tab's filter.
        I18n.tr("&File"),
        I18n.tr("&View"),
        I18n.tr("&Tools"),
        I18n.tr("&Help"),
        I18n.tr("Search"),
        I18n.tr("Transfers"),
        I18n.tr("Library"),
        I18n.tr("All"),
        I18n.tr("Downloading"),
        I18n.tr("Seeding"),
        I18n.tr("Finished"),
        I18n.tr("filter transfers here"));
  }

  /** {@link #strings()} as one text. */
  public static String sample() {
    return String.join(" ", strings());
  }
}
