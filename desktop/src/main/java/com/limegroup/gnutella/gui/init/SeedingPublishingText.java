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
        I18n.tr("You can change both options later in Tools > Options > IceBridge."));
  }

  /** First choice: take part in the network at all (search, and be found by keyword). */
  public static String joinNetworkLabel() {
    return I18n.tr(
        "Join the IceBridge network: search other users' torrents and let them find the ones I am seeding");
  }

  public static String joinNetworkNote() {
    return I18n.tr(
        "While this is on, anyone using Distributed Search can find the torrents you are actively seeding when their keywords match, even if you do not share your catalog below. Turn it off to leave the network.");
  }

  /** Second choice: let people list and crawlers index everything actively seeded. */
  public static String catalogLabel() {
    return I18n.tr("Share my catalog so people can browse it and crawlers can index it");
  }

  public static String catalogNote() {
    return I18n.tr(
        "Lets people browse everything you are actively seeding without searching for it, and lets crawlers index the network. Downloaded history that is not seeding is never published.");
  }
}
