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
import com.limegroup.gnutella.settings.SearchEnginesSettings;
import java.awt.*;
import javax.swing.*;

/**
 * First-run/update wizard page explaining that seeding a torrent publishes it to the BitTorrent
 * network, and letting the user opt in to having the torrents they are actively seeding browsable
 * by crawlers.
 */
final class SeedingPublishingWindow extends SetupWindow {
  private JCheckBox _crawlerOptIn;

  /** Creates the window and its components. */
  SeedingPublishingWindow(SetupManager manager) {
    super(
        manager,
        I18n.tr("Seeding & Publishing"),
        I18n.tr(
            "Seeding a torrent publishes it to the BitTorrent network, making it discoverable by others. You can choose whether crawlers may browse the torrents you are actively seeding."));
  }

  @Override
  protected void createWindow() {
    super.createWindow();
    JPanel mainPanel = new JPanel(new GridBagLayout());
    _crawlerOptIn =
        new JCheckBox(I18n.tr("Let crawlers browse the torrents I am actively seeding"));
    _crawlerOptIn.setSelected(SearchEnginesSettings.ICEBRIDGE_PUBLIC_CATALOG.getValue());
    _crawlerOptIn.setToolTipText(
        I18n.tr(
            "Only torrents you are actively seeding are shared with crawlers. Downloaded history that is not seeding is never published."));
    GridBagConstraints gbc = new GridBagConstraints();
    gbc.anchor = GridBagConstraints.NORTHWEST;
    gbc.fill = GridBagConstraints.HORIZONTAL;
    gbc.gridwidth = GridBagConstraints.REMAINDER;
    gbc.weightx = 1;
    mainPanel.add(_crawlerOptIn, gbc);
    gbc = new GridBagConstraints();
    gbc.anchor = GridBagConstraints.NORTHWEST;
    gbc.fill = GridBagConstraints.HORIZONTAL;
    gbc.gridwidth = GridBagConstraints.REMAINDER;
    gbc.weightx = 1;
    gbc.weighty = 1;
    JLabel scope =
        new JLabel(
            "<html>"
                + I18n.tr(
                    "Only torrents you are actively seeding are shared with crawlers. Downloaded history that is not seeding is never published.")
                + "</html>");
    scope.setBorder(BorderFactory.createEmptyBorder(0, 22, 0, 0));
    scope.setFont(scope.getFont().deriveFont(Font.PLAIN));
    mainPanel.add(scope, gbc);
    setSetupComponent(mainPanel);
  }

  /**
   * Persists the crawler opt-in value. Loading happens in {@link #createWindow()}, which is called
   * every time the page is opened.
   */
  @Override
  public void applySettings(boolean loadCoreComponents) {
    if (_crawlerOptIn != null) {
      SearchEnginesSettings.ICEBRIDGE_PUBLIC_CATALOG.setValue(_crawlerOptIn.isSelected());
    }
  }
}
