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

package com.limegroup.gnutella.gui.options.panes;

import com.frostwire.bittorrent.BTEngine;
import com.frostwire.jlibtorrent.Address;
import com.frostwire.jlibtorrent.EnumNet;
import com.limegroup.gnutella.gui.BoxPanel;
import com.limegroup.gnutella.gui.I18n;
import com.limegroup.gnutella.settings.ConnectionSettings;
import java.awt.*;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.function.Supplier;
import javax.swing.*;
import org.limewire.util.NetworkUtils;

/** Allows the user to pick a custom interface/address to bind to. */
public class NetworkInterfacePaneItem extends AbstractPaneItem {
  private static final String TITLE = I18n.tr("Network Interface");
  private static final String LABEL =
      I18n.tr(
          "You can tell FrostWire to bind outgoing connections to an IP address from a specific network interface. Listening sockets will still listen on all available interfaces. This is useful on multi-homed hosts. If you later disable this interface, FrostWire will revert to binding to an arbitrary address.");
  private static final String ADDRESS_KEY = "frostwire.networkinterfacepane.address";

  /** How the page lists the machine's interfaces; replaced by tests to make it fail. */
  static Supplier<List<EnumNet.IpInterface>> interfaces =
      () -> EnumNet.enumInterfaces(BTEngine.getInstance());

  private final ButtonGroup GROUP = new ButtonGroup();
  private final JCheckBox CUSTOM;
  private final List<JRadioButton> activeButtons = new ArrayList<>();

  public NetworkInterfacePaneItem() {
    super(TITLE, LABEL);
    CUSTOM = new JCheckBox(I18n.tr("Use a specific network interface."));
    CUSTOM.setSelected(ConnectionSettings.USE_CUSTOM_NETWORK_INTERFACE.getValue());
    CUSTOM.addItemListener(e -> updateButtons(CUSTOM.isSelected()));
    add(CUSTOM);
    try {
      List<EnumNet.IpInterface> ipInterfaces = interfaces.get();
      JPanel panel = new JPanel(new GridBagLayout());
      GridBagConstraints gbc = new GridBagConstraints();
      gbc.anchor = GridBagConstraints.NORTHWEST;
      gbc.fill = GridBagConstraints.NONE;
      gbc.gridwidth = GridBagConstraints.REMAINDER;
      // Add the available interfaces / addresses
      for (EnumNet.IpInterface ni : ipInterfaces) {
        JLabel label = new JLabel(ni.friendlyName());
        gbc.insets = new Insets(5, 0, 2, 0);
        panel.add(label, gbc);
        gbc.insets = new Insets(0, 6, 0, 0);
        Address address = ni.interfaceAddress();
        JRadioButton button = new JRadioButton(address.toString());
        // don't bother adding interfaces the user won't be able to chose, that's a frustrating UX
        if (address.isLoopback()
            || address.isMulticast()
            || address.isUnspecified()
            || NetworkUtils.isLinkLocal(address)) {
          continue;
        } else {
          activeButtons.add(button);
        }
        button.setSelected(
            ConnectionSettings.CUSTOM_INETADRESS_NO_PORT.getValue().equals(address.toString()));
        button.putClientProperty(ADDRESS_KEY, address);
        GROUP.add(button);
        panel.add(button, gbc);
      }
      initializeSelection();
      gbc.weightx = 1;
      gbc.weighty = 1;
      gbc.fill = GridBagConstraints.BOTH;
      gbc.insets = new Insets(0, 0, 0, 0);
      gbc.gridheight = GridBagConstraints.REMAINDER;
      panel.add(Box.createGlue(), gbc);
      JScrollPane pane = new JScrollPane(panel);
      pane.setBorder(BorderFactory.createEmptyBorder());
      add(pane);
      // initialize
      updateButtons(CUSTOM.isSelected());
    } catch (Throwable se) {
      // Keep showing the saved choice (so the page is not dirty and Apply keeps it), but there is
      // nothing to pick from.
      CUSTOM.setEnabled(false);
      JPanel labelPanel = new BoxPanel(BoxPanel.X_AXIS);
      labelPanel.add(
          new JLabel(
              I18n.tr(
                  "FrostWire was unable to determine which network interfaces are available on this machine. Outgoing connections will bind to any arbitrary interface.")));
      labelPanel.add(Box.createHorizontalGlue());
      JPanel outerPanel = new BoxPanel();
      outerPanel.add(labelPanel);
      outerPanel.add(Box.createVerticalGlue());
      add(outerPanel);
    }
  }

  private void updateButtons(boolean enable) {
    for (JRadioButton button : activeButtons) {
      button.setEnabled(enable);
    }
  }

  private void initializeSelection() {
    // Make sure one item is selected always.
    Enumeration<AbstractButton> buttons = GROUP.getElements();
    while (buttons.hasMoreElements()) {
      AbstractButton bt = buttons.nextElement();
      if (bt.isSelected()) return;
    }
    // Select the first one if nothing's selected.
    buttons = GROUP.getElements();
    while (buttons.hasMoreElements()) {
      AbstractButton bt = buttons.nextElement();
      if (bt.isEnabled()) {
        bt.setSelected(true);
        return;
      }
    }
  }

  /** Applies the options currently set in this `PaneItem`. */
  public boolean applyOptions() {
    apply(CUSTOM.isSelected(), selectedAddress());
    return false;
  }

  /**
   * Saves the page's choices and, only when one of them changed, rebinds the engine's listen
   * sockets in the background, never on the EDT (frostwire/frostwire#1568).
   *
   * @param address the address picked in the list, or null when there is none
   */
  static void apply(boolean custom, String address) {
    if (!changed(custom, address)) {
      return;
    }
    ConnectionSettings.USE_CUSTOM_NETWORK_INTERFACE.setValue(custom);
    if (custom && address != null) {
      ConnectionSettings.CUSTOM_INETADRESS_NO_PORT.setValue(address);
    }
    EngineSettings.rebindListenInterface();
  }

  /**
   * Whether the choices differ from the saved settings. The address picked in the list only counts
   * while a specific interface is used: with the box unchecked the list still has a selection (the
   * first address), which is not a change.
   */
  static boolean changed(boolean custom, String address) {
    if (ConnectionSettings.USE_CUSTOM_NETWORK_INTERFACE.getValue() != custom) {
      return true;
    }
    return custom
        && address != null
        && !address.equals(ConnectionSettings.CUSTOM_INETADRESS_NO_PORT.getValue());
  }

  /** The address of the selected button, or null when none is selected. */
  private String selectedAddress() {
    Enumeration<AbstractButton> buttons = GROUP.getElements();
    while (buttons.hasMoreElements()) {
      AbstractButton bt = buttons.nextElement();
      if (bt.isSelected()) {
        Address addr = (Address) bt.getClientProperty(ADDRESS_KEY);
        return addr != null ? addr.toString() : null;
      }
    }
    return null;
  }

  public boolean isDirty() {
    return changed(CUSTOM.isSelected(), selectedAddress());
  }

  /** Sets the options for the fields in this `PaneItem` when the window is shown. */
  public void initOptions() {}
}
