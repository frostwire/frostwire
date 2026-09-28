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

package com.limegroup.gnutella.gui.init;

import com.limegroup.gnutella.gui.I18n;
import java.util.Arrays;
import java.util.List;

/** Wording of the seeding and publishing wizard page. */
public final class SeedingPublishingText {

  private SeedingPublishingText() {}

  /**
   * What Distributed Search shares, matching what the app publishes: transfers that are seeding or
   * downloading right now. Paused transfers, private torrents and download history never are.
   */
  public static List<String> distributedSearchPoints() {
    return Arrays.asList(
        I18n.tr(
            "Torrents you are seeding or downloading right now are listed in your shared index, so other people using Distributed Search can find them."),
        I18n.tr(
            "Paused transfers, private torrents and your download history are never published."),
        I18n.tr(
            "To stop taking part, turn off \"Enable IceBridge (distributed relay)\" in Tools > Options > IceBridge."));
  }
}
