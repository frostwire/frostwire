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
 * First-run/update wizard page explaining that seeding a torrent publishes it, and asking two
 * separate questions: whether to join the IceBridge network (search, and be found by keyword), and
 * whether to also share the whole catalog so people can browse it and crawlers can index it.
 */
final class SeedingPublishingWindow extends SetupWindow {
  /** Line width inside the 700px page: its 10px side padding, the bullet and a safety margin. */
  private static final int TEXT_WIDTH = 640;

  private static final int SCOPE_INDENT = 22;

  private JCheckBox _joinNetwork;
  private JCheckBox _shareCatalog;

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
      bullet.add(new WrappedLabel(point, TEXT_WIDTH), BorderLayout.CENTER);
      mainPanel.add(bullet, row);
    }
    mainPanel.add(Box.createVerticalStrut(10), row);

    _joinNetwork = new JCheckBox(SeedingPublishingText.joinNetworkLabel());
    _joinNetwork.setSelected(SearchEnginesSettings.ICEBRIDGE_ENABLED.getValue());
    _joinNetwork.setToolTipText(SeedingPublishingText.joinNetworkNote());
    mainPanel.add(_joinNetwork, row);
    mainPanel.add(note(SeedingPublishingText.joinNetworkNote()), row);
    mainPanel.add(Box.createVerticalStrut(10), row);

    _shareCatalog = new JCheckBox(SeedingPublishingText.catalogLabel());
    _shareCatalog.setSelected(SearchEnginesSettings.ICEBRIDGE_PUBLIC_CATALOG.getValue());
    _shareCatalog.setToolTipText(SeedingPublishingText.catalogNote());
    _shareCatalog.setEnabled(_joinNetwork.isSelected());
    // Sharing the catalog only makes sense on the network: leaving it disables the second choice.
    _joinNetwork.addItemListener(e -> _shareCatalog.setEnabled(_joinNetwork.isSelected()));
    mainPanel.add(_shareCatalog, row);
    GridBagConstraints last = (GridBagConstraints) row.clone();
    last.weighty = 1;
    mainPanel.add(note(SeedingPublishingText.catalogNote()), last);
    setSetupComponent(mainPanel);
  }

  private static JComponent note(String text) {
    WrappedLabel note = new WrappedLabel(text, TEXT_WIDTH - SCOPE_INDENT);
    note.setBorder(BorderFactory.createEmptyBorder(0, SCOPE_INDENT, 0, 0));
    return note;
  }

  /**
   * Persists both choices. Loading happens in {@link #createWindow()}, which is called every time
   * the page is opened.
   */
  @Override
  public void applySettings(boolean loadCoreComponents) {
    if (_joinNetwork != null && _shareCatalog != null) {
      SeedingPublishingChoices.save(_joinNetwork.isSelected(), _shareCatalog.isSelected());
    }
  }

  /**
   * A plain label that shows {@code text} wrapped to {@code maxWidth} pixels, measured with the
   * label's own font, so it never asks the setup dialog to grow.
   */
  private static final class WrappedLabel extends JLabel {
    WrappedLabel(String text, int maxWidth) {
      setFont(getFont().deriveFont(Font.PLAIN));
      FontMetrics metrics = getFontMetrics(getFont());
      setText(WrappedText.html(WrappedText.wrap(text, metrics::stringWidth, maxWidth)));
    }
  }
}
