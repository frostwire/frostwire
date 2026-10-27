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

package com.limegroup.gnutella.gui;

import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import javax.swing.*;

/**
 * @author gubatron
 * @author aldenml
 */
public final class VPNStatusButton extends JPanel implements VPNStatusRefresher.VPNStatusListener {
  static final String VPN_URL = "http://www.frostwire.com/vpn";
  private final IconButton iconButton;
  private final VPNBitTorrentDisabledWarningLabel vpnDropGuardLabel;
  private boolean lastVPNStatus;

  /** VPN state the icon and tooltip show; null until the first VPN check reports. */
  private Boolean shownVPNStatus;

  /** Whether the "VPN Off: BitTorrent disabled" label is currently a child of this panel. */
  private boolean dropGuardLabelShown;

  VPNStatusButton() {
    // Use compact FlowLayout with minimal gaps
    super(new FlowLayout(FlowLayout.CENTER, 0, 0));
    setOpaque(false);
    zeroInsets(this);

    iconButton = new IconButton("vpn_off", true);
    iconButton.setBorder(null);
    iconButton.setMargin(new Insets(0, 0, 0, 0));
    iconButton.setContentAreaFilled(false);
    iconButton.setBorderPainted(false);
    iconButton.setOpaque(false);

    vpnDropGuardLabel = createVPNDisconnectLabel();
    initActionListener();

    // Set compact size
    Dimension size = new Dimension(20, 20);
    setMinimumSize(size);
    setPreferredSize(size);
    setMaximumSize(size);
  }

  private static void zeroInsets(JComponent jc) {
    Insets insets = jc.getInsets();
    insets.left = 0;
    insets.right = 0;
    insets.top = 0;
    insets.bottom = 0;
  }

  private void initActionListener() {
    iconButton.addActionListener(
        new java.awt.event.ActionListener() {
          public void actionPerformed(java.awt.event.ActionEvent e) {
            GUIMediator.openURL(VPN_URL);
          }
        });
  }

  /** Sets up the bittorrent connection disabled due to vpn settings info */
  private VPNBitTorrentDisabledWarningLabel createVPNDisconnectLabel() {
    VPNBitTorrentDisabledWarningLabel bitTorrentDisabledWarning =
        new VPNBitTorrentDisabledWarningLabel();
    bitTorrentDisabledWarning.setToolTipText(
        I18n.tr(
            "Due to current settings without VPN connection BitTorrent will not start. Click to see the settings screen"));
    // Defer HTML content to avoid EDT violation
    // HTML rendering triggers expensive font metrics calculations (>2 second EDT block)
    SwingUtilities.invokeLater(
        () -> {
          bitTorrentDisabledWarning.setText(
              "<html><b>" + I18n.tr("VPN Off: BitTorrent disabled") + "</b></html>");
        });
    bitTorrentDisabledWarning.addMouseListener(
        new MouseAdapter() {
          @Override
          public void mouseClicked(MouseEvent e) {
            onVPNBitTorrentDisabledWarningLabelClicked();
          }

          @Override
          public void mousePressed(MouseEvent e) {
            onVPNBitTorrentDisabledWarningLabelClicked();
          }

          @Override
          public void mouseReleased(MouseEvent e) {
            onVPNBitTorrentDisabledWarningLabelClicked();
          }
        });
    return bitTorrentDisabledWarning;
  }

  private void onVPNBitTorrentDisabledWarningLabelClicked() {
    VPNDropGuard.canUseBitTorrent(
        true,
        () -> {
          updateVPNIcon(false);
          GUIMediator.instance().getStatusLine().refresh();
        });
  }

  /**
   * Called on every VPN status refresh. Updates the icon, tooltip and children in place and only
   * when what they show changes: rebuilding the children on each refresh invalidated and re-laid
   * out the whole status bar every time (Icebase #4e805e1c9aba).
   */
  private void updateVPNIcon(boolean vpnIsOn) {
    lastVPNStatus = vpnIsOn;
    boolean showDropGuardLabel = !vpnIsOn && vpnDropGuardLabel.shouldBeShown();
    if (shownVPNStatus == null) {
      add(iconButton);
    }
    if (shownVPNStatus == null || shownVPNStatus != vpnIsOn) {
      shownVPNStatus = vpnIsOn;
      updateIconAndToolTip(vpnIsOn);
    }
    if (showDropGuardLabel != dropGuardLabelShown) {
      dropGuardLabelShown = showDropGuardLabel;
      if (showDropGuardLabel) {
        add(vpnDropGuardLabel);
      } else {
        remove(vpnDropGuardLabel);
      }
      revalidate();
      repaint();
    }
  }

  private void updateIconAndToolTip(boolean vpnIsOn) {
    iconButton.setIcon(GUIMediator.getThemeImage(vpnIsOn ? "vpn_on" : "vpn_off"));
    // Defer HTML ToolTipText to avoid EDT violation
    // HTML rendering triggers expensive font metrics calculations (>2 second EDT block)
    SwingUtilities.invokeLater(
        () -> {
          if (vpnIsOn) {
            iconButton.setToolTipText(
                "<html><p width=\"260\">"
                    + I18n.tr(
                        "FrostWire has detected a VPN connection, your privacy is safe from prying eyes.")
                    + "</p></html>");
          } else {
            iconButton.setToolTipText(
                "<html><p width=\"260\">"
                    + I18n.tr(
                        "FrostWire can't detect an encrypted VPN connection, your privacy is at risk. Click icon to set up an encrypted VPN connection.")
                    + "</p></html>");
          }
        });
  }

  @Override
  public void onStatusUpdated(boolean vpnIsOn) {
    updateVPNIcon(vpnIsOn);
  }

  public boolean getLastVPNStatus() {
    return lastVPNStatus;
  }
}
