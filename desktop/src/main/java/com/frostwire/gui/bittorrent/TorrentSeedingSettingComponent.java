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

package com.frostwire.gui.bittorrent;

import com.frostwire.gui.theme.ThemeMediator;
import com.limegroup.gnutella.gui.I18n;
import com.limegroup.gnutella.settings.SharingSettings;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import javax.swing.*;

/**
 * The choice to seed finished downloads or not, on the setup wizard's BitTorrent page and in
 * Options. Each choice is a plain-text radio button with its explanation in a wrapped label below
 * it. The radio buttons' text used to be HTML: building them made the wizard's EDT create Swing's
 * HTML view and its composite font while the startup warm-up was loading font files under the JDK's
 * global font lock, and the page froze (Icebase #7d83b84c37c5, frostwire/frostwire#1544).
 */
public class TorrentSeedingSettingComponent extends JPanel {
  private final boolean _precheck;
  private final boolean _border;
  private JRadioButton seedingRadioButton;
  private JRadioButton notSeedingRadioButton;
  private JTextArea seedingDescription;
  private JTextArea notSeedingDescription;

  /**
   * @param precheck - whether or not to pre-select one of the radio buttons.
   */
  public TorrentSeedingSettingComponent(boolean precheck, boolean useBorder) {
    _precheck = precheck;
    _border = useBorder;
    setupUI();
  }

  private void setupUI() {
    setLayout(new GridBagLayout());
    if (_border) {
      setBorder(ThemeMediator.createTitledBorder(I18n.tr("Seeding Settings")));
    }
    initOptionButtons();
    seedingDescription =
        new Description(
            I18n.tr(
                "BitTorrent users on the internet will be able to download file chunks of the data your torrents seed. (Recommended)"),
            seedingRadioButton);
    notSeedingDescription =
        new Description(
            I18n.tr(
                "BitTorrent users on the internet may only download file chunks of that torrent from you while you're downloading its data files. Some trackers will penalize this Leeching behavior."),
            notSeedingRadioButton);
    addChoice(seedingRadioButton, seedingDescription);
    addChoice(notSeedingRadioButton, notSeedingDescription);
  }

  private void addChoice(JRadioButton radioButton, JTextArea description) {
    GridBagConstraints c = new GridBagConstraints();
    c.anchor = GridBagConstraints.LINE_START;
    c.fill = GridBagConstraints.HORIZONTAL;
    c.gridwidth = GridBagConstraints.REMAINDER;
    c.insets = new Insets(5, 5, 0, 5);
    c.weightx = 1.0;
    add(radioButton, c);
    // The explanation lines up with the radio button's text, not with its circle.
    c.insets = new Insets(0, 5 + textIndent(radioButton), 5, 5);
    add(description, c);
  }

  private void initOptionButtons() {
    seedingRadioButton = new JRadioButton(I18n.tr("Seed finished downloads."));
    notSeedingRadioButton = new JRadioButton(I18n.tr("Don't seed finished downloads."));
    ButtonGroup radioGroup = new ButtonGroup();
    radioGroup.add(seedingRadioButton);
    radioGroup.add(notSeedingRadioButton);
    if (_precheck) {
      if (SharingSettings.SEED_FINISHED_TORRENTS.getValue()) {
        seedingRadioButton.setSelected(true);
        notSeedingRadioButton.setSelected(false);
      } else {
        notSeedingRadioButton.setSelected(true);
        seedingRadioButton.setSelected(false);
      }
    }
  }

  /** How far a radio button's text starts from its left edge: its inset, its icon and the gap. */
  private static int textIndent(JRadioButton radioButton) {
    Icon icon = UIManager.getIcon("RadioButton.icon");
    int iconWidth = icon != null ? icon.getIconWidth() : 16;
    return radioButton.getInsets().left + iconWidth + radioButton.getIconTextGap();
  }

  public boolean wantsSeeding() {
    return seedingRadioButton.isSelected();
  }

  public boolean hasOneBeenSelected() {
    return seedingRadioButton.isSelected() || notSeedingRadioButton.isSelected();
  }

  JRadioButton seedingRadioButton() {
    return seedingRadioButton;
  }

  JRadioButton notSeedingRadioButton() {
    return notSeedingRadioButton;
  }

  JTextArea seedingDescription() {
    return seedingDescription;
  }

  JTextArea notSeedingDescription() {
    return notSeedingDescription;
  }

  /**
   * A read-only text area that looks like a label and wraps its plain text to the panel's width,
   * text without spaces (Chinese, Japanese) too. Clicking it picks its radio button, as clicking
   * the radio button's own text does.
   */
  private static final class Description extends JTextArea {
    Description(String text, JRadioButton radioButton) {
      super(text);
      setEditable(false);
      setFocusable(false);
      setLineWrap(true);
      setWrapStyleWord(true);
      setHighlighter(null);
      addMouseListener(
          new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
              if (radioButton.isEnabled()) {
                radioButton.setSelected(true);
              }
            }
          });
    }

    @Override
    public void updateUI() {
      super.updateUI();
      // A label's font and colors, also after a theme switch reinstalls the text area's own.
      setOpaque(false);
      setBorder(BorderFactory.createEmptyBorder());
      // A read-only label takes no drops; the look and feel gives text areas a drop target
      // (frostwire/frostwire#1471).
      setDropTarget(null);
      LookAndFeel.installColorsAndFont(this, "Label.background", "Label.foreground", "Label.font");
    }
  }
}
