/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.limegroup.gnutella.gui.init;

import com.limegroup.gnutella.gui.IcebaseReports;
import com.limegroup.gnutella.settings.SearchEnginesSettings;

/**
 * The independent choices of the "Seeding & Publishing" page. Joining the IceBridge network is what
 * lets other users find torrents you are seeding by keyword; sharing the catalog is the extra step
 * of letting people browse, and crawlers index, everything you are actively seeding. Unchecking the
 * catalog never takes you off the network. The third choice, sending anonymous crash and freeze
 * reports to Icebase, is unrelated to the network and stored on its own.
 */
public final class SeedingPublishingChoices {

  private SeedingPublishingChoices() {}

  public static void save(boolean joinNetwork, boolean shareCatalog, boolean icebaseReports) {
    SearchEnginesSettings.ICEBRIDGE_ENABLED.setValue(joinNetwork);
    SearchEnginesSettings.ICEBRIDGE_PUBLIC_CATALOG.setValue(shareCatalog);
    IcebaseReports.setEnabled(icebaseReports);
  }
}
