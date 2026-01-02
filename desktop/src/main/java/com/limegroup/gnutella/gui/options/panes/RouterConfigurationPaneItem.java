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

import com.limegroup.gnutella.gui.*;
import com.limegroup.gnutella.gui.GUIUtils.SizePolicy;
import com.limegroup.gnutella.settings.ConnectionSettings;
import org.limewire.util.NetworkUtils;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.io.IOException;

import static com.limegroup.gnutella.settings.ConnectionSettings.PORT_RANGE_0;
import static com.limegroup.gnutella.settings.ConnectionSettings.PORT_RANGE_1;

/**
 * This class defines the panel in the options window that allows the user
 * to force their ip address to the specified value.
 */
public final class RouterConfigurationPaneItem extends AbstractPaneItem {
    private final static String TITLE = I18n.tr("Router Configuration");
    private final static String LABEL = I18n.tr("FrostWire can configure itself to work from behind a firewall or router. Using Universal Plug \'n Play (UPnP) and other NAT traversal techniques FrostWire can automatically configure your router or firewall for optimal performance. If your router does not support UPnP, FrostWire can be set to advertise an external port manually. (You may also have to configure your router if you choose manual configuration, but FrostWire will try its best so you don't have to.)");
    /**
     * Constant `WholeNumberField` instance that holds the port
     * to force to.
     */
    private final WholeNumberField PORT_0_FIELD = new SizedWholeNumberField();
    private final WholeNumberField PORT_1_FIELD = new SizedWholeNumberField();
    private final JRadioButton RANDOM_PORT = new JRadioButton(I18n.tr("Use random port (Recommended)"));
    private final JRadioButton MANUAL_PORT = new JRadioButton(I18n.tr("Manual port range"));
    private final JLabel _labelPort0;
    private final JLabel _labelPort1;
    private static final int PORT_RANGE_MIN = PORT_RANGE_0.getDefaultValue();
    private static final int PORT_RANGE_MAX = PORT_RANGE_1.getDefaultValue();

    /**
     * The constructor constructs all of the elements of this
     * `AbstractPaneItem`.
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
     * Defines the abstract method in `AbstractPaneItem`.<p>
     * <p>
     * Sets the options for the fields in this `PaneItem` when the
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
     * Defines the abstract method in `AbstractPaneItem`.<p>
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
            int tcpRangeStart = PORT_0_FIELD.getValue();
            int tcpRangeEnd = PORT_1_FIELD.getValue();
            if (!NetworkUtils.isValidPort(tcpRangeStart, PORT_RANGE_MIN, PORT_RANGE_MAX)) {
                GUIMediator.showError(I18n.tr("You must enter a port between {0} and {1} when manually forcing port.", PORT_RANGE_MIN, PORT_RANGE_MAX));
                throw new IOException("bad port: " + tcpRangeStart);
            }

            if (tcpRangeStart < 0 || tcpRangeEnd < tcpRangeStart) {
                GUIMediator.showError(I18n.tr("You must enter a valid port range."));
                throw new IOException("bad port: " + tcpRangeEnd);
            }
            ConnectionSettings.MANUAL_PORT_RANGE.setValue(true);
            ConnectionSettings.PORT_RANGE_0.setValue(tcpRangeStart);
            ConnectionSettings.PORT_RANGE_1.setValue(tcpRangeEnd);
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
