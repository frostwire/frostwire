/*
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.limegroup.gnutella.gui.options.panes;

import com.frostwire.bittorrent.BTEngine;
import com.limegroup.gnutella.gui.BoxPanel;
import com.limegroup.gnutella.gui.I18n;
import com.limegroup.gnutella.settings.ConnectionSettings;

import javax.swing.*;
import java.awt.*;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Random;

/**
 * Allows the user to pick a custom interface/address to bind to.
 */
public class NetworkInterfacePaneItem extends AbstractPaneItem {
    private final static String TITLE = I18n.tr("Network Interface");
    private final static String LABEL = I18n.tr("You can tell FrostWire to bind outgoing connections to an IP address from a specific network interface. Listening sockets will still listen on all available interfaces. This is useful on multi-homed hosts. If you later disable this interface, FrostWire will revert to binding to an arbitrary address.");
    private static final String ADDRESS = "frostwire.networkinterfacepane.address";
    private final ButtonGroup GROUP = new ButtonGroup();
    private final JCheckBox CUSTOM;
    private final List<JRadioButton> activeButtons = new ArrayList<>();

    public NetworkInterfacePaneItem() {
        super(TITLE, LABEL);
        CUSTOM = new JCheckBox(I18n.tr("Use a specific network interface."));
        CUSTOM.setSelected(ConnectionSettings.CUSTOM_NETWORK_INTERFACE.getValue());
        CUSTOM.addItemListener(e -> updateButtons(CUSTOM.isSelected()));
        add(CUSTOM);
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            JPanel panel = new JPanel(new GridBagLayout());
            GridBagConstraints gbc = new GridBagConstraints();
            gbc.anchor = GridBagConstraints.NORTHWEST;
            gbc.fill = GridBagConstraints.NONE;
            gbc.gridwidth = GridBagConstraints.REMAINDER;
            // Add the available interfaces / addresses
            while (interfaces.hasMoreElements()) {
                NetworkInterface ni = interfaces.nextElement();
                JLabel label = new JLabel(ni.getDisplayName());
                gbc.insets = new Insets(5, 0, 2, 0);
                panel.add(label, gbc);
                Enumeration<InetAddress> addresses = ni.getInetAddresses();
                gbc.insets = new Insets(0, 6, 0, 0);
                while (addresses.hasMoreElements()) {
                    InetAddress address = addresses.nextElement();
                    JRadioButton button = new JRadioButton(address.getHostAddress());
                    GROUP.add(button);
                    if (address.isAnyLocalAddress() || address.isLinkLocalAddress() || address.isLoopbackAddress()) {
                        button.setEnabled(false);
                    } else {
                        activeButtons.add(button);
                    }
                    if (ConnectionSettings.CUSTOM_INETADRESS.getValue().equals(address.getHostAddress()))
                        button.setSelected(true);
                    button.putClientProperty(ADDRESS, address);
                    panel.add(button, gbc);
                }
            }
            initializeSelection();
            gbc.weightx = 1;
            gbc.weighty = 1;
            gbc.fill = GridBagConstraints.BOTH;
            gbc.insets = new Insets(0, 0, 0, 0);
            gbc.gridheight = GridBagConstraints.REMAINDER;
            panel.add(Box.createGlue(), gbc);
            //GUIUtils.restrictSize(panel, SizePolicy.RESTRICT_HEIGHT);
            JScrollPane pane = new JScrollPane(panel);
            pane.setBorder(BorderFactory.createEmptyBorder());
            add(pane);
            // initialize
            updateButtons(CUSTOM.isSelected());
        } catch (SocketException se) {
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
     *
     */
    public boolean applyOptions() {
        boolean isDirty = isDirty();
        if (!isDirty) {
            return false;
        }
        ConnectionSettings.CUSTOM_NETWORK_INTERFACE.setValue(CUSTOM.isSelected());
        Enumeration<AbstractButton> buttons = GROUP.getElements();
        while (buttons.hasMoreElements()) {
            AbstractButton bt = buttons.nextElement();
            if (bt.isSelected()) {
                InetAddress addr = (InetAddress) bt.getClientProperty(ADDRESS);
                ConnectionSettings.CUSTOM_INETADRESS.setValue(addr.getHostAddress());
            }
        }
        String iface = "0.0.0.0";
        if (ConnectionSettings.CUSTOM_NETWORK_INTERFACE.getValue()) {
            iface = ConnectionSettings.CUSTOM_INETADRESS.getValue();
        }
        if (!ConnectionSettings.CUSTOM_NETWORK_INTERFACE.getValue()) {
            iface = "0.0.0.0";
            ConnectionSettings.CUSTOM_INETADRESS.setValue(iface);
        }
        if (iface.equals("0.0.0.0")) {
            iface = "0.0.0.0:%1$d,[::]:%1$d";
        } else {
            // quick IPv6 test
            if (iface.contains(":")) {
                iface = "[" + iface + "]";
            }
            iface = iface + ":%1$d";
        }
        // TODO: consider the actual port range
        int port0 = 37000 + new Random().nextInt(20000);
        if (ConnectionSettings.MANUAL_PORT_RANGE.getValue()) {
            port0 = ConnectionSettings.PORT_RANGE_0.getValue();
        }
        String if_string = String.format(iface, port0);
        BTEngine.getInstance().listenInterfaces(if_string);
        return false;
    }

    public boolean isDirty() {
        if (ConnectionSettings.CUSTOM_NETWORK_INTERFACE.getValue() != CUSTOM.isSelected()) {
            return true;
        }
        String expect = ConnectionSettings.CUSTOM_INETADRESS.getValue();
        Enumeration<AbstractButton> buttons = GROUP.getElements();
        while (buttons.hasMoreElements()) {
            AbstractButton bt = buttons.nextElement();
            if (bt.isSelected()) {
                InetAddress addr = (InetAddress) bt.getClientProperty(ADDRESS);
                if (addr.getHostAddress().equals(expect))
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
