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
import com.limegroup.gnutella.gui.GUIUtils.SizePolicy;
import com.limegroup.gnutella.gui.I18n;
import com.limegroup.gnutella.gui.LabeledComponent;
import com.limegroup.gnutella.gui.SizedTextField;
import com.limegroup.gnutella.settings.ConnectionSettings;

import javax.swing.*;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;

/**
 * This class defines the panel in the options window that allows the user to
 * set the login data for the proxy.
 */
public final class ProxyLoginPaneItem extends AbstractPaneItem {
    private final static String TITLE = I18n.tr("Login Details");
    private final static String LABEL = I18n.tr("Configure username and password to be used for the proxy.");
    /**
     * Constant <tt>JTextField</tt> instance that holds the username.
     */
    private final JTextField PROXY_USERNAME_FIELD =
            new SizedTextField(12, SizePolicy.RESTRICT_BOTH);
    /**
     * Constant <tt>JTextField</tt> instance that holds the pasword.
     */
    private final JTextField PROXY_PASSWORD_FIELD =
            new SizedTextField(12, SizePolicy.RESTRICT_BOTH);
    /**
     * Constant for the check box that determines whether or not to
     * authenticate at proxy settings
     */
    private final JCheckBox CHECK_BOX = new JCheckBox();

    /**
     * The constructor constructs all of the elements of this
     * <tt>AbstractPaneItem</tt>.
     */
    public ProxyLoginPaneItem() {
        super(TITLE, LABEL);
        CHECK_BOX.addItemListener(new LocalAuthenticateListener());
        /*
          Constant for the key of the locale-specific <tt>String</tt> for the
          check box that enables / disables password authentification at the
          proxy.
         */
        String PROXY_AUTHENTICATE_CHECK_BOX_LABEL = I18n.tr("Enable Authentication:");
        LabeledComponent checkBox = new LabeledComponent(
                PROXY_AUTHENTICATE_CHECK_BOX_LABEL, CHECK_BOX,
                LabeledComponent.LEFT_GLUE, LabeledComponent.LEFT);
        /*
          Constant for the key of the locale-specific <tt>String</tt> for the
          label on the username field.
         */
        String PROXY_USERNAME_LABEL_KEY = I18n.tr("Username:");
        LabeledComponent username = new LabeledComponent(
                PROXY_USERNAME_LABEL_KEY, PROXY_USERNAME_FIELD,
                LabeledComponent.LEFT_GLUE, LabeledComponent.LEFT);
        /*
          Constant for the key of the locale-specific <tt>String</tt> for the
          label on the password field.
         */
        String PROXY_PASSWORD_LABEL_KEY = I18n.tr("Password:");
        LabeledComponent password = new LabeledComponent(
                PROXY_PASSWORD_LABEL_KEY, PROXY_PASSWORD_FIELD,
                LabeledComponent.LEFT_GLUE, LabeledComponent.LEFT);
        add(checkBox.getComponent());
        add(getVerticalSeparator());
        add(username.getComponent());
        add(getVerticalSeparator());
        add(password.getComponent());
    }

    /**
     * Defines the abstract method in <tt>AbstractPaneItem</tt>.
     * <p/>
     * <p/>
     * Sets the options for the fields in this <tt>PaneItem</tt> when the
     * window is shown.
     */
    public void initOptions() {
        String username = ConnectionSettings.PROXY_USERNAME.getValue();
        String password = ConnectionSettings.PROXY_PASS.getValue();
        boolean authenticate = ConnectionSettings.PROXY_AUTHENTICATE.getValue();
        PROXY_USERNAME_FIELD.setText(username);
        PROXY_PASSWORD_FIELD.setText(password);
        CHECK_BOX.setSelected(authenticate);
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
        final String username = PROXY_USERNAME_FIELD.getText();
        final String password = PROXY_PASSWORD_FIELD.getText();
        final boolean authenticate = CHECK_BOX.isSelected();
        ConnectionSettings.PROXY_USERNAME.setValue(username);
        ConnectionSettings.PROXY_PASS.setValue(password);
        ConnectionSettings.PROXY_AUTHENTICATE.setValue(authenticate);
        SettingsPack settings = new SettingsPack();
        if (authenticate) {
            int connectionMethod = ConnectionSettings.CONNECTION_METHOD.getValue();
            settings.setInteger(settings_pack.int_types.proxy_type.swigValue(), settings_pack.proxy_type_t.http_pw.swigValue());
            if (connectionMethod == ConnectionSettings.C_HTTP_PROXY) {
                settings.setInteger(settings_pack.int_types.proxy_type.swigValue(), settings_pack.proxy_type_t.http_pw.swigValue());
            } else if (connectionMethod == ConnectionSettings.C_SOCKS5_PROXY) {
                settings.setInteger(settings_pack.int_types.proxy_type.swigValue(), settings_pack.proxy_type_t.socks5_pw.swigValue());
            }
        }
        settings.setString(settings_pack.string_types.proxy_username.swigValue(), username);
        settings.setString(settings_pack.string_types.proxy_password.swigValue(), password);
        BTEngine.getInstance().applySettings(settings);
        return false;
    }

    public boolean isDirty() {
        return !ConnectionSettings.PROXY_USERNAME.getValue().equals(PROXY_USERNAME_FIELD.getText()) ||
                !ConnectionSettings.PROXY_PASS.getValue().equals(PROXY_PASSWORD_FIELD.getText()) ||
                ConnectionSettings.PROXY_AUTHENTICATE.getValue() != CHECK_BOX.isSelected();
    }

    private void updateState() {
        PROXY_USERNAME_FIELD.setEnabled(CHECK_BOX.isSelected());
        PROXY_PASSWORD_FIELD.setEnabled(CHECK_BOX.isSelected());
        PROXY_USERNAME_FIELD.setEditable(CHECK_BOX.isSelected());
        PROXY_PASSWORD_FIELD.setEditable(CHECK_BOX.isSelected());
    }

    /**
     * Listener class that responds to the checking and the unchecking of the
     * checkbox specifying whether or not to use authentication. It makes the
     * other fields editable or not editable depending on the state of the
     * JCheckBox.
     */
    private class LocalAuthenticateListener implements ItemListener {
        public void itemStateChanged(ItemEvent e) {
            updateState();
        }
    }
}
