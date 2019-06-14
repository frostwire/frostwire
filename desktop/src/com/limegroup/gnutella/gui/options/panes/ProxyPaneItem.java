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
import com.frostwire.jlibtorrent.SettingsPack;
import com.frostwire.jlibtorrent.swig.settings_pack;
import com.limegroup.gnutella.gui.*;
import com.limegroup.gnutella.gui.GUIUtils.SizePolicy;
import com.limegroup.gnutella.settings.ConnectionSettings;

import javax.swing.*;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;

/**
 * This class defines the panel in the options window that allows the user to
 * select a proxy to use.
 */
public final class ProxyPaneItem extends AbstractPaneItem {
    private final static String TITLE = I18n.tr("Proxy Options");
    private final static String LABEL = I18n.tr("Configure Proxy Options for FrostWire.");
    private final JRadioButton NO_PROXY_BUTTON = new JRadioButton(I18n.tr("No Proxy"));
    private final JRadioButton SOCKS4_PROXY_BUTTON = new JRadioButton("Socks v4");
    private final JRadioButton SOCKS5_PROXY_BUTTON = new JRadioButton("Socks v5");
    private final JRadioButton HTTP_PROXY_BUTTON = new JRadioButton("HTTP");
    /**
     * Constant <tt>JTextField</tt> instance that holds the ip address to use
     * as a proxy.
     */
    private final JTextField PROXY_HOST_FIELD =
            new SizedTextField(12, SizePolicy.RESTRICT_HEIGHT);
    /**
     * Constant <tt>WholeNumberField</tt> instance that holds the port of the
     * proxy.
     */
    private final WholeNumberField PROXY_PORT_FIELD =
            new SizedWholeNumberField(8080, 5, SizePolicy.RESTRICT_BOTH);

    /**
     * The constructor constructs all of the elements of this
     * <tt>AbstractPaneItem</tt>.
     */
    public ProxyPaneItem() {
        super(TITLE, LABEL);
        /*
          Constant handle to the check box that enables or disables this feature.
         */
        ButtonGroup BUTTONS = new ButtonGroup();
        BUTTONS.add(NO_PROXY_BUTTON);
        BUTTONS.add(SOCKS4_PROXY_BUTTON);
        BUTTONS.add(SOCKS5_PROXY_BUTTON);
        BUTTONS.add(HTTP_PROXY_BUTTON);
        NO_PROXY_BUTTON.addItemListener(new LocalProxyListener());
        add(NO_PROXY_BUTTON);
        add(SOCKS4_PROXY_BUTTON);
        add(SOCKS5_PROXY_BUTTON);
        add(HTTP_PROXY_BUTTON);
        add(getHorizontalSeparator());
        BoxPanel panel = new BoxPanel(BoxPanel.X_AXIS);
        /*
          Constant for the key of the locale-specific <tt>String</tt> for the
          label on the proxy host field.
         */
        String PROXY_HOST_LABEL_KEY = I18n.tr("Proxy:");
        LabeledComponent comp = new LabeledComponent(PROXY_HOST_LABEL_KEY,
                PROXY_HOST_FIELD, LabeledComponent.NO_GLUE,
                LabeledComponent.LEFT);
        panel.add(comp.getComponent());
        panel.addHorizontalComponentGap();
        /*
          Constant for the key of the locale-specific <tt>String</tt> for the
          label on the port text field.
         */
        String PROXY_PORT_LABEL_KEY = I18n.tr("Port:");
        comp = new LabeledComponent(PROXY_PORT_LABEL_KEY, PROXY_PORT_FIELD,
                LabeledComponent.NO_GLUE, LabeledComponent.LEFT);
        panel.add(comp.getComponent());
        add(panel);
    }

    /**
     * Defines the abstract method in <tt>AbstractPaneItem</tt>.
     * <p/>
     * <p/>
     * Sets the options for the fields in this <tt>PaneItem</tt> when the
     * window is shown.
     */
    public void initOptions() {
        String proxy = ConnectionSettings.PROXY_HOST.getValue();
        int proxyPort = ConnectionSettings.PROXY_PORT.getValue();
        int connectionMethod = ConnectionSettings.CONNECTION_METHOD.getValue();
        PROXY_PORT_FIELD.setValue(proxyPort);
        NO_PROXY_BUTTON.setSelected(
                connectionMethod == ConnectionSettings.C_NO_PROXY);
        SOCKS4_PROXY_BUTTON.setSelected(
                connectionMethod == ConnectionSettings.C_SOCKS4_PROXY);
        SOCKS5_PROXY_BUTTON.setSelected(
                connectionMethod == ConnectionSettings.C_SOCKS5_PROXY);
        HTTP_PROXY_BUTTON.setSelected(
                connectionMethod == ConnectionSettings.C_HTTP_PROXY);
        PROXY_HOST_FIELD.setText(proxy);
        updateState();
    }

    /**
     * Defines the abstract method in <tt>AbstractPaneItem</tt>.
     * <p/>
     * <p/>
     * Applies the options currently set in this window, displaying an error
     * message to the user if a setting could not be applied.
     *
     */
    public boolean applyOptions() {
        int connectionMethod = ConnectionSettings.C_NO_PROXY;
        if (SOCKS4_PROXY_BUTTON.isSelected()) {
            connectionMethod = ConnectionSettings.C_SOCKS4_PROXY;
        } else if (SOCKS5_PROXY_BUTTON.isSelected()) {
            connectionMethod = ConnectionSettings.C_SOCKS5_PROXY;
        } else if (HTTP_PROXY_BUTTON.isSelected()) {
            connectionMethod = ConnectionSettings.C_HTTP_PROXY;
        }
        final int proxyPort = PROXY_PORT_FIELD.getValue();
        final String proxyHost = PROXY_HOST_FIELD.getText();
        ConnectionSettings.PROXY_PORT.setValue(proxyPort);
        ConnectionSettings.CONNECTION_METHOD.setValue(connectionMethod);
        ConnectionSettings.PROXY_HOST.setValue(proxyHost);
        SettingsPack settings = new SettingsPack();
        if (connectionMethod == ConnectionSettings.C_NO_PROXY) {
            settings.setInteger(settings_pack.int_types.proxy_type.swigValue(), settings_pack.proxy_type_t.none.swigValue());
        } else if (connectionMethod == ConnectionSettings.C_HTTP_PROXY) {
            settings.setInteger(settings_pack.int_types.proxy_type.swigValue(), settings_pack.proxy_type_t.http.swigValue());
        } else if (connectionMethod == ConnectionSettings.C_SOCKS4_PROXY) {
            settings.setInteger(settings_pack.int_types.proxy_type.swigValue(), settings_pack.proxy_type_t.socks4.swigValue());
        } else if (connectionMethod == ConnectionSettings.C_SOCKS5_PROXY) {
            settings.setInteger(settings_pack.int_types.proxy_type.swigValue(), settings_pack.proxy_type_t.socks5.swigValue());
        }
        settings.setString(settings_pack.string_types.proxy_hostname.swigValue(), proxyHost);
        settings.setInteger(settings_pack.int_types.proxy_port.swigValue(), proxyPort);
        BTEngine.getInstance().applySettings(settings);
        return false;
    }

    public boolean isDirty() {
        if (ConnectionSettings.PROXY_PORT.getValue() != PROXY_PORT_FIELD.getValue())
            return true;
        if (!ConnectionSettings.PROXY_HOST.getValue().equals(PROXY_HOST_FIELD.getText()))
            return true;
        switch (ConnectionSettings.CONNECTION_METHOD.getValue()) {
            case ConnectionSettings.C_SOCKS4_PROXY:
                return !SOCKS4_PROXY_BUTTON.isSelected();
            case ConnectionSettings.C_SOCKS5_PROXY:
                return !SOCKS5_PROXY_BUTTON.isSelected();
            case ConnectionSettings.C_HTTP_PROXY:
                return !HTTP_PROXY_BUTTON.isSelected();
            case ConnectionSettings.C_NO_PROXY:
                return !NO_PROXY_BUTTON.isSelected();
            default:
                return true;
        }
    }

    private void updateState() {
        PROXY_HOST_FIELD.setEditable(!NO_PROXY_BUTTON.isSelected());
        PROXY_PORT_FIELD.setEditable(!NO_PROXY_BUTTON.isSelected());
        PROXY_HOST_FIELD.setEnabled(!NO_PROXY_BUTTON.isSelected());
        PROXY_PORT_FIELD.setEnabled(!NO_PROXY_BUTTON.isSelected());
    }

    /**
     * Listener class that responds to the checking and the unchecking of the
     * RadioButton specifying whether or not to use a proxy configuration. It
     * makes the other fields editable or not editable depending on the state
     * of the JRadioButton.
     */
    private class LocalProxyListener implements ItemListener {
        public void itemStateChanged(ItemEvent e) {
            updateState();
        }
    }
}
