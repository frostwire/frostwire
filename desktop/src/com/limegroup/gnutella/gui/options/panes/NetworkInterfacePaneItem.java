/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2025, FrostWire(R). All rights reserved.

 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.limegroup.gnutella.gui.options.panes;

import com.frostwire.bittorrent.BTEngine;
import com.frostwire.jlibtorrent.Address;
import com.frostwire.jlibtorrent.EnumNet;
import com.limegroup.gnutella.gui.BoxPanel;
import com.limegroup.gnutella.gui.I18n;
import com.limegroup.gnutella.settings.ConnectionSettings;
import org.limewire.util.NetworkUtils;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;

/**
 * Allows the user to pick a custom interface/address to bind to.
 */
public class NetworkInterfacePaneItem extends AbstractPaneItem {
    private final static String TITLE = I18n.tr("Network Interface");
    private final static String LABEL = I18n.tr("You can tell FrostWire to bind outgoing connections to an IP address from a specific network interface. Listening sockets will still listen on all available interfaces. This is useful on multi-homed hosts. If you later disable this interface, FrostWire will revert to binding to an arbitrary address.");
    private static final String ADDRESS_KEY = "frostwire.networkinterfacepane.address";
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
            List<EnumNet.IpInterface> ipInterfaces = EnumNet.enumInterfaces(BTEngine.getInstance());
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
                if (address.isLoopback() || address.isMulticast() || address.isUnspecified() || NetworkUtils.isLinkLocal(address)) {
                    continue;
                } else {
                    activeButtons.add(button);
                }
                button.setSelected(ConnectionSettings.CUSTOM_INETADRESS_NO_PORT.getValue().equals(address.toString()));
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
            CUSTOM.setSelected(false);
            JPanel labelPanel = new BoxPanel(BoxPanel.X_AXIS);
            labelPanel.add(new JLabel(I18n.tr("FrostWire was unable to determine which network interfaces are available on this machine. Outgoing connections will bind to any arbitrary interface.")));
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
            if (bt.isSelected())
                return;
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

    /**
     * Applies the options currently set in this <tt>PaneItem</tt>.
     */
    public boolean applyOptions() {
        boolean isDirty = isDirty();
        if (!isDirty) {
            return false;
        }
        ConnectionSettings.USE_CUSTOM_NETWORK_INTERFACE.setValue(CUSTOM.isSelected());
        Enumeration<AbstractButton> buttons = GROUP.getElements();
        while (buttons.hasMoreElements()) {
            AbstractButton bt = buttons.nextElement();
            if (bt.isSelected() && CUSTOM.isSelected()) {
                Address addr = (Address) bt.getClientProperty(ADDRESS_KEY);
                ConnectionSettings.CUSTOM_INETADRESS_NO_PORT.setValue(addr.toString());
            }
        }
        // We don't save the port we use, just the range, and this is done in RouterConfigurationPaneItem.
        // We use this range to select a random port every time we apply the settings.
        int randomPortInRange = NetworkUtils.getPortInRange(
                ConnectionSettings.MANUAL_PORT_RANGE.getValue(),
                ConnectionSettings.PORT_RANGE_0.getDefaultValue(),
                ConnectionSettings.PORT_RANGE_1.getDefaultValue(),
                ConnectionSettings.PORT_RANGE_0.getValue(),
                ConnectionSettings.PORT_RANGE_1.getValue());
        String iface = NetworkUtils.getLibtorrentFormattedNetworkInterface(
                ConnectionSettings.USE_CUSTOM_NETWORK_INTERFACE.getValue(),
                "0.0.0.0",
                ConnectionSettings.CUSTOM_INETADRESS_NO_PORT.getValue(),
                randomPortInRange);
        BTEngine.getInstance().listenInterfaces(iface);
        return true;
    }

    public boolean isDirty() {
        if (ConnectionSettings.USE_CUSTOM_NETWORK_INTERFACE.getValue() != CUSTOM.isSelected()) {
            return true;
        }
        String expect = ConnectionSettings.CUSTOM_INETADRESS_NO_PORT.getValue();
        Enumeration<AbstractButton> buttons = GROUP.getElements();
        while (buttons.hasMoreElements()) {
            AbstractButton bt = buttons.nextElement();
            if (bt.isSelected()) {
                Address addr = (Address) bt.getClientProperty(ADDRESS_KEY); // this was a null table here for some reason.
                if (addr.toString().equals(expect))
                    return false;
            }
        }
        return true;
    }

    /**
     * Sets the options for the fields in this <tt>PaneItem</tt> when the
     * window is shown.
     */
    public void initOptions() {
    }
}    
