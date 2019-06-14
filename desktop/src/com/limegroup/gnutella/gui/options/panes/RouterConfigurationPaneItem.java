/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2018, FrostWire(R). All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.limegroup.gnutella.gui.options.panes;

import com.limegroup.gnutella.gui.*;
import com.limegroup.gnutella.gui.GUIUtils.SizePolicy;
import com.limegroup.gnutella.settings.ConnectionSettings;
import org.limewire.util.NetworkUtils;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.io.IOException;

/**
 * This class defines the panel in the options window that allows the user
 * to force their ip address to the specified value.
 */
public final class RouterConfigurationPaneItem extends AbstractPaneItem {
    private final static String TITLE = I18n.tr("Router Configuration");
    private final static String LABEL = I18n.tr("FrostWire can configure itself to work from behind a firewall or router. Using Universal Plug \'n Play (UPnP) and other NAT traversal techniques FrostWire can automatically configure your router or firewall for optimal performance. If your router does not support UPnP, FrostWire can be set to advertise an external port manually. (You may also have to configure your router if you choose manual configuration, but FrostWire will try its best so you don't have to.)");
    /**
     * Constant <tt>WholeNumberField</tt> instance that holds the port
     * to force to.
     */
    private final WholeNumberField PORT_0_FIELD = new SizedWholeNumberField();
    private final WholeNumberField PORT_1_FIELD = new SizedWholeNumberField();
    private final JRadioButton RANDOM_PORT = new JRadioButton(I18n.tr("Use random port (Recommended)"));
    private final JRadioButton MANUAL_PORT = new JRadioButton(I18n.tr("Manual port range"));
    private final JLabel _labelPort0;
    private final JLabel _labelPort1;

    /**
     * The constructor constructs all of the elements of this
     * <tt>AbstractPaneItem</tt>.
     */
    public RouterConfigurationPaneItem() {
        super(TITLE, LABEL);
        /*
          Constant handle to the check box that enables or disables this feature.
         */
        ButtonGroup BUTTONS = new ButtonGroup();
        BUTTONS.add(RANDOM_PORT);
        BUTTONS.add(MANUAL_PORT);
        MANUAL_PORT.addItemListener(new LocalPortListener());
        JPanel panel = new JPanel(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.gridwidth = GridBagConstraints.REMAINDER;
        c.anchor = GridBagConstraints.WEST;
        c.insets = new Insets(0, 0, 0, 6);
        panel.add(RANDOM_PORT, c);
        c.gridwidth = GridBagConstraints.REMAINDER;
        panel.add(MANUAL_PORT, c);
        _labelPort0 = new JLabel(I18n.tr("TCP port start:"));
        c = new GridBagConstraints();
        c.gridwidth = GridBagConstraints.RELATIVE;
        c.insets = new Insets(0, 10, 0, 5);
        panel.add(_labelPort0, c);
        c = new GridBagConstraints();
        c.gridwidth = GridBagConstraints.REMAINDER;
        c.anchor = GridBagConstraints.WEST;
        panel.add(PORT_0_FIELD, c);
        _labelPort1 = new JLabel(I18n.tr("TCP port end:"));
        c = new GridBagConstraints();
        c.gridwidth = GridBagConstraints.RELATIVE;
        c.insets = new Insets(0, 10, 0, 5);
        panel.add(_labelPort1, c);
        c = new GridBagConstraints();
        c.gridwidth = GridBagConstraints.REMAINDER;
        c.anchor = GridBagConstraints.WEST;
        panel.add(PORT_1_FIELD, c);
        c.weightx = 1;
        c.weighty = 1;
        c.fill = GridBagConstraints.BOTH;
        panel.add(Box.createGlue(), c);
        GUIUtils.restrictSize(panel, SizePolicy.RESTRICT_HEIGHT);
        add(panel);
    }

    private void updateState() {
        _labelPort0.setEnabled(MANUAL_PORT.isSelected());
        _labelPort1.setEnabled(MANUAL_PORT.isSelected());
        PORT_0_FIELD.setEnabled(MANUAL_PORT.isSelected());
        PORT_1_FIELD.setEnabled(MANUAL_PORT.isSelected());
    }

    /**
     * Defines the abstract method in <tt>AbstractPaneItem</tt>.<p>
     * <p>
     * Sets the options for the fields in this <tt>PaneItem</tt> when the
     * window is shown.
     */
    public void initOptions() {
        RANDOM_PORT.setSelected(!ConnectionSettings.MANUAL_PORT_RANGE.getValue());
        MANUAL_PORT.setSelected(ConnectionSettings.MANUAL_PORT_RANGE.getValue());
        PORT_0_FIELD.setValue(ConnectionSettings.PORT_RANGE_0.getValue());
        PORT_1_FIELD.setValue(ConnectionSettings.PORT_RANGE_1.getValue());
        updateState();
    }

    /**
     * Defines the abstract method in <tt>AbstractPaneItem</tt>.<p>
     * <p>
     * Applies the options currently set in this window, displaying an
     * error message to the user if a setting could not be applied.
     *
     * @throws IOException if the options could not be applied for some reason
     */
    public boolean applyOptions() throws IOException {
        if (RANDOM_PORT.isSelected()) {
            ConnectionSettings.MANUAL_PORT_RANGE.setValue(false);
        } else { // PORT.isSelected()
            int forcedTcpPort = PORT_0_FIELD.getValue();
            int forcedUdpPort = PORT_1_FIELD.getValue();
            if (!NetworkUtils.isValidPort(forcedTcpPort)) {
                GUIMediator.showError(I18n.tr("You must enter a port between 1 and 65535 when manually forcing port."));
                throw new IOException("bad port: " + forcedTcpPort);
            }
            if (!NetworkUtils.isValidPort(forcedUdpPort)) {
                GUIMediator.showError(I18n.tr("You must enter a port between 1 and 65535 when manually forcing port."));
                throw new IOException("bad port: " + forcedUdpPort);
            }
            if (forcedTcpPort < 0 || forcedUdpPort < forcedTcpPort) {
                GUIMediator.showError(I18n.tr("You must enter a valid port range."));
                throw new IOException("bad port: " + forcedUdpPort);
            }
            ConnectionSettings.MANUAL_PORT_RANGE.setValue(true);
            ConnectionSettings.PORT_RANGE_0.setValue(forcedTcpPort);
            ConnectionSettings.PORT_RANGE_1.setValue(forcedUdpPort);
        }
        return true;
    }

    public boolean isDirty() {
        if (ConnectionSettings.MANUAL_PORT_RANGE.getValue() != MANUAL_PORT.isSelected()) {
            return true;
        }
        return MANUAL_PORT.isSelected()
                && (PORT_0_FIELD.getValue() != ConnectionSettings.PORT_RANGE_0.getValue() ||
                PORT_1_FIELD.getValue() != ConnectionSettings.PORT_RANGE_1.getValue());
    }

    /**
     * Listener class that responds to the checking and the
     * unchecking of the check box specifying whether or not to
     * use a local ip configuration.  It makes the other fields
     * editable or not editable depending on the state of the
     * check box.
     */
    private class LocalPortListener implements ItemListener {
        public void itemStateChanged(ItemEvent e) {
            updateState();
        }
    }
}
