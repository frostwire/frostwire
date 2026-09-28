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
 * First-run/update wizard page explaining that seeding a torrent publishes it, that with
 * Distributed Search enabled other peers on the network can find the torrents being actively
 * shared, and letting the user opt in to having those torrents browsable by crawlers.
 */
final class SeedingPublishingWindow extends SetupWindow {
  private JCheckBox _crawlerOptIn;

  /** Creates the window and its components. */
  SeedingPublishingWindow(SetupManager manager) {
    super(
        manager,
        I18n.tr("Seeding & Publishing"),
        I18n.tr(
            "Seeding a torrent publishes it. With Distributed Search enabled, other FrostWire users on the network can find the torrents you are actively sharing and download them from you."));
  }

  @Override
  protected void createWindow() {
    super.createWindow();
    JPanel mainPanel = new JPanel(new GridBagLayout());
    GridBagConstraints row = new GridBagConstraints();
    row.anchor = GridBagConstraints.NORTHWEST;
    row.fill = GridBagConstraints.HORIZONTAL;
    row.gridwidth = GridBagConstraints.REMAINDER;
    row.weightx = 1;
    row.insets = new Insets(0, 0, 6, 0);
    for (String point : SeedingPublishingText.distributedSearchPoints()) {
      JPanel bullet = new JPanel(new BorderLayout(6, 0));
      bullet.setOpaque(false);
      JPanel dot = new JPanel(new BorderLayout());
      dot.setOpaque(false);
      dot.add(new JLabel("\u2022"), BorderLayout.NORTH); // stay on the first line
      bullet.add(dot, BorderLayout.WEST);
      bullet.add(new Paragraph(point), BorderLayout.CENTER);
      mainPanel.add(bullet, row);
    }
    mainPanel.add(Box.createVerticalStrut(10), row);
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
    Paragraph scope =
        new Paragraph(
            I18n.tr(
                "Only torrents you are actively seeding are shared with crawlers. Downloaded history that is not seeding is never published."));
    scope.setBorder(BorderFactory.createEmptyBorder(0, 22, 0, 0));
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

  /**
   * Wrapping text that asks for a page-sized width but wraps at whatever width it is given. Swing
   * HTML labels and plain text areas both report an unwrapped single-line width, which would widen
   * the whole setup dialog.
   */
  private static final class Paragraph extends MultiLineLabel {
    private static final int PREFERRED_WIDTH = 560;

    Paragraph(String text) {
      super(text);
      setOpaque(false);
      setFont(getFont().deriveFont(Font.PLAIN));
    }

    @Override
    public Dimension getPreferredSize() {
      int width = getWidth() > 0 ? getWidth() : PREFERRED_WIDTH;
      setSize(width, 1);
      Dimension d = super.getPreferredSize();
      return new Dimension(Math.min(d.width, PREFERRED_WIDTH), d.height);
    }
  }
}
