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
import com.frostwire.jlibtorrent.SettingsPack;
import com.frostwire.jlibtorrent.swig.settings_pack;
import com.limegroup.gnutella.gui.*;
import com.limegroup.gnutella.gui.GUIUtils.SizePolicy;
import com.limegroup.gnutella.settings.ConnectionSettings;

import javax.swing.*;

/**
 * This class defines the panel in the options window that allows the user
 * to configure I2P (Invisible Internet Project) settings for anonymous torrenting.
 */
public final class I2PPaneItem extends AbstractPaneItem {
    private final static String TITLE = I18n.tr("I2P Options");
    private final static String LABEL = I18n.tr("Configure I2P for anonymous BitTorrent. Requires I2P router running locally.");
    
    private final JCheckBox I2P_ENABLED_CHECKBOX = new JCheckBox(I18n.tr("Enable I2P Support"));
    private final JCheckBox I2P_ALLOW_MIXED_CHECKBOX = new JCheckBox(I18n.tr("Allow mixed mode (I2P + regular peers)"));
    
    private final JTextField I2P_HOST_FIELD = new SizedTextField(12, SizePolicy.RESTRICT_HEIGHT);
    private final WholeNumberField I2P_PORT_FIELD = new SizedWholeNumberField(7656, 5, SizePolicy.RESTRICT_BOTH);
    
    // Tunnel configuration
    private final WholeNumberField I2P_INBOUND_QUANTITY_FIELD = new SizedWholeNumberField(3, 2, SizePolicy.RESTRICT_BOTH);
    private final WholeNumberField I2P_OUTBOUND_QUANTITY_FIELD = new SizedWholeNumberField(3, 2, SizePolicy.RESTRICT_BOTH);
    private final WholeNumberField I2P_INBOUND_LENGTH_FIELD = new SizedWholeNumberField(3, 2, SizePolicy.RESTRICT_BOTH);
    private final WholeNumberField I2P_OUTBOUND_LENGTH_FIELD = new SizedWholeNumberField(3, 2, SizePolicy.RESTRICT_BOTH);

    /**
     * The constructor constructs all of the elements of this AbstractPaneItem.
     */
    public I2PPaneItem() {
        super(TITLE, LABEL);
        
        // Main enable checkbox
        I2P_ENABLED_CHECKBOX.addItemListener(e -> updateState());
        add(I2P_ENABLED_CHECKBOX);
        add(getHorizontalSeparator());
        
        // SAM Bridge settings
        BoxPanel bridgePanel = new BoxPanel(BoxPanel.X_AXIS);
        String hostLabelKey = I18n.tr("SAM Bridge Host:");
        LabeledComponent comp = new LabeledComponent(hostLabelKey,
                I2P_HOST_FIELD, LabeledComponent.NO_GLUE,
                LabeledComponent.LEFT);
        bridgePanel.add(comp.getComponent());
        bridgePanel.addHorizontalComponentGap();
        
        String portLabelKey = I18n.tr("Port:");
        comp = new LabeledComponent(portLabelKey, I2P_PORT_FIELD,
                LabeledComponent.NO_GLUE, LabeledComponent.LEFT);
        bridgePanel.add(comp.getComponent());
        add(bridgePanel);
        
        // Mixed mode checkbox
        add(I2P_ALLOW_MIXED_CHECKBOX);
        add(getHorizontalSeparator());
        
        // Tunnel settings
        JLabel tunnelLabel = new JLabel(I18n.tr("Tunnel Settings (Advanced):"));
        add(tunnelLabel);
        
        BoxPanel tunnelPanel1 = new BoxPanel(BoxPanel.X_AXIS);
        String inboundQtyLabel = I18n.tr("Inbound Tunnels:");
        comp = new LabeledComponent(inboundQtyLabel, I2P_INBOUND_QUANTITY_FIELD,
                LabeledComponent.NO_GLUE, LabeledComponent.LEFT);
        tunnelPanel1.add(comp.getComponent());
        tunnelPanel1.addHorizontalComponentGap();
        
        String outboundQtyLabel = I18n.tr("Outbound Tunnels:");
        comp = new LabeledComponent(outboundQtyLabel, I2P_OUTBOUND_QUANTITY_FIELD,
                LabeledComponent.NO_GLUE, LabeledComponent.LEFT);
        tunnelPanel1.add(comp.getComponent());
        add(tunnelPanel1);
        
        BoxPanel tunnelPanel2 = new BoxPanel(BoxPanel.X_AXIS);
        String inboundLenLabel = I18n.tr("Inbound Hops:");
        comp = new LabeledComponent(inboundLenLabel, I2P_INBOUND_LENGTH_FIELD,
                LabeledComponent.NO_GLUE, LabeledComponent.LEFT);
        tunnelPanel2.add(comp.getComponent());
        tunnelPanel2.addHorizontalComponentGap();
        
        String outboundLenLabel = I18n.tr("Outbound Hops:");
        comp = new LabeledComponent(outboundLenLabel, I2P_OUTBOUND_LENGTH_FIELD,
                LabeledComponent.NO_GLUE, LabeledComponent.LEFT);
        tunnelPanel2.add(comp.getComponent());
        add(tunnelPanel2);
        
        // Info label
        JLabel infoLabel = new JLabel("<html><i>" + 
            I18n.tr("Note: Tunnels [1-16], Hops [0-7]. Higher values = more anonymity, slower speed.") + 
            "</i></html>");
        add(infoLabel);
    }

    /**
     * Defines the abstract method in AbstractPaneItem.
     * Sets the options for the fields in this PaneItem when the window is shown.
     */
    public void initOptions() {
        I2P_ENABLED_CHECKBOX.setSelected(ConnectionSettings.I2P_ENABLED.getValue());
        I2P_HOST_FIELD.setText(ConnectionSettings.I2P_HOSTNAME.getValue());
        I2P_PORT_FIELD.setValue(ConnectionSettings.I2P_PORT.getValue());
        I2P_ALLOW_MIXED_CHECKBOX.setSelected(ConnectionSettings.I2P_ALLOW_MIXED.getValue());
        I2P_INBOUND_QUANTITY_FIELD.setValue(ConnectionSettings.I2P_INBOUND_QUANTITY.getValue());
        I2P_OUTBOUND_QUANTITY_FIELD.setValue(ConnectionSettings.I2P_OUTBOUND_QUANTITY.getValue());
        I2P_INBOUND_LENGTH_FIELD.setValue(ConnectionSettings.I2P_INBOUND_LENGTH.getValue());
        I2P_OUTBOUND_LENGTH_FIELD.setValue(ConnectionSettings.I2P_OUTBOUND_LENGTH.getValue());
        updateState();
    }

    /**
     * Defines the abstract method in AbstractPaneItem.
     * Applies the options currently set in this window.
     */
    public boolean applyOptions() {
        final boolean i2pEnabled = I2P_ENABLED_CHECKBOX.isSelected();
        final String i2pHost = I2P_HOST_FIELD.getText();
        final int i2pPort = I2P_PORT_FIELD.getValue();
        final boolean i2pAllowMixed = I2P_ALLOW_MIXED_CHECKBOX.isSelected();
        final int inboundQty = I2P_INBOUND_QUANTITY_FIELD.getValue();
        final int outboundQty = I2P_OUTBOUND_QUANTITY_FIELD.getValue();
        final int inboundLen = I2P_INBOUND_LENGTH_FIELD.getValue();
        final int outboundLen = I2P_OUTBOUND_LENGTH_FIELD.getValue();
        
        // Validate tunnel settings
        if (inboundQty < 1 || inboundQty > 16 || outboundQty < 1 || outboundQty > 16) {
            GUIMediator.showError(I18n.tr("Tunnel quantities must be between 1 and 16"));
            return false;
        }
        if (inboundLen < 0 || inboundLen > 7 || outboundLen < 0 || outboundLen > 7) {
            GUIMediator.showError(I18n.tr("Tunnel hop lengths must be between 0 and 7"));
            return false;
        }
        
        // Save settings
        ConnectionSettings.I2P_ENABLED.setValue(i2pEnabled);
        ConnectionSettings.I2P_HOSTNAME.setValue(i2pHost);
        ConnectionSettings.I2P_PORT.setValue(i2pPort);
        ConnectionSettings.I2P_ALLOW_MIXED.setValue(i2pAllowMixed);
        ConnectionSettings.I2P_INBOUND_QUANTITY.setValue(inboundQty);
        ConnectionSettings.I2P_OUTBOUND_QUANTITY.setValue(outboundQty);
        ConnectionSettings.I2P_INBOUND_LENGTH.setValue(inboundLen);
        ConnectionSettings.I2P_OUTBOUND_LENGTH.setValue(outboundLen);
        
        // Apply to BTEngine
        SettingsPack settings = new SettingsPack();
        if (i2pEnabled && i2pHost != null && !i2pHost.isEmpty()) {
            settings.setString(settings_pack.string_types.i2p_hostname.swigValue(), i2pHost);
            settings.setInteger(settings_pack.int_types.i2p_port.swigValue(), i2pPort);
            settings.setBoolean(settings_pack.bool_types.allow_i2p_mixed.swigValue(), i2pAllowMixed);
            settings.setInteger(settings_pack.int_types.i2p_inbound_quantity.swigValue(), inboundQty);
            settings.setInteger(settings_pack.int_types.i2p_outbound_quantity.swigValue(), outboundQty);
            settings.setInteger(settings_pack.int_types.i2p_inbound_length.swigValue(), inboundLen);
            settings.setInteger(settings_pack.int_types.i2p_outbound_length.swigValue(), outboundLen);
        } else {
            // Disable I2P by setting empty hostname
            settings.setString(settings_pack.string_types.i2p_hostname.swigValue(), "");
        }
        BTEngine.getInstance().applySettings(settings);
        
        return false;
    }

    public boolean isDirty() {
        if (ConnectionSettings.I2P_ENABLED.getValue() != I2P_ENABLED_CHECKBOX.isSelected())
            return true;
        if (!ConnectionSettings.I2P_HOSTNAME.getValue().equals(I2P_HOST_FIELD.getText()))
            return true;
        if (ConnectionSettings.I2P_PORT.getValue() != I2P_PORT_FIELD.getValue())
            return true;
        if (ConnectionSettings.I2P_ALLOW_MIXED.getValue() != I2P_ALLOW_MIXED_CHECKBOX.isSelected())
            return true;
        if (ConnectionSettings.I2P_INBOUND_QUANTITY.getValue() != I2P_INBOUND_QUANTITY_FIELD.getValue())
            return true;
        if (ConnectionSettings.I2P_OUTBOUND_QUANTITY.getValue() != I2P_OUTBOUND_QUANTITY_FIELD.getValue())
            return true;
        if (ConnectionSettings.I2P_INBOUND_LENGTH.getValue() != I2P_INBOUND_LENGTH_FIELD.getValue())
            return true;
        if (ConnectionSettings.I2P_OUTBOUND_LENGTH.getValue() != I2P_OUTBOUND_LENGTH_FIELD.getValue())
            return true;
        return false;
    }

    private void updateState() {
        boolean enabled = I2P_ENABLED_CHECKBOX.isSelected();
        I2P_HOST_FIELD.setEnabled(enabled);
        I2P_PORT_FIELD.setEnabled(enabled);
        I2P_ALLOW_MIXED_CHECKBOX.setEnabled(enabled);
        I2P_INBOUND_QUANTITY_FIELD.setEnabled(enabled);
        I2P_OUTBOUND_QUANTITY_FIELD.setEnabled(enabled);
        I2P_INBOUND_LENGTH_FIELD.setEnabled(enabled);
        I2P_OUTBOUND_LENGTH_FIELD.setEnabled(enabled);
    }
}
